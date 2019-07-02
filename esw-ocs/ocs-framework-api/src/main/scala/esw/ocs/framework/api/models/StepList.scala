package esw.ocs.framework.api.models

import csw.params.commands.SequenceCommand
import csw.params.core.models.Id
import esw.ocs.framework.api.models.messages.SequencerMsg.DuplicateIdsFound
import esw.ocs.framework.api.models.messages.StepListActionResponse._
import esw.ocs.framework.api.models.messages._

final case class StepList private[models] (runId: Id, steps: List[Step]) { outer =>
  //query
  // todo: what should we return when StepList is empty?
  def isFinished: Boolean = steps.forall(_.isFinished)
  def isPaused: Boolean   = nextPending.exists(_.hasBreakpoint)
  def isInFlight: Boolean = steps.exists(_.isInFlight)

  def nextPending: Option[Step]    = steps.find(_.isPending)
  def nextExecutable: Option[Step] = if (!isPaused) nextPending else None

  private def toSteps(commands: List[SequenceCommand]): List[Step] = commands.map(Step.apply)

  //update
  def replace(id: Id, commands: List[SequenceCommand]): Either[ReplaceError, StepList] =
    ifExistAndNotFinished(id) { step ⇒
      if (step.isPending) replaceSteps(id, toSteps(commands))
      else Left(ReplaceNotSupported(step.status))
    }

  def prepend(commands: List[SequenceCommand]): Either[PrependError, StepList] = ifNotFinished {
    val (pre, post) = steps.span(!_.isPending)
    Right(copy(runId, pre ::: toSteps(commands) ::: post))
  }

  def append(commands: List[SequenceCommand]): Either[AddError, StepList] =
    ifNotFinished(Right(copy(runId, steps ::: toSteps(commands))))

  def delete(id: Id): Either[DeleteError, StepList] = ifExistAndNotFinished(id) { _ ⇒
    steps
      .foldLeft[Either[DeleteError, List[Step]]](Right(List.empty)) {
        case (acc, step) if step.id == id && step.isPending ⇒ acc
        case (_, step) if step.id == id                     ⇒ Left(DeleteNotSupported(step.status))
        case (acc, step)                                    ⇒ acc.map(_ :+ step)
      }
      .map(steps ⇒ copy(runId, steps))
  }

  def insertAfter(id: Id, commands: List[SequenceCommand]): Either[InsertError, StepList] =
    ifExistAndNotFinished(id) { _ ⇒
      val updatedSteps = insertStepsAfter(id, toSteps(commands))
      Right(copy(runId, updatedSteps))
    }

  def discardPending: Either[DiscardPendingError, StepList] =
    ifNotFinished(Right(copy(runId, steps.filterNot(_.isPending))))

  def addBreakpoint(id: Id): Either[AddBreakpointError, StepList] = ifExistAndNotFinished(id) { _ ⇒
    steps
      .foldLeft[Either[AddBreakpointError, List[Step]]](Right(List.empty)) {
        case (acc, step) if step.id == id ⇒ step.addBreakpoint().flatMap(step ⇒ acc.map(_ :+ step))
        case (acc, step)                  ⇒ acc.map(_ :+ step)
      }
      .map(steps ⇒ copy(runId, steps))
  }

  def removeBreakpoint(id: Id): Either[RemoveBreakpointError, StepList] = ifExistAndNotFinished(id) { _ ⇒
    Right(updateAll(id, _.removeBreakpoint()))
  }

  def pause: Either[PauseError, StepList] =
    ifNotFinished {
      nextPending
        .map(_.addBreakpoint().map(updateStep))
        .getOrElse(Left(PauseFailed))
    }

  def resume: Either[ResumeError, StepList] = ifNotFinished {
    nextPending
      .map(step => Right(updateStep(step.removeBreakpoint())))
      .getOrElse(Right(this))
  }

  // api changed from prototype (single Id instead of Set[Id]), confirm?
  private[framework] def updateStatus(id: Id, stepStatus: StepStatus): Either[UpdateError, StepList] =
    ifExistAndNotFinished(id) { _ ⇒
      steps
        .foldLeft[Either[UpdateError, List[Step]]](Right(List.empty)) {
          case (acc, step) if step.id == id ⇒ step.withStatus(stepStatus).flatMap(step ⇒ acc.map(_ :+ step))
          case (acc, step)                  ⇒ acc.map(_ :+ step)
        }
        .map(steps ⇒ copy(runId, steps))
    }

  private def replaceSteps(id: Id, steps: List[Step]): Either[ReplaceError, StepList] =
    Right(copy(runId, insertStepsAfter(id, steps).filterNot(_.id == id)))

  private def insertStepsAfter(id: Id, newSteps: List[Step]): List[Step] = {
    val (pre, post) = steps.span(_.id != id)
    pre ::: post.headOption.toList ::: newSteps ::: post.tail
  }

  private def updateStep(step: Step) = updateAll(step.id, _ => step)

  private def updateAll(id: Id, f: Step => Step): StepList =
    copy(runId, steps.map {
      case step if id == step.id => f(step)
      case step                  => step
    })

  private def ifNotFinished[T <: StepListActionResponse](f: ⇒ Either[T, StepList]): Either[T, StepList] =
    if (isFinished) Left(NotAllowedOnFinishedSeq.asInstanceOf[T]) else f

  private def ifExists[T <: StepListActionResponse](id: Id)(f: Step ⇒ Either[T, StepList]): Either[T, StepList] =
    steps.find(_.id == id) match {
      case Some(step) ⇒ f(step)
      case None       ⇒ Left(IdDoesNotExist(id).asInstanceOf[T])
    }

  private def ifExistAndNotFinished[T <: StepListActionResponse](id: Id)(f: Step ⇒ Either[T, StepList]): Either[T, StepList] =
    ifExists(id)(step ⇒ ifNotFinished(f(step)))

}

object StepList {

  def empty: StepList = StepList(Id(), List.empty)

  def apply(sequence: Sequence): Either[DuplicateIdsFound.type, StepList] = {
    val steps = sequence.commands.toList.map(Step.apply)

    if (steps.map(_.id).toSet.size == steps.size) Right(StepList(sequence.runId, steps))
    else Left(DuplicateIdsFound)
  }

}
