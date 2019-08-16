package esw.utils.csw

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import csw.location.api.scaladsl.{LocationService, RegistrationResult}
import csw.location.models._
import csw.params.core.models.Subsystem
import esw.ocs.api.models.messages.RegistrationError

import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class LocationServiceUtils(locationService: LocationService) {

  private def addCoordinatedShutdownTask(
      coordinatedShutdown: CoordinatedShutdown,
      registrationResult: RegistrationResult
  ): Unit = {
    coordinatedShutdown.addTask(
      CoordinatedShutdown.PhaseBeforeServiceUnbind,
      s"unregistering-${registrationResult.location}"
    )(() => registrationResult.unregister())
  }

  def register(
      akkaRegistration: AkkaRegistration
  )(implicit actorSystem: ActorSystem[_]): Future[Either[RegistrationError, AkkaLocation]] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    locationService
      .register(akkaRegistration)
      .map { result =>
        addCoordinatedShutdownTask(CoordinatedShutdown(actorSystem.toUntyped), result)
        Right(result.location.asInstanceOf[AkkaLocation])
      }
      .recoverWith {
        case NonFatal(e) => Future.successful(Left(RegistrationError(e.getMessage)))
      }
  }

  def listBy(subsystem: Subsystem, componentType: ComponentType)(
      implicit ec: ExecutionContext
  ): Future[List[AkkaLocation]] = {
    locationService
      .list(componentType)
      .map(_.collect {
        case akkaLocation @ AkkaLocation(_, prefix, _) if prefix.subsystem == subsystem => akkaLocation
      })
  }

  //Can be used to listBySequencerId() and listByObsMode(), in future. Separate APIs can be created once we have concrete
  //classes for `SequencerId` and `ObsMode`
  def listByComponentName(name: String)(implicit ec: ExecutionContext): Future[List[Location]] = {
    locationService.list.map { locations =>
      locations.filter(x => x.connection.componentId.name.contains(name))
    }
  }

  def resolveByComponentNameAndType(name: String, componentType: ComponentType)(
      implicit ec: ExecutionContext
  ): Future[Option[Location]] = {
    async {
      await(locationService.list(componentType))
        .find(location => location.connection.componentId.name.equals(name))
    }
  }

  // To be used by Script Writer
  def resolveSequencer(sequencerId: String, observingMode: String)(
      implicit ec: ExecutionContext
  ): Future[AkkaLocation] = {
    async {
      await(locationService.list)
        .find(location => location.connection.componentId.name.contains(s"$sequencerId@$observingMode"))
        .asInstanceOf[Option[AkkaLocation]]
    }.collect {
      case Some(location) => location
      case None           => throw new IllegalArgumentException(s"Could not find any sequencer with name: $sequencerId@$observingMode")
    }
  }
}