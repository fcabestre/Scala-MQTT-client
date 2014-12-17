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

import net.sigusr.mqtt.impl.frames.ConnackVariableHeader._
import net.sigusr.mqtt.impl.frames.ConnectVariableHeader._
import net.sigusr.mqtt.impl.frames.MessageIdentifier._
import net.sigusr.mqtt.impl.frames.Header._
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._
import scodec.bits._

sealed trait Frame {
  def header : Header
}

case class ConnectFrame(header : Header, variableHeader: ConnectVariableHeader,
                        clientId: String,
                        topic: Option[String],
                        message: Option[String],
                        user: Option[String],
                        password: Option[String]) extends Frame

case class ConnackFrame(header : Header, connackVariableHeader: ConnackVariableHeader) extends Frame
case class SubscribeFrame(header : Header, messageIdentifier : MessageIdentifier, topics : Vector[(String, QualityOfService)]) extends Frame
case class SubackFrame(header : Header, messageIdentifier : MessageIdentifier, topics : Vector[QualityOfService]) extends Frame
case class PingReqFrame(header : Header) extends Frame
case class PingRespFrame(header : Header) extends Frame
case class DisconnectFrame(header : Header) extends Frame
case class PublishFrame(header: Header, topic: String, messageIdentifier: MessageIdentifier, payload: ByteVector) extends Frame
case class PubackFrame(header: Header, messageIdentifier: MessageIdentifier) extends Frame
case class PubrecFrame(header: Header, messageIdentifier: MessageIdentifier) extends Frame
case class PubrelFrame(header: Header, messageIdentifier: MessageIdentifier) extends Frame
case class PubcompFrame(header: Header, messageIdentifier: MessageIdentifier) extends Frame

object ConnectFrame {
  implicit val discriminator : Discriminator[Frame, ConnectFrame, Int] = Discriminator(1)
  implicit val codec: Codec[ConnectFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec,
    connectVariableHeaderCodec >>:~ { (hdr: ConnectVariableHeader) =>
      stringCodec ::
        conditional(hdr.willFlag, stringCodec) ::
        conditional(hdr.willFlag, stringCodec) ::
        conditional(hdr.userNameFlag, stringCodec) ::
        conditional(hdr.passwordFlag, stringCodec)
    })).as[ConnectFrame]

}

object Frame {
  implicit val discriminated : Discriminated[Frame, Int] = Discriminated(uint4)
  implicit val frameCodec: Codec[Frame] = Codec.coproduct[Frame].auto
}

object ConnackFrame {
  implicit val discriminator : Discriminator[Frame, ConnackFrame, Int] = Discriminator(2)
  implicit val codec: Codec[ConnackFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, connackVariableHeaderCodec)).as[ConnackFrame]

}

object PublishFrame {
  implicit val discriminator : Discriminator[Frame, PublishFrame, Int] = Discriminator(3)
  implicit val codec: Codec[PublishFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, stringCodec :: messageIdentifierCodec :: bytes)).as[PublishFrame]
}

object PubackFrame {
  implicit val discriminator : Discriminator[Frame, PubackFrame, Int] = Discriminator(4)
  implicit val codec: Codec[PubackFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdentifierCodec)).as[PubackFrame]
}

object PubrecFrame {
  implicit val discriminator: Discriminator[Frame, PubrecFrame, Int] = Discriminator(5)
  implicit val codec: Codec[PubrecFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdentifierCodec)).as[PubrecFrame]
}

object PubrelFrame {
  implicit val discriminator: Discriminator[Frame, PubrelFrame, Int] = Discriminator(6)
  implicit val codec: Codec[PubrelFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdentifierCodec)).as[PubrelFrame]
}

object PubcompFrame {
  implicit val discriminator: Discriminator[Frame, PubcompFrame, Int] = Discriminator(7)
  implicit val codec: Codec[PubcompFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdentifierCodec)).as[PubcompFrame]
}

object SubscribeFrame {
  implicit val discriminator : Discriminator[Frame, SubscribeFrame, Int] = Discriminator(8)
  val topicCodec : Codec[Topic] = (stringCodec :: ignore(6) :: qualityOfServiceCodec).dropUnits.as[Topic]
  implicit val topicsCodec : Codec[Topics] = vector(topicCodec)
  implicit val codec : Codec[SubscribeFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdentifierCodec :: topicsCodec)).as[SubscribeFrame]
}

object SubackFrame {
  implicit val discriminator : Discriminator[Frame, SubackFrame, Int] = Discriminator(9)
  implicit val qosCodec : Codec[Vector[QualityOfService]] = vector(ignore(6).dropLeft(qualityOfServiceCodec))
  implicit val codec : Codec[SubackFrame] = (headerCodec :: variableSizeBytes(remainingLengthCodec, messageIdentifierCodec :: qosCodec)).as[SubackFrame]
}

object PingReqFrame {
  implicit val discriminator : Discriminator[Frame, PingReqFrame, Int] = Discriminator(12)
  implicit val codec: Codec[PingReqFrame] = (headerCodec :: constant(zeroLength)).dropUnits.as[PingReqFrame]
}

object PingRespFrame {
  implicit val discriminator : Discriminator[Frame, PingRespFrame, Int] = Discriminator(13)
  implicit val codec: Codec[PingRespFrame] = (headerCodec :: constant(zeroLength)).dropUnits.as[PingRespFrame]

}

object DisconnectFrame {
  implicit val discriminator : Discriminator[Frame, DisconnectFrame, Int] = Discriminator(14)
  implicit val codec: Codec[DisconnectFrame] = (headerCodec :: constant(zeroLength)).dropUnits.as[DisconnectFrame]
}
