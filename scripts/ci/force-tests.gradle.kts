// Keep compilation incremental while forcing every verification JVM to execute for timing comparisons.
allprojects {
  tasks.withType<Test>().configureEach { outputs.upToDateWhen { false } }
}
