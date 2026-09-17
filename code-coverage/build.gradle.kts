plugins {
  base
  alias(libs.plugins.kover)
}

dependencies {
  kover(project(":generator"))
  kover(project(":cli"))
  kover(project(":gradle-plugin"))
  kover(project(":integration-tests:quarkus"))
}

tasks {
  check {
    finalizedBy(named("koverXmlReport"), named("koverHtmlReport"))
  }
}
