package ca.stevenskelton.httpmavenreceiver
import akka.http.scaladsl.model.HttpResponse
import com.typesafe.scalalogging.Logger

import java.io.File
import scala.concurrent.Future

trait PostHook {
  def postHook(httpResponse: HttpResponse, allowedGithubUser: AllowedGithubUser, file: File)(implicit logger: Logger): Future[HttpResponse]
}
abstract class AllowedGithubUser(val githubUsername: String) extends PostHook

object StevenrskeltonGithubUser extends AllowedGithubUser("stevenrskelton") {

  private def exec(command: String)(implicit logger: Logger): Unit = {
    val result = sys.process.Process(command).!
    if(result != 0){
      val message = s"Could not execute post command `$command`, returned $result"
      logger.error(message)
      throw new Exception(message)
    }
  }

  override def postHook(httpResponse: HttpResponse, allowedGithubUser: AllowedGithubUser, file: File)(implicit logger: Logger): Future[HttpResponse] = {
    if(file.getName.startsWith("tradeauditserver-assembly-")){
      exec(s"sudo -- mv ${file.getAbsolutePath} /home/tradeaudit/")
      exec(s"sudo -- rm /home/tradeaudit/tradeauditserver-assembly-0.1.0-SNAPSHOT.jar")
      exec(s"sudo -- ln -s /home/tradeaudit/${file.getName} /home/tradeaudit/tradeauditserver-assembly-0.1.0-SNAPSHOT.jar")
      exec(s"sudo -- systemctl restart tradeaudit")
      logger.info(s"Successfully installed new tradeaudit version ${file.getName}")
    }
    Future.successful(httpResponse)
  }
}