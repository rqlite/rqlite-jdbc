plugins {
  id("io.vacco.oss.gitflow") version "1.8.3"
}

group = "io.rqlite"
version = "9.1.2.0"

configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  addJ8Spec()
  sharedLibrary(true, false)
}

dependencies {
  testImplementation("io.vacco.metolithe:mt-codegen:3.7.1")
  testImplementation("io.vacco.shax:shax:2.0.16.0.4.3")
  testImplementation("com.zaxxer:HikariCP:6.3.0")
  testImplementation("org.jetbrains.exposed:exposed-dao:0.61.0")
  testImplementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
}

tasks.processResources {
  filesMatching("io/rqlite/jdbc/version") {
    expand("projectVersion" to version)
  }
}

tasks.withType<JacocoReport> {
  afterEvaluate {
    classDirectories.setFrom(
      files(classDirectories.files.map {
        fileTree(it) {
          exclude("io/rqlite/json/**")
        }
      })
    )
  }
}