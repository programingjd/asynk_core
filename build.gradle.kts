import com.jfrog.bintray.gradle.BintrayExtension
import org.cyberneko.html.parsers.DOMParser
import org.jetbrains.dokka.gradle.DokkaTask
import java.io.FileInputStream
import java.io.FileWriter
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.File
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

buildscript {
  repositories {
    jcenter()
    mavenCentral()
  }
}

plugins {
  kotlin("jvm") version "1.2.71"
  `maven-publish`
  id("org.jetbrains.dokka") version "0.9.17"
  id("com.jfrog.bintray") version "1.8.4"
}

group = "info.jdavid.asynk"
version = "0.0.0.16"

repositories {
  jcenter()
}

dependencies {
  compile(kotlin("stdlib-jdk8"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.27.0")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.0")
  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.3.0")
}

kotlin {
  experimental.coroutines = Coroutines.ENABLE
}

val dokkaJavadoc by tasks.creating(DokkaTask::class) {
  outputFormat = "javadoc"
  includeNonPublic = false
  skipEmptyPackages = true
  impliedPlatforms = mutableListOf("JVM")
  jdkVersion = 8
  outputDirectory = "${buildDir}/javadoc"
}

val sourcesJar by tasks.creating(Jar::class) {
  classifier = "sources"
  from(sourceSets["main"].allSource)
}

val javadocJar by tasks.creating(Jar::class) {
  classifier = "javadoc"
  from("${buildDir}/javadoc")
  dependsOn("javadoc")
}

tasks.withType(KotlinJvmCompile::class.java).all {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
  }
}

val jar: Jar by tasks
jar.apply {
  manifest {
    attributes["Sealed"] = true
  }
}

publishing {
  repositories {
    maven {
      url = uri("${buildDir}/repo")
    }
  }
  publications {
    register("mavenJava", MavenPublication::class.java) {
      from(components["java"])
      artifact(sourcesJar)
      artifact(javadocJar)
    }
  }
}

bintray {
  user = "programingjd"
  key = {
    "bintrayApiKey".let { key: String ->
      File("local.properties").readLines().findLast {
        it.startsWith("${key}=")
      }?.substring(key.length + 1)
    }
  }()
  //dryRun = true
  publish = true
  setPublications("mavenJava")
  pkg(delegateClosureOf<BintrayExtension.PackageConfig>{
    repo = "maven"
    name = "${project.group}.${project.name}"
    websiteUrl = "https://github.com/programingjd/asynk_core"
    issueTrackerUrl = "https://github.com/programingjd/asynk_core/issues"
    vcsUrl = "https://github.com/programingjd/asynk_core.git"
    githubRepo = "programingjd/asynk_core"
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

tasks {
  "test" {
    val test = this as Test
    doLast {
      DOMParser().let {
        it.parse(InputSource(FileInputStream(test.reports.html.entryPoint)))
        XPathFactory.newInstance().newXPath().apply {
          val total =
            (
              evaluate("DIV", it.document.getElementById("tests"), XPathConstants.NODE) as Node
            ).textContent.toInt()
          val failed =
            (
              evaluate("DIV", it.document.getElementById("failures"), XPathConstants.NODE) as Node
            ).textContent.toInt()
          val badge = { label: String, text: String, color: String ->
            "https://img.shields.io/badge/_${label}_-${text}-${color}.png?style=flat"
          }
          val color = if (failed == 0) "green" else if (failed < 3) "yellow" else "red"
          File("README.md").apply {
            readLines().mapIndexed { i, line ->
              when (i) {
                0 -> "![jcenter](${badge("jcenter", "${project.version}", "6688ff")}) &#x2003; " +
                     "![jcenter](${badge("Tests", "${total-failed}/${total}", color)})"
                else -> line
              }
            }.joinToString("\n").let {
              FileWriter(this).apply {
                write(it)
                close()
              }
            }
          }
        }
      }
    }
  }
  "bintrayUpload" {
    dependsOn("check")
  }
  "javadoc" {
    dependsOn("dokkaJavadoc")
  }
}