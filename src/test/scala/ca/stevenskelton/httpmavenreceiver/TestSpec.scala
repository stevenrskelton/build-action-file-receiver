package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.scalalogging.Logger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.file.{Files, Path}

class TestSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  implicit val logger = Logger("specs")
  val githubPackage = GithubPackage(
    githubUser = "gh-user",
    githubRepository = "gh-project",
    groupId = "gh-groupId",
    artifactId = "gh.artifact.id",
    version = "gh.version"
  )
  val validFile = new File("/5c55838e6a9fb7bb5470cb222fd3b1f3.png")
  val uploadRequest = UploadRequestHelper.postMultipartFileRequest(Uri("https://localhost"), validFile, githubPackage)


  val TempDirWithPrefix: Path = Files.createTempDirectory("http-maven-receiver-specs-")

  "The service" should {

    val uri = Uri("https://maven.pkg.github.com/gh-user/gh-project/gh-groupId/gh.artifact.id/gh.version/5c55838e6a9fb7bb5470cb222fd3b1f3.png.md5")
    val httpExt = UploadRequestHelper.createHttpExt(uri, HttpResponse(entity = HttpEntity("5c55838e6a9fb7bb5470cb222fd3b1f3")))
    val githubPackages = GithubPackages(httpExt, TempDirWithPrefix, "")
    val artifactUpload = new ArtifactUpload(githubPackages)

    "save upload if not exists, return BadRequest if exists" in {
      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Successfully saved upload of 5c55838e6a9fb7bb5470cb222fd3b1f3.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3 in 0 seconds"
      }
      uploadRequest ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldEqual "5c55838e6a9fb7bb5470cb222fd3b1f3.png already exists"
      }
    }

    "throw error if no github form data included" in {
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
  "service 2" should {

    val uri = Uri("https://maven.pkg.github.com/gh-user/gh-project/gh-groupId/gh.artifact.id/gh.version/5c55838e6a9fb7bb5470cb222fd3b1f3.png.md5")
    val httpExt = UploadRequestHelper.createHttpExt(uri, HttpResponse(entity = HttpEntity("thisMD5doesntmatch")))
    val githubPackages = GithubPackages(httpExt, TempDirWithPrefix, "")
    val artifactUpload = new ArtifactUpload(githubPackages)

    "error if upload md5 sum fails" in {
      UploadRequestHelper.postMultipartFileRequest(
        Uri("https://localhost"),
        validFile,
        githubPackage
      ) ~> artifactUpload.releasesPost ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldEqual "Upload 5c55838e6a9fb7bb5470cb222fd3b1f3.png MD5 not equal, thisMD5doesntmatch expected != 5c55838e6a9fb7bb5470cb222fd3b1f3"
      }
    }

  }




  //  val githubPackages = GithubPackages()
  //  val artifactUpload = ArtifactUpload()
  //  val smallRoute =
  //    get {
  //      concat(
  //        pathSingleSlash {
  //          complete {
  //            "Captain on the bridge!"
  //          }
  //        },
  //        path("ping") {
  //          complete("PONG!")
  //        }
  //      )
  //    }
  //

  //    "return a 'PONG!' response for GET requests to /ping" in {
  //      // tests:
  //      Get("/ping") ~> smallRoute ~> check {
  //        responseAs[String] shouldEqual "PONG!"
  //      }
  //    }
  //
  //    "leave GET requests to other paths unhandled" in {
  //      // tests:
  //      Get("/kermit") ~> smallRoute ~> check {
  //        handled shouldBe false
  //      }
  //    }
  //
  //    "return a MethodNotAllowed error for PUT requests to the root path" in {
  //      // tests:
  //      Put() ~> Route.seal(smallRoute) ~> check {
  //        status shouldEqual StatusCodes.MethodNotAllowed
  //        responseAs[String] shouldEqual "HTTP method not allowed, supported methods: GET"
  //      }
  //    }
  //  }
}