package io.test

import com.fasterxml.jackson.`annotation`.JsonTypeName
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

@JsonTypeName("Child1")
public class Child1(
  public val `value`: String? = null,
  public val value1: Int,
) : Parent() {
  override val type: String
    get() = "Child1"

  public fun copy(`value`: String? = null, value1: Int? = null): Child1 = Child1(value ?:
      this.value, value1 ?: this.value1)

  override fun hashCode(): Int {
    var result = 31 * super.hashCode()
    result = 31 * result + (value?.hashCode() ?: 0)
    result = 31 * result + value1.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Child1

    if (value != other.value) return false
    if (value1 != other.value1) return false

    return true
  }

  override fun toString(): String = """
  |Child1(value='$value',
  | value1='$value1')
  """.trimMargin()
}
