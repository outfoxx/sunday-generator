package io.test

import kotlin.String

public interface Group {
  public val `value`: String

  public interface Member1 : Group {
    public val memberValue1: String

    public interface Sub : Member1 {
      public val subMemberValue: String
    }
  }

  public interface Member2 : Group {
    public val memberValue2: String
  }
}
