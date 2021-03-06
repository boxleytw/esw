package esw.gateway.server

import akka.http.scaladsl.server.Route
import csw.admin.api.AdminService
import csw.admin.impl.AdminServiceImpl
import esw.gateway.api.codecs.GatewayCodecs
import esw.gateway.api.protocol.{PostRequest, WebsocketRequest}
import esw.gateway.api.{AlarmApi, EventApi, LoggingApi}
import esw.gateway.impl._
import esw.gateway.server.handlers.{PostHandlerImpl, WebsocketHandlerImpl}
import esw.gateway.server.metrics.{Metrics, _}
import esw.gateway.server.utils.Resolver
import esw.http.core.wiring.{HttpService, ServerWiring}
import msocket.api.ContentType
import msocket.impl.RouteFactory
import msocket.impl.post.{HttpPostHandler, PostRouteFactory}
import msocket.impl.ws.{WebsocketHandler, WebsocketRouteFactory}
import akka.http.scaladsl.server.Directives._

class GatewayWiring(_port: Option[Int], metricsEnabled: Boolean = false) extends GatewayCodecs {
  lazy val wiring = new ServerWiring(_port)
  import wiring._
  import cswWiring._
  import cswWiring.actorRuntime.{ec, typedSystem}

  private val resolver = new Resolver(locationService)(actorSystem)

  lazy val alarmApi: AlarmApi     = new AlarmImpl(alarmService)
  lazy val eventApi: EventApi     = new EventImpl(eventService, eventSubscriberUtil)
  lazy val loggingApi: LoggingApi = new LoggingImpl(new LoggerCache)
  lazy val adminApi: AdminService = new AdminServiceImpl(locationService)

  lazy val postHandler: HttpPostHandler[PostRequest] = {
    val postHandlerImpl: PostHandlerImpl = new PostHandlerImpl(alarmApi, resolver, eventApi, loggingApi, adminApi)
    if (metricsEnabled) new PostHandlerMetrics(postHandlerImpl) else postHandlerImpl
  }

  def websocketHandlerFactory(contentType: ContentType): WebsocketHandler[WebsocketRequest] = {
    val wsHandlerImpl = new WebsocketHandlerImpl(resolver, eventApi, contentType)
    if (metricsEnabled) new WebsocketHandlerMetrics(wsHandlerImpl, contentType)
    else wsHandlerImpl
  }

  lazy val routes: Route = {
    val mainRoutes = RouteFactory.combine(
      new PostRouteFactory[PostRequest]("post-endpoint", postHandler),
      new WebsocketRouteFactory[WebsocketRequest]("websocket-endpoint", websocketHandlerFactory)
    )
    if (metricsEnabled) mainRoutes ~ Metrics.metricsRoute else mainRoutes
  }

  lazy val httpService = new HttpService(logger, locationService, routes, settings, actorRuntime)
}
