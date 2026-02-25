package io.test.service

import com.fasterxml.jackson.databind.JsonNode
import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(body: JsonNode): RestResponse<Test>

  @GET
  @Path(value = "/tests-client")
  public fun fetchTestClient(body: Test): RestResponse<Test>

  @GET
  @Path(value = "/tests-server")
  public fun fetchTestServer(body: JsonNode): RestResponse<Test>
}
