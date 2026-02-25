package io.test

import kotlin.String

public interface Root {
  public val `value`: String

  public interface Group {
    public val `value`: String

    public interface Member {
      public val memberValue: String
    }
  }
}
