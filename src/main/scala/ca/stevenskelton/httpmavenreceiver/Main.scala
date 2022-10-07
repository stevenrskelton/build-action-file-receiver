package ca.stevenskelton.httpmavenreceiver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {

  val conf = ConfigFactory.load()
  val actorSystem = ActorSystem("http", conf)
  val logger = Logger("http")
  val httpServer = HttpServer.bindPublic(conf)(actorSystem, logger)

  httpServer.map {
    httpBinding =>
      val address = httpBinding.localAddress
      logger.info(s"HTTP server bound to ${address.getHostString}:${address.getPort}")
      httpBinding.whenTerminated.onComplete {
        _ =>
          actorSystem.terminate()
          System.exit(0)
      }
  }.recover {
    ex =>
      logger.error("Failed to bind endpoint, terminating system", ex)
      actorSystem.terminate()
      System.exit(1)
  }
}
