plugins {
  id("common.conventions")
  alias(libs.plugins.quarkus)
}

val nativeSecurityOnly = providers.gradleProperty("nativeSecurityOnly").map(String::toBoolean).getOrElse(false)

dependencies {
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:${libs.versions.quarkus.rest.get()}"))
  implementation("io.quarkus:quarkus-kotlin")
  implementation("io.quarkus:quarkus-rest")
  implementation("io.quarkus:quarkus-oidc")
  implementation("io.quarkus:quarkus-rest-client-oidc-filter")
  implementation("io.quarkus:quarkus-rest-client-oidc-token-propagation")
  implementation("io.outfoxx.sunday:sunday-jaxrs-quarkus:${libs.versions.sundayKt.get()}")
  if (!nativeSecurityOnly) implementation(project(":integration-tests:quarkus-security-provider"))
  (
    if (nativeSecurityOnly) {
      kotlin.sourceSets.test { kotlin.exclude("**/ExplicitSecurityOverrideTest.kt") }
      listOf(
        "native",
        "client",
      )
    } else {
      listOf("first", "second", "native", "client", "web")
    }
  ).forEach { name ->
    implementation(project(path = ":integration-tests:quarkus-security-contracts", configuration = "${name}Elements"))
  }
  testImplementation("io.quarkus:quarkus-junit5")
  testImplementation("io.smallrye:smallrye-jwt-build")
  testImplementation(libs.junit)
  testImplementation("io.strikt:strikt-core:0.35.1")
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.junitPlatform)
}

tasks.test { systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager") }

kotlin.compilerOptions {
  allWarningsAsErrors.set(true)
  freeCompilerArgs.addAll("-Xannotation-default-target=param-property", "-Xemit-jvm-type-annotations")
}

if (nativeSecurityOnly) {
  kotlin.sourceSets.main { kotlin.exclude("**/FirstDelegate.kt", "**/SecondDelegate.kt", "**/WebDelegate.kt") }
  tasks.test {
    systemProperty("quarkus.http.auth.proactive", "true")
    systemProperty("fixture.native-only", "true")
  }
}
