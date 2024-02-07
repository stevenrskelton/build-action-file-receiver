package ca.stevenskelton.httpmavenreceiver

import cats.effect.IO
import org.http4s.multipart.Multipart
import org.http4s.{EntityBody, Status}

import scala.collection.mutable

case class FileUploadFormData(
                               authToken: String,
                               user: String,
                               repository: String,
                               groupId: String,
                               artifactId: String,
                               packaging: String,
                               version: String,
                               filename: String,
                               entityBody: EntityBody[IO]
                             )

object FileUploadFormData {

  val FileUploadFields: Set[String] = Set(
    "authToken", "user", "repository", "groupId", "artifactId", "packaging", "version"
  )

  val FileUploadFieldName = "file"

  val FormErrorMessage = s"PUT body should include fields: ${FileUploadFields.mkString(",")}, and $FileUploadFieldName"

  def fromFormData(formData: Multipart[IO]): IO[FileUploadFormData] = {
    formData.parts.foldLeft(IO.pure(new mutable.HashMap[String, String])) {
      case (fieldsIO, part) if part.name.contains(FileUploadFieldName) =>
        val io = fieldsIO.map {
          fields =>
            val upload = for {
              authToken <- fields.get("authToken")
              user <- fields.get("user")
              repository <- fields.get("repository")
              groupId <- fields.get("groupId")
              artifactId <- fields.get("artifactId")
              packaging <- fields.get("packaging")
              version <- fields.get("version")
              filename <- part.filename
            } yield {
              FileUploadFormData(
                authToken, user, repository, groupId, artifactId, packaging, version, filename, part.body
              )
            }

            upload.getOrElse {
              throw ResponseException(Status.BadRequest, FormErrorMessage)
            }
        }
        return io
        fieldsIO
      case (fieldsIO, part) if FileUploadFields.contains(part.name.getOrElse("")) =>
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