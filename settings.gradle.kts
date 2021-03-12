
pluginManagement {

  val kotlinPluginVersion: String by settings
  val jibPluginVersion: String by settings
  val shadowPluginVersion: String by settings
  val dokkaPluginVersion: String by settings
  val licenserPluginVersion: String by settings
  val kotlinterPluginVersion: String by settings

  plugins {
    kotlin("jvm") version kotlinPluginVersion
    id("com.google.cloud.tools.jib") version jibPluginVersion
    id("com.github.johnrengelman.shadow") version shadowPluginVersion
    id("org.jetbrains.dokka") version dokkaPluginVersion
    id("net.minecrell.licenser") version licenserPluginVersion
    id("org.jmailen.kotlinter") version kotlinterPluginVersion
  }

}

rootProject.name = "sunday-generator"

include(
  "generator",
  "cli",
  "gradle-plugin"
)
