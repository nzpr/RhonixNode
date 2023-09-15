package sdk.data

import blakehash.Blake2b256
import org.scalacheck.Prop
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.scalacheck.Checkers

import java.util.Arrays

class Blake2b256HashTests extends AnyFlatSpec with Checkers {

  "The bytes of a Blake2b256 hash" should "be the same as if it was created directly" in {

    // noinspection ReferenceMustBePrefixed
    val propCreate: Prop = Prop.forAll { (bytes: Array[Byte]) =>
      Arrays.equals(Blake2b256.hash(bytes), Blake2b256Hash.create(bytes).bytes.toArray)
    }

    check(propCreate)
  }

  "Method fromByteArray()" should "create Blake2b256Hash from 32 byte array" in {
    val arr: Array[Byte] = Array.fill(Blake2b256Hash.length)(0x00.toByte)
    val hashTry          = Blake2b256Hash.fromByteArray(arr)
    hashTry.get.bytes.toArray shouldBe arr
  }

  "Method fromByteArray()" should "throw an exception from a 31-byte array" in {
    val arr: Array[Byte] = Array.fill(Blake2b256Hash.length - 1)(0x00.toByte)
    val hashTry          = Blake2b256Hash.fromByteArray(arr)
    val result           = intercept[Exception](hashTry.get)
    result.getMessage shouldBe s"Expected ${Blake2b256Hash.length} but got ${arr.length}"
  }

  // TODO: commented because needs legacy RSpace dependencies
//  "A Blake2b256 hash" should "be the same when round-tripped with scodec" in {
//
//    val propRoundTripCodec: Prop = Prop.forAll { (hash: Blake2b256Hash) =>
//      roundTripCodec[Blake2b256Hash](hash)
//        .map((value: DecodeResult[Blake2b256Hash]) => value.value == hash)
//        .getOrElse(default = false)
//    }
//
//    check(propRoundTripCodec)
//  }
}
