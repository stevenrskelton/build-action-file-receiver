package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.Logger

import java.io.File
import java.nio.file.Path
import java.text.MessageFormat
import java.time.Duration
import scala.concurrent.Future

class DefaultRequestHooks(val directory: Path, val logger: Logger) extends RequestHooks {

  protected val start = System.currentTimeMillis
  protected var destinationFile: File = null
  protected var fileInfo: FileInfo = null

  override def preHook(
                        formFields: Map[String, String],
                        fileInfo: FileInfo,
                        fileSource: Source[ByteString, Any],
                        requestContext: RequestContext
                      ): Future[(GithubPackage, FileInfo, Source[ByteString, Any])] = {
    this.fileInfo = fileInfo
    destinationFile = new File(s"${directory.toFile.getAbsolutePath}/${fileInfo.fileName}")
    if (destinationFile.exists) {
      val msg = s"${destinationFile.getName} already exists"
      logger.error(msg)
      Future.failed(UserMessageException(StatusCodes.BadRequest, msg))
    } else {
      GithubPackage.fromFieldData(formFields).map {
        githubPackage => Future.successful((githubPackage, fileInfo, fileSource))
      }.getOrElse {
        logger.error(GithubPackage.FormErrorMessage)
        Future.failed(UserMessageException(StatusCodes.BadRequest, GithubPackage.FormErrorMessage))
      }
    }
  }

  override def tmpFileHook(tmp: File, md5Sum: String): Future[File] = {
    if (tmp.renameTo(destinationFile)) {
      Future.successful(destinationFile)
    } else {
      val msg = s"Could not rename temporary file ${tmp.getName} to ${destinationFile.getName}"
      logger.error(msg)
      Future.failed(UserMessageException(StatusCodes.BadRequest, msg))
    }
  }

  override def postHook(httpResponse: HttpResponse, allowedGithubUser: AllowedGithubUser, file: File): Future[HttpResponse] = Future.successful {
    val duration = Duration.ofMillis(System.currentTimeMillis - start)
    logger.info(s"Completed ${fileInfo.fileName} in ${Utils.humanReadableDuration(duration)}")
    httpResponse
  }

}
