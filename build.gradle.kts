import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  alias(libs.plugins.versions)
  alias(libs.plugins.versionCatalogUpdate)
  alias(libs.plugins.sonarqube)
  alias(libs.plugins.dokka)
  alias(libs.plugins.githubRelease)
  alias(libs.plugins.kotlin.jvm) apply false // Required by dokka
  alias(libs.plugins.vanniktech.maven.publish) apply false // Required by itself
}

allprojects {
  configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
      substitute(module("com.github.everit-org.json-schema:org.everit.json.schema"))
        .using(module("com.github.erosb:everit-json-schema:1.12.2"))
        .because("Use Maven Central coordinates instead of JitPack")
    }
  }
}

val moduleNames = listOf("generator", "cli", "gradle-plugin")

val releaseVersion: String by project
val isSnapshot = releaseVersion.endsWith("SNAPSHOT")


//
// DOCS
//

dependencies {
  dokka(project(":generator"))
  dokka(project(":cli"))
  dokka(project(":gradle-plugin"))
}

dokka {
  dokkaPublications.html {
    outputDirectory.set(layout.buildDirectory.dir("dokka/${releaseVersion}"))
  }
}

tasks {
  dokkaGenerate.configure {
    val docDir = layout.buildDirectory.dir("dokka")
    val relDocDir = docDir.map { it.dir(releaseVersion) }
    doLast {
      // Copy versioned sunday.raml
      copy {
        into(relDocDir)
        from("generator/src/main/resources") {
          include("sunday.raml")
        }
      }
      // Copy JBang script
      copy {
        into(relDocDir)
        from("scripts") {
          if (isSnapshot) {
            include("sunday-snapshot")
            rename("sunday-snapshot", "sunday")
          } else {
            include("sunday")
          }
        }
        filter<ReplaceTokens>(
          "tokens" to mapOf("env.VER" to releaseVersion),
          "beginToken" to "\${",
          "endToken" to "}",
        )
      }
      // For major.minor.patch releases, add sunday.raml as current
      // and add docs in the "current" directory
      if (releaseVersion.matches("""^\d+.\d+.\d+$""".toRegex())) {
        copy {
          into(docDir.map { it.dir("current") })
          from(relDocDir) {
            include("**")
          }
        }
        copy {
          into(docDir)
          from("generator/src/main/resources") {
            include("sunday.raml")
          }
        }
      }
    }
  }
}


//
// ANALYSIS
//

sonar {
  properties {
    property("sonar.projectName", "sunday-generator")
    property("sonar.projectKey", "outfoxx_sunday-generator")
    property("sonar.organization", "outfoxx")
    property("sonar.host.url", "https://sonarcloud.io")
    property(
      "sonar.coverage.jacoco.xmlReportPaths",
      "$rootDir/code-coverage/build/reports/kover/report.xml",
    )
  }
}

tasks.named("sonar") {
  dependsOn(":code-coverage:koverXmlReport")
}


//
// RELEASING
//

githubRelease {
  copy {
    into(layout.buildDirectory.dir("scripts"))
    from("scripts") {
      include("sunday")
    }
    filter<ReplaceTokens>(
      "tokens" to mapOf("env.VER" to releaseVersion),
      "beginToken" to "\${",
      "endToken" to "}",
    )
  }

  owner = "outfoxx"
  repo = "sunday-generator"
  tagName = releaseVersion
  targetCommitish = "main"
  releaseName = "🚀 v$releaseVersion"
  generateReleaseNotes = true
  draft = false
  prerelease = !releaseVersion.matches("""^\d+\.\d+\.\d+$""".toRegex())
  releaseAssets.from(
    moduleNames.flatMap { moduleName ->
      listOf("", "-javadoc", "-sources").map { suffix ->
        file("$rootDir/$moduleName/build/libs/$moduleName-$releaseVersion$suffix.jar")
      }
    } + layout.buildDirectory.dir("scripts").get().file("sunday"),
  )
  overwrite = true
  authorization = "Token " + (project.findProperty("github.token") as String? ?: System.getenv("GITHUB_TOKEN"))
}
