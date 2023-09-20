package sdk

package object api {
  private val Namespace = List("io", "rhonix", "api")
  private val Version   = "v1"
  val Path: Seq[String] = Namespace :+ Version
}
