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

import akka.actor.{ Actor, ActorLogging, ActorRef, Terminated }
import akka.event.LoggingReceive
import akka.util.ByteString
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames.Frame
import net.sigusr.mqtt.impl.protocol.State._
import scodec.Codec
import scodec.bits.BitVector

import scala.concurrent.duration.{ FiniteDuration, _ }

private[protocol] case object TimerSignal

abstract class Transport(mqttBrokerAddress: InetSocketAddress) extends Actor with ActorLogging { this: Protocol ⇒

  import akka.io.Tcp.{ Abort ⇒ TcpAbort, CommandFailed ⇒ TcpCommandFailed, Connect ⇒ TcpConnect, Connected ⇒ TcpConnected, ConnectionClosed ⇒ TcpConnectionClosed, Received ⇒ TcpReceived, Register ⇒ TcpRegister, Write ⇒ TcpWrite }
  import context.dispatcher

  var state: State = State(client = context.parent, tcpConnection = tcpManagerActor)

  def tcpManagerActor: ActorRef

  def receive: Receive = notConnected

  private def notConnected: Receive = LoggingReceive {
    case Status ⇒
      state = processAction(SendToClient(Disconnected), state)
    case c: Connect ⇒
      state.tcpConnection ! TcpConnect(mqttBrokerAddress)
      context become connecting(handleApiMessages(c))
  }

  private def connecting(pendingActions: Action): Receive = LoggingReceive {
    case Status ⇒
      state = processAction(SendToClient(Disconnected), state)
    case TcpCommandFailed(_: TcpConnect) ⇒
      state = setTCPManager(sender(), state)
      state = processAction(transportNotReady(), state)
      context become notConnected
    case TcpConnected(_, _) ⇒
      val connectionActor: ActorRef = sender()
      state = setTCPManager(connectionActor, state)
      state.tcpConnection ! TcpRegister(self)
      state = processAction(pendingActions, state)
      context watch connectionActor
      context become connected
  }

  private def connected: Receive = LoggingReceive {
    case message: APICommand ⇒
      state = processAction(handleApiMessages(message), state)
    case TimerSignal ⇒
      state = processAction(timerSignal(System.currentTimeMillis(), state), state)
    case TcpReceived(encodedResponse) ⇒
      val frame: Frame = Codec[Frame].decodeValidValue(BitVector.view(encodedResponse.toArray))
      state = processAction(handleNetworkFrames(frame, state), state)
    case Terminated(_) | _: TcpConnectionClosed ⇒
      context unwatch state.tcpConnection
      state = setTCPManager(tcpManagerActor, state)
      state = resetTimerTask(state)
      state = processAction(connectionClosed(), state)
      context become notConnected
  }

  private def processAction(action: Action, state: State): State = {
    action match {
      case Sequence(actions) ⇒ actions.foldLeft(state)((state: State, action: Action) ⇒ processAction(action, state))
      case SetKeepAlive(keepAlive) ⇒
        setTimeOut(keepAlive, state)
      case StartPingRespTimer(timeout) ⇒
        setTimerTask(context.system.scheduler.scheduleOnce(FiniteDuration(timeout, MILLISECONDS), self, TimerSignal), state)
      case SetPendingPingResponse(isPending) ⇒
        setPingResponsePending(isPending, state)
      case SendToClient(message) ⇒
        state.client ! message
        state
      case SendToNetwork(frame) ⇒
        val encodedFrame = Codec[Frame].encodeValid(frame)
        state.tcpConnection ! TcpWrite(ByteString(encodedFrame.toByteArray))
        setLastSentMessageTimestamp(System.currentTimeMillis(), state)
      case ForciblyCloseTransport ⇒
        state.tcpConnection ! TcpAbort
        state
    }
  }
}

