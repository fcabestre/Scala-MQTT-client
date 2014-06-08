package codec

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
    def decodeAux(step: \/[String, (BitVector, Int)], factor: Int, value: Int): \/[String, (BitVector, Int)] =
      step match {
        case e @ -\/(_) => e
        case \/-((b, d)) =>
          if ((d & 128) == 0) \/.right((b, value + (d & 127) * factor))
          else decodeAux(uint8.decode(b), factor * 128, value + (d & 127) * factor)

      }
    decodeAux(uint8.decode(bits), 1, 0)
  }

  def encode(value: Int) = {
    @annotation.tailrec
    def encodeAux(value: Int, digit: Int, bytes: ByteVector): ByteVector =
      if (value == 0) bytes :+ digit.asInstanceOf[Byte]
      else encodeAux(value / 128, value % 128, bytes :+ (digit | 0x80).asInstanceOf[Byte])
    if (value < MinValue || value > MaxValue) \/.left(s"$value must be in the range [$MinValue..$MaxValue]")
    \/.right(BitVector(encodeAux(value / 128, value % 128, ByteVector.empty)))
  }
}

/**
 *
 * @author Frédéric Cabestre
 */
object Codecs {
  import messages.Header

  val remainingLengthCodec = new RemainingLengthCodec
  implicit val headerCodec = (uint4 :: bool :: uint2 :: bool :: remainingLengthCodec).as[Header]
}
