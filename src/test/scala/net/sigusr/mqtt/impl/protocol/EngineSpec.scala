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

import akka.actor.{ Status ⇒ ActorStatus, _ }
import akka.io.Tcp.{
  Abort ⇒ TCPAbort,
  Aborted ⇒ TCPAborted,
  Closed ⇒ TCPClosed,
  CommandFailed ⇒ TCPCommandFailed,
  Connect ⇒ TCPConnect,
  Connected ⇒ TCPConnected,
  Received ⇒ TCPReceived,
  Register ⇒ TCPRegister,
  Write ⇒ TCPWrite
}
import akka.testkit.{ ImplicitSender, TestProbe }
import akka.util.ByteString
import net.sigusr.mqtt.SpecUtils.SpecsTestKit
import net.sigusr.mqtt.api._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import scala.language.reflectiveCalls

object EngineSpec extends Specification with NoTimeConversions {

  sequential
  isolated

  private val fakeBrokerAddress: InetSocketAddress = new InetSocketAddress(0)
  private val fakeLocalAddress: InetSocketAddress = new InetSocketAddress(0)

  class TestManager(_tcpManagerActor: ActorRef) extends Engine(fakeBrokerAddress) with Handlers {
    override def tcpManagerActor: ActorRef = _tcpManagerActor
  }

  class FakeTCPManagerActor(implicit system: ActorSystem) extends TestProbe(system) with ImplicitSender {

    val connackFrame = ByteString(0x20, 0x02, 0x00, 0x00)
    val pingRespFrame = ByteString(0xd0, 0x00)
    var pingReqCount = 0

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
          remote should be_==(fakeBrokerAddress)
          sender() ! TCPCommandFailed(c)
      }
    }

    def expectRegister(): Unit = {
      expectMsgPF() {
        case TCPRegister(_, _, _) ⇒
      }
    }

    def expectWriteConnectFrame(): Unit = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          if (byteString(0) == 0x10) {
            sender() ! TCPReceived(connackFrame)
          }
      }
    }

    def expectWritePingReqFrame(): Unit = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          if (byteString(0) == -64) {
            if (pingReqCount == 0) {
              sender() ! TCPReceived(pingRespFrame)
            }
            pingReqCount += 1
          }
      }
    }

    def expectWriteDisconnectFrame(): Unit = {
      expectMsgPF() {
        case TCPWrite(byteString, _) ⇒
          // Why when I write 0xe0 instead of -32
          // here things go really wrong ?
          if (byteString(0) == -32) {
            sender() ! TCPClosed
          }
      }
    }

    def expectClose(): Unit = {
      expectMsgPF() {
        case TCPAbort ⇒ sender() ! TCPAborted
      }
    }
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

      mqttManagerActor ! Status

      expectMsg(Connected)
    }

    "Provide the right connection status [1]" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Status

      expectMsg(Disconnected)
    }

    "Provide the right connection status [2]" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient", fakeTCPManagerActor.ref)))

      mqttManagerActor ! Connect("test", 30, cleanSession = false, Some(Will(retain = false, AtMostOnce, "test/topic", "test death")), None, None)

      mqttManagerActor ! Status

      expectMsg(Disconnected)
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
  }
}