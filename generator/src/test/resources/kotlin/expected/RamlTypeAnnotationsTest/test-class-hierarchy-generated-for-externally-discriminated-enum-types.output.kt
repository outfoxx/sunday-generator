package io.test

import com.fasterxml.jackson.`annotation`.JsonIgnore
import com.fasterxml.jackson.`annotation`.JsonSubTypes
import kotlin.Any
import kotlin.Boolean
import kotlin.String

@JsonSubTypes(value = [
  JsonSubTypes.Type(value = Child2::class),
  JsonSubTypes.Type(value = Child1::class)
])
public abstract class Parent {
  @get:JsonIgnore
  public abstract val type: Type

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    return true
  }

  override fun toString(): String = """Parent()"""
}
