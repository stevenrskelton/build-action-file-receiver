package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.Logger

import java.io.File
import java.nio.file.StandardOpenOption
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}

case class GithubPackages(
                           httpExt: HttpExt,
                           directory: File,
                           githubToken: String,
                           githubUser: String,
                           githubRepository: String,
                           groupId: String,
                           artifactId: String,
                           version: String
                         )(implicit logger: Logger) {

  implicit val materializer: Materializer = Materializer(httpExt.system)

  val path = s"https://maven.pkg.github.com/$githubUser/$githubRepository/${groupId.replace(".", "/")}/$artifactId/$version"

  val releasesUrl = s"$path/maven-metadata.xml"

  case class MavenPackage(version: String, updated: ZonedDateTime) {
    def jarFilename: String = s"$artifactId-$version.jar"
  }

  def downloadLatestMavenPackage: Future[File] = {
    fetchLatestMetadata.flatMap {
      metadata => downloadMavenPackage(metadata.head)
    }
  }

  def fetchLatestMetadata: Future[Seq[MavenPackage]] = {
    val request = Get("https://akka.io").addHeader(RawHeader("Authorization", s"token $githubToken"))
    httpExt.singleRequest(request).map {
      response =>
        val entity = Try(response.entity.toString)
        entity.filter(_ => response.status == StatusCodes.OK).map {
          bodyString =>
            val xml = XML.loadString(bodyString)
            parseMavenMetadata(xml)
        }.getOrElse {
          throw new Exception(s"${response.status} Could not fetch Github maven: ${entity.toOption.getOrElse("``")}")
        }
    }
  }

  def downloadMavenMD5(mavenPackage: MavenPackage): Future[String] = {
    logger.info(s"Downloading ${mavenPackage.jarFilename}.md5")
    val request = Get(s"$path/${mavenPackage.jarFilename}.md5").addHeader(RawHeader("Authorization", s"token $githubToken"))
    httpExt.singleRequest(request).map {
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
    val jarfile = new File(s"${directory.getPath}/${mavenPackage.jarFilename}")
    if (jarfile.exists) {
      val msg = s"File ${jarfile.getName} exists"
      val ex = new Exception(msg)
      logger.error(msg, ex)
      Future.failed(ex)
    } else {
      downloadMavenMD5(mavenPackage).flatMap {
        md5sum =>
          val md5file = new File(s"${directory.getPath}/${mavenPackage.jarFilename}.md5")
          //TODO: handle error
          FileUtils.writeFile(md5file, md5sum)
          val url = s"$path/${mavenPackage.jarFilename}"
          val request = Get(url).addHeader(RawHeader("Authorization", s"token $githubToken"))
          //TODO: timeout?
          val futureIOResult = httpExt.singleRequest(request).flatMap {
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
                  val md5SumOfDownload = FileUtils.md5sum(jarfile)
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

  def parseMavenMetadata(metadata: Elem): Seq[MavenPackage] = {
    (metadata \\ "snapshotVersion").withFilter {
      n => (n \ "extension").text == "jar"
    }.flatMap {
      n =>
        for {
          value <- n \ "value"
          updated <- n \ "updated"
        } yield {
          val updatedTime = LocalDateTime.parse(updated.text, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
          MavenPackage(value.text, updatedTime.atZone(ZoneId.of("UTC")))
        }
    }.sortBy(_.updated).reverse
  }

}
