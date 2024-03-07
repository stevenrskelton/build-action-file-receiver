package ca.stevenskelton.httpmavenreceiver.multipart

import fs2.Stream
import org.http4s.{Headers, MediaType, TransferCoding}
import org.http4s.multipart.*
import org.http4s.headers.*
final case class Multipart[+F[_]](
                                   parts: Stream[F, Part[F]],
                                   boundary: Boundary,
                                 ) {

  def headers: Headers =
    Headers(
      `Transfer-Encoding`(TransferCoding.chunked),
      `Content-Type`(MediaType.multipartType("form-data", Some(boundary.value))),
    )
}
