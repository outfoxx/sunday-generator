package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Set

public class Test(
  public val implicit: List<String>,
  public val unspecified: List<String>,
  public val nonUnique: List<String>,
  public val unique: Set<String>,
) {
  public fun copy(
    implicit: List<String>? = null,
    unspecified: List<String>? = null,
    nonUnique: List<String>? = null,
    unique: Set<String>? = null,
  ): Test = Test(implicit ?: this.implicit, unspecified ?: this.unspecified, nonUnique ?:
      this.nonUnique, unique ?: this.unique)

  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + implicit.hashCode()
    result = 31 * result + unspecified.hashCode()
    result = 31 * result + nonUnique.hashCode()
    result = 31 * result + unique.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test

    if (implicit != other.implicit) return false
    if (unspecified != other.unspecified) return false
    if (nonUnique != other.nonUnique) return false
    if (unique != other.unique) return false

    return true
  }

  override fun toString(): String = """
  |Test(implicit='$implicit',
  | unspecified='$unspecified',
  | nonUnique='$nonUnique',
  | unique='$unique')
  """.trimMargin()
}
