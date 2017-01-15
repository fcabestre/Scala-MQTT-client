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

package net.sigusr.mqtt.api

import java.net.InetSocketAddress

import akka.actor._
import net.sigusr.mqtt.SpecsTestKit
import org.specs2.mutable._

object ManagerSpec extends Specification {

  "An MQTT protocol manager" should {
    "Provide its status" in new SpecsTestKit {

      val manager = testActorProxy { context ⇒ context.actorOf(Manager.props(new InetSocketAddress(1883))) }

      manager ! Status
      expectMsg(Disconnected)
    }
  }
}
