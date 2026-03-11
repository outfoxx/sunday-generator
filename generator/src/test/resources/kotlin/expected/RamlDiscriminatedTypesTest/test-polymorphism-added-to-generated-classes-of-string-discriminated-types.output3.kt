package io.test

import com.fasterxml.jackson.`annotation`.JsonTypeName
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

@JsonTypeName("child2")
public class Child2(
  public val `value`: String? = null,
  public val value2: Int,
) : Parent() {
  override val type: String
    get() = "child2"

  public fun copy(`value`: String? = null, value2: Int? = null): Child2 = Child2(value ?:
      this.value, value2 ?: this.value2)

  override fun hashCode(): Int {
    var result = 31 * super.hashCode()
    result = 31 * result + (value?.hashCode() ?: 0)
    result = 31 * result + value2.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Child2

    if (value != other.value) return false
    if (value2 != other.value2) return false

    return true
  }

  override fun toString(): String = """
  |Child2(value='$value',
  | value2='$value2')
  """.trimMargin()
}
