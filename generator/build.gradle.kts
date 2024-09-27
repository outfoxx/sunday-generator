
plugins {
  `java-library`
}

val slf4jVersion: String by project

val amfClientVersion: String by project

val kotlinPoetVersion: String by project
val typeScriptPoetVersion: String by project
val swiftPoetVersion: String by project

val kotlinCoroutinesVersion: String by project

val junitVersion: String by project
val hamcrestVersion: String by project
val kotlinCompileTestingVersion: String by project
val dockerJavaVersion: String by project

val jcolorVersion: String by project
val jimfsVersion: String by project

// Test runtime deps
val jakartaJaxrsVersion: String by project
val javaxJaxrsVersion: String by project

val sundayKtVersion: String by project

val jacksonVersion: String by project

val validationVersion: String by project
val zalandoProblemVersion: String by project
val mutinyVersion: String by project
val rxJava3Version: String by project
val rxJava2Version: String by project


configurations.compileClasspath {
  resolutionStrategy {
    force("org.scala-lang:scala-library:2.12.10")
  }
}

dependencies {

  api("com.github.amlorg:amf-api-contract_2.12:$amfClientVersion")

  api("com.squareup:kotlinpoet:$kotlinPoetVersion")
  api("io.outfoxx:typescriptpoet:$typeScriptPoetVersion")
  api("io.outfoxx:swiftpoet:$swiftPoetVersion")

  //
  // TESTING
  //

  // START: generated code dependencies
  testImplementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
  testImplementation("io.outfoxx.sunday:sunday-core:$sundayKtVersion")
  testImplementation("org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.1_spec:$javaxJaxrsVersion")
  testImplementation("jakarta.ws.rs:jakarta.ws.rs-api:$jakartaJaxrsVersion")
  testImplementation("javax.validation:validation-api:$validationVersion")
  testImplementation("org.zalando:problem:$zalandoProblemVersion")
  testImplementation("io.smallrye.reactive:mutiny:$mutinyVersion")
  testImplementation("io.reactivex.rxjava3:rxjava:$rxJava3Version")
  testImplementation("io.reactivex.rxjava2:rxjava:$rxJava2Version")
  // END: generated code dependencies

  testImplementation("org.slf4j:slf4j-jdk14:$slf4jVersion")

  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")

  testImplementation("com.github.docker-java:docker-java-core:$dockerJavaVersion")
  testImplementation("com.github.docker-java:docker-java-transport-httpclient5:$dockerJavaVersion")
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:$kotlinCompileTestingVersion")

  testImplementation("com.diogonunes:JColor:$jcolorVersion")
  testImplementation("com.google.jimfs:jimfs:$jimfsVersion")
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
