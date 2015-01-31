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
import net.sigusr.mqtt.impl.protocol.Registers._
import scodec.Codec
import scodec.bits.BitVector

import scala.concurrent.duration.{ FiniteDuration, _ }
import scalaz.State

private[protocol] case object TimerSignal

abstract class Transport(mqttBrokerAddress: InetSocketAddress) extends Actor with ActorLogging { this: Protocol ⇒

  import akka.io.Tcp.{ Abort ⇒ TcpAbort, CommandFailed ⇒ TcpCommandFailed, Connect ⇒ TcpConnect, Connected ⇒ TcpConnected, ConnectionClosed ⇒ TcpConnectionClosed, Received ⇒ TcpReceived, Register ⇒ TcpRegister, Write ⇒ TcpWrite }
  import context.dispatcher

  var registers: Registers = Registers(client = context.parent, tcpManager = tcpManagerActor)

  def tcpManagerActor: ActorRef

  def receive: Receive = notConnected

  private def notConnected: Receive = LoggingReceive {
    case Status ⇒
      registers = sendToClient(Disconnected).exec(registers)
    case c: Connect ⇒
      registers = sendToTcpManager(TcpConnect(mqttBrokerAddress)).exec(registers)
      context become connecting(handleApiMessages(c))
  }

  private def connecting(pendingActions: Action): Receive = LoggingReceive {
    case Status ⇒
      registers = sendToClient(Disconnected).exec(registers)
    case TcpCommandFailed(_: TcpConnect) ⇒
      registers = (for {
        _ ← setTCPManager(sender())
        _ ← processAction(transportNotReady())
      } yield ()).exec(registers)
      context become notConnected
    case TcpConnected(_, _) ⇒
      registers = (for {
        _ ← setTCPManager(sender())
        _ ← sendToTcpManager(TcpRegister(self))
        _ ← processAction(pendingActions)
        _ ← watchTcpManager
      } yield ()).exec(registers)
      context become connected
  }

  private def connected: Receive = LoggingReceive {
    case message: APICommand ⇒
      registers = processAction(handleApiMessages(message)).exec(registers)
    case TimerSignal ⇒
      registers = (for {
        actions ← timerSignal(System.currentTimeMillis())
        _ ← processAction(actions)
      } yield ()).exec(registers)
    case TcpReceived(encodedResponse) ⇒
      val frame: Frame = Codec[Frame].decodeValidValue(BitVector.view(encodedResponse.toArray))
      registers = (for {
        actions ← handleNetworkFrames(frame)
        _ ← processAction(actions)
      } yield ()).exec(registers)
    case Terminated(_) | _: TcpConnectionClosed ⇒
      registers = (for {
        _ ← unwatchTcpManager
        _ ← setTCPManager(tcpManagerActor)
        _ ← resetTimerTask
        _ ← processAction(connectionClosed())
      } yield ()).exec(registers)
      context become notConnected
  }

  private def processAction(action: Action): RegistersState[Unit] = {
    action match {
      case Sequence(actions) ⇒
        if (actions.isEmpty) State { x ⇒ (x, ()) }
        else for {
          _ ← processAction(actions.head)
          _ ← processAction(Sequence(actions.tail))
        } yield ()
      case SetKeepAlive(keepAlive) ⇒
        setTimeOut(keepAlive)
      case StartPingRespTimer(timeout) ⇒
        setTimerTask(context.system.scheduler.scheduleOnce(FiniteDuration(timeout, MILLISECONDS), self, TimerSignal))
      case SetPendingPingResponse(isPending) ⇒
        setPingResponsePending(isPending)
      case SendToClient(message) ⇒
        sendToClient(message)
      case SendToNetwork(frame) ⇒
        val encodedFrame = Codec[Frame].encodeValid(frame)
        for {
          _ ← sendToTcpManager(TcpWrite(ByteString(encodedFrame.toByteArray)))
          _ ← setLastSentMessageTimestamp(System.currentTimeMillis())
        } yield ()
      case ForciblyCloseTransport ⇒
        sendToTcpManager(TcpAbort)
    }
  }
}

