package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.githubmaven.MavenPackage
import cats.effect.IO
import fs2.io.file.Path
import org.typelevel.log4cats.Logger

import scala.sys.process.ProcessLogger

case class PostUploadAction(command: String) {
  def run(destinationFile: Path, mavenPackage: MavenPackage)(using logger: Logger[IO]): IO[_] = {
    logger.info(s"Starting post upload action for ${destinationFile.fileName}")
    val file = destinationFile.toNioPath.toFile
    val env = Seq(
      "HMV_USER" -> mavenPackage.user,
      "HMV_REPOSITORY" -> mavenPackage.repository,
      "HMV_GROUPID" -> mavenPackage.groupId,
      "HMV_ARTIFACTID" -> mavenPackage.artifactId,
      "HMV_PACKAGING" -> mavenPackage.packaging,
      "HMV_VERSION" -> mavenPackage.version,
      "HMV_FILENAME" -> file.getName
    )
    val processLogger = ProcessLogger(logger.info(_))
    IO.pure(sys.process.Process(Seq(command), file.getParentFile.getAbsoluteFile, env: _*).!(processLogger)).flatMap {
      case 0 => logger.info(s"Completed post upload action for ${destinationFile.fileName}")
      case _ =>
        val ex = Exception(s"Failed post upload action for ${destinationFile.fileName}")
        logger.error(ex)(ex.getMessage)
    }
  }

  //  private def exec(command: String)(implicit logger: Logger): Unit = {
  //    val result = sys.process.Process(command).!
  //    if (result != 0) {
  //      val message = s"Could not execute post command `$command`, returned $result"
  //      logger.error(message)
  //      throw new Exception(message)
  //    }
  //  }
  //
  //  override def postHook(file: File)(implicit logger: Logger): Future[Done] = {
  //    if (file.getName.startsWith("tradeauditserver-assembly-")) {
  //      exec(s"sudo -- mv ${file.getAbsolutePath} /home/tradeaudit/")
  //      exec(s"sudo -- rm /home/tradeaudit/tradeauditserver-assembly.jar")
  //      exec(s"sudo -- ln -s /home/tradeaudit/${file.getName} /home/tradeaudit/tradeauditserver-assembly.jar")
  //      exec(s"sudo -- systemctl restart tradeaudit")
  //      logger.info(s"Successfully installed new tradeaudit version ${file.getName}")
  //    }
  //    Future.successful(Done)
  //  }
}
