package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.FileUploadFormData.FileUploadFieldName
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.io.file.{Files, Path}
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Boundary, Multipart, Part}
import org.http4s.{Headers, MediaType, Uri, HttpApp, Response, Entity, EntityEncoder, Request, Header, Method}
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import org.typelevel.vault.Vault
import scodec.bits.ByteVector

object UploadRequestHelper {

  def httpApp(
               responses: Map[Uri, Response[IO]],
               allowAllVersions: Boolean = false,
               isMavenDisabled: Boolean = false,
               allowedRepositories: Seq[UserRepository] = Nil,
               postUploadActions: Option[PostUploadAction] = None,
             )(using logger: Logger[IO] = Main.logger): IO[HttpApp[IO]] = {

    for
      tmpDir <- Files[IO].createTempDirectory(None, "http-maven-receiver-specs-", None)
    yield {
      given httpClient: Resource[IO, Client[IO]] = Resource.pure {
        Client(request => Resource.pure(responses.getOrElse(request.uri, Response.notFound)))
      }

      Main.httpApp(RequestHandler(
        uploadDirectory = tmpDir,
        allowAllVersions = allowAllVersions,
        isMavenDisabled = isMavenDisabled,
        allowedRepositories = allowedRepositories,
        postUploadActions = postUploadActions,
      ))
    }
  }

  def successResponse(file: Path): Response[IO] = {
    val bodyBytes = Option(getClass.getResourceAsStream(file.absolute.toString))
      .map(_.readAllBytes)
      .getOrElse(java.nio.file.Files.readAllBytes(file.toNioPath))
    Response[IO](entity = Entity.Strict(ByteVector(bodyBytes)))
  }

  def multipartFilePutRequest(
                               resource: Path,
                               formFields: Map[String, String],
                               uri: Uri
                             ): Request[IO] = {

    //TODO: this could be streamed instead of using a Byte[]
    val bodyBytes = Option(getClass.getResourceAsStream(resource.toString))
      .map(_.readAllBytes)
      .getOrElse(java.nio.file.Files.readAllBytes(resource.toNioPath))

    val formParts = formFields.toSeq.map((k, v) => Part.formData(k, v, Headers(`Content-Type`(MediaType.text.plain))))
    val entityPart = Part.fileData(FileUploadFieldName, resource.fileName.toString, Entity.strict(ByteVector(bodyBytes)))
    val parts = formParts :+ entityPart
    val multipart = Multipart(Vector.from(parts), boundary = Boundary("dfkfdkfdkdfkdffd"))
    val multipartEntity = EntityEncoder.multipartEncoder.toEntity(multipart)
    Request[IO](Method.PUT, uri, headers = multipart.headers, entity = multipartEntity)
  }

  def headersFilePutRequest(
                             resource: Path,
                             formFields: Map[String, String],
                             uri: Uri
                           ): Request[IO] = {

    //TODO: this could be streamed instead of using a Byte[]
    val bodyBytes = Option(getClass.getResourceAsStream(resource.toString))
      .map(_.readAllBytes)
      .getOrElse(java.nio.file.Files.readAllBytes(resource.toNioPath))
    val entity = Entity.strict(ByteVector(bodyBytes))
    val headersIter: Seq[Header.ToRaw] = formFields.map {
      (k, v) => Header.ToRaw.keyValuesToRaw(s"X-$k", v)
    }.toSeq :+ Header.Raw.apply(CIString("X-file"), resource.fileName.toString)
    val headers = Headers.apply(headersIter *)
    Request[IO](Method.PUT, uri, headers = headers, entity = entity)
  }

  //import java.io.FileOutputStream
  //import scala.util.Using
  //  def create50MBFile(tmpDir: Path): File = {
  //    val dir = new File(s"${tmpDir.toNioPath.toFile.getPath}/upload")
  //    dir.mkdir()
  //    val file = new File(s"${dir.getPath}/test.bin")
  //    file.deleteOnExit()
  //    if (file.exists) {
  //      file
  //    } else {
  //      //50MB file, this needs to fit into memory to make the request
  //      Using(new FileOutputStream(file)) {
  //        stream =>
  //          val empty = new Array[Byte](1024)
  //          (0 to 50000).foreach {
  //            _ => stream.write(empty)
  //          }
  //      }.map(_ => file).get
  //    }
  //  }

}
