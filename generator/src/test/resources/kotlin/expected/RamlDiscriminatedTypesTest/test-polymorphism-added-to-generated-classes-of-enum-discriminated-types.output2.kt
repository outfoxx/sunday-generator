package io.test

import com.fasterxml.jackson.`annotation`.JsonTypeName
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

@JsonTypeName("Child1")
public class Child1(
  public val `value`: String? = null,
) : Parent() {
  override val type: Type
    get() = Type.Child1

  public fun copy(`value`: String? = null): Child1 = Child1(value ?: this.value)

  override fun hashCode(): Int {
    var result = 31 * super.hashCode()
    result = 31 * result + (value?.hashCode() ?: 0)
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Child1

    if (value != other.value) return false

    return true
  }

  override fun toString(): String = """Child1(value='$value')"""
}
