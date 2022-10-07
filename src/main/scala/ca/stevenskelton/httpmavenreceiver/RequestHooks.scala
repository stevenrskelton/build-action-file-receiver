package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.model.{HttpResponse, Multipart}
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.Source
import akka.util.ByteString

import java.io.File
import scala.concurrent.Future

trait RequestHooks {

  /**
   * Executes when request is received
   *
   * @return
   */
  def preHook(form: Multipart.FormData, requestContext: RequestContext): Future[(GithubPackage, FileInfo, Source[ByteString, Any])]

  /**
   * Executes after file has been uploaded to a temporary file
   *
   * @param tmp
   * @param md5Sum
   * @return Location where temporary file should be moved
   */
  def tmpFileHook(tmp: File, md5Sum: String): Future[File]

  /**
   * Blocks response until complete
   *
   * @param httpResponse
   * @return
   */
  def postHook(httpResponse: HttpResponse): Future[HttpResponse]
}

