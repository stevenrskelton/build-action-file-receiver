ThisBuild / version := "1.0.1"
ThisBuild / organization := "ca.stevenskelton"
ThisBuild / scalaVersion := "3.3.1"

addArtifact(assembly / artifact, assembly)

val javaVersion = "17"
val pekkoHttpVersion = "1.0.0"
val pekkoVersion = "1.0.1"

lazy val root = (project in file("."))
  .settings(
    name := "http-maven-receiver",
    scalacOptions ++= {
      Seq(
        "-encoding", "UTF-8",
        "-deprecation",
        "-feature",
        "-unchecked",
        "-language:experimental.macros",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Ykind-projector",
        //        "-Yexplicit-nulls",
        "-Ysafe-init",
        //        "-Wvalue-discard",
        //        "-source:3.0-migration",
        // "-Xfatal-warnings"
      )
    },
    javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion),
    assembly / mainClass := Some ("ca.stevenskelton.httpmavenreceiver.Main"),
    assembly / assemblyMergeStrategy := {
      //Logback
      case PathList("META-INF", "services", xs@_*) if xs.lastOption.contains("ch.qos.logback.classic.spi.Configurator") => MergeStrategy.first
      case PathList("META-INF", "services", xs@_*) if xs.lastOption.contains("jakarta.servlet.ServletContainerInitializer") => MergeStrategy.first
      case PathList("META-INF", "services", xs@_*) if xs.lastOption.contains("org.slf4j.spi.SLF4JServiceProvider") => MergeStrategy.first
      //Others
      case PathList("META-INF", xs@_*) => MergeStrategy.discard
      //added to support Pekko
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
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.4.7",
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
  "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test,
  "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.15" % Test
)