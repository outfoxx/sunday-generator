package io.test.service

import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.sse.SseEventSource
import org.jboss.resteasy.reactive.RestStreamElementType

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  @Produces(value = ["text/event-stream"])
  @RestStreamElementType(value = "application/json")
  public fun fetchEvents(): SseEventSource

  @GET
  @Path(value = "/tests/server")
  public fun fetchEventsServer(): Test

  @GET
  @Path(value = "/tests/client")
  @Produces(value = ["text/event-stream"])
  @RestStreamElementType(value = "application/json")
  public fun fetchEventsClient(): SseEventSource
}
