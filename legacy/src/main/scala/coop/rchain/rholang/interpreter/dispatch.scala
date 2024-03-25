package coop.rchain.rholang.interpreter

import cats.Parallel
import cats.effect.{Ref, Sync}
import coop.rchain.crypto.hash.Blake2b512Random
import coop.rchain.models.TaggedContinuation.TaggedCont.{Empty, ParBody, ScalaBodyRef}
import coop.rchain.models._
import coop.rchain.rholang.interpreter.accounting.CostAccounting.CostStateRef
import coop.rchain.rholang.interpreter.RhoRuntime.RhoTuplespace

trait Dispatch[M[_], B, K] {
  def dispatch(continuation: K, dataList: Seq[B]): M[Unit]
}

object Dispatch {
  def buildEnv(dataList: Seq[MatchedParsWithRandom]): Env[Par] = {
    val envMap = dataList.flatMap(_.pars).toMap             // Merge all maps into one
    // Sanity check to ensure that there are no duplicate keys in the environment
    // NOTE: It should never happen in the code, so we can use a simple assert.
    assert(
      dataList.map(_.pars.size).sum == envMap.size,
      "Duplicate indices in env during dispatching" + envMap.toString(),
    )
    val maxIdx = if (envMap.isEmpty) 0 else envMap.keys.max // Find max De Bruijn index
    new Env[Par](envMap, level = maxIdx + 1, shift = 0)
  }
}

class RholangAndScalaDispatcher[M[_]] private (
  _dispatchTable: => Map[Long, Seq[ListParWithRandom] => M[Unit]],
)(implicit s: Sync[M], reducer: Reduce[M])
    extends Dispatch[M, MatchedParsWithRandom, TaggedContinuation] {

  private def toListParWithRandom(ps: MatchedParsWithRandom): ListParWithRandom =
    ListParWithRandom(ps.pars.toList.sortBy(_._1).map(_._2), ps.randomState)

  def dispatch(
    continuation: TaggedContinuation,
    dataList: Seq[MatchedParsWithRandom],
  ): M[Unit] =
    continuation.taggedCont match {
      case ParBody(parWithRand) =>
        val env     = Dispatch.buildEnv(dataList)
        val randoms = parWithRand.randomState +: dataList.toVector.map(_.randomState)
        reducer.eval(parWithRand.body)(env, Blake2b512Random.merge(randoms))
      case ScalaBodyRef(ref)    =>
        _dispatchTable.get(ref) match {
          case Some(f) => f(dataList.map(toListParWithRandom))
          case None    => s.raiseError(new Exception(s"dispatch: no function for $ref"))
        }
      case Empty                =>
        s.unit
    }
}

object RholangAndScalaDispatcher {
  type RhoDispatch[F[_]] = Dispatch[F, MatchedParsWithRandom, TaggedContinuation]

  def apply[M[_]: Sync: Parallel: CostStateRef](
    tuplespace: RhoTuplespace[M],
    dispatchTable: => Map[Long, Seq[ListParWithRandom] => M[Unit]],
    urnMap: Map[String, Par],
    mergeChs: Ref[M, Set[Par]],
    mergeableTagName: Par,
  ): (Dispatch[M, MatchedParsWithRandom, TaggedContinuation], Reduce[M]) = {

    implicit lazy val dispatcher: Dispatch[M, MatchedParsWithRandom, TaggedContinuation] =
      new RholangAndScalaDispatcher(dispatchTable)

    implicit lazy val reducer: Reduce[M] =
      new DebruijnInterpreter[M](tuplespace, dispatcher, urnMap, mergeChs, mergeableTagName)

    (dispatcher, reducer)
  }

}
