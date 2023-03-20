package ca.stevenskelton.httpmavenreceiver

case class GithubPackage(
                          githubUser: String,
                          githubRepository: String,
                          groupId: String,
                          artifactId: String,
                          version: String
                        ) {
  val path: String = s"https://maven.pkg.github.com/$githubUser/$githubRepository/${groupId.replace(".", "/")}/$artifactId/$version"

}

object GithubPackage {

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
    } yield GithubPackage(githubUser, githubRepository, groupId, artifactId, version)
  }
}
