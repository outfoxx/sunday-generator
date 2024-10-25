import dev.yumi.gradle.licenser.YumiLicenserGradleExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.versions)
  alias(libs.plugins.versionCatalogUpdate)
  alias(libs.plugins.dokka)
  alias(libs.plugins.githubRelease)
  alias(libs.plugins.sonarqube)
  alias(libs.plugins.nexusPublish)

  alias(libs.plugins.kotlin) apply false
  alias(libs.plugins.licenser) apply false
  alias(libs.plugins.shadow) apply false
}

val ignoreCheckFailures = project.findProperty("ignoreCheckFailures")?.toString()?.toBoolean() ?: false

val moduleNames = listOf( "generator", "cli", "gradle-plugin")

val releaseVersion: String by project
val isSnapshot = releaseVersion.endsWith("SNAPSHOT")

val javaVersion = libs.versions.javaLanguage.get()
val kotlinVersion = libs.versions.kotlinLanguage.get()


allprojects {

  group = "io.outfoxx.sunday"
  version = releaseVersion

  repositories {
    mavenCentral()
    maven {
      setUrl("https://repository-master.mulesoft.org/nexus/content/repositories/releases")
    }
    maven {
      setUrl("https://jitpack.io")
    }
  }

}


configure(moduleNames.map { project(it) }) {

  apply(plugin = "java-library")
  apply(plugin = "jacoco")
  apply(plugin = "maven-publish")
  apply(plugin = "signing")

  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "dev.yumi.gradle.licenser")

  //
  // COMPILE
  //

  configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)

    withSourcesJar()
    withJavadocJar()
  }

  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions{
      apiVersion.set(KotlinVersion.fromVersion(kotlinVersion))
      languageVersion.set(KotlinVersion.fromVersion(kotlinVersion))
      jvmTarget.set(JvmTarget.fromTarget(javaVersion))
    }
  }

  //
  // TEST
  //

  configure<JacocoPluginExtension> {
    toolVersion = rootProject.libs.versions.jacocoTool.get()
  }

  tasks.named<Test>("test").configure {

    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic")
    systemProperty("junit.jupiter.execution.parallel.config.dynamic.factor", "3")

    if (System.getenv("CI").isNullOrBlank()) {
      testLogging {
        events("passed", "skipped", "failed")
      }
    }

    reports.junitXml.required.set(true)

    finalizedBy("jacocoTestReport")
  }

  //
  // ANALYSIS
  //

  sonar {
    properties {
      property(
        "sonar.coverage.jacoco.xmlReportPaths",
        "$rootDir/code-coverage/build/reports/jacoco/testCoverageReport/testCoverageReport.xml",
      )
    }
  }

  //
  // CHECKS
  //

  configure<YumiLicenserGradleExtension> {
    rule(file("${rootProject.layout.projectDirectory}/HEADER.txt"))
    include("**/*.kt")
  }


  //
  // DOCS
  //

  tasks.named<DokkaTask>("dokkaHtml") {
    failOnWarning.set(true)
    suppressObviousFunctions.set(false)
    outputDirectory.set(file("${layout.buildDirectory}/dokka/${project.version}"))
  }

  tasks.named<DokkaTask>("dokkaJavadoc") {
    failOnWarning.set(true)
    suppressObviousFunctions.set(false)
    outputDirectory.set(layout.dir(tasks.named<Javadoc>("javadoc").map { it.destinationDir!! }))
  }

  tasks.named<Javadoc>("javadoc").configure {
    dependsOn("dokkaJavadoc")
  }


  //
  // SIGNING
  //

  configure<SigningExtension> {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
  }

  tasks.withType<Sign>().configureEach {
    onlyIf { !isSnapshot }
  }
}


//
// DOCS
//

tasks {
  dokkaHtmlMultiModule.configure {
    val docDir = layout.buildDirectory.dir("dokka")
    val relDocDir = docDir.map { it.dir(releaseVersion) }
    outputDirectory.set(relDocDir)
    doLast {
      // Copy versioned sunday.raml
      copy {
        into(relDocDir)
        from("generator/src/main/resources") {
          include("sunday.raml")
        }
      }
      // For major.minor.patch releases, add sunday.raml as current
      // and add docs in "current" directory
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
  }
}


//
// RELEASING
//

githubRelease {
  owner("outfoxx")
  repo("sunday-generator")
  tagName(releaseVersion)
  targetCommitish("main")
  releaseName("🚀 v$releaseVersion")
  generateReleaseNotes(true)
  draft(false)
  prerelease(!releaseVersion.matches("""^\d+\.\d+\.\d+$""".toRegex()))
  releaseAssets(
    moduleNames.flatMap { moduleName ->
      listOf("", "-javadoc", "-sources").map { suffix ->
        file("$rootDir/$moduleName/build/libs/$moduleName-$releaseVersion$suffix.jar")
      }
    }
  )
  overwrite(true)
  authorization(
    "Token " + (project.findProperty("github.token") as String? ?: System.getenv("GITHUB_TOKEN"))
  )
}

nexusPublishing {
  repositories {
    sonatype()
  }
}
