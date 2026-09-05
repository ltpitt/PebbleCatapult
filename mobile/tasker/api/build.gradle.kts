plugins {
   pureKotlinModule
   testFixtures
   id("kotlinx-serialization")
}

dependencies {
   api(libs.kotlin.serialization)
   implementation(libs.kotlinova.core)
   implementation(libs.kotlin.serialization.json)
}
