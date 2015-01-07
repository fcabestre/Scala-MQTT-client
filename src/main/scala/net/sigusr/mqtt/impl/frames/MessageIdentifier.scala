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

import scodec.{Err, Codec}
import scodec.bits.BitVector
import scodec.codecs._

import scalaz.\/

class MessageIdentifier(val identifier : Int) extends AnyVal

object MessageIdentifier {
  def checkValue(value : Int): Boolean = value >= 0 && value < 65536

  def apply(value : Int): MessageIdentifier = {
    require(checkValue(value))
    new MessageIdentifier(value)
  }

  def unapply(identifier: MessageIdentifier) : Option[Int] = Some(identifier.identifier)

  implicit val messageIdentifierCodec = new MessageIdentifierCodec
}

class MessageIdentifierCodec extends Codec[MessageIdentifier] {

  override def decode(bits: BitVector): \/[Err, (BitVector, MessageIdentifier)] =
    uint16.decode(bits).map((bits : BitVector, int : Int) => (bits, new MessageIdentifier(int)))

  override def encode(value: MessageIdentifier): \/[Err, BitVector] = uint16.encode(value.identifier)
}

