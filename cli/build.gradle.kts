import com.github.jengelman.gradle.plugins.shadow.ShadowExtension

plugins {
  application
  id("com.google.cloud.tools.jib")
}

val cliktVersion: String by project
val slf4jVersion: String by project

val junitVersion: String by project
val hamcrestVersion: String by project

dependencies {

  implementation(project(path = ":generator", configuration = "shadow"))

  implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
  implementation("org.slf4j:slf4j-jdk14:$slf4jVersion")

  //
  // TESTING
  //

  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")

}


application {
  applicationName = "sunday-generator"
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  mainClassName = "io.outfoxx.sunday.generator.MainKt"
}


publishing {
  publications {
    create<MavenPublication>("cli") {
      the<ShadowExtension>().component(this)
      artifact(tasks.named("sourcesJar"))
      artifact(tasks.named("javadocJar"))

      pom {

        name.set("Sunday Generator - CLI")
        description.set("Sunday Generator is a code generator for Sunday HTTP clients and JAX-RS server stubs in multiple languages.")
        url.set("https://outfoxx.github.io/sunday-generator")

        organization {
          name.set("Outfox, Inc.")
          url.set("https://outfoxx.io")
        }

        issueManagement {
          system.set("GitHub")
          url.set("https://github.com/outfoxx/sunday-generator/issues")
        }

        licenses {
          license {
            name.set("Apache License 2.0")
            url.set("https://raw.githubusercontent.com/outfoxx/sunday-generator/main/LICENSE.txt")
            distribution.set("repo")
          }
        }

        scm {
          url.set("https://github.com/outfoxx/sunday-generator")
          connection.set("scm:https://github.com/outfoxx/sunday-generator.git")
          developerConnection.set("scm:git@github.com:outfoxx/sunday-generator.git")
        }

        developers {
          developer {
            id.set("kdubb")
            name.set("Kevin Wooten")
            email.set("kevin@outfoxx.io")
          }
        }

      }
    }
  }
}


jib {
  to {
    image = "docker.pkg.github.com/outfoxx/sunday-generator/sunday-generator:${project.version}"
    auth {
      username = project.findProperty("github.user") as String? ?: System.getenv("USERNAME")
      password = project.findProperty("github.token") as String? ?: System.getenv("GITHUB_TOKEN")
    }
  }
  from {
    image = "openjdk:14-jdk-alpine"
  }
  containerizingMode = "packaged"
}
