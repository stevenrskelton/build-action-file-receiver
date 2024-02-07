package ca.stevenskelton.httpmavenreceiver

import cats.effect.IO
import org.typelevel.log4cats.Logger

import scala.collection.mutable.ListBuffer

class RecordingLogger extends Logger[IO] {

  def lines: Array[String] = logBuffer.toArray

  private val logBuffer = new ListBuffer[String]()

  override def error(t: Throwable)(message: => String): IO[Unit] = IO.pure(logBuffer.append(t.toString))

  override def warn(t: Throwable)(message: => String): IO[Unit] = IO.pure(logBuffer.append(t.toString))

  override def info(t: Throwable)(message: => String): IO[Unit] = IO.pure(logBuffer.append(t.toString))

  override def debug(t: Throwable)(message: => String): IO[Unit] = IO.pure(logBuffer.append(t.toString))

  override def trace(t: Throwable)(message: => String): IO[Unit] = IO.pure(logBuffer.append(t.toString))

  override def error(message: => String): IO[Unit] = IO.pure(logBuffer.append(message))

  override def warn(message: => String): IO[Unit] = IO.pure(logBuffer.append(message))

  override def info(message: => String): IO[Unit] = IO.pure(logBuffer.append(message))

  override def debug(message: => String): IO[Unit] = IO.pure(logBuffer.append(message))

  override def trace(message: => String): IO[Unit] = IO.pure(logBuffer.append(message))
}
