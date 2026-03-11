package io.test

import kotlin.String
import kotlin.collections.List

public interface Test {
  public val arrayOfStrings: List<String>

  public val arrayOfNullableStrings: List<String?>

  public val nullableArrayOfStrings: List<String>?

  public val nullableArrayOfNullableStrings: List<String?>?

  public val declaredArrayOfStrings: List<String>

  public val declaredArrayOfNullableStrings: List<String?>
}
