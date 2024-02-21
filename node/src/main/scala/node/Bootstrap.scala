package node

import sdk.data.BalancesDeploy
import sdk.primitive.ByteArray
import weaver.WeaverState
import weaver.data.FinalData

object Bootstrap {
  def const(finalState: FinalData[ByteArray]): WeaverState[ByteArray, ByteArray, BalancesDeploy] =
    WeaverState.empty[ByteArray, ByteArray, BalancesDeploy](finalState)
}
