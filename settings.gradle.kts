rootProject.name = "duke-dungeon"

// The engine comes from Maven Central: `uz.duke-engine:bom:0.2.0` in build.gradle.kts names the version
// and the modules take it. This repository builds on its own — a clone and `./gradlew build` is enough,
// with no second checkout and nothing published in between.
//
// To work on the engine and the game together, put the engine checkout beside this one and build with
// `-PdukeEngineLocal`. Every `uz.duke-engine:…` dependency is then answered by that checkout instead of
// by the repository, so a change to the engine shows up here without publishing anything.
if (providers.gradleProperty("dukeEngineLocal").isPresent) {
    includeBuild("../duke-engine")
}
