package io.rhonix.rholang.reducer.env

import sdk.history.ByteArray32

trait RandReader {
  def getCurrentRand: ByteArray32
}
