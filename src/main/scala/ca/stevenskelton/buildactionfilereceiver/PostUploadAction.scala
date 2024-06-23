package ca.stevenskelton.buildactionfilereceiver

import ca.stevenskelton.buildactionfilereceiver.githubmaven.MavenPackage
import cats.effect.{ExitCode, IO}
import fs2.io.file.Path
import org.http4s.Status.InternalServerError
import org.typelevel.log4cats.Logger

import scala.sys.process.ProcessLogger

case class PostUploadAction(command: String, jarDirectory: Path):

  val commandPath: Path =
    val cmdPath = Path(command)
    if cmdPath.isAbsolute then cmdPath
    else Path(s"${jarDirectory.absolute.toString}/$command")

  def run(destinationFile: Path, mavenPackage: MavenPackage)(using logger: Logger[IO]): IO[ExitCode] =
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

    for
      _ <- logger.info(s"Starting post upload action for ${destinationFile.fileName}")
      processLogger <- IO.pure(ProcessLogger(logger.info(_).unsafeRunSync()(cats.effect.unsafe.implicits.global)))
      processExitCode <- IO.blocking(sys.process.Process(Seq(commandPath.toString), destinationFile.toNioPath.toFile.getAbsoluteFile.getParentFile, env *).!(processLogger))
      actionExitCode <- processExitCode match
        case 0 => logger.info(s"Completed post upload action for ${destinationFile.fileName}").as(ExitCode.Success)
        case _ =>
          val ex = ResponseException(InternalServerError, s"Failed post upload action for ${destinationFile.fileName}")
          logger.error(ex)(ex.getMessage) *> IO.raiseError(ex)
    yield actionExitCode