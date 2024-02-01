package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.githubmaven.MD5Util
import cats.effect.*
import fs2.io.file.{Files, Path}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.Multipart
import org.typelevel.log4cats.LoggerFactory

import java.io.File
import java.time.Duration

import java.security.MessageDigest

case class Http4sArtifactUploadRoute(
                                      uploadDirectory: Path,
                                      maxUploadFileSizeBytes: Long,
                                      postUploadActions: PostUploadActions,
                                    )(implicit loggerFactory: LoggerFactory[IO]) {

  private val logger = loggerFactory.getLoggerFromClass(getClass)

  private val httpClient = EmberClientBuilder
    .default[IO]
    .build

  def releasesPut(request: Request[IO]): IO[Response[IO]] = {
    val clientIp = request.remoteAddr
    request.decode[IO, Multipart[IO]] { multipart =>
      FileUploadFormData.fromFormData(multipart).flatMap {
        fileUploadFormData =>
          logger.info(s"Received request for file `${fileUploadFormData.filename}` by GitHub user `${fileUploadFormData.githubUser}` upload from IP $clientIp")
          val start = System.currentTimeMillis

          val digest = MessageDigest.getInstance("MD5")
          var fileSize = 0L

          val gitHubMavenUtil = MD5Util()

          for {
            expectedMD5 <- gitHubMavenUtil.fetchMavenMD5(fileUploadFormData)
            tmpFile <- Files[IO].createTempFile(Some(uploadDirectory), System.currentTimeMillis.toString, ".tmp", None)
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
            dest = verifyMD5(tmpFile, fileUploadFormData.filename, uploadMD5, expectedMD5)
            response <- successfulResponseBody(fileUploadFormData.filename, fileSize, uploadMD5)
            //            _ <- postUploadActions.run(response)
            _ <- logger.info({
              val duration = Duration.ofMillis(System.currentTimeMillis - start)
              s"Completed ${fileUploadFormData.filename} in ${Utils.humanReadableDuration(duration)}"
            })
          } yield {
            response
          }
      }
    }
  }

  private def verifyMD5(tmp: Path, filename: String, fileMD5: String, mavenMD5: String): File = {
    //      if (fileInfo.fileName.contains("SNAPSHOT")) {
    //        super.tmpFileHook(tmp, md5Sum)
    //      } else {
    val destinationFile = new File(s"${uploadDirectory.toNioPath.toFile.getAbsolutePath}/$filename")
    if (fileMD5 != mavenMD5) {
      val errorMessage = s"Upload ${destinationFile.getName} MD5 not equal, $mavenMD5 expected != $fileMD5 of upload."
      logger.error(errorMessage)
      tmp.toNioPath.toFile.delete
      //          Future.failed(UserMessageException(StatusCodes.BadRequest, errorMessage))
      throw new Exception(errorMessage)
    } else {
      logger.info(s"MD5 validated $fileMD5, saving file at ${destinationFile.getName}")
      if (tmp.toNioPath.toFile.renameTo(destinationFile)) {
        destinationFile
      } else {
        throw new Exception(s"Could not rename ${tmp} to ${destinationFile.getAbsolutePath}")
      }
    }
    //      }
  }

  private def successfulResponseBody(filename: String, fileSize: Long, destinationFileMD5: String): IO[Response[IO]] = {
    val responseBody = s"Successfully saved upload of $filename, ${Utils.humanFileSize(fileSize)}, MD5 $destinationFileMD5"
    Ok(responseBody).map(_.withContentType(`Content-Type`(MediaType.text.plain)))
  }


}
