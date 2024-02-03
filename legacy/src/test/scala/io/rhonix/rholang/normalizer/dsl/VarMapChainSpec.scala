package io.rhonix.rholang.normalizer.dsl

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import coop.rchain.rholang.interpreter.compiler.SourcePosition
import VarMapChainSpec.*
import io.rhonix.rholang.normalizer.envimpl.{VarMap, VarMapChain}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VarMapChainSpec extends AnyFlatSpec with Matchers {

  "Empty VarMapChain " should "only contain one empty varMap" in {
    val varMapChain = VarMapChain.empty[IO, String]
    val result      = varMapChain.iter.toSeq
    result shouldBe Seq(EmptyMap)
  }

  "VarMapChain" should "return all variables from the current (last) variable map" in {
    val chain  = VarMapChain[IO, String](Seq(InitMap1, InitMap2))
    val result = chain.getAllInScope
    result shouldBe InitMap2.getAll
  }

  it should "add a new variable to the current (last) variable map" in {
    val NewName = "newVar"
    val chain   = VarMapChain[IO, String](Seq(InitMap1, InitMap2))
    val _       = chain.putVar(NewName, Sort, Pos)
    val result  = chain.iter.toSeq
    result shouldBe Seq(InitMap1, InitMap2.put(NewName, Sort, Pos))
  }

  it should "return the correct index of the added variable" in {
    val NewName     = "newVar"
    val chain       = VarMapChain[IO, String](Seq(InitMap1, InitMap2))
    val idx         = chain.putVar(NewName, Sort, Pos)
    val expectedIdx = InitMap2.put(NewName, Sort, Pos).get(NewName).get.index
    idx shouldBe expectedIdx
  }

  it should "retrieve a variable from the current variable map" in {
    val NewName     = "newVar"
    val initMap2New = InitMap2.put(NewName, Sort, Pos)
    val chain       = VarMapChain[IO, String](Seq(InitMap1, initMap2New))
    val result      = chain.getVar(NewName)
    result shouldBe initMap2New.get(NewName)
  }

  it should "return None if the variable does not exist in the last var map" in {
    val Name   = "NotExistingName"
    val chain  = VarMapChain[IO, String](Seq(InitMap1.put(Name, Sort, Pos), InitMap2))
    val result = chain.getVar(Name)
    result shouldBe None
  }

  it should "search for a variable in the chain of variable maps and return the first match along with its depth" in {
    val NewName1    = "newVar1"
    val NewName2    = "newVar2"
    val InitMap1New = InitMap1.put(NewName1, Sort, Pos)
    val InitMap2New = InitMap2.put(NewName2, Sort, Pos)
    val chain       = VarMapChain[IO, String](Seq(InitMap1New, InitMap2New))
    val result1     = chain.getFirstVarInChain(NewName1)
    val result2     = chain.getFirstVarInChain(NewName2)
    result1 shouldBe Some(InitMap1New.get(NewName1).get, 0)
    result2 shouldBe Some(InitMap2New.get(NewName2).get, 1)
  }

  it should "return None if the variable is not found in the chain" in {
    val chain  = VarMapChain[IO, String](Seq(InitMap1, InitMap2))
    val result = chain.getFirstVarInChain("NotExistingName")
    result shouldBe None
  }

  it should "run a scope function with a new, empty variable map" in {
    val chain   = VarMapChain[IO, String](Seq(InitMap1, InitMap2))
    val scopeFn = IO(chain.iter.toSeq shouldBe Seq(InitMap1, InitMap2, EmptyMap))

    // Execute the scope function
    chain.withNewScope(scopeFn).unsafeRunSync()
  }

  it should "run a scope function with a copy of the current variable map." in {
    val chain   = VarMapChain[IO, String](Seq(InitMap1, InitMap2))
    val scopeFn = IO(chain.iter.toSeq shouldBe Seq(InitMap1, InitMap2, InitMap2))

    // Execute the scope function
    chain.withCopyScope(scopeFn).unsafeRunSync()
  }

}

object VarMapChainSpec {
  val Pos: SourcePosition = SourcePosition(0, 0)
  val Sort: String        = "testSort"

  val InitMap1: VarMap[String] = VarMap(
    Seq(("testVar11", Sort, Pos), ("testVar12", Sort, Pos), ("testVar13", Sort, Pos)),
  )
  val InitMap2: VarMap[String] = VarMap(
    Seq(("testVar21", Sort, Pos), ("testVar22", Sort, Pos), ("testVar23", Sort, Pos)),
  )

  val EmptyMap: VarMap[String] = VarMap.empty[String]
}
