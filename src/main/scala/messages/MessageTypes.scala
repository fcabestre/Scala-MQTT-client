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

package messages

sealed trait MessageTypes { def enum : Int }

case object CONNECT extends MessageTypes { val enum = 1 }
case object CONNACK extends MessageTypes { val enum = 2 }
case object PUBLISH extends MessageTypes { val enum = 3 }
case object PUBACK extends MessageTypes { val enum = 4 }
case object PUBREC extends MessageTypes { val enum = 5 }
case object PUBREL extends MessageTypes { val enum = 6 }
case object PUBCOMP extends MessageTypes { val enum = 7 }
case object SUBSCRIBE extends MessageTypes { val enum = 8 }
case object SUBACK extends MessageTypes { val enum = 9 }
case object UNSUBSCRIBE extends MessageTypes { val enum = 10 }
case object UNSUBACK extends MessageTypes { val enum = 11 }
case object PINGREQ extends MessageTypes { val enum = 12 }
case object PINGRESP extends MessageTypes { val enum = 13 }
case object DISCONNECT extends MessageTypes { val enum = 14 }

object MessageTypes {
  def messageType(enum : Int) = ???
}