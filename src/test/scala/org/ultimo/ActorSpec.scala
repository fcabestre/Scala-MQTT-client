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

package org.ultimo

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.specs2.mutable._
import org.specs2.specification.AfterExample

class ActorSpec extends TestKit(ActorSystem("MQTTClient-system")) with SpecificationLike with AfterExample {

  override protected def after: Any = system.shutdown()

  "The MQTTClient API" should {

    "Allow to connect to a broker" in {

      import org.ultimo.codec.Codecs._
      import akka.util.ByteString
      import org.ultimo.client.MQTTClient
      import org.ultimo.messages._
      import scodec.Codec

      val endpoint = new InetSocketAddress("localhost", 1883)
      val client = system.actorOf(MQTTClient.props(endpoint), "MQTTClient-service")

      val header = Header(CONNECT, dup = false, AtLeastOnce, retain = false)
      val variableHeader = ConnectVariableHeader(userNameFlag = false, passwordFlag = false, willRetain = false, AtLeastOnce, willFlag = false, cleanSession = true, 30)
      val message = ConnectMessage(header, variableHeader, "client", None, None, None, None)
      val encoded = Codec.encodeValid(message)

      val bytes = ByteString(encoded.toByteBuffer)
      client ! bytes
    }
  }
}
