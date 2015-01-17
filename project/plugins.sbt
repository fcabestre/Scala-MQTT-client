addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.0.0.BETA1")

resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
    Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

