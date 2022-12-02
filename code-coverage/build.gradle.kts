
plugins {
  base
  id("jacoco-report-aggregation")
}

repositories {
  mavenCentral()
}

dependencies {
  jacocoAggregation(project(":generator"))
  jacocoAggregation(project(":cli"))
  jacocoAggregation(project(":gradle-plugin"))
}

reporting {
  reports {
    create<JacocoCoverageReport>("testCoverageReport") {
      testType.set(TestSuiteType.UNIT_TEST)
      reportTask {
        reports.xml.required.set(true)
      }
    }
  }
}

tasks {

  check {
    finalizedBy(named<JacocoReport>("testCoverageReport"))
  }

}
