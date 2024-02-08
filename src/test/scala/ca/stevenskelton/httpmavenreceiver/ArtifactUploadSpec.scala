package ca.stevenskelton.httpmavenreceiver

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.http4s.*
import org.http4s.client.Client
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.File

class ArtifactUploadSpec extends AsyncFreeSpec with Matchers with AsyncIOSpec {

  private val requestUri = Uri.unsafeFromString("http://localhost/releases")

  "Version 1.0.10" - {

    val uploadFilename = "test-file-1.0.10.png"
    val uploadFile = new File(s"/test-file/1.0.10/$uploadFilename")
     val uploadFileMD5uri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/1.0.10/$uploadFilename.md5")
     val uploadFileMD5File = new File(s"/test-file/1.0.10/$uploadFilename.md5")

     val uploadPackageMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/maven-metadata.xml")
     val uploadPackageMetadataFile = new File("/maven/maven-metadata.xml")
     val uploadFileForm = Map(
      "authToken" -> "",
      "user" -> "gh-user",
      "repository" -> "gh-project",
      "groupId" -> "gh.groupid",
      "artifactId" -> "test-file",
      "packaging" -> "png",
      "version" -> "1.0.10"
    )
    
    "save upload if file does not exist, return BadRequest if it already exists" in {
      val gitHubResponses = Map(
        uploadPackageMetadataUri -> UploadRequestHelper.successResponse(uploadPackageMetadataFile),
        uploadFileMD5uri -> UploadRequestHelper.successResponse(uploadFileMD5File)
      )
      val httpApp = UploadRequestHelper.httpApp(gitHubResponses).unsafeRunSync()

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      val resp1: IO[String] = client.expect[String](request)
      assert(resp1.unsafeRunSync() == "Successfully saved upload of test-file-1.0.10.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3")

      val resp2: IO[String] = client.expect[String](request)
      val ex = intercept[ResponseException](resp2.unsafeRunSync())
      assert(ex.status == Status.Conflict)
      assert(ex.message == "test-file-1.0.10.png already exists")
    }

    "throw error if no github form data included in request" in {
      val httpApp = UploadRequestHelper.httpApp(Map.empty).unsafeRunSync()

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, Map.empty, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      val resp1: IO[String] = client.expect[String](request)
      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.BadRequest)
      assert(ex.message == FileUploadFormData.FormErrorMessage)
    }

    "cause error when upload md5 sum doesn't match" in {
      val gitHubResponses = Map(
        uploadPackageMetadataUri -> UploadRequestHelper.successResponse(uploadPackageMetadataFile),
        uploadFileMD5uri -> Response(entity = Entity.utf8String("36a9ba7d32ad98d518f67bd6b1787233"))
      )
      val httpApp = UploadRequestHelper.httpApp(gitHubResponses).unsafeRunSync()

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      val resp1: IO[String] = client.expect[String](request)
      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.Conflict)
      assert(ex.message == "Upload test-file-1.0.10.png MD5 not equal, 36a9ba7d32ad98d518f67bd6b1787233 expected != 5c55838e6a9fb7bb5470cb222fd3b1f3 of upload.")
    }

    "cause error when version doesn't exist" in {
      val gitHubResponses = Map(
        uploadPackageMetadataUri -> UploadRequestHelper.successResponse(uploadPackageMetadataFile)
      )
      val httpApp = UploadRequestHelper.httpApp(gitHubResponses).unsafeRunSync()

      val uploadFileForm404 = Map(
        "authToken" -> "",
        "user" -> "gh-user",
        "repository" -> "gh-project",
        "groupId" -> "gh.groupid",
        "artifactId" -> "test-file",
        "packaging" -> "png",
        "version" -> "9.0.0"
      )
      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm404, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      val resp1: IO[String] = client.expect[String](request)
      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.NotFound)
      assert(ex.message == "Version 9.0.0 not found, latest is 1.0.10 updated on 2024-02-05T02:18:05Z[UTC]")
    }

    "cause errors when Maven package 404" in {
      val gitHubResponses = Map(
        uploadPackageMetadataUri -> Response(status = Status.NotFound)
      )
      val httpApp = UploadRequestHelper.httpApp(gitHubResponses).unsafeRunSync()

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      val resp1: IO[String] = client.expect[String](request)
      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.NotFound)
      assert(ex.message == "404 Could not fetch GitHub maven: https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/maven-metadata.xml")
    }

  }

  "Snapshot 1.0.1" - {

    val uploadFilename = "test-file-0.1.0-SNAPSHOT.png"
    val mavenFilename = "test-file-0.1.0-20230330.234307-29.png"
    val uploadFile = new File(s"/test-file/0.1.0-SNAPSHOT/$mavenFilename")
    val uploadFileMD5uri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/0.1.0-SNAPSHOT/$mavenFilename.md5")
    val uploadFileMD5File = new File(s"/test-file/0.1.0-SNAPSHOT/$mavenFilename.md5")

    val uploadPackageMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/maven-metadata.xml")
    val uploadPackageMetadataFile = new File("/maven/maven-metadata_withsnapshot.xml")

    val uploadPackageSnapshotMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/0.1.0-SNAPSHOT/maven-metadata.xml")
    val uploadPackageSnapshotMetadataFile = new File("/maven/0.1.0-SNAPSHOT/maven-metadata.xml")
    
    val uploadFileForm = Map(
      "authToken" -> "",
      "user" -> "gh-user",
      "repository" -> "gh-project",
      "groupId" -> "gh.groupid",
      "artifactId" -> "test-file",
      "packaging" -> "png",
      "version" -> "0.1.0-SNAPSHOT"
    )
    
    "save upload if file does not exist, return BadRequest if it already exists" in {
      val gitHubResponses = Map(
        uploadPackageMetadataUri -> UploadRequestHelper.successResponse(uploadPackageMetadataFile),
        uploadPackageSnapshotMetadataUri -> UploadRequestHelper.successResponse(uploadPackageSnapshotMetadataFile),
        uploadFileMD5uri -> UploadRequestHelper.successResponse(uploadFileMD5File)
      )
      val httpApp = UploadRequestHelper.httpApp(gitHubResponses).unsafeRunSync()

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      val resp1: IO[String] = client.expect[String](request)
      assert(resp1.unsafeRunSync() == "Successfully saved upload of test-file-0.1.0-20230330.234307-29.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3")

      val resp2: IO[String] = client.expect[String](request)
      val ex = intercept[ResponseException](resp2.unsafeRunSync())
      assert(ex.status == Status.Conflict)
      assert(ex.message == "test-file-0.1.0-20230330.234307-29.png already exists")
    }

    "throw error if no github form data included in request" in {
      val httpApp = UploadRequestHelper.httpApp(Map.empty).unsafeRunSync()

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, Map.empty, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      val resp1: IO[String] = client.expect[String](request)
      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.BadRequest)
      assert(ex.message == FileUploadFormData.FormErrorMessage)
    }

  }

  //  "PostActions" should {
  //    "change response in postHook success" in {
  //      val artifactUpload = ArtifactUploadRoute(
  //        httpExt,
  //        Files.createTempDirectory("http-maven-receiver-specs-"),
  //        au => new DefaultRequestHooks(au.directory, logger) {
  //          override def postHook(httpResponse: HttpResponse,file: File): Future[HttpResponse] = {
  //            Future.successful(HttpResponse(StatusCodes.Conflict, entity = HttpEntity("custom-error")))
  //          }
  //        },
  //        1000000L,
  //        Seq()
  //      )
  //      val uploadRequest = UploadRequestHelper.gitHubPackagePutRequest(validFile, githubPackage)
  //
  //      uploadRequest ~> artifactUpload.releasesPutRoute ~> check {
  //        status shouldEqual StatusCodes.Conflict
  //        responseAs[String] shouldEqual "custom-error"
  //      }
  //    }
  //
  //    "change response in postHook failure" in {
  //      val artifactUpload = ArtifactUploadRoute(
  //        httpExt,
  //        Files.createTempDirectory("http-maven-receiver-specs-"),
  //        au => new DefaultRequestHooks(au.directory, logger) {
  //          override def postHook(httpResponse: HttpResponse, file: File): Future[HttpResponse] = {
  //            Future.failed(UserMessageException(StatusCodes.Conflict, "custom-error"))
  //          }
  //        },
  //        1000000L,
  //        Seq()
  //      )
  //      val uploadRequest = UploadRequestHelper.gitHubPackagePutRequest(validFile, githubPackage)
  //
  //      uploadRequest ~> artifactUpload.releasesPutRoute ~> check {
  //        status shouldEqual StatusCodes.Conflict
  //        responseAs[String] shouldEqual "custom-error"
  //      }
  //    }
  //
  //  }
  //
  //  "Unhandled exceptions" should {
  //    val httpExt = UploadRequestHelper.createHttpExtMock(uri, HttpResponse(entity = HttpEntity("5c55838e6a9fb7bb5470cb222fd3b1f3")))
  //
  //    "be returned as generic 500 (1)" in {
  //      val artifactUpload = ArtifactUploadRoute(
  //        httpExt,
  //        Files.createTempDirectory("http-maven-receiver-specs-"),
  //        au => new DefaultRequestHooks(au.directory, logger) {
  //          override def preHook(formFields: Map[String, String],
  //                               fileInfo: FileInfo,
  //                               fileSource: Source[ByteString, Any],
  //                               requestContext: RequestContext): Future[(GitHubPackage, FileInfo, Source[ByteString, Any])] =
  //            Future.failed(new Exception("sensitive information and stacktrace"))
  //        },
  //        1000000L,
  //        Seq()
  //      )
  //      val uploadRequest = UploadRequestHelper.gitHubPackagePutRequest(validFile, githubPackage)
  //
  //      uploadRequest ~> artifactUpload.releasesPutRoute ~> check {
  //        status shouldEqual StatusCodes.InternalServerError
  //        responseAs[String] shouldEqual "There was an internal server error."
  //      }
  //    }
  //    "be returned as generic 500 (2)" in {
  //      val artifactUpload = ArtifactUploadRoute(
  //        httpExt,
  //        Files.createTempDirectory("http-maven-receiver-specs-"),
  //        au => new DefaultRequestHooks(au.directory, logger) {
  //          override def tmpFileHook(tmp: File, md5Sum: String): Future[File] = Future.failed(new Exception("sensitive information and stacktrace"))
  //        },
  //        1000000L,
  //        Seq()
  //      )
  //      val uploadRequest = UploadRequestHelper.gitHubPackagePutRequest(validFile, githubPackage)
  //
  //      uploadRequest ~> artifactUpload.releasesPutRoute ~> check {
  //        status shouldEqual StatusCodes.InternalServerError
  //        responseAs[String] shouldEqual "There was an internal server error."
  //      }
  //    }
  //    "be returned as generic 500 (3)" in {
  //      val artifactUpload = ArtifactUploadRoute(
  //        httpExt,
  //        Files.createTempDirectory("http-maven-receiver-specs-"),
  //        au => new DefaultRequestHooks(au.directory, logger) {
  //          override def postHook(httpResponse: HttpResponse, file: File): Future[HttpResponse] = {
  //            Future.failed(new Exception("sensitive information and stacktrace"))
  //          }
  //        },
  //        1000000L,
  //        Seq()
  //      )
  //      val uploadRequest = UploadRequestHelper.gitHubPackagePutRequest(validFile, githubPackage)
  //
  //      uploadRequest ~> artifactUpload.releasesPutRoute ~> check {
  //        status shouldEqual StatusCodes.InternalServerError
  //        responseAs[String] shouldEqual "There was an internal server error."
  //      }
  //    }
}