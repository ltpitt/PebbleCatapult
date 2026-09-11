import dev.detekt.gradle.Detekt
import java.io.File

plugins {
   `kotlin-dsl`
   alias(libs.plugins.detekt)

   // We must specify JVM plugin explicitly here to avoid version conflicts
   // It produces "Unsupported Kotlin plugin version" but it lets us compile
   // See https://slack-chats.kotlinlang.org/t/29177439/when-updating-kotlin-to-2-2-0-i-m-getting-https-github-com-t
   alias(libs.plugins.kotlin.jvm)
}

repositories {
   mavenLocal()
   google()
   mavenCentral()
   gradlePluginPortal()
}

detekt {
   config.setFrom("$projectDir/../config/detekt.yml", "$projectDir/../config/detekt-buildSrc.yml")
}

tasks.withType<Detekt>().configureEach {
   reports {
      sarif.required.set(true)
   }
}

dependencies {
   implementation(libs.androidGradleCacheFix)
   implementation(libs.android.agp)
   implementation(libs.detekt.plugin)
   implementation(libs.dependencyAnalysis)
   implementation(libs.kotlin.plugin)
   implementation(libs.kotlin.plugin.compose)
   implementation(libs.kotlin.plugin.serialization)
   implementation(libs.kotlinova.gradle)
   implementation(libs.metro.plugin)
   implementation(libs.moduleGraphAssert)
   implementation(libs.orgJson)
   implementation(libs.ksp)
   implementation(libs.sqldelight.gradle)
   implementation(libs.unmock.plugin)

   // Workaround to have libs accessible (from https://github.com/gradle/gradle/issues/15383)
   compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

   detektPlugins(libs.detekt.ktlint)
   detektPlugins(libs.detekt.compose)
}

fun gitHooksDir(): File {
   val repositoryRoot = rootDir.resolve("../..").canonicalFile
   val process = ProcessBuilder("git", "-C", repositoryRoot.absolutePath, "rev-parse", "--git-path", "hooks")
      .redirectErrorStream(true)
      .start()
   val output = process.inputStream.bufferedReader().readText().trim()

   check(process.waitFor() == 0) { "Unable to resolve git hooks directory: $output" }

   val hooksDir = File(output)
   return if (hooksDir.isAbsolute) hooksDir else repositoryRoot.resolve(output)
}

tasks.register("commit-hooks", Copy::class) {
   from("$rootDir/../config/hooks/")
   into(gitHooksDir())
}

afterEvaluate {
   tasks.getByName("jar").dependsOn("commit-hooks")
}
