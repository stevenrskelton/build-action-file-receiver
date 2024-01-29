package ca.stevenskelton.httpmavenreceiver

import ca.stevenskelton.httpmavenreceiver.customuseractions.StevenRSkeltonGitHubUser
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

  private val DefaultAllowedUploadSize = 1024000L

  private val conf = ConfigFactory.load().resolve()

  implicit private val actorSystem: ActorSystem = ActorSystem("http", conf)
  implicit private val logger: Logger = Logger("http")

  private val uploadDirectory: File = new File(conf.getString("http-maven-receiver.file-directory"))

  private val host: String = Try(conf.getString("http-maven-receiver.host")).toOption.getOrElse {
    logger.warn("No `http-maven-receiver.host` config value found, binding to localhost")
    "localhost"
  }
  private val port: Int = conf.getInt("http-maven-receiver.port")
  private val maxUploadByteSize = Try(conf.getBytes("http-maven-receiver.max-upload-size").toLong).getOrElse {
    logger.info(s"No `http-maven-receiver.max-upload-size` config value found, setting to default ${Utils.humanFileSize(DefaultAllowedUploadSize)}")
    DefaultAllowedUploadSize
  }

  if (!uploadDirectory.exists) {
    logger.info(s"Creating file directory: ${uploadDirectory.getAbsolutePath}")
    if (!uploadDirectory.mkdirs) {
      logger.error(s"Could not create directory")
      System.exit(1)
    }
  }

  logger.info(s"Setting file directory to: ${uploadDirectory.getAbsolutePath} with max upload size: ${Utils.humanFileSize(maxUploadByteSize)}")

  val httpExtImpl = HttpExtImpl(actorSystem)
  
  private val artifactUpload = ArtifactUploadRoute(
    httpExtImpl,
    uploadDirectory.toPath,
    new MavenMD5CompareRequestHooks(_),
    maxUploadByteSize,
    allowedGitHubUsers = Seq(StevenRSkeltonGitHubUser)
  )

  val publicRoutes = path("releases")(artifactUpload.releasesPutRoute)

  httpExtImpl.httpExt.newServerAt(host, port).bind(concat(publicRoutes, Route.seal {
    extractRequestContext {
      context =>
        complete {
          logger.info(s"404 ${context.request.method.value} ${context.unmatchedPath}")
          HttpResponse(StatusCodes.NotFound)
        }
    }
  })).map {
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

}
