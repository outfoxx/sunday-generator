package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.String
import kotlin.Unit

public interface Test {
  public val bool: Boolean

  public val string: String

  public val `file`: ByteArray

  public val any: Any

  public val nil: Unit
}
