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

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import com.typesafe.config.ConfigFactory
import org.specs2.matcher.{ Expectable, Matcher }
import org.specs2.specification.{ AfterExample, Scope }
import scodec.Err

import scala.util.Random
import scalaz.\/

object SpecUtils {

  class SuccessfulDisjunctionMatcher[T](v: T) extends Matcher[\/[Err, T]] {
    def apply[S <: \/[Err, T]](e: Expectable[S]) = {
      result(e.value exists {
        _ == v
      }, s"${e.description} equals to $v", s"The result is ${e.description}, instead of the expected value '$v'", e)
    }
  }

  class FailedDisjunctionMatcher[T](m: Err) extends Matcher[\/[Err, T]] {
    def apply[S <: \/[Err, T]](e: Expectable[S]) = {
      result(~e.value exists {
        _ == m
      }, s"${e.description} equals to $m", s"The result is ${e.description} instead of the expected error message '$m'", e)
    }
  }

  def succeedWith[T](t: T) = new SuccessfulDisjunctionMatcher[T](t)

  def failWith[T](t: Err) = new FailedDisjunctionMatcher[T](t)

  val configDebug =
    """akka {
         loglevel = DEBUG
         actor {
            debug {
              receive = on
              autoreceive = off
              lifecycle = off
            }
         }
       }
    """

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

  class SpecsTestKit extends TestKit(ActorSystem("MQTTClient-system", ConfigFactory.parseString(config))) with ImplicitSender with Scope with AfterExample {
    def after = system.shutdown()
    def clientActor = testActor
  }

  def makeRandomByteVector(size: Int) = {
    val bytes = new Array[Byte](64)
    Random.nextBytes(bytes)
    bytes.to[Vector]
  }
}
