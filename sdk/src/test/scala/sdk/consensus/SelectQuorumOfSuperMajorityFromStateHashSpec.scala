package sdk.consensus

import org.scalacheck.Arbitrary
import org.scalacheck.ScalacheckShapeless.derivedArbitrary
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import sdk.primitive.ByteArray

class SelectQuorumOfSuperMajorityFromStateHashSpec extends AsyncFlatSpec with Matchers with ScalaCheckPropertyChecks {
  "next" should "be deterministic" in {
    implicit val arbHash: Arbitrary[ByteArray] = Arbitrary {
      Arbitrary.arbitrary[Array[Byte]].map(ByteArray(_))
    }

    forAll { (bonds: Map[Int, Long], stateHash: ByteArray) =>
      SelectQuorumOfSuperMajorityFromStateHash(bonds).next(stateHash) shouldEqual
        SelectQuorumOfSuperMajorityFromStateHash(bonds).next(stateHash)
    }
  }
}
