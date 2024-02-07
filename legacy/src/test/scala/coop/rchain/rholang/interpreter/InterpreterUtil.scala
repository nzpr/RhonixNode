package coop.rchain.rholang.interpreter

import cats.effect.Sync
import cats.syntax.all._
import coop.rchain.rholang.syntax._
import org.scalatest.matchers.should.Matchers._

object InterpreterUtil {
  def evaluate[F[_]: Sync](runtime: RhoRuntime[F], term: String): F[Unit] =
    runtime.evaluate(term).map {
      withClue(s"Evaluate was called and failed with: ") {
        _.errors shouldBe empty
      }
    }
}
