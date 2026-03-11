package io.test.service

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Any
import kotlin.ByteArray
import kotlin.String
import kotlin.collections.Map
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  @Consumes(value = ["application/octet-stream"])
  public fun fetchTest(body: ByteArray): RestResponse<Map<String, Any>>
}
