package rhonix

/** Package describing API. HTTP, RPC, any other possible protocol.
 * This package defines routes, authentication, servers, codecs - everything that
 * makes API specification to be accessible on a wire.
 * */
package object api {
  private val namespace = "io.rhonix.api"
  private val version   = "v1"
  val prefix            = s"$namespace.$version"
}
