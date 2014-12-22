package net.sigusr.mqtt.impl.protocol

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.io.Tcp.{Register, Connected, Connect}
import akka.testkit.TestActorRef
import net.sigusr.mqtt.SpecUtils.SpecsTestKit
import net.sigusr.mqtt.api.MQTTReady
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

object TransportSpec extends Specification with NoTimeConversions {

  private val fakeBrokerAddress : InetSocketAddress = new InetSocketAddress(0)

  class TestClient(testActor: ActorRef) extends TCPTransport(testActor, fakeBrokerAddress) with Client with Protocol {
    override def tcpActor: ActorRef = testActor
  }

  object TestClient {
    def props(testActor : ActorRef) = Props(classOf[TestClient], testActor)
  }

  "The TCPTransport" should {

    "Exchange messages during initialisation" in new SpecsTestKit {
      val clientRef = TestActorRef[TestClient](TestClient.props(testActor), "MQTTClient-service")
      val client = clientRef.underlyingActor
      expectMsg(Connect(fakeBrokerAddress, None, Nil, None, pullMode = false))
      clientRef.receive(Connected(fakeBrokerAddress, fakeBrokerAddress))
      expectMsg(MQTTReady)
    }

  }
}