package io.test

import javax.validation.constraints.DecimalMax
import javax.validation.constraints.DecimalMin
import kotlin.Double
import kotlin.Float

public interface Test {
  @get:DecimalMin(value = "1.0")
  public val floatMin: Float

  @get:DecimalMax(value = "2.0")
  public val floatMax: Float

  public val floatMultiple: Float

  @get:DecimalMin(value = "4.0")
  public val doubleMin: Double

  @get:DecimalMax(value = "5.0")
  public val doubleMax: Double

  public val doubleMultiple: Double
}
