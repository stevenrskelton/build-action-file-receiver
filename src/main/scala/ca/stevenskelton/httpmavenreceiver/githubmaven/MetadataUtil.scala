package ca.stevenskelton.httpmavenreceiver.githubmaven

import ca.stevenskelton.httpmavenreceiver.{FileUploadFormData, ResponseException}
import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.*
import org.http4s.client.Client

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import scala.xml.{Elem, XML}

object MetadataUtil {

  def fetchMetadata(httpClient: Resource[IO, Client[IO]], fileUploadFormData: FileUploadFormData): IO[MavenPackage] = {
    val gitHubMetadataUri = Uri.fromString(s"${fileUploadFormData.gitHubMavenPath}/maven-metadata.xml").getOrElse {
      val msg = s"Invalid package ${fileUploadFormData.gitHubMavenPath}"
      return IO.raiseError(ResponseException(Status.BadGateway, msg))
    }
    httpClient.use {
      client =>
        val request = Request[IO](
          Method.GET,
          gitHubMetadataUri,
          headers = Headers(Header.ToRaw.keyValuesToRaw("Authorization" -> s"token ${fileUploadFormData.githubAuthToken}")),
        )
        client.expectOr[String](request) {
          errorResponse =>
            val msg = s"${errorResponse.status.code} Could not fetch GitHub maven: $gitHubMetadataUri"
            IO.raiseError(ResponseException(errorResponse.status, msg))
        }.map {
          xmlString =>
            val xml = XML.loadString(xmlString)
            val all = parseMavenMetadata(fileUploadFormData, xml)
            all.find {
              mavenPackage => mavenPackage.version == fileUploadFormData.version
            }.getOrElse {
              all.headOption.map {
                latest =>
                  //TODO: should this be sorted by `updated`?
                  val msg = s"Version ${fileUploadFormData.version} not found, latest is ${latest.version} updated on ${latest.updated.toString}"
                  throw ResponseException(Status.BadGateway, msg)
              }.getOrElse {
                throw ResponseException(Status.BadGateway, "No release found.")
              }
            }
        }
    }
  }

  private def parseMavenMetadata(fileUploadFormData: FileUploadFormData, metadata: Elem): Seq[MavenPackage] = {
    (metadata \\ "snapshotVersion").withFilter {
      n => (n \ "extension").text == "jar"
    }.flatMap {
      n =>
        for {
          value <- n \ "value"
          updated <- n \ "updated"
        } yield {
          val updatedTime = LocalDateTime.parse(updated.text, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
          MavenPackage(
            githubUser = fileUploadFormData.githubUser,
            githubRepository = fileUploadFormData.githubRepository,
            groupId = fileUploadFormData.groupId,
            artifactId = fileUploadFormData.artifactId,
            version = value.text,
            updated = updatedTime.atZone(ZoneId.of("UTC"))
          )
        }
    }.sortBy(_.updated).reverse
  }
}
