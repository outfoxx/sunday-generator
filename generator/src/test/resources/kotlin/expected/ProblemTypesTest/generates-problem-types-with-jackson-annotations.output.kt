package io.test.service

import com.fasterxml.jackson.`annotation`.JsonCreator
import com.fasterxml.jackson.`annotation`.JsonIgnore
import com.fasterxml.jackson.`annotation`.JsonProperty
import com.fasterxml.jackson.`annotation`.JsonTypeName
import io.test.InvalidIdProblem
import java.net.URI
import kotlin.String
import org.zalando.problem.AbstractThrowableProblem
import org.zalando.problem.Exceptional
import org.zalando.problem.Status
import org.zalando.problem.ThrowableProblem

@JsonTypeName(InvalidIdProblem.TYPE)
public class InvalidIdProblem @JsonCreator constructor(
  @JsonProperty(value = "offending_id")
  public val offendingId: String,
  instance: URI? = null,
  cause: ThrowableProblem? = null,
) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
    "The id contains one or more invalid characters.", instance, cause) {
  @JsonIgnore
  override fun getCause(): Exceptional? = super.cause

  public companion object {
    public const val TYPE: String = "http://example.com/invalid_id"

    public val TYPE_URI: URI = URI(TYPE)
  }
}
