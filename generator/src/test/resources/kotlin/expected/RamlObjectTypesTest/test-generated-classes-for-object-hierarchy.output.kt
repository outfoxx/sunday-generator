package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

public open class Test(
  public val `value`: String,
) {
  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + value.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test

    if (value != other.value) return false

    return true
  }

  override fun toString(): String = """Test(value='$value')"""
}
