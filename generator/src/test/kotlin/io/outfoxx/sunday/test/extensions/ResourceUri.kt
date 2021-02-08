package io.outfoxx.sunday.test.extensions

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@Target(VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class ResourceUri(
  val value: String
)
