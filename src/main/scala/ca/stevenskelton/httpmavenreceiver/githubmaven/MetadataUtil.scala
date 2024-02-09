package ca.stevenskelton.httpmavenreceiver.githubmaven

import ca.stevenskelton.httpmavenreceiver.{AuthToken, FileUploadFormData, ResponseException}
import cats.effect.kernel.Resource
import cats.effect.{IO, Resource}
import org.http4s.*
import org.http4s.client.Client
import org.typelevel.log4cats.LoggerFactory

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import scala.xml.{Elem, XML}

object MetadataUtil {

  private def fetchXML(uri: Uri, authToken: AuthToken)(implicit httpClient: Resource[IO, Client[IO]], loggerFactory: LoggerFactory[IO]): IO[Elem] = {
    httpClient.use {
      client =>
        val request = Request[IO](
          Method.GET,
          uri,
          headers = Headers(Header.ToRaw.keyValuesToRaw("Authorization" -> s"token ${authToken.value}")),
        )
        client.expectOr[String](request) {
            errorResponse =>
              val msg = s"${errorResponse.status.code} Could not fetch GitHub maven: $uri"
              IO.raiseError(ResponseException(errorResponse.status, msg))
          }
          .map(XML.loadString)
    }
  }

  def fetchMetadata(fileUploadFormData: FileUploadFormData, allowAllVersions: Boolean)(implicit httpClient: Resource[IO, Client[IO]], loggerFactory: LoggerFactory[IO]): IO[MavenPackage] = {
    MavenPackage.gitHubMavenArtifactPath(fileUploadFormData).map {
      gitHubMavenArtifactPath =>
        fetchXML(gitHubMavenArtifactPath / "maven-metadata.xml", fileUploadFormData.authToken)
          .map { xml =>
            if (allowAllVersions) {
              parseAllVersionMetadata(fileUploadFormData, xml).getOrElse {
                parseLatestVersionMetadata(fileUploadFormData, xml)
              }
            } else {
              parseLatestVersionMetadata(fileUploadFormData, xml)
            }
          }
          .flatMap {
            mavenPackage =>
              if (fileUploadFormData.version != mavenPackage.version) {
                val msg = s"Version ${fileUploadFormData.version} requested. Latest is ${mavenPackage.version}${mavenPackage.updated.fold("")(z => s" updated on ${z.toString}")}"
                IO.raiseError(ResponseException(Status.Conflict, msg))
              } else if (fileUploadFormData.version.endsWith("-SNAPSHOT")) {
                fetchXML(gitHubMavenArtifactPath / fileUploadFormData.version / "maven-metadata.xml", fileUploadFormData.authToken)
                  .map(parseLatestSnapshotVersionMetadata(fileUploadFormData, _))
              } else {
                IO.pure(mavenPackage)
              }
          }
    }.getOrElse {
      val msg = s"Invalid package ${fileUploadFormData.user} | ${fileUploadFormData.repository} | ${fileUploadFormData.groupId} | ${fileUploadFormData.artifactId}"
      IO.raiseError(ResponseException(Status.BadGateway, msg))
    }
  }

  private def lastUpdated(xmlText: String): ZonedDateTime = {
    LocalDateTime.parse(xmlText, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).atZone(ZoneId.of("UTC"))
  }

  private def parseLatestVersionMetadata(fileUploadFormData: FileUploadFormData, metadata: Elem): MavenPackage = {
    MavenPackage(
      user = fileUploadFormData.user,
      repository = fileUploadFormData.repository,
      groupId = fileUploadFormData.groupId,
      artifactId = fileUploadFormData.artifactId,
      packaging = fileUploadFormData.packaging,
      version = (metadata \ "versioning" \ "latest").text,
      snapshot = None,
      updated = Some(lastUpdated((metadata \ "versioning" \ "lastUpdated").text)),
    )
  }

  private def parseAllVersionMetadata(fileUploadFormData: FileUploadFormData, metadata: Elem): Option[MavenPackage] = {
    (metadata \ "versioning" \ "versions" \ "version")
      .find(_.text == fileUploadFormData.version)
      .map {
        version =>
          MavenPackage(
            user = fileUploadFormData.user,
            repository = fileUploadFormData.repository,
            groupId = fileUploadFormData.groupId,
            artifactId = fileUploadFormData.artifactId,
            packaging = fileUploadFormData.packaging,
            version = fileUploadFormData.version,
            snapshot = None,
            updated = None,
          )
      }
  }

  private def parseLatestSnapshotVersionMetadata(fileUploadFormData: FileUploadFormData, metadata: Elem): MavenPackage = {
    val node = (metadata \ "versioning" \ "snapshot").head
    val versionString = s"${(node \ "timestamp").text}-${(node \ "buildNumber").text}"
    val snapshotVersion = (metadata \ "version").text.replaceFirst("-SNAPSHOT", s"-$versionString")
    MavenPackage(
      user = fileUploadFormData.user,
      repository = fileUploadFormData.repository,
      groupId = fileUploadFormData.groupId,
      artifactId = fileUploadFormData.artifactId,
      packaging = fileUploadFormData.packaging,
      version = fileUploadFormData.version,
      snapshot = Some(snapshotVersion),
      updated = Some(lastUpdated((metadata \ "versioning" \ "lastUpdated").text)),
    )
  }

  //    private def parseAllSnapshotMetadata(fileUploadFormData: FileUploadFormData, metadata: Elem): MavenPackage = {
  //      (metadata \\ "snapshotVersion").withFilter {
  //        node => (node \ "extension").text == fileUploadFormData.packaging
  //      }.flatMap {
  //        n =>
  //          for {
  //            value <- n \ "value"
  //            updated <- n \ "updated"
  //          } yield {
  //            val updatedTime = LocalDateTime.parse(updated.text, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
  //            MavenPackage(
  //              user = fileUploadFormData.user,
  //              repository = fileUploadFormData.repository,
  //              groupId = fileUploadFormData.groupId,
  //              artifactId = fileUploadFormData.artifactId,
  //              packaging = fileUploadFormData.packaging,
  //              version = value.text,
  //              updated = updatedTime.atZone(ZoneId.of("UTC"))
  //            )
  //          }
  //      }.sortBy(_.updated).reverse
  //    }
}