package node

import sdk.hashing.Blake2b
import sdk.primitive.ByteArray
import weaver.data.{Bonds, FinalData}

object DummyGenesis {
  def apply(netSize: Int): FinalData[ByteArray] = {
    val rnd               = new scala.util.Random()
    /// Genesis data
    val lazinessTolerance = 1 // c.lazinessTolerance
    val senders           =
      Iterator.range(0, netSize).map(_ => Array(rnd.nextInt().toByte)).map(Blake2b.hash256).map(ByteArray(_)).toSet
    // Create lfs message, it has no parents, sees no offences and final fringe is empty set
    val genesisBonds      = Bonds(senders.map(_ -> 100L).toMap)
    FinalData(genesisBonds, lazinessTolerance, 10000)
  }
}
