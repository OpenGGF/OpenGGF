# MICA and TALON — character example

This framework demonstration is not a released mod. The maintained Mod API 0.7 candidate example registers **MICA and TALON** in Sonic
1, Sonic 2 and Sonic 3 & Knuckles. MICA leads with TALON as her default follower;
TALON is also selectable solo. The [character reference](concept/README.md) has
both approved model sheets, the in-game palettes, the on-model rules and the
candidate selection.

The playable sheet contains 274 original indexed poses and 52 canonical animations,
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
checkout's `target/` (the authored sheet assembly refreshes `src/main/mod/`). No ROM data is packaged. Supply your own supported ROMs.

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
fault boundary. Presentation includes her HUD/name, results, 1-up, signs, special/bonus-stage
player sets, continue poses, data-select portrait and counters, ICZ snowboard
rider, and all three games' ending sets. Missing slots retain native fallback art;
vehicles, companions, choreography and timing remain host-owned.

The [powered pose banks](art/mica/powered/README.md) resolve the 70 S2/S3K
script obligations through new geometry, explicit normal aliases and justified
reserved-script N/A entries. The coverage probe checks production binding.

Gilded Super is available in all three hosts; S3K also supports Hyper. Her jade
slots cycle to gold; S2/S3K use her powered banks and her homing move remains her own.
Enable **Gilded form settings** in the host overlay to override the per-game form
switches, S1 emerald requirement, ring requirement and S1 music at the next full
load. S1's six-emerald/50-ring default is mod-defined and requires no donor ROM.
S2 and S3K retain their native default emerald gates. TALON uses his own 171-frame, 47-animation sheet and palette. His native flight
flaps normally; airborne down previews a target and down+jump dives onto it.
Solar uses white-gold plumage with flame wingtips, Instagib dives and twice the
native flap lift, with no Flickies or Hyper tier. Regenerate Solar Java tables
with `python3 tools/modding/playable_art/solar_java.py`; `--check` verifies sync.
TALON owns his HUD/life and 1-up portraits, signs, continue actors, save portrait
and Tails-role special-stage banks. S1 generated T/A/L glyphs and S2 native small
glyphs spell TALON. S3K displays TALO (28 pixels); full TALON requires 35 pixels
in its 32-pixel cell. Results use the full name.

The build copies presentation, wave3 stage, continue, portrait, snowboard and
ending PNGs into the jar alongside `art/mica.ggfp`. Presentation is currently a
code-supplied contract; GGFP presentation sections are pending. S2 2P and title art remain open; TALON cockpit assets await presentation-lane wiring. Optional 32×32 reel
portraits replace lead/sidekick symbols in S2 CNZ and S3K Slots, including
TALON solo and the Gilded/Solar symbol. Native rewards, other symbols and
scrolling remain host-owned. See each character’s `art/*/reels/README.md`.

Follow the [creator tutorial](../../docs/modding/guides/original-character.md)
for the concept-to-capture workflow and source map.

## Acceptance

`TestMicaCharacterIntegration` compiles and packages these exact sources, then
checks the packed 274-frame/52-animation sheet and full palette, the roster,
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

TALON is registered alongside MICA. Selecting MICA
chooses MICA & TALON by default; either character can be selected solo, and the
sidekick row stays editable. TALON uses the Tails route and native follow/respawn
strategy. S3K flight is native; S1/S2 flight explicitly borrows that model as a
mod capability. Solar doubles flap acceleration and has no Flickies.

TALON's `talon-sheet.yaml` and `talon.png` bake to `art/talon.ggfp`, independently
of MICA. His six-phase wingbeat and tired cycles serve normal/ascent flight;
S1/S2 use private mod animation IDs because those hosts have no native flight
vocabulary. S3K uses its native flight IDs. Airborne down requests the target
reticle; down+jump takes over flight with a targeted divebomb. Without down,
jump retains native flight/flaps. Solar palettes are generated from his own
presentation data. HUD glyph and missing presentation limits are listed above.

## S3K Competition miniatures

Select MICA as lead and TALON in the sidekick row before entering Competition.
The existing Sonic and Tails wheel slots then use their configured creator
character's miniature banks. With a native team, the slots retain native art.
This is a presentation substitution over native Competition mechanics: abilities and race rules remain native archetype-owned. Menu wheel/results
portraits and record icons use independent generated character art; native
Competition menus have no character-name strings to replace.
The first configured character for an archetype wins; this does not add wheel
positions or admit arbitrary creator movement into the miniature simulation.

Both banks use independent native 2P IDs and per-frame hotspots through
`StageSpriteSet.Surface.S3K_COMPETITION`. An absent bank or omitted ID retains
that archetype's native frame. `S3K_COMPETITION_TAIL` independently overrides the
native appendage bank; TALON explicitly supplies transparent frames for all 16
IDs because his wings are integrated. The body banks cover MICA's 44 and TALON's
46 selectors, including the blank $00 and shared $27/$26 mapping. Their palettes
use private presentation rows, preserving native arena colours and partial-bank
fallback. Art, selection and capture evidence are described in each
`art/<character>/competition/README.md`.

## Object-pose closure (2026-11-10)

MICA supplies all 63 remaining S3K object aliases: FBZ gravity-loop and cage
entry, chain grips, carry balls, final hang, cutscene overhead poses, crane
release, emerald look, wall run, parachute yaw and LRZ/SSZ corkscrew yaw.
Sixty new object drawings and six rare-script/transformation drawings follow
native-pose generation; three aliases reuse approved spin/grip art. The complete
12-phase raw tumble bank enables the native selector; incomplete sheets retain
their canonical fallback. Native timing and terminators remain ROM-owned.

Rare animation IDs are also supplied in both characters' Sonic 2/S3K profiles.
TALON adds two legacy hanging/curling poses. S2 tail-only dummy frames deliberately
bind his existing blank (he has no separate fox tails); legacy idle/walk dummy
scripts reuse their exact authored family phases. MICA's normal transformation
has five phase aliases; powered-bank artwork remains a separate obligation.
See [MICA provenance](art/mica/object-poses-wave4/native-pose-provenance.json) and
[TALON provenance](art/talon/rare-poses/provenance.json). This closes the specified
normal animation/direct-pose audit rows, not presentation or powered-bank coverage.

Gilded S2/S3K uses optional powered GGFP banks: 34 native-pose generated base
drawings, deterministic walk/run slopes, explicit normal-animation/phase aliases,
and one combined tile/mapping namespace. Hyper shares Super geometry. See
[powered art](art/mica/powered/README.md) for provenance and the native shared-script
exceptions. Headless posed captures verify presentation rather than route completion.

## Ending ownership (2026-11-10)

`TalonEndings` supplies eight S2 ending object poses, three monochrome search
photos, five 24×16 goggles-down cockpit crops, and S3K reunion selectors 3/4.
Built-in GPT Image transformed real-palette native references against the approved
model and WAIT. Native decoded pixels and raw candidates remain in external
scratch; provenance and indexed authored images live in `art/talon/endings/`.
The first configured sidekick owns photos and the new optional
`S2_TORNADO_PILOT` set. The ending and SCZ/WFZ renderers replace only the cockpit
piece, keeping native sequence clocks, vehicle pieces and DPLC decisions.
Missing sidekick/slot retains native art. The unpublished API remains 0.7.0.

Ending object/reunion sets apply to TALON's solo Tails route; MICA's native S3K
reunion does not include a sidekick portrait. S1 credits demos use the configured
team's ordinary playable sheet. S2 credits are text slides, and S3K scrolls ROM
text (`S3kCreditsState`); neither has a credits gameplay bank to redraw.
Presentation-lane merge must add `TalonEndings.load(assets)` to its endings map;
this lane leaves the character definition and TalonPresentation untouched.

The S3K reunion plane also has a separate sidekick pilot (native selector 12).
`S3K_TORNADO_PILOT` aliases the generated S2 side-view cockpit with hotspot
(12,8). Native alpha geometry is identical; native palettes differ. This keeps
TALON visible in MICA's team reunion without adding a reunion portrait actor.

The optional `competitionPortraits` contract supplies both menu identity sizes.
The wheel, arena selector and results panels share the player portrait; Time
Attack top-three records use a smaller independent pose. Saved native archetype
records show the current configured creator character. See the inventory in
[characters](../../docs/modding/characters.md#s3k-competition-menu-identity) and
`art/<character>/competition-menu/README.md` for art and size evidence.

presentation data. Presentation ownership and remaining standalone Blue Sphere hosting limits are
listed in `art/talon/README.md`.
