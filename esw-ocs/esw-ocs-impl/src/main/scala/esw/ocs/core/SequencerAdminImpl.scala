package esw.ocs.core

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import csw.command.client.messages.sequencer.SequencerMsg
import csw.params.commands.SequenceCommand
import csw.params.core.models.Id
import esw.ocs.api.SequencerAdminApi
import esw.ocs.api.models.StepList
import esw.ocs.core.messages.SequencerMessages._
import esw.ocs.api.models.responses._
import esw.ocs.core.messages.SequencerState

import scala.concurrent.Future

class SequencerAdminImpl(sequencer: ActorRef[EswSequencerMessage])(implicit system: ActorSystem[_], timeout: Timeout)
    extends SequencerAdminApi {
  private implicit val scheduler: Scheduler = system.scheduler

  override def getSequence: Future[Option[StepList]] = sequencer ? GetSequence
  def getState: Future[SequencerState[SequencerMsg]] = sequencer ? GetSequencerState

  override def add(commands: List[SequenceCommand]): Future[OkOrUnhandledResponse]       = sequencer ? (Add(commands, _))
  override def prepend(commands: List[SequenceCommand]): Future[OkOrUnhandledResponse]   = sequencer ? (Prepend(commands, _))
  override def replace(id: Id, commands: List[SequenceCommand]): Future[GenericResponse] = sequencer ? (Replace(id, commands, _))

  override def insertAfter(id: Id, commands: List[SequenceCommand]): Future[GenericResponse] =
    sequencer ? (InsertAfter(id, commands, _))

  override def delete(id: Id): Future[GenericResponse]                    = sequencer ? (Delete(id, _))
  override def pause: Future[PauseResponse]                               = sequencer ? Pause
  override def resume: Future[OkOrUnhandledResponse]                      = sequencer ? Resume
  override def addBreakpoint(id: Id): Future[GenericResponse]             = sequencer ? (AddBreakpoint(id, _))
  override def removeBreakpoint(id: Id): Future[RemoveBreakpointResponse] = sequencer ? (RemoveBreakpoint(id, _))
  override def reset(): Future[OkOrUnhandledResponse]                     = sequencer ? Reset
  override def abortSequence(): Future[OkOrUnhandledResponse]             = sequencer ? AbortSequence
  override def goOnline(): Future[GoOnlineResponse]                       = sequencer ? GoOnline
  override def goOffline(): Future[OkOrUnhandledResponse]                 = sequencer ? GoOffline

}