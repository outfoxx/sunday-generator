package io.test

import javax.validation.constraints.Pattern
import javax.validation.constraints.Size
import kotlin.String
import kotlin.collections.List

public interface Test {
  public val codes: List<@Size(max = 5, min = 2) @Pattern(regexp = """^[A-Z]+$""") String?>
}
