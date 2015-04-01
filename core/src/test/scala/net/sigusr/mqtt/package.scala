package net.sigusr

package object mqtt {
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
}
