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

import scodec.Attempt._
import scodec._
import scodec.bits.{ BitVector, _ }
import scodec.codecs._

final class RemainingLengthCodec extends Codec[Int] {

  val MinValue = 0
  val MaxValue = 268435455

  def sizeBound = SizeBound.bounded(8, 32)

  def decode(bits: BitVector): Attempt[DecodeResult[Int]] = {
    @annotation.tailrec
    def decodeAux(step: Attempt[DecodeResult[Int]], factor: Int, depth: Int, value: Int): Attempt[DecodeResult[Int]] =
      if (depth == 4) failure(Err("The remaining length must be 4 bytes long at most"))
      else step match {
        case f: Failure ⇒ f
        case Successful(d) ⇒
          if ((d.value & 128) == 0) successful(DecodeResult(value + (d.value & 127) * factor, d.remainder))
          else decodeAux(uint8.decode(d.remainder), factor * 128, depth + 1, value + (d.value & 127) * factor)
      }
    decodeAux(uint8.decode(bits), 1, 0, 0)
  }

  def encode(value: Int): Attempt[BitVector] = {
    @annotation.tailrec
    def encodeAux(value: Int, digit: Int, bytes: ByteVector): ByteVector =
      if (value == 0) bytes :+ digit.asInstanceOf[Byte]
      else encodeAux(value / 128, value % 128, bytes :+ (digit | 0x80).asInstanceOf[Byte])
    if (value < MinValue || value > MaxValue) failure(Err(s"The remaining length must be in the range [$MinValue..$MaxValue], $value is not valid"))
    else successful(BitVector(encodeAux(value / 128, value % 128, ByteVector.empty)))
  }
}
