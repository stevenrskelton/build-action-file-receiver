package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.model.{HttpEntity, Multipart}

case class GithubPackage(
                          githubUser: String,
                          githubRepository: String,
                          groupId: String,
                          artifactId: String,
                          version: String,
                          githubToken: Option[String]
                        ) {
  val path: String = s"https://maven.pkg.github.com/$githubUser/$githubRepository/${groupId.replace(".", "/")}/$artifactId/$version"

  def multipartFormData: Seq[Multipart.FormData.BodyPart.Strict] = Seq(
    Multipart.FormData.BodyPart.Strict.apply("githubUser", HttpEntity(githubUser)),
    Multipart.FormData.BodyPart.Strict.apply("githubRepository", HttpEntity(githubRepository)),
    Multipart.FormData.BodyPart.Strict.apply("groupId", HttpEntity(groupId)),
    Multipart.FormData.BodyPart.Strict.apply("artifactId", HttpEntity(artifactId)),
    Multipart.FormData.BodyPart.Strict.apply("version", HttpEntity(version)),
    Multipart.FormData.BodyPart.Strict.apply("githubToken", HttpEntity(githubToken.getOrElse("")))
  )

}

object GithubPackage {

  val MaxUploadByteSize = 120000000
  val FileUploadFieldName = "file"
  val FormErrorMessage = s"POST should include fields: githubUser, githubRepository, groupId, artifactId, version, and $FileUploadFieldName"

  def fromFieldData(iterable: Iterable[(String, String)]): Option[GithubPackage] = {
    val map = iterable.toMap
    for {
      githubUser <- map.get("githubUser")
      githubRepository <- map.get("githubRepository")
      groupId <- map.get("groupId")
      artifactId <- map.get("artifactId")
      version <- map.get("version")
    } yield GithubPackage(githubUser, githubRepository, groupId, artifactId, version, map.get("githubToken").filterNot(_.isEmpty))
  }
}
