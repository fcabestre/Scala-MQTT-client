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
import akka.io.Tcp._
import akka.testkit.{ImplicitSender, TestProbe}
import akka.util.ByteString
import net.sigusr.mqtt.SpecUtils.SpecsTestKit
import net.sigusr.mqtt.api._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import scala.language.reflectiveCalls

object TransportSpec extends Specification with NoTimeConversions {

  sequential
  isolated

  private val fakeBrokerAddress : InetSocketAddress = new InetSocketAddress(0)
  private val fakeLocalAddress : InetSocketAddress = new InetSocketAddress(0)

  class TestMQTTManager(_tcpManagerActor: ActorRef) extends TCPTransport(fakeBrokerAddress) with Protocol {
    override def tcpManagerActor: ActorRef = _tcpManagerActor
  }

  class FakeTCPManagerActor(implicit system : ActorSystem) extends TestProbe(system) with ImplicitSender {
    
    val connackFrame = ByteString(0x20, 0x02, 0x00, 0x00)
    val pingRespFrame = ByteString(0xd0, 0x00)
    var pingReqCount = 0

    def expectConnect(): Unit = {
      expectMsgPF() {
        case Connect(remote, _, _, _, _) =>
          remote should be_==(fakeBrokerAddress)
          sender() ! Connected(fakeBrokerAddress, fakeLocalAddress)
      }
    }

    def expectConnectThenFail(): Unit = {
      expectMsgPF() {
        case c @ Connect(remote, _, _, _, _) =>
          remote should be_==(fakeBrokerAddress)
          sender() ! CommandFailed(c)
      }
    }

    def expectRegister(): Unit = {
      expectMsgPF() {
        case Register(_, _, _) =>
      }
    }

    def expectWriteConnectFrame(): Unit = {
      expectMsgPF() {
        case Write(byteString, _) =>
          if (byteString(0) == 0x10) {
            sender() ! Received(connackFrame)
          }
      }
    }

    def expectWritePingReqFrame(): Unit = {
      expectMsgPF() {
        case Write(byteString, _) =>
          if (byteString(0) == -64) {
            if (pingReqCount == 0) {
              sender() ! Received(pingRespFrame)
            }
            else if (pingReqCount == 1) {
              // What should be the sender here ?
//              sender() ! TimerSignal
            }
            pingReqCount += 1
          }
      }
    }

    def expectWriteDisconnectFrame(): Unit = {
      expectMsgPF() {
        case Write(byteString, _) =>
          // Why when I write 0xe0 instead of -32
          // here things go really wrong ?
          if (byteString(0) == -32) {
            sender() ! Closed
          }
      }
    }

    def expectClose(): Unit = {
      expectMsgPF() {
        case Close => sender() ! Aborted
      }
    }
  }

  class FakeMQTTManagerParent(testMQTTManagerName : String, fakeTCPManagerActor : ActorRef)(implicit testActor : ActorRef) extends Actor {
    val child = context.actorOf(Props(new TestMQTTManager(fakeTCPManagerActor)), testMQTTManagerName)
    def receive = {
      case x if sender == child => testActor forward x
      case x => child forward x
    }
  }

  "The TCPTransport" should {

    "Exchange messages during a successful initialisation" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-0", fakeTCPManagerActor.ref)))

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      expectMsg(MQTTReady)
    }

    "Exchange messages during a failed initialisation" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-1", fakeTCPManagerActor.ref)))

      fakeTCPManagerActor.expectConnectThenFail()
      expectMsg(MQTTNotReady)
    }

    "After a successful initialisation connect and then disconnect" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-2", fakeTCPManagerActor.ref)))

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      expectMsg(MQTTReady)
      mqttManagerActor ! MQTTConnect("test", 30, cleanSession = false, Some("test/topic"), Some("test death"), None, None)
      fakeTCPManagerActor.expectWriteConnectFrame()
      expectMsg(MQTTConnected)
      mqttManagerActor ! MQTTDisconnect
      fakeTCPManagerActor.expectWriteDisconnectFrame()
      expectMsg(MQTTDisconnected)
    }

    "After a successful initialisation connect, ping the server and disconnect when the server stops replying" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(Props(new FakeMQTTManagerParent("MQTTClient-3", fakeTCPManagerActor.ref)))

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      expectMsg(MQTTReady)
      mqttManagerActor ! MQTTConnect("test", 1, cleanSession = false, Some("test/topic"), Some("test death"), None, None)
      fakeTCPManagerActor.expectWriteConnectFrame()
      expectMsg(MQTTConnected)
      fakeTCPManagerActor.expectWritePingReqFrame()
      fakeTCPManagerActor.expectWritePingReqFrame()
      fakeTCPManagerActor.expectClose()
      expectMsg(MQTTDisconnected)
    }
  }
}