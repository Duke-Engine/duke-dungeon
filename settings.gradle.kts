rootProject.name = "duke-dungeon"

// The engine comes from Maven Central: `uz.duke-engine:bom:…` in build.gradle.kts names the version and the
// modules take it. This repository builds on its own — a clone and `./gradlew build` is enough, with no second
// checkout and nothing published in between.
//
// To work on the engine and the game together, put the engine checkout beside this one and build with
// `-PdukeEngineLocal`. Every `uz.duke-engine:…` dependency is then answered by that checkout instead of
// by the repository, so a change to the engine shows up here without publishing anything.
//
// Another checkout can stand in for the one beside this with `-PdukeEngineDir=<path>`: a snapshot of the
// engine's last commit, say, while its working tree is half-way through a change and does not compile.
if (providers.gradleProperty("dukeEngineLocal").isPresent) {
    includeBuild(providers.gradleProperty("dukeEngineDir").getOrElse("../duke-engine"))
}
