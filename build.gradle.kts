@file:Suppress("RemoveCurlyBracesFromTemplate")

import com.jfrog.bintray.gradle.BintrayExtension

plugins {
  kotlin("jvm") version KOTLIN.version
  id("org.jetbrains.dokka") version "0.9.17"
  `maven-publish`
  id("com.jfrog.bintray") version "1.8.4"
  id("io.gitlab.arturbosch.detekt").version("1.0.0.RC9.2")
}

group = "info.jdavid.asynk"
version = ASYNK.version

repositories {
  jcenter()
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib:${KOTLIN.version}")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${KOTLINX.version}")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.0")
  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.3.0")
}

tasks.compileKotlin {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}
tasks.compileTestKotlin {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

tasks.jar {
  dependsOn("detekt")
}

tasks.dokka {
  outputFormat = "javadoc"
  includeNonPublic = false
  skipEmptyPackages = true
  impliedPlatforms = mutableListOf("JVM")
  jdkVersion = 8
  outputDirectory = "${buildDir}/javadoc"
}

tasks.javadoc {
  dependsOn("dokka")
}

tasks.test {
  @Suppress("UnstableApiUsage") useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
  }
}

tasks.jar {
  manifest {
    attributes["Sealed"] = true
  }
}

val sourcesJar by tasks.registering(Jar::class) {
  classifier = "sources"
  from (sourceSets["main"].allSource)
}

val javadocJar by tasks.registering(Jar::class) {
  classifier = "javadoc"
  from(tasks.dokka.get().outputDirectory)
  dependsOn("javadoc")
}

publishing {
  repositories {
    maven {
      url = uri("${buildDir}/repo")
    }
    publications {
      register("mavenJava", MavenPublication::class) {
        @Suppress("UnstableApiUsage") from(components["java"])
        artifact(sourcesJar.get())
        artifact(javadocJar.get())
      }
    }
  }
}

bintray {
  user = BINTRAY.user
  key = BINTRAY.password(rootProject.projectDir)
  publish = true
  setPublications(*publishing.publications.names.toTypedArray())
  pkg(delegateClosureOf<BintrayExtension.PackageConfig>{
    repo = "maven"
    name = "${project.group}.${project.name}"
    websiteUrl = "https://github.com/${BINTRAY.user}/asynk_${project.name}"
    issueTrackerUrl = "https://github.com/${BINTRAY.user}/asynk_${project.name}/issues"
    vcsUrl = "https://github.com/${BINTRAY.user}/asynk_${project.name}.git"
    githubRepo = "${BINTRAY.user}/asynk_${project.name}"
    githubReleaseNotesFile = "README.md"
    setLicenses("Apache-2.0")
    setLabels("asynk", "java", "kotlin", "async", "coroutines", "suspend", "nio", "nio2")
    publicDownloadNumbers = true
    version(delegateClosureOf<BintrayExtension.VersionConfig> {
      name = "${project.version}"
      mavenCentralSync(delegateClosureOf<BintrayExtension.MavenCentralSyncConfig> {
        sync = false
      })
    })
  })
}

tasks.bintrayUpload {
  dependsOn("check")
}
