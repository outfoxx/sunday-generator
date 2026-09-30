package io.test

import javax.validation.Valid
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

public interface Test {
  @get:Valid
  public val `child`: Child

  public val `children`: List<@Valid Child>

  public val `childMap`: Map<String, @Valid Child>

  public val `names`: List<String>
}
