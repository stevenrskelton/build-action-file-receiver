package ca.stevenskelton.httpmavenreceiver

import org.apache.pekko.http.scaladsl.model.StatusCode

case class UserMessageException(statusCode: StatusCode, message: String) extends Exception(message)
