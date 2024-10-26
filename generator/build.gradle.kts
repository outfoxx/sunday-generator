plugins {
  `java-library`
}

dependencies {

  api(libs.amfClient)

  api(libs.kotlinPoet)
  api(libs.typeScriptPoet)
  api(libs.swiftPoet)

  //
  // TESTING
  //

  // START: generated code dependencies
  testImplementation(libs.jackson)
  testImplementation(libs.sundayKt)
  testImplementation(libs.javaxJaxrs)
  testImplementation(libs.jakartaJaxrs)
  testImplementation(libs.validation)
  testImplementation(libs.zalandoProblem)
  testImplementation(libs.mutiny)
  testImplementation(libs.rxJava3)
  testImplementation(libs.rxJava2)
  testImplementation(libs.quarkusRest)
  // END: generated code dependencies

  testImplementation(libs.slf4j)

  testImplementation(libs.junit)
  testImplementation(libs.junitParams)
  testRuntimeOnly(libs.junitEngine)

  testImplementation(libs.hamcrest)

  testImplementation(libs.dockerJava)
  testImplementation(libs.dockerJavaTransport)
  testImplementation(libs.kotlinCompileTesting)

  testImplementation(libs.jcolor)
  testImplementation(libs.jimfs)
}

tasks {
  javadoc {
    include("io/outfoxx/**")
  }
}

publishing {
  publications {
    create<MavenPublication>("generator") {

      from(components["java"])

      pom {

        name.set("Sunday Generator")
        description.set(
          "Sunday Generator is a code generator for Sunday HTTP clients and JAX-RS server stubs in multiple languages.",
        )
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

signing {
  sign(publishing.publications.named("generator").get())
}
