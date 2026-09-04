plugins {
   pureKotlinModule
   testFixtures
   id("kotlinx-serialization")
}

dependencies {
   implementation(libs.kotlinova.core)
   implementation(libs.kotlin.serialization.json)
}
