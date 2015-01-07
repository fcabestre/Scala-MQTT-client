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

package net.sigusr.mqtt.impl

import scodec.Codec
import scodec.bits._
import scodec.codecs._

package object frames {

  type Topic = (String, QualityOfService)
  type Topics = Vector[Topic]

  def headerDependentMessageIdentifierCodec(header : Header) : Codec[MessageIdentifier] =
    if (header.qos != AtMostOnce) MessageIdentifier.messageIdentifierCodec
    else provide(MessageIdentifier(0))


  val qualityOfServiceCodec = new CaseEnumCodec[QualityOfService](uint2)
  val remainingLengthCodec = new RemainingLengthCodec
  val stringCodec = variableSizeBytes(uint16, utf8)
  val zeroLength = bin"00000000"
}
