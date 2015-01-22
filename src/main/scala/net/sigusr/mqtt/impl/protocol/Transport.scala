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

import akka.actor.{ Terminated, Actor, ActorLogging, ActorRef }
import akka.event.LoggingReceive
import akka.util.ByteString
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames.Frame
import scodec.Codec
import scodec.bits.BitVector

import scala.concurrent.duration.{ FiniteDuration, _ }

private[protocol] case object TimerSignal

abstract class Transport(mqttBrokerAddress: InetSocketAddress) extends Actor with ActorLogging { this: Protocol ⇒

  import akka.io.Tcp._
  import context.dispatcher

  var state: State = State(client = context.parent, tcpManager = tcpManagerActor)

  tcpManagerActor ! Connect(mqttBrokerAddress)
  def tcpManagerActor: ActorRef

  def receive = LoggingReceive {
    case CommandFailed(_: Connect) ⇒
      state = state.setTCPManager(sender())
      processAction(transportNotReady())
      context stop self
    case Connected(_, _) ⇒
      val connectionActor: ActorRef = sender()
      state = state.setTCPManager(connectionActor)
      connectionActor ! Register(self)
      processAction(transportReady())
      context watch connectionActor
      context become connected
  }

  def connected: Receive = LoggingReceive {
    case message: APIMessage ⇒
      processAction(handleApiMessages(message))
    case TimerSignal ⇒
      processAction(timerSignal(System.currentTimeMillis(), state))
    case Received(encodedResponse) ⇒
      val frame: Frame = Codec[Frame].decodeValidValue(BitVector.view(encodedResponse.toArray))
      processAction(handleNetworkFrames(frame, state))
    case Terminated(_) ⇒
      context unwatch state.tcpManager
      processAction(connectionClosed())
      context stop self
    case _: ConnectionClosed ⇒
      processAction(connectionClosed())
      context stop self
  }

  private def processAction(action: Action): Unit = {
    action match {
      case Sequence(actions) ⇒ actions foreach { (action: Action) ⇒ processAction(action) }
      case SetKeepAlive(keepAlive) ⇒
        state = state.setTimeOut(keepAlive)
      case StartPingRespTimer(timeout) ⇒
        state = state.setTimerTask(context.system.scheduler.scheduleOnce(FiniteDuration(timeout, MILLISECONDS), self, TimerSignal))
      case SetPendingPingResponse(isPending) ⇒
        state = state.setPingResponsePending(isPending)
      case SendToClient(message) ⇒
        state.client ! message
      case SendToNetwork(frame) ⇒
        state = state.setLastSentMessageTimestamp(System.currentTimeMillis())
        val encodedFrame = Codec[Frame].encodeValid(frame)
        state.tcpManager ! Write(ByteString(encodedFrame.toByteArray))
      case ForciblyCloseTransport ⇒
        state.tcpManager ! Abort
    }
  }

  override def postStop(): Unit = {
    state.timerTask foreach { _.cancel() }
  }
}

