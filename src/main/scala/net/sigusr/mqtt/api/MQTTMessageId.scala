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

class MQTTMessageId(val identifier : Int) extends AnyVal

object MQTTMessageId {

  def checkValue(value : Int): Boolean = value >= 0 && value < 65536

  def apply(value : Int): MQTTMessageId = {
    if (!checkValue(value))
      throw new IllegalArgumentException("The value of a message identifier must be in the range [0..65535]")
    new MQTTMessageId(value)
  }

  def unapply(identifier: MQTTMessageId) : Option[Int] = Some(identifier.identifier)
}