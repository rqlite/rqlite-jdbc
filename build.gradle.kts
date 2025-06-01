plugins { id("io.vacco.oss.gitflow") version "1.5.4" }

group = "io.vacco.rqlite"
version = "8.37.0.5"

configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  addJ8Spec()
  addClasspathHell()
  sharedLibrary(true, false)
}

dependencies {
  testImplementation("io.vacco.metolithe:mt-codegen:2.40.0")
  testImplementation("org.liquibase:liquibase-core:4.31.1")
  testImplementation("io.vacco.shax:shax:2.0.16.0.4.3")
  testImplementation("com.zaxxer:HikariCP:6.3.0")
}

tasks.processResources {
  filesMatching("io/rqlite/jdbc/version") {
    expand("projectVersion" to version)
  }
}
