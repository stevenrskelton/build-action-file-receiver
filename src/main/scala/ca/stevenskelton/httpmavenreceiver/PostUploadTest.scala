package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.FileUploadFormData.FileUploadFieldName
import ca.stevenskelton.httpmavenreceiver.logging.StdOutLoggerFactory
import cats.effect.kernel.Resource
import cats.effect.{ExitCode, IO, IOApp}
import fs2.io.file.Path
import org.http4s.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Boundary, Multipart, Part}
import org.typelevel.log4cats.{Logger, LoggerFactory}
import org.typelevel.vault.Vault
import scodec.bits.ByteVector

object PostUploadTest extends IOApp:

  given loggerFactory: LoggerFactory[IO] = StdOutLoggerFactory()

  given logger: Logger[IO] = loggerFactory.getLoggerFromClass(getClass)

  override def run(args: List[String]): IO[ExitCode] =
    MainArgs.parse(args, Main.jarDirectory).flatMap:
      mainArgs =>

        given httpClient: Resource[IO, Client[IO]] = EmberClientBuilder
          .default[IO]
          .withLogger(logger)
          .build

        val workingDirectory = Path("").absolute

        val handler = RequestHandler(
          mainArgs.uploadDirectory,
          mainArgs.allowAllVersions,
          isMavenDisabled = true,
          Some(PostUploadAction("src/test/resources/postuploadactions/echoenv.sh", workingDirectory)),
        )
        val formFields = Map(
          "authToken" -> "",
          "user" -> "gh-user",
          "repository" -> "gh-project",
          "groupId" -> "gh.groupid",
          "artifactId" -> "test-file",
          "packaging" -> "png",
          "version" -> "1.0.1",
        )
        val formParts = formFields.toSeq.map((k, v) => Part.formData(k, v, Headers(`Content-Type`(MediaType.text.plain))))
        val entityPart = Part.fileData(FileUploadFieldName, "resource.getName", Entity.strict(ByteVector.empty))
        val parts = formParts :+ entityPart
        val multipart = Multipart(Vector.from(parts), boundary = Boundary("dfkfdkfdkdfkdffd"))
        val multipartEntity = EntityEncoder.multipartEncoder.toEntity(multipart)

        val request = Request[IO](Method.PUT, Uri.unsafeFromString("/"), headers = multipart.headers, multipartEntity)
        handler.releasesPut(request).as(ExitCode.Success).recoverWith:
          case ex =>
            logger.error(ex)(ex.getMessage).as(ExitCode.Error)
