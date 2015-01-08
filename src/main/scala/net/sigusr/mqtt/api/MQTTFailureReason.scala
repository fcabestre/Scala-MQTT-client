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

import scala.annotation.switch

sealed trait MQTTConnectionFailureReason extends CaseEnum
case object BadProtocolVersion extends MQTTConnectionFailureReason { val enum = 1 }
case object IdentifierRejected extends MQTTConnectionFailureReason { val enum = 2 }
case object ServerUnavailable extends MQTTConnectionFailureReason { val enum = 3 }
case object BadUserNameOrPassword extends MQTTConnectionFailureReason { val enum = 4 }
case object NotAuthorized extends MQTTConnectionFailureReason { val enum = 5 }

object MQTTConnectionFailureReason {
  def fromEnum(enum: Int): MQTTConnectionFailureReason =
    (enum: @switch) match {
      case 1 => BadProtocolVersion
      case 2 => IdentifierRejected
      case 3 => ServerUnavailable
      case 4 => BadUserNameOrPassword
      case 5 => NotAuthorized
      case _ => throw new IllegalArgumentException("Connect failure reason encoded value should be in the range [1..5]")
    }
}
