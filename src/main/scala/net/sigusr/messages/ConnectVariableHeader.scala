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

package net.sigusr.messages

import shapeless.Iso

case class ConnectVariableHeader(userNameFlag : Boolean,
                                 passwordFlag : Boolean,
                                 willRetain : Boolean,
                                 willQoS : QualityOfService,
                                 willFlag : Boolean,
                                 cleanSession : Boolean,
                                 keepAliveTimer : Int) {

  require((userNameFlag || !passwordFlag) && keepAliveTimer >= 0 && keepAliveTimer <= 65535)

  val protocolName = "MQIsdp"
  val protocolVersion = 0x03
}

object ConnectVariableHeader {
  implicit val hlistIso = Iso.hlist(ConnectVariableHeader.apply _, ConnectVariableHeader.unapply _)
}