package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

public class Test(
  public val fromNilUnion: String?,
  public val notRequired: String? = null,
) {
  public fun copy(fromNilUnion: String? = null, notRequired: String? = null): Test =
      Test(fromNilUnion ?: this.fromNilUnion, notRequired ?: this.notRequired)

  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + (fromNilUnion?.hashCode() ?: 0)
    result = 31 * result + (notRequired?.hashCode() ?: 0)
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test

    if (fromNilUnion != other.fromNilUnion) return false
    if (notRequired != other.notRequired) return false

    return true
  }

  override fun toString(): String = """
  |Test(fromNilUnion='$fromNilUnion',
  | notRequired='$notRequired')
  """.trimMargin()
}
