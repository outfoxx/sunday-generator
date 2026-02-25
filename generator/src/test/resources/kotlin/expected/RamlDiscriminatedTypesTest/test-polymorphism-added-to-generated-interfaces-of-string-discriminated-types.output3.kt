package io.test

import com.fasterxml.jackson.`annotation`.JsonTypeName
import kotlin.Int
import kotlin.String

@JsonTypeName("child2")
public interface Child2 : Parent {
  public val `value`: String?

  public val value2: Int
}
