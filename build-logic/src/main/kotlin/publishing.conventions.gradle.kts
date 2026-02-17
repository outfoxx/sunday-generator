plugins {
  base
  id("com.vanniktech.maven.publish")
}

mavenPublishing {
  pom {
    name.set(project.name)
  }
}
