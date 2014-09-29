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

package net.sigusr

import java.net.InetSocketAddress

import org.specs2.mutable._
import org.specs2.time.NoTimeConversions
import net.sigusr.SpecUtils._
import scala.concurrent.duration._

class ActorSpec extends Specification with NoTimeConversions {

  args(skipAll = true)

  "The MQTTClient API" should {

    "Allow to connect to a broker and then disconnect" in new SpecsTestKit {

      import net.sigusr.client.{MQTTClient, MQTTDisconnect, MQTTDisconnected, MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress("localhost", 1883)
      val client = system.actorOf(MQTTClient.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      client ! MQTTConnect("Test")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      client ! MQTTDisconnect

      receiveOne(1 seconds) should be_==(MQTTDisconnected)
    }

    "Allow to connect to a broker and be disconnected" in new SpecsTestKit {

      import net.sigusr.client.{MQTTClient, MQTTDisconnected, MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress("localhost", 1883)
      val client = system.actorOf(MQTTClient.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      client ! MQTTConnect("Test", keepAlive = 1)

      receiveOne(1 seconds) should be_==(MQTTConnected)

      receiveOne(2 seconds) should be_==(MQTTDisconnected)
    }
  }
}
