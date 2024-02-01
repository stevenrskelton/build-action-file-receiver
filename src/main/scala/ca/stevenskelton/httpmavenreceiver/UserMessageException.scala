package ca.stevenskelton.httpmavenreceiver

import org.http4s.Status

case class UserMessageException(status: Status, message: String) extends Exception(message)
