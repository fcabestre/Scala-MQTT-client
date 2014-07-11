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

package org.ultimo.client

import akka.actor.{ActorRef, ActorLogging, Actor, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import java.net.InetSocketAddress

import org.ultimo.messages._
import org.ultimo.codec.Codecs._
import org.ultimo.messages.{DisconnectMessage, ConnectMessage, ConnackMessage}
import scodec.bits.BitVector
import scodec.{Encoder, Codec}

import scala.annotation.switch

object MQTTClient {
  def props(source: ActorRef, remote: InetSocketAddress) =
    Props(classOf[MQTTClient], source, remote)
}

class MQTTClient(source: ActorRef, remote: InetSocketAddress) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  IO(Tcp) ! Connect(remote)

  def receive = start

  def start: Receive = {
    case CommandFailed(_: Connect) ⇒
      source ! MQTTNotReady
      context stop self

    case c@Connected(_, _) ⇒
      sender ! Register(self)
      source ! MQTTReady
      context become ready(sender)
  }

  def ready(connection: ActorRef): Receive = {
    case MQTTConnect(clientId, keepAlive, cleanSession, topic, message, user, password) =>
      val header = Header(CONNECT, dup = false, AtMostOnce, retain = false)
      val variableHeader = ConnectVariableHeader(user.isDefined, password.isDefined, willRetain = false, AtLeastOnce, willFlag = false, cleanSession, keepAlive)
      val connectMessage = ConnectMessage(header, variableHeader, clientId, topic, message, user, password)
      encodeAndSend(connection, connectMessage)

    case Received(encodedResponse) ⇒
      val response = Codec[ConnackMessage].decodeValidValue(BitVector.view(encodedResponse.toArray))
      response.connackVariableHeader.returnCode match {
        case ConnectionAccepted =>
          source ! MQTTConnected
          context become connected(sender)
        case code: ConnectReturnCode =>
          source ! MQTTConnectionFailure(code2Reason(code))
      }

    case CommandFailed(w: Write) ⇒ // O/S buffer was full
    case _: ConnectionClosed ⇒
      context stop self
  }

  def connected(connection: ActorRef): Receive = {
    case MQTTDisconnect =>
      val disconnectMessage = DisconnectMessage(Header(DISCONNECT, dup = false, AtMostOnce, retain = false))
      encodeAndSend(connection, disconnectMessage)
      context become ready(sender)

    case CommandFailed(w: Write) ⇒ // O/S buffer was full
    case _: ConnectionClosed ⇒
      context stop self
  }

  def code2Reason(code : ConnectReturnCode) = code match {
    case ConnectionRefused1 => BadProtocolVersion
    case ConnectionRefused2 => IdentifierRejected
    case ConnectionRefused3 => ServerUnavailable
    case ConnectionRefused4 => BadUserNameOrPassword
    case ConnectionRefused5 => NotAuthorized
    case _ => NotAuthorized // impossible
  }


  def encodeAndSend[A: Encoder](connection: ActorRef, message: A) = {
    val encodedConnectMessage = Codec.encodeValid(message)
    connection ! Write(ByteString(encodedConnectMessage.toByteArray))
  }
}

