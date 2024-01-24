//package ca.stevenskelton.httpmavenreceiver
//
//import org.apache.pekko.actor.{ActorSystem, ExtendedActorSystem}
//import org.apache.pekko.http.scaladsl.client.RequestBuilding.Post
//import org.apache.pekko.http.scaladsl.model._
//import org.apache.pekko.http.scaladsl.{Http, HttpExt}
//import com.typesafe.scalalogging.Logger
//import org.mockito.ArgumentMatcher
//import org.mockito.ArgumentMatchers.{any, argThat}
//import org.mockito.IdiomaticMockito.StubbingOps
//import org.mockito.MockitoSugar._
//
//import java.io.{File, FileOutputStream}
//import java.nio.file.Path
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.Future
//import scala.util.Using
//
//object UploadRequestHelper {
//
//  val actorSystem: ExtendedActorSystem = ActorSystem("specs").asInstanceOf[ExtendedActorSystem]
//  val httpExt = Http(actorSystem)
//  implicit val logger = Logger("specs")
//
//  def toMap(githubPackage: GitHubPackage): Map[String, String] = Map(
//    "githubUser" -> githubPackage.githubUser,
//    "githubRepository" -> githubPackage.githubRepository,
//    "groupId" -> githubPackage.groupId,
//    "artifactId" -> githubPackage.artifactId,
//    "version" -> githubPackage.version
//  )
//
//  def postGitHubPackageRequest(
//                                resource: File,
//                                githubPackage: GitHubPackage,
//                                uri: Uri = Uri./
//                              ): HttpRequest = postMultipartFileRequest(resource, toMap(githubPackage), uri)
//
//  def postMultipartFileRequest(
//                                resource: File,
//                                formFields: Map[String, String],
//                                uri: Uri = Uri./
//                              ): HttpRequest = {
//
//    //TODO: this could be streamed instead of using a Byte[]
//    val bodyBytes = Option(getClass.getResourceAsStream(resource.getAbsolutePath)).map(_.readAllBytes).getOrElse {
//      java.nio.file.Files.readAllBytes(resource.toPath)
//    }
//    val requestEntity = HttpEntity(ContentTypes.`application/octet-stream`, bodyBytes)
//    val filePart = Multipart.FormData.BodyPart.Strict(GitHubPackage.FileUploadFieldName, requestEntity, Map("filename" -> resource.getName))
//    val parts = formFields.toSeq.map(keyValue => Multipart.FormData.BodyPart.Strict.apply(keyValue._1, HttpEntity(keyValue._2))) :+ filePart
//    val multipartForm = Multipart.FormData(parts: _*)
//    Post(uri.toString, multipartForm)
//  }
//
//  def create50MBFile(tmpDir: Path): File = {
//    val dir = new File(s"${tmpDir.toFile.getPath}/upload")
//    dir.mkdir()
//    val file = new File(s"${dir.getPath}/test.bin")
//    if (!file.exists) {
//      //50MB file, this needs to fit into memory to make the request
//      Using(new FileOutputStream(file)) {
//        stream =>
//          val empty = new Array[Byte](1024)
//          (0 to 50000).foreach {
//            _ => stream.write(empty)
//          }
//      }
//    }
//    file.deleteOnExit
//    file
//  }
//
//  def createHttpExtMock(uri: Uri, httpResponse: HttpResponse, headers: Seq[(String, String)] = Nil): HttpExt = {
//
//    val matches = new ArgumentMatcher[HttpRequest] {
//      override def matches(argument: HttpRequest): Boolean = {
//        argument.uri == uri && argument.headers.map(header => header.name -> header.value).toSet == headers.toSet
//      }
//    }
//
//    val mockHttp = mock[HttpExt]
//    mockHttp.system shouldReturn actorSystem
//    mockHttp.singleRequest(argThat(matches), any, any, any) shouldReturn Future.successful(httpResponse)
//    mockHttp
//  }
//}
