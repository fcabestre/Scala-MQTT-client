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

package net.sigusr.mqtt.impl.protocol

import java.net.InetSocketAddress

import akka.actor.ActorRef
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import net.sigusr.mqtt.api.MQTTAPIMessage
import net.sigusr.mqtt.impl.frames.Frame
import scodec.Codec
import scodec.bits.BitVector

trait Transport {
  def initTransport(remote: InetSocketAddress) : Unit
  def disconnected() : Unit
  def messageToSend(message: MQTTAPIMessage) : Unit
  def frameReceived(frame: Frame) : Unit
  def transportReady() : Unit
  def transportNotReady() : Unit
}

trait TCPTransport extends Transport{ this: Protocol =>
  
  import context.system
  def initTransport(remote: InetSocketAddress) : Unit = IO(Tcp) ! Connect(remote)

  import akka.io.Tcp._

  def receive = start

  def start: Receive = {
    case CommandFailed(_: Connect) =>
      transportNotReady()
      context stop self

    case c @ Connected(_, _) =>
      sender ! Register(self)
      transportReady()
      context become ready(sender())
  }

  def ready(connection: ActorRef): Receive = {
    case message : MQTTAPIMessage =>
      messageToSend(message)

    case frame : Frame =>
      val encodedMessage = Codec[Frame].encodeValid(frame)
      connection ! Write(ByteString(encodedMessage.toByteArray))

    case Received(encodedResponse) ⇒
      frameReceived(Codec[Frame].decodeValidValue(BitVector.view(encodedResponse.toArray)))

    case _: ConnectionClosed ⇒
      disconnected()
      context stop self

    case CommandFailed(w: Write) ⇒ // O/S buffer was full
  }
}




