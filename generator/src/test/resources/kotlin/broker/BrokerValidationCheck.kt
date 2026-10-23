package io.test

import io.outfoxx.sunday.broker.BrokerConsumeSpec
import io.outfoxx.sunday.broker.BrokerConsumer
import io.outfoxx.sunday.broker.BrokerMessage
import io.outfoxx.sunday.broker.BrokerMessageCodec
import io.outfoxx.sunday.broker.BrokerProducer
import io.outfoxx.sunday.broker.BrokerRawDelivery
import io.outfoxx.sunday.broker.BrokerSendSpec
import io.test.service.EventsBroker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking

/** Exercises the compiled broker facade with recording transports. */
object BrokerValidationCheck {
  /** Checks publication validation and caller-owned acknowledgement for each validation mode. */
  @JvmStatic
  fun verify(namespace: String) = runBlocking {
    val sent = mutableListOf<BrokerMessage>()
    val producer = object : BrokerProducer {
      override suspend fun send(spec: BrokerSendSpec, message: BrokerMessage) {
        sent += message
      }
    }
    var acknowledgements = 0
    val raw = object : BrokerRawDelivery {
      override val message = BrokerMessage("""{"kind":"future-kind"}""".encodeToByteArray(), "application/json")
      override val exchange = "example.events"
      override val routingKey = "example.changed"
      override val redelivered = false
      override suspend fun ack() { acknowledgements++ }
      override suspend fun nack(requeue: Boolean) { error("unexpected nack") }
    }
    val consumer = object : BrokerConsumer {
      override fun consume(spec: BrokerConsumeSpec): Flow<BrokerRawDelivery> = flowOf(raw)
    }
    val broker = EventsBroker(producer, consumer, BrokerMessageCodec())
    val delivery = broker.consumeEvents().single()
    check(delivery.raw === raw)
    check(acknowledgements == 0)
    val unknown = delivery.body
    check(unknown.kind.wireValue == "future-kind")
    val known = Event(EventKind.Created)
    broker.sendEvent(known)
    broker.sendBatch(listOf(known))
    broker.sendCodes(listOf("ABC"))
    broker.sendCodeMap(mapOf("code" to "ABC"))
    check(sent.size == 4)
    suspend fun rejected(block: suspend () -> Unit) {
      val before = sent.size
      val failure = runCatching { block() }.exceptionOrNull()
      check(failure?.javaClass?.name == "$namespace.validation.ConstraintViolationException") {
        "Expected outbound validation failure, got $failure"
      }
      check(sent.size == before) { "Invalid payload reached the producer" }
    }
    if (namespace == "none") {
      broker.sendEvent(unknown)
      broker.sendCodes(listOf("x"))
      broker.sendCodeMap(mapOf("code" to "x"))
      check(sent.size == 7)
    } else {
      rejected { broker.sendCodes(listOf("x")) }
      rejected { broker.sendCodeMap(mapOf("code" to "x")) }
      rejected { broker.sendEvent(unknown) }
      rejected { broker.sendBatch(listOf(unknown)) }
      rejected { broker.sendBatch(emptyList()) }
    }
    check(acknowledgements == 0)
  }
}
