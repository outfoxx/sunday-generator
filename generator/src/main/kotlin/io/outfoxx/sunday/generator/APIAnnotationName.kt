package io.outfoxx.sunday.generator

enum class APIAnnotationName(val id: String, private val modeSpecific: Boolean) {

  ServiceGroup("group", false),

  JavaPkg("javaPackage", true),
  JavaModelPkg("javaModelPackage", true),

  KotlinType("kotlinType", true),
  KotlinImpl("kotlinImplementation", true),

  JavaType("javaType", true),
  JavaImpl("javaImplementation", true),

  SwiftType("swiftType", true),
  SwiftImpl("swiftImplementation", true),

  TypeScriptType("typeScriptType", true),
  TypeScriptImpl("typeScriptImplementation", true),

  Nested("nested", false),

  ExternalDiscriminator("externalDiscriminator", false),
  ExternallyDiscriminated("externallyDiscriminated", false),

  Patchable("patchable", false),

  Asynchronous("asynchronous", false),
  Reactive("reactive", false),

  SSE("sse", false),

  ProblemBaseUri("problemBaseUri", false),
  ProblemBaseUriParams("problemUriParams", false),
  ProblemTypes("problemTypes", false),
  Problems("problems", false),

  ;

  fun matches(test: String, generationMode: GenerationMode? = null) =
    if (modeSpecific && generationMode != null) {
      test == "$id:${generationMode.name.toLowerCase()}" || test == "sunday-$id-${generationMode.name.toLowerCase()}"
    } else {
      test == id || test == "sunday-$id"
    }

}
