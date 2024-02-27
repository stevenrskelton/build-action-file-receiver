package ca.stevenskelton.httpmavenreceiver

import cats.effect.IO
import org.http4s.multipart.Boundary
import org.http4s.multipart.MultipartParser
import org.http4s.{DecodeFailure, DecodeResult, EntityBody, EntityDecoder, InvalidMessageBodyFailure, MediaRange, Status}

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
  
  def makeDecoder: EntityDecoder[IO, FileUploadFormData] = {
    // Modified org.http4s.MultipartDecoder.makeDecoder to not use a Vector
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            msg.body
              .through(MultipartParser.parseToPartsStream(Boundary(boundary)))
              .takeThrough(!_.name.contains(FileUploadFieldName))
              .fold(IO.pure[mutable.HashMap[String,String] | Option[FileUploadFormData]](new mutable.HashMap[String, String])) {
                (fieldsIO, part) => part.name match {
                  case Some(FileUploadFieldName) =>
                    fieldsIO.map {
                      case fields: mutable.HashMap[String,String] =>
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
                    }
                  case Some(partName) if FileUploadFields.contains(partName) =>
                    fieldsIO.flatTap {
                      case fields: mutable.HashMap[String, String] =>
                        part.bodyText.compile.string.map {
                          fieldValue => fields.put(partName, fieldValue)
                        }
                      case _ => IO.raiseError(ResponseException(Status.BadRequest, FormErrorMessage))
                    }
                  case _ => fieldsIO
                }
              }.compile.lastOrError.flatten.map {
                case Some(fileUploadFields) => Right(fileUploadFields)
                case obj => Left(new ResponseException(Status.BadRequest, FormErrorMessage) with DecodeFailure)
              }
          }
        case None =>
          DecodeResult.failureT(
            InvalidMessageBodyFailure("Missing boundary extension to Content-Type")
          )
      }
    }
  }

end FileUploadFormData