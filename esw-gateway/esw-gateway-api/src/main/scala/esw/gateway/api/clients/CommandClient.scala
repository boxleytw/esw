package esw.gateway.api.clients

import akka.stream.scaladsl.Source
import csw.location.models.ComponentId
import csw.params.commands.CommandResponse.SubmitResponse
import csw.params.commands.{CommandResponse, ControlCommand}
import csw.params.core.models.Id
import csw.params.core.states.{CurrentState, StateName}
import esw.gateway.api.CommandServiceApi
import esw.gateway.api.codecs.RestlessCodecs
import esw.gateway.api.messages.GatewayHttpRequest.CommandRequest
import esw.gateway.api.messages.GatewayWebsocketRequest.{QueryFinal, SubscribeCurrentState}
import esw.gateway.api.messages.{CommandAction, CommandError, GatewayWebsocketRequest, InvalidComponent}
import msocket.api.{ClientSocket, HttpClient}

import scala.concurrent.Future

class CommandClient(httpClient: HttpClient, socket: ClientSocket[GatewayWebsocketRequest])
    extends CommandServiceApi
    with RestlessCodecs {

  override def process(
      componentId: ComponentId,
      command: ControlCommand,
      action: CommandAction
  ): Future[Either[InvalidComponent, CommandResponse]] = {
    httpClient.post[CommandRequest, Either[InvalidComponent, CommandResponse]](
      CommandRequest(componentId, command, action)
    )
  }

  override def queryFinal(componentId: ComponentId, runId: Id): Future[Either[InvalidComponent, SubmitResponse]] = {
    socket.requestResponse[Either[InvalidComponent, SubmitResponse]](QueryFinal(componentId, runId))
  }

  override def subscribeCurrentState(
      componentId: ComponentId,
      stateNames: Set[StateName],
      maxFrequency: Option[Int]
  ): Source[CurrentState, Future[Option[CommandError]]] = {
    socket.requestStreamWithError[CurrentState, CommandError](
      SubscribeCurrentState(componentId, stateNames, maxFrequency)
    )
  }
}