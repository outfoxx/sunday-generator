import net.minecrell.gradle.licenser.LicenseExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.KotlinterExtension

plugins {
  kotlin("jvm") apply false
  id("org.jetbrains.dokka")
  id("net.minecrell.licenser") apply false
  id("org.jmailen.kotlinter") apply false
}

repositories {
  mavenCentral()
  jcenter()
}

val releaseVersion: String by project

subprojects {

  apply(plugin = "maven-publish")
  apply(plugin = "java")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "jacoco")
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "net.minecrell.licenser")
  apply(plugin = "org.jmailen.kotlinter")

  group = "io.outfoxx.sunday"
  version = releaseVersion

  repositories {
    mavenCentral()
    maven {
      setUrl("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    jcenter()
    maven {
      setUrl("https://repository-master.mulesoft.org/nexus/content/repositories/releases")
    }
    maven {
      setUrl("https://jitpack.io")
    }
  }



  //
  // COMPILE
  //

  configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8

    withSourcesJar()
    withJavadocJar()
  }

  tasks {

    withType<KotlinCompile> {
      kotlinOptions {
        jvmTarget = "11"
      }
    }

  }

  //
  // TEST
  //

  configure<JacocoPluginExtension> {
    toolVersion = "0.8.5"
  }

  tasks {
    withType<Test> {
      useJUnitPlatform()
    }
  }


  //
  // DOCS
  //

  tasks {
    withType<DokkaTask> {
      outputDirectory.set(file("$buildDir/javadoc/${project.version}"))
    }

    withType<Javadoc> {
      dependsOn(named("dokkaHtml"))
    }
  }


  //
  // CHECKS
  //

  configure<KotlinterExtension> {
    indentSize = 2
  }

  configure<LicenseExtension> {
    header = file("${rootDir}/HEADER.txt")
    include("**/*.kt")
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
