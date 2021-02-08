package io.outfoxx.sunday.generator

import amf.client.model.document.BaseUnit
import amf.client.model.domain.DataNode
import amf.client.model.domain.ObjectNode
import java.net.URI

data class ProblemTypeDefinition(
  val type: URI,
  val status: Int,
  val title: String,
  val detail: String,
  val custom: Map<String, String>,
  val definedIn: BaseUnit,
) {

  constructor(code: String, fields: ObjectNode, baseURI: URI, definedIn: BaseUnit) : this(
    baseURI.resolve("./$code"),
    fields.get<DataNode>("status")?.rawScalarValue?.toInt()
      ?: error("Problem type '$code' missing status"),
    fields.get<DataNode>("title")?.stringValue
      ?: error("Problem type '$code' missing title"),
    fields.get<DataNode>("detail")?.stringValue
      ?: error("Problem type '$code' missing detail"),
    fields.get<ObjectNode>("custom")?.properties()
      ?.map { it.key to (it.value.stringValue ?: "string") }
      ?.toMap() ?: emptyMap(),
    definedIn,
  )

}
