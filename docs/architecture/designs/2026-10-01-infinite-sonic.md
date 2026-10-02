# Infinite Sonic procedural challenge

Task: create a separate Infinite Sonic mod on a new branch, in the main checkout
without worktrees, then install it for the local IntelliJ engine launch.
Base: `67c850fc5132156acede8687ca169ada074f795f` (`develop`).
Branch: `feature/ai-infinite-sonic`. The user initially selected terrain-only scope, then requested terrain-aware badniks
and explicitly kept the zero-ring challenge. Terrain prototype commit: `ff18f7ffd4`.

## Implementation

The independent source project is [examples/infinite-sonic](../../../examples/infinite-sonic/README.md).
Its owner-scoped registration contributes a Sonic 1 patch and a namespaced invisible
controller. Only solo Sonic activates the terrain patch. Until 0.8.0 only GHZ1's registry
level identifier was replaced; see the 0.8.0 section for every pre-Final act.
Versions 0.1–0.3 left the existing Mod API and engine source unchanged; 0.4 adds
the opt-in whole-game pacing hook described below.

The normal ROM loader supplies GHZ art, palettes, chunks, collision and background.
`TerrainLibrary` scans decoded foreground columns for continuous floor profiles with
at most four pixels of height change per horizontal pixel. It rejects missing floors,
ceiling surfaces and loop-flagged columns. It aligns selected columns at chunk boundaries
and pairs each with its horizontal reflection. Section entrances/exits therefore share
a floor height, while the interior may rise or dip. Duplicate sections/blocks are
interned. The current World REV01 ROM produces 14 sections; the combined generated
foreground and original background bank uses 124 of the byte layout's 256 indices.
No ROM bytes are packaged in the jar.

A fixed seed and logical two-column section index choose terrain without consuming
the game's RNG. A 64-column layout is regenerated in 16-column steps: at local X=8192,
Sonic and the camera move left 4096 pixels; reverse travel below X=2048 restores the
preceding window until origin zero. A scalar controller origin participates in the
ordinary object rewind codec. The level is a `MutableLevel.snapshot` and runtime
writes use `LevelMutationSurface`, preserving copy-on-write snapshot boundaries.
`PlayableEntity.shiftX` is the published fractional-position-preserving delta operation;
the engine's equivalent `NativePositionOps` helper is not part of the compiled mod API.
No stock physics constants change. The initial prototype paused the timer and
populated nearby seeded encounters without an end condition; the 0.4 survival
follow-up below adds the challenge clock, scrolling failure and replacement HUD.

## Rejected approaches and evidence

- Whole columns with identical unshifted endpoint heights provided only one distinct
  safe column in GHZ1. Mirrored/vertically aligned ROM sections provide variety without
  introducing bridge/loop object requirements.
- Treating GHZ1 as level ID zero loaded the stock course. Its registry descriptor uses
  `0x80`; the implementation now resolves the descriptor rather than hardcoding either.
- Preserving GHZ1's camera maximum Y=768 caused pit death at frame 1049, X=5837,
  Y=986 in a generated dip. The mod owns a deeper camera bound (1536).
- Returning a plain `Level` implementation supported traversal but failed the full
  rewind registry capture because the level adapter requires `AbstractLevel`. The
  supported mutable snapshot wrapper fixes capture and terrain restoration.

These were uncommitted prototype iterations on the base above, not shipped engine
regressions. Temporary probes remain disposable under `target/`.

## Validation and limits

See the [coverage matrix](../validation/levels/infinite-sonic-ghz1.md).
`run_categories.py --base 67c850fc5132156acede8687ca169ada074f795f` selected the entire
ordinary suite (2,957 classes) because the new `examples/` path is unclassified.
Focused validation is proportionate: this is a separate mod, with no engine, public
API, build-policy, physics or timing changes. The production package validator and
actual ROM-backed traversal/reload/rewind paths are exercised directly.

The build script queues engine compilation and validates the resulting jar. The
local installation was also checked with the production repository scanner, state
store, effective catalog builder and restricted `ModClassLoaderFactory`: enabled,
SHA-256 trusted, zero registration failures, content and terrain patches registered.
The jar/state live in ignored root `mods/`, matching IntelliJ's default project-root
working directory. Restart an already-running engine to discover it.

This is a playable prototype, not stock-ROM parity or complete level certification.
GPU presentation, background continuity at rebases, native live-history rewind UI,
movement donors and automatic death/respawn remain unverified. Reflections can mirror
scenery; no loops, checkpoints or difficulty progression are included. Ring rows and pits were added in the 0.3.0 follow-up below.

## Terrain-aware enemies (0.2.0)

Follow-up on `ff18f7ffd45d192802441f3effa2d4d9fc2a279a`, same branch and checkout.
`EncounterPlan` uses an independent seed salt so the existing terrain stays unchanged.
The first three 512px sections are safe; subsequent sections choose rest, ground or air.
Ground candidates require at most 16px relief and 2px adjacent steps across the complete
128px patrol plus sprite margins. Invalid habitats remain empty. Flyers sit above the
highest floor in their whole corridor, with at least 48px below the sprite even at the
bottom of their bob. These thresholds are mod design choices, not ROM constants.

`CourseBadnik` uses ROM Motobug/Buzz Bomber art, collision sizes and priorities,
with custom one-pixel bounded patrols. Source references: `Moto_Main` in S1 object 40,
`Buzz_Main` in objects 22/23, and their animation tables. Shared badnik touch handling
owns damage, bouncing and score; `CourseBurst` uses ROM explosion art and S1 break-item
sound. This pass omits missiles, smoke and released animals. Rings remain absent.

A 32-bit visited-section mask shifts with the terrain window and prevents immediate
respawning of killed or culled actors. Discarded history is intentionally bounded:
far backtracking can regenerate encounters. Actors and explosions implement the
published level-repeat offset contract; scalar patrol and controller state rewind.

Reusing stock native badnik instances was rejected because they do not expose the
needed public position-offset behavior for world recycling. Custom mod actors keep
this change outside engine physics and native object parity. Initial rewind tests
caught missing actors: a `(ObjectSpawn, long)` constructor cannot create the generic
rewind probe. Adding a spawn-only constructor fixed the strict immediate-restore and
forward-replay object-manager comparisons, including occupied slots. The regression
also covers explosion restoration after real ground and flying enemy stomps.

The follow-up selection plan again chose 2,957 ordinary classes plus guards because
`examples/` is unclassified. The same bounded mod-only scope justifies focused validation.
The environment made `.git` read-only during work, preventing the Maven queue from
opening `.git/maven-admission.lock`. No direct Maven bypass was used: Java 21 compiled
the mod and focused test against the already-built, unchanged engine classes, and the
JUnit Platform launcher executed the ROM-backed tests. See the matrix for results.

On 2026-10-02 permissions were restored and the queued Maven focused test passed
all 15 cases with no skips before committing; the cached-engine limitation above
describes the earlier iteration, not the final delivery validation.

## Ring trails and jump corridors (0.3.0)

Follow-up on `b6abb05e818954054392a144ca56377671aa7bfa`, same branch and checkout,
2026-10-02. The requested ring rows supersede the earlier zero-ring challenge.
The logical-coordinate generator already regenerated the window at local X=8192:
subtract 4096 from player/camera and add 4096 to controller origin. Its fixed seed
preserves terrain on backtracking, while forward travel continues the sequence.
This is deterministic procedural assembly of reusable ROM motifs, not a promise
that no local motif can ever recur.

Every fourth 512px section, starting at section 3, now uses flat ROM-derived banks
around a seeded 64/96/128px pit. The first pit is always 128px: testing found that
full-speed Sonic could coast over some smaller gaps, so they alone did not establish
a jump requirement. Removing whole 16px chunk columns at every map row
makes a true opening, without buried collision. Both banks share the existing seam
height and provide at least 192px of runway. Other sections retain the original
14-candidate hill/dip palette. Enemy placement excludes the entire jump corridor.
Cutting arbitrary sloped sections was rejected: a downhill launch and elevated
landing would need a separate reachability proof. Flat equal-height banks provide
bounded jump difficulty without modifying engine physics.

The stock profile starts a jump at 6.5px/frame upward; normal gravity is 0.21875px
per frame squared. A held level-ground jump is roughly 96px high and 60 frames long;
release shortens it. These numbers inform conservative design, while the regression
uses real movement, collision and landing in both directions for all three widths.
It starts at 3px/frame, then holds direction and Jump; this is not a claim that a
standing jump without horizontal input, or every button timing, succeeds.

`RingPlan` independently salts the seed and places four rings, 24px apart, in about
two thirds of sections, including an opening row. Placement checks the full row
against terrain and avoids pits. `CourseRing` uses ROM ring/spin/sparkle rendering,
normal ring awards and sound, and captured scalar pickup state. Four section masks
support partial allocation retries without duplicate rings. Rings shift with world
rebasing and collection rewinds with the player/HUD. As with enemies, revisiting
history discarded from the finite window can repopulate it.

Validation is confined to the mod, as explicitly requested. The unchanged engine
and public API do not need a broad regression run; the change-based fallback selects
2,957 ordinary classes solely because `examples/` is unclassified. See the updated
coverage matrix for completed checks and remaining presentation/respawn gaps.

## Escalating survival challenge (0.4.0)

Follow-up on `30f4e0654e79d3b56e0976277eede2919f0ad983`, current feature branch,
main checkout, 2026-10-02. Whole-game speed compounds by 1.5 every 30 seconds
of active play. `ChallengeClock` counts simulation time divided by the current
rate and owns the fractional frame-step accumulator; its module rewind adapter
restores both. Each rendered interactive frame pumps complete ordinary steps,
leaving native movement, jumps, collision, objects and animation unchanged per tick.
The host bounds creator pacing at 32 steps/frame; the challenge saturates at 32×
and labels the HUD MAX SPEED. At high speeds machine throughput can limit delivery.

The camera advances at least 4.5px per simulation tick, retaining fractional
pixels and vertical tracking. Sonic can gain ground until his centre reaches a
follow point at 60% of the viewport width (just right of centre); the camera then follows his position. It follows world
recycling with Sonic. The initial velocity-matching policy at `841fb3d98b` consumed
every burst of downhill speed immediately but still took away ground on slowdowns,
preventing the player from rebuilding a lead. Position-based following removes
that ratchet without changing Sonic’s physics or the whole-game pacing contract. A running start supplies the opening reaction buffer. If Sonic's right edge
is left of the camera, or another lethal event kills him, the controller ends the
run and clears remaining lives so the death routine takes its game-over branch.
The module replaces the S1 `GameOverFlowProvider` with one that only fades the
music: no GAME/OVER card spawns, the corpse is held with no restart countdown,
and nothing exits to the title or continue screen. After 60 frames the
controller accepts a fresh player 1 A press and requests the ordinary
death-restart reload; lives stay at zero until then so a still-falling corpse
cannot queue a second restart. The reload re-enters `loadLevelOverride` (clock
reset) and the new controller resets score and lives on its first frame. Rings and hit
invulnerability cannot prevent scrolling failure. Scoring earns one point per
minimum-scroll pixel, retaining fractional credit: 270 points per real second at
1× and 405 at 1.5×. Death freezes challenge scoring, progression and scrolling.

The mod suppresses the stock HUD and draws score, speed/countdown, rings and
game-over/restart text using code-drawn glyphs through the CPU presentation primitive path.
It has no GPU-owned mutable state and ships no additional ROM or bitmap assets.

A running-speed-only implementation was tried first while awaiting clarification,
then removed when the user selected whole-game acceleration. Increasing physics
constants would change jump reach and terrain traversal; pumping complete steps
preserves both. A static array of glyph strings was rejected by the package
validator; literal immutable glyph data satisfies creator-state restrictions.

The generic engine hook is `GameModule.gameplayStepsPerFrame()`. Engine presentation
uses the new wrapper; canonical one-tick stepping is retained for trace and capture
tools. Pause, rewind/release, external frame ownership, non-level modes, deaths and
transitions suppress pumping. No game/zone identifiers occur in shared pacing code.
This shared timing/API change requires normal change-based validation, unlike the
earlier mod-only follow-ups. See the coverage matrix for final evidence and limits.

The candidate descriptor deliberately remains unchanged at 0.7.0: its strict
key/value parser rejects comments, so a temporary explanatory comment was removed
after validation caught it. Version rationale belongs here and in the compatibility
guide, not in that descriptor. The final API policy/signature checks pass.

## Countdown and accelerated audio (0.5.0)

Follow-up on `e21eaa933d52a2b9ba039565e075b0f87bf381ca`, same branch and checkout.
The final five seconds have a centered 3px-glyph countdown and one ROM warning
chime at each second boundary. They disappear at the next stage, game over and
the 32× ceiling. Warning decisions derive from the captured challenge clock;
no independent timer or presentation-frame counter needs restoration.

`GameModule.gameplayAudioPlaybackRate()` supplies the continuous multiplier,
separately from the alternating whole-step budget. Using that integer budget as
an audio rate was rejected because 1.5× would oscillate between normal and double
pitch. GameLoop owns and releases its rate on pause/rewind, transition/death and
teardown, without resetting unrelated external playback owners on ordinary
frames. The existing resampling path raises tempo and pitch for music and effects;
its bounded capacity now matches the gameplay ceiling of 32×.

Missing drums were an engine Sonic 1 initialization defect, not missing mod
assets: `Sonic1SmpsCompatibilityPolicy` used legacy silence for boot/stop and
omitted the music header's FM6 disposition. Shipped `StopAllSound` starts with
YM2612 2B=80 and 27=00, followed by FMSilenceAll/PSGSilenceAll. `Sound_PlayBGM`
`.silencefm6` sets FM6 stereo (B6=C0); the seven-track branch disables DAC instead.
The policy now emits these ROM programs. Enabling DAC only in the mod, forcing
it on every note, or embedding drum samples would hide the owning initialization
fault and was not used. All sample bytes still come from the user ROM.

Validation and remaining coverage are recorded in the existing per-act matrix.

### Tempo correction after live playback feedback

Follow-up on `5637105e5c7aa4172c509bb94a7e63d9765e401d`, same checkout.
That version connected the existing forward playback rate, whose SMPS path
rendered extra chip samples after only one driver service. The earlier sample-
voice resampling tests proved pitch change, not shorter ROM note durations;
the user's live playback exposed the missing clock advancement.

The producer now services one source V-blank, renders its samples, then services
the next. A fractional source-frame phase and the integer sample-clock remainder
persist between output packets, participate in snapshot/restore, and roll back
with a failed session transaction. Output remains one wall-clock packet; all
source samples are consumed, including the tail beyond the final selected output
sample. Silent and reverse presentation do not advance the source clock.
Source cadence also advances existing load/fade/SFX state through its ordinary
session service, rather than modifying per-song tempo constants.

Pitch-only PCM resampling from the prior commit is rejected by the new ROM
regression. Bursting extra sequencer services before rendering was also rejected:
that would skip the intermediate notes' audio. The regression compares full
note state and final PCM to the corresponding normal-speed recording at 1.5×,
2×, 3.375× and 32× in NTSC/PAL. It also covers fractional restore, silent frames,
and speed changes back to normal. See the existing validation matrix for runs.

## Elevation tiers and wider pits (0.6.0)

Follow-up on `7e14f78180`, same branch and checkout, 2026-10-02. The request was
more varied solid ground: larger gaps and some elevation change. Corridors keep
their place (every fourth section from section 3) and their flat ROM banks. Each
corridor now joins two elevation *stretches*. A stretch is the three ordinary
sections before it, raised or lowered by -64, -32, 0 or +32px.
Ordinary sections are translated whole by 16px chunk rows. Their outside edges therefore still meet at a flat seam.

Tiers stay stateless, so backtracking and recycling regenerate the same course.
Even stretches choose a tier from the seed. Odd stretches choose a tier within 64px
of both even neighbours. With a 96px tier span, that set is never empty. Independent
per-stretch tiers were rejected: they could ask for a 96px climb, which is at the
limit of a held jump (about 96.6px peak). Pit width is paired with the step:
|step| = 64 allows up to 128px, |step| = 32 up to 160px, and level corridors up to
192px. A held jump that climbs 64px still lands after roughly 47 frames. The physics regression
takes each width at its largest paired step and crosses it in both directions.
One direction is therefore always the climb. A third of drops are pit-less
ledges. Forward travel is never a wall, and backtracking jumps a ledge of at most 64px.
The opening and the first 128px taught pit stay at ground level.

The real constraint is the byte layout's 256 block indices, shared with the 83 GHZ
background/foreground source blocks. Each tier shift, and each pit width × tier ×
bank, creates new blocks. Flats and every pit variant are built first. Ground
keeps all 14 ROM hill/dip sections. Other tiers then take whole ROM sections in ROM
order while the budget allows. With the REV01 ROM this fills exactly 256 slots
and gives raised/lowered tiers three, two and one section shapes. Generated
foreground indices now exceed 0x7F; `TerrainLibrary.floor` no longer masks S1's
loop flag, which the ROM loader already strips from source layouts. Raised sections
repeat the source's bottom chunk row instead of leaving empty space below.

Enemy habitats now exclude every corridor, including pit-less ledges. Validation
is focused on the mod, whose engine dependency is unchanged; see the matrix.

## Title wordmark (0.7.0)

Follow-up on `69600f49c5`, same branch, 2026-10-02. The request was an
INFINITE label above SONIC on the title screen. The only clear band is above the
emblem: the logo starts at screen Y 32, and TitleSonic's highest mapping
piece (Y offset 8 from final Y `$96`) reaches Y 30. SONIC sits in the ribbon at
about Y 103–140, and Sonic fills the ring between them. `TitleWordmark` wraps
the stock `TitleScreenProvider` and delegates everything. After the stock draw,
it flushes a screen-space pass of GL rects. The pass contains a 5×7 font at 3× with a 6px italic lean, a
fire gradient with a chrome horizon row, a 2px navy outline and a 3px drop
shadow. The band spans Y 2–30. It is centred on the projection width, so
widescreen keeps it centred.

The word appears 40 active frames after the title's fade-in, after Sonic rises.
It slides in from the right with an ease-out-back overshoot, trailing speed
streaks. A diagonal glint then crosses it every 180 frames, followed by a
twinkle. It is not drawn during fade-in or the frozen level-select background.

The first draft held its masks in static arrays. `ggfmod` validation rejected
it (`STATIC_STATE_UNSUPPORTED`: only literal static constants are allowed), so
the masks and gradient are now instance fields. The placement was checked against
a ROM-decoded Plane A render, without Sonic's sprite or the GHZ background.
`TestInfiniteSonic.titleWrapsStockScreenWithWordmarkAboveTheEmblem` pins the
wrapper, delegation and band height. The animation was not checked in a live window.

## Every zone before Final Zone (0.8.0)

Follow-up on `bb62addc50`, same branch and checkout, 2026-10-02. The request was
support for every other Sonic 1 zone except Final Zone. Every act of registry zones
0–5 (GHZ, MZ, SYZ, LZ, SLZ, SBZ, including SBZ3's LZ layout) now loads the course;
zone 6 (Final Zone) and the ending stay stock. Each act builds its library from its
own decoded layout, so art, chunks, collision, background, palette cycles and music
are that zone's. All acts of a zone share a tileset; merging their layouts was not
attempted because loading sibling levels from the override has unverified side effects.

A probe of the 0.7.0 scanner built only GHZ1, SYZ1, SYZ3 and SLZ2. It required the
*topmost* floor of a whole 256px column and assumed GHZ's seam residue, and the
other zones' tunnels, ceilings and deeper layouts defeat both. The generalised scan:

- Traces every standable surface (floor-capable solid pixel with open space above) in
  each column, following it with at most 4px change per pixel. A section must keep
  `CLEARANCE` = 112px of collision-free space above its floor at every X. Content
  above that window is copied unchanged and may be solid, so the encounter regression
  now scans down from `floor - CLEARANCE` rather than from the column top.
- Picks the seam residue (floor Y mod 16) with a flat jump bank and the most
  candidates. Sections still move by whole 16px chunk rows. Seam = 960 + residue.
- Compacts the background to the source blocks its layer actually uses (4–13), not
  the whole source block bank. That raises the generated-foreground budget from 173 to
  243–252 slots. Ground sections are now budget-checked as well as raised tiers.
- Caps the course at six 256px rows (camera limit 1536) and always clears jump-bank
  scenery above `BANK_CLEARANCE` (176px). Without both, MZ3, SYZ2 and SBZ1 overflowed
  the 256-entry index on their 8-row bank cut variants alone.
- Makes section 0 a flat bank. A zone's first hill candidate could otherwise put the
  start inside a slope.

GHZ terrain changes as a result. The seam residue becomes 0 (seam 960, formerly 963),
and its tiers have more shapes. The seeded rules for corridors, tiers, encounters and
rings are unchanged.

Course start: the stock descriptors put Sonic at each act's own start (e.g. SBZ3 at
X=2944, Y=0). `CourseZones` delegates the stock registry but returns course
descriptors whose start resolves lazily to (80, seam − 19). The level manager captures
the descriptor before loading and reads its start after the override has built the
library. A first-frame teleport from the controller was rejected because the camera
and the first title-card frames would already use the stock start.

Water: the S1 zone feature provider only owns LZ/SBZ3 water. It handles dynamic
heights, wind tunnels and water slides, and its tunnels and slides match ROM layout
block IDs (`Slide_Chunks`) that generated blocks reuse for unrelated terrain. Underwater
top speed is also below the minimum scroll. The course therefore returns a dry
`CourseFeatures` provider (it keeps S1's horizontal background wrapping) and a
`WaterDataProvider` wrapper reporting no water. GHZ/SLZ loop handling stays inert
because `Sonic1LoopManager` only acts on a `Sonic1Level`.

Lava: MZ lava tiles are ordinary solid collision. The damage comes from invisible
object $54 (`LTag_Main`, hurt sizes $14–$16), which the course never spawns. An offline
render showed lava chosen as MZ flat banks and hill sections. Candidates covered by a
stock Lava Tag (±$80 horizontally, ±$30 vertically) are now rejected. Lava still shows
beneath some grass ledges, as in the stock zone, but is never walkable.

Enemies stay Motobug/Buzz Bomber; `Sonic1ObjectArtProvider` loads both in every zone,
on palette line 0. Zone-native badniks would be a separate follow-up. Validation:
see the coverage matrix's 0.8.0 section.

## Title zone picker and act-less cards (0.9.0)

Follow-up on `e2432a8d53`, same branch, 2026-10-02. The request was to drop "ACT n"
from the mod's title cards and add a left/right zone menu (zone, not act) to the title.

Engine hooks (additive to the unpublished 0.7 candidate; pin regenerated in place):

- `TitleScreenProvider.startZoneIndex()` (default 0). `GameLoop` previously hard-coded
  `loadZoneAndActForFreshRuntime(0, 0)` for a one-player title exit; it now loads the
  provider's zone, act 0, falling back to 0 when out of range. Redirecting GHZ1 inside
  `loadLevelOverride` was rejected: zone-indexed owners (title card, music, scroll and
  palette-cycle handlers) would still see Green Hill.
- `GameModule.showsTitleCardActNumber(zone, act)` (default true, delegated). The S1
  card already hid the act element for Final Zone; a module veto hides it too and moves
  the oval to 8 px after "ZONE" (`Card_ConData` FZ row: `$12C − $124`), keeping its slide
  distance. The S2 card honours the veto as well. The mod vetoes zones 0–5.

`ZoneMenu` sits in the only clear band: the emblem's ribbon tails end near Y 178 and
"(C)SEGA 1991" occupies Y 200–207 right of centre. A first placement at Y 166–192
(the unused PSB slot) covered the ribbon tails and copyright in a live widescreen
capture and was moved to an 18 px banner at Y 181–199 with pips at Y 212, left of the
copyright. The live capture also showed both chevrons pointing inward; fixed. The
first slide crossfaded the old and new names on top of each other ("EEN H.MARBLE");
it is now sequential (old whips out, new eases in, 12 frames). The menu rises in after
the wordmark lands, an early left/right reveals it at once, presses play S1
`sfx_Switch` ($CD), and the selection survives title resets. Layout states were checked
in offline composites of captured draw commands over the earlier live capture; the
final build was not re-captured live, and the act-less card was checked by element
inspection, not a render.

Validation: `run_categories.py --base e2432a8d53 --run` selected the full ordinary
suite (2958 classes) plus guards. Its only change-caused failures were ten
`TestGameLoop` title-exit errors (a test module with no zone registry); `titleStartZone`
now falls back to zone 0, and `TestGameLoop` (92), `TestInfiniteSonic` (62, 0 skipped),
`TestModApiSignatureSurface` and the S1/S2 title-card tests pass. Unattributed to this
change: S3K ROM-backed audio errors (the root S3K ROM's SHA-1 does not match the
documented dump, so no path was supplied), and `TestRemainingRewindTailInventory`
(1316 vs 1315 classes) plus the `TestObjectPhysicsStandardizationGuard` violation in
`LrzFlameObjectInstance`, both reproduced unchanged on `e2432a8d53`.
