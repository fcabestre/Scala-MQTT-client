package net.sigusr.mqtt

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.specs2.specification.{AfterExample, Scope}

class SpecsTestKit extends TestKit(ActorSystem("MQTTClient-system", ConfigFactory.parseString(config))) with ImplicitSender with Scope with AfterExample {

  class TestActorProxy(val actorBuilder: ActorContext => ActorRef) extends Actor {
    val child = actorBuilder(context)

    def receive = {
      case x if sender == child ⇒ testActor forward x
      case x ⇒ child forward x
    }
  }

  def after = system.shutdown()
  def clientActor = testActor
  def testActorProxy(actorBuilder: ActorContext => ActorRef) = system.actorOf(Props(new TestActorProxy(actorBuilder)))
}
