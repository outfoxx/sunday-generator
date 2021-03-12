
plugins {
  kotlin("jvm")
  `java-gradle-plugin`
}

val junitVersion: String by project
val hamcrestVersion: String by project
val kotlinCompileTestingVersion: String by project

dependencies {

  compileOnly(localGroovy())

  implementation(project(":generator"))

  //
  // TESTING
  //

  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")

  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:$kotlinCompileTestingVersion")
}

gradlePlugin {
  plugins {
    register("sunday") {
      id = "io.outfoxx.sunday-generator"
      implementationClass = "io.outfoxx.sunday.generator.gradle.SundayGeneratorPlugin"
    }
  }
}

tasks {

  withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
  }

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "11"
      javaParameters = true
      freeCompilerArgs = freeCompilerArgs + "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes"
    }
  }

  test {
    useJUnitPlatform()
    environment("ANDROID_HOME", project.projectDir.toPath().resolve("src/test/android/tinysdk"))
  }

}
