package sim.balances

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sdk.hashing.Blake2b256Hash
import sdk.history.History.EmptyRootHash
import sdk.store.{ByteArrayKeyValueTypedStore, InMemoryKeyValueStore}
import sim.balances.BalancesStateBuilderWithReaderSpec.witSut
import sim.balances.data.State

class BalancesStateBuilderWithReaderSpec extends AnyFlatSpec with Matchers {

  it should "build correct values for final and post state" in {
    witSut { bb =>
      val toFinalize = new State(Map(1 -> 10, 2 -> 10, 3 -> 10))
      val toMerge    = new State(Map(1 -> 1L, 2 -> 3L))

      for {
        h1      <- bb.buildState(EmptyRootHash, toFinalize, toMerge)
        (f1, p1) = h1

        finalState = toFinalize.diffs.toList
        postState  = (toFinalize ++ toMerge).diffs.toList

        _ <- finalState.traverse { case (k, v) => bb.readState(f1, k).map(_.get shouldBe v) }
        _ <- postState.traverse { case (k, v) => bb.readState(p1, k).map(_.get shouldBe v) }
      } yield ()
    }
  }

  "Attempt to commit negative balance" should "raise an error" in {
    val r = witSut { bb =>
      val toFinalize = new State(Map(1 -> -1))
      bb.buildState(EmptyRootHash, toFinalize, State.Default).attempt
    }
    r.swap.toOption.isDefined shouldBe true
  }
}

object BalancesStateBuilderWithReaderSpec {

  def witSut[A](f: BalancesStateBuilderWithReader[IO] => IO[A]): A = {
    val mkHistory     = sdk.history.History.create(EmptyRootHash, new InMemoryKeyValueStore[IO])
    val mkValuesStore = IO.delay {
      new ByteArrayKeyValueTypedStore[IO, Blake2b256Hash, Balance](
        new InMemoryKeyValueStore[IO],
        Blake2b256Hash.codec,
        datumCodec,
      )
    }

    (mkHistory, mkValuesStore)
      .flatMapN { case history -> valueStore => f(BalancesStateBuilderWithReader(history, valueStore)) }
      .unsafeRunSync()
  }
}
