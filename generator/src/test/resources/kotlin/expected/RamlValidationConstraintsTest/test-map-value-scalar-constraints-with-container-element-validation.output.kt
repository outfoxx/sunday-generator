package io.test

import javax.validation.constraints.Pattern
import javax.validation.constraints.Size
import kotlin.String
import kotlin.collections.Map

public interface Test {
  public val labels: Map<String, @Size(max = 5, min = 2) @Pattern(regexp = """^[a-z]+$""") String>
}
