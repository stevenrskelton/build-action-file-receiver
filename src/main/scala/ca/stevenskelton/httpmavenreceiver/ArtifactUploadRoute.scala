package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, completeOrRecoverWith, extractClientIP, extractRequestContext, post, withSizeLimit}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.server.directives.MarshallingDirectives.{as, entity}
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Flow}
import com.typesafe.scalalogging.Logger

import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class ArtifactUploadRoute(httpExt: HttpExt,
                               directory: Path,
                               createHooks: ArtifactUploadRoute => RequestHooks,
                               maxUploadFileSizeBytes: Long,
                               allowedGithubUsers: Seq[AllowedGithubUser]
                              )(implicit val logger: Logger) {

  implicit val materializer: Materializer = Materializer(httpExt.system)

  private def successfulResponseBody(fileInfo: FileInfo, destinationFile: File, destinationFileMD5: String): HttpResponse = {
    val responseBody = s"Successfully saved upload of ${fileInfo.fileName}, ${Utils.humanFileSize(destinationFile)}, MD5 $destinationFileMD5"
    HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`text/plain(UTF-8)`, responseBody))
  }

  val releasesPost: Route = post {
    extractClientIP {
      clientIp =>
        withSizeLimit(maxUploadFileSizeBytes) {
          entity(as[Multipart.FormData]) { formData =>
            extractRequestContext { ctx =>
              val hooks = createHooks(this)
              val uploadedF = FileUploadDirectives.parseFormData(formData, ctx).flatMap {
                o =>
                  logger.info(s"Received request for `${o._2.fileName}` upload from $clientIp")
                  val requestGithubUser = o._1("githubUser")
                  val githubUser = allowedGithubUsers.find(_.githubUsername == requestGithubUser).getOrElse {
                    val message = s"Could not find user `$requestGithubUser``"
                    logger.error(message)
                    throw new Exception(message)
                  }
                  hooks.preHook(o._1, o._2, o._3, ctx).flatMap {
                    case (githubPackage, fileInfo, bytes) =>
                      val tmpFile = File.createTempFile(System.currentTimeMillis.toString, ".tmp", directory.toFile)
                      val digest = MessageDigest.getInstance("MD5")
                      bytes
                        .via(Flow.fromFunction {
                          byteString =>
                            digest.update(byteString.toArray, 0, byteString.size)
                            byteString
                        })
                        .runWith(FileIO.toPath(tmpFile.toPath))
                        .flatMap { ioResult =>
                          ioResult.status match {
                            case Success(_) =>
                              val uploadMD5 = Utils.byteArrayToHexString(digest.digest)
                              hooks.tmpFileHook(tmpFile, uploadMD5).flatMap {
                                dest =>
                                  val response = successfulResponseBody(fileInfo, dest, uploadMD5)
                                  githubUser.postHook(dest)(logger).flatMap {
                                    _ => hooks.postHook(response, githubUser, dest)
                                  }
                              }
                            case Failure(ex) => Future.failed(ex)
                          }
                        }
                        .recoverWith {
                          case ex =>
                            logger.error("Upload IOResult failed", ex)
                            tmpFile.delete()
                            Future.failed(ex)
                        }
                  }
              }
              completeOrRecoverWith(uploadedF) {
                case UserMessageException(statusCode, userMessage) => complete(statusCode, HttpEntity(ContentTypes.`text/plain(UTF-8)`, userMessage))
              }
            }
          }
        }
    }
  }
}