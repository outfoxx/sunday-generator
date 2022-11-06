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

import amf.apicontract.client.platform.model.domain.api.WebApi
import io.outfoxx.sunday.generator.utils.accepts
import io.outfoxx.sunday.generator.utils.contentType

/**
 * Common base class for generators and their parameters
 */

abstract class Generator(
  val api: WebApi,
  options: Options
) {

  open class Options(
    val defaultProblemBaseUri: String,
    val defaultMediaTypes: List<String>,
    val serviceSuffix: String
  )

  open val options: Options = options
  val defaultMediaTypes: List<String>

  init {

    // Sort default media types according to provided parameter

    val ordered = LinkedHashSet<String>()
    val mediaTypes = api.contentType.filterNotNull().toMutableList()
    api.accepts.filterNotNull().filterNot(mediaTypes::contains).forEach(mediaTypes::add)

    // Add any from command line also referenced in model
    options.defaultMediaTypes.forEach { defaultMediaType ->
      if (mediaTypes.contains(defaultMediaType)) {
        ordered.add(defaultMediaType)
      }
    }

    // Ensure all from model are included (in declaration order)
    mediaTypes.forEach { ordered.add(it) }

    this.defaultMediaTypes = ordered.toList()
  }

  abstract fun generateServiceTypes()
}
