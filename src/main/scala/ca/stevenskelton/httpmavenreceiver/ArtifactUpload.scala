package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, completeOrRecoverWith, onSuccess, post, withSizeLimit}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl._
import com.typesafe.scalalogging.Logger

import java.io.File
import java.time.Duration
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success}

class ArtifactUpload(val githubPackages: GithubPackages)(implicit logger: Logger) {

  private implicit val materializer: Materializer = githubPackages.materializer

  private def humanReadableDuration(duration: Duration): String = {
    val seconds = duration.toSeconds
    if (seconds >= 2592000) {
      s"${(seconds / 2592000).toInt} months"
    } else if (seconds > 86400) {
      s"${(seconds / 86400).toInt} days"
    } else if (seconds > 3600) {
      s"${(seconds / 3600).toInt} hours"
    } else if (seconds > 60) {
      s"${(seconds / 60).toInt} minutes"
    } else {
      s"$seconds seconds"
    }
  }

  val releasesPost: Route = withSizeLimit(GithubPackage.MaxUploadByteSize) {
    post {
      val start = System.currentTimeMillis()
      FileUploadDirectives.githubPackageUpload {
        case (_, fileInfo, bytes) if fileInfo.fileName.contains("SNAPSHOT") =>
          logger.info(s"Receiving SNAPSHOT ${fileInfo.fileName}")
          val dest = new File(s"${githubPackages.directory.toFile.getAbsolutePath}/${fileInfo.fileName}")
          val uploadedF = bytes
            .runWith(FileIO.toPath(dest.toPath))
            .flatMap { ioResult =>
              ioResult.status match {
                case Success(_) =>
                  val duration = Duration.ofMillis(System.currentTimeMillis() - start)
                  val msg = s"Successfully saved SNAPSHOT upload of  ${fileInfo.fileName}, ${FileUtils.humanFileSize(dest)} in ${humanReadableDuration(duration)}"
                  logger.info(msg)
                  Future.successful(msg)
                case Failure(ex) => Future.failed(ex)
              }
            }
          onSuccess(uploadedF) {
            body => complete(OK, HttpEntity(ContentTypes.`text/plain(UTF-8)`, body))
          }
        case (githubPackage, fileInfo, bytes) =>
          logger.info(s"Downloading ${fileInfo.fileName}.md5")
          val urlMd5 = s"${githubPackage.path}/${fileInfo.fileName}.md5"
          val request = Get(urlMd5).addHeader(RawHeader("Authorization", s"token ${githubPackages.githubToken}"))
          val uploadedF = githubPackages.httpExt.singleRequest(request).flatMap {
            response =>
              if (response.status == StatusCodes.OK) {
                FileUtils.sinkToString(response.entity.dataBytes).flatMap { md5 =>
                  val dest = new File(s"${githubPackages.directory.toFile.getAbsolutePath}/${fileInfo.fileName}")
                  if (dest.exists) {
                    logger.warn(s"${dest.getName} already exists, checking MD5")
                    val existsMD5 = FileUtils.md5sum(dest)
                    if (md5 != existsMD5) {
                      logger.error(s"Deleting ${dest.getName} MD5 not equal: $md5 != $existsMD5")
                      if (!dest.delete) throw UserMessageException(StatusCodes.InternalServerError, s"Could not delete partial file ${dest.getName}")
                    } else {
                      val msg = s"${dest.getName} already exists"
                      logger.error(msg)
                      throw UserMessageException(StatusCodes.BadRequest, msg)
                    }
                  }
                  val tmpFile = File.createTempFile(System.currentTimeMillis.toString, ".tmp", githubPackages.directory.toFile)
                  logger.info(s"Receiving ${fileInfo.fileName} as ${tmpFile.getName}")
                  bytes
                    .runWith(FileIO.toPath(tmpFile.toPath))
                    .flatMap { ioResult =>
                      ioResult.status match {
                        case Success(_) =>
                          val uploadMD5 = FileUtils.md5sum(tmpFile)
                          if (md5 != uploadMD5) {
                            val errorMessage = s"Upload ${dest.getName} MD5 not equal, $md5 expected != $uploadMD5"
                            logger.error(errorMessage)
                            tmpFile.delete
                            Future.failed(new UserMessageException(StatusCodes.BadRequest, errorMessage))
                          } else {
                            tmpFile.renameTo(dest)
                            val duration = Duration.ofMillis(System.currentTimeMillis() - start)
                            val msg = s"Successfully saved upload of ${fileInfo.fileName}, ${FileUtils.humanFileSize(dest)}, MD5 $md5 in ${humanReadableDuration(duration)}"
                            logger.info(msg)
                            Future.successful(msg)
                          }
                        case Failure(ex) => Future.failed(ex)
                      }
                    }
                    .recoverWith {
                      case ex =>
                        logger.error(s"Upload IOResult failed ${ex.getMessage}")
                        dest.delete()
                        Future.failed(ex)
                    }
                }
              } else {
                response.entity.discardBytes()
                val ex = UserMessageException(StatusCodes.BadRequest, s"Maven version ${fileInfo.fileName} does not exist in Github")
                logger.error(s"Upload failed ${fileInfo.fileName}", ex)
                Future.failed(ex)
              }
          }
          completeOrRecoverWith(uploadedF) {
            case UserMessageException(statusCode, userMessage) => complete(statusCode, HttpEntity(ContentTypes.`text/plain(UTF-8)`, userMessage))
          }
      }
    }
  }
}
