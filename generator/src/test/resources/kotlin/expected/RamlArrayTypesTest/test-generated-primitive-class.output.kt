package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Int
import kotlin.String

public class Test(
  public val binary: ByteArray,
  public val nullableBinary: ByteArray?,
) {
  public fun copy(binary: ByteArray? = null, nullableBinary: ByteArray? = null): Test = Test(binary
      ?: this.binary, nullableBinary ?: this.nullableBinary)

  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + binary.contentHashCode()
    result = 31 * result + (nullableBinary?.contentHashCode() ?: 0)
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test

    if (!binary.contentEquals(other.binary)) return false
    if (nullableBinary != null) {
      if (other.nullableBinary == null) return false
      if (!nullableBinary.contentEquals(other.nullableBinary)) return false
    }
    else if (other.nullableBinary != null) return false

    return true
  }

  override fun toString(): String = """
  |Test(binary='$binary',
  | nullableBinary='$nullableBinary')
  """.trimMargin()
}
