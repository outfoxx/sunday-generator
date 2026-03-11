package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.String

public open class Empty(
  `value`: String,
  value2: String,
) : Test2(value, value2) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Empty

    if (value != other.value) return false
    if (value2 != other.value2) return false
    return true
  }

  override fun toString(): String = """
  |Empty(value='$value',
  | value2='$value2')
  """.trimMargin()
}
