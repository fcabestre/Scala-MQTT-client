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

  import akka.io.Tcp.{ Connect ⇒ TcpConnect, _ }
  import context.dispatcher

  var state: State = State(client = context.parent, tcpConnection = tcpManagerActor)

  def tcpManagerActor: ActorRef

  def receive: Receive = notConnected

  private def notConnected: Receive = LoggingReceive {
    case Status ⇒
      processAction(SendToClient(Disconnected))
    case c: Connect ⇒
      tcpManagerActor ! TcpConnect(mqttBrokerAddress)
      context become connecting(handleApiMessages(c))
  }

  private def connecting(pendingActions: Action): Receive = LoggingReceive {
    case Status ⇒
      processAction(SendToClient(Disconnected))
    case CommandFailed(_: TcpConnect) ⇒
      state = state.setTCPManager(sender())
      processAction(transportNotReady())
      context become notConnected
    case Connected(_, _) ⇒
      val connectionActor: ActorRef = sender()
      state = state.setTCPManager(connectionActor)
      connectionActor ! Register(self)
      processAction(pendingActions)
      context watch connectionActor
      context become connected
  }

  private def connected: Receive = LoggingReceive {
    case message: APICommand ⇒
      processAction(handleApiMessages(message))
    case TimerSignal ⇒
      processAction(timerSignal(System.currentTimeMillis(), state))
    case Received(encodedResponse) ⇒
      val frame: Frame = Codec[Frame].decodeValidValue(BitVector.view(encodedResponse.toArray))
      processAction(handleNetworkFrames(frame, state))
    case Terminated(_) ⇒
      context unwatch state.tcpConnection
      state = state.setTCPManager(tcpManagerActor)
      state = state.resetTimerTask
      processAction(connectionClosed())
      context become notConnected
    case _: ConnectionClosed ⇒
      context unwatch state.tcpConnection
      state = state.setTCPManager(tcpManagerActor)
      state = state.resetTimerTask
      processAction(connectionClosed())
      context become notConnected
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
        state.tcpConnection ! Write(ByteString(encodedFrame.toByteArray))
      case ForciblyCloseTransport ⇒
        state.tcpConnection ! Abort
    }
  }
}

