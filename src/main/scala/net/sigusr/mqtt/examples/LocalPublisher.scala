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

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Random

class LocalPublisher(toPublish : Vector[String]) extends Actor {

  import context.dispatcher

  context.actorOf(Manager.props(new InetSocketAddress(1883)))

  val length = toPublish.length

  def scheduleRandomMessage() = {
    val message = toPublish(Random.nextInt(length))
    context.system.scheduler.scheduleOnce(FiniteDuration(Random.nextInt(2000) + 1000, MILLISECONDS), self, message)
  }

  def receive: Receive = {
    case Ready =>
      sender() ! Connect(localSubscriber)
    case Connected =>
      println("Successfully connected to localhost:1883")
      println(s"Ready to publish to topic [ $localPublisher ]")
      scheduleRandomMessage()
      context become ready(sender())
    case ConnectionFailure(reason) =>
      println(s"Connection to localhost:1883 failed [$reason]")
  }

  def ready(mqttManager: ActorRef): Receive = {
    case m : String =>
      println(s"Publishing [ $m ]")
      mqttManager ! Publish(localPublisher, m.getBytes("UTF-8").to[Vector])
      scheduleRandomMessage()
  }

}

object LocalPublisher {

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

  val system = ActorSystem(localPublisher, ConfigFactory.parseString(config))

  def shutdown(): Unit = {
    system.shutdown()
    println(s"<$localPublisher> stopped")
  }

  def main(args : Array[String]) = {
    system.actorOf(Props(classOf[LocalPublisher], args.to[Vector]))
    sys.addShutdownHook { shutdown() }
    println(s"<$localPublisher> started")
  }
}


