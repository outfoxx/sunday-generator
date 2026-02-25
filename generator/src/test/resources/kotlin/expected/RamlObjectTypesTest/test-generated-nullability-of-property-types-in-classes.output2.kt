package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.collections.Map

public class Test2(
  public val optionalObject: Map<String, Any>? = null,
  public val nillableObject: Map<String, Any>?,
  public val optionalHierarchy: Parent? = null,
  public val nillableHierarchy: Parent?,
) {
  public fun copy(
    optionalObject: Map<String, Any>? = null,
    nillableObject: Map<String, Any>? = null,
    optionalHierarchy: Parent? = null,
    nillableHierarchy: Parent? = null,
  ): Test2 = Test2(optionalObject ?: this.optionalObject, nillableObject ?: this.nillableObject,
      optionalHierarchy ?: this.optionalHierarchy, nillableHierarchy ?: this.nillableHierarchy)

  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + (optionalObject?.hashCode() ?: 0)
    result = 31 * result + (nillableObject?.hashCode() ?: 0)
    result = 31 * result + (optionalHierarchy?.hashCode() ?: 0)
    result = 31 * result + (nillableHierarchy?.hashCode() ?: 0)
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test2

    if (optionalObject != other.optionalObject) return false
    if (nillableObject != other.nillableObject) return false
    if (optionalHierarchy != other.optionalHierarchy) return false
    if (nillableHierarchy != other.nillableHierarchy) return false

    return true
  }

  override fun toString(): String = """
  |Test2(optionalObject='$optionalObject',
  | nillableObject='$nillableObject',
  | optionalHierarchy='$optionalHierarchy',
  | nillableHierarchy='$nillableHierarchy')
  """.trimMargin()
}
