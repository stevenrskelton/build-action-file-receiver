package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.Main.ExitException
import cats.effect.ExitCode
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.File

class MainArgsSpec extends AsyncFreeSpec with Matchers with AsyncIOSpec {

  val jarDirectory = new File("src").getAbsoluteFile
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

      val mainArgs = MainArgs.parse(List.empty, jarDirectory).unsafeRunSync()
      assert(mainArgs.postUploadAction.isEmpty)
    }
    "absolute exists" in {
      given logger: RecordingLogger = RecordingLogger()

      val mainArgs = MainArgs.parse(List("--exec=/bin/ls"), jarDirectory).unsafeRunSync()
      assert(mainArgs.postUploadAction.contains(PostUploadAction("/bin/ls", jarDirectory)))
    }
    "absolute does not exist" in {
      given logger: RecordingLogger = RecordingLogger()

      val ex = intercept[ExitException](MainArgs.parse(List("--exec=/bin/lsss"), jarDirectory).unsafeRunSync())
      assert(ex.code == ExitCode.Error)
      assert(ex.msg.startsWith("Exec /bin/lsss does not exist in working directory /"))
    }
    "relative exists without leading /" in {
      given logger: RecordingLogger = RecordingLogger()

      val mainArgs = MainArgs.parse(List("--exec=test/resources/postuploadactions/echoenv.sh"), jarDirectory).unsafeRunSync()
      assert(mainArgs.postUploadAction.contains(PostUploadAction("test/resources/postuploadactions/echoenv.sh", jarDirectory)))
      assert(logger.lines(0) == "Post upload command: test/resources/postuploadactions/echoenv.sh")
    }
    "relative exists with leading ./" in {
      given logger: RecordingLogger = RecordingLogger()

      val mainArgs = MainArgs.parse(List("--exec=./test/resources/postuploadactions/echoenv.sh"), jarDirectory).unsafeRunSync()
      assert(mainArgs.postUploadAction.contains(PostUploadAction("./test/resources/postuploadactions/echoenv.sh", jarDirectory)))
      assert(logger.lines(0) == "Post upload command: ./test/resources/postuploadactions/echoenv.sh")
    }
    "relative exists with leading ../" in {
      given logger: RecordingLogger = RecordingLogger()

      val mainArgs = MainArgs.parse(List("--exec=../src/test/resources/postuploadactions/echoenv.sh"), jarDirectory).unsafeRunSync()
      assert(mainArgs.postUploadAction.contains(PostUploadAction("../src/test/resources/postuploadactions/echoenv.sh", jarDirectory)))
      assert(logger.lines(0) == "Post upload command: ../src/test/resources/postuploadactions/echoenv.sh")
    }
    "relative does not exist without leading /" in {
      given logger: RecordingLogger = RecordingLogger()

      val ex = intercept[ExitException](MainArgs.parse(List("--exec=echoenv.sh"), jarDirectory).unsafeRunSync())
      assert(ex.code == ExitCode.Error)
      assert(ex.msg.startsWith("Exec echoenv.sh does not exist in working directory /"))
    }
    "relative does not exist with leading ./" in {
      given logger: RecordingLogger = RecordingLogger()

      val ex = intercept[ExitException](MainArgs.parse(List("--exec=./echoenv.sh"), jarDirectory).unsafeRunSync())
      assert(ex.code == ExitCode.Error)
      assert(ex.msg.startsWith("Exec ./echoenv.sh does not exist in working directory /"))
    }
    "not executable" in {
      given logger: RecordingLogger = RecordingLogger()

      val ex = intercept[ExitException](MainArgs.parse(List("--exec=../build.sbt"), jarDirectory).unsafeRunSync())
      assert(ex.code == ExitCode.Error)
      assert(ex.msg == "Exec ../build.sbt not executable.")
    }
  }

}
