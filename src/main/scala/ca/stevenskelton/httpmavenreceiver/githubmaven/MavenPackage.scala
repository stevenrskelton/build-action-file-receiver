package ca.stevenskelton.httpmavenreceiver.githubmaven

import java.time.ZonedDateTime

private case class MavenPackage(
                                 user: String,
                                 repository: String,
                                 groupId: String,
                                 artifactId: String,
                                 packaging: String,
                                 version: String,
                                 updated: ZonedDateTime
                               ) {
  val filename: String = s"$artifactId-$version.$packaging"
  val gitHubMavenPath: String = s"https://maven.pkg.github.com/$user/$repository/${groupId.replace(".", "/")}/$artifactId/$version"
  val artifactUrl: String = s"$gitHubMavenPath/$filename"
}
