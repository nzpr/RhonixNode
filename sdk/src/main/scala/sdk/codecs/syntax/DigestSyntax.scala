package sdk.codecs.syntax

import sdk.codecs.Digest
import sdk.primitive.ByteArray

trait DigestSyntax {
  implicit def digestSyntax[A](x: A): DigestOps[A] = new DigestOps(x)
}

final case class DigestOps[A](x: A) extends AnyVal {

  /** Compute digest of a value provided that there is an instance of Digest[A] in scope. */
  def digest(implicit d: Digest[A]): ByteArray = Digest[A].digest(x)
}
