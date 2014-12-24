package net.sigusr.mqtt

import akka.actor.{Props, Actor}

class TcpActor(_receive : Actor.Receive) extends Actor {
  def receive = _receive
}

object TcpActor {
  def props(receive : Actor.Receive)  = Props(classOf[TcpActor], receive)
}
