package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import ca.stevenskelton.httpmavenreceiver.MavenMD5CompareRequestHooks.MavenPackage

import java.io.File
import java.nio.file.StandardOpenOption
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}

class MavenMD5CompareRequestHooks(artifactUpload: ArtifactUploadRoute)
  extends DefaultRequestHooks(artifactUpload.directory, artifactUpload.logger) {

  import artifactUpload.materializer

  private def withAuthorization(request: HttpRequest, githubAuthToken: String): HttpRequest = {
    request.addHeader(RawHeader("Authorization", s"token $githubAuthToken"))
  }

  protected var md5Sum: String = _

  override def preHook(
                        formFields: Map[String, String],
                        fileInfo: FileInfo,
                        fileSource: Source[ByteString, Any],
                        requestContext: RequestContext
                      ): Future[(GithubPackage, FileInfo, Source[ByteString, Any])] = {
    super.preHook(formFields, fileInfo, fileSource, requestContext).flatMap {
      case obj@(githubPackage, fileInfo, fileSource) =>
        if (fileInfo.fileName.contains("SNAPSHOT")) {
          Future.successful(obj)
        } else {
          val urlMd5 = s"${githubPackage.path}/${fileInfo.fileName}.md5"
          logger.info(s"Fetching MD5 at $urlMd5")
          val request = withAuthorization(Get(urlMd5), formFields("githubAuthToken"))
          artifactUpload.httpExt.singleRequest(request).flatMap {
            response =>
              if (response.status == StatusCodes.OK) {
                Utils.sinkToString(response.entity.dataBytes).map {
                  md5 =>
                    md5Sum = md5
                    logger.info(s"MD5 retrieved, $md5Sum")
                    obj
                }
              } else {
                val errorMessage = s"Maven version ${fileInfo.fileName} ${githubPackage.version} does not exist in Github"
                logger.error(errorMessage)
                Future.failed(UserMessageException(StatusCodes.BadRequest, errorMessage))
              }
          }
        }
    }
  }

  override def tmpFileHook(tmp: File, md5Sum: String): Future[File] = {
    if (fileInfo.fileName.contains("SNAPSHOT")) {
      super.tmpFileHook(tmp, md5Sum)
    } else {
      if (md5Sum != this.md5Sum) {
        val errorMessage = s"Upload ${destinationFile.getName} MD5 not equal, ${this.md5Sum} expected != $md5Sum"
        logger.error(errorMessage)
        tmp.delete
        Future.failed(UserMessageException(StatusCodes.BadRequest, errorMessage))
      } else {
        logger.info(s"MD5 validated $md5Sum, saving file at ${destinationFile.getName}")
        tmp.renameTo(destinationFile)
        Future.successful(destinationFile)
      }
    }
  }

  override def postHook(httpResponse: HttpResponse, allowedGithubUser: AllowedGithubUser, file: File): Future[HttpResponse] =
    super.postHook(httpResponse, allowedGithubUser, file)

  def downloadLatestMavenPackage(githubPackage: GithubPackage): Future[File] = {
    fetchLatestMetadata(githubPackage).flatMap {
      metadata => downloadMavenPackage(metadata.head)
    }
  }

  private def fetchLatestMetadata(githubPackage: GithubPackage): Future[Seq[MavenPackage]] = {
    val request = withAuthorization(Get(s"${githubPackage.path}/maven-metadata.xml"), githubPackage.githubAuthToken)
    artifactUpload.httpExt.singleRequest(request).map {
      response =>
        val entity = Try(response.entity.toString)
        entity.filter(_ => response.status == StatusCodes.OK).map {
          bodyString =>
            val xml = XML.loadString(bodyString)
            MavenMD5CompareRequestHooks.parseMavenMetadata(githubPackage, xml)
        }.getOrElse {
          throw new Exception(s"${response.status} Could not fetch Github maven: ${entity.toOption.getOrElse("``")}")
        }
    }
  }

  def downloadMavenMD5(mavenPackage: MavenPackage): Future[String] = {
    logger.info(s"Downloading ${mavenPackage.jarFilename}.md5")
    val request = withAuthorization(Get(s"${mavenPackage.artifactUrl}.md5"), mavenPackage.githubPackage.githubAuthToken)
    artifactUpload.httpExt.singleRequest(request).map {
      response =>
        if (response.status == StatusCodes.OK) {
          response.entity.toString
        } else {
          val error = s"${response.status.value} Could not download MD5 for ${mavenPackage.jarFilename}: ${response.entity}"
          val ex = new Exception(error)
          logger.error(error, ex)
          throw ex
        }
    }
  }

  def downloadMavenPackage(mavenPackage: MavenPackage): Future[File] = {
    val jarfile = new File(s"${directory.toFile.getPath}/${mavenPackage.jarFilename}")
    if (jarfile.exists) {
      val msg = s"File ${jarfile.getName} exists"
      val ex = new Exception(msg)
      logger.error(msg, ex)
      Future.failed(ex)
    } else {
      downloadMavenMD5(mavenPackage).flatMap {
        md5sum =>
          val md5file = new File(s"${directory.toFile.getPath}/${mavenPackage.jarFilename}.md5")
          //TODO: handle error
          Utils.writeFile(md5file, md5sum)(logger)
          val request = withAuthorization(Get(mavenPackage.artifactUrl), mavenPackage.githubPackage.githubAuthToken)
          //TODO: timeout?
          val futureIOResult = artifactUpload.httpExt.singleRequest(request).flatMap {
            response =>
              if (response.status == StatusCodes.OK) {
                response.entity.dataBytes.runWith(FileIO.toPath(jarfile.toPath, Set(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)))
              } else {
                response.discardEntityBytes()
                val ex = new Exception(s"${response.status} Could not fetch Github maven.")
                logger.error(s"Download ${jarfile.getName} failed", ex)
                Future.failed(ex)
              }
          }
          futureIOResult.onComplete {
            case Success(ioResult) =>
              ioResult.status match {
                case Success(_) =>
                  val md5SumOfDownload = Utils.md5sum(jarfile)
                  if (md5sum != md5SumOfDownload) {
                    logger.error(s"MD5SUM of ${jarfile.getName} not equal: $md5sum != $md5SumOfDownload")
                    jarfile.delete
                    md5file.delete
                  }
                case Failure(ioEx) =>
                  logger.error(s"IOResult ${jarfile.getName} failed", ioEx)
                  jarfile.delete
                  md5file.delete
              }
            case Failure(ex) =>
              logger.error(s"Download ${jarfile.getName} failed", ex)
              jarfile.delete
              md5file.delete
          }
          Future.successful(jarfile)
      }
    }
  }

}

object MavenMD5CompareRequestHooks {

  private def parseMavenMetadata(githubPackage: GithubPackage, metadata: Elem): Seq[MavenPackage] = {
    (metadata \\ "snapshotVersion").withFilter {
      n => (n \ "extension").text == "jar"
    }.flatMap {
      n =>
        for {
          value <- n \ "value"
          updated <- n \ "updated"
        } yield {
          val updatedTime = LocalDateTime.parse(updated.text, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
          MavenPackage(githubPackage, value.text, updatedTime.atZone(ZoneId.of("UTC")))
        }
    }.sortBy(_.updated).reverse
  }

  private case class MavenPackage(githubPackage: GithubPackage, version: String, updated: ZonedDateTime) {
    val jarFilename: String = s"${githubPackage.artifactId}-${githubPackage.version}.jar"
    val artifactUrl = s"${githubPackage.path}/$jarFilename"
  }
}