package io.rhonix.rholang.normalizer.dsl

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.rhonix.rholang.normalizer.envimpl.BundleInfoChain
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BundleInfoChainSpec extends AnyFlatSpec with Matchers {

  "BundleInfoChain" should "initialize with default status" in {
    val chain = BundleInfoChain()
    chain.getStatus shouldBe false
  }

  it should "set the status to true if the scope function runs" in {
    val chain   = BundleInfoChain()
    val scopeFn = IO {
      chain.getStatus shouldBe true
    }
    chain.runWithNewStatus(scopeFn).unsafeRunSync()
  }

  it should "maintain the status as true if the scope function runs within the nested scope" in {
    val chain   = BundleInfoChain()
    val scopeFn = IO {
      chain.getStatus shouldBe true
    }
    chain.runWithNewStatus(chain.runWithNewStatus(scopeFn)).unsafeRunSync()
  }
}
