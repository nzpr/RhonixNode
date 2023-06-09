package io.rhonix.node

import dproc.Discovery

object KademliaDiscovery {
  def apply[F[_], P]: Discovery[F, P] = ??? // TODO pull Kademlia P2P code
}
