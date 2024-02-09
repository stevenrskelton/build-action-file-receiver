package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.githubmaven.{MD5Util, MavenPackage, MetadataUtil}
import cats.effect.{IO, Resource}
import fs2.io.file.{Files, Path}
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.Multipart
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
    val fileUtils = FileUtils()
    request.decode[IO, Multipart[IO]] { multipart =>
      FileUploadFormData.fromFormData(multipart).flatMap {
        fileUploadFormData =>
          logger.info(s"Received request for file `${fileUploadFormData.filename}` by GitHub user `${fileUploadFormData.user}` upload from IP $clientIp")

          for {
            mavenPackage <-
              if (isMavenDisabled) IO.pure(MavenPackage.unverified(fileUploadFormData))
              else MetadataUtil.fetchMetadata(fileUploadFormData, allowAllVersions)
            tempFile <- fileUtils.createTempFileIfNotExists(fileUploadFormData.filename, uploadDirectory / mavenPackage.filename)
            successfulUpload <- handleUpload(
              tempFile,
              mavenPackage,
              fileUploadFormData.entityBody,
              fileUploadFormData.authToken,
            ).onError {
              ex =>
                Files[IO].exists(tempFile).flatMap {
                  case true => Files[IO].delete(tempFile)
                  case false => IO.pure(())
                } *> IO.raiseError(ex)
            }
            response <- successfulUpload.responseBody()
            _ <- logger.info({
              val duration = Duration.ofMillis(System.currentTimeMillis - start)
              s"Completed ${mavenPackage.filename} (${Utils.humanReadableBytes(successfulUpload.fileSize)}) in ${Utils.humanReadableDuration(duration)}"
            })
          } yield {
            response
          }
      }
    }
  }

  private def handleUpload(tempFile: Path, mavenPackage: MavenPackage, entityBody: EntityBody[IO], authToken: AuthToken): IO[SuccessfulUpload] = {
    val digest = MessageDigest.getInstance("MD5")
    var fileSize = 0L

    val fileUtils = FileUtils()
    for {
      expectedMD5 <-
        if (isMavenDisabled) IO.pure(MD5Hash.Empty)
        else MD5Util.fetchMavenMD5(mavenPackage, authToken)
      _ <- entityBody
        .chunkLimit(65536)
        .map {
          chunk =>
            digest.update(chunk.toArray, 0, chunk.size)
            fileSize += chunk.size
            chunk
        }
        .flatMap(fs2.Stream.chunk)
        .through(Files[IO].writeAll(tempFile))
        .compile
        .drain
      uploadMD5 = MD5Hash(Utils.byteArrayToHexString(digest.digest))
      destinationFile <-
        if (isMavenDisabled) {
          fileUtils.moveTempToDestinationFile(tempFile, uploadDirectory / mavenPackage.filename)
        } else {
          fileUtils.verifyMD5(tempFile, uploadDirectory / mavenPackage.filename, uploadMD5, expectedMD5)
        }
      _ <- postUploadActions.fold(IO.pure(0))(
        action => action.run(destinationFile, mavenPackage, loggerFactory.getLoggerFromClass(action.getClass))
      )
    } yield {
      SuccessfulUpload(mavenPackage.filename, fileSize, uploadMD5)
    }
  }

}
