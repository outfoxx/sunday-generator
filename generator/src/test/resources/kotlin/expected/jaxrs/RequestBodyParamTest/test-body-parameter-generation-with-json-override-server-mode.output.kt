package io.test.service

import com.fasterxml.jackson.databind.JsonNode
import io.test.Test
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(body: JsonNode): Response

  @GET
  @Path(value = "/tests-client")
  public fun fetchTestClient(body: Test): Response

  @GET
  @Path(value = "/tests-server")
  public fun fetchTestServer(body: JsonNode): Response
}
