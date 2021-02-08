package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.test.extensions.ResourceExtension

enum class SchemaMode(
  val directory: String? = null,
  val extension: String
) {

  OpenAPI3("openapi", "yaml"),
  RAML10("raml", "raml")

  ;

  fun resourceUri(name: String) = ResourceExtension.get("${directory?.let { "$it/" } ?: ""}$name.$extension")

}
