package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.multipart.MultipartParser
import cats.effect.IO
import org.http4s.{DecodeFailure, DecodeResult, EntityBody, EntityDecoder, Headers, InvalidMessageBodyFailure, MediaRange, Status}
import org.http4s.multipart.Boundary
import org.typelevel.ci.CIString

import scala.collection.mutable

case class FileUploadFormData(
                               authToken: AuthToken,
                               user: String,
                               repository: String,
                               groupId: String,
                               artifactId: String,
                               packaging: String,
                               version: String,
                               filename: String,
                               entityBody: EntityBody[IO]
                             )

object FileUploadFormData:

  val FileUploadFields: Set[String] = Set(
    "authToken", "user", "repository", "groupId", "artifactId", "packaging", "version"
  )

  val FileUploadFieldName = "file"

  val FormErrorMessage = s"PUT body should include fields: ${FileUploadFields.mkString(",")}, and $FileUploadFieldName"

  val HeadersErrorMessage = s"PUT headers should include: ${FileUploadFields.map("X-" + _).mkString(",")}, and X-$FileUploadFieldName"

  def makeDecoder: EntityDecoder[IO, FileUploadFormData] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match
        case Some(boundary) =>
          DecodeResult:
            msg.body
              .through(MultipartParser.parseToPartsStream(Boundary(boundary)))
              .takeThrough(!_.name.contains(FileUploadFieldName))
              .fold(IO.pure[mutable.HashMap[String, String] | Option[FileUploadFormData]](new mutable.HashMap[String, String])):
                (fieldsIO, part) =>
                  part.name match
                    case Some(FileUploadFieldName) =>
                      fieldsIO.map:
                        case fields: mutable.HashMap[String, String] =>
                          for {
                            authToken <- fields.get("authToken")
                            user <- fields.get("user")
                            repository <- fields.get("repository")
                            groupId <- fields.get("groupId")
                            artifactId <- fields.get("artifactId")
                            packaging <- fields.get("packaging")
                            version <- fields.get("version")
                            filename <- part.filename
                          } yield FileUploadFormData(
                            authToken = authToken,
                            user = user,
                            repository = repository,
                            groupId = groupId,
                            artifactId = artifactId,
                            packaging = packaging,
                            version = version,
                            filename = filename,
                            entityBody = part.body
                          )
                        case _ => None
                    case Some(partName) if FileUploadFields.contains(partName) =>
                      fieldsIO.flatTap:
                        case fields: mutable.HashMap[String, String] =>
                          part.bodyText.compile.string.map:
                            fieldValue => fields.put(partName, fieldValue)
                        case _ => IO.raiseError(ResponseException(Status.BadRequest, FormErrorMessage))
                    case _ => fieldsIO
              .compile.lastOrError.flatten.map:
                case Some(fileUploadFields) => Right(fileUploadFields)
                case obj => Left(new ResponseException(Status.BadRequest, FormErrorMessage) with DecodeFailure)

        case None =>
          DecodeResult.failureT(
            InvalidMessageBodyFailure("Missing boundary extension to Content-Type")
          )
    }
  end makeDecoder

  private def readHeader(name: String, headers: Headers, isMavenDisabled: Boolean): Option[String] =
    headers.get(CIString(name)).map(_.head.value).orElse:
      if (isMavenDisabled) Some("") else None

  def readFromHttpHeaders(headers: Headers, entityBody: EntityBody[IO], isMavenDisabled: Boolean): Option[FileUploadFormData] = {
    for {
      authToken <- readHeader("X-authToken", headers, isMavenDisabled)
      user <- readHeader("X-user", headers, isMavenDisabled)
      repository <- readHeader("X-repository", headers, isMavenDisabled)
      groupId <- readHeader("X-groupId", headers, isMavenDisabled)
      artifactId <- readHeader("X-artifactId", headers, isMavenDisabled)
      packaging <- readHeader("X-packaging", headers, isMavenDisabled)
      version <- readHeader("X-version", headers, isMavenDisabled)
      filename <- headers.get(CIString("X-file")).map(_.head).map(_.value)
    } yield FileUploadFormData(
      authToken = authToken,
      user = user,
      repository = repository,
      groupId = groupId,
      artifactId = artifactId,
      packaging = packaging,
      version = version,
      filename = filename,
      entityBody = entityBody,
    )
  }

end FileUploadFormData