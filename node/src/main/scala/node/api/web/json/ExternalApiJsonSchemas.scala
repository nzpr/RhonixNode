package node.api.web.json

import sdk.api.data.*

/**
 * Json encoding for API types
 */
trait ExternalApiJsonSchemas extends JsonSchemasPretty {
  implicit val balanceArrayByte: JsonSchema[Array[Byte]]    = ??? // genericRecord[Array[Byte]]
  implicit val balanceJson: JsonSchema[Balance]             = genericRecord[Balance]
  implicit val deployJson: JsonSchema[Deploy]               = genericRecord[Deploy]
  implicit val justificationJson: JsonSchema[Justification] = genericRecord[Justification]
  implicit val blockJson: JsonSchema[Block]                 = genericRecord[Block]
  implicit val bondJson: JsonSchema[Bond]                   = genericRecord[Bond]
  implicit val statusJson: JsonSchema[Status]               = genericRecord[Status]
}
