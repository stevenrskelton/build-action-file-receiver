package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.Main.ExitException
import cats.effect.ExitCode
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class MainArgsSpec extends AsyncFreeSpec with Matchers with AsyncIOSpec {

  //  --help
  //  --disable-maven
  //  --allow-all-versions
  //  --host=[STRING]
  //  --port=[INTEGER]
  //  --max-upload-size=[STRING]
  //  --exec=[STRING]
  //  --upload-directory=[STRING]
  "exec" - {
    "empty" in {
      given logger: RecordingLogger = RecordingLogger()

      val mainArgs = MainArgs.parse(List.empty).unsafeRunSync()
      assert(mainArgs.postUploadAction.isEmpty)
    }
    "absolute exists" in {
      given logger: RecordingLogger = RecordingLogger()

      val mainArgs = MainArgs.parse(List("--exec=/bin/ls")).unsafeRunSync()
      assert(mainArgs.postUploadAction.contains(PostUploadAction("/bin/ls")))
    }
    "absolute does not exist" in {
      given logger: RecordingLogger = RecordingLogger()

      val ex = intercept[ExitException](MainArgs.parse(List("--exec=/bin/lsss")).unsafeRunSync())
      assert(ex.code == ExitCode.Error)
      assert(ex.msg.startsWith("Exec /bin/lsss does not exist with working directory /"))
    }
    "relative exists" in {
      given logger: RecordingLogger = RecordingLogger()

      val mainArgs = MainArgs.parse(List("--exec=src/test/resources/postuploadactions/echoenv.sh")).unsafeRunSync()
      assert(mainArgs.postUploadAction.contains(PostUploadAction("src/test/resources/postuploadactions/echoenv.sh")))
      assert(logger.lines(0) == "Post upload command: src/test/resources/postuploadactions/echoenv.sh")
    }
    "relative exists with leading ./" in {
      given logger: RecordingLogger = RecordingLogger()

      val mainArgs = MainArgs.parse(List("--exec=./src/test/resources/postuploadactions/echoenv.sh")).unsafeRunSync()
      assert(mainArgs.postUploadAction.contains(PostUploadAction("./src/test/resources/postuploadactions/echoenv.sh")))
      assert(logger.lines(0) == "Post upload command: ./src/test/resources/postuploadactions/echoenv.sh")
    }
    "relative does not exist" in {
      given logger: RecordingLogger = RecordingLogger()

      val ex = intercept[ExitException](MainArgs.parse(List("--exec=./echoenv.sh")).unsafeRunSync())
      assert(ex.code == ExitCode.Error)
      assert(ex.msg.startsWith("Exec ./echoenv.sh does not exist with working directory /"))
    }
    "not executable" in {
      given logger: RecordingLogger = RecordingLogger()

      val ex = intercept[ExitException](MainArgs.parse(List("--exec=build.sbt")).unsafeRunSync())
      assert(ex.code == ExitCode.Error)
      assert(ex.msg == "Exec build.sbt not executable.")
    }
  }

}
