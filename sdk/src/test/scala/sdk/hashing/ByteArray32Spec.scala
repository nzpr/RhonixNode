package sdk.hashing

import org.scalacheck.ScalacheckShapeless.arbitrarySingletonType
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.Checkers
import sdk.history.ByteArray32
import sdk.primitive.ByteArray
import sdk.syntax.all.*

class ByteArray32Spec extends AnyFlatSpec with Checkers {

  "Method fromByteArray()" should "create Blake2b256Hash from 32 byte array" in {
    val byteArrayGen: Gen[Array[Byte]] = Gen.listOfN(ByteArray32.Length, Arbitrary.arbitrary[Byte]).map(_.toArray)
    val propFromByteArray: Prop        = Prop.forAll(byteArrayGen) { (bytes: Array[Byte]) =>
      val hashTry = ByteArray32.convert(bytes)
      hashTry.isSuccess && hashTry.getUnsafe.bytes == ByteArray(bytes)
    }
    check(propFromByteArray)
  }

  "Object creation when bytes array is not of the correct length" should "fail" in {
    val byteArrayGen: Gen[Array[Byte]] = Gen.choose(0, 100).filterNot(_ == ByteArray32.Length).flatMap { size =>
      Gen.listOfN(size, Arbitrary.arbitrary[Byte]).map(_.toArray)
    }
    val propFromByteArray: Prop        = Prop.forAll(byteArrayGen) { (bytes: Array[Byte]) =>
      ByteArray32.convert(bytes).isFailure
    }
    check(propFromByteArray)
  }
}
