package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.security.ClientSettings
import io.outfoxx.sunday.withSecurity
import io.test.Base
import io.test.Test1
import io.test.Test2
import kotlin.collections.List
import kotlin.jvm.JvmOverloads
import kotlin.reflect.typeOf
import kotlinx.coroutines.flow.Flow

public class API<Req : Request> @JvmOverloads constructor(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(),
  private val clientSettings: ClientSettings? = null,
) {
  public fun fetchEventsSimple(): Flow<Base> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchEventsSimple")) else
      this.transport)
    .eventStream(
      method = Method.Get,
      pathTemplate = "/test1",
      acceptTypes = listOf(MediaType.EventStream),
      decoder = { decoder, _, _, data, _ -> decoder.decode<Base>(data, typeOf<Base>()) }
    )

  public fun fetchEventsDiscriminated(): Flow<Base> = (if (clientSettings != null)
      this.transport.withSecurity(clientSettings.bindings.getValue("fetchEventsDiscriminated")) else
      this.transport)
    .eventStream(
      method = Method.Get,
      pathTemplate = "/test2",
      acceptTypes = listOf(MediaType.EventStream),
      decoder = { decoder, event, _, data, logger ->
        when (event) {
          "Test1" -> decoder.decode<Test1>(data, typeOf<Test1>())
          "Test2" -> decoder.decode<Test2>(data, typeOf<Test2>())
          else -> {
            logger.error("Unknown event type, ignoring event: event=$event")
            null
          }
        }
      }
    )
}
