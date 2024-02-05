package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.Main.loggerFactory.LoggerType
import ca.stevenskelton.httpmavenreceiver.logging.StdOutLoggerFactory
import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.{Ipv4Address, Port}
import epollcat.EpollApp
import org.http4s.dsl.impl./
import org.http4s.dsl.io.{->, PUT, Root}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.{HttpApp, HttpRoutes}
import org.typelevel.log4cats.LoggerFactory

import java.io.File

object Main extends EpollApp /*IOApp*/ {

  implicit val loggerFactory: LoggerFactory[IO] = StdOutLoggerFactory()

  val logger: LoggerType = loggerFactory.getLoggerFromClass(getClass)

  private val DefaultAllowedUploadSize = 30 * 1024 * 1024

  def httpApp(handler: RequestHandler): HttpApp[IO] = HttpRoutes.of[IO] {
    case request@PUT -> Root / "releases" => handler.releasesPut(request)
  }.orNotFound

  override def run(args: List[String]): IO[ExitCode] = {

    logger.info(s"${SbtBuildInfo.name} ${SbtBuildInfo.version}")

    val argMap = args
      .filter(_.startsWith("--"))
      .map {
        argument =>
          val equalsChar = argument.indexOf("=")
          if (equalsChar > -1) {
            val (key, value) = argument.splitAt(equalsChar)
            (key.drop(2), value.drop(1))
          } else {
            (argument.drop(2), "")
          }
      }.toMap

    if (argMap.contains("help")) {
      logger.info(
        """
          |Command line arguments:
          |  --help
          |  --disable-maven
          |  --host=[STRING]
          |  --port=[INTEGER]
          |  --max-upload-size=[STRING]
          |  --upload-directory=[STRING]
          |""".stripMargin)
      return IO.pure(ExitCode.Success)
    }

    val disableMaven: Boolean = argMap.contains("disable-maven")

    val host: Ipv4Address = Ipv4Address.fromString(argMap.getOrElse("host", "0.0.0.0"))
      .getOrElse {
        logger.error(s"Invalid host: ${argMap("host")}")
        return IO.pure(ExitCode.Error)
      }

    val port: Port = Port.fromString(argMap.getOrElse("port", "8080"))
      .getOrElse {
        logger.error(s"Invalid port: ${argMap("port")}")
        return IO.pure(ExitCode.Error)
      }

    val maxUploadByteSize = argMap.get("max-upload-size").map {
      userValue =>
        Utils.humanReadableToBytes(userValue).getOrElse {
          logger.error(s"Invalid maximum upload size: ${argMap("max-upload-size")}")
          return IO.pure(ExitCode.Error)
        }
    }.getOrElse(DefaultAllowedUploadSize)

    val uploadDirectory: File = new File(argMap.getOrElse("upload-directory", "files"))
    if (!uploadDirectory.exists) {
      if (!uploadDirectory.mkdirs) {
        logger.error(s"Could not create upload directory: ${uploadDirectory.getAbsolutePath}")
        return IO.pure(ExitCode.Error)
      } else {
        logger.info(s"Created upload directory: ${uploadDirectory.getAbsolutePath}")
      }
    }
    if (!uploadDirectory.canWrite) {
      logger.error(s"Can not write to directory: ${uploadDirectory.getAbsolutePath}")
      return IO.pure(ExitCode.Error)
    }
    logger.info(s"Setting file upload directory to: ${uploadDirectory.getAbsolutePath} with max upload size: ${Utils.humanReadableBytes(maxUploadByteSize)}")

    val httpClient = EmberClientBuilder
      .default[IO]
      .withLogger(logger)
      .build

    val handler = RequestHandler(
      httpClient,
      fs2.io.file.Path.fromNioPath(uploadDirectory.toPath),
      disableMaven,
      PostUploadActions(),
    )(loggerFactory)

    EmberServerBuilder
      .default[IO]
      .withReceiveBufferSize(maxUploadByteSize)
//      .withHttp2
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp(handler))
      .withLogger(logger)
      .build
      .useForever
      .as(ExitCode.Success)
  }
}
