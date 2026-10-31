plugins {
    java
    application
}

group = "uz.duke-engine"
version = "0.2.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // jME decodes every picture a test loads into direct memory, which by default is capped at the heap's
    // 512 MB, and the test classes keep their loaders (and so their pictures) for the whole run. Two heroes
    // with pictures of their own -- Lange and the Huntress -- took the suite past it halfway through; the bestiary's
    // licensed monsters (golem and spider skins, troll, minotaur) took it past 1 GB.
    jvmArgs("-XX:MaxDirectMemorySize=2g")
    // The living world (a 1024-cell walking grid, its nature kits) took the default 512 MB heap past its end in the
    // last tests of a whole run, which the test process dies of without a word.
    maxHeapSize = "2g"
}

// The credits the front menu rolls are CREDITS.md's own rows, shipped beside the game's data.
tasks.processResources {
    from("CREDITS.md")
}

// Licensed models (CGTrader Royalty Free: the game may ship them, this public repository may not) live in the
// git-ignored licensed-assets/, laid out as resources are (licensed-assets/models/licensed/queen.glb). Where the
// folder is, it is one more resource folder -- in the jar, so in installDist and packageInstaller's MSI, and on
// the tests' classpath; where it is not (CI, a fresh clone), every block naming one draws its stand-in
// (a Monster's Licensed look -- see MonsterLook.licensedOr).
if (file("licensed-assets").isDirectory) {
    sourceSets["main"].resources.srcDir("licensed-assets")
}

dependencies {
    // The engine, from Maven Central. The BOM names the version once and the modules below take it, so
    // there is one number to change and no way to mix two of them. See settings.gradle.kts for building
    // against a checkout instead.
    implementation(platform("uz.duke-engine:bom:0.9.0"))

    // The 3D client, for an angled camera over a world of solid shapes.
    implementation("uz.duke-engine:client3d")
    // The starter set: the effects every game may use, and the art they are drawn with.
    implementation("uz.duke-engine:kit")
    // The RPG's mechanisms: skills, items, a hero's levels, the bow and the swing, the keep's mission.
    implementation("uz.duke-engine:rpg")
    // The RPG's part of the window: the hero panel, the bag and the buffs, the card, one hero's orders.
    // The client finds it on the classpath.
    implementation("uz.duke-engine:rpg-client-3d")
    // The RTS's flavour, which the dungeon still runs on, and its players and orders.
    implementation("uz.duke-engine:rts")
    // The desktop platform the 3D client now needs at runtime (tests too).
    runtimeOnly("uz.duke-engine:client3d-desktop")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

// maps/maps.list, one line a file under maps/ (people/people.map): a phone packs resources into its APK and
// cannot scan a folder, so the engine finds the shipped maps by this list (MapPackages.LIST). The engine's own rule.
val mapList = tasks.register("generateMapList") {
    val maps = file("src/main/resources/maps")
    val out = layout.buildDirectory.dir("generated/maplist")
    inputs.dir(maps)
    outputs.dir(out)
    doLast {
        val lines = maps.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(maps).invariantSeparatorsPath }.sorted().toList()
        val target = out.get().file("maps/maps.list").asFile
        target.parentFile.mkdirs()
        target.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
sourceSets["main"].resources.srcDir(files(layout.buildDirectory.dir("generated/maplist")).builtBy(mapList))

// shortcut: processor not on Maven Central yet; make unconditional once the engine release carries it
if (providers.gradleProperty("dukeEngineLocal").isPresent) {
    dependencies {
        annotationProcessor(platform("uz.duke-engine:bom:0.9.0"))
        annotationProcessor("uz.duke-engine:processor")
    }
}
}

application {
    mainClass.set("uz.dukeengine.dungeon.Main")
}

// ---------------------------------------------------------------------------
// Turns the .fbx animations in art/anim into .glb libraries the game can
// read:
//
//   ./gradlew convertAnimations
//   ./gradlew convertAnimations -PblenderPath="D:/apps/blender.exe"
//
// jME reads glTF and nothing else, and nothing else in this repository reads
// FBX — so the conversion is Blender's, run headless. Kept as a task for the
// reason the icon cut is: a step nobody can repeat is a step nobody dares
// change. See art/anim/fbx_to_glb.py for what it does on the way.
//
// Blender is not a build dependency. This task is run by hand when an animation
// arrives, and the build does not depend on it: what ships is the .glb it wrote,
// which is committed.
// ---------------------------------------------------------------------------
fun blender(task: String): String {
    // Where it actually is, checked rather than hoped for: a name on the PATH
    // that is not there fails inside the process launcher, which reports
    // "a problem occurred starting process" and not one word about Blender.
    val onThePath = System.getenv("PATH").orEmpty()
        .split(File.pathSeparator)
        .flatMap { listOf(File(it, "blender"), File(it, "blender.exe")) }
        .firstOrNull { it.canExecute() }?.absolutePath
    return (findProperty("blenderPath") as String?)
        ?: onThePath
        ?: listOf(
            "C:/Program Files/Blender Foundation/Blender 5.2/blender.exe",
            "C:/Program Files/Blender Foundation/Blender 4.2/blender.exe",
            "/Applications/Blender.app/Contents/MacOS/Blender",
            "/usr/bin/blender",
        ).firstOrNull { File(it).exists() }
        ?: throw GradleException(
            "Blender was not found on the PATH or where it is usually installed. " +
                "Install it, or point at it: " +
                "./gradlew $task -PblenderPath=\"...\""
        )
}

private val animations = listOf(
    // source (under art/anim), output (under resources), clip name
    Triple("magic_area_attack.fbx", "magic.glb", "Magic_Area_Attack"),
)

tasks.register("convertAnimations") {
    group = "application"
    description = "Convert the .fbx animations into .glb libraries, using Blender"
    doLast {
        val blender = blender("convertAnimations")
        animations.forEach { (from, into, clip) ->
            providers.exec {
                workingDir = rootProject.projectDir
                commandLine(
                    blender, "-b", "--factory-startup",
                    "-P", "art/anim/fbx_to_glb.py", "--",
                    "art/anim/$from",
                    "src/main/resources/animations/characters/$into",
                    clip,
                )
            }.standardOutput.asText.get()
                .lines().filter { it.startsWith("CONVERTED") }
                .forEach { logger.lifecycle(it) }
        }
    }
}

// Six castle kits from Sketchfab (all CC BY 4.0, see CREDITS.md) cut into the pieces a map builds castles of:
// resources/models/buildings/<kit>/, a .gltf a piece over a folder of pictures the kit's pieces share, and the fort's
// dark copies for the queen's castle. See art/castles/export_castles.py, which says the grid and the pivots.
//
//   ./gradlew exportCastles -PcastlesSource="C:/Users/me/Downloads/castles"
//
// The source holds each download unzipped into a folder named as its zip (medieval-fortress-kit/, ...). Not in
// the repository; what ships is what this wrote, which is committed. Blender is found as convertAnimations finds it.
tasks.register("exportCastles") {
    group = "application"
    description = "Cut the six castle kits into the game's scenery pieces, using Blender"
    doLast {
        val source = File((findProperty("castlesSource") as String?)
            ?: throw GradleException("Say where the downloads are: -PcastlesSource=\".../castles\""))
        providers.exec {
            workingDir = rootProject.projectDir
            commandLine(
                blender("exportCastles"), "-b", "--factory-startup",
                "-P", "art/castles/export_castles.py", "--",
                source.absolutePath, "src/main/resources/models/buildings",
            )
        }.standardOutput.asText.get()
            .lines().filter { it.startsWith("PIECE") || it.startsWith("DARK") }
            .forEach { logger.lifecycle(it) }
    }
}

// Inuciian's "Baba Yaga's hut" (CC BY 4.0, see CREDITS.md) cut into the models, written under
// build/exported/inuciian as the pack lays them out: the hut, its people, its props and its forest, each one
// glTF with its pictures inside, and the dark wood's recoloured copies. See art/inuciian/export_models.py.
//
//   ./gradlew exportInuciian -PinuciianSource="C:/Users/me/Downloads/baba-yagas-hut"
//
// The source (the Sketchfab download: source/*.blend and textures/) is not in the repository;
// what ships is what this wrote, sorted by kind under resources/models/ and committed: the manifest
// resources/models/assets.txt names each piece's place and, as its old path, where this export puts it. Blender is found as convertAnimations finds it.
tasks.register("exportInuciian") {
    group = "application"
    description = "Cut Inuciian's Baba Yaga scene into the game's models, using Blender"
    doLast {
        val source = File((findProperty("inuciianSource") as String?)
            ?: throw GradleException("Say where the download is: -PinuciianSource=\".../baba-yagas-hut\""))
        val blend = File(source, "source").listFiles { f -> f.name.endsWith(".blend") }?.firstOrNull()
            ?: throw GradleException("no .blend under $source/source")
        providers.exec {
            workingDir = rootProject.projectDir
            commandLine(
                blender("exportInuciian"), "-b", "--factory-startup",
                "-P", "art/inuciian/export_models.py", "--",
                blend.absolutePath, File(source, "textures").absolutePath,
                "build/exported/inuciian",
            )
        }.standardOutput.asText.get()
            .lines().filter { it.startsWith("EXPORTED") }
            .forEach { logger.lifecycle(it) }
    }
}

// Two weapon packs (CC BY 4.0, see CREDITS.md) made into the game's hand-held models: nodgerty's elven
// bow and dagger (written as build/exported/weapons/elven/{bow,dagger}.glb, shipped as
// resources/models/items/weapons/elven_{bow,dagger}.glb), panarth's low-poly medieval weapons one file each
// (build/exported/weapons/medieval, shipped by the same names in resources/models/items/weapons).
// See art/weapons/export_weapons.py.
//
//   ./gradlew exportWeapons -PelvenSource=".../free-elven-assassin-weapon-set-bow-dagger" \
//       -PelvenFbx=".../resault/KatanaBow.fbx" -PmedievalFbx=".../melee.FBX" \
//       -PmedievalSource=".../low-poly-medieval-weapons-free-pack"
//
// The FBX files come inside the downloads' source/ archives, unpacked by hand; the pictures are the
// downloads' textures/. Not in the repository; what ships is what this wrote, which is committed.
tasks.register("exportWeapons") {
    group = "application"
    description = "Make the elven and the medieval weapon packs into the game's held models, using Blender"
    doLast {
        fun said(name: String, example: String) = (findProperty(name) as String?)
            ?: throw GradleException("Say where it is: -P$name=\"$example\"")
        val elven = File(said("elvenSource", ".../free-elven-assassin-weapon-set-bow-dagger"))
        val medieval = File(said("medievalSource", ".../low-poly-medieval-weapons-free-pack"))
        providers.exec {
            workingDir = rootProject.projectDir
            commandLine(
                blender("exportWeapons"), "-b", "--factory-startup",
                "-P", "art/weapons/export_weapons.py", "--",
                said("elvenFbx", ".../KatanaBow.fbx"), File(elven, "textures").absolutePath,
                said("medievalFbx", ".../melee.FBX"), File(medieval, "textures/textureFlat.png").absolutePath,
                "build/exported/weapons",
            )
        }.standardOutput.asText.get()
            .lines().filter { it.startsWith("WEAPON") }
            .forEach { logger.lifecycle(it) }
    }
}

// neilhancart's "Huntress" (CC BY 4.0, see CREDITS.md) made into the Rogue hero: her model in
// resources/models/characters/heroes/huntress.glb and the Rogue's clips retargeted onto her skeleton in
// resources/animations/characters/huntress.glb. See art/huntress/export_huntress.py.
//
//   ./gradlew exportHuntress -PhuntressSource="C:/Users/me/Downloads/huntress" \
//       -PualSource=".../Universal Animation Library[Standard]/Unreal-Godot/UAL1_Standard.glb"
//
// The sources (Sketchfab's glTF download: scene.gltf, scene.bin, textures/; Quaternius' library, CC0)
// are not in the repository;
// what ships is what this wrote, which is committed. Blender is found as convertAnimations finds it.
tasks.register("exportHuntress") {
    group = "application"
    description = "Make neilhancart's Huntress into the Rogue hero's model and clips, using Blender"
    doLast {
        val source = File((findProperty("huntressSource") as String?)
            ?: throw GradleException("Say where the download is: -PhuntressSource=\".../huntress\""))
        val gltf = File(source, "scene.gltf").takeIf { it.isFile }
            ?: throw GradleException("no scene.gltf under $source")
        val ual = File((findProperty("ualSource") as String?)
            ?: throw GradleException("Say where Quaternius' library is: -PualSource=\".../UAL1_Standard.glb\""))
        providers.exec {
            workingDir = rootProject.projectDir
            commandLine(
                blender("exportHuntress"), "-b", "--factory-startup",
                "-P", "art/huntress/export_huntress.py", "--",
                gltf.absolutePath, "src/main/resources/animations/characters", ual.absolutePath,
                "src/main/resources/models/characters/heroes/huntress.glb",
            )
        }.standardOutput.asText.get()
            .lines().filter { it.startsWith("HUNTRESS") }
            .forEach { logger.lifecycle(it) }
    }
}

// phucpham1076's "Lange - Half-Elf Knight" (CC BY 4.0, see CREDITS.md) made into the knight:
// resources/models/characters/heroes/lange.glb, his pictures and clips inside. See art/lange/export_lange.py.
//
//   ./gradlew exportLange -PlangeSource="C:/Users/me/Downloads/lange-half-elf-knight" \
//       -PualSource=".../Universal Animation Library[Standard]/Unreal-Godot/UAL1_Standard.glb" \
//       -PkaykitAnimations=".../KayKit_Character_Animations_1.1/Animations/gltf/Rig_Medium"
//
// The sources (the Sketchfab download: source/KnightElf.zip and textures/; Quaternius' Universal
// Animation Library and KayKit's Character Animations, both CC0, which his borrowed clips come out
// of -- art/lange/retarget.py) are not in the repository; what ships is what this wrote, which is
// committed. The downsized pictures are left beside the unpacked FBX under build/lange/out.
// Blender is found as convertAnimations finds it.
tasks.register("exportLange") {
    group = "application"
    description = "Make Lange, the knight, into the game's model, using Blender"
    doLast {
        fun said(name: String, example: String) = (findProperty(name) as String?)
            ?: throw GradleException("Say where it is: -P$name=\"$example\"")
        val source = File(said("langeSource", ".../lange-half-elf-knight"))
        val ual = File(said("ualSource", ".../UAL1_Standard.glb"))
        val kaykit = File(said("kaykitAnimations", ".../Animations/gltf/Rig_Medium"))
        val unpacked = layout.buildDirectory.dir("lange").get().asFile
        copy {
            from(zipTree(File(source, "source/KnightElf.zip")))
            into(unpacked)
        }
        val out = File(unpacked, "out")
        providers.exec {
            workingDir = rootProject.projectDir
            commandLine(
                blender("exportLange"), "-b", "--factory-startup",
                "-P", "art/lange/export_lange.py", "--",
                File(unpacked, "KnightElfposing.fbx").absolutePath,
                File(unpacked, "Animations/KnightElfIdle.fbx").absolutePath,
                File(source, "textures").absolutePath,
                ual.absolutePath,
                kaykit.absolutePath,
                out.absolutePath,
            )
        }.standardOutput.asText.get()
            .lines().filter { it.startsWith("EXPORTED") || it.startsWith("SIZE") || it.startsWith("CLIP")
                || it.startsWith("EYES") }
            .forEach { logger.lifecycle(it) }
        copy {
            from(File(out, "lange.glb"))
            into("src/main/resources/models/characters/heroes")
        }
        // The game draws base colour only: the other maps, and their bytes, are dropped (see the script).
        providers.exec {
            workingDir = rootProject.projectDir
            commandLine("node", "art/models/base_colour_only.js", "src/main/resources/models/characters/heroes/lange.glb")
        }.standardOutput.asText.get().lines().filter { it.isNotBlank() }.forEach { logger.lifecycle(it) }
    }
}

// A map drawn once, from a seed, then filled by hand on the Map tab of the IDE:
//
//   ./gradlew newMap --args="crypt 42"             # a floor's size, at depth 1
//   ./gradlew newMap --args="crypt 42 3 60 40 12"  # depth 3, 60 by 40 cells, 12 rooms
//
// From the repository root, so the folder lands in this module's maps/ rather
// than under the module's own directory, where nothing would ever read it.
tasks.register<JavaExec>("newMap") {
    group = "application"
    description = "Draw a new map from a seed, into a folder of its own under maps/"
    mainClass.set("uz.dukeengine.dungeon.stage.MapWriter")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

// The two maps the game ships, written again from their seeds.
tasks.register<JavaExec>("writeExampleMaps") {
    group = "application"
    description = "Write the maps the game ships again, from their seeds"
    mainClass.set("uz.dukeengine.dungeon.stage.MapWriter")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

// The picture beside each map, drawn from the map as it stands — the map itself is not touched.
tasks.register<JavaExec>("writeMapPreviews") {
    group = "application"
    description = "Draw preview.png for every map the game keeps"
    mainClass.set("uz.dukeengine.dungeon.stage.MapPicture")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

// The pictures a painted map's ground is drawn with, painted from a seed into resources/ground.
tasks.register<JavaExec>("writeGroundPictures") {
    group = "application"
    description = "Paint the ground pictures a painted map is drawn with"
    mainClass.set("uz.dukeengine.dungeon.tools.GroundPictures")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

// The painted test map, written again from its seed: maps/painted and nothing else.
tasks.register<JavaExec>("writePaintedMap") {
    group = "application"
    description = "Write the painted test map again from its seed"
    mainClass.set("uz.dukeengine.dungeon.stage.PaintedMapWriter")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

// The campaign's open world, written again from a seed: maps/world and nothing else (never maps/first).
//   ./gradlew writeWorldMap                  # its own seed
//   ./gradlew writeWorldMap --args="42"      # another
tasks.register<JavaExec>("writeWorldMap") {
    group = "application"
    description = "Write the open world again from a seed"
    mainClass.set("uz.dukeengine.dungeon.stage.WorldMapWriter")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
    maxHeapSize = "2g"
}

// The languages' tables (data/lang/*.duke) against the data: per language, how many keys it says, and every key it
// lacks, has stale (its "; uz:" no longer the data's Uzbek) or has that the data no longer has. Prints, fails nothing.
tasks.register<JavaExec>("langReport") {
    group = "verification"
    description = "Print what each language's table lacks, has stale and has unused"
    mainClass.set("uz.dukeengine.dungeon.tools.LangTable")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
    args("--report")
}

// A language's table written again from the data, its words kept by key; a new code starts a new language.
//   ./gradlew langTable --args="en"
//   ./gradlew langTable --args="de --language=Deutsch"
tasks.register<JavaExec>("langTable") {
    group = "application"
    description = "Write data/lang/<code>.duke again: every key in the data's order, the words kept by key"
    mainClass.set("uz.dukeengine.dungeon.tools.LangTable")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

// ---------------------------------------------------------------------------
// The installer, for the machine you are sitting at.
//
//   ./gradlew packageInstaller
//   ./gradlew packageInstaller -PinstallerVersion=1.2.0
//
// The same thing the release workflow builds, by the same route and with the
// same flags, so what comes out of a laptop and what comes out of CI are the
// same installer. Which is the whole reason it lives here rather than in a note
// somewhere: the two drift the moment they are written down twice.
//
// jpackage builds only for the operating system it is standing on, so this makes
// an .msi on Windows, a .dmg on macOS and a .deb on Linux — never the other two.
// Each needs the platform's own tools: WiX 3 for the MSI, fakeroot for the .deb.
// Missing them, jpackage says so plainly and this stops.
// ---------------------------------------------------------------------------

private val osName = System.getProperty("os.name").lowercase()
private val onWindows = osName.contains("win")
private val onMac = osName.contains("mac")

tasks.register<Exec>("packageInstaller") {
    group = "distribution"
    description = "Native installer for this OS, carrying its own Java runtime"
    dependsOn("installDist")

    // The toolchain's jpackage rather than whatever is on the PATH. A jpackage
    // from an older JDK bundles an older runtime, which then refuses the classes
    // it was handed — and on Windows the windowed exe dies with exit 1 and not a
    // word of explanation.
    val jdkHome = javaToolchains.launcherFor(java.toolchain).get()
            .metadata.installationPath.asFile
    executable = File(jdkHome, "bin/jpackage" + if (onWindows) ".exe" else "").absolutePath

    val type = if (onWindows) "msi" else if (onMac) "dmg" else "deb"
    val lib = layout.buildDirectory.dir("install/duke-dungeon/lib").get().asFile
    val out = layout.buildDirectory.dir("installer").get().asFile
    // Not the project's own 0.1.0-SNAPSHOT: jpackage takes one to three integers
    // and nothing else, and macOS additionally refuses a leading zero — the
    // version goes into the bundle's own metadata, where it is counted fields
    // rather than a name. Override it for a real build; this is only a default
    // that both of them will accept.
    val appVersion = (findProperty("installerVersion") as String?) ?: "1.0.0"

    doFirst { delete(out) }

    argumentProviders.add(CommandLineArgumentProvider {
        buildList {
            addAll(listOf(
                "--type", type,
                "--name", "Last Hero", // a stand-in title; the owner names the game
                "--app-version", appVersion,
                // The release workflow's vendor, its repository's owner: the MSI's
                // upgrade code is made from vendor and name, and any other vendor
                // installs beside a released game instead of over it.
                "--vendor", "Duke-Engine",
                "--description", "A dungeon crawler on the duke-engine",
                "--input", lib.absolutePath,
                "--main-jar", tasks.jar.get().archiveFileName.get(),
                "--main-class", "uz.dukeengine.dungeon.Main",
                // Spelled out rather than left to the default: LWJGL reaches for
                // sun.misc.Unsafe, which lives in jdk.unsupported, and a runtime
                // built without it fails at the first frame rather than here.
                // jdk.zipfs is how the engine lists the maps inside the game's
                // own jar; without it the exe says only "Failed to launch JVM".
                "--add-modules", "java.se,jdk.unsupported,jdk.zipfs",
                "--java-options", "-Xmx2g",
                "--dest", out.absolutePath,
            ))
            when (type) {
                // Per-user, so nobody has to be an administrator to play a game.
                "msi" -> addAll(listOf("--win-dir-chooser", "--win-menu",
                        "--win-shortcut", "--win-per-user-install",
                        // What "Duke-Engine" + "Duke Dungeon" made (read from the 1.0.37 MSI): kept, so 1.0.x installs upgrade.
                        "--win-upgrade-uuid", "05e2eb51-00a8-3343-9491-610dc3899eb4"))
                // GLFW wants the process's first thread on macOS, and there is no
                // command line to add that to afterwards.
                "dmg" -> addAll(listOf("--java-options", "-XstartOnFirstThread"))
                // dpkg will not have a capital or a space in a package name.
                "deb" -> addAll(listOf("--linux-package-name", "duke-dungeon",
                        "--linux-shortcut", "--linux-menu-group", "Game"))
            }
        }
    })

    doLast { logger.lifecycle("installer written to $out") }
}
