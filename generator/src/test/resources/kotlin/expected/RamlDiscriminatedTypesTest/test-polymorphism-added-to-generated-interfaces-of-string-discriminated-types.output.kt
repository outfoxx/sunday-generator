package io.test

import com.fasterxml.jackson.`annotation`.JsonSubTypes
import com.fasterxml.jackson.`annotation`.JsonTypeInfo
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
public interface Parent {
  public val type: String
}
