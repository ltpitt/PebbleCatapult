plugins {
   androidLibraryModule
   compose
   di
   navigation
   serialization
   showkase
}

android {
   namespace = "com.matejdro.catapult.tools.ui"

   androidResources.enable = true
}

dependencies {
   api(projects.common)
   api(projects.logging.api)
   api(projects.bluetooth.api)
   api(libs.kotlin.coroutines)
   api(libs.kotlinova.core)
   api(libs.kotlinova.navigation)

   implementation(projects.commonCompose)
   implementation(libs.androidx.core)
   implementation(libs.androidx.compose.material.icons)
   implementation(libs.dispatch)

   testImplementation(testFixtures(projects.bluetooth.api))
   testImplementation(libs.kotlinova.core.test)
}
