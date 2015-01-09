package net.sigusr.mqtt

package object api {

  /**
   * Default inactivity interval before sending a PINGREQ to the broker. 'Inactivity'
   * means the client doesn't send any message to the broker during this period. This
   * duration is expressed in seconds.
   */
  val DEFAULT_KEEP_ALIVE : Int = 30

  val zeroId = MessageId(0)

  implicit def asMessageIdentifier(int : Int) : MessageId = MessageId(int)

  implicit class MessageIdentifierLiteral(val sc: StringContext) extends AnyVal {
    def mi(args: Any*): MessageId = {
      val strings = sc.parts.iterator
      val expressions = args.iterator
      val buf = new StringBuffer(strings.next())
      while (strings.hasNext) {
        buf append expressions.next
        buf append strings.next
      }
      MessageId(buf.toString.toInt)
    }
  }
}
