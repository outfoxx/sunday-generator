package io.test

import com.fasterxml.jackson.`annotation`.JsonTypeName
import kotlin.Int
import kotlin.String

@JsonTypeName("Child1")
public interface Child1 : Parent {
  public val `value`: String?

  public val value1: Int
}
