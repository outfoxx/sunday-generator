package io.test

import kotlin.Any
import kotlin.String
import kotlin.collections.Map

public interface Test2 {
  public val optionalObject: Map<String, Any>?

  public val nillableObject: Map<String, Any>?

  public val optionalHierarchy: Parent?

  public val nillableHierarchy: Parent?
}
