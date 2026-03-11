package io.test

import javax.validation.Valid
import kotlin.Any
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

public interface Test {
  @get:Valid
  public val child: Child

  public val children: List<Child>

  @get:Valid
  public val childMap: Map<String, Any>

  public val names: List<String>
}
