package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

public class Test3(
  `value`: String,
  value2: String,
  public val value3: String,
) : Empty(value, value2) {
  public fun copy(
    `value`: String? = null,
    value2: String? = null,
    value3: String? = null,
  ): Test3 = Test3(value ?: this.value, value2 ?: this.value2, value3 ?: this.value3)

  override fun hashCode(): Int {
    var result = 31 * super.hashCode()
    result = 31 * result + value3.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test3

    if (value != other.value) return false
    if (value2 != other.value2) return false
    if (value3 != other.value3) return false

    return true
  }

  override fun toString(): String = """
  |Test3(value='$value',
  | value2='$value2',
  | value3='$value3')
  """.trimMargin()
}
