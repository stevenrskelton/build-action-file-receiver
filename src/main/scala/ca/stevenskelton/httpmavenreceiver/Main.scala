package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.logging.StdOutLoggerFactory
import cats.effect.{ExitCode, IO, IOApp, Resource}
import fs2.io.file.Path
import org.http4s.client.Client
import org.http4s.dsl.impl./
import org.http4s.dsl.io.{->, PUT, Root}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.{HttpApp, HttpRoutes, MessageFailure}
import org.typelevel.log4cats.{Logger, LoggerFactory}

object Main extends /*epollcat.EpollApp */ IOApp:

  case class ExitException(msg: String, code: ExitCode = ExitCode.Error) extends Exception(msg)

  given loggerFactory: LoggerFactory[IO] = StdOutLoggerFactory()

  given logger: Logger[IO] = loggerFactory.getLoggerFromClass(getClass)

  def httpApp(handler: RequestHandler): HttpApp[IO] = HttpRoutes.of[IO] {
    case request@PUT -> Root / "releases" => handler.releasesPut(request).handleErrorWith {
      case ex: MessageFailure => ex.cause
        .map(cause => logger.error(cause)(cause.getMessage))
        .getOrElse(logger.error(ex.getMessage))
        .as(ex.toHttpResponse(request.httpVersion))
    }
  }.orNotFound

  def jarDirectory: Path = Path(getClass.getProtectionDomain.getCodeSource.getLocation.toURI.getPath).parent.get

  override def run(args: List[String]): IO[ExitCode] =
    logger.info(s"${SbtBuildInfo.name} ${SbtBuildInfo.version}") *>
    logger.info(s"Maximum JVM Heap ${Utils.humanReadableBytes(Runtime.getRuntime().maxMemory())}") *>
      MainArgs.parse(args, jarDirectory)
        .flatMap:
          mainArgs =>

            given httpClient: Resource[IO, Client[IO]] = EmberClientBuilder
              .default[IO]
              .withLogger(logger)
              .build

            val handler = RequestHandler(
              mainArgs.uploadDirectory,
              mainArgs.allowAllVersions,
              mainArgs.disableMaven,
              mainArgs.postUploadAction,
            )

            EmberServerBuilder
              .default[IO]
              //              .withReceiveBufferSize(mainArgs.maxUploadByteSize)
              //      .withHttp2
              .withHost(mainArgs.host)
              .withPort(mainArgs.port)
              .withHttpApp(httpApp(handler))
              .withLogger(logger)
              .build
              .useForever
              .as(ExitCode.Success)

        .handleErrorWith:
          case ExitException(msg, code) => logger.error(msg).as(code)
          case ex => logger.error(ex)(ex.getMessage).as(ExitCode.Error)

end Main