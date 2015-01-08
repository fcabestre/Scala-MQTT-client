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

package net.sigusr.mqtt.api

sealed trait MQTTAPIMessage

case object MQTTNotReady extends MQTTAPIMessage
case object MQTTReady extends MQTTAPIMessage
case class MQTTConnect(clientId : String,
                   keepAlive : Int = DEFAULT_KEEP_ALIVE,
                   cleanSession : Boolean = true,
                   topic : Option[String] = None,
                   message : Option[String] = None,
                   user : Option[String] = None,
                   password : Option[String] = None) extends MQTTAPIMessage {
  require(keepAlive >= 0 && keepAlive < 65636)
}

case object MQTTConnected extends MQTTAPIMessage
case class MQTTConnectionFailure(reason : MQTTConnectionFailureReason) extends MQTTAPIMessage
case object MQTTDisconnect extends MQTTAPIMessage
case object MQTTDisconnected extends MQTTAPIMessage
case class MQTTWrongClientMessage(message : MQTTAPIMessage) extends MQTTAPIMessage
case class MQTTPublish(topic: String, payload: Vector[Byte], qos: QualityOfService = AtMostOnce, messageExchangeId: Option[MQTTMessageId] = None, dup : Boolean = false, retain: Boolean = false) extends MQTTAPIMessage {
  require(qos == AtMostOnce || messageExchangeId.isDefined)
}
case class MQTTPublished(messageExchangeId: MQTTMessageId) extends MQTTAPIMessage
case class MQTTSubscribe(topics: Vector[(String, QualityOfService)], messageExchangeId: MQTTMessageId) extends MQTTAPIMessage
case class MQTTSubscribed(topicResults: Vector[QualityOfService], messageExchangeId: MQTTMessageId) extends MQTTAPIMessage
case class MQTTUnsubscribe(topics : Vector[String], messageExchangeId: MQTTMessageId)
case class MQTTUnsubscribed(messageExchangeId: MQTTMessageId)
case class MQTTMessage(topic: String, payload: Vector[Byte]) extends MQTTAPIMessage
