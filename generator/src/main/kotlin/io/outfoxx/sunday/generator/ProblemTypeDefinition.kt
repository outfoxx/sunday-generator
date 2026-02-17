/*
 * Copyright 2020 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator

import amf.core.client.platform.model.document.BaseUnit
import amf.core.client.platform.model.domain.DataNode
import amf.core.client.platform.model.domain.ObjectNode
import io.outfoxx.sunday.generator.utils.get
import io.outfoxx.sunday.generator.utils.rawScalarValue
import io.outfoxx.sunday.generator.utils.stringValue
import java.net.URI

data class ProblemTypeDefinition(
  val type: URI,
  val status: Int,
  val title: String,
  val detail: String,
  val custom: Map<String, String>,
  val definedIn: BaseUnit,
  val source: ObjectNode,
) {

  constructor(code: String, fields: ObjectNode, baseURI: URI, definedIn: BaseUnit, source: ObjectNode) : this(
    baseURI.resolve("./$code"),
    fields.get<DataNode>("status")?.rawScalarValue?.toInt()
      ?: error("Problem type '$code' missing status"),
    fields.get<DataNode>("title")?.stringValue
      ?: error("Problem type '$code' missing title"),
    fields.get<DataNode>("detail")?.stringValue
      ?: error("Problem type '$code' missing detail"),
    fields
      .get<ObjectNode>("custom")
      ?.properties()
      ?.map { it.key to (it.value.stringValue ?: "string") }
      ?.toMap() ?: emptyMap(),
    definedIn,
    source,
  )
}
