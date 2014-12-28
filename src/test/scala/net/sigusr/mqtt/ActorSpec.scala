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
import akka.io.{IO, Tcp}
import akka.testkit._
import net.sigusr.mqtt.SpecUtils._
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames.{AtLeastOnce, ExactlyOnce}
import net.sigusr.mqtt.impl.protocol.{Protocol, TCPTransport}
import org.specs2.mutable._
import org.specs2.time.NoTimeConversions

import scala.concurrent.duration._

object ActorSpec extends Specification with NoTimeConversions {

  args(skipAll = true)
  sequential

  val brokerHost = "localhost"

  class TestMQTTManager(source: ActorRef, remote: InetSocketAddress) extends TCPTransport(source, remote) with Protocol {
    import context.system
    override def tcpManagerActor = IO(Tcp)
  }

  object TestMQTTManager {
    def props(source: ActorRef, remote: InetSocketAddress) = Props(classOf[MQTTManager], source, remote)
  }

  "The MQTTClient API" should {

    "Allow to connect to a broker and then disconnect" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTDisconnect, MQTTDisconnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(TestMQTTManager.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("Test")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      mqttManager ! MQTTDisconnect

      receiveOne(1 seconds) should be_==(MQTTDisconnected)
    }

    "Allow to connect to a broker and keep connected even when idle" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(TestMQTTManager.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("Test", keepAlive = 2)

      receiveOne(1 seconds) should be_==(MQTTConnected)

      expectNoMsg(4 seconds) should not throwA()

      mqttManager ! MQTTDisconnect

      receiveOne(1 seconds) should be_==(MQTTDisconnected)
    }

    "Disallow to send a server side message" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(TestMQTTManager.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("Test")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      mqttManager ! MQTTReady

      receiveOne(1 seconds) should be_==(MQTTWrongClientMessage)
    }

    "Allow to publish a message with QOS 1 and receive a Puback response" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(TestMQTTManager.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("TestPubAck")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      mqttManager ! MQTTPublish("a/b", AtLeastOnce, retain = false, "Hello world".getBytes.to[Vector], Some(123))

      receiveOne(1 seconds) should be_==(MQTTPublishSuccess(Some(123)))
    }

    "Allow to publish a message with QOS 2 and complete the handshake" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = TestActorRef(TestMQTTManager.props(testActor, endpoint), "MQTTClient-service")

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("TestPubAck")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      mqttManager ! MQTTPublish("a/b", ExactlyOnce, retain = false, "Hello world".getBytes.to[Vector], Some(123))

      receiveOne(2 seconds) should be_==(MQTTPublishSuccess(Some(123)))
    }
  }
}
