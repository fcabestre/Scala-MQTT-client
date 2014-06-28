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

package org.ultimo.messages

import shapeless.Iso

case class ConnectVariableHeader(cleanSession : Boolean,
                                 willFlag : Boolean,
                                 willQoS : QualityOfService,
                                 willRetain : Boolean,
                                 passwordFlag : Boolean,
                                 userNameFlag : Boolean,
                                 keepAliveTimer : Int) {

  require(keepAliveTimer >= 0 && keepAliveTimer <= 65535)

  val protocolName = "MQIsdp"
  val protocolVersion = 0x03
}

object ConnectVariableHeader {
  implicit val hlistIso = Iso.hlist(ConnectVariableHeader.apply _, ConnectVariableHeader.unapply _)
}