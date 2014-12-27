package net.sigusr.mqtt.impl.protocol

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.io.Tcp._
import akka.util.ByteString
import net.sigusr.mqtt.SpecUtils.SpecsTestKit
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.impl.protocol.Transport.PingRespTimeout
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

object TransportSpec extends Specification with NoTimeConversions {

  sequential
  isolated

  private val fakeBrokerAddress : InetSocketAddress = new InetSocketAddress(0)
  private val fakeLocalAddress : InetSocketAddress = new InetSocketAddress(0)

  class TestMQTTClient(clientActor: ActorRef, _tcpActor: ActorRef) extends TCPTransport(clientActor, fakeBrokerAddress) with Client with Protocol {
    override def tcpManagerActor: ActorRef = _tcpActor
  }

  object TestMQTTClient {
    def props(clientActor : ActorRef, tcpActor: ActorRef) = Props(classOf[TestMQTTClient], clientActor, tcpActor)
  }

  "The TCPTransport" should {

    "Exchange messages during a successful initialisation" in new SpecsTestKit {
      lazy val mqttClientRef = system.actorOf(TestMQTTClient.props(clientActor, tcpManagerActor), "MQTTClient-0")
      val tcpManagerActor : ActorRef = tcpActor {
        case Connect(remote, _, _, _, _) =>
          remote should be_==(fakeBrokerAddress)
          mqttClientRef ! Connected(fakeBrokerAddress, fakeLocalAddress)
      }

      // Required to initialize the lazy val
      mqttClientRef
      expectMsg(MQTTReady)
    }

    "Exchange messages during a failed initialisation" in new SpecsTestKit {
      lazy val mqttClientRef = system.actorOf(TestMQTTClient.props(clientActor, tcpManagerActor), "MQTTClient-1")
      private val tcpManagerActor : ActorRef = tcpActor {
        case Connect(remote, _, _, _, _) =>
          remote should be_==(fakeBrokerAddress)
          val connect = Connect(fakeBrokerAddress, None, Nil, None, pullMode = false)
          mqttClientRef ! CommandFailed(connect)
      }

      // Required to initialize the lazy val
      mqttClientRef
      expectMsg(MQTTNotReady)
    }

    "After a successful initialisation connect and then disconnect" in new SpecsTestKit {
      val connackFrame = ByteString(0x20, 0x02, 0x00, 0x00)
      lazy val mqttClientRef = system.actorOf(TestMQTTClient.props(clientActor, tcpManagerActor), "MQTTClient-2")
      val tcpManagerActor : ActorRef = tcpActor {
        case Connect(remote, _, _, _, _) =>
          remote should be_==(fakeBrokerAddress)
          mqttClientRef ! Connected(fakeBrokerAddress, fakeLocalAddress)
        case Write(byteString, _) =>
          val byte = byteString(0)
          if (byte == 0x10) {
            mqttClientRef ! Received(connackFrame)
          }
          // Why when I write 0xe0 instead of -32
          // here things go really wrong ?
          else if (byte == -32) {
            mqttClientRef ! Closed
          }
      }

      // Required to initialize the lazy val
      mqttClientRef
      expectMsg(MQTTReady)
      mqttClientRef ! MQTTConnect("test", 30, cleanSession = false, Some("test/topic"), Some("test death"), None, None)
      expectMsg(MQTTConnected)
      mqttClientRef ! MQTTDisconnect
      expectMsg(MQTTDisconnected)
    }

    "After a successful initialisation connect, ping the server and disconnect when the server stops replying" in new SpecsTestKit {
      val connackFrame = ByteString(0x20, 0x02, 0x00, 0x00)
      val pingRespFrame = ByteString(0xd0, 0x00)
      lazy val mqttClientRef = system.actorOf(TestMQTTClient.props(clientActor, tcpManagerActor), "MQTTClient-3")
      var pingReqCount = 0
      val tcpManagerActor : ActorRef = tcpActor {
        case Connect(remote, _, _, _, _) =>
          remote should be_==(fakeBrokerAddress)
          mqttClientRef ! Connected(fakeBrokerAddress, fakeLocalAddress)
        case Write(byteString, _) =>
          val byte: Byte = byteString(0)
          if (byte == 0x10) {
            mqttClientRef ! Received(connackFrame)
          }
          else if (byte == 0xc0) {
            if (pingReqCount == 0) {
              mqttClientRef ! Received(pingRespFrame)
            }
            else if (pingReqCount == 1) {
              // What should be the sender here ?
              mqttClientRef ! PingRespTimeout
            }
            pingReqCount += 1
          }
        case Close =>
          mqttClientRef ! Aborted
      }

      // Required to initialize the lazy val
      mqttClientRef
      expectMsg(MQTTReady)
      mqttClientRef ! MQTTConnect("test", 1, cleanSession = false, Some("test/topic"), Some("test death"), None, None)
      expectMsg(MQTTConnected)
      expectMsg(MQTTDisconnected)
    }
  }
}