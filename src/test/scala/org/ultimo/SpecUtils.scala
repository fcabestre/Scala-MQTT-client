package org.ultimo

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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.specs2.matcher.{Matchers, Expectable, Matcher}

import scalaz.\/

object SpecUtils {

  class SuccessfulDisjunctionMatcher[T](v : T) extends Matcher[\/[String, T]] {
    def apply[S <: \/[String, T]](e: Expectable[S]) = {
      result(e.value exists { _ == v }, s"${e.description} equals to $v", s"The result is ${e.description}, instead of the expected value '$v'", e)
    }
  }

  class FailedDisjunctionMatcher[T](m : String) extends Matcher[\/[String, T]] {
    def apply[S <: \/[String, T]](e: Expectable[S]) = {
      result(~e.value exists { _ == m }, s"${e.description} equals to $m", s"The result is ${e.description} instead of the expected error message '$m'", e)
    }
  }

  def succeedWith[T](t: T) = new SuccessfulDisjunctionMatcher[T](t)
  def failWith[T](t: String) = new FailedDisjunctionMatcher[T](t)
}
