package coop.rchain.rholang.interpreter

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import coop.rchain.metrics
import coop.rchain.metrics.{Metrics, NoopSpan, Span}
import coop.rchain.models.Expr.ExprInstance.GString
import coop.rchain.models.rholang.implicits.*
import coop.rchain.rholang.Resources.mkRuntime
import coop.rchain.rholang.interpreter.errors.InterpreterError
import coop.rchain.rholang.syntax.*
import coop.rchain.shared.Log
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.duration.*

class VarRefSpec extends AnyFlatSpec with ScalaCheckPropertyChecks with Matchers {
  import VarRefSpec.*
  "Rholang VarRef" should "match in single level depth pattern" in {
    val term =
      s"""
         #  match 42 {
         #    x => {
         #      match 42 {
         #        =x => @"${outcomeCh}"!(true)
         #        _  => @"${outcomeCh}"!(false)
         #      }
         #    }
         #  }
         # """.stripMargin('#')
    testExecute(term).unsafeRunSync() should equal(Right(true))
  }

  it should "match in two level depth pattern" in {
    val term =
      s"""
         #  match 42 {
         #    x => {
         #      match {for(@42 <- @Nil) {Nil}} {
         #        {for(@{=x} <- @Nil) { Nil }} => @"${outcomeCh}"!(true)
         #        _                            => @"${outcomeCh}"!(false)
         #      }
         #    }
         #  }
         # """.stripMargin('#')
    testExecute(term).unsafeRunSync() should equal(Right(true))
  }
}

object VarRefSpec {
  implicit val logF: Log[IO]            = Log.log[IO]
  implicit val noopMetrics: Metrics[IO] = new metrics.Metrics.MetricsNOP[IO]
  implicit val noopSpan: Span[IO]       = NoopSpan[IO]()
  private val maxDuration               = 5.seconds

  val outcomeCh      = "ret"
  val reduceErrorMsg = "Error: index out of bound: -1"

  private def testExecute(source: String): IO[Either[InterpreterError, Boolean]] =
    mkRuntime[IO]("rholang-variable-reference")
      .use { runtime =>
        for {
          evalResult <- runtime.evaluate(source)
          result     <- if (evalResult.errors.isEmpty)
                          for {
                            data      <- runtime.getData(GString(outcomeCh)).map(_.head)
                            boolResult = data.a.pars.head.exprs.head.getGBool
                          } yield Right(boolResult)
                        else IO.pure(Left(evalResult.errors.head))
        } yield result
      }
      .timeout(maxDuration)
}
