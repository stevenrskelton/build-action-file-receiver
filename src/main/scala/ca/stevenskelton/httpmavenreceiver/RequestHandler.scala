package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.githubmaven.{MD5Util, MavenPackage, MetadataUtil}
import cats.effect.*
import fs2.io.file.{Files, Path}
import org.http4s.{EntityBody, MediaType, Request, Response, Status}
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
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
    logger.info(s"Starting request from ${request.remoteAddr.fold("?")(_.toUriString)}") *> {
      if (request.contentType.contains(`Content-Type`.apply(MediaType.multipart.`form-data`)))
        request.decodeWith(FileUploadFormData.makeDecoder, strict = true)(handleFileUploadFormData)
      else
        val fileUploadFormData = FileUploadFormData.readFromHttpHeaders(request.headers, request.body, isMavenDisabled)
        fileUploadFormData.map(handleFileUploadFormData).getOrElse:
          IO.raiseError(ResponseException(Status.BadRequest, FileUploadFormData.HeadersErrorMessage))
      end if
    }
  end releasesPut

  private def handleFileUploadFormData(fileUploadFormData: FileUploadFormData): IO[Response[IO]] =
    for {
      startTime <- IO.realTimeInstant

      _ <- logger.info(s"Parsed request for file `${fileUploadFormData.filename}` by GitHub user `${fileUploadFormData.user}`")

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
          logger.error(ex)(ex.getMessage) *> Files[IO].exists(tempFile).flatMap {
            case true => Files[IO].delete(tempFile)
            case false => IO.unit
          } *> IO.raiseError(ex)

      response <- successfulUpload.responseBody()

      endTime <- IO.realTimeInstant
      _ <- logger.info({
        val duration = s"${"%.2f".format(Duration.between(startTime, endTime).toMillis * 0.001)} seconds"
        s"Completed ${mavenPackage.filename} (${Utils.humanReadableBytes(successfulUpload.fileSize)}) in $duration}"
      })
      _ <- logger.info( s"Current JVM Heap ${Utils.humanReadableBytes(Runtime.getRuntime().totalMemory())}")

    } yield response
  end handleFileUploadFormData

  private def handleUpload(tempFile: Path, mavenPackage: MavenPackage, entityBody: EntityBody[IO], authToken: AuthToken): IO[SuccessfulUpload] =
    IO {
      val digest = MessageDigest.getInstance("MD5")
      var fileSize = 0

      for {
        expectedMD5 <-
          if (isMavenDisabled) IO.pure(None)
          else MD5Util.fetchMavenMD5(mavenPackage, authToken).map(Some.apply)

        _ <- entityBody
          .map:
            chunk =>
              digest.update(chunk)
              fileSize += 1
              chunk
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

  end handleUpload
end RequestHandler


