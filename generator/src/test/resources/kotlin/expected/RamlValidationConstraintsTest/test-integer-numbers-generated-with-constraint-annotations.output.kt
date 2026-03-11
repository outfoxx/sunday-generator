package io.test

import javax.validation.constraints.Max
import javax.validation.constraints.Min
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.Short

public interface Test {
  @get:Min(value = 1)
  public val byteMin: Byte

  @get:Max(value = 2)
  public val byteMax: Byte

  public val byteMultiple: Byte

  @get:Min(value = 4)
  public val shortMin: Short

  @get:Max(value = 5)
  public val shortMax: Short

  public val shortMultiple: Short

  @get:Min(value = 7)
  public val intMin: Int

  @get:Max(value = 8)
  public val intMax: Int

  public val intMultiple: Int

  @get:Min(value = 10)
  public val longMin: Long

  @get:Max(value = 11)
  public val longMax: Long

  public val longMultiple: Long
}
