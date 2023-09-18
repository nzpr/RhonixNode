package sdk.hashing

import org.scalacheck.ScalacheckShapeless.arbitrarySingletonType
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.Checkers
import sdk.hashing.Blake2b256Hash.codec
import sdk.primitive.ByteArray
import sdk.syntax.all.sdkSyntaxTry

import java.util.Arrays

class Blake2b256HashTests extends AnyFlatSpec with Checkers {

  "The bytes of a Blake2b256 hash" should "be the same as if it was created directly" in {
    // noinspection ReferenceMustBePrefixed
    val propCreate: Prop = Prop.forAll { (bytes: Array[Byte]) =>
      Arrays.equals(Blake2b256.hash(bytes), Blake2b256Hash.create(bytes).bytes.toArray)
    }
    check(propCreate)
  }

  "A Blake2b256 hash" should "be the same when round-tripped with scodec" in {
    val propRoundTripCodec: Prop = Prop.forAll { (bytes: Array[Byte]) =>
      val hash    = Blake2b256Hash.create(bytes)
      val encoded = codec.encode(hash)
      val decoded = codec.decode(encoded.getUnsafe)
      hash.bytes == decoded.getUnsafe.bytes
    }
    check(propRoundTripCodec)
  }

  "Method fromByteArray()" should "create Blake2b256Hash from 32 byte array" in {
    val byteArrayGen: Gen[Array[Byte]] = Gen.listOfN(Blake2b256Hash.Length, Arbitrary.arbitrary[Byte]).map(_.toArray)
    val propFromByteArray: Prop        = Prop.forAll(byteArrayGen) { (bytes: Array[Byte]) =>
      val hashTry = Blake2b256Hash.fromByteArray(bytes)
      hashTry.get.bytes == ByteArray(bytes)
    }
    check(propFromByteArray)
  }

  "Object creation when bytes array is not of the correct length" should "fail" in {
    val byteArrayGen: Gen[Array[Byte]] = Gen.choose(0, 100).filterNot(_ == Blake2b256Hash.Length).flatMap { size =>
      Gen.listOfN(size, Arbitrary.arbitrary[Byte]).map(_.toArray)
    }
    val propFromByteArray: Prop        = Prop.forAll(byteArrayGen) { (bytes: Array[Byte]) =>
      Blake2b256Hash.fromByteArray(bytes).isFailure
    }
    check(propFromByteArray)
  }
}
