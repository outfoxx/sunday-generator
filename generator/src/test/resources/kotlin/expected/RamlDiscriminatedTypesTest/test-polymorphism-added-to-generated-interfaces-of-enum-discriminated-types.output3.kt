package io.test

import com.fasterxml.jackson.`annotation`.JsonTypeName
import kotlin.String

@JsonTypeName("Child2")
public interface Child2 : Parent {
  public val `value`: String?
}
