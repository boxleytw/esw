package esw.integration.test.ocs.framework.core

import akka.actor.testkit.typed.scaladsl.ActorTestKitBase
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.http.scaladsl.Http
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.{Done, actor}
import csw.params.commands.CommandResponse.Completed
import csw.params.commands.{CommandName, Setup}
import csw.params.core.models.Prefix
import csw.testkit.LocationTestKit
import esw.ocs.framework.api.models.StepStatus.Finished
import esw.ocs.framework.api.models.{Sequence, Step}
import esw.ocs.framework.{BaseTestSuite, SequencerWiring}

class SequencerTest extends ActorTestKitBase with BaseTestSuite {
  private val locationTestKit                        = LocationTestKit()
  implicit val untypedActorSystem: actor.ActorSystem = system.toUntyped
  implicit val mat: ActorMaterializer                = ActorMaterializer()
  var wiring: SequencerWiring                        = _

  override def beforeAll(): Unit = {
    locationTestKit.startLocationServer()
  }

  override protected def beforeEach(): Unit = {
    wiring = new SequencerWiring("testSequencerId1", "testObservingMode1")
    wiring.start()
  }

  override protected def afterEach(): Unit = {
    wiring.shutDown()
  }

  override def afterAll(): Unit = {
    Http().shutdownAllConnectionPools().await
    locationTestKit.shutdownLocationServer()
    system.terminate()
    system.whenTerminated.await
  }

  "Sequencer" must {
    "process a given sequence" in {

      val command1 = Setup(Prefix("test"), CommandName("command-1"), None)
      val sequence = Sequence(command1)

      val processSeqResponse = wiring.sequencer.processSequence(sequence)

      processSeqResponse.rightValue should ===(Completed(sequence.runId))

      wiring.sequencer.getSequence.futureValue.steps should ===(
        List(Step(command1, Finished.Success(Completed(command1.runId)), hasBreakpoint = false))
      )
    }

    "process sequence and execute commands that are added later" in {
      val command1 = Setup(Prefix("test"), CommandName("command-1"), None)
      val command2 = Setup(Prefix("test"), CommandName("command-2"), None)

      val sequence = Sequence(command1)

      val processSeqResponse = wiring.sequencer.processSequence(sequence)

      wiring.sequenceEditorClient.add(List(command2)).rightValue should ===(Done)

      processSeqResponse.rightValue should ===(Completed(sequence.runId))

      wiring.sequencer.getSequence.futureValue.steps should ===(
        List(
          Step(command1, Finished.Success(Completed(command1.runId)), hasBreakpoint = false),
          Step(command2, Finished.Success(Completed(command2.runId)), hasBreakpoint = false)
        )
      )
    }
  }
}