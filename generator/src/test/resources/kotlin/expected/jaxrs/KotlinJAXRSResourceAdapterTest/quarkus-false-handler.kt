package io.test

import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import jakarta.ws.rs.container.AsyncResponse
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import kotlin.String
import kotlin.Unit
import org.jboss.resteasy.reactive.RestResponse

/**
 * Application-owned operation behavior. Register the generated resource with the server.
 */
public interface API {
  /**
   * Handles the sync operation.
   */
  public fun sync(): RestResponse<String>

  /**
   * Handles the created operation.
   */
  public fun created(uriInfo: UriInfo): RestResponse<Unit>

  /**
   * Handles the asynchronous operation.
   */
  public fun asynchronous(asyncResponse: AsyncResponse)

  /**
   * Handles the reactive operation.
   */
  public fun reactive(): Uni<RestResponse<String>>

  /**
   * Handles the sse operation.
   */
  public fun sse(sse: Sse, sseEvents: SseEventSink)

  /**
   * Handles the events operation.
   */
  public fun events(): Multi<String>

  /**
   * Handles the when operation.
   */
  public fun `when`(delegate_: String?, securityContext: String?): RestResponse<String>

  /**
   * Handles the policy operation.
   */
  public fun policy(): RestResponse<String>
}
