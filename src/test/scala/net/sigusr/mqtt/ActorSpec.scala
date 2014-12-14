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

package net.sigusr.mqtt

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import net.sigusr.mqtt.SpecUtils._
import net.sigusr.mqtt.api.{MQTTPublishSuccess, MQTTPublish, MQTTClient, MQTTWrongClientMessage}
import net.sigusr.mqtt.impl.frames.{ExactlyOnce, AtLeastOnce}
import net.sigusr.mqtt.impl.protocol.{Protocol, TCPTransport}
import org.specs2.mutable._
import org.specs2.time.NoTimeConversions
import akka.testkit._

import scala.concurrent.duration._

class ActorSpec extends Specification with NoTimeConversions {

  //args(skipAll = true)

  sequential

  val brokerHost = "192.168.1.215"

  class TestClient(source: ActorRef, remote: InetSocketAddress) extends Protocol(source, remote) with TCPTransport

  object TestClient {
    def props(source: ActorRef, remote: InetSocketAddress) = Props(classOf[MQTTClient], source, remote)
  }

  "The MQTTClient API" should {

    "Allow to connect to a broker and then disconnect" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTDisconnect, MQTTDisconnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val client = system.actorOf(TestClient.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      client ! MQTTConnect("Test")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      client ! MQTTDisconnect

      receiveOne(1 seconds) should be_==(MQTTDisconnected)
    }

    "Allow to connect to a broker and keep connected even when idle" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val client = system.actorOf(TestClient.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      client ! MQTTConnect("Test", keepAlive = 1)

      receiveOne(1 seconds) should be_==(MQTTConnected)

      expectNoMsg(2 seconds) should not throwA()
    }

    "Disallow to send a server side message" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val client = system.actorOf(TestClient.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      client ! MQTTConnect("Test")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      client ! MQTTReady

      receiveOne(1 seconds) should be_==(MQTTWrongClientMessage)
    }

    "Allow to publish a message with QOS 1 and receive a Puback response" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val client = system.actorOf(TestClient.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      client ! MQTTConnect("TestPubAck", keepAlive = 1)

      receiveOne(1 seconds) should be_==(MQTTConnected)

      client ! MQTTPublish("a/b", AtLeastOnce, retain = false, "Hello world".getBytes, Some(123))

      receiveOne(1 seconds) should be_==(MQTTPublishSuccess(Some(123)))
    }

    "Allow to publish a message with QOS 2 and complete the handshake" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val client = TestActorRef(TestClient.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      client ! MQTTConnect("TestPubAck", keepAlive = 1)

      receiveOne(1 seconds) should be_==(MQTTConnected)

      client ! MQTTPublish("a/b", ExactlyOnce, retain = false, "Hello world".getBytes, Some(123))

      receiveOne(2 seconds) should be_==(MQTTPublishSuccess(Some(123)))
    }
  }
}
