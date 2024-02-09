package io.rhonix.rholang.normalizer.dsl

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.rhonix.rholang.normalizer.envimpl.PatternInfoChain
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PatternInfoChainSpec extends AnyFlatSpec with Matchers {

  "PatternInfoChain" should "initialize with default values" in {
    val chain = PatternInfoChain()
    chain.getStatus shouldBe (false, false)
  }

  it should "set the first status flag to true if the scope function runs with inReceive flag" in {
    val chain   = PatternInfoChain()
    val scopeFn = IO {
      chain.getStatus._1 shouldBe true
      chain.getStatus._2 shouldBe false
    }
    chain.runWithNewStatus(inReceive = false)(scopeFn).unsafeRunSync()
  }

  it should "set both status flags to true if the scope function runs with inReceive flag" in {
    val chain   = PatternInfoChain()
    val scopeFn = IO {
      chain.getStatus._1 shouldBe true
      chain.getStatus._2 shouldBe true
    }
    chain.runWithNewStatus(inReceive = true)(scopeFn).unsafeRunSync()
  }

  it should "clear the second status flag if the scope function runs within the nested scope without inReceive flag" in {
    val chain   = PatternInfoChain()
    val scopeFn = IO {
      chain.getStatus._1 shouldBe true
      chain.getStatus._2 shouldBe false
    }
    chain.runWithNewStatus(inReceive = true)(chain.runWithNewStatus(inReceive = false)(scopeFn)).unsafeRunSync()
  }
}
