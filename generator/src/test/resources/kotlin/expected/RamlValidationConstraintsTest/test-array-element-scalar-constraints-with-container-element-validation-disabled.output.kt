package io.test

import javax.validation.constraints.Pattern
import javax.validation.constraints.Size
import kotlin.String
import kotlin.collections.List

public interface Test {
  @get:Size(
    max = 5,
    min = 2,
  )
  @get:Pattern(regexp = """^[A-Z]+$""")
  public val codes: List<String>
}
