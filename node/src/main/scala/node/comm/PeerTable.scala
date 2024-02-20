package node.comm

import cats.effect.std.AtomicCell
import cats.effect.{Async, Sync}
import cats.syntax.all.*
import node.comm.PeerTable.ST
import sdk.comm.Peer
import slick.api.SlickApi
import slick.syntax.all.DBIOActionRunSyntax

final class PeerTable[F[_]: Sync, PId, P] private (
  private val stCell: AtomicCell[F, ST[PId, P]],
  loadPeersF: () => F[Seq[P]],
  storePeerF: P => F[Unit],
  removePeerF: P => F[Unit],
) {
  def add(peers: Map[PId, P]): F[Unit] = stCell.evalUpdate(state => updatePeers().as(ST(state.peers ++ peers)))
  def remove(keys: Set[PId]): F[Unit]  = stCell.evalUpdate(state => updatePeers().as(ST(state.peers -- keys)))
  def all: F[Map[PId, P]]              = stCell.get.map(_.peers)

  private def updatePeers(): F[Unit] = for {
    dbPeers  <- loadPeersF()
    refPeers <- stCell.get.map(_.peers.values.toSeq)
    toInsert  = refPeers.filter(p => !dbPeers.contains(p))
    toRemove  = dbPeers.filter(p => !refPeers.contains(p))
    _        <- toInsert.traverse(storePeerF)
    _        <- toRemove.traverse(removePeerF)
  } yield ()
}

object PeerTable {
  final case class ST[PId, P](peers: Map[PId, P])

  def apply[F[_]: Async](cfg: Config)(implicit api: SlickApi[F]): F[PeerTable[F, String, Peer]] = {
    import api.slickDb
    for {
      dbPeers <- api.peers
      state   <- if (dbPeers.isEmpty) {
                   // At the first start, when there are no peers in the DB,
                   // read them from the configuration file and save in the DB
                   val peerTable = PeerTable.ST[String, Peer](cfg.peers.map { p =>
                     p.host -> Peer(p.host, p.port, p.isSelf, p.isValidator)
                   }.toMap)
                   cfg.peers
                     .traverse(p => api.actions.peerInsertIfNot(p.host, p.port, p.isSelf, p.isValidator).run)
                     .as(peerTable)
                 } else {
                   // During the next launches use peers from the DB
                   PeerTable.ST[String, Peer](dbPeers.map(p => p.host -> p).toMap).pure
                 }
      stCell  <- AtomicCell[F].of(state)
    } yield new PeerTable(
      stCell,
      () => api.peers,
      (peer: Peer) => api.actions.peerInsertIfNot(peer.host, peer.port, peer.isSelf, peer.isValidator).run.void,
      (peer: Peer) => api.actions.removePeer(peer.host).run.void,
    )
  }
}
