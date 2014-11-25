package net.sigusr.frames

import scodec.bits.BitVector
import scodec.{Err, Codec}
import scodec.codecs._

import scalaz.\/

class CaseEnumCodec[T <: CaseEnum](codec: Codec[Int])(implicit fromEnum: Function[Int, \/[Err, T]]) extends Codec[T] {

   override def decode(bits: BitVector): \/[Err, (BitVector, T)] =
     codec.decode(bits) flatMap {
       (b: BitVector, i: Int) => fromEnum(i) flatMap {
         (m: T) => \/.right((b, m))
       }
     }

   override def encode(value: T): \/[Err, BitVector] = codec.encode(value.enum)
 }
