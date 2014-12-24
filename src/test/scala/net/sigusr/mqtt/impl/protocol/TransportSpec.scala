package net.sigusr.mqtt.impl.protocol

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.io.Tcp._
import akka.testkit.TestActorRef
import akka.util.ByteString
import net.sigusr.mqtt.SpecUtils.SpecsTestKit
import net.sigusr.mqtt.api._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scodec.bits.BitVector

object TransportSpec extends Specification with NoTimeConversions {

  private val fakeBrokerAddress : InetSocketAddress = new InetSocketAddress(0)

  class TestClient(clientActor: ActorRef, _tcpActor: ActorRef) extends TCPTransport(clientActor, fakeBrokerAddress) with Client with Protocol {
    override def tcpActor: ActorRef = _tcpActor
  }

  object TestClient {
    def props(testActor : ActorRef, tcpActor: ActorRef) = Props(classOf[TestClient], testActor, tcpActor)
  }

  "The TCPTransport" should {

    "Exchange messages during a successful initialisation" in new SpecsTestKit {
      private val _tcpActor = tcpActor {
        case Connect(remote, _, _, _, _) =>
          remote should_== fakeBrokerAddress
      }
      val clientRef = TestActorRef[TestClient](TestClient.props(testActor, _tcpActor), "MQTTClient-service")
      val client = clientRef.underlyingActor
      clientRef.receive(Connected(fakeBrokerAddress, fakeBrokerAddress))
      expectMsg(MQTTReady)
    }

    "Exchange messages during a failed initialisation" in new SpecsTestKit {
      private val _tcpActor = tcpActor {
        case Connect(remote, _, _, _, _) =>
          remote should_== fakeBrokerAddress //Connect(fakeBrokerAddress, None, Nil, None, pullMode = false)
      }
      val clientRef = TestActorRef[TestClient](TestClient.props(testActor, _tcpActor), "MQTTClient-service")
      val client = clientRef.underlyingActor
      private val connect = Connect(fakeBrokerAddress, None, Nil, None, pullMode = false)
      clientRef.receive(CommandFailed(connect))
      expectMsg(MQTTNotReady)
    }

    "After a successful initialisation connect and then disconnect" in new SpecsTestKit {
      val connectFrame = ByteString(0x10, 0x2a, 0x00, 0x06, 0x4d, 0x51, 0x49, 0x73, 0x64, 0x70, 0x03, 0x2c, 0x00, 0x3c, 0x00, 0x04, 0x74, 0x65, 0x73, 0x74, 0x00, 0x0a, 0x74, 0x65, 0x73, 0x74, 0x2f, 0x74, 0x6f, 0x70, 0x69, 0x63, 0x00, 0x0a, 0x74, 0x65, 0x73, 0x74, 0x20, 0x64, 0x65, 0x61, 0x74, 0x68)
      val connackFrame = ByteString(0x20, 0x02, 0x00, 0x00)
      private val _tcpActor = tcpActor {
        case Connect(remote, _, _, _, _) =>
          remote should_== fakeBrokerAddress
        case Write(byteString, _) =>
          byteString should_== connectFrame
      }
      val clientRef = TestActorRef[TestClient](TestClient.props(testActor, _tcpActor), "MQTTClient-service")
      val client = clientRef.underlyingActor
      clientRef.receive(Connected(fakeBrokerAddress, fakeBrokerAddress))
      expectMsg(MQTTReady)
      clientRef.receive(MQTTConnect("test", 30, cleanSession = false, Some("test/topic"), Some("test death"), None, None))
      clientRef.receive(Received(connackFrame))
      expectMsg(MQTTConnected)
    }
  }
}