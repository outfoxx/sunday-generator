package io.test

import com.fasterxml.jackson.`annotation`.JsonIgnore
import java.time.LocalDateTime
import kotlin.String

public class Test {
  @get:JsonIgnore
  public val className: String
    get() = LocalDateTime::class.qualifiedName + "-value-" + "-literal"

  public fun copy(): Test = Test()

  override fun toString(): String = """Test()"""
}
