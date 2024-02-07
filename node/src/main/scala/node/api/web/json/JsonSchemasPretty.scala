package node.api.web.json

/**
 * Extension of a endpoints4s.generic.JsonSchemas to make object names short without namespace.
 */
trait JsonSchemasPretty extends endpoints4s.generic.JsonSchemas {
  import scala.reflect.runtime.universe
  import scala.reflect.runtime.universe.*

  // for case classes
  def genericRecord[A: TypeTag: GenericJsonSchema.GenericRecord]: Record[A] = {
    val tpeName = universe.typeOf[A].typeSymbol.name.decodedName.toString
    super.genericRecord[A].named(tpeName)
  }

  // for traits
  def genericTaggedPretty[A: TypeTag: GenericJsonSchema.GenericTagged]: Tagged[A] = {
    val tpeName = universe.typeOf[A].typeSymbol.name.decodedName.toString
    super.genericTagged[A].named(tpeName)
  }
}
