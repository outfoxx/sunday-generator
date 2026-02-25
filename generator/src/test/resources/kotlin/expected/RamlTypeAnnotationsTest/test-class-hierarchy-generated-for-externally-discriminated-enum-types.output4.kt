package io.test

import com.fasterxml.jackson.`annotation`.JsonTypeInfo
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

public class Test(
  @JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
    property = "parentType",
  )
  public val parent: Parent,
  public val parentType: Type,
) {
  public fun copy(parent: Parent? = null, parentType: Type? = null): Test = Test(parent ?:
      this.parent, parentType ?: this.parentType)

  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + parent.hashCode()
    result = 31 * result + parentType.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test

    if (parent != other.parent) return false
    if (parentType != other.parentType) return false

    return true
  }

  override fun toString(): String = """
  |Test(parent='$parent',
  | parentType='$parentType')
  """.trimMargin()
}
