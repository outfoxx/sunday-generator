package io.outfoxx.sunday.generator

import java.nio.file.Files

/** Creates an invocation default configuration for CLI integration tests. */
internal fun placementConfig(
  value: String,
  language: String = "kotlin",
  category: String? = null,
): String {
  val path = Files.createTempFile("sunday-placement", ".yaml")
  path.toFile().deleteOnExit()
  val fields = if (category == null) "    package: $value" else "    $category:\n      package: $value"
  Files.writeString(path, "defaults:\n  $language:\n$fields\n")
  return path.toString()
}
