package ca.stevenskelton.httpmavenreceiver.githubmaven

import ca.stevenskelton.httpmavenreceiver.FileUploadFormData

import java.time.ZonedDateTime

private case class MavenPackage(
                                 user: String,
                                 repository: String,
                                 groupId: String,
                                 artifactId: String,
                                 packaging: String,
                                 version: String,
                                 updated: ZonedDateTime
                               )

object MavenPackage {

  def gitHubMavenPath(fileUploadFormData: FileUploadFormData): String = {
    gitHubMavenPath(user = fileUploadFormData.user, repository = fileUploadFormData.repository,
      groupId = fileUploadFormData.groupId, artifactId = fileUploadFormData.artifactId,
      version = fileUploadFormData.version)
  }

  def gitHubMavenPath(user: String, repository: String, groupId: String, artifactId: String, version: String): String = {
    s"https://maven.pkg.github.com/$user/$repository/${groupId.replace(".", "/")}/$artifactId/$version"
  }

  def mavenFilename(artifactId: String, packaging: String, version: String): String = {
    s"$artifactId-$version.$packaging"
  }

  def artifactUrl(fileUploadFormData: FileUploadFormData): String = {
    artifactUrl(user = fileUploadFormData.user, repository = fileUploadFormData.repository, groupId = fileUploadFormData.groupId,
      artifactId = fileUploadFormData.artifactId, packaging = fileUploadFormData.packaging, version = fileUploadFormData.version)
  }

  def artifactUrl(user: String, repository: String, groupId: String, artifactId: String, packaging: String, version: String): String = {
    s"${gitHubMavenPath(user, repository, groupId, artifactId, version)}/${mavenFilename(artifactId, packaging, version)}"
  }
}