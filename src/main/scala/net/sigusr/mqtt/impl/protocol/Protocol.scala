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

import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames._
import scodec.bits.ByteVector

trait Protocol {

  private[protocol] def handleApiMessages(apiMessage : MQTTAPIMessage) : Action = apiMessage match {
    case MQTTConnect(clientId, keepAlive, cleanSession, topic, message, user, password) =>
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce.enum, willFlag = false, cleanSession, keepAlive)
      Sequence(
        Seq(SetKeepAliveValue(keepAlive * 1000),
        SendToNetwork(ConnectFrame(header, variableHeader, clientId, topic, message, user, password))))
    case MQTTDisconnect =>
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      SendToNetwork(DisconnectFrame(header))
    case MQTTPublish(topic, payload, qos, messageId, retain, dup) if qos == AtMostOnce =>
      val header = Header(dup, qos.enum, retain)
      SendToNetwork(PublishFrame(header, topic, messageId.getOrElse(zeroId).identifier, ByteVector(payload)))
    case MQTTPublish(topic, payload, qos, Some(messageId), retain, dup) =>
      val header = Header(dup, qos.enum, retain)
      SendToNetwork(PublishFrame(header, topic, messageId.identifier, ByteVector(payload)))
    case MQTTSubscribe(topics, messageId) =>
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      SendToNetwork(SubscribeFrame(header, messageId.identifier, topics.map((v:(String, QualityOfService)) => (v._1, v._2.enum))))
    case m => SendToClient(MQTTWrongClientMessage(m))
  }

  private[protocol] def handleNetworkFrames(frame : Frame, keepAliveValue : Long) : Action = {
    frame match {
      case ConnackFrame(header, 0) =>
        if (keepAliveValue == 0) SendToClient(MQTTConnected)
        else Sequence(Seq(StartTimer(keepAliveValue), SendToClient(MQTTConnected)))
      case ConnackFrame(header, returnCode) => SendToClient(MQTTConnectionFailure(MQTTConnectionFailureReason.fromEnum(returnCode)))
      case PingRespFrame(header) =>
        SetPendingPingResponse(isPending = false)
      case PublishFrame(header, topic, messageIdentifier, payload) =>
        SendToClient(MQTTMessage(topic, payload.toArray.to[Vector]))
      case PubackFrame(header, messageId) =>
        SendToClient(MQTTPublished(messageId))
      case PubrecFrame(header, messageIdentifier) =>
        SendToNetwork(PubrelFrame(header, messageIdentifier))
      case PubcompFrame(header, messageId) =>
        SendToClient(MQTTPublished(messageId))
      case SubackFrame(header, messageIdentifier, topicResults) =>
        SendToClient(MQTTSubscribed(topicResults.map(QualityOfService.fromEnum), messageIdentifier.identifier))
      case _ => Sequence()
    }
  }

  private[protocol] def timerSignal(currentTime: Long, keepAliveValue: Long, lastSentMessageTimestamp: Long, isPingResponsePending: Boolean): Action =
    if (isPingResponsePending)
      CloseTransport
    else {
      val timeout = keepAliveValue - currentTime + lastSentMessageTimestamp
      if (timeout < 1000)
        Sequence(Seq(
          SetPendingPingResponse(isPending = true),
          StartTimer(keepAliveValue),
          SendToNetwork(PingReqFrame(Header(dup = false, AtMostOnce.enum, retain = false)))))
      else
          StartTimer(timeout)
    }

  private[protocol] def connectionClosed() : Action = SendToClient(MQTTDisconnected)

  private[protocol] def transportReady() : Action = SendToClient(MQTTReady)

  private[protocol] def transportNotReady() : Action = SendToClient(MQTTNotReady)
}

