package messages

import shapeless.Iso

/**
 *
 * @author Frédéric Cabestre
 */
case class Header(messageType : Int, dup : Boolean, qos : Int, retain : Boolean, remainingLength : Int)

object Header {
  implicit val hlistIso = Iso.hlist(Header.apply _, Header.unapply _)
}