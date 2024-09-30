//Fat-Jar Assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.2.0")

//GraalVM
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.4")

//Scala Native
//addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.16")
//addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.1.11",
  "org.scala-lang.modules" %% "scala-xml" % "2.3.0"
)