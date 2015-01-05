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
import net.sigusr.mqtt.impl.protocol.Transport.{InternalAPIMessage, PingRespTimeout, SendKeepAlive}
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.language.postfixOps

trait Protocol {

  def handleApiMessages(apiMessage : MQTTAPIMessage) : Action = apiMessage match {
    case MQTTConnect(clientId, keepAlive, cleanSession, topic, message, user, password) =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce, willFlag = false, cleanSession, keepAlive)
      Sequence(
        Seq(SetKeepAliveValue(keepAlive seconds),
        SendToNetwork(ConnectFrame(header, variableHeader, clientId, topic, message, user, password))))
    case MQTTDisconnect =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      SendToNetwork(DisconnectFrame(header))
    case MQTTPublish(topic, payload, qos @ AtMostOnce, messageId, retain, dup) =>
      val header = Header(dup, qos, retain)
      SendToNetwork(PublishFrame(header, topic, MessageIdentifier(messageId.getOrElse(0)), ByteVector(payload)))
    case MQTTPublish(topic, payload, qos, Some(messageId), retain, dup) =>
      val header = Header(dup, qos, retain)
      SendToNetwork(PublishFrame(header, topic, MessageIdentifier(messageId), ByteVector(payload)))
    case MQTTSubscribe(topics, messageId) =>
      val header = Header(dup = false, AtLeastOnce, retain = false)
      SendToNetwork(SubscribeFrame(header, MessageIdentifier(messageId), topics))
    case m => SendToClient(MQTTWrongClientMessage(m))
  }

  def handleNetworkFrames(frame : Frame) : Action = {
    frame match {
      case ConnackFrame(header, connackVariableHeader) =>
        Sequence(Seq(StartKeepAliveTimer, SendToClient(MQTTConnected)))
      case PingRespFrame(header) =>
        CancelPingResponseTimer
      case PublishFrame(header, topic, messageIdentifier, payload) =>
        SendToClient(MQTTMessage(topic, payload.toArray.to[Vector]))
      case PubackFrame(header, MessageIdentifier(messageId)) =>
        SendToClient(MQTTPublished(messageId))
      case PubrecFrame(header, messageIdentifier) =>
        SendToNetwork(PubrelFrame(header, messageIdentifier))
      case PubcompFrame(header, MessageIdentifier(messageId)) =>
        SendToClient(MQTTPublished(messageId))
      case SubackFrame(header, messageIdentifier, topicResults) =>
        SendToClient(MQTTSubscribed(messageIdentifier.identifier, topicResults))
      case _ => Sequence()
    }
  }

  def handleInternalApiMessages(apiMessage: InternalAPIMessage): Action = apiMessage match {
    case SendKeepAlive => 
      Sequence(Seq(
        StartPingResponseTimer, 
        SendToNetwork(PingReqFrame(Header(dup = false, AtMostOnce, retain = false)))))
    case PingRespTimeout => CloseTransport
  }

  def connectionClosed() : Action = SendToClient(MQTTDisconnected)

  def transportReady() : Action = SendToClient(MQTTReady)

  def transportNotReady() : Action = SendToClient(MQTTNotReady)
}

