package ca.stevenskelton.buildactionfilereceiver.multipart

import fs2.Stream
import org.http4s.headers.{`Content-Type`, `Transfer-Encoding`}
import org.http4s.multipart.{Boundary, Part}
import org.http4s.{Headers, MediaType, TransferCoding}

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
