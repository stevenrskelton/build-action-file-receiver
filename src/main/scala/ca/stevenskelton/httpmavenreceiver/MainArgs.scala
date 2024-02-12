package ca.stevenskelton.httpmavenreceiver

import cats.effect.{ExitCode, IO}
import com.comcast.ip4s.{Host, Port}
import fs2.io.file.{Files, Path}
import org.typelevel.log4cats.Logger
import ca.stevenskelton.httpmavenreceiver.Main.ExitException

import java.io.File

case class MainArgs(
                     allowAllVersions: Boolean,
                     disableMaven: Boolean,
                     host: Host,
                     port: Port,
                     maxUploadByteSize: Int,
                     postUploadAction: Option[PostUploadAction],
                     uploadDirectory: Path,
                   )
object MainArgs {

  private val DefaultAllowedUploadSize = 30 * 1024 * 1024

  def parse(args: List[String], jarDirectory: File)(using logger: Logger[IO]): IO[MainArgs] = {

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

    for {

      _ <- if (argMap.contains("help")) {
        val msg =
          """
            |Command line arguments:
            |  --help
            |  --disable-maven
            |  --allow-all-versions
            |  --host=[STRING]
            |  --port=[INTEGER]
            |  --max-upload-size=[STRING]
            |  --exec=[STRING]
            |  --upload-directory=[STRING]
            |""".stripMargin
        IO.raiseError(ExitException(msg, ExitCode.Success))
      } else IO.pure(())

      disableMaven <- IO.pure(argMap.contains("disable-maven"))

      allowAllVersions <- IO.pure(argMap.contains("allow-all-versions"))

      host <- Host.fromString(argMap.getOrElse("host", "0.0.0.0"))
        .map(IO.pure)
        .getOrElse {
          IO.raiseError(ExitException(s"Invalid host: ${argMap("host")}"))
        }

      port <- Port.fromString(argMap.getOrElse("port", "8080"))
        .map(IO.pure)
        .getOrElse {
          IO.raiseError(ExitException(s"Invalid port: ${argMap("port")}"))
        }

      maxUploadByteSize <- argMap.get("max-upload-size")
        .map {
          userValue =>
            Utils.humanReadableToBytes(userValue)
              .map(IO.pure)
              .getOrElse {
                IO.raiseError(ExitException(s"Invalid maximum upload size: ${argMap("max-upload-size")}"))
              }
        }
        .getOrElse(IO.pure(DefaultAllowedUploadSize))

      postUploadAction <- argMap.get("exec")
        .map {
          cmd =>
            val cmdPath = Path(cmd)
            val path = if(cmdPath.isAbsolute) cmdPath else Path(s"${jarDirectory.getAbsolutePath}/$cmd")

            Files[IO].exists(path).flatMap {
              case false => IO.raiseError(ExitException(s"Exec $cmd does not exist with working directory ${jarDirectory.toString}"))
              case true => Files[IO].isExecutable(path).flatMap {
                case false => IO.raiseError(ExitException(s"Exec $cmd not executable."))
                case true => logger.info(s"Post upload command: $cmd").as(Some(PostUploadAction(cmd, jarDirectory.getAbsoluteFile)))
              }
            }
        }
        .getOrElse(IO.pure(None))

      uploadDirectory <- {
        val path = Path(argMap.getOrElse("upload-directory", "files"))
        val pathString = path.absolute.toString
        for {
          _ <- Files[IO].exists(path).flatMap {
            case false =>
              Files[IO].createDirectories(path) *>
                logger.info(s"Created upload directory: $pathString")
                  .onError {
                    ex => IO.raiseError(ExitException(s"Could not create upload directory: $pathString"))
                  }
            case true => Files[IO].isWritable(path).flatMap {
              case true => IO.pure(())
              case false => IO.raiseError(ExitException(s"Can not write to directory: $pathString"))
            }
          }
          _ <- logger.info(s"Setting file upload directory to: $pathString \nMaximum upload size: ${Utils.humanReadableBytes(maxUploadByteSize)}")
        } yield {
          path
        }
      }

    } yield {
      MainArgs(
        allowAllVersions = allowAllVersions,
        disableMaven = disableMaven,
        host = host,
        port = port,
        maxUploadByteSize = maxUploadByteSize,
        postUploadAction = postUploadAction,
        uploadDirectory = uploadDirectory,
      )
    }
  }
}
