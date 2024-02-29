package io.rhonix.rholang.normalizer

import cats.effect.Sync
import cats.syntax.all.*
import coop.rchain.rholang.interpreter.compiler.{NameSort, VarSort}
import io.rhonix.rholang.ast.rholang.Absyn.*
import io.rhonix.rholang.normalizer.env.{BoundVarScope, BoundVarWriter}
import io.rhonix.rholang.normalizer.syntax.all.*
import io.rhonix.rholang.types.*

import scala.jdk.CollectionConverters.*

object SendSyncNormalizer {

  /** Normalizes synchronous send by transformation to send/receive pair with generated sync channel.
   *
   * {{{
   *   // Input: Two sync send expressions.
   *   @1!?(2) ; @3!?(4).
   *
   *   // Output: Generated AST with two send/receive pairs.
   *   new varGen1 {
   *     @1!(varGen1, 2) |
   *     for(_ <- varGen1) {
   *       new varGen2 {
   *         @3!(varGen2, 4) |
   *         for(_ <- varGen2) {
   *           Nil
   *         }
   *       }
   *     }
   *   }
   * }}}
   *
   * @param p sync send parser AST object
   * @return transformed [[ParN]] AST object
    */
  def normalizeSendSync[F[_]: Sync: NormalizerRec: BoundVarScope, T >: VarSort: BoundVarWriter](
    p: PSendSynch,
  ): F[NewN] = {
    val normalizedData = for {
      chan         <- NormalizerRec[F].normalize(p.name_)
      data         <- p.listproc_.asScala.toSeq.traverse(NormalizerRec[F].normalize)
      // Receive body is Nil when sync send ends with `.` or normalizes proc after `;`.
      continuation <- p.synchsendcont_ match {
                        case _: EmptyCont               => Sync[F].delay(NilN: ParN)
                        case nonEmptyCont: NonEmptyCont => NormalizerRec[F].normalize(nonEmptyCont.proc_)
                      }
    } yield (chan, data, continuation)

    normalizedData
      .withNewUniqueBoundVars[T](countVars = 1, typeVars = NameSort)
      .map { case ((chan, data, continuation), Seq(varIndex)) =>
        val varGen  = BoundVarN(varIndex)
        // Send on the same channel, but prepend generated name to send data
        val send    = SendN(chan, varGen +: data)
        val bind    = ReceiveBindN(WildcardN, varGen)
        val receive = ReceiveN(bind, continuation, 0)
        NewN(bindCount = 1, p = ParN.combine(send, receive), uri = Seq(), injections = Map[String, ParN]())
      }
  }
}
