package weaver.rules

import cats.syntax.all.*

object Dag {
  private def edge[M, S](
    fringes: Iterable[Iterable[M]],
    sorting: (M, M) => Boolean,
    sender: M => S,
  ): Set[M] = {
    val r = fringes.flatten.iterator
//      .map(x => sender(x) -> x)
      // TODO this try catch is because justifications can be already garbage collected so no way to find out sender
      //  this happens so this is valid case or maybe bug ???
      .map(x =>
        try (sender(x) -> x).some
        catch { case _: Throwable => None },
      )
      .collect { case Some(x) => x }
      .foldLeft(Map.empty[S, M]) { case (acc, (s, m)) =>
        if (acc.get(s).forall(curM => sorting(m, curM))) acc + (s -> m) else acc
      }
      .valuesIterator
      .toSet
//    println(s"edge:  $r across ${fringes.mkString("\n ")}")
    r
  }

  /** Highest messages across number of fringes. */
  def ceiling[M, S](
    fringes: Iterable[Iterable[M]],
    isSelfDescendant: (M, M) => Boolean,
    sender: M => S,
  ): Set[M] = edge(fringes, isSelfDescendant, sender)

  /** Lowest messages across number of fringes. */
  def floor[M, S](
    fringes: Iterable[Iterable[M]],
    isSelfDescendant: (M, M) => Boolean,
    sender: M => S,
  ): Set[M] = edge(fringes, (x: M, y: M) => isSelfDescendant(y, x), sender)

  def computeFJS[M, S](
    mgjs: Set[M],
    bonded: Set[S],
    jsF: M => Set[M],
    isSelfDescendant: (M, M) => Boolean,
    senderF: M => S,
  ): Set[M] = {
    assert(mgjs.forall(j => bonded.contains(senderF(j))), "Senders of MGJS should be bonded.")
    val x = (mgjs ++ mgjs.flatMap(jsF))
      .groupBy(senderF)
      .values
      .map(t => t.find(x => (t - x).forall(isSelfDescendant(x, _))))
    assert(x.forall(_.isDefined), s"Unable to compute full justifications $x")
    x.flatten.filter(v => bonded.contains(senderF(v))).toSet
  }

  /** Minimal generative justification set. Subset of target set fully defining the view of the target set. */
  def computeMGJS[M](
    justifications: Set[M],
    seen: (M, M) => Boolean,
  ): Set[M] = justifications.foldLeft(justifications) { case (acc, x) =>
    acc -- justifications.filter(seen(x, _))
  }

  def between[M](ceiling: Set[M], floor: Set[M], seen: M => Set[M]): Set[M] =
    ceiling.flatMap(seen) ++ ceiling -- floor.flatMap(seen)

  // Get items between, including ceil excluding floor
  def between[M, S](
    ceil: Set[M],
    floor: Set[M],
    seqWithSender: M => (S, Int),
    lookup: (S, Int) => Option[M],
  ): Iterator[M] = {
    val c = ceil.map(seqWithSender).toList.toMap
    val f = floor.map(seqWithSender).toList.toMap
    val r = c.iterator.flatMap { case (k, up) =>
      f.get(k)
        .map { bottom =>
          if (bottom == up) Iterator.empty[(S, Int)] // if bottom item == up item, discard
          else (bottom + 1 to up).reverseIterator.map(k -> _)
        }
        .getOrElse((0 to up).reverseIterator.map(k -> _))

    }
    r.flatMap(lookup.tupled)
  }
}
