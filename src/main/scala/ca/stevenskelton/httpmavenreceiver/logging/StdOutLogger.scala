package ca.stevenskelton.httpmavenreceiver.logging

import cats.Applicative
import org.typelevel.log4cats.SelfAwareStructuredLogger

class StdOutLogger[F[_] : Applicative] extends SelfAwareStructuredLogger[F]:

  private val yes: F[Boolean] = Applicative[F].pure(true)

  private def void(arg: => Any): F[Unit] = Applicative[F].pure:
    println(arg)

  @inline override def isTraceEnabled: F[Boolean] = yes

  @inline override def isDebugEnabled: F[Boolean] = yes

  @inline override def isInfoEnabled: F[Boolean] = yes

  @inline override def isWarnEnabled: F[Boolean] = yes

  @inline override def isErrorEnabled: F[Boolean] = yes

  @inline override def trace(t: Throwable)(msg: => String): F[Unit] = void(t)

  @inline override def trace(msg: => String): F[Unit] = void(msg)

  @inline override def trace(ctx: Map[String, String])(msg: => String): F[Unit] = void(msg)

  @inline override def debug(t: Throwable)(msg: => String): F[Unit] = void(msg)

  @inline override def debug(msg: => String): F[Unit] = void(msg)

  @inline override def debug(ctx: Map[String, String])(msg: => String): F[Unit] = void(msg)

  @inline override def info(t: Throwable)(msg: => String): F[Unit] = void(msg)

  @inline override def info(msg: => String): F[Unit] = void(msg)

  @inline override def info(ctx: Map[String, String])(msg: => String): F[Unit] = void(msg)

  @inline override def warn(t: Throwable)(msg: => String): F[Unit] = void(msg)

  @inline override def warn(msg: => String): F[Unit] = void(msg)

  @inline override def warn(ctx: Map[String, String])(msg: => String): F[Unit] = void(msg)

  @inline override def error(t: Throwable)(msg: => String): F[Unit] = void(msg)

  @inline override def error(msg: => String): F[Unit] = void(msg)

  @inline override def error(ctx: Map[String, String])(msg: => String): F[Unit] = void(msg)

  @inline override def trace(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
    void(msg)

  @inline override def debug(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
    void(msg)

  @inline override def info(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
    void(msg)

  @inline override def warn(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
    void(msg)

  @inline override def error(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
    void(msg)

