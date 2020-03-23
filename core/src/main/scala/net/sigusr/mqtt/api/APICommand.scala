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

import net.sigusr.mqtt.api.QualityOfService.AtMostOnce

sealed trait APICommand

case class Will(retain: Boolean, qos: QualityOfService, topic: String, message: String)

case class Connect(
  clientId: String,
  keepAlive: Int = DEFAULT_KEEP_ALIVE,
  cleanSession: Boolean = true,
  will: Option[Will] = None,
  user: Option[String] = None,
  password: Option[String] = None) extends APICommand {
  assert(keepAlive >= 0 && keepAlive < 65636, "Keep alive value should be in the range [0..65535]")
  assert(user.isDefined || password.isEmpty, "A password cannot be provided without user")
}

case object Status extends APICommand

case object Disconnect extends APICommand

case class Publish(
  topic: String,
  payload: Vector[Byte],
  qos: QualityOfService = AtMostOnce,
  messageId: Option[MessageId] = None,
  retain: Boolean = false) extends APICommand {
  assert(qos == AtMostOnce || messageId.isDefined, "A message identifier must be provided when QoS is greater than 0")
}

case class Subscribe(topics: Vector[(String, QualityOfService)], messageId: MessageId) extends APICommand

case class Unsubscribe(topics: Vector[String], messageId: MessageId) extends APICommand