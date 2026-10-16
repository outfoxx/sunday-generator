package example

import com.sun.net.httpserver.HttpServer
import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.jdk.JdkTransport
import io.outfoxx.sunday.problems.SundayHttpProblem
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

/** Exercises omission through the actual HTTP transport. */
object OptionalCheck {
  @JvmStatic
  fun verify() = runBlocking {
    var received = byteArrayOf()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
      received = exchange.requestBody.readBytes()
      exchange.sendResponseHeaders(204, -1)
      exchange.close()
    }
    server.start()
    try {
      JdkTransport(URITemplate("http://127.0.0.1:${server.address.port}"), SundayHttpProblem.Factory).use { transport ->
        val client = OptionalAPI(transport)
        client.optionalPost().execute()
        check(received.isEmpty())
        client.defaultPost().execute()
        check(received.isEmpty())
        client.optionalPost(Input(target = "ok")).execute()
        check(received.decodeToString().contains("ok"))
        client.requiredPost(Input()).execute()
        check(received.isNotEmpty())
      }
    } finally {
      server.stop(0)
    }
  }
}
