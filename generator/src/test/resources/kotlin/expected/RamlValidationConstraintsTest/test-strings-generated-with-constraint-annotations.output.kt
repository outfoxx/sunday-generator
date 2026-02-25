package io.test

import javax.validation.constraints.Pattern
import javax.validation.constraints.Size
import kotlin.String

public interface Test {
  @get:Pattern(regexp = """^[a-zA-Z0-9]+$""")
  public val pattern: String

  @get:Size(min = 5)
  public val min: String

  @get:Size(max = 10)
  public val max: String
}
