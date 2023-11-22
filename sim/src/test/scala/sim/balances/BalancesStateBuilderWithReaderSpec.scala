package sim.balances

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import node.hashing.Blake2b
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sdk.diag.Metrics
import sdk.history.ByteArray32
import sdk.history.History.EmptyRootHash
import sdk.primitive.ByteArray
import sdk.store.{ByteArrayKeyValueTypedStore, InMemoryKeyValueStore}
import sdk.syntax.all.sdkSyntaxTry
import sim.balances.BalancesStateBuilderWithReaderSpec.*
import sim.balances.data.BalancesState

class BalancesStateBuilderWithReaderSpec extends AnyFlatSpec with Matchers {

  it should "build correct values for final and post state" in {
    witSut { bb =>
      val toFinalize = new BalancesState(
        Map(
          ByteArray(Array(1.byteValue)) -> 10,
          ByteArray(Array(2.byteValue)) -> 10,
          ByteArray(Array(3.byteValue)) -> 10,
        ),
      )
      val toMerge    = new BalancesState(Map(ByteArray(Array(1.byteValue)) -> 1L, ByteArray(Array(2.byteValue)) -> 3L))

      for {
        h1      <- bb.buildState(EmptyRootHash, toFinalize, toMerge)
        (f1, p1) = h1

        finalState = toFinalize.diffs.toList
        postState  = (toFinalize ++ toMerge).diffs.toList

        _ <- finalState.traverse { case (k, v) => bb.readBalance(f1, k).map(_.get shouldBe v) }
        _ <- postState.traverse { case (k, v) => bb.readBalance(p1, k).map(_.get shouldBe v) }
      } yield ()
    }
  }

  "Attempt to commit negative balance" should "raise an error" in {
    val r = witSut { bb =>
      val toFinalize = new BalancesState(Map(ByteArray(Array(1.byteValue)) -> -1))
      bb.buildState(EmptyRootHash, toFinalize, BalancesState.Default).attempt
    }
    r.swap.toOption.isDefined shouldBe true
  }
}

object BalancesStateBuilderWithReaderSpec {

  implicit def blake2b256Hash(x: Array[Byte]): ByteArray32 = ByteArray32.convert(Blake2b.hash256(x)).getUnsafe

  def witSut[A](f: BalancesStateBuilderWithReader[IO] => IO[A]): A = {
    val mkHistory     = sdk.history.History.create(EmptyRootHash, new InMemoryKeyValueStore[IO])
    val mkValuesStore = IO.delay {
      new ByteArrayKeyValueTypedStore[IO, ByteArray32, Balance](
        new InMemoryKeyValueStore[IO],
        ByteArray32.codec,
        balanceCodec,
      )
    }

    implicit val m: Metrics[IO] = Metrics.unit[IO]

    (mkHistory, mkValuesStore)
      .flatMapN { case history -> valueStore => f(BalancesStateBuilderWithReader(history, valueStore)) }
      .unsafeRunSync()
  }
}
