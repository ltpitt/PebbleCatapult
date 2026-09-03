plugins {
   pureKotlinModule
   testFixtures
}

dependencies {
   api(libs.pebblekit.api)
   testFixturesApi(projects.bluetooth.api)
   testFixturesApi(projects.bucketsync.api)
   testFixturesApi(libs.pebblekit.api)
   testFixturesImplementation(libs.kotlin.coroutines)
}
