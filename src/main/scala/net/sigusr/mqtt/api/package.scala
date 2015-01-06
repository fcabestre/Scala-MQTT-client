package net.sigusr.mqtt

package object api {

  /**
   * Default inactivity interval before sending a PINGREQ to the broker. 'Inactivity'
   * means the client doesn't send any message to the broker during this period. This
   * duration is expressed in seconds.
   */
  val DEFAULT_KEEP_ALIVE : Int = 30
}
