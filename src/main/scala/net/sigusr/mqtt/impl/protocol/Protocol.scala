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

import akka.actor._
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames.{ConnackFrame, ConnectFrame, DisconnectFrame, _}
import net.sigusr.mqtt.impl.protocol.Transport.{InternalAPIMessage, PingRespTimeout, SendKeepAlive}
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.language.postfixOps

trait Protocol extends Transport {

  var messageCounter = 0
  def incrMessageCounter: Int = (messageCounter + 1) % 65535
  var pubMap = Map[Int, Int]()
  // keyed by 'messageCounter', each value represents an optional client message exchange ID, and the topics to subscribe
  var subMap = Map[Int, (Option[Int], Vector[String])]()

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
    case MQTTPublish(topic, qos, retain, payload, exchangeId) =>
      val header = Header(dup = false, qos, retain)
      messageCounter = incrMessageCounter
      if (qos == AtLeastOnce || qos == ExactlyOnce) {
        exchangeId foreach { id => pubMap += (messageCounter -> id) }
        List(SendToNetwork(PublishFrame(header, topic, MessageIdentifier(messageCounter), ByteVector(payload))))
      } else {
        // according to spec, if QOS == AtMostOnce, then the server will not send an Ack
        List(
          SendToClient(MQTTPublishSuccess(exchangeId)),
          SendToNetwork(PublishFrame(header, topic, MessageIdentifier(messageCounter), ByteVector(payload))))
      }
    case MQTTSubscribe(topics, exchangeId) =>
      val header = Header(dup = false, AtLeastOnce, retain = false)
      messageCounter = incrMessageCounter
      subMap += (messageCounter -> (exchangeId, topics.map(_._1)))
      List(SendToNetwork(SubscribeFrame(header, MessageIdentifier(messageCounter), topics)))
    case _ => List(SendToClient(MQTTWrongClientMessage))
  }

  def handleNetworkFrames(frame : Frame) : List[Action] = {
    frame match {
      case ConnackFrame(header, connackVariableHeader) =>
        List(StartKeepAliveTimer, SendToClient(MQTTConnected))
      case PingRespFrame(header) =>
        List(CancelPingResponseTimer)
      case PublishFrame(header, topic, messageIdentifier: MessageIdentifier, payload: ByteVector) =>
        List(SendToClient(MQTTMessage(topic, payload.toArray)))
      case PubackFrame(header, messageIdentifier) =>
        // QoS 1 response
        val apiResponse = completePublish(header, messageIdentifier)
        List(SendToClient(apiResponse))
      case PubrecFrame(header, messageIdentifier) =>
        // QoS 2 part 2
        List(SendToNetwork(PubrelFrame(header, messageIdentifier)))
      case PubcompFrame(header, messageIdentifier) =>
        // QoS 2 part 3
        val apiResponse = completePublish(header, messageIdentifier)
        List(SendToClient(apiResponse))
      case SubackFrame(header, messageIdentifier, topicResults) =>
        val apiResponse = {
          val clientInfo = subMap.getOrElse(messageIdentifier.identifier, (None, Vector()))
          // TODO - codec does not support subscribe failures yet
          MQTTSubscribeSuccess(clientInfo._1)
        }
        List(SendToClient(apiResponse))
      case _ => Nil
    }
  }

  private def completePublish(header: Header, messageIdentifier: MessageIdentifier): MQTTPublishSuccess = {
    if (pubMap.contains(messageIdentifier.identifier)) {
      val exId = pubMap get messageIdentifier.identifier
      pubMap -= messageIdentifier.identifier
      MQTTPublishSuccess(exId)
    } else {
      MQTTPublishSuccess(None)
    }
  }

  def handleInternalApiMessages(apiMessage: InternalAPIMessage): List[Action] = apiMessage match {
    case SendKeepAlive => 
      List(
        StartPingResponseTimer, 
        SendToNetwork(PingReqFrame(Header(dup = false, AtMostOnce, retain = false))))
    case PingRespTimeout => List(CloseTransport)
    case _ => Nil
  }

  def disconnected() : Action = SendToClient(MQTTDisconnected)

  def connectionClosed() : Action = SendToClient(MQTTDisconnected)

  def transportReady() : Action = SendToClient(MQTTReady)

  def transportNotReady() : Action = SendToClient(MQTTNotReady)
}

