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

sealed trait APIMessage

case class Will(retain: Boolean, qos: QualityOfService, topic: String, message: String)

case class Connect(
    clientId: String,
    keepAlive: Int = DEFAULT_KEEP_ALIVE,
    cleanSession: Boolean = true,
    will: Option[Will] = None,
    user: Option[String] = None,
    password: Option[String] = None) extends APIMessage {
  assert(keepAlive >= 0 && keepAlive < 65636, "Keep alive value should be in the range [0..65535]")
  assert(user.isDefined || !password.isDefined, "A password cannot be provided without user")
}

case object Connected extends APIMessage
case class ConnectionFailure(reason: ConnectionFailureReason) extends APIMessage
case object Status extends APIMessage

case object Disconnect extends APIMessage
case object Disconnected extends APIMessage

case class Publish(
    topic: String,
    payload: Vector[Byte],
    qos: QualityOfService = AtMostOnce,
    messageId: Option[MessageId] = None,
    retain: Boolean = false) extends APIMessage {
  assert(qos == AtMostOnce || messageId.isDefined, "A message identifier must be provided when QoS is greater than 0")
}
case class Published(messageId: MessageId) extends APIMessage
case class Message(topic: String, payload: Vector[Byte]) extends APIMessage

case class Subscribe(topics: Vector[(String, QualityOfService)], messageId: MessageId) extends APIMessage
case class Subscribed(topicResults: Vector[QualityOfService], messageId: MessageId) extends APIMessage

case class Unsubscribe(topics: Vector[String], messageId: MessageId)
case class Unsubscribed(messageId: MessageId)

case class WrongClientMessage(message: APIMessage) extends APIMessage
case object Stop extends APIMessage