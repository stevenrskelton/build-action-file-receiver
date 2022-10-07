package ca.stevenskelton.httpmavenreceiver

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.model._
import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar._

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object UploadRequestHelper {

  val actorSystem: ExtendedActorSystem = ActorSystem("specs").asInstanceOf[ExtendedActorSystem]
  val httpExt = Http(actorSystem)

  def postMultipartFileRequest(
                                uri: Uri,
                                resource: File,
                                githubPackage: GithubPackage
                              ): HttpRequest = {

    val bodyBytes = getClass.getResourceAsStream(resource.getAbsolutePath).readAllBytes()
    val requestEntity = HttpEntity(ContentTypes.`application/octet-stream`, bodyBytes)
    val filePart = Multipart.FormData.BodyPart.Strict(GithubPackage.FileUploadFieldName, requestEntity, Map("filename" -> resource.getName))
    val parts = githubPackage.multipartFormData :+ filePart
    val multipartForm = Multipart.FormData(parts: _*)
    Post(uri.toString, multipartForm)
  }

  def createHttpExt(uri: Uri, httpResponse: HttpResponse): HttpExt = {
    val mockHttp = mock[HttpExt]
    mockHttp.system shouldReturn actorSystem
    mockHttp.singleRequest(argThat((argument: HttpRequest) => argument.uri == uri), any, any, any) shouldReturn Future.successful(httpResponse)
    mockHttp
  }
}
