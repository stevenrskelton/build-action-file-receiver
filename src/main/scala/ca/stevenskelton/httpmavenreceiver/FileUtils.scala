package ca.stevenskelton.httpmavenreceiver

import cats.effect.IO
import fs2.io.file.{Files, Path}
import org.http4s.Status
import org.typelevel.log4cats.LoggerFactory

class FileUtils()(implicit val loggerFactory: LoggerFactory[IO]) {

  private val logger = loggerFactory.getLoggerFromClass(getClass)

  def createTempFileIfNotExists(filename: String, destinationFile: Path): IO[Path] = {
    Files[IO].exists(destinationFile).flatMap {
      exists =>
        if (exists) {
          val msg = s"${destinationFile.fileName} already exists"
          logger.error(msg)
          IO.raiseError(ResponseException(Status.Conflict, msg))
        } else {
          Files[IO].createTempFile(destinationFile.parent, System.currentTimeMillis.toString, ".tmp", None)
        }
    }
  }

  def verifyMD5(tempFile: Path, destinationFile: Path, md5: MD5Hash, expectedMd5: MD5Hash): IO[Path] = {
    if (md5 != expectedMd5) {
      val errorMessage = s"Upload ${destinationFile.fileName} MD5 not equal, ${expectedMd5.value} expected != ${md5.value} of upload."
      logger.error(errorMessage)
      Files[IO].delete(tempFile) *> IO.raiseError(ResponseException(Status.Conflict, errorMessage))
    } else {
      logger.info(s"MD5 validated ${md5.value}, saving file at ${destinationFile.fileName}")
      moveTempToDestinationFile(tempFile, destinationFile)
    }
  }

  def moveTempToDestinationFile(tempFile: Path, destinationFile: Path): IO[Path] = {
    Files[IO].move(tempFile, destinationFile).as(destinationFile).handleErrorWith {
      ex =>
        val msg = s"Could not rename $tempFile to ${destinationFile.fileName}"
        IO.raiseError(ResponseException(Status.InternalServerError, msg, Some(ex)))
    }
  }

}
