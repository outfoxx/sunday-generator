package io.test.service

import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.String
import org.eclipse.microprofile.rest.client.`annotation`.ClientHeaderParam
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.jboss.resteasy.reactive.RestHeader

@RegisterRestClient
@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @PUT
  @Path(value = "/tests")
  @ClientHeaderParam(
    name = "Expect",
    value = "100-continue",
  )
  public fun putTest(@RestHeader(value = "x-custom") xCustom: String): Test
}
