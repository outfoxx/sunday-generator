package io.test

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.collections.List

public class Test(
  public val arrayOfStrings: List<String>,
  public val arrayOfNullableStrings: List<String?>,
  public val nullableArrayOfStrings: List<String>?,
  public val nullableArrayOfNullableStrings: List<String?>?,
  public val declaredArrayOfStrings: List<String>,
  public val declaredArrayOfNullableStrings: List<String?>,
) {
  public fun copy(
    arrayOfStrings: List<String>? = null,
    arrayOfNullableStrings: List<String?>? = null,
    nullableArrayOfStrings: List<String>? = null,
    nullableArrayOfNullableStrings: List<String?>? = null,
    declaredArrayOfStrings: List<String>? = null,
    declaredArrayOfNullableStrings: List<String?>? = null,
  ): Test = Test(arrayOfStrings ?: this.arrayOfStrings, arrayOfNullableStrings ?:
      this.arrayOfNullableStrings, nullableArrayOfStrings ?: this.nullableArrayOfStrings,
      nullableArrayOfNullableStrings ?: this.nullableArrayOfNullableStrings, declaredArrayOfStrings
      ?: this.declaredArrayOfStrings, declaredArrayOfNullableStrings ?:
      this.declaredArrayOfNullableStrings)

  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + arrayOfStrings.hashCode()
    result = 31 * result + arrayOfNullableStrings.hashCode()
    result = 31 * result + (nullableArrayOfStrings?.hashCode() ?: 0)
    result = 31 * result + (nullableArrayOfNullableStrings?.hashCode() ?: 0)
    result = 31 * result + declaredArrayOfStrings.hashCode()
    result = 31 * result + declaredArrayOfNullableStrings.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test

    if (arrayOfStrings != other.arrayOfStrings) return false
    if (arrayOfNullableStrings != other.arrayOfNullableStrings) return false
    if (nullableArrayOfStrings != other.nullableArrayOfStrings) return false
    if (nullableArrayOfNullableStrings != other.nullableArrayOfNullableStrings) return false
    if (declaredArrayOfStrings != other.declaredArrayOfStrings) return false
    if (declaredArrayOfNullableStrings != other.declaredArrayOfNullableStrings) return false

    return true
  }

  override fun toString(): String = """
  |Test(arrayOfStrings='$arrayOfStrings',
  | arrayOfNullableStrings='$arrayOfNullableStrings',
  | nullableArrayOfStrings='$nullableArrayOfStrings',
  | nullableArrayOfNullableStrings='$nullableArrayOfNullableStrings',
  | declaredArrayOfStrings='$declaredArrayOfStrings',
  | declaredArrayOfNullableStrings='$declaredArrayOfNullableStrings')
  """.trimMargin()
}
