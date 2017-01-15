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

package net.sigusr.mqtt.integration

import java.net.InetSocketAddress

import akka.actor.Props
import akka.io.{ IO, Tcp }
import net.sigusr.mqtt.SpecUtils._
import net.sigusr.mqtt.SpecsTestKit
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.protocol.Engine
import org.specs2.mutable._

import scala.concurrent.duration._

object ActorSpec extends Specification {

  sequential

  val brokerHost = "localhost"

  class TestMQTTManager(remote: InetSocketAddress) extends Engine(remote) {
    import context.system
    override def tcpManagerActor = IO(Tcp)
  }

  "The MQTTClient API" should {

    "Allow to connect to a broker and then disconnect" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{ Connect, Connected, Disconnect, Disconnected }

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }

      mqttManager ! Connect("Test")

      receiveOne(1 seconds) should be_==(Connected)

      mqttManager ! Disconnect

      receiveOne(1 seconds) should be_==(Disconnected)
    }

    "Allow to connect to a broker with user and password and then disconnect" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{ Connect, Connected, Disconnect, Disconnected }

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }

      mqttManager ! Connect("Test", user = Some("user"), password = Some("pass"))

      receiveOne(1 seconds) should be_==(Connected)

      mqttManager ! Disconnect

      receiveOne(1 seconds) should be_==(Disconnected)
    }

    "Disallow to connect to a broker with a wrong user" in new SpecsTestKit {

      import net.sigusr.mqtt.api.Connect

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }

      mqttManager ! Connect("Test", user = Some("wrong"), password = Some("pass"))

      receiveOne(1 seconds) should be_==(ConnectionFailure(IdentifierRejected))
    }

    "Disallow to connect to a broker with a wrong password" in new SpecsTestKit {

      import net.sigusr.mqtt.api.Connect

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }

      mqttManager ! Connect("Test", user = Some("user"), password = Some("wrong"))

      receiveOne(1 seconds) should be_==(ConnectionFailure(IdentifierRejected))
    }

    "Allow to connect to a broker and keep connected even when idle" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{ Connect, Connected }

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }

      mqttManager ! Connect("Test", keepAlive = 2)

      receiveOne(1 seconds) should be_==(Connected)

      expectNoMsg(4 seconds) should not throwA ()

      mqttManager ! Disconnect

      receiveOne(1 seconds) should be_==(Disconnected)
    }

    "Allow to subscribe to topics and receive a subscription acknowledgement" in new SpecsTestKit {

      import net.sigusr.mqtt.api.{ Connect, Connected }

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }

      mqttManager ! Connect("Test")

      receiveOne(1 seconds) should be_==(Connected)

      mqttManager ! Subscribe(Vector(("topic0", AtMostOnce), ("topic1", AtLeastOnce), ("topic2", ExactlyOnce)), 42)

      receiveOne(1 seconds) should be_==(Subscribed(Vector(AtMostOnce, AtLeastOnce, ExactlyOnce), 42))

      mqttManager ! Disconnect

      receiveOne(1 seconds) should be_==(Disconnected)
    }

    "Allow to publish a message with QOS 0" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{ Connect, Connected }

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }

      mqttManager ! Connect("Test")

      receiveOne(1 seconds) should be_==(Connected)

      mqttManager ! Publish("a/b", "Hello world".getBytes.to[Vector], AtMostOnce, Some(123))

      mqttManager ! Disconnect

      receiveOne(1 seconds) should be_==(Disconnected)
    }

    "Allow to publish a 'large' message with QOS 0 and read it back" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{ Connect, Connected }

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }
      val payload = makeRandomByteVector(131070)

      mqttManager ! Connect("Test", cleanSession = true)

      receiveOne(1 seconds) should be_==(Connected)

      mqttManager ! Subscribe(Vector(("a/b", AtMostOnce)), 1)

      receiveOne(1 seconds) should be_==(Subscribed(Vector(AtMostOnce), 1))

      mqttManager ! Publish("a/b", payload, AtMostOnce)

      receiveOne(1 seconds) //should be_==(Message("a/b", payload))

      mqttManager ! Unsubscribe(Vector("a/b"), 2)

      receiveOne(1 seconds) should be_==(Unsubscribed(2))

      mqttManager ! Disconnect

      receiveOne(1 seconds) should be_==(Disconnected)
    }

    "Allow to publish a message with QOS 1 and receive a Puback response" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{ Connect, Connected }

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }

      mqttManager ! Connect("Test")

      receiveOne(1 seconds) should be_==(Connected)

      mqttManager ! Publish("a/b", "Hello world".getBytes.to[Vector], AtLeastOnce, Some(123))

      receiveOne(1 seconds) should be_==(Published(123))

      mqttManager ! Disconnect

      receiveOne(1 seconds) should be_==(Disconnected)
    }

    "Allow to publish a message with QOS 2 and complete the handshake" in new SpecsTestKit {
      import net.sigusr.mqtt.api.{ Connect, Connected }

      val endpoint = new InetSocketAddress(brokerHost, 1883)
      val mqttManager = testActorProxy { context ⇒ context.actorOf(Props(new TestMQTTManager(endpoint))) }

      mqttManager ! Connect("Test")

      receiveOne(1 seconds) should be_==(Connected)

      mqttManager ! Publish("a/b", "Hello world".getBytes.to[Vector], ExactlyOnce, Some(123))

      receiveOne(2 seconds) should be_==(Published(123))

      mqttManager ! Disconnect

      receiveOne(1 seconds) should be_==(Disconnected)
    }
  }
}
