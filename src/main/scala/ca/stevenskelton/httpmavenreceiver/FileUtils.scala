package ca.stevenskelton.httpmavenreceiver

import cats.effect.IO
import fs2.io.file.{Files, Path}
import org.http4s.Status
import org.typelevel.log4cats.Logger

object FileUtils:

  def createTempFileIfNotExists(destinationFile: Path)(using logger: Logger[IO]): IO[Path] =
    Files[IO].exists(destinationFile).flatMap:
      exists =>
        if (exists)
          val msg = s"${destinationFile.fileName} already exists"
          logger.error(msg) *> IO.raiseError(ResponseException(Status.Conflict, msg))
        else
          Files[IO].createTempFile(destinationFile.parent, System.currentTimeMillis.toString, ".tmp", None)

  def verifyMD5(tempFile: Path, destinationFile: Path, md5: MD5Hash, expectedMd5: MD5Hash)(using logger: Logger[IO]): IO[Path] =
    if (md5 != expectedMd5)
      val errorMessage = s"Upload ${destinationFile.fileName} MD5 not equal, $expectedMd5 expected != $md5 of upload."
      logger.error(errorMessage) *>
        Files[IO].delete(tempFile) *>
        IO.raiseError(ResponseException(Status.Conflict, errorMessage))
    else
      logger.info(s"MD5 validated $md5, saving file at ${destinationFile.fileName}") *>
        moveTempToDestinationFile(tempFile, destinationFile)

  def moveTempToDestinationFile(tempFile: Path, destinationFile: Path)(using logger: Logger[IO]): IO[Path] =
    Files[IO].move(tempFile, destinationFile).as(destinationFile).handleErrorWith:
      ex =>
        val errorMessage = s"Could not rename $tempFile to ${destinationFile.fileName}"
        logger.error(errorMessage) *>
          IO.raiseError(ResponseException(Status.InternalServerError, errorMessage, Some(ex)))

