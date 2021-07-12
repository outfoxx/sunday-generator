import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX

plugins {
  `java-gradle-plugin`
  `maven-publish`
  id("com.gradle.plugin-publish")
  id("com.github.johnrengelman.shadow")
}

val junitVersion: String by project
val hamcrestVersion: String by project
val kotlinCompileTestingVersion: String by project

dependencies {

  shadow(gradleApi())

  implementation(project(path = ":generator"))

  //
  // TESTING
  //

  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")

  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:$kotlinCompileTestingVersion")
}

tasks.shadowJar.configure {
  archiveClassifier.set("")
  dependencies {
    exclude(dependency("org.jetbrains.kotlin:.*"))
  }
  minimize()
}

tasks.publishPlugins.configure {
  dependsOn(tasks.shadowJar)
  useAutomatedPublishing()
}


gradlePlugin {
  isAutomatedPublishing = false
  plugins {
    register("sunday") {
      id = "io.outfoxx.sunday-generator"
      implementationClass = "io.outfoxx.sunday.generator.gradle.SundayGeneratorPlugin"
    }
  }
}

pluginBundle {
  website = "https://outfoxx.github.io/sunday"
  vcsUrl = "https://github.com/outfoxx/sunday-generator"
  tags = setOf("sunday", "raml", "kotlin", "swift", "typescript")

  plugins {
    named("sunday") {
      displayName = "Sunday Generator - Gradle Plugin"
      description = "Sunday Generator is a code generator for Sunday HTTP clients and JAX-RS server stubs in multiple languages."
    }
  }
}

publishing {
  publications {
    register<MavenPublication>("pluginMaven").configure {
      // Remove existing "dependencies" node
      pom {
        withXml {
          val pomNode = asNode()
          (pomNode.get("dependencies") as groovy.util.NodeList).forEach {
            pomNode.remove(it as groovy.util.Node)
          }
        }
      }

      project.shadow.component(this)
      groupId = "io.outfoxx.sunday"
      artifactId = "gradle-plugin"
    }
  }
}
