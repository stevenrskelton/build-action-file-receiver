package ca.stevenskelton.httpmavenreceiver.customuseractions

import ca.stevenskelton.httpmavenreceiver.AllowedGitHubUser
import com.typesafe.scalalogging.Logger
import org.apache.pekko.Done

import java.io.File
import scala.concurrent.Future

object StevenRSkeltonGitHubUser extends AllowedGitHubUser("stevenrskelton") {

  private def exec(command: String)(implicit logger: Logger): Unit = {
    val result = sys.process.Process(command).!
    if (result != 0) {
      val message = s"Could not execute post command `$command`, returned $result"
      logger.error(message)
      throw new Exception(message)
    }
  }

  override def postHook(file: File)(implicit logger: Logger): Future[Done] = {
    if (file.getName.startsWith("tradeauditserver-assembly-")) {
      exec(s"sudo -- mv ${file.getAbsolutePath} /home/tradeaudit/")
      exec(s"sudo -- rm /home/tradeaudit/tradeauditserver-assembly.jar")
      exec(s"sudo -- ln -s /home/tradeaudit/${file.getName} /home/tradeaudit/tradeauditserver-assembly.jar")
      exec(s"sudo -- systemctl restart tradeaudit")
      logger.info(s"Successfully installed new tradeaudit version ${file.getName}")
    }
    Future.successful(Done)
  }
}