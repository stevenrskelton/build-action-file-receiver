package ca.stevenskelton.httpmavenreceiver

import cats.effect.IO
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.Multipart
import org.http4s.{EntityBody, MediaType, Status}

import scala.collection.mutable

case class FileUploadFormData(
                               githubAuthToken: String,
                               githubUser: String,
                               githubRepository: String,
                               groupId: String,
                               artifactId: String,
                               version: String,
                               filename: String,
                               entityBody: EntityBody[IO]
                             ) {
  val gitHubMavenPath: String = s"https://maven.pkg.github.com/$githubUser/$githubRepository/${groupId.replace(".", "/")}/$artifactId/$version"
}

object FileUploadFormData {

  val FileUploadFields: Set[String] = Set(
    "githubAuthToken", "githubUser", "githubRepository", "groupId", "artifactId", "version"
  )

  val FileUploadFieldName = "jar"

  val FormErrorMessage = s"PUT body should include fields: githubUser, githubRepository, groupId, artifactId, version, and $FileUploadFieldName"

  def fromFormData(formData: Multipart[IO]): IO[FileUploadFormData] = {
    formData.parts.foldLeft(IO.pure(new mutable.HashMap[String, String])) {
      case (fieldsIO, part) if part.name.contains(FileUploadFieldName) =>
        val io = fieldsIO.map {
          fields =>
            val upload = for {
              githubAuthToken <- fields.get("githubAuthToken")
              githubUser <- fields.get("githubUser")
              githubRepository <- fields.get("githubRepository")
              groupId <- fields.get("groupId")
              artifactId <- fields.get("artifactId")
              version <- fields.get("version")
              filename <- part.filename
            } yield {
              FileUploadFormData(
                githubAuthToken, githubUser, githubRepository, groupId, artifactId, version,
                filename, part.body)
            }

            upload.getOrElse {
              throw ResponseException(Status.BadRequest, FormErrorMessage)
            }
        }
        return io
        fieldsIO
      case (fieldsIO, part) if FileUploadFields.contains(part.name.getOrElse("")) && part.contentType.contains(`Content-Type`(MediaType.text.plain)) =>
        fieldsIO.flatMap {
          fields =>
            part.bodyText.compile.string.map {
              value =>
                fields.put(part.name.get, value)
                fields
            }
        }
      case (_, part) =>
        val msg = s"Found unexpected `${part.name.getOrElse("")}` form field of type ${part.contentType.fold("?")(_.toString)}."
        return IO.raiseError(ResponseException(Status.BadRequest, msg))
    }
    IO.raiseError(ResponseException(Status.BadRequest, FormErrorMessage))
  }
}