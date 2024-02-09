package io.rhonix.rholang.normalizer.dsl

import coop.rchain.rholang.interpreter.compiler.{IdContext, SourcePosition}
import BoundVarWriterImplSpec.*
import io.rhonix.rholang.normalizer.envimpl.BoundVarWriterImpl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BoundVarWriterImplSpec extends AnyFlatSpec with Matchers {

  "putBoundVars" should "return all indices when no shadowing occurs" in {
    resetIdx()
    val writer   = BoundVarWriterImpl[String](incrementIdx)
    val bindings = Seq(BindA, BindB, BindC)
    val result   = writer.putBoundVars(bindings)
    result shouldBe Seq(1, 2, 3)
  }

  it should "return empty list when no bindings are provided" in {
    resetIdx()
    val writer = BoundVarWriterImpl[String](incrementIdx)
    val result = writer.putBoundVars(Seq.empty)
    result shouldBe Seq.empty
  }

  it should "return indices are those that haven't been shadowed by the new bindings" in {
    val writer = BoundVarWriterImpl[String](incrementIdx)

    resetIdx()
    val bindings1 = Seq(BindA, BindB, BindA)
    val result1   = writer.putBoundVars(bindings1)
    result1 shouldBe Seq(2, 3)

    resetIdx()
    val bindings2 = Seq(BindA, BindB, BindB)
    val result2   = writer.putBoundVars(bindings2)
    result2 shouldBe Seq(1, 3)

    resetIdx()
    val bindings3 = Seq(BindA, BindA, BindA)
    val result3   = writer.putBoundVars(bindings3)
    result3 shouldBe Seq(3)
  }
}

object BoundVarWriterImplSpec {
  private var idx: Int                       = 0
  val resetIdx: () => Unit                   = () => idx = 0
  val incrementIdx: IdContext[String] => Int = _ => { idx += 1; idx }

  private val Pos: SourcePosition             = SourcePosition(0, 0)
  val BindA: (String, String, SourcePosition) = ("a", "typeA", Pos)
  val BindB: (String, String, SourcePosition) = ("b", "typeB", Pos)
  val BindC: (String, String, SourcePosition) = ("c", "typeC", Pos)
}
