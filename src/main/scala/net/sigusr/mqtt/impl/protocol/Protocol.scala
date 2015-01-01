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
import net.sigusr.mqtt.impl.frames.{ConnackFrame, ConnectFrame, DisconnectFrame, _}
import net.sigusr.mqtt.impl.protocol.Transport.{InternalAPIMessage, PingRespTimeout, SendKeepAlive}
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.language.postfixOps

trait Protocol {

  def handleApiMessages(apiMessage : MQTTAPIMessage) : List[Action] = apiMessage match {
    case MQTTConnect(clientId, keepAlive, cleanSession, topic, message, user, password) =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce, willFlag = false, cleanSession, keepAlive)
      List(
        SetKeepAliveValue(keepAlive seconds),
        SendToNetwork(ConnectFrame(header, variableHeader, clientId, topic, message, user, password)))
    case MQTTDisconnect =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      List(SendToNetwork(DisconnectFrame(header)))
    case MQTTPublish(topic, payload, qos @ AtMostOnce, messageId, retain, dup) =>
      val header = Header(dup, qos, retain)
      List(SendToNetwork(PublishFrame(header, topic, MessageIdentifier(messageId.getOrElse(0)), ByteVector(payload))))
    case MQTTPublish(topic, payload, qos, Some(messageId), retain, dup) =>
      val header = Header(dup, qos, retain)
      List(SendToNetwork(PublishFrame(header, topic, MessageIdentifier(messageId), ByteVector(payload))))
    case MQTTSubscribe(topics, messageId) =>
      val header = Header(dup = false, AtLeastOnce, retain = false)
      List(SendToNetwork(SubscribeFrame(header, MessageIdentifier(messageId), topics)))
    case m => List(SendToClient(MQTTWrongClientMessage(m)))
  }

  def handleNetworkFrames(frame : Frame) : List[Action] = {
    frame match {
      case ConnackFrame(header, connackVariableHeader) =>
        List(StartKeepAliveTimer, SendToClient(MQTTConnected))
      case PingRespFrame(header) =>
        List(CancelPingResponseTimer)
      case PublishFrame(header, topic, messageIdentifier, payload) =>
        List(SendToClient(MQTTMessage(topic, payload.toArray.to[Vector])))
      case PubackFrame(header, MessageIdentifier(messageId)) =>
        List(SendToClient(MQTTPublishSuccess(messageId)))
      case PubrecFrame(header, messageIdentifier) =>
        List(SendToNetwork(PubrelFrame(header, messageIdentifier)))
      case PubcompFrame(header, MessageIdentifier(messageId)) =>
        List(SendToClient(MQTTPublishSuccess(messageId)))
      case SubackFrame(header, messageIdentifier, topicResults) =>
        List(SendToClient(MQTTSubscribeSuccess(messageIdentifier.identifier, topicResults)))
      case _ => Nil
    }
  }

  def handleInternalApiMessages(apiMessage: InternalAPIMessage): List[Action] = apiMessage match {
    case SendKeepAlive => 
      List(
        StartPingResponseTimer, 
        SendToNetwork(PingReqFrame(Header(dup = false, AtMostOnce, retain = false))))
    case PingRespTimeout => List(CloseTransport)
  }

  def connectionClosed() : Action = SendToClient(MQTTDisconnected)

  def transportReady() : Action = SendToClient(MQTTReady)

  def transportNotReady() : Action = SendToClient(MQTTNotReady)
}

