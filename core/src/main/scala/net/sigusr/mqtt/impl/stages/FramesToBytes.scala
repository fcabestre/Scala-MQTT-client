package net.sigusr.mqtt.impl.stages

import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.util.ByteString
import net.sigusr.mqtt.impl.frames.Frame
import scodec.Codec

class FramesToBytes extends GraphStage[FlowShape[Frame, ByteString]] {

  val in = Inlet[Frame]("FramesToBytes.in")
  val out = Outlet[ByteString]("FramesToBytes.out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler with InHandler {

      @throws[Exception](classOf[Exception])
      override def onPull(): Unit = if (!hasBeenPulled(in)) tryPull(in)

      @throws[Exception](classOf[Exception])
      override def onPush(): Unit = {
        val result = Codec[Frame].encode(grab(in)).require
        push(out, ByteString(result.toByteArray))
        tryPull(in)
      }

      setHandler(out, this)
      setHandler(in, this)
    }

  override def shape: FlowShape[Frame, ByteString] = FlowShape(in, out)
}
