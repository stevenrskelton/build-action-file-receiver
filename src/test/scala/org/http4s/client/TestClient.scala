//package org.http4s.client
//
//import cats.effect.{IO, MonadCancelThrow, Resource}
//import org.http4s.{Request, Response}
//
//class TestClient(response: Response[IO])(implicit F: MonadCancelThrow[IO]) extends DefaultClient[IO]()(F) {
//
//  override def run(req: Request[IO]): Resource[IO, Response[IO]] = {
//    Resource.pure(response)
//  }
//}
