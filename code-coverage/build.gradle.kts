plugins {
  base
  alias(libs.plugins.kover)
}

dependencies {
  kover(project(":generator"))
  kover(project(":cli"))
  kover(project(":gradle-plugin"))
}

tasks {
  check {
    finalizedBy(named("koverXmlReport"), named("koverHtmlReport"))
  }
}
