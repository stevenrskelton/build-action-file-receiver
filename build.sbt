import sbt.TupleSyntax.t3ToTable3

ThisBuild / version := "1.2.1"
ThisBuild / organization := "ca.stevenskelton"
ThisBuild / scalaVersion := "3.5.1"

val javaVersion = "21"

// set to Debug for compilation details (Info is default)
logLevel := Level.Info

Compile / sourceGenerators += (Compile / sourceManaged, version, name).map {
  (sourceDirectory, version, name) =>
    val file = sourceDirectory / "SbtBuildInfo.scala"
    IO.write(file, """package ca.stevenskelton.buildactionfilereceiver
                     |object SbtBuildInfo {
                     |  val version = "%s"
                     |  val name = "%s"
                     |}
                     |""".stripMargin.format(version, name))
    Seq(file)
}.taskValue

val http4sVersion = "1.0.0-M41"

lazy val root = (project in file("."))
  .settings(
    name := "build-action-file-receiver",
    scalacOptions ++= {
      Seq(
        "-encoding", "UTF-8",
        "-deprecation",
        "-feature",
        "-unchecked",
        //"-indent",
        "-new-syntax", "-rewrite",
        //"-Yexplicit-nulls",
        "-Wsafe-init",
        "-Wunused:all",
        "-Wvalue-discard",
        "-Wnonunit-statement",
        "-Xfatal-warnings",
      )
    },
    Test / scalacOptions --= Seq(
      "-Wunused:all",
      "-Wnonunit-statement",
    ),
    Compile / mainClass := Some("ca.stevenskelton.buildactionfilereceiver.Main"),
    javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion),
    addArtifact(assembly / artifact, assembly),
//    addArtifact(nativeLink / artifact, nativeLink),
    assembly / mainClass := Some ("ca.stevenskelton.buildactionfilereceiver.Main"),
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
  "org.http4s"              %% "http4s-ember-client"  % http4sVersion,
  "org.http4s"              %% "http4s-ember-server"  % http4sVersion,
  "org.http4s"              %% "http4s-dsl"           % http4sVersion,
  "org.typelevel"           %% "log4cats-core"        % "2.7.0",
  "co.fs2"                  %% "fs2-io"               % "3.11.0",
  "org.scala-lang.modules"  %% "scala-xml"            % "2.3.0",
  "org.scalatest"           %% "scalatest"            % "3.3.0-alpha.1"   % Test,
  "org.typelevel"           %% "cats-effect-testing-scalatest" % "1.5.0"  % Test,
)

enablePlugins(DisabledScalaNativePlugin)
enablePlugins(NativeImagePlugin)

//nativeImageVersion := "21.0.3"
//nativeImageVersion := "21.0.2+13.1"
//nativeImageGraalHome := file("/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.2+13.1/Contents/Home").toPath
//nativeImageGraalHome := file("/opt/hostedtoolcache/graalvm-jdk-21_linux-x64_bin/21.0.0/x64/graalvm-jdk-21.0.2+13.1").toPath
//nativeImageGraalHome := file("/usr/src/graalvm-community-openjdk-23+37.1").toPath
nativeImageGraalHome := scala.sys.env.get("GRAALVM_HOME").fold(throw new Exception("Set GRAALVM_HOME to the directory where GraalVM is installed"))(file(_).toPath)

nativeImageOptions ++= List(
//  "--static",
  "--verbose",
  "--allow-incomplete-classpath",
  "--report-unsupported-elements-at-runtime",
  "--no-fallback",
  "-march=compatibility",
)
/*


//required by sconfig native
//nativeLinkStubs := true

//brew install llvm
//brew install s2n

//https://fs2.io/#/io?id=tls

enablePlugins(ScalaNativePlugin)


enablePlugins(ScalaNativeJUnitPlugin)
testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-s", "-v")

import scala.scalanative.build._

// defaults set with common options shown
nativeConfig ~= { c =>
  c.withLTO(LTO.none) // thin
    .withMode(Mode.debug) // releaseFast
    .withGC(GC.immix) // commix
}

nativeLinkingOptions += s"-L/home/runner/work/build-action-file-receiver/build-action-file-receiver/s2n-tls/s2n-tls-install/lib"

libraryDependencies ++= Seq(
  "com.armanbilge"          %%% "epollcat"            % "0.1.4",
  "org.http4s"              %%% "http4s-ember-client" % http4sVersion,
  "org.http4s"              %%% "http4s-ember-server" % http4sVersion,
  "org.http4s"              %%% "http4s-dsl"          % http4sVersion,
  "org.typelevel"           %%% "log4cats-core"       % "2.6.0",
  "co.fs2"                  %%% "fs2-io"              % "3.9.4",
  "org.scala-lang.modules"  %%% "scala-xml"           % "2.2.0",
  "org.scalatest"           %%% "scalatest"           % "3.3.0-alpha.1" % Test,
  "org.typelevel"           %%% "cats-effect-testing-scalatest" % "1.5.0"  % Test,
)
*/
