# MICA and TALON — character example

This maintained Mod API 0.7 candidate example currently registers **MICA** in Sonic
1, Sonic 2 and Sonic 3 & Knuckles. TALON and the approved concept art in
[concept](concept/) are reserved for later work.

The playable art is deliberately temporary: an original 8×8 jade diamond with
sand feet. It is not the approved character sprite set. Super form and character
abilities are off in this skeleton.

## Build and run

Use Java 25, Python 3 and this checkout's Maven dependencies:

```sh
python3 examples/mica-and-talon/build.py
python3 examples/mica-and-talon/build.py --run
```

The script compiles the current engine, compiles the mod against that same build,
converts the authored PNG/YAML into playable art, and packages
`target/mica-and-talon/mica-and-talon.jar`. All generated output stays under the
checkout's `target/`. No ROM data is packaged. Supply your own supported ROMs.

With `--run`, the engine loads the development classes. Choose a game and MICA in
its launch character row. Settings default to
`target/mica-and-talon/player-settings`; use `--save-root PATH` to choose another
owned directory. To install the jar normally, use the engine's Mods manager and
enable trusted code for this example.

## Extension points

`mica.MicaMod` reads `art/mica.ggfp` through bounded mod assets and calls
`registerCharacter("mica", ...)`. The manifest uses `baseGame: any`.
The canonical save identity is `mica-and-talon:mica`.
`MicaCharacter.definition` declares the `SONIC_AND_TAILS` route, resolved to
`SONIC_ALONE` when launched without a follower. A stock follower may accompany
MICA where the host supports it. The per-instance physics are deliberately simple;
replace the placeholder sheet and extend this class for the later ability lane.

The dry palette comes from the playable sheet. With no underwater supplier, the
renderer derives water colours from the current host zone's normal/water palette
pair. An optional `CharacterDefinition.underwaterPaletteSupplier` can instead
supply a complete authored 16-colour line. Both suppliers run through the owner
fault boundary. Presentation surfaces still use stock fallback art.

## Acceptance

`TestMicaCharacterIntegration` compiles and packages these exact sources, then
checks the roster, first-level load, identity, save retention, rewind and disabled
owner fallback for each host, plus real LZ, CPZ2 and HCZ water palette inputs.
`TestLevelPlayableArtInitializerModArt` checks explicit water palette installation
and isolation between equal dry palettes.
