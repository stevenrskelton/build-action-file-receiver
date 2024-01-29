package ca.stevenskelton.httpmavenreceiver

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.{Http, HttpExt}
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.stream.Materializer

import scala.concurrent.Future

trait HttpExtInterface {
  val materializer: Materializer

  def singleRequest(request: HttpRequest): Future[HttpResponse]
}

case class HttpExtImpl(actorSystem: ActorSystem) extends HttpExtInterface {
  val httpExt: HttpExt = Http(actorSystem)
  
  override val materializer: Materializer = Materializer(httpExt.system)
  
  override def singleRequest(request: HttpRequest): Future[HttpResponse] = httpExt.singleRequest(request)
}