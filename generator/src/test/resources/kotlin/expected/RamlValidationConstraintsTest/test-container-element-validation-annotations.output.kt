package io.test

import io.outfoxx.sunday.validation.javax.Schema
import io.outfoxx.sunday.validation.javax.SerializableModel
import javax.validation.Valid
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.jvm.JvmSuppressWildcards

public interface Test : SerializableModel {
  @get:Schema(requiredValue = true)
  @get:Valid
  public val `child`: Child

  @get:Schema(requiredValue = true)
  public val `children`: @JvmSuppressWildcards List<@Schema(requiredValue = true) @Valid Child>

  @get:Schema(requiredValue = true)
  public val `childMap`: @JvmSuppressWildcards Map<@Schema(requiredValue = true)
      String, @Schema(requiredValue = true) @Valid Child>

  @get:Schema(requiredValue = true)
  public val `names`: @JvmSuppressWildcards List<@Schema(requiredValue = true) String>
}
