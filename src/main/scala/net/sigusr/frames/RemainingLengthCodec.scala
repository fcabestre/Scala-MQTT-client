package net.sigusr.frames

import scodec.bits.{BitVector, _}
import scodec.codecs._
import scodec.{Codec, Err}

import scalaz.{-\/, \/, \/-}

final class RemainingLengthCodec extends Codec[Int] {

   val MinValue = 0
   val MaxValue = 268435455
   val MinBytes = 0
   val MaxBytes = 4

   def decode(bits: BitVector): Err \/ (BitVector, Int) = {
     @annotation.tailrec
     def decodeAux(step: \/[Err, (BitVector, Int)], factor: Int, depth: Int, value: Int): \/[Err, (BitVector, Int)] =
       if (depth == 4) \/.left(Err("The remaining length must be 4 bytes long at most"))
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
     if (value < MinValue || value > MaxValue) \/.left(Err(s"The remaining length must be in the range [$MinValue..$MaxValue], $value is not valid"))
     else \/.right(BitVector(encodeAux(value / 128, value % 128, ByteVector.empty)))
   }
 }
