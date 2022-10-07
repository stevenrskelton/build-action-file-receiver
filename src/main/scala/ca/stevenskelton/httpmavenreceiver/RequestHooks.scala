package ca.stevenskelton.httpmavenreceiver

import akka.Done
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.directives.FileInfo
import com.typesafe.scalalogging.Logger

import java.io.File
import java.nio.file.Path
import java.time.Duration
import scala.concurrent.Future

class RequestHooks(val directory: Path, val githubPackage: GithubPackage, val fileInfo: FileInfo, val logger: Logger) {

  private val start = System.currentTimeMillis()

  val destinationFile = new File(s"${directory.toFile.getAbsolutePath}/${fileInfo.fileName}")

  def preHook(): Future[Done] = {
    if (destinationFile.exists) {
      val msg = s"${destinationFile.getName} already exists"
      logger.error(msg)
      Future.failed(UserMessageException(StatusCodes.BadRequest, msg))
    } else {
      Future.successful(Done)
    }
  }

  def tmpFileHook(tmp: File, md5Sum: String): Future[File] = {
    if (tmp.renameTo(destinationFile)) {
      Future.successful(destinationFile)
    } else {
      val msg = s"Could not rename temporary file ${tmp.getName} to ${destinationFile.getName}"
      logger.error(msg)
      Future.failed(UserMessageException(StatusCodes.BadRequest, msg))
    }
  }

  def postHook(httpResponse: HttpResponse): Future[HttpResponse] = Future.successful {
    val duration = Duration.ofMillis(System.currentTimeMillis() - start)
    logger.info(s"Completed ${fileInfo.fileName} in ${Utils.humanReadableDuration(duration)}")
    httpResponse
  }

}

