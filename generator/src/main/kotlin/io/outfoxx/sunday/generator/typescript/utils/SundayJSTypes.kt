package io.outfoxx.sunday.generator.typescript.utils

import io.outfoxx.typescriptpoet.TypeName

const val SUNDAY_PKG = "@outfoxx/sunday"
val REQUEST_FACTORY = TypeName.namedImport("RequestFactory", SUNDAY_PKG)
val EVENT_TYPES = TypeName.namedImport("EventTypes", SUNDAY_PKG)
val ANY_TYPE = TypeName.namedImport("AnyType", SUNDAY_PKG)
val OFFSET_DATETIME = TypeName.namedImport("OffsetDateTime", SUNDAY_PKG)
val LOCAL_DATETIME = TypeName.namedImport("LocalDateTime", SUNDAY_PKG)
val LOCAL_DATE = TypeName.namedImport("LocalDate", SUNDAY_PKG)
val LOCAL_TIME = TypeName.namedImport("LocalTime", SUNDAY_PKG)
val DURATION = TypeName.namedImport("Duration", SUNDAY_PKG)
val PROBLEM = TypeName.namedImport("Problem", SUNDAY_PKG)
val MEDIA_TYPE = TypeName.namedImport("MediaType", SUNDAY_PKG)
val URL_TEMPLATE = TypeName.namedImport("URLTemplate", SUNDAY_PKG)
