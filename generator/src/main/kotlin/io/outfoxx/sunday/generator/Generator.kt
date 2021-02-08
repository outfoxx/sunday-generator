package io.outfoxx.sunday.generator

import amf.client.model.domain.WebApi
import java.nio.file.Path

/**
 * Common base class for generators and their parameters
 */

abstract class Generator(
  val api: WebApi,
  defaultMediaTypes: List<String>
) {

  abstract fun generateServiceTypes()

  val defaultMediaTypes: List<String>

  init {

    // Sort default media types according to provided parameter

    val sorted = LinkedHashSet<String>()
    val mediaTypes = api.contentType.filterNotNull() + api.accepts.filterNotNull()

    // Add any from command line also referenced in model
    defaultMediaTypes.forEach { defaultMediaType ->
      if (mediaTypes.contains(defaultMediaType)) {
        sorted.add(defaultMediaType)
      }
    }

    // Ensure all from model are included (in declaration order)
    mediaTypes.forEach { sorted.add(it) }

    this.defaultMediaTypes = sorted.toList()
  }

}
