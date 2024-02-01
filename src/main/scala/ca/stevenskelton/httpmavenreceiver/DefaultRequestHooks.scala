//package ca.stevenskelton.httpmavenreceiver
//
//import ca.stevenskelton.httpmavenreceiver.MainHttp4s.loggerFactory.LoggerType
//import cats.effect.IO
//import com.typesafe.scalalogging.Logger
//import fs2.io.file.Path
//import org.apache.pekko.http.scaladsl.model.{HttpResponse, StatusCodes}
//import org.apache.pekko.http.scaladsl.server.RequestContext
//import org.apache.pekko.http.scaladsl.server.directives.FileInfo
//import org.apache.pekko.stream.scaladsl.Source
//import org.apache.pekko.util.ByteString
//import org.typelevel.log4cats.LoggerFactory
//
//import java.io.File
//import java.time.Duration
//import scala.concurrent.Future
//
//class DefaultRequestHooks(val directory: Path, val loggerFactory: LoggerFactory[IO]) extends RequestHooks {
//
//  private val logger: LoggerType = loggerFactory.getLoggerFromClass(getClass)
//  
//  protected val start: Long = System.currentTimeMillis
//  protected var destinationFile: File = null
//  protected var fileInfo: FileInfo = null
//
//  override def preHook(
//                        formFields: Map[String, String],
//                        fileInfo: FileInfo,
//                        fileSource: Source[ByteString, Any],
//                        requestContext: RequestContext
//                      ): Future[(GitHubPackage, FileInfo, Source[ByteString, Any])] = {
//    this.fileInfo = fileInfo
//    destinationFile = new File(s"${directory.toFile.getAbsolutePath}/${fileInfo.fileName}")
//    if (destinationFile.exists) {
//      val msg = s"${destinationFile.getName} already exists"
//      logger.error(msg)
//      Future.failed(UserMessageException(StatusCodes.BadRequest, msg))
//    } else {
//      GitHubPackage.fromFieldData(formFields).map {
//        githubPackage => Future.successful((githubPackage, fileInfo, fileSource))
//      }.getOrElse {
//        logger.error(GitHubPackage.FormErrorMessage)
//        Future.failed(UserMessageException(StatusCodes.BadRequest, GitHubPackage.FormErrorMessage))
//      }
//    }
//  }
//
//  override def tmpFileHook(tmp: File, md5Sum: String): Future[File] = {
//    if (tmp.renameTo(destinationFile)) {
//      Future.successful(destinationFile)
//    } else {
//      val msg = s"Could not rename temporary file ${tmp.getName} to ${destinationFile.getName}"
//      logger.error(msg)
//      Future.failed(UserMessageException(StatusCodes.BadRequest, msg))
//    }
//  }
//
//  override def postHook(httpResponse: HttpResponse, file: File): Future[HttpResponse] = Future.successful {
//    val duration = Duration.ofMillis(System.currentTimeMillis - start)
//    logger.info(s"Completed ${fileInfo.fileName} in ${Utils.humanReadableDuration(duration)}")
//    httpResponse
//  }
//
//}
