package io.test.service

import io.outfoxx.sunday.validation.jakarta.EntitySchema
import io.test.Test
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Int
import kotlin.String
import org.jboss.resteasy.reactive.RestQuery
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  @EntitySchema
  public fun fetchTest(
    @RestQuery @NotNull @Valid obj: Test,
    @RestQuery @NotNull strReq: String,
    @RestQuery @DefaultValue(value = "5") int: Int,
  ): RestResponse<Test>
}
