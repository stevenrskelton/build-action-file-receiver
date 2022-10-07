package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, completeOrRecoverWith, extractRequestContext, post, withSizeLimit}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MarshallingDirectives.{as, entity}
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
                          createHooks: ArtifactUpload => RequestHooks,
                          githubToken: Option[String] = None,
                          maxUploadFileSizeBytes: Long = 100000
                         )(implicit val logger: Logger) {

  implicit val materializer: Materializer = Materializer(httpExt.system)

  val releasesPost: Route = withSizeLimit(maxUploadFileSizeBytes) {
    post {
      extractRequestContext { ctx =>
        entity(as[Multipart.FormData]) { formData =>
          val hooks = createHooks(this)
          val uploadedF = hooks.preHook(formData, ctx).flatMap {
            case (_, fileInfo, bytes) =>
              val tmpFile = File.createTempFile(System.currentTimeMillis.toString, ".tmp", directory.toFile)
              bytes
                .runWith(FileIO.toPath(tmpFile.toPath))
                .flatMap { ioResult =>
                  ioResult.status match {
                    case Success(_) =>
                      val uploadMD5 = Utils.md5sum(tmpFile)
                      hooks.tmpFileHook(tmpFile, uploadMD5).flatMap {
                        dest =>
                          val responseBody = s"Successfully saved upload of ${fileInfo.fileName}, ${Utils.humanFileSize(dest)}, MD5 $uploadMD5"
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
        }
      }
    }
  }
}