package io.test.service

import io.test.Base
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlinx.coroutines.flow.Flow
import org.jboss.resteasy.reactive.RestStreamElementType

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/test1")
  @Produces(value = ["text/event-stream"])
  @RestStreamElementType(value = "application/json")
  public suspend fun fetchEventsSimple(): Flow<Base>

  @GET
  @Path(value = "/test2")
  @Produces(value = ["text/event-stream"])
  @RestStreamElementType(value = "application/json")
  public suspend fun fetchEventsDiscriminated(): Flow<Base>
}
