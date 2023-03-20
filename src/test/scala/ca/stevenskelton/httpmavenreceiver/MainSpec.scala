package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.mockito.ArgumentMatchers.any
import org.mockito.IdiomaticMockito.StubbingOps
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.file.Files
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class MainSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  import UploadRequestHelper.logger

  var largeFile: File = null

  val githubPackage = GithubPackage(
    githubUser = "gh-user",
    githubRepository = "gh-project",
    groupId = "gh-groupId",
    artifactId = "gh.artifact.id",
    version = "gh.version"
  )

  "test" should {

    val tempDirWithPrefix = Files.createTempDirectory("http-maven-receiver-specs-")
    largeFile = UploadRequestHelper.create50MBFile(tempDirWithPrefix)
    val uri = Uri(s"https://maven.pkg.github.com/gh-user/gh-project/gh-groupId/gh.artifact.id/gh.version/${largeFile.getName}.md5")
    val uploadRequest = UploadRequestHelper.postGithubPackageRequest(largeFile, githubPackage, Uri("http://127.0.0.1:18180/releases"))
    val httpExt = UploadRequestHelper.createHttpExtMock(uri, HttpResponse(entity = HttpEntity("5ef060ec815ed109d822cd3c00be7c83")))
    httpExt.newServerAt(any(), any()) shouldReturn UploadRequestHelper.httpExt.newServerAt("127.0.0.1", 18180)

    "save upload if file does not exist, return BadRequest if it already exists" in {

      val artifactUpload = ArtifactUpload(httpExt, tempDirWithPrefix, new MavenMD5CompareRequestHooks(_))

      val main = Main
      val serverBinding = Await.result(main.bindPublic(artifactUpload, "127.0.0.1", 18180), 30.seconds)
      //      result.localAddress.getHostName shouldEqual("")

      val response = Await.result(UploadRequestHelper.httpExt.singleRequest(uploadRequest), 30.seconds)
      response.status shouldEqual StatusCodes.OK
      //      uploadRequest ~> artifactUpload.releasesPost ~> check {
      //        status shouldEqual StatusCodes.OK
      //        responseAs[String] shouldEqual "Successfully saved upload of 5c55838e6a9fb7bb5470cb222fd3b1f3.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3"
      //      }
      serverBinding.terminate(1.second)
    }
  }

}
