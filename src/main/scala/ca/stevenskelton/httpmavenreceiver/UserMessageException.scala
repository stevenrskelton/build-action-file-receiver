package ca.stevenskelton.httpmavenreceiver

import akka.http.scaladsl.model.StatusCode

case class UserMessageException(statusCode: StatusCode, message: String) extends Exception(message)
