package io.outfoxx.sunday.generator.utils

fun String.toLowercaseInitialWordOrAbbreviation(): String {
  val builder = StringBuilder()
  var needToLower = true
  forEach {
    if (needToLower && it.isLowerCase()) {
      needToLower = false
      builder.append(it)
    } else {
      builder.append(it.toLowerCase())
    }
  }
  return builder.toString()
}

fun String.toUppercaseInitialWordOrAbbreviation(): String {
  val builder = StringBuilder()
  var needToUpper = true
  forEach {
    if (needToUpper && it.isUpperCase()) {
      needToUpper = false
      builder.append(it)
    } else {
      builder.append(it.toUpperCase())
    }
  }
  return builder.toString()
}

fun String.toUpperCamelCase(): String = split('-', '_', '.').joinToString("") { it.capitalize() }

fun String.toLowerCamelCase(): String = toUpperCamelCase().decapitalize()

val camelRegex = """(?<=[a-zA-Z])[A-Z]""".toRegex()

fun String.camelCaseToKebabCase() =
  camelRegex.replace(this) { "-${it.value}" }
    .toLowerCase()
    .replace("a-p-i", "api")
