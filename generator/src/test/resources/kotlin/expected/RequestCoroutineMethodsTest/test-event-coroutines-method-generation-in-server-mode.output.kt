package io.test.service

import io.test.Test1
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.sse.OutboundSseEvent
import kotlin.Any
import kotlinx.coroutines.flow.Flow

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/test1")
  @Produces(value = ["text/event-stream"])
  public fun fetchEventsSimple(): Flow<Test1>

  @GET
  @Path(value = "/test2")
  @Produces(value = ["text/event-stream"])
  public fun fetchEventsDiscriminated(): Flow<Any>

  @GET
  @Path(value = "/test3")
  @Produces(value = ["text/event-stream"])
  public fun fetchEventsSimpleSse(): Flow<OutboundSseEvent>
}
