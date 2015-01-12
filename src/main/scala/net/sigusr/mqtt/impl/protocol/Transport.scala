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

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.event.LoggingReceive
import akka.util.ByteString
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames.Frame
import scodec.Codec
import scodec.bits.BitVector

import scala.concurrent.duration.{FiniteDuration, _}

private[protocol] case object TimerSignal

abstract class Transport(mqttBrokerAddress: InetSocketAddress) extends Actor with ActorLogging { this: Protocol =>

  import akka.io.Tcp._
  import context.dispatcher

  var lastSentMessageTimestamp: Long = 0
  var isPingResponsePending = false
  var keepAliveValue : Long = DEFAULT_KEEP_ALIVE.toLong
  var timerTask: Option[Cancellable] = None

  tcpManagerActor ! Connect(mqttBrokerAddress)

  def tcpManagerActor : ActorRef
  
  def receive = LoggingReceive {
    case CommandFailed(_ : Connect) =>
      processAction(transportNotReady(), context.parent, sender())
      context stop self
    case Connected(_, _) =>
      val connectionActor: ActorRef = sender()
      connectionActor ! Register(self)
      processAction(transportReady(), context.parent, connectionActor)
      context become connected(context.parent, connectionActor)
  }

  def connected(clientActor : ActorRef, connectionActor : ActorRef): Receive = LoggingReceive {
    case message : APIMessage =>
      processAction(handleApiMessages(message), clientActor, connectionActor)
    case TimerSignal =>
      processAction(timerSignal(System.currentTimeMillis(), keepAliveValue, lastSentMessageTimestamp, isPingResponsePending), clientActor, connectionActor)
    case Received(encodedResponse) ⇒
      val frame: Frame = Codec[Frame].decodeValidValue(BitVector.view(encodedResponse.toArray))
      processAction(handleNetworkFrames(frame, keepAliveValue), clientActor, connectionActor)
    case _: ConnectionClosed ⇒
      processAction(connectionClosed(), clientActor, connectionActor)
      context stop self
  }

  private def processAction(action: Action, clientActor : ActorRef, connectionActor : ActorRef) : Unit = {
    action match {
      case Sequence(actions) => actions foreach { (action : Action) => processAction(action, clientActor, connectionActor) }
      case SetKeepAliveValue(duration) =>
        keepAliveValue = duration
      case StartTimer(timeout) =>
        timerTask = Some(context.system.scheduler.scheduleOnce(FiniteDuration(timeout, MILLISECONDS), self, TimerSignal))
      case SetPendingPingResponse(isPending) =>
        isPingResponsePending = isPending
      case SendToClient(message) =>
        clientActor ! message
      case SendToNetwork(frame) =>
        lastSentMessageTimestamp = System.currentTimeMillis()
        val encodedFrame = Codec[Frame].encodeValid(frame)
        connectionActor ! Write(ByteString(encodedFrame.toByteArray))
      case ForciblyCloseTransport =>
        connectionActor ! Abort
    }
  }

  override def postStop(): Unit = {
    timerTask foreach { _.cancel() }
  }
}




