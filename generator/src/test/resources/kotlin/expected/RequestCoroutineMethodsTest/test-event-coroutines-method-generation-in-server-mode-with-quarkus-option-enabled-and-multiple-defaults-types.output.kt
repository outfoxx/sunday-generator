package io.test.service

import io.test.Test1
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.sse.OutboundSseEvent
import kotlin.Any
import kotlinx.coroutines.flow.Flow

@Produces(value = ["application/json", "application/yaml"])
@Consumes(value = ["application/json", "application/yaml"])
public interface API {
  @GET
  @Path(value = "/test1")
  @Produces(value = ["text/event-stream"])
  public suspend fun fetchEventsSimple(): Flow<Test1>

  @GET
  @Path(value = "/test2")
  @Produces(value = ["text/event-stream"])
  public suspend fun fetchEventsDiscriminated(): Flow<Any>

  @GET
  @Path(value = "/test3")
  @Produces(value = ["text/event-stream"])
  public suspend fun fetchEventsSimpleSse(): Flow<OutboundSseEvent>
}
