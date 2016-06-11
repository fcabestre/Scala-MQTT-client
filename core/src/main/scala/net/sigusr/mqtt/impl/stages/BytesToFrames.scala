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
package net.sigusr.mqtt.impl.stages

import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.util.ByteString
import net.sigusr.mqtt.impl.frames.Frame
import scodec.Attempt.{ Failure, Successful }
import scodec.Err.InsufficientBits
import scodec.bits.BitVector
import scodec.{ Attempt, Codec, DecodeResult, Err }

import scala.annotation.tailrec

class BytesToFrames extends GraphStage[FlowShape[ByteString, Frame]] {

  val in = Inlet[ByteString]("BytesToFrames.in")
  val out = Outlet[Frame]("BytesToFrames.out")

  def decode(bits: BitVector): Attempt[DecodeResult[Vector[Frame]]] = {
    @tailrec
    def decodeAux(bits: BitVector, decodedFrames: Vector[Frame]): Attempt[DecodeResult[Vector[Frame]]] =
      Codec[Frame].decode(bits) match {
        case Successful(value) ⇒
          if (value.remainder.isEmpty)
            Attempt.successful(DecodeResult(decodedFrames :+ value.value, value.remainder))
          else
            decodeAux(value.remainder, decodedFrames :+ value.value)
        case f @ Failure(cause) ⇒ cause match {
          case _: InsufficientBits ⇒ Attempt.successful(DecodeResult(decodedFrames, bits))
          case _ ⇒ f
        }
      }
    decodeAux(bits, Vector())
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler with InHandler {

      var remainingBytes = BitVector.empty

      def onSuccess(result: DecodeResult[Vector[Frame]]): Unit = {
        remainingBytes = result.remainder
        if (result.value.nonEmpty)
          emitMultiple(out, result.value)
        tryPull(in)
      }

      def onError(error: Err) = fail(out, new Exception(error.messageWithContext))

      override def onPush(): Unit = {
        val bits = remainingBytes ++ BitVector(grab(in).asByteBuffer)
        decode(bits).fold(onError, onSuccess)
      }

      override def onPull(): Unit = if (!hasBeenPulled(in)) tryPull(in)

      setHandler(out, this)
      setHandler(in, this)
    }

  override def shape: FlowShape[ByteString, Frame] = FlowShape(in, out)
}
