package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.FileUploadFormData.FileUploadFieldName
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.io.file.{Files, Path}
import org.http4s.*
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Boundary, Multipart, Part}
import org.typelevel.log4cats.{Logger, LoggerFactory}
import org.typelevel.vault.Vault
import scodec.bits.ByteVector

import java.io.{File, FileOutputStream}
import scala.util.Using

object UploadRequestHelper {

  implicit val loggerFactory: LoggerFactory[IO] = Main.loggerFactory
  implicit val logger: Logger[IO] = Main.logger
  //
  //  def toMap(githubPackage: GitHubPackage): Map[String, String] = Map(
  //    "githubAuthToken" -> githubPackage.githubAuthToken,
  //    "githubUser" -> githubPackage.githubUser,
  //    "githubRepository" -> githubPackage.githubRepository,
  //    "groupId" -> githubPackage.groupId,
  //    "artifactId" -> githubPackage.artifactId,
  //    "version" -> githubPackage.version
  //  )
  //
  //  def gitHubPackagePutRequest(
  //                                resource: File,
  ////                                githubPackage: GitHubPackage,
  //                                uri: Uri = Uri.Path.Root
  //                              ): Request[IO] = multipartFilePutRequest(resource, toMap(githubPackage), uri)

  def httpApp(responses: Map[Uri, Response[IO]], isMavenDisabled: Boolean = false): IO[HttpApp[IO]] = for {
    tmpDir <- Files[IO].createTempDirectory(None, "http-maven-receiver-specs-", None)
  } yield {
    val httpClient: Resource[IO, Client[IO]] = Resource.pure(Client(request => Resource.pure(responses.getOrElse(request.uri, {
      logger.error(s"Uri 404: ${request.uri}")
      Response.notFound
    }))))
    Main.httpApp(RequestHandler(
      uploadDirectory = tmpDir,
      allowAllVersions = false,
      isMavenDisabled = isMavenDisabled,
      postUploadActions = None,
    )(httpClient, loggerFactory))
  }

  def successResponse(file: File): Response[IO] = {
    val bodyBytes = Option(getClass.getResourceAsStream(file.getAbsolutePath)).map(_.readAllBytes).getOrElse {
      java.nio.file.Files.readAllBytes(file.toPath)
    }
    Response[IO](entity = Entity.Strict(ByteVector(bodyBytes)))
  }

  def multipartFilePutRequest(
                               resource: File,
                               formFields: Map[String, String],
                               uri: Uri
                             ): Request[IO] = {

    //TODO: this could be streamed instead of using a Byte[]
    val bodyBytes = Option(getClass.getResourceAsStream(resource.getAbsolutePath)).map(_.readAllBytes).getOrElse {
      java.nio.file.Files.readAllBytes(resource.toPath)
    }
    //    val requestEntity = HttpEntity(ContentTypes.`application/octet-stream`, bodyBytes)
    //    val filePart = Multipart.FormData.BodyPart.Strict(GitHubPackage.FileUploadFieldName, requestEntity, Map("filename" -> resource.getName))
    //    val parts = formFields.toSeq.map(keyValue => Multipart.FormData.BodyPart.Strict.apply(keyValue._1, HttpEntity(keyValue._2))) :+ filePart
    //    val multipartForm = Multipart.FormData(parts: _*)
    //val m = Multipart(Vector.from(Seq(
    //
    //))

    val formParts = formFields.toSeq.map((k, v) => Part.formData(k, v, Headers(`Content-Type`(MediaType.text.plain))))
    val entityPart = Part.fileData(FileUploadFieldName, resource.getName, Entity.strict(ByteVector(bodyBytes)))
    val parts = formParts :+ entityPart
    val multipart = Multipart(Vector.from(parts), boundary = Boundary("dfkfdkfdkdfkdffd"))
    val multipartEntity = EntityEncoder.multipartEncoder.toEntity(multipart)
    Request[IO](Method.PUT, uri, headers = multipart.headers, entity = multipartEntity)
  }

  def create50MBFile(tmpDir: Path): File = {
    val dir = new File(s"${tmpDir.toNioPath.toFile.getPath}/upload")
    dir.mkdir()
    val file = new File(s"${dir.getPath}/test.bin")
    if (!file.exists) {
      //50MB file, this needs to fit into memory to make the request
      Using(new FileOutputStream(file)) {
        stream =>
          val empty = new Array[Byte](1024)
          (0 to 50000).foreach {
            _ => stream.write(empty)
          }
      }
    }
    file.deleteOnExit()
    file
  }
  
}
