package net.sigusr.mqtt.impl.protocol

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.io.Tcp.Connect
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

    "Send messages to the client when processing a SendToClient action" in new SpecsTestKit {
      val client = TestActorRef[TestClient](TestClient.props(testActor), "MQTTClient-service").underlyingActor
      expectMsg(Connect(fakeBrokerAddress, None, Nil, None, pullMode = false))
      client.processAction(testActor, testActor, SendToClient(MQTTReady))
      expectMsg(MQTTReady)
    }

  }
}