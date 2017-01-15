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

package net.sigusr.mqtt.impl.frames

import scodec.bits.{ BitVector, _ }
import scodec.codecs._

case class ConnectVariableHeader(
    userNameFlag: Boolean,
    passwordFlag: Boolean,
    willRetain: Boolean,
    willQoS: Int,
    willFlag: Boolean,
    cleanSession: Boolean,
    keepAliveTimer: Int
) {
}

object ConnectVariableHeader {

  /**
   * This is the, once for all encoded, protocol name [MQIsdp] and protocol version [3]
   */
  val connectVariableHeaderFixedBytes: BitVector = BitVector(hex"00064D514973647003")

  implicit val connectVariableHeaderCodec = (
    constant(connectVariableHeaderFixedBytes) :~>:
    bool ::
    bool ::
    bool ::
    qosCodec ::
    bool ::
    bool ::
    ignore(1) :~>:
    keepAliveCodec
  ).as[ConnectVariableHeader]
}