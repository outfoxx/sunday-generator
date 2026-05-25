package io.test.service

import io.smallrye.mutiny.Multi
import io.test.Base
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.jboss.resteasy.reactive.RestStreamElementType

@RegisterRestClient
@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/test1")
  @Produces(value = ["text/event-stream"])
  @RestStreamElementType(value = "application/json")
  public fun fetchEventsSimple(): Multi<Base>

  @GET
  @Path(value = "/test2")
  @Produces(value = ["text/event-stream"])
  @RestStreamElementType(value = "application/json")
  public fun fetchEventsDiscriminated(): Multi<Base>
}
