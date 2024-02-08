package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.githubmaven.{MD5Util, MavenPackage, MetadataUtil}
import cats.effect.{IO, Resource}
import fs2.io.file.{Files, Path}
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.Multipart
import org.http4s.{MediaType, Request, Response, Status}
import org.typelevel.log4cats.LoggerFactory

import java.security.MessageDigest
import java.time.Duration

case class RequestHandler(
                           uploadDirectory: Path,
                           allowAllVersions: Boolean,
                           isMavenDisabled: Boolean,
                           postUploadActions: Option[PostUploadAction],
                         )(implicit httpClient: Resource[IO, Client[IO]], loggerFactory: LoggerFactory[IO]) {

  private val logger = loggerFactory.getLoggerFromClass(getClass)

  def releasesPut(request: Request[IO]): IO[Response[IO]] = {
    logger.info("Starting releasesPut handler")
    val start = System.currentTimeMillis
    val clientIp = request.remoteAddr
    request.decode[IO, Multipart[IO]] { multipart =>
      FileUploadFormData.fromFormData(multipart).flatMap {
        fileUploadFormData =>
          logger.info(s"Received request for file `${fileUploadFormData.filename}` by GitHub user `${fileUploadFormData.user}` upload from IP $clientIp")

          val digest = MessageDigest.getInstance("MD5")
          var fileSize = 0L
          
          for {
            mavenPackage <-
              if (isMavenDisabled || allowAllVersions) IO.pure(MavenPackage.unverified(fileUploadFormData))
              else MetadataUtil.fetchMetadata(fileUploadFormData)
            tmpFile <- createTempFileIfNotExists(fileUploadFormData.filename)
            expectedMD5 <-
              if (isMavenDisabled) IO.pure("")
              else MD5Util.fetchMavenMD5(mavenPackage, fileUploadFormData.authToken)
            _ <- fileUploadFormData.entityBody
              .chunkLimit(65536)
              .map {
                chunk =>
                  digest.update(chunk.toArray, 0, chunk.size)
                  fileSize += chunk.size
                  chunk
              }
              .flatMap(fs2.Stream.chunk)
              .through(Files[IO].writeAll(tmpFile))
              .compile
              .drain
            uploadMD5 = Utils.byteArrayToHexString(digest.digest)
            destinationFile <- if (isMavenDisabled) {
              moveTempToDestinationFile(tmpFile, getDestinationFile(fileUploadFormData.filename))
            } else {
              verifyMD5(tmpFile, fileUploadFormData.filename, uploadMD5, expectedMD5)
            }
            response <- successfulResponseBody(fileUploadFormData.filename, fileSize, uploadMD5)
            _ <- postUploadActions.fold(IO.pure(0))(
              action => action.run(destinationFile, fileUploadFormData, loggerFactory.getLoggerFromClass(action.getClass))
            )
            _ <- logger.info({
              val duration = Duration.ofMillis(System.currentTimeMillis - start)
              s"Completed ${fileUploadFormData.filename} (${Utils.humanReadableBytes(fileSize)}) in ${Utils.humanReadableDuration(duration)}"
            })
          } yield {
            response
          }
      }
    }
  }

  private inline def getDestinationFile(filename: String): Path = uploadDirectory / filename

  private def createTempFileIfNotExists(filename: String): IO[Path] = {
    //TODO: check if another temp file exists
    //TODO: verify MD5 of existing file?
    val destinationFile = getDestinationFile(filename)
    Files[IO].exists(destinationFile).flatMap {
      exists =>
        if (exists) {
          val msg = s"${destinationFile.fileName} already exists"
          logger.error(msg)
          IO.raiseError(ResponseException(Status.Conflict, msg))
        } else {
          Files[IO].createTempFile(Some(uploadDirectory), System.currentTimeMillis.toString, ".tmp", None)
        }
    }
  }

  private def verifyMD5(tempFile: Path, filename: String, fileMD5: String, mavenMD5: String): IO[Path] = {
    //      if (fileInfo.fileName.contains("SNAPSHOT")) {
    //        super.tmpFileHook(tmp, md5Sum)
    //      } else {
    val destinationFile = getDestinationFile(filename)
    if (fileMD5 != mavenMD5) {
      val errorMessage = s"Upload ${destinationFile.fileName} MD5 not equal, $mavenMD5 expected != $fileMD5 of upload."
      logger.error(errorMessage)
      Files[IO].delete(tempFile) *> IO.raiseError(ResponseException(Status.Conflict, errorMessage))
    } else {
      logger.info(s"MD5 validated $fileMD5, saving file at ${destinationFile.fileName}")
      moveTempToDestinationFile(tempFile, destinationFile)
    }
    //      }
  }

  def moveTempToDestinationFile(tempFile: Path, destinationFile: Path): IO[Path] = {
    Files[IO].move(tempFile, destinationFile).as(destinationFile).handleErrorWith {
      ex =>
        val msg = s"Could not rename $tempFile to ${destinationFile.toString}"
        IO.raiseError(ResponseException(Status.InternalServerError, msg, Some(ex)))
    }
  }

  private def successfulResponseBody(filename: String, fileSize: Long, destinationFileMD5: String): IO[Response[IO]] = {
    val responseBody = s"Successfully saved upload of $filename, ${Utils.humanReadableBytes(fileSize)}, MD5 $destinationFileMD5"
    Ok(responseBody).map(_.withContentType(`Content-Type`(MediaType.text.plain)))
  }

}
