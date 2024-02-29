package sdk.data

import sdk.primitive.ByteArray

/**
 * Hash broadcast message.
 * @param hash hash
 * @param src suggested peer to retrieve block
 */
final case class HashBroadcast(hash: ByteArray, src: String)
