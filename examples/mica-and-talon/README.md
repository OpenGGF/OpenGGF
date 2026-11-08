# MICA and TALON — character example

This maintained Mod API 0.7 candidate example currently registers **MICA** in Sonic
1, Sonic 2 and Sonic 3 & Knuckles. TALON and the approved concept art in
[concept](concept/) are reserved for later work.

The playable sheet contains 98 original indexed poses and 18 canonical animations,
including four slope cycles for WALK and RUN, the breathing idle loop, and
HOMING_DIVE. Homing is MICA’s air move; powered Gilded forms use Instagib. Python 3 with Pillow is required to rebuild the sheet.

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
MICA where the host supports it. The instance-owned `CharacterPhysicsSpec` uses `PhysicsProfile.SONIC_2_SONIC`,
the normal single-player Sonic profile in all three hosts.

The dry palette comes from the playable sheet. With no underwater supplier, the
renderer derives water colours from the current host zone's normal/water palette
pair. An optional `CharacterDefinition.underwaterPaletteSupplier` can instead
supply a complete authored 16-colour line. Both suppliers run through the owner
fault boundary. Presentation surfaces still use stock fallback art.

## Acceptance

`TestMicaCharacterIntegration` compiles and packages these exact sources, then
checks the packed 98-frame/18-animation sheet and full palette, the roster,
first-level load, identity, native Sonic physics equality, homing kills, powered
Instagib, dive pose selection, strike rewind replay, save retention and disabled
owner fallback for each host, plus real LZ, CPZ2 and HCZ water palette inputs.
`TestLevelPlayableArtInitializerModArt` checks explicit water palette installation
and isolation between equal dry palettes.

The build reruns `tools/modding/playable_art/assemble.py` from `art/mica/frames.yaml`.
`src/main/mod/mica-custom-frames.json` lists the four HOMING_DIVE mapping indices
and the assembler binds them as a fixed two-tick canonical strike cycle. See the [assembler notes](../../tools/modding/playable_art/README.md).

Headless title-route captures use `--interactive-pacing`. The capture host advertises
its explicitly decorated character registry to native Data Select admission, then
re-registers the jar for the selected launch team; it does not install it globally.

Core sheet acceptance uses real title-first GHZ1/EHZ1/AIZ1 captures. Upright and
ball anchors retain the native standing/rolling baselines; rotated slope cycles
use selectors 0/2/4/6. MICA retains native Sonic physics and speed-gated RUN timing.

A spring launch need not select SPRING: subtype bit 0 deliberately selects WALK
and native twirl state. S2's `loc_189FE` (s2.asm:33937-33953) and S3K's
`loc_22FE0` (sonic3k.asm:47778-47801) own that behavior. The sheet preserves it;
S1's ordinary upward spring displays the authored SPRING poses.
