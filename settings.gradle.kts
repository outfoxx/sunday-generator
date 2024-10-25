dependencyResolutionManagement {
  versionCatalogs {
    create("libs")
  }
}

rootProject.name = "sunday-generator"

include(
  "generator",
  "cli",
  "gradle-plugin",
  "code-coverage",
)
