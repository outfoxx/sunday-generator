package io.test.service

import com.fasterxml.jackson.`annotation`.JsonIgnore
import java.net.URI
import javax.`annotation`.processing.Generated
import kotlin.String
import kotlin.collections.List
import org.zalando.problem.AbstractThrowableProblem
import org.zalando.problem.Exceptional
import org.zalando.problem.Status
import org.zalando.problem.ThrowableProblem

@Generated(
  value = ["io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry"],
  date = "2024-01-01T00:00:00",
)
public class TestResolverProblem(
  public val optionalString: String? = null,
  public val arrayOfStrings: List<String>,
  public val optionalArrayOfStrings: List<String>? = null,
  instance: URI? = null,
  cause: ThrowableProblem? = null,
) : AbstractThrowableProblem(TYPE_URI, "Test Resolve Type Reference", Status.INTERNAL_SERVER_ERROR,
    "Tests the resolveTypeReference function implementation.", instance, cause) {
  @JsonIgnore
  override fun getCause(): Exceptional? = super.cause

  @Generated
  public companion object {
    public const val TYPE: String = "http://example.com/test_resolver"

    public val TYPE_URI: URI = URI(TYPE)
  }
}
