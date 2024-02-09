package ca.stevenskelton.httpmavenreceiver

import cats.effect.{IO, Resource}
import fs2.io.file.{Files, Path}
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.Multipart
import org.typelevel.log4cats.LoggerFactory

import java.security.MessageDigest
import java.time.Duration

case class SuccessfulUpload(filename: String, fileSize: Long, md5: MD5Hash) {

  def responseBody(): IO[Response[IO]] = {
    val responseBody = s"Successfully saved upload of $filename, ${Utils.humanReadableBytes(fileSize)}, MD5 $md5"
    Ok(responseBody).map(_.withContentType(`Content-Type`(MediaType.text.plain)))
  }

}
