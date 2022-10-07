package ca.stevenskelton.httpmavenreceiver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object Main extends App {

  private val conf = ConfigFactory.load()

  implicit private val actorSystem = ActorSystem("http", conf)
  implicit private val logger = Logger("http")

  private val directory = new File(conf.getString("http-maven-receiver.file-directory"))
  logger.info(s"Setting file directory to: ${directory.getPath}")

  private val artifactUpload = {
    val httpExt = Http(actorSystem)
    val configGithubToken = Try(conf.getString("http-maven-receiver.github-token")).toOption
    ArtifactUpload(
      httpExt,
      directory.toPath,
      configGithubToken,
      (githubPackage, fileInfo) => new MavenMD5CompareRequestHooks(httpExt, configGithubToken, directory.toPath, githubPackage, fileInfo)
    )
  }

  bindPublic(
    artifactUpload,
    conf.getString("http-maven-receiver.host"),
    conf.getInt("http-maven-receiver.port")
  ).map {
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

  def bindPublic(artifactUpload: ArtifactUpload, host: String, port: Int)(implicit actorSystem: ActorSystem, logger: Logger): Future[Http.ServerBinding] = {

    val publicRoutes = path("releases")(artifactUpload.releasesPost)

    artifactUpload.httpExt.newServerAt(host, port).bind(concat(publicRoutes, Route.seal {
      extractRequestContext {
        context =>
          complete {
            logger.info(s"404 ${context.request.method.value} ${context.unmatchedPath}")
            HttpResponse(StatusCodes.NotFound)
          }
      }
    }))
  }
}
