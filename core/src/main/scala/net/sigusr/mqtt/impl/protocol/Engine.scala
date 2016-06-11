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
import akka.io.Tcp
import akka.util.ByteString
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames.Frame
import net.sigusr.mqtt.impl.protocol.Registers._
import scodec.Err.InsufficientBits
import scodec.bits.BitVector
import scodec.{ Codec, DecodeResult, Err }

import scala.concurrent.duration.{ FiniteDuration, _ }
import scalaz.State

private[protocol] case object TimerSignal

abstract class Engine(mqttBrokerAddress: InetSocketAddress) extends Actor with Handlers with ActorLogging {

  import context.dispatcher

  type RegistersManagerBody = Any ⇒ RegistersState[Unit]

  var registers: Registers = Registers(client = context.parent, tcpManager = tcpManagerActor)

  def tcpManagerActor: ActorRef

  def receive: Receive = notConnected

  private def registersManager(body: ⇒ RegistersManagerBody): Receive = {
    case m ⇒ registers = body(m).exec(registers)
  }

  private def notConnected: Receive = LoggingReceive {
    registersManager {
      case Status ⇒
        sendToClient(Disconnected)
      case c: Connect ⇒
        for {
          _ ← sendToTcpManager(Tcp.Connect(mqttBrokerAddress))
          actions ← handleApiConnect(c)
        } yield context become connecting(actions)
      case _: APICommand ⇒
        sendToClient(Error(NotConnected))
    }
  }

  private def connecting(pendingActions: Action): Receive = LoggingReceive {
    registersManager {
      case Status ⇒
        sendToClient(Disconnected)
      case _: APICommand ⇒
        sendToClient(Error(NotConnected))
      case Tcp.CommandFailed(_: Tcp.Connect) ⇒
        for {
          _ ← setTCPManager(sender())
          _ ← processAction(transportNotReady())
        } yield context become notConnected
      case Tcp.Connected(_, _) ⇒
        for {
          _ ← setTCPManager(sender())
          _ ← sendToTcpManager(Tcp.Register(self))
          _ ← processAction(pendingActions)
          _ ← watchTcpManager
        } yield context become connected
    }
  }

  private def connected: Receive = LoggingReceive {
    registersManager {
      case message: APICommand ⇒
        for {
          actions ← handleApiCommand(message)
          _ ← processAction(actions)
        } yield ()
      case TimerSignal ⇒
        for {
          actions ← timerSignal(System.currentTimeMillis())
          _ ← processAction(actions)
        } yield ()
      case Tcp.Received(encodedResponse) ⇒
        for {
          bits ← getRemainingBits(encodedResponse)
          _ ← decode(bits)
        } yield ()
      case Terminated(_) | _: Tcp.ConnectionClosed ⇒
        disconnect()
    }
  }

  private def decode(bits: BitVector): RegistersState[Unit] = {
    def onSuccess(d: DecodeResult[Frame]): RegistersState[Unit] = {
      for {
        actions ← handleNetworkFrames(d.value)
        _ ← processAction(actions)
        _ ← decode(d.remainder)
      } yield ()
    }
    def onError(bits: BitVector): Err ⇒ RegistersState[Unit] = {
      case _: InsufficientBits ⇒ setRemainingBits(bits)
      case _ ⇒ disconnect()
    }
    Codec[Frame].decode(bits).fold(onError(bits), onSuccess)
  }

  private def disconnect(): RegistersState[Unit] = {
    for {
      _ ← unwatchTcpManager
      _ ← setTCPManager(tcpManagerActor)
      _ ← resetTimerTask
      _ ← processAction(connectionClosed())
    } yield context become notConnected
  }

  private def processActionSeq(actions: Seq[Action]): RegistersState[Unit] =
    if (actions.isEmpty) State { x ⇒ (x, ()) }
    else for {
      _ ← processAction(actions.head)
      _ ← processActionSeq(actions.tail)
    } yield ()

  private def processAction(action: Action): RegistersState[Unit] = action match {
    case Sequence(actions) ⇒
      processActionSeq(actions)
    case SetKeepAlive(keepAlive) ⇒
      setTimeOut(keepAlive)
    case StartPingRespTimer(timeout) ⇒
      setTimerTask(context.system.scheduler.scheduleOnce(FiniteDuration(timeout, MILLISECONDS), self, TimerSignal))
    case SetPendingPingResponse(isPending) ⇒
      setPingResponsePending(isPending)
    case SendToClient(message) ⇒
      sendToClient(message)
    case SendToNetwork(frame) ⇒
      for {
        _ ← sendToTcpManager(Tcp.Write(ByteString(Codec[Frame].encode(frame).require.toByteArray)))
        _ ← setLastSentMessageTimestamp(System.currentTimeMillis())
      } yield ()
    case ForciblyCloseTransport ⇒
      sendToTcpManager(Tcp.Abort)
    case StoreSentInFlightFrame(id, frame) ⇒
      storeInFlightSentFrame(id, frame)
    case RemoveSentInFlightFrame(id) ⇒
      removeInFlightSentFrame(id)
    case StoreRecvInFlightFrameId(id) ⇒
      storeInFlightRecvFrame(id)
    case RemoveRecvInFlightFrameId(id) ⇒
      removeInFlightRecvFrame(id)
  }
}

