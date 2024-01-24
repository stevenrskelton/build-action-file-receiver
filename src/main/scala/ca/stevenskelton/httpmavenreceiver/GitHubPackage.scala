package ca.stevenskelton.httpmavenreceiver

case class GitHubPackage(
                          githubAuthToken: String,
                          githubUser: String,
                          githubRepository: String,
                          groupId: String,
                          artifactId: String,
                          version: String
                        ) {
  val path: String = s"https://maven.pkg.github.com/$githubUser/$githubRepository/${groupId.replace(".", "/")}/$artifactId/$version"
}

object GitHubPackage {

  val FileUploadFieldName = "jar"
  val FormErrorMessage = s"PUT body should include fields: githubUser, githubRepository, groupId, artifactId, version, and $FileUploadFieldName"

  def fromFieldData(iterable: Iterable[(String, String)]): Option[GitHubPackage] = {
    val map = iterable.toMap
    for {
      githubAuthToken <- map.get("githubAuthToken")
      githubUser <- map.get("githubUser")
      githubRepository <- map.get("githubRepository")
      groupId <- map.get("groupId")
      artifactId <- map.get("artifactId")
      version <- map.get("version")
    } yield GitHubPackage(githubAuthToken, githubUser, githubRepository, groupId, artifactId, version)
  }
}
