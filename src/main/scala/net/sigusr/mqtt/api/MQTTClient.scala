package net.sigusr.mqtt.api

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import net.sigusr.mqtt.impl.protocol.{Protocol, TCPTransport}

class MQTTClient(source: ActorRef, remote: InetSocketAddress) extends TCPTransport(source, remote) with Protocol

object MQTTClient {
  def props(source: ActorRef, remote: InetSocketAddress) = Props(classOf[MQTTClient], source, remote)
}

