package node

import cats.Parallel
import cats.effect.kernel.Sync
import cats.effect.{Async, Ref}
import cats.syntax.all.*
import dproc.DProc
import dproc.DProc.ExeEngine
import dproc.data.Block
import node.Hashing.*
import node.state.StateManager
import sdk.api.data.Balance
import sdk.crypto.ECDSA
import sdk.data.{BalancesDeploy, BalancesState}
import sdk.diag.Metrics
import sdk.hashing.Blake2b
import sdk.history.{ByteArray32, History}
import sdk.merging.MergeLogicForPayments.mergeRejectNegativeOverflow
import sdk.merging.Relation
import sdk.primitive.ByteArray
import sdk.syntax.all.digestSyntax
import secp256k1.Secp256k1
import slick.api.SlickApi
import weaver.WeaverState
import weaver.data.FinalData

final case class Node[F[_]](
  // inputs and outputs
  dProc: DProc[F, ByteArray, BalancesDeploy],
  // balanceShard
  exeEngine: ExeEngine[F, ByteArray, ByteArray, BalancesDeploy],
  lfsHash: F[ByteArray32],
  buildGenesis: (
    FinalData[ByteArray],
    BalancesState,
  ) => F[Block.WithId[ByteArray, ByteArray, BalancesDeploy]],
  readBalance: (ByteArray32, ByteArray) => F[Option[Balance]],
)

object Node {

  def stream[F[_]](setup: Setup[F]): fs2.Stream[F, Unit] = ???

  val SupportedECDSA: Map[String, ECDSA] = Map("secp256k1" -> Secp256k1.apply)
  val BalancesShardName: String          = "balances"

  def make[F[_]: Async: Parallel: Metrics](
    fringeMappingRef: Ref[F, Map[Set[ByteArray], ByteArray32]],
    state: StateManager[F],
    id: ByteArray,
    balancesShard: BalancesStateBuilderWithReader[F],
    lfs: WeaverState[ByteArray, ByteArray, BalancesDeploy],
    database: SlickApi[F],
  ): F[Node[F]] = {
    val exeEngine = new ExeEngine[F, ByteArray, ByteArray, BalancesDeploy] {
      def execute(
        base: Set[ByteArray],
        fringe: Set[ByteArray],
        toFinalize: Set[BalancesDeploy],
        toMerge: Set[BalancesDeploy],
        txs: Set[BalancesDeploy],
      ): F[((ByteArray, Seq[BalancesDeploy]), (ByteArray, Seq[BalancesDeploy]))] =
        for {
          baseState <- fringeMappingRef.get.map(_.getOrElse(base, History.EmptyRootHash))

          r <- mergeRejectNegativeOverflow(
                 balancesShard.readBalance,
                 baseState,
                 toFinalize,
                 toMerge ++ txs,
               )

          ((newFinState, finRj), (newMergeState, provRj)) = r

          r <- balancesShard.buildState(baseState, newFinState, newMergeState)

          (finalHash, postHash) = r

          _ <- fringeMappingRef.update(_ + (fringe -> finalHash)).unlessA(fringe.isEmpty)
        } yield ((finalHash.bytes, finRj), (postHash.bytes, provRj))

      // data read from the final state associated with the final fringe
      def consensusData(fringe: Set[ByteArray]): F[FinalData[ByteArray]] =
        lfs.lazo.trustAssumption.pure[F] // TODO
    }

    for {
      dProc <- DProc.apply[F, ByteArray, ByteArray, BalancesDeploy](
                 state.weaverStRef,
                 state.propStRef,
                 state.procStRef,
                 state.bufferStRef,
                 Set.empty[BalancesDeploy].pure,
                 id.some,
                 exeEngine,
                 Relation.notRelated[F, BalancesDeploy],
                 b => Sync[F].delay(ByteArray(Blake2b.hash256(b.digest.bytes))),
                 DbApiImpl(database).saveBlock,
                 DbApiImpl(database).readBlock(_).flatMap(_.liftTo[F](new Exception("Block not found"))),
               )

      lfsHashF     = (fringeMappingRef.get, state.weaverStRef.get).mapN { case (fM, w) =>
                       w.lazo.fringes
                         .minByOption { case (i, _) => i }
                         .map { case (_, fringe) => fringe }
                         .flatMap(fM.get)
                         .getOrElse(History.EmptyRootHash)
                     }
      buildGenesis = Genesis.mkGenesisBlock[F](id, _: FinalData[ByteArray], _: BalancesState, balancesShard)
      readBalance  = balancesShard.readBalance(_: ByteArray32, _: ByteArray).map(_.map(new Balance(_)))
    } yield Node(dProc, exeEngine, lfsHashF, buildGenesis, readBalance)
  }
}
