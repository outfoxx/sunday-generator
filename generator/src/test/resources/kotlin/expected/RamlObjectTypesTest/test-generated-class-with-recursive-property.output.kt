package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.collections.List

public class Test(
  public val parent: Test?,
  public val other: Test? = null,
  public val children: List<Test>,
) {
  public fun copy(
    parent: Test? = null,
    other: Test? = null,
    children: List<Test>? = null,
  ): Test = Test(parent ?: this.parent, other ?: this.other, children ?: this.children)

  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + (parent?.hashCode() ?: 0)
    result = 31 * result + (other?.hashCode() ?: 0)
    result = 31 * result + children.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test

    if (parent != other.parent) return false
    if (other != other.other) return false
    if (children != other.children) return false

    return true
  }

  override fun toString(): String = """
  |Test(parent='$parent',
  | other='$other',
  | children='$children')
  """.trimMargin()
}
