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

import org.specs2.matcher.{ Expectable, Matcher }
import scodec.{ Attempt, Err }

import scala.util.Random

object SpecUtils {

  class SuccessfulAttemptMatcher[T](v: T) extends Matcher[Attempt[T]] {
    def apply[S <: Attempt[T]](e: Expectable[S]) = {
      result(
        e.value.fold(_ ⇒ false, _ == v),
        s"${e.description} equals to $v",
        s"The result is ${e.description}, instead of the expected value '$v'",
        e
      )
    }
  }

  class FailedAttemptMatcher[T](m: Err) extends Matcher[Attempt[T]] {
    def apply[S <: Attempt[T]](e: Expectable[S]) = {
      result(
        e.value.fold(_ ⇒ true, _ != m),
        s"${e.description} equals to $m",
        s"The result is ${e.description} instead of the expected error message '$m'",
        e
      )
    }
  }

  def succeedWith[T](t: T) = new SuccessfulAttemptMatcher[T](t)

  def failWith[T](t: Err) = new FailedAttemptMatcher[T](t)

  def makeRandomByteVector(size: Int) = {
    val bytes = new Array[Byte](size)
    Random.nextBytes(bytes)
    bytes.to[Vector]
  }
}
