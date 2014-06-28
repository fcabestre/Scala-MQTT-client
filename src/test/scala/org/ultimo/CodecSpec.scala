package org.ultimo

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

import org.specs2.mutable._
import scodec.Codec

class CodecSpec extends Specification {

  "A remaining length codec" should {
    "Perform encoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import scodec.bits._

      remainingLengthCodec.encode(0) should succeedWith(hex"00".bits)
      remainingLengthCodec.encode(127) should succeedWith(hex"7f".bits)
      remainingLengthCodec.encode(128) should succeedWith(hex"8001".bits)
      remainingLengthCodec.encode(16383) should succeedWith(hex"ff7f".bits)
      remainingLengthCodec.encode(16384) should succeedWith(hex"808001".bits)
      remainingLengthCodec.encode(2097151) should succeedWith(hex"ffff7f".bits)
      remainingLengthCodec.encode(2097152) should succeedWith(hex"80808001".bits)
      remainingLengthCodec.encode(268435455) should succeedWith(hex"ffffff7f".bits)

    }

    "Fail to encode certain input values" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._

      remainingLengthCodec.encode(-1) should failWith(s"The remaining length must be in the range [0..268435455], -1 is not valid")
      remainingLengthCodec.encode(268435455 + 1) should failWith(s"The remaining length must be in the range [0..268435455], 268435456 is not valid")

    }

    "Perform decoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import scodec.bits._

      remainingLengthCodec.decode(hex"00".bits) should succeedWith((BitVector.empty, 0))
      remainingLengthCodec.decode(hex"7f".bits) should succeedWith((BitVector.empty, 127))
      remainingLengthCodec.decode(hex"8001".bits) should succeedWith((BitVector.empty, 128))
      remainingLengthCodec.decode(hex"ff7f".bits) should succeedWith((BitVector.empty, 16383))
      remainingLengthCodec.decode(hex"808001".bits) should succeedWith((BitVector.empty, 16384))
      remainingLengthCodec.decode(hex"ffff7f".bits) should succeedWith((BitVector.empty, 2097151))
      remainingLengthCodec.decode(hex"80808001".bits) should succeedWith((BitVector.empty, 2097152))
      remainingLengthCodec.decode(hex"ffffff7f".bits) should succeedWith((BitVector.empty, 268435455))

    }

    "Fail to decode certain input values" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import scodec.bits._

      remainingLengthCodec.decode(hex"808080807f".bits) should failWith("The remaining length must be 4 bytes long at most")
    }
  }

  "A header codec" should {
    "Perform encoding of valid input" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{AtLeastOnce, CONNACK, Header}
      import scodec.bits._

      val header = Header(CONNACK, dup = false, AtLeastOnce, retain = true, 5)
      Codec.encode(header) should succeedWith(bin"0010001100000101")
    }

    "Perform decoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{ExactlyOnce, Header, PUBREL}
      import scodec.bits._

      val header = Header(PUBREL, dup = true, ExactlyOnce, retain = false, 128)
      Codec[Header].decode(bin"011011001000000000000001110011") should succeedWith((bin"110011", header))
    }
  }

  "A connect variable header codec" should {
    "Perform encoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{AtMostOnce, ConnectVariableHeader}
      import scodec.bits._

      val connectVariableHeader = ConnectVariableHeader(cleanSession = true, willFlag = true, AtMostOnce, willRetain = false, passwordFlag = true, userNameFlag = true, 1024)
      val res = connectVariableHeaderFixedBytes ++ bin"110001100000010000000000"
      Codec.encode(connectVariableHeader) should succeedWith(res)
    }

    "Perform decoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{AtLeastOnce, ConnectVariableHeader}
      import scodec.bits._

      val res = ConnectVariableHeader(cleanSession = false, willFlag = false, AtLeastOnce, willRetain = true, passwordFlag = false, userNameFlag = false, 12683)
      Codec[ConnectVariableHeader].decode(connectVariableHeaderFixedBytes ++ bin"000110000011000110001011101010") should succeedWith((bin"101010",res))
    }
  }
}

