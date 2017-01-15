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

import net.sigusr.mqtt.impl.frames.ConnectVariableHeader._
import net.sigusr.mqtt.impl.frames.Header._
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._
import shapeless._

sealed trait Frame {
  def header: Header
}

case class ConnectFrame(
  header: Header,
  variableHeader: ConnectVariableHeader,
  clientId: String,
  topic: Option[String],
  message: Option[String],
  user: Option[String],
  password: Option[String]
) extends Frame

case class ConnackFrame(header: Header, returnCode: Int) extends Frame
case class PublishFrame(header: Header, topic: String, messageIdentifier: Int, payload: ByteVector) extends Frame
case class PubackFrame(header: Header, messageIdentifier: Int) extends Frame
case class PubrecFrame(header: Header, messageIdentifier: Int) extends Frame
case class PubrelFrame(header: Header, messageIdentifier: Int) extends Frame
case class PubcompFrame(header: Header, messageIdentifier: Int) extends Frame
case class SubscribeFrame(header: Header, messageIdentifier: Int, topics: Vector[(String, Int)]) extends Frame
case class SubackFrame(header: Header, messageIdentifier: Int, topics: Vector[Int]) extends Frame
case class UnsubscribeFrame(header: Header, messageIdentifier: Int, topics: Vector[String]) extends Frame
case class UnsubackFrame(header: Header, messageIdentifier: Int) extends Frame
case class PingReqFrame(header: Header) extends Frame
case class PingRespFrame(header: Header) extends Frame
case class DisconnectFrame(header: Header) extends Frame

object Frame {
  implicit val discriminated: Discriminated[Frame, Int] = Discriminated(uint4)
  implicit val frameCodec = Codec.coproduct[Frame].auto
}

object ConnectFrame {
  implicit val discriminator: Discriminator[Frame, ConnectFrame, Int] = Discriminator(1)
  implicit val codec: Codec[ConnectFrame] = (headerCodec :: variableSizeBytes(
    remainingLengthCodec,
    connectVariableHeaderCodec >>:~ { (hdr: ConnectVariableHeader) ⇒
      stringCodec ::
        conditional(hdr.willFlag, stringCodec) ::
        conditional(hdr.willFlag, stringCodec) ::
        conditional(hdr.userNameFlag, stringCodec) ::
        conditional(hdr.passwordFlag, stringCodec)
    }
  )).as[ConnectFrame]
}

object ConnackFrame {
  implicit val discriminator: Discriminator[Frame, ConnackFrame, Int] = Discriminator(2)
  implicit val codec: Codec[ConnackFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, bytePaddingCodec :~>: returnCodeCodec)).as[ConnackFrame]
}

object PublishFrame {
  val dupLens = lens[PublishFrame].header.dup
  implicit val discriminator: Discriminator[Frame, PublishFrame, Int] = Discriminator(3)
  implicit val codec: Codec[PublishFrame] = (headerCodec >>:~ {
    (hdr: Header) ⇒ variableSizeBytes(remainingLengthCodec, stringCodec :: (if (hdr.qos != 0) messageIdCodec else provide(0)) :: bytes)
  }).as[PublishFrame]
}

object PubackFrame {
  implicit val discriminator: Discriminator[Frame, PubackFrame, Int] = Discriminator(4)
  implicit val codec: Codec[PubackFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdCodec)).as[PubackFrame]
}

object PubrecFrame {
  implicit val discriminator: Discriminator[Frame, PubrecFrame, Int] = Discriminator(5)
  implicit val codec: Codec[PubrecFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdCodec)).as[PubrecFrame]
}

object PubrelFrame {
  val dupLens = lens[PubrelFrame].header.dup
  implicit val discriminator: Discriminator[Frame, PubrelFrame, Int] = Discriminator(6)
  implicit val codec: Codec[PubrelFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdCodec)).as[PubrelFrame]
}

object PubcompFrame {
  implicit val discriminator: Discriminator[Frame, PubcompFrame, Int] = Discriminator(7)
  implicit val codec: Codec[PubcompFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdCodec)).as[PubcompFrame]
}

object SubscribeFrame {
  implicit val discriminator: Discriminator[Frame, SubscribeFrame, Int] = Discriminator(8)
  val topicCodec: Codec[(String, Int)] = (stringCodec :: ignore(6) :: uint2).dropUnits.as[(String, Int)]
  implicit val topicsCodec: Codec[Vector[(String, Int)]] = vector(topicCodec)
  implicit val codec: Codec[SubscribeFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdCodec :: topicsCodec)).as[SubscribeFrame]
}

object SubackFrame {
  implicit val discriminator: Discriminator[Frame, SubackFrame, Int] = Discriminator(9)
  implicit val qosCodec: Codec[Vector[Int]] = vector(ignore(6).dropLeft(uint2))
  implicit val codec: Codec[SubackFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdCodec :: qosCodec)).as[SubackFrame]
}

object UnsubscribeFrame {
  implicit val discriminator: Discriminator[Frame, UnsubscribeFrame, Int] = Discriminator(10)
  implicit val codec: Codec[UnsubscribeFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdCodec :: vector(stringCodec))).as[UnsubscribeFrame]
}

object UnsubackFrame {
  implicit val discriminator: Discriminator[Frame, UnsubackFrame, Int] = Discriminator(11)
  implicit val codec: Codec[UnsubackFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdCodec)).as[UnsubackFrame]
}

object PingReqFrame {
  implicit val discriminator: Discriminator[Frame, PingReqFrame, Int] = Discriminator(12)
  implicit val codec: Codec[PingReqFrame] = (headerCodec :: bytePaddingCodec).dropUnits.as[PingReqFrame]
}

object PingRespFrame {
  implicit val discriminator: Discriminator[Frame, PingRespFrame, Int] = Discriminator(13)
  implicit val codec: Codec[PingRespFrame] = (headerCodec :: bytePaddingCodec).dropUnits.as[PingRespFrame]
}

object DisconnectFrame {
  implicit val discriminator: Discriminator[Frame, DisconnectFrame, Int] = Discriminator(14)
  implicit val codec: Codec[DisconnectFrame] = (headerCodec :: bytePaddingCodec).dropUnits.as[DisconnectFrame]
}
