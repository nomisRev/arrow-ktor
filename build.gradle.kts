import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.knit.KnitPluginExtension
import org.gradle.api.Project
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

@Suppress("DSL_SCOPE_VIOLATION") plugins {
  base
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.assert) apply false
  alias(libs.plugins.detekt)
//  alias(libs.plugins.dokka)
  alias(libs.plugins.kover)
  id("org.jetbrains.kotlinx.knit") version "0.4.0"
}

repositories {
  mavenCentral()
}

allprojects {
  group = property("projects.group").toString()
  version = property("projects.version").toString()
  setupDetekt()

  tasks {
    withType<KotlinCompile>().configureEach {
//      compilerOptions.jvmTarget.set(JvmTa)
    }

    withType<Test>().configureEach {
      maxParallelForks = Runtime.getRuntime().availableProcessors()
      useJUnitPlatform()
      testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(SKIPPED, FAILED, STANDARD_OUT, STANDARD_ERROR)
      }
    }
  }
}

//tasks.test {
//  useJUnitPlatform()
//  extensions.configure(KoverTaskExtension::class) {
//    includes.add("io.github.nomisrev.*")
//  }
//}

configure<KnitPluginExtension> {
  siteRoot = "https://nomisrev.github.io/arrow-ktor/"
}

tasks {
//  withType<DokkaTaskPartial>().configureEach {
//    outputDirectory.set(rootDir.resolve("docs"))
//    moduleName.set("Arrow Ktor")
//    dokkaSourceSets {
//      named("commonMain") {
//        includes.from("README.md")
//        perPackageOption {
//          matchingRegex.set(".*\\.internal.*")
//          suppress.set(true)
//        }
//        externalDocumentationLink("https://api.ktor.io")
//        sourceLink {
//          localDirectory.set(file("src/commonMain/kotlin"))
//          remoteUrl.set(uri("https://github.com/nomisRev/arrow-resilience-ktor/tree/main/src/commonMain/kotlin").toURL())
//          remoteLineSuffix.set("#L")
//        }
//      }
//    }
//  }

//  getByName("knitPrepare").dependsOn(getTasksByName("dokka", true))
}

fun Project.setupDetekt() {
  plugins.apply("io.gitlab.arturbosch.detekt")

  configure<DetektExtension> {
    parallel = true
    buildUponDefaultConfig = true
    allRules = true
  }

  tasks {
    withType<Detekt>().configureEach {
      reports {
        html.required by true
        sarif.required by true
        txt.required by false
        xml.required by false
      }

      exclude("**/example/**")
      exclude("**/ReadMeSpec.kt")
    }

    configureEach {
      if (name == "build") dependsOn(withType<Detekt>())
    }
  }
}

infix fun <T> Property<T>.by(value: T) {
  set(value)
}
