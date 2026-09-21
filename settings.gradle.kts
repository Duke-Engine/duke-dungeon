rootProject.name = "duke-dungeon"

// The engine, built alongside this game. Every `uz.duke-engine:…` dependency in build.gradle.kts is
// answered by this checkout rather than by a repository — so the game and the engine can be changed
// together, in one step, without publishing anything in between.
//
// Delete this line once the engine version you want is on Maven Central; nothing else changes.
includeBuild("../duke-engine")
