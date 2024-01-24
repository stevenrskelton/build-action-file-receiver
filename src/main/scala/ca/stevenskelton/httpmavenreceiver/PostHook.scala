package ca.stevenskelton.httpmavenreceiver

import com.typesafe.scalalogging.Logger
import org.apache.pekko.Done

import java.io.File
import scala.concurrent.Future

trait PostHook {
  def postHook(file: File)(implicit logger: Logger): Future[Done]
}
