# Day and night, and weather (2026-10-15)

The open world's atmosphere. Data: `data/world/daynight.duke` (`world/DayNight`). Drawn by `Atmosphere`.

- **The clock** is the run's: frames of play, ticked by `DungeonRun.tick`, saved as `Clock` in the save, kept through a
  death. The hour is it read round `CycleFrames` (36000, 20 minutes) from `StartHour` (8). Deterministic: every machine
  reads the same hour.
- **The light** is `Light` keys (hour, colour, percent) eased round the clock: dawn warm, noon nothing, dusk orange,
  night a dark blue at half. Only a wide map (the open world) is drawn by it; the castles, halls and descent keep theirs.
  Until the engine's E82 the colour is laid by the game's canvas over the world above the panel; fires and the fountain
  get an added halo (`Glows`) as strong as the night is dark.
- **Night roamers** (`NightRoamers`, the marsh's Arvoh/Wisp): by day they keep to where they stand and sense nobody,
  unless struck (`MonsterBrain`). No punishment for the hero.
- **Weather** by the ground under the hero (the paint character most of nine cells round him stand on), drawn from the
  run's seed once every `WeatherFrames` (5400, 3 minutes): marsh fog, highland snow, rain, storms with thunder on a
  frame the seed says. Falls round the camera through `Duke3D.weather` (capped under 4096 flakes), its loop on the
  Ambience channel; crickets at night when nothing falls.
- **Clock**: a sun or a moon and the hour above the key hints, by the minimap.
- Testing: `--hour=H` (1-24) opens at that hour (with `--actN=` or `--map=`); `--weather=Rain|Storm|Snow|Fog` draws that
  weather whatever the sky says.

Skipped: villagers going indoors at night (people have no homes to walk to yet); a wet sheen (asked in E82).
