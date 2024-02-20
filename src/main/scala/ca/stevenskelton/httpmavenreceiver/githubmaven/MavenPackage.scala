package ca.stevenskelton.httpmavenreceiver.githubmaven

import ca.stevenskelton.httpmavenreceiver.FileUploadFormData
import org.http4s.{ParseResult, Uri}

import java.time.ZonedDateTime

case class MavenPackage(
                         user: String,
                         repository: String,
                         groupId: String,
                         artifactId: String,
                         packaging: String,
                         version: String,
                         snapshotTimeIncrement: Option[String],
                         updated: Option[ZonedDateTime],
                       ):

  val filename: String = s"$artifactId-${snapshotTimeIncrement.getOrElse(version)}.$packaging"

  //  val gitHubMavenArtifactPath: Uri = MavenPackage.gitHubMavenArtifactPath(
  //    user = user,
  //    repository = repository,
  //    groupId = groupId,
  //    artifactId = artifactId,
  //  )

  val gitHubMavenArtifactPath: Uri = Uri.unsafeFromString(s"https://maven.pkg.github.com/$user/$repository/${groupId.replace(".", "/")}/$artifactId")


object MavenPackage:

  def unverified(fileUploadFormData: FileUploadFormData): MavenPackage =
    MavenPackage(
      user = fileUploadFormData.user,
      repository = fileUploadFormData.repository,
      groupId = fileUploadFormData.groupId,
      artifactId = fileUploadFormData.artifactId,
      packaging = fileUploadFormData.packaging,
      version = fileUploadFormData.version,
      snapshotTimeIncrement = None,
      updated = None,
    )

  def gitHubMavenArtifactPath(fileUploadFormData: FileUploadFormData): ParseResult[Uri] =
    Uri.fromString(s"https://maven.pkg.github.com/${fileUploadFormData.user}/${fileUploadFormData.repository}/${fileUploadFormData.groupId.replace(".", "/")}/${fileUploadFormData.artifactId}")

end MavenPackage
