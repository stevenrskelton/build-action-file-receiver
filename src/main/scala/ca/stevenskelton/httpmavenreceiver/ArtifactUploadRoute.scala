package ca.stevenskelton.httpmavenreceiver

import com.typesafe.scalalogging.Logger
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Directives.{complete, completeOrRecoverWith, extractClientIP, extractRequestContext, put, withSizeLimit}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.directives.FileInfo
import org.apache.pekko.http.scaladsl.server.directives.MarshallingDirectives.{as, entity}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{FileIO, Flow}

import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import scala.concurrent.ExecutionContext.Implicits.*
import scala.concurrent.Future

case class ArtifactUploadRoute(httpExt: HttpExtInterface,
                               directory: Path,
                               createHooks: ArtifactUploadRoute => RequestHooks,
                               maxUploadFileSizeBytes: Long,
                               allowedGitHubUsers: Seq[AllowedGitHubUser]
                              )(implicit val logger: Logger) {

  implicit val materializer: Materializer = httpExt.materializer

  lazy val releasesPutRoute: Route = put {
    extractClientIP {
      clientIp =>
        withSizeLimit(maxUploadFileSizeBytes) {
          entity(as[Multipart.FormData]) { formData =>
            extractRequestContext { ctx =>
              val hooks = createHooks(this)
              val uploadedF = FileUploadDirectives.parseFormData(formData, ctx).flatMap {
                o =>
                  val requestGitHubUser = o._1.getOrElse("githubUser", throw UserMessageException(StatusCodes.Unauthorized, GitHubPackage.FormErrorMessage))
                  logger.info(s"Received request for file `${o._2.fileName}` by GitHub user `$requestGitHubUser` upload from IP $clientIp")
                  val gitHubUser = if (allowedGitHubUsers.isEmpty) {
                    None
                  } else {
                    Some(allowedGitHubUsers.find(_.githubUsername == requestGitHubUser).getOrElse {
                      val message = s"Could not find user `$requestGitHubUser`"
                      logger.error(message)
                      throw UserMessageException(StatusCodes.NetworkAuthenticationRequired, message)
                    })
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
                          val uploadMD5 = Utils.byteArrayToHexString(digest.digest)
                          hooks.tmpFileHook(tmpFile, uploadMD5).flatMap {
                            dest =>

                              gitHubUser
                                .fold(Future.successful(()))(_.postHook(dest)(logger))
                                .flatMap { _ =>
                                    val response = successfulResponseBody(fileInfo, dest, uploadMD5)
                                    hooks.postHook(response, dest)
                                }
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
                case ex =>
                  val t = ex
                  throw ex
              }
            }
          }
        }
    }
  }

  private def successfulResponseBody(fileInfo: FileInfo, destinationFile: File, destinationFileMD5: String): HttpResponse = {
    val responseBody = s"Successfully saved upload of ${fileInfo.fileName}, ${Utils.humanFileSize(destinationFile)}, MD5 $destinationFileMD5"
    HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`text/plain(UTF-8)`, responseBody))
  }
}