package esw.ocs.api.protocol

import akka.util.Timeout
import csw.params.core.models.Id

sealed trait SequencerWebsocketRequest

private[ocs] object SequencerWebsocketRequest {
  // Sequencer Command Protocol
  case class QueryFinal(runId: Id, timeout: Timeout) extends SequencerWebsocketRequest
}
