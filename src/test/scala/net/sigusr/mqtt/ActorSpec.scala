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

import akka.actor.{Actor, ActorRef, Props}
import akka.io.{IO, Tcp}
import net.sigusr.mqtt.SpecUtils._
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.frames.{AtMostOnce, AtLeastOnce, ExactlyOnce}
import net.sigusr.mqtt.impl.protocol.{Protocol, TCPTransport}
import org.specs2.mutable._
import org.specs2.time.NoTimeConversions

import scala.concurrent.duration._

object ActorSpec extends Specification with NoTimeConversions {

  args(skipAll = true)
  sequential

  val brokerHost = "localhost"

  class TestMQTTManager(remote: InetSocketAddress) extends TCPTransport(remote) with Protocol {
    import context.system
    override def tcpManagerActor = IO(Tcp)
  }

  class FakeMQTTManagerParent(testMQTTManagerName : String, remote : InetSocketAddress)(implicit testActor : ActorRef) extends Actor {
    val child = context.actorOf(Props(new TestMQTTManager(remote)), testMQTTManagerName)
    def receive = {
      case x if sender == child => testActor forward x
      case x => child forward x
    }
  }

  "The MQTTClient API" should {

    "Allow to connect to a broker and then disconnect" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTDisconnect, MQTTDisconnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-service", endpoint)))

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("Test")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      mqttManager ! MQTTDisconnect

      receiveOne(1 seconds) should be_==(MQTTDisconnected)
    }

    "Allow to connect to a broker and keep connected even when idle" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-service", endpoint)))

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("Test", keepAlive = 2)

      receiveOne(1 seconds) should be_==(MQTTConnected)

      expectNoMsg(4 seconds) should not throwA()

      mqttManager ! MQTTDisconnect

      receiveOne(1 seconds) should be_==(MQTTDisconnected)
    }

    "Allow to subscribe to topics and receive a subscription acknowledgement" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-service", endpoint)))

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("Test")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      mqttManager ! MQTTSubscribe(Vector(("topic0", AtMostOnce), ("topic1", AtLeastOnce), ("topic2", ExactlyOnce)), 42)

      receiveOne(1 seconds) should be_==(MQTTSubscribed(42, Vector(AtMostOnce, AtLeastOnce, ExactlyOnce)))

      mqttManager ! MQTTDisconnect

      receiveOne(1 seconds) should be_==(MQTTDisconnected)
    }

    "Disallow to send a server side message" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-service", endpoint)))

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("Test")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      mqttManager ! MQTTReady

      receiveOne(1 seconds) should be_==(MQTTWrongClientMessage(MQTTReady))
    }

    "Allow to publish a message with QOS 1 and receive a Puback response" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-service", endpoint)))

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("TestPubAck")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      mqttManager ! MQTTPublish("a/b", "Hello world".getBytes.to[Vector], AtLeastOnce, Some(123))

      receiveOne(1 seconds) should be_==(MQTTPublished(123))
    }

    "Allow to publish a message with QOS 2 and complete the handshake" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{MQTTConnect, MQTTConnected, MQTTReady}

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-service", endpoint)))

      expectMsg(1 second, MQTTReady)

      mqttManager ! MQTTConnect("TestPubAck")

      receiveOne(1 seconds) should be_==(MQTTConnected)

      mqttManager ! MQTTPublish("a/b", "Hello world".getBytes.to[Vector], ExactlyOnce, Some(123))

      receiveOne(2 seconds) should be_==(MQTTPublished(123))
    }
  }
}
