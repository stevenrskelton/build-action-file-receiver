addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.1.10",
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0"
)