package io.test.service

import io.test.Test
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
  public fun fetchTest(
    @RestQuery obj: Test?,
    @RestQuery str: String?,
    @RestQuery int: Int?,
    @RestQuery @DefaultValue(value = "test") def1: String,
    @RestQuery @DefaultValue(value = "10") def2: Int,
  ): RestResponse<Test>
}
