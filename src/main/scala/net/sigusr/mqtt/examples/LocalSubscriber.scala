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

package net.sigusr.mqtt.examples

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import net.sigusr.mqtt.api._

class LocalSubscriber(topics : Vector[String]) extends Actor {

  val stopTopic: String = s"$actorName/stop"

  context.actorOf(Manager.props(new InetSocketAddress(1883)))

  def receive: Receive = {
    case Ready =>
      sender() ! Connect(actorName)
    case Connected =>
      println("Successfully connected to localhost:1883")
      sender() ! Subscribe((stopTopic +: topics) zip Vector.fill(topics.length + 1) {AtMostOnce}, 1)
      context become ready(sender())
    case ConnectionFailure(reason) =>
      println(s"Connection to localhost:1883 failed [$reason]")
  }

  def ready(mqttManager: ActorRef): Receive = {
    case Subscribed(vQoS, MessageId(1)) =>
      println("Successfully subscribed to topics:")
      println(topics.mkString(" ", ",\n ", ""))
    case Message(`stopTopic`, _) =>
      mqttManager ! Disconnect
      context become disconnecting
    case Message(topic, payload) =>
      val message = new String(payload.to[Array], "UTF-8")
      println(s"[$topic] $message")
  }

  def disconnecting(): Receive = {
    case Disconnected =>
      println("Disconnected from localhost:1883")
      LocalSubscriber.shutdown()
  }
}

object LocalSubscriber {

  val config =
    """akka {
         loglevel = INFO
         actor {
            debug {
              receive = off
              autoreceive = off
              lifecycle = off
            }
         }
       }
    """
  val system = ActorSystem(actorName, ConfigFactory.parseString(config))

  def shutdown(): Unit = {
    system.shutdown()
    println(s"<$actorName> stopped")
  }

  def main(args : Array[String]) = {
    system.actorOf(Props(classOf[LocalSubscriber], args.to[Vector]))
    sys.addShutdownHook { shutdown() }
    println(s"<$actorName> started")
  }
}
