package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

public open class Test2(
  `value`: String,
  public val value2: String,
) : Test(value) {
  override fun hashCode(): Int {
    var result = 31 * super.hashCode()
    result = 31 * result + value2.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test2

    if (value != other.value) return false
    if (value2 != other.value2) return false

    return true
  }

  override fun toString(): String = """
  |Test2(value='$value',
  | value2='$value2')
  """.trimMargin()
}
