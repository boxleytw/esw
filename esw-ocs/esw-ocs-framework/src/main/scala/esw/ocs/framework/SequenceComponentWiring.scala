package esw.ocs.framework

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorRef
import akka.actor.typed.SpawnProtocol.Spawn
import akka.actor.typed.scaladsl.AskPattern.Askable
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{AkkaRegistration, ComponentId, ComponentType}
import csw.location.api.scaladsl.LocationService
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.params.core.models.Prefix
import esw.ocs.framework.api.models.messages.SequenceComponentMsg
import esw.ocs.framework.core.SequenceComponent

import scala.concurrent.Await
import scala.concurrent.duration.DurationLong

class SequenceComponentWiring(name: String) {
  lazy val actorRuntime = new ActorRuntime(name)
  import actorRuntime._

  lazy val sequenceComponentRef: ActorRef[SequenceComponentMsg] =
    Await.result(typedSystem ? Spawn(SequenceComponent.behavior, name), 5.seconds)

  private lazy val locationService: LocationService = HttpLocationServiceFactory.makeLocalClient

  //fixme: should this come from conf file
  private lazy val prefix = Prefix("sequence-component")

  def start(): Unit = {

    val registration = AkkaRegistration(AkkaConnection(ComponentId(name, ComponentType.Service)), prefix, sequenceComponentRef)
    log.info(s"Registering $name with Location Service using registration: [${registration.toString}]")

    val registrationResult = Await.result(locationService.register(registration), 5.seconds)

    coordinatedShutdown.addTask(
      CoordinatedShutdown.PhaseBeforeServiceUnbind,
      s"unregistering-${registrationResult.location}"
    )(() => registrationResult.unregister())
  }

}
