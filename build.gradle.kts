plugins { id("io.vacco.oss.gitflow") version "1.8.3" }

group = "io.rqlite"
version = "8.42.0.1"

configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  addJ8Spec()
  sharedLibrary(true, false)
}

dependencies {
  testImplementation("io.vacco.metolithe:mt-codegen:3.0.0")
  testImplementation("io.vacco.shax:shax:2.0.16.0.4.3")
  testImplementation("com.zaxxer:HikariCP:6.3.0")
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