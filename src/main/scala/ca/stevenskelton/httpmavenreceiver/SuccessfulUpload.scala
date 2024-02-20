package ca.stevenskelton.httpmavenreceiver

import cats.effect.IO
import org.http4s.dsl.io.{Ok, http4sOkSyntax}
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Response}

case class SuccessfulUpload(filename: String, fileSize: Long, md5: MD5Hash):

  def responseBody(): IO[Response[IO]] =
    val responseBody = s"Successfully saved upload of $filename, ${Utils.humanReadableBytes(fileSize)}, MD5 $md5"
    Ok(responseBody).map(_.withContentType(`Content-Type`(MediaType.text.plain)))

