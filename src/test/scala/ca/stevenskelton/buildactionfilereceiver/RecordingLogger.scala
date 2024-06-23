package ca.stevenskelton.buildactionfilereceiver

import cats.effect.IO
import org.typelevel.log4cats.Logger

import scala.collection.mutable.ListBuffer

class RecordingLogger extends Logger[IO] {

  def lines: Array[String] = logBuffer.toArray

  private val logBuffer = new ListBuffer[String]()

  private def log(msg: String): IO[Unit] = {
    //    IO.println(msg) *>
    IO(synchronized(logBuffer.append(msg)))
  }

  override def error(t: Throwable)(message: => String): IO[Unit] = log(t.toString)

  override def warn(t: Throwable)(message: => String): IO[Unit] = log(t.toString)

  override def info(t: Throwable)(message: => String): IO[Unit] = log(t.toString)

  override def debug(t: Throwable)(message: => String): IO[Unit] = log(t.toString)

  override def trace(t: Throwable)(message: => String): IO[Unit] = log(t.toString)

  override def error(message: => String): IO[Unit] = log(message)

  override def warn(message: => String): IO[Unit] = log(message)

  override def info(message: => String): IO[Unit] = log(message)

  override def debug(message: => String): IO[Unit] = log(message)

  override def trace(message: => String): IO[Unit] = log(message)
}
