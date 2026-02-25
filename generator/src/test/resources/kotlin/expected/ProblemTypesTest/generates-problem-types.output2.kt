package io.test.service

import com.fasterxml.jackson.`annotation`.JsonIgnore
import java.net.URI
import kotlin.String
import org.zalando.problem.AbstractThrowableProblem
import org.zalando.problem.Exceptional
import org.zalando.problem.Status
import org.zalando.problem.ThrowableProblem

public class AccountNotFoundProblem(
  instance: URI? = null,
  cause: ThrowableProblem? = null,
) : AbstractThrowableProblem(TYPE_URI, "Account Not Found", Status.NOT_FOUND,
    "The requested account does not exist or you do not have permission to access it.", instance,
    cause) {
  @JsonIgnore
  override fun getCause(): Exceptional? = super.cause

  public companion object {
    public const val TYPE: String = "http://example.com/account_not_found"

    public val TYPE_URI: URI = URI(TYPE)
  }
}
