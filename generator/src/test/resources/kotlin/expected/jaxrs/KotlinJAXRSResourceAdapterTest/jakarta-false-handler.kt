package io.test

import io.reactivex.rxjava3.core.Single
import jakarta.ws.rs.container.AsyncResponse
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import kotlin.String

/**
 * Application-owned operation behavior. Register the generated resource with the server.
 */
public interface API {
  /**
   * Handles the sync operation.
   */
  public fun sync(): Response

  /**
   * Handles the created operation.
   */
  public fun created(uriInfo: UriInfo): Response

  /**
   * Handles the asynchronous operation.
   */
  public fun asynchronous(asyncResponse: AsyncResponse)

  /**
   * Handles the reactive operation.
   */
  public fun reactive(): Single<Response>

  /**
   * Handles the sse operation.
   */
  public fun sse(sse: Sse, sseEvents: SseEventSink)

  /**
   * Handles the events operation.
   */
  public fun events(sse: Sse, sseEvents: SseEventSink)

  /**
   * Handles the when operation.
   */
  public fun `when`(delegate_: String?, securityContext: String?): Response

  /**
   * Handles the policy operation.
   */
  public fun policy(): Response
}
