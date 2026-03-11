package io.test

import com.fasterxml.jackson.`annotation`.JsonInclude
import com.fasterxml.jackson.`annotation`.JsonSubTypes
import com.fasterxml.jackson.`annotation`.JsonTypeInfo
import io.outfoxx.sunday.json.patch.Patch
import kotlin.Any
import kotlin.Boolean
import kotlin.String

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.EXISTING_PROPERTY,
  property = "type",
)
@JsonSubTypes(value = [
  JsonSubTypes.Type(value = Child::class)
])
public abstract class Test() : Patch {
  public abstract val type: TestType

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    return true
  }

  override fun toString(): String = """Test()"""
}
