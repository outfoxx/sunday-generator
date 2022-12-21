import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.cadixdev.gradle.licenser.LicenseExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.dokka")
  id("com.github.breadmoirai.github-release")
  id("org.sonarqube")
  id("io.github.gradle-nexus.publish-plugin")

  kotlin("jvm") apply false
  id("org.cadixdev.licenser") apply false
  id("org.jmailen.kotlinter") apply false
  id("io.gitlab.arturbosch.detekt") apply (false)
  id("com.github.johnrengelman.shadow") apply false
}

val ignoreCheckFailures = project.findProperty("ignoreCheckFailures")?.toString()?.toBoolean() ?: false

val moduleNames = listOf( "generator", "cli", "gradle-plugin")

val releaseVersion: String by project
val isSnapshot = releaseVersion.endsWith("SNAPSHOT")

val kotlinVersion: String by project
val javaVersion: String by project


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
  apply(plugin = "org.cadixdev.licenser")
  apply(plugin = "org.jmailen.kotlinter")
  apply(plugin = "io.gitlab.arturbosch.detekt")

  //
  // COMPILE
  //

  configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    withSourcesJar()
    withJavadocJar()
  }

  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      kotlinOptions {
        languageVersion = kotlinVersion
        apiVersion = kotlinVersion
      }
      jvmTarget = javaVersion
    }
  }

  //
  // TEST
  //

  configure<JacocoPluginExtension> {
    toolVersion = "0.8.8"
  }

  tasks.named<Test>("test").configure {

    useJUnitPlatform()

    if (System.getenv("CI").isNullOrBlank()) {
      testLogging {
        events("passed", "skipped", "failed")
      }
    }

    reports.junitXml.required.set(true)

    finalizedBy("jacocoTestReport")
  }


  //
  // CHECKS
  //

  configure<LicenseExtension> {
    header.set(resources.text.fromFile(file("${rootProject.layout.projectDirectory}/HEADER.txt")))
    include("**/*.kt")
    ignoreFailures.set(ignoreCheckFailures)
  }

  configure<DetektExtension> {
    source = files("src/main/kotlin")

    config = files("${rootProject.layout.projectDirectory}/src/main/detekt/detekt.yml")
    buildUponDefaultConfig = true
    baseline = file("src/main/detekt/detekt-baseline.xml")
    ignoreFailures = ignoreCheckFailures
  }

  tasks.withType<Detekt>().configureEach {
    jvmTarget = javaVersion
  }

  configure<org.jmailen.gradle.kotlinter.KotlinterExtension> {
    ignoreFailures = ignoreCheckFailures
  }


  //
  // DOCS
  //

  tasks.named<DokkaTask>("dokkaHtml") {
    failOnWarning.set(true)
    suppressObviousFunctions.set(false)
    outputDirectory.set(file("$buildDir/dokka/${project.version}"))
  }

  tasks.named<DokkaTask>("dokkaJavadoc") {
    failOnWarning.set(true)
    suppressObviousFunctions.set(false)
    outputDirectory.set(tasks.named<Javadoc>("javadoc").get().destinationDir)
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
    val docDir = buildDir.resolve("dokka")
    val relDocDir = docDir.resolve(releaseVersion)
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
          into(docDir.resolve("current"))
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

  //
  // ANALYSIS
  //

  sonarqube {
    properties {
      property("sonar.sources", "src/main")
      property("sonar.tests", "src/test")
      property("sonar.kotlin.detekt.reportPaths", "build/reports/detekt/detekt.xml")
      property("sonar.kotlin.ktlint.reportPaths", "build/reports/ktlint/main-lint.xml")
      property("sonar.junit.reportPaths", "build/test-results/test")
      property("sonar.jacoco.reportPath", "")
      property("sonar.jacoco.reportPaths", "")
      property(
        "sonar.coverage.jacoco.xmlReportPaths",
        "$rootDir/code-coverage/build/reports/jacoco/testCoverageReport/testCoverageReport.xml",
      )
    }
  }

}


//
// ANALYSIS
//

sonarqube {
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
  draft(false)
  prerelease(!releaseVersion.matches("""^\d+\.\d+\.\d+$""".toRegex()))
  releaseAssets(
    moduleNames.flatMap { moduleName ->
      val baseSuffix = if (moduleName == "gradle-plugin") "" else "-all"
      listOf(baseSuffix, "-javadoc", "-sources").map { suffix ->
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
