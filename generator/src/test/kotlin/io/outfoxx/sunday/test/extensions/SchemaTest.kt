package io.outfoxx.sunday.test.extensions

import io.outfoxx.sunday.generator.kotlin.SchemaMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION

@ParameterizedTest
@EnumSource(SchemaMode::class)
@Retention(RUNTIME)
@Target(FUNCTION)
annotation class SchemaTest
