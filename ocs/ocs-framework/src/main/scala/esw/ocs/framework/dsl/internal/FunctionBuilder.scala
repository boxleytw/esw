package esw.ocs.framework.dsl.internal

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.Try

private[framework] class FunctionBuilder[I, O] {

  private val handlers: mutable.Buffer[PartialFunction[I, O]] = mutable.Buffer.empty

  private def combinedHandler: PartialFunction[I, O] = handlers.foldLeft(PartialFunction.empty[I, O])(_ orElse _)

  def addHandler[T <: I: ClassTag](handler: T => O)(iff: T => Boolean): Unit = handlers += {
    case input: T if iff(input) => handler(input)
  }

  def build(default: I => O): I => Try[O] = input => Try(combinedHandler.lift(input).getOrElse(default(input)))
}