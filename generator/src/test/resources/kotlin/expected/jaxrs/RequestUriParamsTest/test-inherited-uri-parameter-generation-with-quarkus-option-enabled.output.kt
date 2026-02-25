package io.test.service

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Any
import kotlin.Int
import kotlin.String
import kotlin.collections.Map
import org.jboss.resteasy.reactive.RestPath
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{obj}/{str}/{int}/{def}")
  public fun fetchTest(
    @RestPath obj: Map<String, Any>,
    @RestPath str: String,
    @RestPath def: String,
    @RestPath int: Int,
  ): RestResponse<Map<String, Any>>
}
