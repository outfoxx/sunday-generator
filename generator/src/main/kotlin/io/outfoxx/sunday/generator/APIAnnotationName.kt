package io.outfoxx.sunday.generator

enum class APIAnnotationName(val id: String, private val modeSpecific: Boolean) {

  ServiceGroup("group", false),

  KotlinPkg("kotlinPackage", true),
  KotlinModelPkg("kotlinModelPackage", true),

  KotlinType("kotlinType", true),
  KotlinImpl("kotlinImplementation", true),

  TypeScriptModule("typeScriptModule", false),
  TypeScriptModelModule("typeScriptModelModule", false),

  TypeScriptType("typeScriptType", true),
  TypeScriptImpl("typeScriptImplementation", true),

  SwiftType("swiftType", true),
  SwiftImpl("swiftImplementation", true),

  Nested("nested", false),

  ExternalDiscriminator("externalDiscriminator", false),
  ExternallyDiscriminated("externallyDiscriminated", false),

  Patchable("patchable", false),

  RequestOnly("requestOnly", false),
  ResponseOnly("responseOnly", false),

  ProblemBaseUri("problemBaseUri", false),
  ProblemBaseUriParams("problemUriParams", false),
  ProblemTypes("problemTypes", false),
  Problems("problems", false),

  // Sunday
  EventSource("eventSource", false),
  EventStream("eventStream", false),

  // JAX-RS
  Asynchronous("asynchronous", false),
  Reactive("reactive", false),
  SSE("sse", false),

  ;

  fun matches(test: String, generationMode: GenerationMode? = null) =
    if (modeSpecific && generationMode != null) {
      test == "$id:${generationMode.name.toLowerCase()}" || test == "sunday-$id-${generationMode.name.toLowerCase()}"
    } else {
      test == id || test == "sunday-$id"
    }

  override fun toString() = id

}
