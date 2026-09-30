package io.test

import javax.validation.Valid
import kotlin.Any
import kotlin.String
import kotlin.collections.List

public interface Test {
  @get:Valid
  public val `child`: Child

  public val `children`: List<@Valid Child>

  public val `childMap`: Any

  public val `names`: List<String>
}
