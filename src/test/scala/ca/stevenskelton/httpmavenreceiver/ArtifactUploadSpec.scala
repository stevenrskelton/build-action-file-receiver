package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.file.{Files, Path}
import scala.concurrent.Future

class ArtifactUploadSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  implicit val logger = Logger("specs")

  val validFile = new File("/5c55838e6a9fb7bb5470cb222fd3b1f3.png")
  val uri = Uri("https://maven.pkg.github.com/gh-user/gh-project/gh-groupId/gh.artifact.id/gh.version/5c55838e6a9fb7bb5470cb222fd3b1f3.png.md5")
  val githubPackage = GithubPackage(
    githubUser = "gh-user",
    githubRepository = "gh-project",
    groupId = "gh-groupId",
    artifactId = "gh.artifact.id",
    version = "gh.version",
    githubToken = None
  )

  "Successful Maven responses" should {

    val httpExt = UploadRequestHelper.createHttpExtMock(uri, HttpResponse(entity = HttpEntity("5c55838e6a9fb7bb5470cb222fd3b1f3")))
    val TempDirWithPrefix: Path = Files.createTempDirectory("http-maven-receiver-specs-")
    val artifactUpload = ArtifactUpload(httpExt, TempDirWithPrefix, new MavenMD5CompareRequestHooks(_), 10000)
    val uploadRequest = UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage)

    "save upload if file does not exist, return BadRequest if it already exists" in {
      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Successfully saved upload of 5c55838e6a9fb7bb5470cb222fd3b1f3.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3"
      }
      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldEqual "5c55838e6a9fb7bb5470cb222fd3b1f3.png already exists"
      }
    }

    "throw error if no github form data included in request" in {
      val bodyBytes = getClass.getResourceAsStream(validFile.getAbsolutePath).readAllBytes()
      val requestEntity = HttpEntity(ContentTypes.`application/octet-stream`, bodyBytes)
      val filePart = Multipart.FormData.BodyPart.Strict(GithubPackage.FileUploadFieldName, requestEntity, Map("filename" -> validFile.getName))
      val multipartForm = Multipart.FormData(filePart)
      val invalidRequest = Post(uri.toString, multipartForm)

      invalidRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldEqual GithubPackage.FormErrorMessage
      }
    }
  }
  "Abnormal Maven responses" should {

    "cause error when upload md5 sum doesn't match" in {
      val httpExt = UploadRequestHelper.createHttpExtMock(uri, HttpResponse(entity = HttpEntity("thisMD5doesntmatch")))
      val TempDirWithPrefix: Path = Files.createTempDirectory("http-maven-receiver-specs-")
      val artifactUpload = ArtifactUpload(httpExt, TempDirWithPrefix, new MavenMD5CompareRequestHooks(_), 10000)

      UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage) ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldEqual "Upload 5c55838e6a9fb7bb5470cb222fd3b1f3.png MD5 not equal, thisMD5doesntmatch expected != 5c55838e6a9fb7bb5470cb222fd3b1f3"
      }
    }

    "cause errors when Maven 404" in {
      val httpExt = UploadRequestHelper.createHttpExtMock(uri, HttpResponse(StatusCodes.NotFound))
      val TempDirWithPrefix: Path = Files.createTempDirectory("http-maven-receiver-specs-")
      val artifactUpload = ArtifactUpload(httpExt, TempDirWithPrefix, new MavenMD5CompareRequestHooks(_), 10000)

      UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage) ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldEqual "Maven version 5c55838e6a9fb7bb5470cb222fd3b1f3.png gh.version does not exist in Github"
      }
    }

  }

  "Authorization headers on Maven responses" should {

    "match config if not on request" in {
      val httpExt = UploadRequestHelper.createHttpExtMock(uri, HttpResponse(entity = HttpEntity("5c55838e6a9fb7bb5470cb222fd3b1f3")), Seq("Authorization" -> "token a-token"))
      val TempDirWithPrefix: Path = Files.createTempDirectory("http-maven-receiver-specs-")
      val artifactUpload = ArtifactUpload(httpExt, TempDirWithPrefix, new MavenMD5CompareRequestHooks(_), 10000, Some("a-token"))

      UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage) ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Successfully saved upload of 5c55838e6a9fb7bb5470cb222fd3b1f3.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3"
      }
    }

    "match request if specified" in {

      val httpExt = UploadRequestHelper.createHttpExtMock(uri, HttpResponse(entity = HttpEntity("5c55838e6a9fb7bb5470cb222fd3b1f3")), Seq("Authorization" -> "token b-token"))
      val TempDirWithPrefix: Path = Files.createTempDirectory("http-maven-receiver-specs-")
      val artifactUpload = ArtifactUpload(httpExt, TempDirWithPrefix, new MavenMD5CompareRequestHooks(_), 10000, Some("a-token"))

      val githubPackageB = GithubPackage(
        githubUser = "gh-user",
        githubRepository = "gh-project",
        groupId = "gh-groupId",
        artifactId = "gh.artifact.id",
        version = "gh.version",
        githubToken = Some("b-token")
      )

      UploadRequestHelper.postMultipartFileRequest(validFile, githubPackageB) ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Successfully saved upload of 5c55838e6a9fb7bb5470cb222fd3b1f3.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3"
      }
    }
  }

  "Custom hooks" should {
    val httpExt = UploadRequestHelper.createHttpExtMock(uri, HttpResponse(entity = HttpEntity("5c55838e6a9fb7bb5470cb222fd3b1f3")))

    "save upload if file does not exist, return BadRequest if it already exists" in {
      var preHookCount = 0
      var tmpFileHookCount = 0
      var postHookCount = 0
      var tmpFile: Option[File] = None
      var destFile: Option[File] = None

      val artifactUpload = ArtifactUpload(
        httpExt,
        Files.createTempDirectory("http-maven-receiver-specs-"),
        au => new DefaultRequestHooks(au.directory, logger) {
          override def preHook(formData: Multipart.FormData, requestContext: RequestContext): Future[(GithubPackage, FileInfo, Source[ByteString, Any])] = {
            preHookCount += 1
            super.preHook(formData, requestContext)
          }

          override def tmpFileHook(tmp: File, md5Sum: String): Future[File] = {
            tmpFileHookCount += 1
            tmpFile = Some(tmp)
            super.tmpFileHook(tmp, md5Sum).map {
              dest =>
                destFile = Some(dest)
                dest
            }
          }

          override def postHook(httpResponse: HttpResponse): Future[HttpResponse] = {
            postHookCount += 1
            super.postHook(httpResponse)
          }
        }, 10000)
      val uploadRequest = UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage)

      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Successfully saved upload of 5c55838e6a9fb7bb5470cb222fd3b1f3.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3"
        preHookCount shouldEqual 1
        tmpFileHookCount shouldEqual 1
        postHookCount shouldEqual 1
        tmpFile.get.exists() shouldEqual false
        tmpFile.get.getName should endWith(".tmp")
        destFile.get.exists() shouldEqual true
        destFile.get.getName shouldEqual "5c55838e6a9fb7bb5470cb222fd3b1f3.png"
      }

      tmpFile = None
      destFile = None

      // Check hooks are not called after a failure

      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldEqual "5c55838e6a9fb7bb5470cb222fd3b1f3.png already exists"
        preHookCount shouldEqual 2
        tmpFileHookCount shouldEqual 1
        postHookCount shouldEqual 1
        tmpFile shouldEqual None
        destFile shouldEqual None
      }
    }

    "change response in postHook success" in {
      val artifactUpload = ArtifactUpload(
        httpExt,
        Files.createTempDirectory("http-maven-receiver-specs-"),
        au => new DefaultRequestHooks(au.directory, logger) {
          override def postHook(httpResponse: HttpResponse): Future[HttpResponse] = {
            Future.successful(HttpResponse(StatusCodes.Conflict, entity = HttpEntity("custom-error")))
          }
        }, 10000)
      val uploadRequest = UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage)

      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[String] shouldEqual "custom-error"
      }
    }

    "change response in postHook failure" in {
      val artifactUpload = ArtifactUpload(
        httpExt,
        Files.createTempDirectory("http-maven-receiver-specs-"),
        au => new DefaultRequestHooks(au.directory, logger) {
          override def postHook(httpResponse: HttpResponse): Future[HttpResponse] = {
            Future.failed(UserMessageException(StatusCodes.Conflict, "custom-error"))
          }
        }, 10000)
      val uploadRequest = UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage)

      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[String] shouldEqual "custom-error"
      }
    }

  }


  "Unhandled exceptions" should {
    val httpExt = UploadRequestHelper.createHttpExtMock(uri, HttpResponse(entity = HttpEntity("5c55838e6a9fb7bb5470cb222fd3b1f3")))

    "be returned as generic 500 (1)" in {
      val artifactUpload = ArtifactUpload(
        httpExt,
        Files.createTempDirectory("http-maven-receiver-specs-"),
        au => new DefaultRequestHooks(au.directory, logger) {
          override def preHook(formData: Multipart.FormData, requestContext: RequestContext): Future[(GithubPackage, FileInfo, Source[ByteString, Any])] =
            Future.failed(new Exception("sensitive information and stacktrace"))
        }, 10000)
      val uploadRequest = UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage)

      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.InternalServerError
        responseAs[String] shouldEqual "There was an internal server error."
      }
    }
    "be returned as generic 500 (2)" in {
      val artifactUpload = ArtifactUpload(
        httpExt,
        Files.createTempDirectory("http-maven-receiver-specs-"),
        au => new DefaultRequestHooks(au.directory, logger) {
          override def tmpFileHook(tmp: File, md5Sum: String): Future[File] = Future.failed(new Exception("sensitive information and stacktrace"))
        }, 10000)
      val uploadRequest = UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage)

      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.InternalServerError
        responseAs[String] shouldEqual "There was an internal server error."
      }
    }
    "be returned as generic 500 (3)" in {
      val artifactUpload = ArtifactUpload(
        httpExt,
        Files.createTempDirectory("http-maven-receiver-specs-"),
        au => new DefaultRequestHooks(au.directory, logger) {
          override def postHook(httpResponse: HttpResponse): Future[HttpResponse] = Future.failed(new Exception("sensitive information and stacktrace"))
        }, 10000)
      val uploadRequest = UploadRequestHelper.postMultipartFileRequest(validFile, githubPackage)

      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.InternalServerError
        responseAs[String] shouldEqual "There was an internal server error."
      }
    }
  }
}