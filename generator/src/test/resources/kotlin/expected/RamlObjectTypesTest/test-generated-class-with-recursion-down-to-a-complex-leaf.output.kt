package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

public open class Node(
  public val type: NodeType,
) {
  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + type.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Node

    if (type != other.type) return false

    return true
  }

  override fun toString(): String = """Node(type='$type')"""
}
