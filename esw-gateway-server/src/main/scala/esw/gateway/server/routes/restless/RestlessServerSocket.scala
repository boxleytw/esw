package esw.gateway.server.routes.restless

import akka.NotUsed
import akka.stream.scaladsl.Source
import esw.gateway.server.routes.restless.api.GatewayApi
import esw.gateway.server.routes.restless.codecs.RestlessCodecs
import esw.gateway.server.routes.restless.messages.GatewayWebsocketRequest
import esw.gateway.server.routes.restless.messages.GatewayWebsocketRequest.{
  QueryFinal,
  Subscribe,
  SubscribeCurrentState,
  SubscribeWithPattern
}
import msocket.core.api.Payload
import msocket.core.api.ToPayload.{FutureToPayload, SourceWithErrorToPayload}
import msocket.core.server.ServerSocket

class RestlessServerSocket(gatewayApi: GatewayApi) extends ServerSocket[GatewayWebsocketRequest] with RestlessCodecs {

  import gatewayApi.cswCtx.actorRuntime.{ec, mat}

  override def requestStream(request: GatewayWebsocketRequest): Source[Payload[_], NotUsed] = request match {
    case QueryFinal(componentType, componentName, runId) =>
      gatewayApi.queryFinal(componentType, componentName, runId).payload
    case SubscribeCurrentState(componentType, componentName, stateNames, maxFrequency) =>
      gatewayApi.subscribeCurrentState(componentType, componentName, stateNames, maxFrequency).resultPayloads

    case Subscribe(eventKeys, maxFrequency) => gatewayApi.subscribe(eventKeys, maxFrequency).resultPayloads
    case SubscribeWithPattern(subsystem, maxFrequency, pattern) =>
      gatewayApi.pSubscribe(subsystem, maxFrequency, pattern).resultPayloads
  }
}
