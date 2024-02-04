package ca.stevenskelton.httpmavenreceiver.logging

import cats.Applicative
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}

class StdOutLoggerFactory[F[_]](implicit F: Applicative[F]) extends LoggerFactory[F] {
  override def getLoggerFromName(name: String): SelfAwareStructuredLogger[F] = {
    val _ = name
    StdOutLogger[F]()
  }

  override def fromName(name: String): F[SelfAwareStructuredLogger[F]] =
    F.pure(getLoggerFromName(name))
}
