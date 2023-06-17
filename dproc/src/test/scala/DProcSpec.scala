import cats.effect.{IO, Ref}
import cats.syntax.all.*
import dproc.DProc.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sdk.Proposer

class DProcSpec extends AnyFlatSpec with Matchers {

  behavior of "attemptPropose"

  "if proposal succeed" should "move state to Adding" in {
    for {
      stRef <- Ref.of[IO, Proposer](Proposer.default)
      _     <- attemptPropose(stRef, IO.unit)
      state <- stRef.get
    } yield state.status shouldBe Proposer.Adding
  }

  "if proposal is cancelled" should "revert state to Idle" in {
    for {
      stRef <- Ref.of[IO, Proposer](Proposer.default)
      // .start.flatMap(_.join) is a way to just get the outcome of a fiber,
      // since for cancelled fiber there is no return value
      _     <- attemptPropose(stRef, IO.canceled).start.flatMap(_.join)
      state <- stRef.get
    } yield state.status shouldBe Proposer.Idle
  }

  "if proposal is erred" should "revert state to Idle" in {
    for {
      stRef <- Ref.of[IO, Proposer](Proposer.default)
      _     <- attemptPropose(stRef, IO.raiseError(new Exception()))
      state <- stRef.get
    } yield state.status shouldBe Proposer.Idle
  }

  "if proposal succeed" should "return Right(value)" in {
    for {
      stRef <- Ref.of[IO, Proposer](Proposer.default)
      res   <- attemptPropose(stRef, IO.pure(42))
    } yield res shouldBe Right(42)
  }

  behavior of "attemptProcess"

  it should "remove input from processing state regardless of processing result" in {
    // succeed
    for {
      stRef <- Ref.of[IO, sdk.Processor[Int]](sdk.Processor.default)
      _     <- attemptProcess(stRef, 42, IO.unit)
      state <- stRef.get
    } yield state.processingSet shouldBe empty
    // failed
    for {
      stRef <- Ref.of[IO, sdk.Processor[Int]](sdk.Processor.default)
      _     <- attemptProcess(stRef, 42, new Exception().raiseError[IO, Unit])
      state <- stRef.get
    } yield state.processingSet shouldBe empty
    // cancelled
    for {
      stRef <- Ref.of[IO, sdk.Processor[Int]](sdk.Processor.default)
      _     <- attemptProcess(stRef, 42, IO.canceled).start.flatMap(_.join)
      state <- stRef.get
    } yield state.processingSet shouldBe empty
  }

  it should "return Right(value) if process succeed" in {
    for {
      stRef <- Ref.of[IO, sdk.Processor[Int]](sdk.Processor.default)
      res   <- attemptProcess(stRef, 42, 42.pure[IO])
    } yield res shouldBe Right(42)
  }
}
