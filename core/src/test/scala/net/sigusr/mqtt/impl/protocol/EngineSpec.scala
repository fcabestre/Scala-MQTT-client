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

import akka.actor._
import akka.io.Tcp.{ Abort ⇒ TCPAbort, Aborted ⇒ TCPAborted, Closed ⇒ TCPClosed, CommandFailed ⇒ TCPCommandFailed, Connect ⇒ TCPConnect, Connected ⇒ TCPConnected, Received ⇒ TCPReceived, Register ⇒ TCPRegister, Write ⇒ TCPWrite }
import akka.testkit.{ ImplicitSender, TestProbe }
import akka.util.ByteString
import net.sigusr.mqtt.SpecsTestKit
import net.sigusr.mqtt.api.{ Status ⇒ MqttApiStatus, _ }
import net.sigusr.mqtt.impl.frames.{ Frame, Header, PublishFrame }
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification
import scodec.Codec
import scodec.bits.ByteVector

object EngineSpec extends Specification {

  sequential
  isolated

  private val fakeBrokerAddress: InetSocketAddress = new InetSocketAddress(0)
  private val fakeLocalAddress: InetSocketAddress = new InetSocketAddress(0)

  class TestManager(_tcpManagerActor: ActorRef) extends Engine(fakeBrokerAddress) with Handlers {
    override def tcpManagerActor: ActorRef = _tcpManagerActor
  }

  class FakeTCPManagerActor(implicit system: ActorSystem) extends TestProbe(system) with ImplicitSender {

    import net.sigusr.mqtt.SpecUtils._

    val garbageFrame = ByteString(0xff)
    val connackFrame = ByteString(0x20, 0x02, 0x00, 0x00)
    val pingRespFrame = ByteString(0xd0, 0x00)
    val pubrecFrame = ByteString(0x50, 0x02, 0x00, 0x2a)
    val pubrelFrame = ByteString(0x62, 0x02, 0x00, 0x2a)
    val pubcompFrame = ByteString(0x70, 0x02, 0x00, 0x2a)

    private val payload0 = "payload".getBytes.to[Vector]
    val frame0 = PublishFrame(Header(qos = ExactlyOnce.enum), "topic", 42, ByteVector(payload0))
    val publishFrame0 = ByteString(Codec[Frame].encode(frame0).require.toByteArray)

    val payload1: Vector[Byte] = "payload of frame 1".getBytes.to[Vector]
    val frame1 = PublishFrame(Header(qos = AtMostOnce.enum), "topic", 42, ByteVector(payload1))
    val publishFrame1 = ByteString(Codec[Frame].encode(frame1).require.toByteArray)

    val payload2: Vector[Byte] = "payload of frame 2".getBytes.to[Vector]
    val frame2 = PublishFrame(Header(qos = AtMostOnce.enum), "topic", 42, ByteVector(payload2))
    val publishFrame2 = ByteString(Codec[Frame].encode(frame2).require.toByteArray)

    val bigPayload = makeRandomByteVector(2500)
    val bigFrame = PublishFrame(Header(qos = AtMostOnce.enum), "topic", 42, ByteVector(bigPayload))
    val encodedBigFrame = ByteString(Codec[Frame].encode(bigFrame).require.toByteArray)
    val encodedBigFramePart1 = encodedBigFrame.take(1500)
    val encodedBigFramePart2 = encodedBigFrame.drop(1500)

    var pingReqCount = 0
    var mqttManager: Option[ActorRef] = None

    def expectConnect(): Unit = {
      expectMsgPF() {
        case TCPConnect(remote, _, _, _, _) ⇒
          remote should be_==(fakeBrokerAddress)
          sender() ! TCPConnected(fakeBrokerAddress, fakeLocalAddress)
      }
    }

    def expectConnectThenFail(): Unit = {
      expectMsgPF() {
        case c @ TCPConnect(remote, _, _, _, _) ⇒
          remote should_== fakeBrokerAddress
          sender() ! TCPCommandFailed(c)
      }
    }

    def expectRegister(): Unit = {
      expectMsgPF() {
        case TCPRegister(handler, _, _) ⇒
          mqttManager = Some(handler)
      }
    }

    def expectWriteConnectFrame(): Unit = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          (byteString.head & 0xff) should_== 0x10
          sender() ! TCPReceived(connackFrame)
      }
    }

    def expectWritePingReqFrame(): Unit = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          (byteString.head & 0xff) should_== 0xc0
          if (pingReqCount == 0) {
            sender() ! TCPReceived(pingRespFrame)
          }
          pingReqCount += 1
      }
    }

    def expectWritePublishFrame(): Unit = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          (byteString.head & 0xff) should_== 0x34
          sender() ! TCPReceived(pubrecFrame)
      }
    }

    def expectWritePubrelFrame(): Unit = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          (byteString.head & 0xff) should_== 0x62
          sender() ! TCPReceived(pubcompFrame)
      }
    }

    def expectWritePubrecFrame(): Unit = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          (byteString.head & 0xff) should_== 0x50
          sender() ! TCPReceived(pubrelFrame)
      }
    }

    def expectWritePubcompFrame(): MatchResult[Any] = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          (byteString.head & 0xff) should_== 0x70
      }
    }

    def expectWriteDisconnectFrame(): Unit = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          (byteString.head & 0xff) should_== 0xe0
          sender() ! TCPClosed
      }
    }

    def expectClose(): Unit = {
      expectMsgPF() {
        case TCPAbort ⇒ sender() ! TCPAborted
      }
    }

    def sendPublishFrame(publishFrame: ByteString): Unit = mqttManager.foreach(_ ! TCPReceived(publishFrame))

    def sendGarbageFrame(): Unit = mqttManager.foreach(_ ! TCPReceived(garbageFrame))
  }

  class FakeMQTTManagerParent(testMQTTManagerName: String, fakeTCPManagerActor: ActorRef)(implicit testActor: ActorRef) extends Actor {
    val child = context.actorOf(Props(new TestManager(fakeTCPManagerActor)), testMQTTManagerName)
    def receive = {
      case x if sender == child ⇒ testActor forward x
      case x ⇒ child forward x
    }
  }

  "The Transport" should {

    "Manage successful connection" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)
    }

    "Manage unsuccessful connection" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnectThenFail()

      expectMsg(ConnectionFailure(ServerNotResponding))
    }

    "Provide the right connection status [0]" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      mqttManagerActor ! MqttApiStatus

      expectMsg(Connected)
    }

    "Provide the right connection status [1]" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! MqttApiStatus

      expectMsg(Disconnected)
    }

    "Provide the right connection status [2]" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      mqttManagerActor ! MqttApiStatus

      expectMsg(Disconnected)
    }

    "Send back an error when sending an API command while not connected" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Disconnect

      expectMsg(Error(NotConnected))
    }

    "Send back an error when sending an API command during connection" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      expectMsg(Error(NotConnected))
    }

    "Allow graceful disconnection" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      mqttManagerActor ! Disconnect
      fakeTCPManagerActor.expectWriteDisconnectFrame()

      expectMsg(Disconnected)
    }

    "Send back an error when sending a Connect API message if already connected" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      expectMsg(Error(AlreadyConnected))

      mqttManagerActor ! Disconnect
      fakeTCPManagerActor.expectWriteDisconnectFrame()

      expectMsg(Disconnected)
    }

    "Manage the connection actor's death" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      fakeTCPManagerActor.ref ! PoisonPill

      expectMsg(Disconnected)
    }

    "Keep an idle connection alive or disconnect" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 1, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      fakeTCPManagerActor.expectWritePingReqFrame()
      fakeTCPManagerActor.expectWritePingReqFrame()
      fakeTCPManagerActor.expectClose()

      expectMsg(Disconnected)
    }

    "Disconnect when a wrong frame is received" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 1, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      fakeTCPManagerActor.sendGarbageFrame()

      expectMsg(Disconnected)
    }

    "Manage publishing a message with a QOS of exactly once" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 1, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      mqttManagerActor ! Publish("topic", "payload".getBytes.to[Vector], ExactlyOnce, Some(42))

      fakeTCPManagerActor.expectWritePublishFrame()
      fakeTCPManagerActor.expectWritePubrelFrame()

      expectMsg(Published(42))

      mqttManagerActor ! Disconnect
      fakeTCPManagerActor.expectWriteDisconnectFrame()

      expectMsg(Disconnected)
    }

    "Manage receiving a message with a QOS of exactly once" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 1, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      fakeTCPManagerActor.sendPublishFrame(fakeTCPManagerActor.publishFrame0)
      fakeTCPManagerActor.expectWritePubrecFrame()

      expectMsg(Message("topic", "payload".getBytes.to[Vector]))

      fakeTCPManagerActor.expectWritePubcompFrame()

      mqttManagerActor ! Disconnect
      fakeTCPManagerActor.expectWriteDisconnectFrame()

      expectMsg(Disconnected)
    }

    "Manage receiving a message in multiple packets" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 1, cleanSession = true, Some(Will(retain = false, AtLeastOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      fakeTCPManagerActor.sendPublishFrame(fakeTCPManagerActor.encodedBigFramePart1)
      fakeTCPManagerActor.sendPublishFrame(fakeTCPManagerActor.encodedBigFramePart2)

      expectMsg(Message("topic", fakeTCPManagerActor.bigPayload))

      mqttManagerActor ! Disconnect
      fakeTCPManagerActor.expectWriteDisconnectFrame()

      expectMsg(Disconnected)
    }

    "Manage receiving two messages in one packet" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 1, cleanSession = true, Some(Will(retain = false, AtLeastOnce, "test/topic", "test death")), None, None)

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      fakeTCPManagerActor.expectWriteConnectFrame()

      expectMsg(Connected)

      fakeTCPManagerActor.sendPublishFrame(fakeTCPManagerActor.publishFrame1 ++ fakeTCPManagerActor.publishFrame2)

      expectMsg(Message("topic", fakeTCPManagerActor.payload1))
      expectMsg(Message("topic", fakeTCPManagerActor.payload2))

      mqttManagerActor ! Disconnect
      fakeTCPManagerActor.expectWriteDisconnectFrame()

      expectMsg(Disconnected)
    }
  }
}