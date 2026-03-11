package io.test

import com.fasterxml.jackson.`annotation`.JsonProperty
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

public class Test(
  @param:JsonProperty(value = "some-value")
  public val someValue: String,
  @param:JsonProperty(value = "another_value")
  public val anotherValue: String,
) {
  public fun copy(someValue: String? = null, anotherValue: String? = null): Test = Test(someValue ?:
      this.someValue, anotherValue ?: this.anotherValue)

  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + someValue.hashCode()
    result = 31 * result + anotherValue.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test

    if (someValue != other.someValue) return false
    if (anotherValue != other.anotherValue) return false

    return true
  }

  override fun toString(): String = """
  |Test(someValue='$someValue',
  | anotherValue='$anotherValue')
  """.trimMargin()
}
