logLevel := Level.Warn

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.5")

//Auto Formatting Scalariform plugin.
//resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

// The ScalaStyle plugin
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")

// The SBT-Scoverage Code Coverage Plugin
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")
