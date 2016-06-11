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

package net.sigusr.mqtt

import akka.actor._
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.specs2.specification.{AfterEach, Scope}

class SpecsTestKit extends TestKit(ActorSystem("MQTTClient-system", ConfigFactory.parseString(config))) with ImplicitSender with Scope with AfterEach {

  implicit val materializer = ActorMaterializer()

  class TestActorProxy(val actorBuilder: ActorContext => ActorRef) extends Actor {
    val child = actorBuilder(context)

    def receive = {
      case x if sender == child ⇒ testActor forward x
      case x ⇒ child forward x
    }
  }

  def after = system.terminate()
  def clientActor = testActor
  def testActorProxy(actorBuilder: ActorContext => ActorRef) = system.actorOf(Props(new TestActorProxy(actorBuilder)))
}
