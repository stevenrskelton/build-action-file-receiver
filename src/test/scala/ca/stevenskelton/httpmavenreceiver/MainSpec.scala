package ca.stevenskelton.httpmavenreceiver

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.io.file.Path
import org.http4s.*
import org.http4s.client.Client
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class MainSpec extends AsyncFreeSpec with Matchers with AsyncIOSpec {

  private val requestUri = Uri.unsafeFromString("http://localhost/releases")

  private val fileForm = Map(
    "authToken" -> "",
    "user" -> "gh-user",
    "repository" -> "gh-project",
    "groupId" -> "gh.groupid",
    "artifactId" -> "test-file",
    "packaging" -> "png",
  )

  "VERSION 1.0.10" - {
    val uploadFile = Path(s"/test-file/1.0.10/test-file-1.0.10.png")

    val mavenFilename = "test-file-1.0.10.png"
    val uploadFileMD5uri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/1.0.10/$mavenFilename.md5")
    val uploadFileMD5File = Path(s"/test-file/1.0.10/$mavenFilename.md5")

    val uploadPackageMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/maven-metadata.xml")
    val uploadPackageMetadataFile = Path("/maven/maven-metadata.xml")
    val uploadFileForm = fileForm + ("version" -> "1.0.10")

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

      val resp1: IO[String] = Client.fromHttpApp(httpApp).expect[String](
        UploadRequestHelper.multipartFilePutRequest(uploadFile, Map.empty, requestUri)
      )

      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.BadRequest)
      assert(ex.message == FileUploadFormData.FormErrorMessage)
    }

    "cause error when upload md5 sum doesn't match" in {
      val httpApp = UploadRequestHelper.httpApp(Map(
        uploadPackageMetadataUri -> UploadRequestHelper.successResponse(uploadPackageMetadataFile),
        uploadFileMD5uri -> Response(entity = Entity.utf8String("36a9ba7d32ad98d518f67bd6b1787233"))
      )).unsafeRunSync()

      val resp1: IO[String] = Client.fromHttpApp(httpApp).expect[String](
        UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm, requestUri)
      )

      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.Conflict)
      assert(ex.message == "Upload test-file-1.0.10.png MD5 not equal, 36a9ba7d32ad98d518f67bd6b1787233 expected != 5c55838e6a9fb7bb5470cb222fd3b1f3 of upload.")
    }

    "cause errors when Maven package 404" in {
      val httpApp = UploadRequestHelper.httpApp(Map(
        uploadPackageMetadataUri -> Response(status = Status.NotFound)
      )).unsafeRunSync()

      val resp1: IO[String] = Client.fromHttpApp(httpApp).expect[String](
        UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm, requestUri)
      )

      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.NotFound)
      assert(ex.message == "404 Could not fetch GitHub maven: https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/maven-metadata.xml")
    }

  }

  "SNAPSHOT 0.1.0" - {
    val uploadFile = Path(s"/test-file/0.1.0-SNAPSHOT/test-file-0.1.0-SNAPSHOT.png")

    val mavenFilename = "test-file-0.1.0-20230330.234307-29.png"
    val uploadFileMD5uri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/0.1.0-SNAPSHOT/$mavenFilename.md5")
    val uploadFileMD5File = Path(s"/test-file/0.1.0-SNAPSHOT/$mavenFilename.md5")

    val uploadPackageMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/maven-metadata.xml")
    val uploadPackageMetadataFile = Path("/maven/maven-metadata_withsnapshot.xml")

    val uploadPackageSnapshotMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/0.1.0-SNAPSHOT/maven-metadata.xml")
    val uploadPackageSnapshotMetadataFile = Path("/maven/0.1.0-SNAPSHOT/maven-metadata.xml")

    val uploadFileForm = fileForm + ("version" -> "0.1.0-SNAPSHOT")

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

  }

  "BIN-ASSEMBLY" - {
    "rename based on packaging" in {
      val uploadFile = Path(s"/test-file/1.0.10/test-file-1.0.10.png")

      val mavenFilename = "test-file-assembly-1.0.5.bin"
      val uploadFileMD5uri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file-assembly/1.0.5/$mavenFilename.md5")
      val uploadFileMD5File = Path(s"/test-file/1.0.10/test-file-1.0.10.png.md5")

      val uploadPackageMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file-assembly/maven-metadata.xml")
      val uploadPackageMetadataFile = Path("/maven/maven-metadata.xml")

      val gitHubResponses = Map(
        uploadPackageMetadataUri -> UploadRequestHelper.successResponse(uploadPackageMetadataFile),
        uploadFileMD5uri -> UploadRequestHelper.successResponse(uploadFileMD5File)
      )
      val httpApp = UploadRequestHelper.httpApp(gitHubResponses, allowAllVersions = true).unsafeRunSync()

      val differentUploadFileForm = fileForm ++ Map(
        "artifactId" -> "test-file-assembly",
        "packaging" -> "bin",
        "version" -> "1.0.5",
      )

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, differentUploadFileForm, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      val resp1: IO[String] = client.expect[String](request)
      assert(resp1.unsafeRunSync() == "Successfully saved upload of test-file-assembly-1.0.5.bin, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3")

    }
  }

  "allow-all-versions" - {
    val uploadFile = Path(s"/test-file/0.1.0-SNAPSHOT/test-file-0.1.0-SNAPSHOT.png")

    val mavenFilename = "test-file-0.1.0-20230330.234307-29.png"
    val uploadFileMD5uri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/0.1.0-SNAPSHOT/$mavenFilename.md5")
    val uploadFileMD5File = Path(s"/test-file/0.1.0-SNAPSHOT/$mavenFilename.md5")

    val uploadPackageMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/maven-metadata.xml")
    val uploadPackageMetadataFile = Path("/maven/maven-metadata.xml")

    val uploadPackageSnapshotMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/0.1.0-SNAPSHOT/maven-metadata.xml")
    val uploadPackageSnapshotMetadataFile = Path("/maven/0.1.0-SNAPSHOT/maven-metadata.xml")

    val gitHubResponses = Map(
      uploadPackageMetadataUri -> UploadRequestHelper.successResponse(uploadPackageMetadataFile),
      uploadPackageSnapshotMetadataUri -> UploadRequestHelper.successResponse(uploadPackageSnapshotMetadataFile),
      uploadFileMD5uri -> UploadRequestHelper.successResponse(uploadFileMD5File)
    )

    val uploadFileForm = fileForm + ("version" -> "0.1.0-SNAPSHOT")

    def exec(allowAllVersions: Boolean): IO[String] = {
      val httpApp = UploadRequestHelper.httpApp(gitHubResponses, allowAllVersions = allowAllVersions).unsafeRunSync()

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      client.expect[String](request)
    }

    "fail because newer version exists" in {
      val resp1: IO[String] = exec(allowAllVersions = false)
      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.Conflict)
      assert(ex.message == "Version 0.1.0-SNAPSHOT requested. Latest is 1.0.10 updated on 2024-02-05T02:18:05Z[UTC]")
    }

    "succeed when latest version ignored" in {
      val resp1: IO[String] = exec(allowAllVersions = true)
      assert(resp1.unsafeRunSync() == "Successfully saved upload of test-file-0.1.0-20230330.234307-29.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3")
    }
  }

  "disable-maven" - {
    val uploadFile = Path(s"/test-file/0.1.0-SNAPSHOT/test-file-0.1.0-SNAPSHOT.png")

    val uploadPackageMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/maven-metadata.xml")
    val uploadPackageMetadataFile = Path("/maven/maven-metadata.xml")

    val uploadFileForm404 = fileForm + ("version" -> "9.0.0-SNAPSHOT")

    def exec(isMavenDisabled: Boolean): IO[String] = {
      val gitHubResponses = if (isMavenDisabled) Map.empty else Map(
        uploadPackageMetadataUri -> UploadRequestHelper.successResponse(uploadPackageMetadataFile),
      )

      val httpApp = UploadRequestHelper.httpApp(gitHubResponses, isMavenDisabled = isMavenDisabled).unsafeRunSync()

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm404, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      client.expect[String](request)
    }

    "cause error when version doesn't exist" in {
      val resp1: IO[String] = exec(false)
      val ex = intercept[ResponseException](resp1.unsafeRunSync())
      assert(ex.status == Status.Conflict)
      assert(ex.message == "Version 9.0.0-SNAPSHOT requested. Latest is 1.0.10 updated on 2024-02-05T02:18:05Z[UTC]")
    }

    "succeed when GitHub disabled" in {
      val resp1: IO[String] = exec(true)
      assert(resp1.unsafeRunSync() == "Successfully saved upload of test-file-9.0.0-SNAPSHOT.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3")
    }

  }

  "post upload action" - {
    val uploadFile = Path(s"/test-file/1.0.10/test-file-1.0.10.png")

    val mavenFilename = "test-file-1.0.10.png"
    val uploadFileMD5uri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/1.0.10/$mavenFilename.md5")
    val uploadFileMD5File = Path(s"/test-file/1.0.10/$mavenFilename.md5")

    val uploadPackageMetadataUri = Uri.unsafeFromString(s"https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/maven-metadata.xml")
    val uploadPackageMetadataFile = Path("/maven/maven-metadata.xml")
    val uploadFileForm = fileForm + ("version" -> "1.0.10")

    "exec" in {
      given logger: RecordingLogger = RecordingLogger()

      val gitHubResponses = Map(
        uploadPackageMetadataUri -> UploadRequestHelper.successResponse(uploadPackageMetadataFile),
        uploadFileMD5uri -> UploadRequestHelper.successResponse(uploadFileMD5File)
      )
      val cmd = Path("src/test/resources/postuploadactions/echoenv.sh").absolute
      val workingDirectory = Path("").absolute
      val httpApp = UploadRequestHelper.httpApp(gitHubResponses, postUploadActions = Some(PostUploadAction(cmd.toString, workingDirectory))).unsafeRunSync()

      val request: Request[IO] = UploadRequestHelper.multipartFilePutRequest(uploadFile, uploadFileForm, requestUri)
      val client: Client[IO] = Client.fromHttpApp(httpApp)

      val resp1: IO[String] = client.expect[String](request)
      assert(resp1.unsafeRunSync() == "Successfully saved upload of test-file-1.0.10.png, 7kb, MD5 5c55838e6a9fb7bb5470cb222fd3b1f3")

      val log = logger.lines
      assert(log.length == 16)
      assert(log(0) == "Starting releasesPut handler")
      assert(log(1) == "Received request for file `test-file-1.0.10.png` by GitHub user `gh-user` upload from IP ?")
      assert(log(2) == "Fetching MD5 at https://maven.pkg.github.com/gh-user/gh-project/gh/groupid/test-file/1.0.10/test-file-1.0.10.png.md5")
      assert(log(3) == "Received 7478 bytes for test-file-1.0.10.png")
      assert(log(4) == "MD5 validated 5c55838e6a9fb7bb5470cb222fd3b1f3, saving file at test-file-1.0.10.png")
      assert(log(5) == "Starting post upload action for test-file-1.0.10.png")
      assert(log(6).startsWith("/"))
      assert(log(7) == uploadFileForm("user"))
      assert(log(8) == uploadFileForm("repository"))
      assert(log(9) == uploadFileForm("groupId"))
      assert(log(10) == uploadFileForm("artifactId"))
      assert(log(11) == uploadFileForm("packaging"))
      assert(log(12) == uploadFileForm("version"))
      assert(log(13) == "test-file-1.0.10.png")
      assert(log(14) == "Completed post upload action for test-file-1.0.10.png")
      assert(log(15).startsWith("Completed test-file-1.0.10.png (7kb) in"))
    }
  }

}