package ca.stevenskelton.httpmavenreceiver.githubmaven

import ca.stevenskelton.httpmavenreceiver.FileUploadFormData
import org.http4s.{ParseResult, Uri}

import java.time.{ZoneId, ZonedDateTime}

private case class MavenPackage(
                                 user: String,
                                 repository: String,
                                 groupId: String,
                                 artifactId: String,
                                 packaging: String,
                                 version: String,
                                 filename: String,
                                 updated: ZonedDateTime
                               ) {

  val gitHubMavenArtifactPath: Uri = {
    Uri.unsafeFromString(s"https://maven.pkg.github.com/$user/$repository/${groupId.replace(".", "/")}/$artifactId")
  }
}

object MavenPackage {

  def unverified(fileUploadFormData: FileUploadFormData): MavenPackage = {
    MavenPackage(
      user = fileUploadFormData.user,
      repository = fileUploadFormData.repository,
      groupId = fileUploadFormData.groupId,
      artifactId = fileUploadFormData.artifactId,
      packaging = fileUploadFormData.packaging,
      version = fileUploadFormData.version,
      filename = s"${fileUploadFormData.artifactId}-${fileUploadFormData.version}.${fileUploadFormData.packaging}",
      updated = ZonedDateTime.now(ZoneId.of("UTC"))
    )
  }

  def gitHubMavenArtifactPath(fileUploadFormData: FileUploadFormData): ParseResult[Uri] = {
    Uri.fromString(s"https://maven.pkg.github.com/${fileUploadFormData.user}/${fileUploadFormData.repository}/${fileUploadFormData.groupId.replace(".", "/")}/${fileUploadFormData.artifactId}")
  }
  
}