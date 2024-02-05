package ca.stevenskelton.httpmavenreceiver.githubmaven

import ca.stevenskelton.httpmavenreceiver.{FileUploadFormData, ResponseException}
import cats.effect.{IO, Resource}
import org.http4s.*
import org.http4s.client.Client
import org.typelevel.log4cats.LoggerFactory

import scala.util.Try

case class MD5Util(httpClient: Resource[IO, Client[IO]])(implicit loggerFactory: LoggerFactory[IO]) {

  private val logger = loggerFactory.getLoggerFromClass(getClass)

  def fetchMavenMD5(fileUploadFormData: FileUploadFormData): IO[String] = {
    //    if (filename.contains("SNAPSHOT")) {
    //      IO.pure((gitHubPackage, filename))
    //    } else {
    val gitHubMD5Uri = Uri.fromString(s"${MavenPackage.artifactUrl(fileUploadFormData)}.md5").getOrElse {
      val msg = s"Invalid package filename ${MavenPackage.artifactUrl(fileUploadFormData)}"
      return IO.raiseError(ResponseException(Status.BadRequest, msg))
    }
    logger.info(s"Fetching MD5 at $gitHubMD5Uri")
    httpClient.use {
      client =>
        val request = Request[IO](
          Method.GET,
          gitHubMD5Uri,
          headers = Headers(Header.ToRaw.keyValuesToRaw("Authorization" -> s"token ${fileUploadFormData.authToken}")),
        )
        client.expectOr[String](request) {
          errorResponse =>
            val msg = if (errorResponse.status.code == Status.NotFound.code) {
              s"Maven ${fileUploadFormData.filename} does not exist in GitHub"
            } else {
              val errorBody = Try(errorResponse.entity.body.bufferAll.compile.toString).toOption.getOrElse("[error reading body]")
              s"Error reading Maven (${errorResponse.status.code}):\n$errorBody"
            }
            logger.error(msg)
            IO.raiseError(ResponseException(errorResponse.status, msg))
        }
    }
    //    }
  }

  //
  //  def downloadLatestMavenPackage(fileUploadFormData: FileUploadFormData): IO[File] = {
  //    MetadataUtil.fetchMetadata(httpClient, fileUploadFormData).flatMap(downloadMavenPackage)
  //  }
  //
  //    private def downloadMavenMD5(mavenPackage: MavenPackage): IO[String] = {
  //      logger.info(s"Downloading ${mavenPackage.jarFilename}.md5")
  //      val request = withAuthorization(Get(s"${mavenPackage.artifactUrl}.md5"), mavenPackage.githubPackage.githubAuthToken)
  //      artifactUpload.httpExt.singleRequest(request).map {
  //        response =>
  //          if (response.status == StatusCodes.OK) {
  //            response.entity.toString
  //          } else {
  //            val error = s"${response.status.value} Could not download MD5 for ${mavenPackage.jarFilename}: ${response.entity}"
  //            val ex = new Exception(error)
  //            logger.error(error, ex)
  //            throw ex
  //          }
  //      }
  //    }
  //  
  //
  //
  //
  //
  //  private def downloadMavenPackage(mavenPackage: MavenPackage): IO[File] = {
  //    val jarFile = new File(s"${directory.toFile.getPath}/${mavenPackage.jarFilename}")
  //    if (jarFile.exists) {
  //      val msg = s"File ${jarFile.getName} exists"
  //      val ex = new Exception(msg)
  //      logger.error(msg, ex)
  //      Future.failed(ex)
  //    } else {
  //      downloadMavenMD5(mavenPackage).flatMap {
  //        md5sum =>
  //          val md5file = new File(s"${directory.toFile.getPath}/${mavenPackage.jarFilename}.md5")
  //          //TODO: handle error
  //          Utils.writeFile(md5file, md5sum)(logger)
  //          val request = withAuthorization(Get(mavenPackage.artifactUrl), mavenPackage.githubPackage.githubAuthToken)
  //          //TODO: timeout?
  //          val futureIOResult = artifactUpload.httpExt.singleRequest(request).flatMap {
  //            response =>
  //              if (response.status == StatusCodes.OK) {
  //                response.entity.dataBytes.runWith(FileIO.toPath(jarFile.toPath, Set(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)))
  //              } else {
  //                response.discardEntityBytes()
  //                val ex = new Exception(s"${response.status} Could not fetch GitHub maven.")
  //                logger.error(s"Download ${jarFile.getName} failed", ex)
  //                Future.failed(ex)
  //              }
  //          }
  //          futureIOResult.onComplete {
  //            case Success(ioResult) =>
  //              val md5SumOfDownload = Utils.md5sum(jarFile)
  //              if (md5sum != md5SumOfDownload) {
  //                logger.error(s"MD5SUM of ${jarFile.getName} not equal: $md5sum != $md5SumOfDownload")
  //                jarFile.delete
  //                md5file.delete
  //              }
  //            case Failure(ex) =>
  //              logger.error(s"Download ${jarFile.getName} failed", ex)
  //              jarFile.delete
  //              md5file.delete
  //          }
  //          Future.successful(jarFile)
  //      }
  //    }
  //  }
}
