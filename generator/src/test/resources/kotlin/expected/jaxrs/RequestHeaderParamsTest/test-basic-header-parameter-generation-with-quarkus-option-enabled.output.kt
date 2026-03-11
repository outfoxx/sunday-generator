package io.test.service

import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.jboss.resteasy.reactive.RestHeader
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(
    @RestHeader(value = "obj") obj: Test,
    @RestHeader(value = "str-req") strReq: String,
    @RestHeader(value = "int") @DefaultValue(value = "5") int: Int,
  ): RestResponse<Test>

  @DELETE
  @Path(value = "/tests")
  public fun deleteTest(): RestResponse<Unit>
}
