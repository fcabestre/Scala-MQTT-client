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

import net.sigusr.mqtt.SpecUtils._
import net.sigusr.mqtt.api._
import org.specs2.mutable._
import scodec.bits._
import scodec.{ Codec, DecodeResult, Err, SizeBound }

import scala.util.Random

object CodecSpec extends Specification {

  "A remaining length codec" should {

    "Provide its size bounds" in {
      remainingLengthCodec.sizeBound should_=== SizeBound.bounded(8, 32)
    }

    "Perform encoding of valid inputs" in {
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
      remainingLengthCodec.encode(-1) should failWith(Err(s"The remaining length must be in the range [0..268435455], -1 is not valid"))
      remainingLengthCodec.encode(268435455 + 1) should failWith(Err(s"The remaining length must be in the range [0..268435455], 268435456 is not valid"))
    }

    "Perform decoding of valid inputs" in {
      remainingLengthCodec.decode(hex"00".bits) should succeedWith(DecodeResult(0, BitVector.empty))
      remainingLengthCodec.decode(hex"7f".bits) should succeedWith(DecodeResult(127, BitVector.empty))
      remainingLengthCodec.decode(hex"8001".bits) should succeedWith(DecodeResult(128, BitVector.empty))
      remainingLengthCodec.decode(hex"ff7f".bits) should succeedWith(DecodeResult(16383, BitVector.empty))
      remainingLengthCodec.decode(hex"808001".bits) should succeedWith(DecodeResult(16384, BitVector.empty))
      remainingLengthCodec.decode(hex"ffff7f".bits) should succeedWith(DecodeResult(2097151, BitVector.empty))
      remainingLengthCodec.decode(hex"80808001".bits) should succeedWith(DecodeResult(2097152, BitVector.empty))
      remainingLengthCodec.decode(hex"ffffff7f".bits) should succeedWith(DecodeResult(268435455, BitVector.empty))
    }

    "Fail to decode certain input values" in {
      remainingLengthCodec.decode(hex"808080807f".bits) should failWith(Err("The remaining length must be 4 bytes long at most"))
      remainingLengthCodec.decode(hex"ffffff".bits) should failWith(Err.insufficientBits(8, 0))
    }
  }

  "A header codec" should {
    "Perform encoding of valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = true)
      Codec.encode(header) should succeedWith(bin"0011")
    }

    "Perform decoding of valid inputs" in {
      val header = Header(dup = true, ExactlyOnce.enum, retain = false)
      Codec[Header].decode(bin"1100110011") should succeedWith(DecodeResult(header, bin"110011"))
    }
  }

  "A connect variable header codec" should {
    "Perform encoding of valid inputs" in {

      import net.sigusr.mqtt.impl.frames.ConnectVariableHeader._

      val connectVariableHeader = ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce.enum, willRetain = false, passwordFlag = true, userNameFlag = true, keepAliveTimer = 1024)
      val res = connectVariableHeaderFixedBytes ++ bin"110001100000010000000000"
      Codec.encode(connectVariableHeader) should succeedWith(res)
    }

    "Perform decoding of valid inputs" in {

      import net.sigusr.mqtt.impl.frames.ConnectVariableHeader._

      val res = ConnectVariableHeader(cleanSession = false, willFlag = false, willQoS = AtLeastOnce.enum, willRetain = true, passwordFlag = false, userNameFlag = false, keepAliveTimer = 12683)
      Codec[ConnectVariableHeader].decode(connectVariableHeaderFixedBytes ++ bin"001010000011000110001011101010") should succeedWith(DecodeResult(res, bin"101010"))
    }
  }

  "A connect message codec should" should {
    "[0] Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = true, passwordFlag = true, willRetain = true, AtLeastOnce.enum, willFlag = true, cleanSession = true, 15)
      val connectMessage = ConnectFrame(header, connectVariableHeader, "clientId", Some("Topic"), Some("Message"), Some("User"), Some("Password"))

      val valid = Codec[Frame].encode(connectMessage).require
      Codec[Frame].decode(valid) should succeedWith(DecodeResult(connectMessage, bin""))
    }

    "[1] Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = true, passwordFlag = false, willRetain = true, AtLeastOnce.enum, willFlag = false, cleanSession = true, 15)
      val connectMessage = ConnectFrame(header, connectVariableHeader, "clientId", None, None, Some("User"), None)

      Codec[Frame].decode(Codec[Frame].encode(connectMessage).require) should succeedWith(DecodeResult(connectMessage, bin""))
    }

    "[2] Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = false, passwordFlag = false, willRetain = true, ExactlyOnce.enum, willFlag = false, cleanSession = false, 128)
      val connectMessage = ConnectFrame(header, connectVariableHeader, "clientId", None, None, None, None)

      Codec[Frame].decode(Codec[Frame].encode(connectMessage).require) should succeedWith(DecodeResult(connectMessage, bin""))
    }

    "Perform encoding and match a captured value" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = false, passwordFlag = false, willRetain = true, AtLeastOnce.enum, willFlag = true, cleanSession = false, 60)
      val connectMessage = ConnectFrame(header, connectVariableHeader, "test", Some("test/topic"), Some("test death"), None, None)

      val capture = BitVector(0x10, 0x2a, 0x00, 0x06, 0x4d, 0x51, 0x49, 0x73, 0x64, 0x70, 0x03, 0x2c, 0x00, 0x3c, 0x00, 0x04, 0x74, 0x65, 0x73, 0x74, 0x00, 0x0a, 0x74, 0x65, 0x73, 0x74, 0x2f, 0x74, 0x6f, 0x70, 0x69, 0x63, 0x00, 0x0a, 0x74, 0x65, 0x73, 0x74, 0x20, 0x64, 0x65, 0x61, 0x74, 0x68)
      Codec[Frame].encode(connectMessage) should succeedWith(capture)
    }
  }

  "A connack message codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connackFrame = ConnackFrame(header, 0)

      Codec[Frame].decode(Codec[Frame].encode(connackFrame).require) should succeedWith(DecodeResult(connackFrame, bin""))
    }

    "Perform decoding of captured values" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connackFrame = ConnackFrame(header, 0)

      Codec[Frame].decode(BitVector(0x20, 0x02, 0x00, 0x00)) should succeedWith(DecodeResult(connackFrame, bin""))
    }
  }

  "A topics codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      import net.sigusr.mqtt.impl.frames.SubscribeFrame._
      val topics = Vector(("topic0", AtMostOnce.enum), ("topic1", AtLeastOnce.enum), ("topic2", ExactlyOnce.enum))
      Codec[Vector[(String, Int)]].decode(Codec[Vector[(String, Int)]].encode(topics).require) should succeedWith(DecodeResult(topics, bin""))
    }
  }

  "A subscribe codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topics = Vector(("topic0", AtMostOnce.enum), ("topic1", AtLeastOnce.enum), ("topic2", ExactlyOnce.enum))
      val subscribeFrame = SubscribeFrame(header, 3, topics)
      Codec[Frame].decode(Codec[Frame].encode(subscribeFrame).require) should succeedWith(DecodeResult(subscribeFrame, bin""))
    }

    "Perform encoding and match a captured value" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topics = Vector(("topic", AtLeastOnce.enum))
      val subscribeFrame = SubscribeFrame(header, 1, topics)
      val capture = BitVector(0x82, 0x0a, 0x00, 0x01, 0x00, 0x05, 0x74, 0x6f, 0x70, 0x69, 0x63, 0x01)
      Codec[Frame].encode(subscribeFrame) should succeedWith(capture)
    }
  }

  "A suback codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val qos = Vector(AtMostOnce.enum, AtLeastOnce.enum, ExactlyOnce.enum)
      val subackFrame = SubackFrame(header, 3, qos)
      Codec[Frame].decode(Codec[Frame].encode(subackFrame).require) should succeedWith(DecodeResult(subackFrame, bin""))
    }
  }

  "An unsubscribe codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topics = Vector("topic0", "topic1")
      val unsubscribeFrame = UnsubscribeFrame(header, Random.nextInt(65536), topics)
      Codec[Frame].decode(Codec[Frame].encode(unsubscribeFrame).require) should succeedWith(DecodeResult(unsubscribeFrame, bin""))
    }
  }

  "An unsuback codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val unsubackFrame = UnsubackFrame(header, Random.nextInt(65536))
      Codec[Frame].decode(Codec[Frame].encode(unsubackFrame).require) should succeedWith(DecodeResult(unsubackFrame, bin""))
    }
  }

  "A disconnect message codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val disconnectFrame = DisconnectFrame(header)

      Codec[Frame].decode(Codec[Frame].encode(disconnectFrame).require) should succeedWith(DecodeResult(disconnectFrame, bin""))
    }
  }

  "A ping request message codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val pingReqFrame = PingReqFrame(header)

      Codec[Frame].decode(Codec[Frame].encode(pingReqFrame).require) should succeedWith(DecodeResult(pingReqFrame, bin""))
    }

    "Perform encoding and match a captured value" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectMessage = PingReqFrame(header)

      val capture = BitVector(0xc0, 0x00)
      Codec[Frame].encode(connectMessage) should succeedWith(capture)
    }
  }

  "A ping response message codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val pingRespFrame = PingRespFrame(header)

      Codec[Frame].decode(Codec[Frame].encode(pingRespFrame).require) should succeedWith(DecodeResult(pingRespFrame, bin""))
    }

    "Perform decoding of captured values" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val pingRespFrame = PingRespFrame(header)

      Codec[Frame].decode(BitVector(0xd0, 0x00)) should succeedWith(DecodeResult(pingRespFrame, bin""))
    }
  }

  "A publish message codec" should {
    "Perform round trip encoding/decoding of a valid input with a QoS greater than 0" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topic = "a/b"
      val publishFrame = PublishFrame(header, topic, 10, ByteVector("Hello world".getBytes))

      Codec[Frame].decode(Codec[Frame].encode(publishFrame).require) should succeedWith(DecodeResult(publishFrame, bin""))
    }

    "Perform round trip encoding/decoding of a valid input with a QoS equals to 0" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val topic = "a/b"
      val publishFrame = PublishFrame(header, topic, 0, ByteVector("Hello world".getBytes))

      Codec[Frame].decode(Codec[Frame].encode(publishFrame).require) should succeedWith(DecodeResult(publishFrame, bin""))
    }
  }

  "A message codec" should {
    "Fail if there is not enough bytes to decodde" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topic = "a/b"
      val publishFrame = PublishFrame(header, topic, 10, ByteVector(makeRandomByteVector(256)))

      val bitVector = Codec[Frame].encode(publishFrame).require
      val head = bitVector.take(56 * 8)
      Codec[Frame].decode(head) should failWith(Err.insufficientBits(2104, 424))
    }
  }
}
