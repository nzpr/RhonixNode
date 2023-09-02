package coop.rchain.rholang

import coop.rchain.models.PrettyPrinted
import coop.rchain.rholang.interpreter.compiler.Compiler
import io.rhonix.rholang.ast.rholang.Absyn._
import io.rhonix.rholang.ast.rholang.PrettyPrinter
import cats.Eval
import org.scalacheck.Arbitrary
import org.scalacheck.Test.Parameters
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import coop.rchain.catscontrib.effect.implicits.sEval

class ProcGenTest extends AnyFlatSpec with ScalaCheckPropertyChecks with Matchers {
  implicit val params: Parameters = Parameters.defaultVerbose.withMinSuccessfulTests(1000)
  implicit val procArbitrary      = Arbitrary(
    ProcGen.topLevelGen(5).map(PrettyPrinted[Proc](_, PrettyPrinter.print)),
  )
  behavior of "Proc generator"

  it should "generate correct procs that are normalized successfully" in {

    forAll { p: PrettyPrinted[Proc] =>
      Compiler[Eval].astToADT(p.value)
    }
  }

  behavior of "Proc shrinker"

  it should "produce a correct Proc" in {
    forAll { original: PrettyPrinted[Proc] =>
      ProcGen.procShrinker
        .shrink(original.value)
        .headOption
        .forall(shrinked => Compiler[Eval].astToADT(shrinked).map(_ => true).value)
    }
  }

  it should "produce a shorter Proc when pretty printed" in {
    def length(p: Proc) = PrettyPrinter.print(p).length

    forAll { original: PrettyPrinted[Proc] =>
      ProcGen.procShrinker
        .shrink(original.value)
        .headOption
        .forall(shrinked => length(original.value) >= length(shrinked))
    }
  }
}
