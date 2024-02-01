ThisBuild / version := "1.0.2"
ThisBuild / organization := "ca.stevenskelton"
ThisBuild / scalaVersion := "3.3.1"

addArtifact(assembly / artifact, assembly)

val javaVersion = "17"

enablePlugins(ScalaNativePlugin)

// set to Debug for compilation details (Info is default)
logLevel := Level.Info

// import to add Scala Native options
import scala.scalanative.build._

// defaults set with common options shown
nativeConfig ~= { c =>
  c.withLTO(LTO.none) // thin
    .withMode(Mode.debug) // releaseFast
    .withGC(GC.immix) // commix
}

Compile / sourceGenerators += (Compile / sourceManaged).map {
  sourceDirectory =>
    val file = sourceDirectory / "SbtBuildInfo.scala"
    IO.write(file, """package ca.stevenskelton.httpmavenreceiver
                     |object SbtBuildInfo {
                     |  val version = "%s"
                     |  val name = "%s"
                     |}
                     |""".stripMargin.format(version, name))
    Seq(file)
}.taskValue

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
        "-source:3.0-migration",
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
    },
  )

////required by sconfig native
//nativeLinkStubs := true

//brew install llvm
//brew install s2n
val http4sVersion = "1.0.0-M40"

libraryDependencies ++= Seq(
  "org.http4s"              %%% "http4s-ember-client" % http4sVersion,
  "org.http4s"              %%% "http4s-ember-server" % http4sVersion,
  "org.http4s"              %%% "http4s-dsl"          % http4sVersion,
  "com.armanbilge"          %%% "epollcat"            % "0.1.4",
  "org.typelevel"           %%% "log4cats-core"       % "2.6.0",
  "co.fs2"                  %%% "fs2-io"              % "3.9.4",
  "org.scala-lang.modules"  %%% "scala-xml"           % "2.2.0",
  "org.scalatest"           %%% "scalatest"           % "3.3.0-alpha.1" % Test
)
