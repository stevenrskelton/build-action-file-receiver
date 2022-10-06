package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, completeOrRecoverWith, post, withSizeLimit}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.Logger

import java.io.File
import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class ArtifactUpload(httpExt: HttpExt,
                          directory: Path,
                          githubToken: Option[String],
                          createHooks: (GithubPackage, FileInfo) => RequestHooks
                         )(implicit logger: Logger) {

  private implicit val materializer: Materializer = Materializer(httpExt.system)

  val releasesPost: Route = withSizeLimit(GithubPackage.MaxUploadByteSize) {
    post {
      FileUploadDirectives.githubPackageUpload {
        case (paramGithubPackage, paramFileInfo, bytes) =>
          val hooks = createHooks(paramGithubPackage, paramFileInfo)
          val uploadedF = hooks.preHook().flatMap { _ =>
            val tmpFile = File.createTempFile(System.currentTimeMillis.toString, ".tmp", directory.toFile)
            bytes
              .runWith(FileIO.toPath(tmpFile.toPath))
              .flatMap { ioResult =>
                ioResult.status match {
                  case Success(_) =>
                    val uploadMD5 = Utils.md5sum(tmpFile)
                    hooks.tmpFileHook(tmpFile, uploadMD5).flatMap {
                      dest =>
                        val responseBody = s"Successfully saved upload of ${hooks.fileInfo.fileName}, ${Utils.humanFileSize(dest)}, MD5 $uploadMD5"
                        val response = HttpResponse(StatusCodes.OK, Nil, HttpEntity(ContentTypes.`text/plain(UTF-8)`, responseBody))
                        hooks.postHook(response)
                    }
                  case Failure(ex) => Future.failed(ex)
                }
              }
              .recoverWith {
                case ex =>
                  logger.error(s"Upload IOResult failed ${ex.getMessage}")
                  tmpFile.delete()
                  Future.failed(ex)
              }
          }
          completeOrRecoverWith(uploadedF) {
            case UserMessageException(statusCode, userMessage) => complete(statusCode, HttpEntity(ContentTypes.`text/plain(UTF-8)`, userMessage))
          }
        //        case (githubPackage, fileInfo, bytes) if fileInfo.fileName.contains("SNAPSHOT") =>
        //          logger.info(s"Receiving SNAPSHOT ${fileInfo.fileName}")
        //          val dest = new File(s"${directory.toFile.getAbsolutePath}/${fileInfo.fileName}")
        //          val uploadedF = bytes
        //            .runWith(FileIO.toPath(dest.toPath))
        //            .flatMap { ioResult =>
        //              ioResult.status match {
        //                case Success(_) =>
        //                  val duration = Duration.ofMillis(System.currentTimeMillis() - start)
        //                  val msg = s"Successfully saved SNAPSHOT upload of  ${fileInfo.fileName}, ${Utils.humanFileSize(dest)} in ${Utils.humanReadableDuration(duration)}"
        //                  logger.info(msg)
        //                  Future.successful(msg)
        //                case Failure(ex) => Future.failed(ex)
        //              }
        //            }
        //          onSuccess(uploadedF) {
        //            body => complete(OK, HttpEntity(ContentTypes.`text/plain(UTF-8)`, body))
        //          }
        //        case (githubPackage, fileInfo, bytes) =>
        //          logger.info(s"Downloading ${fileInfo.fileName}.md5")
        //          val urlMd5 = s"${githubPackage.path}/${fileInfo.fileName}.md5"
        //          val request = withAuthorization(Get(urlMd5), githubPackage)
        //          val uploadedF = httpExt.singleRequest(request).flatMap {
        //            response =>
        //              if (response.status == StatusCodes.OK) {
        //                Utils.sinkToString(response.entity.dataBytes).flatMap { md5 =>
        //                  val dest = new File(s"${directory.toFile.getAbsolutePath}/${fileInfo.fileName}")
        //                  if (dest.exists) {
        //                    logger.warn(s"${dest.getName} already exists, checking MD5")
        //                    val existsMD5 = Utils.md5sum(dest)
        //                    if (md5 != existsMD5) {
        //                      logger.error(s"Deleting ${dest.getName} MD5 not equal: $md5 != $existsMD5")
        //                      if (!dest.delete) throw UserMessageException(StatusCodes.InternalServerError, s"Could not delete partial file ${dest.getName}")
        //                    } else {
        //                      val msg = s"${dest.getName} already exists"
        //                      logger.error(msg)
        //                      throw UserMessageException(StatusCodes.BadRequest, msg)
        //                    }
        //                  }
        //                  val tmpFile = File.createTempFile(System.currentTimeMillis.toString, ".tmp", directory.toFile)
        //                  logger.info(s"Receiving ${fileInfo.fileName} as ${tmpFile.getName}")
        //                  bytes
        //                    .runWith(FileIO.toPath(tmpFile.toPath))
        //                    .flatMap { ioResult =>
        //                      ioResult.status match {
        //                        case Success(_) =>
        //                          val uploadMD5 = Utils.md5sum(tmpFile)
        //                          if (md5 != uploadMD5) {
        //                            val errorMessage = s"Upload ${dest.getName} MD5 not equal, $md5 expected != $uploadMD5"
        //                            logger.error(errorMessage)
        //                            tmpFile.delete
        //                            Future.failed(new UserMessageException(StatusCodes.BadRequest, errorMessage))
        //                          } else {
        //                            tmpFile.renameTo(dest)
        //                            val duration = Duration.ofMillis(System.currentTimeMillis() - start)
        //                            val msg = s"Successfully saved upload of ${fileInfo.fileName}, ${Utils.humanFileSize(dest)}, MD5 $md5 in ${Utils.humanReadableDuration(duration)}"
        //                            logger.info(msg)
        //                            Future.successful(msg)
        //                          }
        //                        case Failure(ex) => Future.failed(ex)
        //                      }
        //                    }
        //                    .recoverWith {
        //                      case ex =>
        //                        logger.error(s"Upload IOResult failed ${ex.getMessage}")
        //                        dest.delete()
        //                        Future.failed(ex)
        //                    }
        //                }
        //              } else {
        //                response.entity.discardBytes()
        //                val ex = UserMessageException(StatusCodes.BadRequest, s"Maven version ${fileInfo.fileName} ${githubPackage.version} does not exist in Github")
        //                logger.error(s"Upload failed ${fileInfo.fileName}, ${ex.getMessage}")
        //                Future.failed(ex)
        //              }
        //          }
        //          completeOrRecoverWith(uploadedF) {
        //            case UserMessageException(statusCode, userMessage) => complete(statusCode, HttpEntity(ContentTypes.`text/plain(UTF-8)`, userMessage))
        //          }
      }
    }
  }

}
