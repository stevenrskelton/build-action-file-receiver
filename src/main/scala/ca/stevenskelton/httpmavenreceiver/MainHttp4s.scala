package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.logging.StdOutLoggerFactory
import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.typelevel.log4cats.*

import java.io.File

object MainHttp4s extends IOApp {

  implicit val loggerFactory: LoggerFactory[IO] = StdOutLoggerFactory()

  private val logger = loggerFactory.getLoggerFromClass(getClass)

  private val DefaultAllowedUploadSize = 1024000L

  override def run(args: List[String]): IO[ExitCode] = {

    println(s"${SbtBuildInfo.name} ${SbtBuildInfo.version}")
    val argMap = args.map(_.split('=')).filter(_.length == 2).map {
      arr => (arr(0), arr(1))
    }.toMap

    val uploadDirectory: File = new File(argMap.getOrElse("directory", "."))

    val host: Ipv4Address = Ipv4Address.fromString(argMap.getOrElse("host", {
      logger.warn("No `host` config value found, binding to localhost")
      "0.0.0.0"
    })).get
    val port: Port = Port.fromString(argMap.getOrElse("port", {
      logger.warn("No `port` config value found, binding to 8080")
      "8080"
    })).get

    val maxUploadByteSize = argMap.get("maxsize").map(_.toLong).getOrElse({
      logger.info(s"No `http-maven-receiver.max-upload-size` config value found, setting to default ${Utils.humanFileSize(DefaultAllowedUploadSize)}")
      DefaultAllowedUploadSize
    })

    if (!uploadDirectory.exists) {
      logger.info(s"Creating file directory: ${uploadDirectory.getAbsolutePath}")
      if (!uploadDirectory.mkdirs) {
        logger.error(s"Could not create directory")
        System.exit(1)
      }
    }

    logger.info(s"Setting file directory to: ${uploadDirectory.getAbsolutePath} with max upload size: ${Utils.humanFileSize(maxUploadByteSize)}")

    val http4sArtifactUploadRoute = Http4sArtifactUploadRoute(
      fs2.io.file.Path.fromNioPath(uploadDirectory.toPath),
      maxUploadByteSize,
      PostUploadActions(),
    )(loggerFactory)

    val httpApp = HttpRoutes.of[IO] {
      case request@PUT -> Root => http4sArtifactUploadRoute.releasesPut(request)
    }.orNotFound

    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
