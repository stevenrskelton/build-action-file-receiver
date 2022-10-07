package ca.stevenskelton.httpmavenreceiver

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.model._
import akka.http.scaladsl.{Http, HttpExt}
import org.mockito.ArgumentMatcher
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
                                resource: File,
                                githubPackage: GithubPackage,
                                uri: Uri = Uri./
                              ): HttpRequest = {

    val bodyBytes = getClass.getResourceAsStream(resource.getAbsolutePath).readAllBytes()
    val requestEntity = HttpEntity(ContentTypes.`application/octet-stream`, bodyBytes)
    val filePart = Multipart.FormData.BodyPart.Strict(GithubPackage.FileUploadFieldName, requestEntity, Map("filename" -> resource.getName))
    val parts = githubPackage.multipartFormData :+ filePart
    val multipartForm = Multipart.FormData(parts: _*)
    Post(uri.toString, multipartForm)
  }

  def createHttpExtMock(uri: Uri, httpResponse: HttpResponse, headers: Seq[(String, String)] = Nil): HttpExt = {

    val matches = new ArgumentMatcher[HttpRequest] {
      override def matches(argument: HttpRequest): Boolean = {
        argument.uri == uri && argument.headers.map(header => header.name -> header.value).toSet == headers.toSet
      }
    }

    val mockHttp = mock[HttpExt]
    mockHttp.system shouldReturn actorSystem
    mockHttp.singleRequest(argThat(matches), any, any, any) shouldReturn Future.successful(httpResponse)
    mockHttp
  }
}
