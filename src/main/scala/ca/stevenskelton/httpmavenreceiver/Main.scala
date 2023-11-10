package ca.stevenskelton.httpmavenreceiver

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object Main extends App {

  private val conf = ConfigFactory.load().resolve()

  implicit private val actorSystem: ActorSystem = ActorSystem("http", conf)
  implicit private val logger: Logger = Logger("http")

  private val uploadDirectory: File = new File(Option(conf.getString("http-maven-receiver.file-directory")).getOrElse {
    throw new Exception("Set `http-maven-receiver.upload-directory`")
  })
  private val host: String = Option(conf.getString("http-maven-receiver.host")).getOrElse {
    throw new Exception("Set `http-maven-receiver.host` to the IP to bind")
  }
  private val port: Int = Option(conf.getInt("http-maven-receiver.port")).getOrElse {
    throw new Exception("Set `http-maven-receiver.port` to the port to bind")
  }
  private val maxUploadByteSize = Try(conf.getBytes("http-maven-receiver.max-upload-size").toLong).getOrElse(1024000L)

  if (!uploadDirectory.exists) {
    logger.info(s"Creating file directory: ${uploadDirectory.getAbsolutePath}")
    if (!uploadDirectory.mkdirs) {
      logger.error(s"Could not create directory")
      System.exit(1)
    }
  }

  logger.info(s"Setting file directory to: ${uploadDirectory.getAbsolutePath} with max upload size: $maxUploadByteSize bytes")

  private val artifactUpload = ArtifactUploadRoute(
    Http(actorSystem),
    uploadDirectory.toPath,
    new MavenMD5CompareRequestHooks(_),
    maxUploadByteSize,
    allowedGithubUsers = Seq(StevenrskeltonGithubUser)
  )

  bindPublic(artifactUpload, host, port).map {
    httpBinding =>
      val address = httpBinding.localAddress
      logger.info(s"HTTP server bound to ${address.getHostString}:${address.getPort}")
      httpBinding.whenTerminated.onComplete {
        _ =>
          logger.info(s"Shutting down HTTP server on ${address.getHostString}:${address.getPort}")
          actorSystem.terminate()
          System.exit(0)
      }
  }.recover {
    ex =>
      logger.error("Failed to bind endpoint, terminating system", ex)
      actorSystem.terminate()
      System.exit(1)
  }

  private def bindPublic(artifactUpload: ArtifactUploadRoute, host: String, port: Int)(implicit actorSystem: ActorSystem, logger: Logger): Future[Http.ServerBinding] = {

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
