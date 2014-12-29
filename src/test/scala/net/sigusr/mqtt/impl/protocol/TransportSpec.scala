package net.sigusr.mqtt.impl.protocol

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, ActorRef, Props}
import akka.io.Tcp._
import akka.testkit.{ImplicitSender, TestProbe}
import akka.util.ByteString
import net.sigusr.mqtt.SpecUtils.SpecsTestKit
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.protocol.Transport.PingRespTimeout
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import scala.language.reflectiveCalls

object TransportSpec extends Specification with NoTimeConversions {

  sequential
  isolated

  private val fakeBrokerAddress : InetSocketAddress = new InetSocketAddress(0)
  private val fakeLocalAddress : InetSocketAddress = new InetSocketAddress(0)

  class TestMQTTManager(clientActor: ActorRef, _tcpManagerActor: ActorRef) extends TCPTransport(clientActor, fakeBrokerAddress) with Protocol {
    override def tcpManagerActor: ActorRef = _tcpManagerActor
  }

  object TestMQTTManager {
    def props(clientActor : ActorRef, tcpManagerActor: ActorRef) = Props(classOf[TestMQTTManager], clientActor, tcpManagerActor)
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
              sender() ! PingRespTimeout
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

  "The TCPTransport" should {

    "Exchange messages during a successful initialisation" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(TestMQTTManager.props(clientActor, fakeTCPManagerActor.ref), "MQTTClient-0")

      fakeTCPManagerActor.expectConnect()
      fakeTCPManagerActor.expectRegister()
      expectMsg(MQTTReady)
    }

    "Exchange messages during a failed initialisation" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(TestMQTTManager.props(clientActor, fakeTCPManagerActor.ref), "MQTTClient-1")

      fakeTCPManagerActor.expectConnectThenFail()
      expectMsg(MQTTNotReady)
    }

    "After a successful initialisation connect and then disconnect" in new SpecsTestKit {
      val fakeTCPManagerActor = new FakeTCPManagerActor
      val mqttManagerActor = system.actorOf(TestMQTTManager.props(clientActor, fakeTCPManagerActor.ref), "MQTTClient-2")

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
      val mqttManagerActor = system.actorOf(TestMQTTManager.props(clientActor, fakeTCPManagerActor.ref), "MQTTClient-3")

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