package ca.stevenskelton.httpmavenreceiver

case class AllowedGithubUser(githubUsername: String, postActions: Seq[String])
