package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.Main.ExitException
import cats.effect.{ExitCode, IO}
import com.comcast.ip4s.{Host, Port}
import fs2.io.file.{Files, Path}
import org.typelevel.log4cats.Logger

case class MainArgs(
                     allowAllVersions: Boolean,
                     disableMaven: Boolean,
                     allowedRepositories: Seq[String],
                     host: Host,
                     port: Port,
                     postUploadAction: Option[PostUploadAction],
                     uploadDirectory: Path,
                   )

object MainArgs:

  private val DefaultPort = 8080
  private val DefaultUploadFolder = "files"

  def parse(args: List[String], jarDirectory: Path)(using logger: Logger[IO]): IO[MainArgs] =

    val argMap = args
      .filter(_.startsWith("--"))
      .map {
        argument =>
          val equalsChar = argument.indexOf("=")
          if (equalsChar > -1)
            val (key, value) = argument.splitAt(equalsChar)
            (key.drop(2), value.drop(1))
          else
            (argument.drop(2), "")
      }.toMap

    for {

      _ <- if (argMap.contains("help"))
        val msg =
          """
            |Command line arguments:
            |  --help
            |  --disable-maven
            |  --allowed-repos=[STRING comma-separated]
            |  --allow-all-versions
            |  --host=[STRING]
            |  --port=[INTEGER]
            |  --exec=[STRING]
            |  --upload-directory=[STRING]
            |""".stripMargin
        IO.raiseError(ExitException(msg, ExitCode.Success))
      else IO.unit

      disableMaven <- IO.pure(argMap.contains("disable-maven"))

      allowedRepositories <- IO.pure(argMap.get("allowed-repos").toSeq.flatMap(_.split(',')))

      allowAllVersions <- IO.pure(argMap.contains("allow-all-versions"))

      host <- Host.fromString(argMap.getOrElse("host", "0.0.0.0"))
        .map(IO.pure)
        .getOrElse:
          IO.raiseError(ExitException(s"Invalid host: ${argMap("host")}"))

      port <- Port.fromString(argMap.getOrElse("port", DefaultPort.toString))
        .map(IO.pure)
        .getOrElse:
          IO.raiseError(ExitException(s"Invalid port: ${argMap("port")}"))

      postUploadAction <- argMap.get("exec")
        .map:
          cmd =>
            val postUploadAction = PostUploadAction(cmd, jarDirectory)
            Files[IO].exists(postUploadAction.commandPath).flatMap:
              case false => IO.raiseError(ExitException(s"Exec $cmd does not exist in working directory ${jarDirectory.toString}"))
              case true => Files[IO].isExecutable(postUploadAction.commandPath).flatMap:
                case false => IO.raiseError(ExitException(s"Exec $cmd not executable."))
                case true => logger.info(s"Post upload command: $cmd").as(Some(postUploadAction))
        .getOrElse(IO.pure(None))

      uploadDirectory <-
        val path = Path(argMap.getOrElse("upload-directory", DefaultUploadFolder))
        val pathString = path.absolute.toString
        for {
          _ <- Files[IO].exists(path).flatMap:
            case false =>
              Files[IO].createDirectories(path) *>
                logger.info(s"Created upload directory: $pathString")
                  .onError:
                    ex => IO.raiseError(ExitException(s"Could not create upload directory: $pathString"))
            case true => Files[IO].isWritable(path).flatMap:
              case true => IO.unit
              case false => IO.raiseError(ExitException(s"Can not write to directory: $pathString"))

          _ <- logger.info(s"Setting file upload directory to: $pathString")

          _ <-
            if (allowedRepositories.isEmpty)
              logger.warn("WARNING: Allowing all repositories.")
            else
              logger.info(s"Allowing repositories:\n${allowedRepositories.map(" ‣ " + _).mkString("\n")}")

        } yield path

    } yield
      MainArgs(
        allowAllVersions = allowAllVersions,
        disableMaven = disableMaven,
        allowedRepositories = allowedRepositories,
        host = host,
        port = port,
        postUploadAction = postUploadAction,
        uploadDirectory = uploadDirectory,
      )
