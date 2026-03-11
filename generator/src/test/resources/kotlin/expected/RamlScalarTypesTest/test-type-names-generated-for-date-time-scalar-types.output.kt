package io.test

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

public interface Test {
  public val dateOnly: LocalDate

  public val timeOnly: LocalTime

  public val dateTimeOnly: LocalDateTime

  public val dateTime: OffsetDateTime
}
