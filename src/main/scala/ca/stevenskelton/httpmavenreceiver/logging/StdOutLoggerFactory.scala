package ca.stevenskelton.httpmavenreceiver.logging

import cats.Applicative
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}

//object StdOutLoggerFactory extends LoggerFactoryGenCompanion {
//  def apply[F[_] : Applicative]: LoggerFactory[F] = impl[F]()
//
//  def impl[F[_]]()(implicit F: Applicative[F]): LoggerFactory[F] =
//    new LoggerFactory[F] {
//      override def getLoggerFromName(name: String): SelfAwareStructuredLogger[F] = {
//        val _ = name
//        StdOutLogger[F]()
//      }
//
//      override def fromName(name: String): F[SelfAwareStructuredLogger[F]] =
//        F.pure(getLoggerFromName(name))
//    }
//
//}
class StdOutLoggerFactory[F[_]](implicit F: Applicative[F]) extends LoggerFactory[F] {
  override def getLoggerFromName(name: String): SelfAwareStructuredLogger[F] = {
    val _ = name
    StdOutLogger[F]()
  }

  override def fromName(name: String): F[SelfAwareStructuredLogger[F]] =
    F.pure(getLoggerFromName(name))
}
