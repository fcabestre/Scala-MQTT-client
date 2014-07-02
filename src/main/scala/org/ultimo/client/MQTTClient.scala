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

object MQTTClient {
  def props(remote: InetSocketAddress) =
    Props(classOf[MQTTClient], remote)
}

class MQTTClient(remote: InetSocketAddress) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  IO(Tcp) ! Connect(remote)

  def receive = {
    case CommandFailed(_: Connect) ⇒
      log.debug("Connection failed")
      context stop self

    case c @ Connected(remote, local) ⇒
      log.info("Connected to broker")
      sender ! Register(self)
      context become connected(sender)
  }

  def connected(connection : ActorRef) : Receive = {
    case data: ByteString ⇒ {
      log.info(s"sending ${data.toString()}")
      connection ! Write(data)
    }
    case CommandFailed(w: Write) ⇒ // O/S buffer was full
    case Received(data) ⇒ log.info(data.toString())
    case "close" ⇒ connection ! Close
    case _: ConnectionClosed ⇒ context stop self
  }
}

