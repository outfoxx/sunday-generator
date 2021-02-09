
val releaseVersion: String by project

subprojects {

  apply(plugin = "maven-publish")
  apply(plugin = "java")

  group = "io.outfoxx.sunday"
  version = releaseVersion

  repositories {
    maven {
      setUrl("https://repository-master.mulesoft.org/nexus/content/repositories/releases")
    }
    maven {
      setUrl("https://repository-master.mulesoft.org/nexus/content/repositories/snapshots")
    }
    maven {
      setUrl("https://jitpack.io")
    }
    mavenCentral()
  }

  configure<PublishingExtension> {
    repositories {
      maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/outfoxx/sunday-generator")
        credentials {
          username = project.findProperty("github.user") as String? ?: System.getenv("USERNAME")
          password = project.findProperty("github.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
      }
    }
    publications {
      create<MavenPublication>("gpr") {
        from(components["java"])
      }
    }
  }

}
