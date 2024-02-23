package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.githubmaven.{MD5Util, MavenPackage, MetadataUtil}
import cats.effect.{ExitCode, IO, Resource}
import fs2.io.file.{Files, Path}
import org.http4s.client.Client
import org.http4s.multipart.Multipart
import org.http4s.{EntityBody, Request, Response}
import org.typelevel.log4cats.Logger

import java.security.MessageDigest
import java.time.Duration

case class RequestHandler(
                           uploadDirectory: Path,
                           allowAllVersions: Boolean,
                           isMavenDisabled: Boolean,
                           postUploadActions: Option[PostUploadAction],
                         )(using httpClient: Resource[IO, Client[IO]], logger: Logger[IO]):

  def releasesPut(request: Request[IO]): IO[Response[IO]] =
    for {
      _ <- logger.info("Starting releasesPut handler")

      startTime <- IO.realTimeInstant

      handlerResponse <- request.decode[IO, Multipart[IO]] { multipart =>
        FileUploadFormData.fromFormData(multipart).flatMap:
          fileUploadFormData =>
            for {

              _ <- logger.info(s"Received request for file `${fileUploadFormData.filename}` by GitHub user `${fileUploadFormData.user}` upload from IP ${request.remoteAddr}")

              mavenPackage <-
                if (isMavenDisabled) IO(MavenPackage.unverified(fileUploadFormData))
                else MetadataUtil.fetchMetadata(fileUploadFormData, allowAllVersions)

              tempFile <- FileUtils.createTempFileIfNotExists(uploadDirectory / mavenPackage.filename)

              successfulUpload <- handleUpload(
                tempFile,
                mavenPackage,
                fileUploadFormData.entityBody,
                fileUploadFormData.authToken,
              ).onError:
                ex =>
                  Files[IO].exists(tempFile).flatMap {
                    case true => Files[IO].delete(tempFile)
                    case false => IO.unit
                  } *> IO.raiseError(ex)

              response <- successfulUpload.responseBody()

              endTime <- IO.realTimeInstant
              _ <- logger.info({
                val duration = s"${"%.2f".format(Duration.between(startTime, endTime).toMillis * 0.001)} seconds"
                s"Completed ${mavenPackage.filename} (${Utils.humanReadableBytes(successfulUpload.fileSize)}) in $duration}"
              })

            } yield response
      }
    } yield handlerResponse

  private def handleUpload(tempFile: Path, mavenPackage: MavenPackage, entityBody: EntityBody[IO], authToken: AuthToken): IO[SuccessfulUpload] = IO {

    val digest = MessageDigest.getInstance("MD5")
    var fileSize = 0
    
    for {
      expectedMD5 <-
        if (isMavenDisabled) IO.pure(None)
        else MD5Util.fetchMavenMD5(mavenPackage, authToken).map(Some.apply)
      
      _ <- entityBody
        .chunkLimit(65536)
        .map:
          chunk =>
            digest.update(chunk.toArray, 0, chunk.size)
            fileSize += chunk.size
            chunk
        .flatMap(fs2.Stream.chunk)
        .through(Files[IO].writeAll(tempFile))
        .compile
        .drain

      _ <- logger.info(s"Received $fileSize bytes for ${mavenPackage.filename}")

      uploadMD5 = Utils.byteArrayToHexString(digest.digest)

      destinationFile <-
        expectedMD5.fold(
          FileUtils.moveTempToDestinationFile(tempFile, uploadDirectory / mavenPackage.filename)
        )(
          FileUtils.verifyMD5(tempFile, uploadDirectory / mavenPackage.filename, uploadMD5, _)
        )

      _ <- postUploadActions.fold(IO.pure(ExitCode.Success))(
        action => action.run(destinationFile, mavenPackage)
      )

    } yield SuccessfulUpload(mavenPackage.filename, fileSize, uploadMD5)
  }.flatten
