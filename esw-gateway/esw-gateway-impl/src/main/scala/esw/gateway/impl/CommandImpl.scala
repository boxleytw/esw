package esw.gateway.impl

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.location.models.ComponentId
import csw.params.commands.CommandResponse.{OnewayResponse, SubmitResponse, ValidateResponse}
import csw.params.commands.ControlCommand
import csw.params.core.models.Id
import csw.params.core.states.{CurrentState, StateName}
import esw.gateway.api.protocol.{InvalidComponent, InvalidMaxFrequency}
import esw.gateway.api.{CommandApi, CommandServiceFactoryApi}
import esw.gateway.impl.SourceExtensions.RichSource
import msocket.api.models.Subscription

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class CommandImpl(commandServiceFactory: CommandServiceFactoryApi)(implicit ec: ExecutionContext) extends CommandApi {

  def submit(componentId: ComponentId, command: ControlCommand): Future[Either[InvalidComponent, SubmitResponse]] =
    process(componentId, _.submit(command))

  def oneway(componentId: ComponentId, command: ControlCommand): Future[Either[InvalidComponent, OnewayResponse]] =
    process(componentId, _.oneway(command))

  def validate(componentId: ComponentId, command: ControlCommand): Future[Either[InvalidComponent, ValidateResponse]] = {
    process(componentId, _.validate(command))
  }

  def queryFinal(componentId: ComponentId, runId: Id): Future[Either[InvalidComponent, SubmitResponse]] = {
    process(componentId, _.queryFinal(runId)(Timeout(100.hours)))
  }

  private def process[T](componentId: ComponentId, action: CommandService => Future[T]): Future[Either[InvalidComponent, T]] = {
    commandServiceFactory
      .commandService(componentId)
      .flatMap {
        case Right(commandService) => action(commandService).map(Right(_))
        case Left(value)           => Future.successful(Left(value))
      }
  }

  override def subscribeCurrentState(
      componentId: ComponentId,
      stateNames: Set[StateName],
      maxFrequency: Option[Int]
  ): Source[CurrentState, Subscription] = {

    def futureSource: Future[Source[CurrentState, _]] =
      commandServiceFactory
        .commandService(componentId)
        .map {
          case Right(commandService)       => commandService.subscribeCurrentState(stateNames)
          case Left(err: InvalidComponent) => Source.failed(err)
        }

    def currentStateSource: Source[CurrentState, Subscription] = Source.futureSource(futureSource).withSubscription()

    maxFrequency match {
      case Some(x) if x <= 0 => Source.failed(new InvalidMaxFrequency).withSubscription()
      case Some(frequency)   => currentStateSource.buffer(1, OverflowStrategy.dropHead).throttle(frequency, 1.second)
      case None              => currentStateSource
    }
  }
}
