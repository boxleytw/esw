package esw.ocs.framework.dsl.internal

import scala.collection.mutable

private[framework] class FunctionHandlers[I, O] {
  private val handlers: mutable.Buffer[I ⇒ O] = mutable.Buffer.empty

  def add(handler: I ⇒ O): Unit = handlers += handler

  def execute(input: I): List[O] = handlers.map(f ⇒ f(input)).toList
}
