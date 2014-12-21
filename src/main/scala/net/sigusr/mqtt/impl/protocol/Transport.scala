/*
 * Copyright 2014 Frédéric Cabestre
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sigusr.mqtt.impl.protocol

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, Cancellable}
import akka.event.LoggingReceive
import akka.util.ByteString
import net.sigusr.mqtt.api.MQTTAPIMessage
import net.sigusr.mqtt.impl.frames.Frame
import net.sigusr.mqtt.impl.protocol.Transport.PingRespTimeout
import scodec.Codec
import scodec.bits.BitVector

object Transport {
  private[protocol] sealed trait InternalAPIMessage
  private[protocol] case object SendKeepAlive extends InternalAPIMessage
  private[protocol] case object PingRespTimeout extends InternalAPIMessage
}

trait Transport {
  def connectionClosed() : Action
  def transportReady() : Action
  def transportNotReady() : Action
}

abstract class TCPTransport(client: ActorRef, mqttBrokerAddress: InetSocketAddress) extends Actor with Transport { this: Client with Protocol =>

  import akka.io.Tcp._
  import context.dispatcher
  import net.sigusr.mqtt.impl.protocol.Transport.{InternalAPIMessage, SendKeepAlive}

  import scala.concurrent.duration.FiniteDuration

  var keepAliveValue : Option[FiniteDuration] = None
  var keepAliveTask: Option[Cancellable] = None
  var pingResponseTask: Option[Cancellable] = None

  self ! (client, mqttBrokerAddress)

  def receive = init

  def init: Receive = LoggingReceive {
    case (client : ActorRef, remote : InetSocketAddress) =>
      tcpActor ! Connect(remote)
      context become starting(client)
  }

  def starting(client : ActorRef) : Receive = LoggingReceive {
    case CommandFailed(_: Connect) =>
      processAction(client, null, transportNotReady())
      context stop self

    case c @ Connected(_, _) =>
      sender ! Register(self)
      processAction(client, sender(), transportReady())
      context become connected(client, sender())
  }

  def connected(client : ActorRef, connection: ActorRef): Receive = LoggingReceive {
    case message : MQTTAPIMessage =>
      handleApiMessages(message).foreach(processAction(client, connection, _))
    case internalMessage: InternalAPIMessage =>
      handleInternalApiMessages(internalMessage).foreach(processAction(client, connection, _))
    case Received(encodedResponse) ⇒
      val frame: Frame = Codec[Frame].decodeValidValue(BitVector.view(encodedResponse.toArray))
      handleNetworkFrames(frame).foreach(processAction(client, connection, _))
    case _: ConnectionClosed ⇒
      processAction(client, connection, connectionClosed())
      context stop self
    case CommandFailed(w: Write) ⇒ // O/S buffer was full
  }

  def processAction(client : ActorRef, connection: ActorRef, action : Action) = {
    action match {
      case Nothing =>
      case SetKeepAliveValue(duration) =>
        keepAliveValue = Some(duration)
      case StartKeepAliveTimer =>
        keepAliveValue foreach { k =>
          keepAliveTask = Some(context.system.scheduler.schedule(k, k, self, SendKeepAlive))
        }
      case StartPingResponseTimer =>
        keepAliveValue foreach { k =>
          pingResponseTask = Some(context.system.scheduler.scheduleOnce(k, self, PingRespTimeout))
        }
      case CancelPingResponseTimer =>
        pingResponseTask foreach { _.cancel() }
      case SendToClient(message) =>
        client ! message
      case SendToNetwork(frame) =>
        val encodedFrame = Codec[Frame].encodeValid(frame)
        connection ! Write(ByteString(encodedFrame.toByteArray))
      case CloseTransport =>
        connection ! Close
    }
  }

  override def postStop(): Unit = {
    keepAliveTask foreach { _.cancel() }
    pingResponseTask foreach { _.cancel() }
  }
}




