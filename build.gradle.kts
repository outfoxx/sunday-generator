import net.minecrell.gradle.licenser.LicenseExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.KotlinterExtension

plugins {
  kotlin("jvm") apply false
  id("org.jetbrains.dokka")
  id("net.minecrell.licenser") apply false
  id("org.jmailen.kotlinter") apply false
  id("com.github.johnrengelman.shadow") apply false
}

repositories {
  mavenCentral()
  jcenter()
}

val releaseVersion: String by project
val isSnapshot = releaseVersion.endsWith("SNAPSHOT")

subprojects {

  apply(plugin = "maven-publish")
  apply(plugin = "java")
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "jacoco")
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "net.minecrell.licenser")
  apply(plugin = "org.jmailen.kotlinter")
  apply(plugin = "com.github.johnrengelman.shadow")
  apply(plugin = "signing")

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


  //
  // PUBLISHING
  //

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
      maven {
        name = "MavenCentral"
        val snapshotUrl = "https://oss.sonatype.org/content/repositories/snapshots/"
        val releaseUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
        url = uri(if (isSnapshot) snapshotUrl else releaseUrl)
        credentials {
          username = project.findProperty("ossrhUsername")?.toString()
          password = project.findProperty("ossrhPassword")?.toString()
        }
      }
    }
  }

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
    val docDir = buildDir.resolve("dokka/$releaseVersion")
    outputDirectory.set(docDir)
    doLast {
      copy {
        into(docDir)
        from("generator/src/main/resources") {
          include("sunday.raml")
        }
      }
    }
  }
}
