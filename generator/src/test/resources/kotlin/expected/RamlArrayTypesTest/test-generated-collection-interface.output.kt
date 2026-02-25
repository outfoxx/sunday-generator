package io.test

import kotlin.String
import kotlin.collections.List
import kotlin.collections.Set

public interface Test {
  public val implicit: List<String>

  public val unspecified: List<String>

  public val nonUnique: List<String>

  public val unique: Set<String>
}
