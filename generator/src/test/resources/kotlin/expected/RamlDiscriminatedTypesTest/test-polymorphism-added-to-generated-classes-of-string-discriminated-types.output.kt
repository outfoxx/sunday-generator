package io.test

import com.fasterxml.jackson.`annotation`.JsonSubTypes
import com.fasterxml.jackson.`annotation`.JsonTypeInfo
import kotlin.Any
import kotlin.Boolean
import kotlin.String

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.EXISTING_PROPERTY,
  property = "type",
)
@JsonSubTypes(value = [
  JsonSubTypes.Type(value = Child1::class),
  JsonSubTypes.Type(value = Child2::class)
])
public abstract class Parent {
  public abstract val type: String

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    return true
  }

  override fun toString(): String = """Parent()"""
}
