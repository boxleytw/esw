package esw.ocs.framework

import akka.actor.typed.ActorRef
import akka.actor.typed.SpawnProtocol.Spawn
import akka.actor.typed.scaladsl.AskPattern.Askable
import csw.command.client.messages.CommandResponseManagerMessage
import csw.command.client.{CRMCacheProperties, CommandResponseManager, CommandResponseManagerActor}
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{AkkaLocation, AkkaRegistration, ComponentId, ComponentType}
import csw.location.api.scaladsl.LocationService
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.api.scaladsl.Logger
import csw.logging.client.scaladsl.LoggerFactory
import esw.ocs.async.macros.StrandEc
import esw.ocs.framework.api.models.messages.SequencerMsg
import esw.ocs.framework.core._
import esw.ocs.framework.core.internal.{ScriptLoader, SequencerConfig}
import esw.ocs.framework.dsl.{CswServices, Script}

import scala.concurrent.Await
import scala.concurrent.duration.DurationLong
import scala.util.Try

class SequencerWiring(val sequencerId: String, val observingMode: String) {

  private lazy val settings: SequencerConfig = new SequencerConfig(sequencerId, observingMode)
  private val actorRuntime                   = new ActorRuntime(settings.name)
  import actorRuntime._

  private lazy val engine        = new Engine()
  private lazy val componentId   = ComponentId(settings.name, ComponentType.Sequencer)
  private lazy val loggerFactory = new LoggerFactory(settings.name)
  private lazy val log: Logger   = loggerFactory.getLogger

  private lazy val crmRef: ActorRef[CommandResponseManagerMessage] =
    Await.result(typedSystem ? Spawn(CommandResponseManagerActor.behavior(CRMCacheProperties(), loggerFactory), "crm"), 5.seconds)
  private lazy val commandResponseManager: CommandResponseManager = new CommandResponseManager(crmRef)

  private lazy val sequencerRef: ActorRef[SequencerMsg] =
    Await.result(typedSystem ? Spawn(SequencerBehavior.behavior(sequencer, script), settings.name), 5.seconds)

  //Pass lambda to break circular dependency shown below.
  //SequencerRef -> Script -> cswServices -> SequencerOperator -> SequencerRef
  private lazy val sequenceOperatorFactory = () => new SequenceOperator(sequencerRef)

  private lazy val cswServices    = new CswServices(sequenceOperatorFactory)
  private lazy val script: Script = new ScriptLoader(sequencerId, observingMode).load(cswServices)
  private lazy val sequencer      = new Sequencer(commandResponseManager)(StrandEc(), timeout)

  private lazy val sequenceEditorClient = new SequenceEditorClient(sequencerRef)

  private lazy val locationService: LocationService = HttpLocationServiceFactory.makeLocalClient

  def shutDown(): Unit = {
    Await.result(locationService.unregister(AkkaConnection(componentId)), 5.seconds)
    Await.result(sequenceEditorClient.shutdown(), 5.seconds)
    typedSystem.terminate()
  }

  def start(): Try[AkkaLocation] = {
    val registration = AkkaRegistration(AkkaConnection(componentId), settings.prefix, sequencerRef)
    log.info(s"Registering ${componentId.name} with Location Service using registration: [${registration.toString}]")

    engine.start(sequenceOperatorFactory(), script)
    Try(
      Await.result(
        locationService
          .register(registration)
          .map(_.location.asInstanceOf[AkkaLocation]),
        5.seconds
      )
    )
  }
}