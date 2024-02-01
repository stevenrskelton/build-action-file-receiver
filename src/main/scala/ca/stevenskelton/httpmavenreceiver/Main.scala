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

object Main extends IOApp {

  implicit val loggerFactory: LoggerFactory[IO] = StdOutLoggerFactory()

  private val logger = loggerFactory.getLoggerFromClass(getClass)

  private val DefaultAllowedUploadSize = 1024000L

  override def run(args: List[String]): IO[ExitCode] = {

    logger.info(s"${SbtBuildInfo.name} ${SbtBuildInfo.version}")

    val argMap = args
      .filter(_.startsWith("--"))
      .map {
        argument =>
          val equalsChar = argument.indexOf("=")
          if(equalsChar > -1) {
            val (key, value) = argument.splitAt(equalsChar)
            (key.drop(2), value.drop(1))
          }else {
            (argument.drop(2), "")
          }
      }.toMap

    if(argMap.contains("help")) {
      logger.info(
        """
          |Command line arguments:
          |  --help
          |  --directory=[STRING]
          |  --host=[STRING]
          |  --port=[INTEGER]
          |  --maxsize=[INTEGER]
          |""".stripMargin)
      return IO.pure(ExitCode.Success)
    }

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

    val maxUploadByteSize = argMap.get("maxsize").map {
      _.toLongOption.getOrElse {
        logger.error(s"Invalid maximum upload size: ${argMap("port")}")
        return IO.pure(ExitCode.Error)
      }
    }.getOrElse(DefaultAllowedUploadSize)

    val uploadDirectory: File = new File(argMap.getOrElse("directory", "."))
    if (!uploadDirectory.exists) {
      if (!uploadDirectory.mkdirs) {
        logger.error(s"Could not create upload directory: ${uploadDirectory.getAbsolutePath}")
        return IO.pure(ExitCode.Error)
      } else {
        logger.info(s"Created upload directory: ${uploadDirectory.getAbsolutePath}")
      }
    }
    if(!uploadDirectory.canWrite){
      logger.error(s"Can not write to directory: ${uploadDirectory.getAbsolutePath}")
      return IO.pure(ExitCode.Error)
    }
    logger.info(s"Setting file upload directory to: ${uploadDirectory.getAbsolutePath} with max upload size: ${Utils.humanFileSize(maxUploadByteSize)}")

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
