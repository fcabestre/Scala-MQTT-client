import org.specs2.matcher.{Expectable, Matcher}
import scala.Predef._
import scalaz.\/

/**
 *
 * @author Frédéric Cabestre
 */
object SpecUtils {

  class DisjunctionMatcher[T](v : T, f : \/[_, _] => \/[_, _]) extends Matcher[\/[String, T]] {
    def apply[S <: \/[String, T]](e: Expectable[S]) = {
      result(f(e.value) exists { _ == v }, e.description + " equals to " + e, e.description + " does not equal to " + e, e)
    }
  }

  def succeedWith[T](t: T) = new DisjunctionMatcher(t, identity)
  def failWith(t: String) = new DisjunctionMatcher(t, _.swap)
}
