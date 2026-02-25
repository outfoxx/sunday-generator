package io.test

import javax.validation.constraints.Size
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Set

public interface Test {
  @get:Size(min = 5)
  public val minList: List<String>

  @get:Size(max = 10)
  public val maxList: List<String>

  @get:Size(min = 15)
  public val minSet: Set<String>

  @get:Size(max = 20)
  public val maxSet: Set<String>
}
