package ca.stevenskelton.httpmavenreceiver.githubmaven

import ca.stevenskelton.httpmavenreceiver.{AuthToken, MD5Hash, ResponseException}
import cats.effect.{IO, Resource}
import org.http4s.client.Client
import org.http4s.{Header, Headers, Method, Request, Status}
import org.typelevel.log4cats.Logger

import scala.util.Try

object MD5Util:

  def fetchMavenMD5(mavenPackage: MavenPackage, authToken: AuthToken)(using httpClient: Resource[IO, Client[IO]], logger: Logger[IO]): IO[MD5Hash] =

    val gitHubMD5Uri = mavenPackage.gitHubMavenArtifactPath / mavenPackage.version / s"${mavenPackage.filename}.md5"

    logger.info(s"Fetching MD5 at $gitHubMD5Uri") *>
      httpClient.use:
        client =>

          val request = Request[IO](
            Method.GET,
            gitHubMD5Uri,
            headers = Headers(Header.ToRaw.keyValuesToRaw("Authorization" -> s"token $authToken")),
          )

          client
            .expectOr[String](request):
              errorResponse =>
                val msg = if errorResponse.status.code == Status.NotFound.code then
                  s"Maven ${mavenPackage.filename} does not exist in GitHub"
                else
                  val errorBody = Try(errorResponse.entity.body.bufferAll.compile.toString).toOption.getOrElse("[error reading body]")
                  s"Error reading Maven (${errorResponse.status.code}):\n$errorBody"

                logger.error(msg) *> IO.raiseError(ResponseException(errorResponse.status, msg))

  end fetchMavenMD5
end MD5Util