
plugins {
  kotlin("jvm")
}

allprojects {

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

}
