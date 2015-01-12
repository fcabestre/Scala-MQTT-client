import sbt._
import Keys._

object CustomBuild extends Build
{
  lazy val root =
    Project("root", file("."))
      .configs( IntegrationTest )
      .settings( Defaults.itSettings : _*)
      .settings( libraryDependencies += specs )

  lazy val specs =   "org.specs2" %% "specs2" % "2.4.15" % "it,test"
}