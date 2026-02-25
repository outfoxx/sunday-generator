package io.test.service

import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Int
import kotlin.String
import org.jboss.resteasy.reactive.RestPath
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{obj}/{str}/{int}/{def}/{def1}/{def2}")
  public fun fetchTest(
    @RestPath @DefaultValue(value = "10") def2: Int,
    @RestPath obj: Test?,
    @RestPath str: String?,
    @RestPath @DefaultValue(value = "test") def1: String,
    @RestPath int: Int?,
    @RestPath def: String,
  ): RestResponse<Test>
}
