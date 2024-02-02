package ca.stevenskelton.httpmavenreceiver

import org.http4s.{Charset, Entity, HttpVersion, MessageFailure, Response, Status}

case class UserMessageException(status: Status, message: String, override val cause: Option[Throwable] = None)
  extends RuntimeException(message, cause.orNull) with MessageFailure {

  override def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(status, httpVersion, entity = Entity.string(message, Charset.`UTF-8`))
}
