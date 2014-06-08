/**
 *
 * @author Frédéric Cabestre
 */

import org.specs2.mutable._
import scodec.Codec

class CodecSpec extends Specification {

  "A remaining length codec" should {
    "Perform encodings" in {

      import codec.Codecs._
      import scodec.bits._
      import SpecUtils._

      remainingLengthCodec.encode(0) should succeedWith(hex"00".bits)
      remainingLengthCodec.encode(127) should succeedWith(hex"7f".bits)
      remainingLengthCodec.encode(128) should succeedWith(hex"8001".bits)
      remainingLengthCodec.encode(16383) should succeedWith(hex"ff7f".bits)
      remainingLengthCodec.encode(16384) should succeedWith(hex"808001".bits)
      remainingLengthCodec.encode(2097151) should succeedWith(hex"ffff7f".bits)
      remainingLengthCodec.encode(2097152) should succeedWith(hex"80808001".bits)
      remainingLengthCodec.encode(268435455) should succeedWith(hex"ffffff7f".bits)

    }

    "Fail on certain input values" in {
      pending
    }

    "Perform decodings" in {

      import codec.Codecs._
      import scodec.bits._
      import SpecUtils._

      remainingLengthCodec.decode(hex"00".bits) should succeedWith((BitVector.empty, 0))
      remainingLengthCodec.decode(hex"7f".bits) should succeedWith((BitVector.empty, 127))
      remainingLengthCodec.decode(hex"8001".bits) should succeedWith((BitVector.empty, 128))
      remainingLengthCodec.decode(hex"ff7f".bits) should succeedWith((BitVector.empty, 16383))
      remainingLengthCodec.decode(hex"808001".bits) should succeedWith((BitVector.empty, 16384))
      remainingLengthCodec.decode(hex"ffff7f".bits) should succeedWith((BitVector.empty, 2097151))
      remainingLengthCodec.decode(hex"80808001".bits) should succeedWith((BitVector.empty, 2097152))
      remainingLengthCodec.decode(hex"ffffff7f".bits) should succeedWith((BitVector.empty, 268435455))

    }
  }

  "A header encoding" should {
    "Perform encodings" in {

      import messages.Header
      import codec.Codecs._
      import scodec.bits._
      import SpecUtils._

      val header = Header(2, dup = false, 1, retain = true, 5)
      Codec.encode(header) should succeedWith(bin"0010001100000101")
    }

    "Perform decodings" in {

      import messages.Header
      import codec.Codecs._
      import scodec.bits._
      import SpecUtils._

      val header = Header(6, dup = true, 2, retain = false, 128)
      Codec[Header].decode(bin"011011001000000000000001110011") should succeedWith((bin"110011", header))
    }
  }
}

