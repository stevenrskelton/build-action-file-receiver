package ca.stevenskelton.httpmavenreceiver

case class GithubPackage(
                          githubAuthToken: String,
                          githubUser: String,
                          githubRepository: String,
                          groupId: String,
                          artifactId: String,
                          version: String
                        ) {
  val path: String = if (version.contains("SNAPSHOT")) {
    s"https://maven.pkg.github.com/$githubUser/$githubRepository/${groupId.replace(".", "/")}/$artifactId/$version"
  } else {
    s"https://maven.pkg.github.com/$githubUser/$githubRepository/${groupId.replace(".", "/")}/$artifactId"
  }

}

object GithubPackage {

  val FileUploadFieldName = "jar"
  val FormErrorMessage = s"POST should include fields: githubUser, githubRepository, groupId, artifactId, version, and $FileUploadFieldName"

  def fromFieldData(iterable: Iterable[(String, String)]): Option[GithubPackage] = {
    val map = iterable.toMap
    for {
      githubAuthToken <- map.get("githubAuthToken")
      githubUser <- map.get("githubUser")
      githubRepository <- map.get("githubRepository")
      groupId <- map.get("groupId")
      artifactId <- map.get("artifactId")
      version <- map.get("version")
    } yield GithubPackage(githubAuthToken, githubUser, githubRepository, groupId, artifactId, version)
  }
}
