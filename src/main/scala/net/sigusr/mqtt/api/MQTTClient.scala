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

package net.sigusr.mqtt.api

import java.net.InetSocketAddress

import akka.actor._
import akka.util.ByteString
import akka.io.{IO,Tcp}

import net.sigusr.mqtt.impl.frames.{ConnackFrame, ConnectFrame, DisconnectFrame, _}
import scodec.Codec
import scodec.bits.BitVector

object MQTTClient {
  def props(source: ActorRef, remote: InetSocketAddress) = Props(classOf[MQTTClient], source, remote)
}

class MQTTClient(source: ActorRef, remote: InetSocketAddress) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  IO(Tcp) ! Connect(remote)

  def receive = start

  def start: Receive = {
    case CommandFailed(_: Connect) =>
      source ! MQTTNotReady
      context stop self

    case c @ Connected(_, _) =>
      sender ! Register(self)
      source ! MQTTReady
      context become ready(sender())
  }

  def apiMessage2Frame(apiMessage : MQTTAPIMessage) : Frame = apiMessage match {
    case MQTTConnect(clientId, keepAlive, cleanSession, topic, message, user, password) =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce, willFlag = false, cleanSession, keepAlive)
      ConnectFrame(header, variableHeader, clientId, topic, message, user, password)
    case MQTTDisconnect =>
      val header = Header(dup = false, AtMostOnce, retain = false)
      DisconnectFrame(header)
  }

  def frame2ApiMessage(frame : Frame) : MQTTAPIMessage = frame match {
    case ConnackFrame(header, connackVariableHeader) => MQTTConnected
  }

  def ready(connection: ActorRef): Receive = {
    case message : MQTTAPIMessage =>
      val encodedMessage = Codec[Frame].encodeValid(apiMessage2Frame(message))
      connection ! Write(ByteString(encodedMessage.toByteArray))

    case Received(encodedResponse) ⇒ 
      val decodedMessage = frame2ApiMessage(Codec[Frame].decodeValidValue(BitVector.view(encodedResponse.toArray)))
      source ! decodedMessage

    case _: ConnectionClosed ⇒
      source ! MQTTDisconnected
      context stop self

    case CommandFailed(w: Write) ⇒ // O/S buffer was full
  }
}

