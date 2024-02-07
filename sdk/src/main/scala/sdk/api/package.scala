package sdk

package object api {
  private val Namespace     = List("api")
  private val Version       = "v1"
  val RootPath: Seq[String] = Namespace :+ Version
}
