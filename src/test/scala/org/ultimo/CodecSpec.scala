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

      val header = Header(CONNACK, dup = false, AtLeastOnce, retain = true)
      Codec.encode(header) should succeedWith(bin"00100011")
    }

    "Perform decoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{ExactlyOnce, Header, PUBREL}
      import scodec.bits._

      val header = Header(PUBREL, dup = true, ExactlyOnce, retain = false)
      Codec[Header].decode(bin"01101100110011") should succeedWith((bin"110011", header))
    }
  }

  "A connect variable header codec" should {
    "Perform encoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{AtMostOnce, ConnectVariableHeader}
      import scodec.bits._

      val connectVariableHeader = ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce, willRetain = false, passwordFlag = true, userNameFlag = true, keepAliveTimer = 1024)
      val res = connectVariableHeaderFixedBytes ++ bin"110001100000010000000000"
      Codec.encode(connectVariableHeader) should succeedWith(res)
    }

    "Perform decoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{AtLeastOnce, ConnectVariableHeader}
      import scodec.bits._

      val res = ConnectVariableHeader(cleanSession = false, willFlag = false, willQoS = AtLeastOnce, willRetain = true, passwordFlag = false, userNameFlag = false, keepAliveTimer = 12683)
      Codec[ConnectVariableHeader].decode(connectVariableHeaderFixedBytes ++ bin"001010000011000110001011101010") should succeedWith((bin"101010", res))
    }
  }

  "A connect message codec should" should {
    "[0] Perform round trip encoding/decoding of a valid input" in {
      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{CONNECT, Header, AtMostOnce, ConnectVariableHeader, ConnectMessage, AtLeastOnce}
      import scodec.bits._

      val header = Header(CONNECT, dup = false, AtMostOnce, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = true, passwordFlag = true, willRetain = true, AtLeastOnce, willFlag = true, cleanSession = true, 15)
      val connectMessage = ConnectMessage(header, connectVariableHeader, "clientId", Some("Topic"), Some("Message"), Some("User"), Some("Password"))

      Codec[ConnectMessage].decode(Codec.encodeValid(connectMessage)) should succeedWith((bin"", connectMessage))
    }

    "[1] Perform round trip encoding/decoding of a valid input" in {
      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{CONNECT, Header, AtMostOnce, ConnectVariableHeader, ConnectMessage, AtLeastOnce}
      import scodec.bits._

      val header = Header(CONNECT, dup = false, AtMostOnce, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = true, passwordFlag = false, willRetain = true, AtLeastOnce, willFlag = false, cleanSession = true, 15)
      val connectMessage = ConnectMessage(header, connectVariableHeader, "clientId", None, None, Some("User"), None)

      Codec[ConnectMessage].decode(Codec.encodeValid(connectMessage)) should succeedWith((bin"", connectMessage))
    }

    "[2] Perform round trip encoding/decoding of a valid input" in {
      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{CONNECT, Header, AtMostOnce, ConnectVariableHeader, ConnectMessage, ExactlyOnce}
      import scodec.bits._

      val header = Header(CONNECT, dup = false, AtMostOnce, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = false, passwordFlag = false, willRetain = true, ExactlyOnce, willFlag = false, cleanSession = false, 128)
      val connectMessage = ConnectMessage(header, connectVariableHeader, "clientId", None, None, None, None)

      Codec[ConnectMessage].decode(Codec.encodeValid(connectMessage)) should succeedWith((bin"", connectMessage))
    }
  }

  "A connack variable header codec" should {
    "Perform encoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{ConnackVariableHeader, ConnectionRefused2}
      import scodec.bits._

      val connackVariableHeader = ConnackVariableHeader(ConnectionRefused2)
      Codec.encode(connackVariableHeader) should succeedWith(BitVector(hex"0002"))
    }

    "Perform decoding of valid inputs" in {

      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{ConnackVariableHeader, ConnectionRefused5}
      import scodec.bits._

      val res = ConnackVariableHeader(ConnectionRefused5)
      Codec[ConnackVariableHeader].decode(BitVector(hex"000503")) should succeedWith((bin"00000011", res))
    }
  }

  "A connack message codec should" should {
    "Perform round trip encoding/decoding of a valid input" in {
      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{AtMostOnce, CONNACK, Header, ConnackVariableHeader, ConnectionAccepted, ConnackMessage}
      import scodec.bits._

      val header = Header(CONNACK, dup = false, AtMostOnce, retain = false)
      val connackVariableHeader = ConnackVariableHeader(ConnectionAccepted)
      val connectMessage = ConnackMessage(header, connackVariableHeader)

      Codec[ConnackMessage].decode(Codec.encodeValid(connectMessage)) should succeedWith((bin"", connectMessage))
    }
  }

  "A disconnect message codec should" should {
    "Perform round trip encoding/decoding of a valid input" in {
      import org.ultimo.SpecUtils._
      import org.ultimo.codec.Codecs._
      import org.ultimo.messages.{DISCONNECT, Header, AtMostOnce, DisconnectMessage}
      import scodec.bits._

      val header = Header(DISCONNECT, dup = false, AtMostOnce, retain = false)
      val disconnectMessage = DisconnectMessage(header)

      Codec[DisconnectMessage].decode(Codec.encodeValid(disconnectMessage)) should succeedWith((bin"", disconnectMessage))
    }
  }
}
