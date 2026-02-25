package io.test.service

import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestStreamElementType

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  @Produces(value = ["text/event-stream"])
  @RestStreamElementType(value = "application/json")
  public fun fetchEvents(@Context sse: Sse, @Context sseEvents: SseEventSink)

  @GET
  @Path(value = "/tests/server")
  @Produces(value = ["text/event-stream"])
  @RestStreamElementType(value = "application/json")
  public fun fetchEventsServer(@Context sse: Sse, @Context sseEvents: SseEventSink)

  @GET
  @Path(value = "/tests/client")
  public fun fetchEventsClient(): RestResponse<Test>
}
