package ca.stevenskelton.httpmavenreceiver.githubmaven

import java.time.ZonedDateTime

private case class MavenPackage(
                                 githubUser: String,
                                 githubRepository: String,
                                 groupId: String,
                                 artifactId: String,
                                 version: String,
                                 updated: ZonedDateTime
                               ) {
  val jarFilename: String = s"$artifactId-$version.jar"
  val gitHubMavenPath: String = s"https://maven.pkg.github.com/$githubUser/$githubRepository/${groupId.replace(".", "/")}/$artifactId/$version"
  val artifactUrl: String = s"$gitHubMavenPath/$jarFilename"
}
