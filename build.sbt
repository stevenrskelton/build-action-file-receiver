ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "ca.stevenskelton"
ThisBuild / scalaVersion := "2.13.8"

addArtifact(assembly / artifact, assembly)

val javaVersion = "11"
val akkaVersion = "2.6.20"
val akkaHttpVersion = "10.2.10"

lazy val root = (project in file("."))
  .settings(
    name := "http-maven-receiver",
    scalacOptions += s"-target:jvm-$javaVersion",
    javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion),
    assembly / mainClass := Some ("ca.stevenskelton.httpmavenreceiver.Main"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs@_*) => MergeStrategy.discard
      //added to support Akka
      case "reference.conf" => MergeStrategy.concat
      case x => MergeStrategy.first
    },
    assembly / artifact := {
      val art = (assembly / artifact).value
      art.withClassifier(Some("assembly"))
    }
  )

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.4.2",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.4.1",
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
  "org.mockito" %% "mockito-scala" % "1.17.12" % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.14" % Test
)