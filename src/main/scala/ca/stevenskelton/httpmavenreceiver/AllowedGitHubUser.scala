package ca.stevenskelton.httpmavenreceiver
import com.typesafe.scalalogging.Logger
import org.apache.pekko.Done

import java.io.File
import scala.concurrent.Future

abstract class AllowedGitHubUser(val githubUsername: String) extends PostHook

object AllowedGitHubUser {
  def apply(githubUsername: String): AllowedGitHubUser = new AllowedGitHubUser(githubUsername) {
    override def postHook(file: File)(implicit logger: Logger): Future[Done] = Future.successful(Done)
  }
}
