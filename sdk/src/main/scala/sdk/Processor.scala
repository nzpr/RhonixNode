package sdk

final case class Processor[T](processingSet: Set[T]) {

  /** Start processing. */
  def start(x: T): (Processor[T], Boolean) =
    if (processingSet.contains(x))
      this                            -> false
    else
      Processor[T](processingSet + x) -> true

  /** Processing is complete. */
  def done(x: T): Processor[T] = Processor(processingSet - x)
}

object Processor {
  def default[T]: Processor[T] = Processor(Set.empty[T])
}
