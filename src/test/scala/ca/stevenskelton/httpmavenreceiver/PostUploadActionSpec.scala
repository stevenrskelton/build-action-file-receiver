package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.githubmaven.MavenPackage
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.Stream
import fs2.io.file.Path
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.File

class PostUploadActionSpec extends AsyncFreeSpec with Matchers with AsyncIOSpec /*extends AnyWordSpec */ {

  private val destinationFile = Path("src/test/resources/postuploadactions/destinationfile.jar")
  private val mavenPackage = new MavenPackage(
    user = "gh-user",
    repository = "gh-project",
    groupId = "gh.groupid",
    artifactId = "test-file",
    packaging = "extension",
    version = "1.0.1",
    snapshot = None,
    updated = None,
  )

  "run" - {
    "populate environmental variables" in {
      val logger = new RecordingLogger
      val postUploadAction = PostUploadAction("./echoenv.sh")
      postUploadAction.run(destinationFile, mavenPackage, logger).unsafeRunSync()
      val log = logger.lines
      assert(log.length == 10)
      assert(log(0) == "Starting post upload action for destinationfile.jar")
      assert(log(1) == new File("").getAbsolutePath + "/src/test/resources/postuploadactions")
      assert(log(2) == mavenPackage.user)
      assert(log(3) == mavenPackage.repository)
      assert(log(4) == mavenPackage.groupId)
      assert(log(5) == mavenPackage.artifactId)
      assert(log(6) == mavenPackage.packaging)
      assert(log(7) == mavenPackage.version)
      assert(log(8) == "destinationfile.jar")
      assert(log(9) == "Completed post upload action for destinationfile.jar")
    }

    "handle error" in {
      val logger = new RecordingLogger
      val postUploadAction = PostUploadAction("./error.sh")
      postUploadAction.run(destinationFile, mavenPackage, logger).unsafeRunSync()
      val log = logger.lines
      assert(log.length == 3)
      assert(log(0) == "Starting post upload action for destinationfile.jar")
      assert(log(1) == "./error.sh: line 1: cd: hi: No such file or directory")
      assert(log(2) == "java.lang.Exception: Failed post upload action for destinationfile.jar")
    }
  }

}
