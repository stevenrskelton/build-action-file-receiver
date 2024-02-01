addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.16")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.1.11",
  "org.scala-lang.modules" %% "scala-xml" % "2.2.0"
)