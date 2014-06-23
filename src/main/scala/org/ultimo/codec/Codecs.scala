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

package org.ultimo.codec

import org.ultimo.messages.{CaseEnum, QualityOfService, MessageTypes}
import scodec._
import codecs._
import scalaz.{\/-, -\/, \/}
import scodec.bits.{ByteVector, BitVector}


final class RemainingLengthCodec extends Codec[Int] {

  val MinValue = 0
  val MaxValue = 268435455
  val MinBytes = 0
  val MaxBytes = 4

  def decode(bits: BitVector): String \/ (BitVector, Int) = {
    @annotation.tailrec
    def decodeAux(step: \/[String, (BitVector, Int)], factor: Int, depth: Int, value: Int): \/[String, (BitVector, Int)] =
      if (depth == 4) \/.left("The remaining length must be 4 bytes long at most")
      else step match {
        case e @ -\/(_) => e
        case \/-((b, d)) =>
          if ((d & 128) == 0) \/.right((b, value + (d & 127) * factor))
          else decodeAux(uint8.decode(b), factor * 128, depth + 1, value + (d & 127) * factor)

      }
    decodeAux(uint8.decode(bits), 1, 0, 0)
  }

  def encode(value: Int) = {
    @annotation.tailrec
    def encodeAux(value: Int, digit: Int, bytes: ByteVector): ByteVector =
      if (value == 0) bytes :+ digit.asInstanceOf[Byte]
      else encodeAux(value / 128, value % 128, bytes :+ (digit | 0x80).asInstanceOf[Byte])
    if (value < MinValue || value > MaxValue) \/.left(s"The remaining length must be in the range [$MinValue..$MaxValue], $value is not valid")
    else \/.right(BitVector(encodeAux(value / 128, value % 128, ByteVector.empty)))
  }
}

class CaseEnumCodec[T <: CaseEnum](codec : Codec[Int])(implicit fromEnum : Function[Int, \/[String, T]]) extends Codec[T] {

  override def decode(bits: BitVector): \/[String, (BitVector, T)] =
    codec.decode(bits) flatMap {
      (b: BitVector, i: Int) => fromEnum(i) flatMap {
        (m: T) => \/.right((b, m))
      }
    }

  override def encode(value: T): \/[String, BitVector] = codec.encode(value.enum)
}

object Codecs {

  import org.ultimo.messages.Header

  val messageTypeCodec = new CaseEnumCodec[MessageTypes](uint4)
  val qualityOfServiceCodec = new CaseEnumCodec[QualityOfService](uint2)
  val remainingLengthCodec = new RemainingLengthCodec
  implicit val headerCodec = (messageTypeCodec :: bool :: qualityOfServiceCodec :: bool :: remainingLengthCodec).as[Header]
}