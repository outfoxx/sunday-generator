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
      mavenCoordinates {
        groupId = "io.outfoxx.sunday"
        artifactId = "gradle-plugin"
      }
    }
  }
}

publishing {
  publications {
    val libraryPub = create<MavenPublication>("library") {
      project.shadow.component(this)
      artifact(tasks.javadocJar)
    }

    // Recreate plugin publishing from MavenPluginPublishPlugin, referencing our shadow jar publication
    val pluginDeclaration = gradlePlugin.plugins.first()
    publications.create<MavenPublication>(pluginDeclaration.name + "PluginMarkerMaven") {
      val internal = this as MavenPublicationInternal
      internal.isAlias = true
      internal.artifactId = pluginDeclaration.id + PLUGIN_MARKER_SUFFIX
      internal.groupId = pluginDeclaration.id
      internal.pom.withXml {
        val root = asElement()
        val document = root.ownerDocument
        val dependencies = root.appendChild(document.createElement("dependencies"))
        val dependency = dependencies.appendChild(document.createElement("dependency"))

        dependency.appendChild(document.createElement("groupId")).textContent = libraryPub.groupId
        dependency.appendChild(document.createElement("artifactId")).textContent = libraryPub.artifactId
        dependency.appendChild(document.createElement("version")).textContent = libraryPub.version
      }
      internal.pom.name.set(pluginDeclaration.displayName)
      internal.pom.description.set(pluginDeclaration.description)
    }
  }
}
