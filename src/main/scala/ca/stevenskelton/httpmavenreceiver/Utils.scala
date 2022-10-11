package ca.stevenskelton.httpmavenreceiver

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.typesafe.scalalogging.Logger

import java.io._
import java.security.MessageDigest
import java.time.Duration
import scala.concurrent.Future
import scala.util.{Try, Using}

object Utils {

  private val ByteStringSink = Sink.fold[String, ByteString]("") { case (acc, str) =>
    acc + str.utf8String
  }

  def sinkToString(source: akka.stream.scaladsl.Source[ByteString, _])(implicit mat: Materializer): Future[String] = {
    source.runWith(ByteStringSink)
  }

  def writeFile(file: File, content: String)(implicit logger: Logger): Try[Unit] = {
    if (file.exists) logger.info(s"Overwriting existing ${file.getAbsolutePath}")
    Using(new BufferedWriter(new FileWriter(file))) {
      _.write(content)
    }
  }

  def byteArrayToHexString(bytes: Array[Byte]): String = {
    // this array of bytes has bytes in decimal format
    // so we need to convert it into hexadecimal format

    // for this we create an object of StringBuilder
    // since it allows us to update the string i.e. its
    // mutable
    val sb = new StringBuilder

    // loop through the bytes array
    for (i <- 0 until bytes.length) { // the following line converts the decimal into
      // hexadecimal format and appends that to the
      // StringBuilder object
      sb.append(Integer.toString((bytes(i) & 0xff) + 0x100, 16).substring(1))
    }
    sb.toString
  }

  def md5sum(file: File): String = {
    // instantiate a MessageDigest Object by passing
    // string "MD5" this means that this object will use
    // MD5 hashing algorithm to generate the checksum
    val digest = MessageDigest.getInstance("MD5")

    Using(new FileInputStream(file)) {
      fis =>
        // Create byte array to read data in chunks
        val byteArray = new Array[Byte](1024)
        var bytesCount = 0

        // read the data from file and update that data in
        // the message digest
        def read: Int = {
          bytesCount = fis.read(byteArray)
          bytesCount
        }

        while (read != -1) digest.update(byteArray, 0, bytesCount)
    }
    // store the bytes returned by the digest() method
    byteArrayToHexString(digest.digest)
  }

  def humanFileSize(file: File): String = {
    if (!file.exists) ""
    else {
      val size = file.length
      if (size > 1000000) {
        s"${(size / 1000000).toInt}mb"
      } else if (size > 1024) {
        s"${(size / 1024).toInt}kb"
      } else {
        s"${size}bytes"
      }
    }
  }

  def humanReadableDuration(duration: Duration): String = {
    val seconds = duration.toSeconds
    if (seconds >= 2592000) {
      s"${(seconds / 2592000).toInt} months"
    } else if (seconds > 86400) {
      s"${(seconds / 86400).toInt} days"
    } else if (seconds > 3600) {
      s"${(seconds / 3600).toInt} hours"
    } else if (seconds > 60) {
      s"${(seconds / 60).toInt} minutes"
    } else {
      s"$seconds seconds"
    }
  }
}
