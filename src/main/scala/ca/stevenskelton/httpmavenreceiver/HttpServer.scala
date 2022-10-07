package ca.stevenskelton.httpmavenreceiver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

import java.io.File
import scala.concurrent.Future

object HttpServer {

  def bindPublic(config: Config)(implicit actorSystem: ActorSystem, logger: Logger): Future[Http.ServerBinding] = {

    val host = config.getString("http-maven-receiver.host")
    val port = config.getInt("http-maven-receiver.port")
    val directory = new File(config.getString("http-maven-receiver.file-directory"))
    logger.info(s"Setting file directory to: ${directory.getPath}")

    val jarUpload = new ArtifactUpload(GithubPackages(
      Http(actorSystem),
      directory.toPath,
      githubToken = "TODO"
    ))

    //    val publicRoutes = path("releases")(concat(jarUpload.releasesGet, jarUpload.releasesPost))
    val publicRoutes = path("releases")(jarUpload.releasesPost)

    jarUpload.githubPackages.httpExt.newServerAt(host, port).bind(concat(publicRoutes, Route.seal {
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
