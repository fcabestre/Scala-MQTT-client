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
import net.sigusr.mqtt.impl.frames.Header._
import scodec.Codec
import scodec.codecs._

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
case class PingReqFrame(header : Header) extends Frame
case class PingRespFrame(header : Header) extends Frame
case class DisconnectFrame(header : Header) extends Frame

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
object PingReqFrame {
  implicit val discriminator : Discriminator[Frame, PingReqFrame, Int] = Discriminator(10)
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

