package sdk.history

sealed trait HistoryAction {
  def key: KeySegment
}

final case class InsertAction(key: KeySegment, hash: ByteArray32) extends HistoryAction
final case class DeleteAction(key: KeySegment)                    extends HistoryAction
