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

import net.sigusr.mqtt.api.ConnectionFailureReason.ServerNotResponding
import net.sigusr.mqtt.api.QualityOfService.{ AtLeastOnce, AtMostOnce, ExactlyOnce }
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames._
import net.sigusr.mqtt.impl.protocol.Registers.RegistersState
import scalaz.State._
import scodec.bits.ByteVector

trait Handlers {

  private val zeroId = MessageId(0)

  private[protocol] def handleApiConnect(connect: Connect): RegistersState[Action] = gets { registers =>
    val header = Header(dup = false, AtMostOnce.value)
    val retain = connect.will.fold(false)(_.retain)
    val qos = connect.will.fold(AtMostOnce.value)(_.qos.value)
    val topic = connect.will.map(_.topic)
    val message = connect.will.map(_.message)
    val variableHeader = ConnectVariableHeader(
      connect.user.isDefined,
      connect.password.isDefined,
      willRetain = retain,
      qos,
      willFlag = connect.will.isDefined,
      connect.cleanSession,
      connect.keepAlive)
    val actions = Seq(
      SetKeepAlive(connect.keepAlive.toLong * 1000),
      SendToNetwork(ConnectFrame(header, variableHeader, connect.clientId, topic, message, connect.user, connect.password)))
    Sequence(if (!connect.cleanSession) actions ++ registers.inFlightSentFrame.toSeq.map(p => SendToNetwork(p._2)) else actions)
  }

  private[protocol] def handleApiCommand(apiCommand: APICommand): RegistersState[Action] = gets { registers =>
    apiCommand match {
      case Connect(clientId, keepAlive, cleanSession, will, user, password) =>
        SendToClient(Error(AlreadyConnected))
      case Disconnect =>
        val header = Header(dup = false, AtMostOnce.value)
        SendToNetwork(DisconnectFrame(header))
      case Publish(topic, payload, qos, messageId, retain) if qos == AtMostOnce =>
        val header = Header(dup = false, qos.value, retain = retain)
        SendToNetwork(PublishFrame(header, topic, messageId.getOrElse(zeroId).identifier, ByteVector(payload)))
      case Publish(topic, payload, qos, Some(messageId), retain) =>
        val header = Header(dup = false, qos.value, retain = retain)
        val frame = PublishFrame(header, topic, messageId.identifier, ByteVector(payload))
        Sequence(Seq(
          StoreSentInFlightFrame(messageId.identifier, PublishFrame.dupLens.set(frame)(true)),
          SendToNetwork(frame)))
      case Subscribe(topics, messageId) =>
        val header = Header(dup = false, AtLeastOnce.value)
        SendToNetwork(SubscribeFrame(header, messageId.identifier, topics.map((v: (String, QualityOfService)) => (v._1, v._2.value))))
      case Unsubscribe(topics, messageId) =>
        val header = Header(dup = false, AtLeastOnce.value)
        SendToNetwork(UnsubscribeFrame(header, messageId.identifier, topics))
      case Status =>
        SendToClient(Connected)
    }
  }

  private[protocol] def handleNetworkFrames(frame: Frame): RegistersState[Action] = gets { registers =>
    frame match {
      case ConnackFrame(_, 0) =>
        if (registers.keepAlive == 0) SendToClient(Connected)
        else Sequence(Seq(
          StartPingRespTimer(registers.keepAlive),
          SendToClient(Connected)))
      case ConnackFrame(_, returnCode) =>
        SendToClient(ConnectionFailure(ConnectionFailureReason.withValue(returnCode)))
      case PingRespFrame(_) =>
        SetPendingPingResponse(isPending = false)
      case PublishFrame(header, topic, messageIdentifier, payload) =>
        val toClient = SendToClient(Message(topic, payload.toArray.toVector))
        header.qos match {
          case AtMostOnce.value =>
            toClient
          case AtLeastOnce.value =>
            Sequence(Seq(
              toClient,
              SendToNetwork(PubackFrame(Header(), messageIdentifier))))
          case ExactlyOnce.value =>
            if (registers.inFlightRecvFrame(messageIdentifier))
              Sequence(Seq(
                SendToNetwork(PubrecFrame(Header(), messageIdentifier))))
            else
              Sequence(Seq(
                toClient,
                StoreRecvInFlightFrameId(messageIdentifier),
                SendToNetwork(PubrecFrame(Header(), messageIdentifier))))
        }
      case PubackFrame(_, messageId) =>
        Sequence(Seq(
          RemoveSentInFlightFrame(messageId),
          SendToClient(Published(messageId))))
      case PubrecFrame(header, messageIdentifier) =>
        val pubrelFrame = PubrelFrame(header.copy(qos = 1), messageIdentifier)
        Sequence(Seq(
          RemoveSentInFlightFrame(messageIdentifier),
          StoreSentInFlightFrame(messageIdentifier.identifier, PubrelFrame.dupLens.set(pubrelFrame)(true)),
          SendToNetwork(pubrelFrame)))
      case PubrelFrame(header, messageIdentifier) =>
        Sequence(Seq(
          RemoveRecvInFlightFrameId(messageIdentifier),
          SendToNetwork(PubcompFrame(header.copy(qos = 0), messageIdentifier))))
      case PubcompFrame(_, messageId) =>
        Sequence(Seq(
          RemoveSentInFlightFrame(messageId),
          SendToClient(Published(messageId))))
      case SubackFrame(_, messageIdentifier, topicResults) =>
        SendToClient(Subscribed(topicResults.map(QualityOfService.withValue), messageIdentifier.identifier))
      case UnsubackFrame(_, messageId) =>
        SendToClient(Unsubscribed(messageId))
      case _ => ForciblyCloseTransport
    }
  }

  private[protocol] def timerSignal(currentTime: Long): RegistersState[Action] = gets { registers =>
    if (registers.isPingResponsePending)
      ForciblyCloseTransport
    else {
      val timeout = registers.keepAlive - currentTime + registers.lastSentMessageTimestamp
      if (timeout < 1000)
        Sequence(Seq(
          SetPendingPingResponse(isPending = true),
          StartPingRespTimer(registers.keepAlive),
          SendToNetwork(PingReqFrame(Header(dup = false, AtMostOnce.value)))))
      else
        StartPingRespTimer(timeout)
    }
  }

  private[protocol] def connectionClosed(): Action = SendToClient(Disconnected)

  private[protocol] def transportNotReady(): Action = SendToClient(ConnectionFailure(ServerNotResponding))
}

