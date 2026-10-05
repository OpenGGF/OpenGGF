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
main checkout, 2026-10-02. Whole-game speed originally compounded by 1.5 every 30 seconds
of active play. `ChallengeClock` counts simulation time divided by the current
rate and owns the fractional frame-step accumulator; its module rewind adapter
restores both. Each rendered interactive frame pumps complete ordinary steps,
leaving native movement, jumps, collision, objects and animation unchanged per tick.
The host bounds creator pacing at 32 steps/frame; the challenge saturates at 32×
and labels the HUD MAX SPEED. At high speeds machine throughput can limit delivery.

Follow-up, 2026-10-05: the 1.5× compounding curve (1× → 1.5× → 2.25× → 3.375×)
ramped too aggressively in play. Each stage now adds 0.25× linearly (1× → 1.25×
→ 1.5× → 1.75× → 2×); the 32× ceiling and MAX SPEED label are unchanged but now
sit far beyond a realistic run.

The camera advances at least 4px per simulation tick (4.5px before 0.13.0), retaining fractional
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
minimum-scroll pixel, retaining fractional credit: 240 points per real second at
1×, 300 at 1.25× and 360 at 1.5× (270/337/405 before 0.13.0). Death freezes challenge scoring, progression and scrolling.

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

Enemies stayed Motobug/Buzz Bomber in 0.8.0 (`Sonic1ObjectArtProvider` loads both in
every zone); 0.10.0 replaces them with zone badniks. Validation: see the coverage
matrix's 0.8.0 section.

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

## Zone badniks, session lives and the death menu (0.10.0)

Follow-up on `cb5fe701ee`, same branch, 2026-10-03. The request: zone-appropriate
badniks instead of GHZ's everywhere; lives that persist for the session; and a
CONTINUE/RESTART menu when Sonic dies.

**Badniks.** `CourseSpecies` lists each species' ROM art key, `obColType`, priority,
display width, standing radius (`obHeight`/`y_radius`) and animation frames, taken from
the shipped objects and the engine's S1 implementations. Line-ups are keyed by the
source level's stock zone id (`Level.getZoneIndex`), the same id that selects the
object art, so SBZ3 uses Labyrinth's line-up and art. Every listed species' art is
loaded for its zone by `Sonic1ObjectArtProvider`. `EncounterPlan` draws the species
from bits of its existing seeded value, so encounter positions and the empty/ground/air
split are unchanged; ground Y is `floor − depth` and flyers clear the 48 px gap by
their own depth. The spawn subtype stores the species id, so rewind recreation
needs no extra state (append-only ids; GHZ keeps Motobug 0 / Buzz Bomber 1). A first
draft used an enum; `ggfmod` validation rejects it (`STATIC_STATE_UNSUPPORTED`: mod
classes may hold only literal static constants), so species are int ids and a
`Traits` record built on demand.
Behavior stays the mod's bounded patrol: Rollers ($8E while rolling) and Walking Bombs
($9A) keep `col_hurt` and cannot be destroyed; Orbinauts expose four `$98` spike touch
regions on the radius-16 `Orb_CircleSpikeball` orbit through the multi-region touch
contract; Ball Hogs hop in place. Left out: Jaws (they only swim, and the course is
dry), Caterkillers (segmented body chain), Chopper and Newtron (water/ambush
behaviors), all projectiles, bomb fuses and Yadrin's `React_Yadrin` spiked top.

**Lives.** `CourseSession` (module service and rewind adapter) outlives the death
reload. It awards one life per 50,000 points of total score, mirroring REV01
`AddPoints`' Japanese-console rule as a mod choice (overseas consoles only advance
the threshold); 100/200-ring lives use the existing `LevelGamestate` award. The HUD
row reads `RINGS n  LIVES n`.

**Death menu.** On death the controller records the lives left, the score and a
clock snapshot taken before `end()`, then sets the engine's lives to exactly 1. The
death routine's own subtraction then reaches zero, takes the game-over branch to the
course's `GameOverFlowProvider` and holds the corpse with no restart countdown, as the
previous press-space restart did. Letting the stock routine run with lives left was
rejected: it counts down and requests the reload itself, leaving no time for a menu.
After 60 frames, edge-detected up/down move the cursor (`sfx_Switch`) and a fresh A
press confirms; both choices use the ordinary death-restart reload. CONTINUE makes
`loadLevelOverride` restore the saved clock instead of resetting it, and the new
controller restores the score and session lives. RESTART (and the last-life GAME OVER
restart) resets the session. A continue restarts from the course opening, not the
death distance: the opening runway is the only start that is guaranteed to be flat,
enemy-free and pit-free. Restoring the logical origin was rejected for that reason.


## Act line-ups, ring-only lives and in-place CONTINUE (0.11.0)

Follow-up on `9423b53e23`, same branch, 2026-10-03. Feedback on 0.10.0: start with
0 lives and earn them only from 100 rings; badniks still not zone appropriate; CONTINUE
must not reload but resume from the last safe spot.

**Badnik investigation.** The 0.10.0 selection was checked in the live course rather
than assumed. In every zone the spawned species matched the table, and the art sheets
those badniks draw from, rendered offline from the loaded `Sonic1ObjectArtProvider`
sheets with the level palette, showed the right sprites (MZ: Yadrin, Buzz Bomber,
Batbrain; SBZ: Walking Bomb, Orbinaut, Ball Hog). Sheets hold their own pattern copies, so
there is no VRAM-slot aliasing. Neither the jar contents nor the code path explained
the report. Code mods only load at JVM start, so an engine still running the previous
jar is the remaining explanation; this was not confirmed. A GL capture with the mod is
not available: `GameplayCaptureTool` boots with the DETERMINISTIC launch policy, which
disables external content, and the STANDARD policy alone did not discover the installed
mod. The hand-written zone table was itself a guess, so line-ups now come from the act's own stock
object layout (`TerrainLibrary` reads the source level's `getObjects()` and maps
Sonic1 object ids). Each placed badnik becomes one entry, so species appear as often as
the act places them. The zone table stays only as a fallback for an act with no supported
ground or air badnik; no shipped act needs it. Derived line-ups (ground | air):
GHZ Motobug+Crabmeat | Buzz Bomber; MZ Yadrin (one placed per act; Caterkillers
unsupported) | Batbrain+Buzz Bomber; SYZ Crabmeat+Yadrin(+Roller acts 1–2) | Buzz
Bomber; LZ and SBZ3 Burrobot | Orbinaut; SLZ Walking Bomb | Orbinaut; SBZ1–2 Ball Hog+
Walking Bomb | Orbinaut.

**Lives.** The session starts with 0 spare lives (`resetSession` then 0). The
controller pre-claims the stock `LevelGamestate` 100/200 flags every frame and awards
a life itself whenever the ring count reaches a new multiple of 100, including a
return to 100 after a ring loss. The 0.10.0 every-50,000-point life is removed. HUD and
menu show spare lives.

**In-place CONTINUE.** The controller records the logical world X of the last frame
Sonic stood (not airborne, not hurt) on floor that exists from 32 px behind to 64 px
ahead within 24 px of relief. CONTINUE spends a spare life, restores the clock captured
at death, calls the sprite's level-start `resetState()` (clears dead, death routine,
hurt, object control and animation), writes the position (`floor − 19`), resets the
position history, faces right with the usual running start and $78 frames of
invulnerability, zeroes rings, unfreezes the camera that `applyDeath` froze, puts Sonic a
quarter of the way across the screen and restarts the zone music the death flow faded.
Terrain, origin, cleared encounters and score are untouched because nothing reloads. If
the spot has left the retained window, the first safe floor right of the screen edge is
used. Holding the corpse is unchanged (lives set to 1 so the death routine reaches zero
and takes the course's game-over flow). RESTART and the no-lives GAME OVER still use
the ordinary death-restart reload. The 0.10.0 reload-based continue, which restarted
from the opening runway, was rejected by the user.

## Platform stretches, ring arcs, 16:9 and single-level zones (0.12.0)

Follow-up on `53573ea280` after merging develop (`d112667006`), same branch, 2026-10-03.
Request: make play more varied, particularly platforming; force 16:9; each zone is one
level, so remove the in-game level select.

**Platform stretches.** Sections 4k+2 and 4k+3 of a third of eligible stretches (k ≥ 2,
banks within one tier) become one 320–448 px pit: 4k+2 keeps a flat bank and opens
with a corridor half-cut at its right end, 4k+3 is an empty column then the far bank's
half-cut. The only new block is one empty block. `PlatformPlan` places one to three
stones level with the lower bank, adding stones until every open span is ≤ 144 px.
The stones are the shipped objects, created through the session `ObjectRegistry`
(exposed as a module game service): Obj18 subtypes $00/$03 (stationary, falls after 30
frames) and Obj52 at subtype `look << 4` for each appearance the act places. Kinds come
from the act's own stock placement, as badniks do, so the zone has loaded their art.
Both objects seat Sonic at obY−9 (`MvSonicOnPtfm2`), so a stone's centre is its surface
+9. SBZ appearance 2 is excluded because only full subtype $28 selects the stomper art.

Stock objects delete themselves outside `out_of_range` (camera−128 to camera+width+192
in 128 px steps), so the controller spawns each stone only within 64 px of the screen,
tracked by per-section bits like rings. They keep their spawn coordinates and do not
follow the 4096 px world shift, so the shift waits until no stone is loaded and is
forced at local X 12,288 (stones dropped and re-spawned). Rings arc over corridor pits
(four rings, 40–94 px above the higher bank, overhanging each bank by 32 px) and sit
above stones.

Rejected: Obj52's 32 px look (the only Labyrinth block, also MZ3). In the first physics
run the policy landed on the last pixels of a 32 px MZ3 stone and ran off before it
could re-jump; at running speed a 32 px stone gives about five frames. Stones are now
≥ 64 px, so the Labyrinth course keeps ordinary corridors. Acts without usable stones:
MZ3, LZ1–3, SLZ1, SBZ2–3; pooling a zone's acts is a possible follow-up. Also rejected:
the test policy holding forward whenever its predicted landing was on a stone; it
drifted to the far edge of MZ2's stone and fell. The policy now coasts toward a short
surface's centre, and re-presses jump on even level frames so a held jump cannot
block a new one while replays stay deterministic.

**16:9 and level select.** `GameModule.requiredDisplayAspect()` and
`suppressesLevelSelect()` are additive 0.7 candidate hooks. `Engine.initializeGame`
applies the required aspect as a session override once the patch resolves, before
gameplay opens; clearing session overrides at the master title restores the player's
aspect, and `resolveDisplayAspect` still forces 4:3 in trace test mode. Suppression
ignores `LEVEL_SELECT_ON_STARTUP`, maps a title LEVEL_SELECT exit to one-player play and
disables the debug level-select key. The polarity is "suppresses" so Mockito module mocks
(default `false`) keep level select.

Validation (focused, 2026-10-03, queued Maven, S1 REV01): `TestInfiniteSonic` 129 run,
0 failures, 14 skipped (the crossing test for the seven acts without stones, at both
aspects); `TestEngine` 33, `TestGameLoop` 97, `TestModApiSignatureSurface` 9,
`TestModApiPinPolicy` 4, `TestModApiReleasePolicy` 13, `TestModApiHookPolicy` 19, all
passing. The crossing test drives the input policy over each act's first stretch at 4:3
and 16:9 and requires every planned stone to spawn as a stock object and Sonic to ride
one. Live play and a visual check of the stones were not performed.

## Gentler hits, shield monitors and a slower scroll (0.13.0)

Follow-up on `741b2b34cc`, same branch, main checkout, 2026-10-05. Request: one enemy hit
was effectively a game over (the knockback alone drops Sonic behind the scrolling edge);
with 20 rings a hit should cost just 20 rings and no knockback; slow the minimum scroll
to give more time on platform stretches; add a shield pickup that negates knockback for
one hit.

**Hit absorption.** Every hazard in the course is a `CourseBadnik` (stock platforms do
not hurt), so absorption lives in that object's `TouchResponseListener`. The engine's
touch pass calls the listener before it applies damage. `CourseGuard.absorb` decides
whether the contact would hurt (category HURT, or ENEMY while Sonic is not attacking,
mirroring the engine's S1 test: roll animation with the rolling status, or the shared
anim 9); if so, a shield is removed (rings kept), else 20+ rings pay a 20-ring toll.
Then it sets the stock $78 post-hit invulnerability, which makes the engine's following
`applyHurt` return immediately: no knockback, no lost-ring spawn, no hurt routine.
Below 20 rings with no shield the stock path runs unchanged (knockback and full ring
loss, or death with none). No engine hook was needed. Rejected: an engine damage-policy
hook on `GameModule` (Mod API surface change for a single mod) and undoing a hurt after
the fact from the controller (lost rings already scheduled, velocity already replaced).

**Shield monitors.** `ShieldPlan` places at most one box per open section (not a corridor
or platform stretch, section ≥ 6, seeded one in ten), on ground flat within 4 px across
its 32 px width, at offset 304 or 160 so it never overlaps a ring row. `CourseMonitor`
draws `Map_Monitor` with the `.shield` animation (static frames 0/1/2 alternating with
icon 6, speed 1) and the broken shell (frame 11). It is a non-solid `col_item` ($46): the
stock Obj26 is solid at the sides, and a stall against it at scroll speed would push
Sonic off the left edge. Any touch breaks it, spawns the mod's explosion and calls the
stock `giveShield()` with sfx $C1 and $AF. It follows world rebases and rewinds like the
other mod objects.

Found while testing: `CourseController.liveStones()` dereferenced every active object's
spawn, but the player-bound shield has none, so the first shield crashed the controller
every frame; it now skips spawnless objects. Also found: `giveShield()` replacing a live
shield leaves the destroyed instance in the fixed power-up slot, and a rewind restore
then consumes that stale captured entry for the respawned shield, so the shield vanished
(reproduced in stock S1 headless; the same applies to two stock shield monitors in a
row). The mod avoids replacement (a box broken while shielded only plays the sound); the
engine quirk is left for a separate fix.

**Scroll.** The minimum scroll drops from 75% (0x480, 4.5 px/tick) to two thirds of
0x600 (0x400, 4 px/tick), about 11% slower; survival scoring follows (240 points/s at 1×).

**SDK packaging on macOS.** `build.py` failed in `ggfmod package` with "Directory entry
escapes root": `ModAssetSnapshot` copied the tree under `Files.createTempDirectory`,
which on macOS is `/var/...`, while the containment check compares each entry's real path
(`/private/var/...`). The snapshot now returns its temp root as a real path.

## Ring spill, lower top speed and a left follow point (0.14.0)

Follow-up on `5220937207`, same branch, main checkout, 2026-10-05. Request: on a 20-ring
toll the rings should actually come out of Sonic, still without knockback and with
immediate invulnerability; lower Sonic's top speed a little; hold him about 30% of the way
across the screen.

**Spill.** `CourseGuard` sets the $78 flash before anything else (so the new rings start
uncollectable, as after a stock hit: S1 collects lost rings only below flash time 90), then
calls the stock `RingManager.spawnLostRings(player, 20, frame)`: the shipped bouncing-ring
objects, spread and sound. That spawn empties the ring counter as stock damage does, so the
guard restores the remainder immediately after. The shield branch spills nothing. Lost
rings keep local coordinates and do not follow the 4096 px world shift, so the shift waits
while any are live (forced at the same limits as for stock platforms); they expire in a few
seconds.

**Top speed.** The module wraps the S1 `PhysicsProvider` while a course is active and
returns each profile with `max` capped at 0x540 (7/8 of 0x600); every other constant,
including jump and roll speeds, is unchanged. The sprite resolves its profile at level
start and in `resetState`, so CONTINUE keeps the course value and Final Zone stays stock.
The running start and CONTINUE use 0x540. The 4 px/tick minimum scroll is now explicit
(0x400, 76% of the new top speed). Every act's platform crossing still passes, so the
144 px stone spans and 192 px pits remain jumpable.

**Follow point.** 60% → 30% of the viewport width: Sonic can bank less lead, but sees far
more of the course ahead. The CONTINUE placement (25%) and the course start (80 px) are
both left of it.


## Monitor variety, exit to title and the run frames (0.15.0)

Follow-up on `2a4ca53aa0`, same branch, main checkout, 2026-10-05. Request: more variety in
the levels; a way back to the title screen (EXIT at death, and Escape); show Sonic's
full-speed running animation even though the course caps him below it.

**Spring chasms (removed).** The first cut of 0.15.0 added red launch springs before some
wide pits (mod-drawn, non-solid, -$1000, the only wide-pit set piece in LZ/SLZ1). Real
physics crossed them in all six zones, but in play review they were judged to add nothing:
the launch is automatic, so the chasm asks nothing of the player. Removed before release;
platform stretches are back to their 0.12.0 rules. A solid stock Obj41 was rejected earlier
because its side stalls Sonic against the scrolling edge.

**Monitor variety.** `ShieldPlan` became `MonitorPlan` (one in eight eligible sections,
up from one in ten) with S1 Pow subtypes as kinds from a separate seed: 5/10 shield,
3/10 Super Ring (Pow_ChkRings: +10, sfx_Ring) and 2/10 Invincibility (Pow_ChkInvinc:
`giveInvincibility()` and bgm_Invincible), shown with Map_Monitor icons 6, 8 and 7.
Found while testing: S1's touch pass handles only the first overlapping object, so a
badnik patrolling over a monitor takes the touch first; that is normal play, and the
test clears nearby patrols.

**Hop arcs.** A third of ring rows lift their middle pair by 28 px.

**Exit to title.** The death menu is now an option list: CONTINUE (with a spare life),
RESTART and EXIT; the no-lives GAME OVER shows RESTART/EXIT instead of a press-space
restart. EXIT ends the challenge clock and calls `requestGameOverExit(TITLE_SCREEN)`, the
stock GAME OVER card's own exit, so `GameLoop` fades to black and enters the Sonic 1 title
(with the zone picker) through the existing path. A tap of Escape (GLFW 256) or the
gamepad Back button does the same mid-run. Level objects have no keyboard access, so the
module captures the live `InputHandler` the title screen receives (every course is
started from it) and serves it through `getGameService`. Holding Escape for two seconds
still reaches the engine's master title.

**Run frames.** Sonic_Animate selects SonAni_Run (and Roll2) from inertia $600, which the
0x540 cap never reaches. The controller swaps Sonic's `ScriptedVelocityAnimationProfile`
for `withRunSpeedThreshold(0x500)` each tick it differs, so cruising shows the run frames;
the frame delay is unchanged ($800 - inertia >> 8 is 2 at both 0x540 and 0x600).

## High routes and zone hazards (0.16.0)

Follow-up on `e974573148`, same branch, main checkout, 2026-10-05. Request: after the spring
chasms were removed, implement two of five proposed variety ideas: zone hazards that need
timing, and high/low route splits.

**High routes.** `RoutePlan` hangs three of the act's stationary stock platforms (Obj18 type
0, Obj52 $x0, and now SLZ's Obj59) as one ledge over an open section whose floor varies by
at most 16 px; one in five eligible sections from the seventh. They reuse the platform
stretch machinery: `PlatformPlan.at` returns route stones for ordinary sections, so the
controller's stone spawning, rebase wait, and the ring row above stones all apply. The
route section forces a ground badnik beneath (when the patrol finds gentle footing) and,
half the time, a monitor on the last platform; the ring row skips that platform.
Tried and changed: stones spread evenly across the section (171 px apart) are overshot by a
full-speed jump (about 300 px), so the ledge became three platforms 24 px apart that Sonic
runs across (a 4 px/frame run-off drops about 4 px, inside PlatformObject's 16 px catch).
A 64 px rise was also too high: a held jump rises about 96 px on level ground but the
measured takeoff on a GHZ slope launched at -$597 and rose about 70 px, so the ledge is 48 px
above the highest floor (at most 64 px above any). The physics test jumps 140 px before it;
a full-height jump can overshoot the first platform and still ride the ledge to its end.
Obj59 subtype 0 is not stationary (Elev_Var2 entry 0 is action 1: wait, then rise $10);
kept as the stock behaviour. Adding it gives SLZ1 platform stretches too.

**Hazards.** Rejected: spawning the stock hazard objects (Obj13/14 lava balls, Obj57, Obj58,
Obj6D). They hurt through the engine's own hit path, bypassing `CourseGuard`, and stock
knockback at the 30% follow point almost always ends the run at the left edge; they also
keep their own coordinates through the world shift. `CourseHazard` instead draws each zone's
ROM sheet (zone-gated in the S1 art provider, so every act of the zone has it) with hit boxes
from React_Sizes, mod motion, a `CourseGuard` touch listener, and the level-repeat offset:
spikes (Map_Spike frame 0, col_40x32), Obj14 fireballs (types 1/2, -$500/-$600, gravity
$18, every 72 ticks while on screen), the Obj58 ball (col_32x32, a 96 px cosine roll), the
LZ Obj57 chain (radius $50, -$180 per tick as subtype $D5, only the spikeball hurts), the
Obj6D pipe (Ani_Flame frames, col_24x48 only at the full-flame frame $0A), and a GHZ giant
ball (Map_GBall, col_40x40) on Map_Swing_GHZ chain links. `HazardPlan` places them in open
sections (no badnik, monitor or route there) and MZ/SLZ fireballs in corridor pits of 96 px
or more. The controller spawns them with a section bitset like monitors.

**Plan memo.** The controller asks every plan about every unspawned section each tick, and
route/hazard/encounter plans scan hundreds of floor samples; `TerrainLibrary.memo` caches
them per section (pure seeded functions; derived data outside rewind). The test class went
from 87 s to 74 s.

Found while testing: a stomp test picked a badnik under a high route, and the falling Sonic
landed on the ledge; a monitor test's search crossed hazards and broke other monitors.
Both tests now avoid the overlap.

## Split paths, hazard frequency and no Invincibility (0.17.0)

Follow-up on `5bceccc8bc`, same branch, main checkout, 2026-10-05. Play review: the 48 px
ledges in GHZ and MZ were "virtually on the ground" and not a different path; Invincibility
monitors are reserved for another plan; some hazards (the GHZ wrecking ball) were not seen.

**High roads (replacing the one-section ledges).** Rejected for a true two-floor terrain: the
generated layout already spends its 256 block slots on ground sections and tiers, so
composite blocks with a second floor have no room. `RoutePlan` instead builds a second path
per stretch (sections 4k-4k+2, one in two eligible from stretch 2; GHZ's hills leave about one in eight stretches eligible-and-chosen, SLZ/SBZ about two in five) out of the act's
stationary stock platforms: steps (pairs of platforms under 128 px, single 128 px blocks; a single 96 px MZ block left too little runway to land late and jump again) climb in
equal rises of at most 48 px with 120 px gaps (a step's pair sits flush; at 100 px a late landing on a step left no full jump to the next), then a level road 128 px above the highest floor
under the whole route runs with 8 px gaps to offset 320 of section 4k+2, leaving ground before
the next corridor. Floors may vary by 64 px over the stretch (at most three steps). The
controller's per-section stone slots grew from three to six. Rings ride the road; half the
roads end in a monitor; road sections force ground patrols and take no hazards. The physics
test's policy chooses only when to jump and which way to steer in the air; a first version
that required an exact landing window and a full-height jump failed because jumps off GHZ's
gently curved floor rise about 70 px instead of 96. Then a 24 px gap inside an SLZ step caught a
landing and dropped Sonic to the ground, so steps became flush pairs and road gaps 8 px.

**Hazards.** A per-zone count over 1000 sections found every art sheet loaded and ready, and
GHZ placing only 54 wrecking balls (about one every 30 s at 1x): its fit demanded level ground
across the whole ±128 px swing, and a failed fit placed nothing. Ground hazards now take one
in three eligible sections (was one in four), three in four signatures (was two in three),
fall back to a spike bed when the signature does not fit, and only need level ground where
the hazard meets the path (±48 px for the swing and chain); the Spring Yard ball follows the
floor, so its run only has to be gentle.

**Monitors.** Invincibility is removed: three in five shields, two in five Super Rings.

## Follow point, leaderboards, speed ramp, seamless background, kept rings (0.18.0)

Follow-up on `8e6d2e26e3`, same branch, main checkout, 2026-10-05. Play review asked for
Sonic nearer the middle, per-zone leaderboards (at each zone's start and on an idle title,
arcade style), a smooth speed-up, a background that does not jump when the window shifts,
and CONTINUE keeping the rings.

**Follow point.** 30% → 45% of the viewport (180 px at 16:9). CONTINUE puts the camera
back at the same point instead of a quarter of the way across.

**Kept rings.** CONTINUE no longer zeroes the rings; `ringsSeen` takes the kept count so
lives already earned for it are not paid twice. RESTART and EXIT reload, which clears them.

**Speed ramp.** `ChallengeClock` keeps a live `rate` that moves 0.25/60 per presentation
frame toward the stage multiplier, as `RewindSpeedController` ramps the tape coast; the step
budget, the audio rate and the countdown's real-time conversion all read it. The stage
(HUD speed, score per second, leaderboard speed) still changes on the boundary. The rate is
in the rewind snapshot; the old four-field snapshot constructor now means a settled rate.

**Background across a shift.** Every S1 `Deform_*` handler derives its bands from camera X:
GHZ, MZ, LZ, SBZ accumulate camera deltas at 96/256, 128/256 and similar, GHZ's water lines
interpolate from bg2 X to camera X, and SYZ/SLZ read camera X directly. The -4096 px shift
therefore moved GHZ's mountains 1536 px and its hills 2048 px inside an 8192 px period, and
bent the water. Rejected: subclassing the handlers to shift their accumulators (most keep
them private) and wrapping the logical X at a period (no single period is seamless for every
band: GHZ's water alone needs 104 × 16384 px). Landed: `CourseScroll` wraps the module's
scroll provider and gives each stock handler the logical camera X (origin + local X), which
only moves forward, then adds the origin back to each FG word (keeping any per-line deform)
and recomputes the BG-FG spread. Background window selection already wraps with
`floorMod`, so large BG camera X values are safe. Off the course the origin is 0 and the
calls are stock. Reverse recycling is removed: the camera never scrolls back, so it was
unreachable. Known: over a long run GHZ's water interpolation spreads (as it would in a
very long stock level).

**Leaderboards.** `Leaderboard` keeps each zone's top 10 (score, speed reached) in
`saves/infinite-sonic/leaderboard.txt` under `SavePaths.root()`, outside rewind. A run
(identified per level load) submits at each death and on an Escape exit and replaces its own
entry, so CONTINUE and rewind cannot list it twice. The HUD shows the top 10 for the first
300 updates, flashes NEW TOP SCORE! with the checkpoint chime when the score passes the
earlier top, and the death screen shows NEW TOP SCORE! or RANK n OF 10. `LeaderboardScreen`
is the title's attract board: after 600 idle frames on the active title it pages ZONE
LEADERS and each zone with scores (300 frames a page), then returns; the dismissing press
is kept from the title so it cannot start a game. Name entry was not added.

Found while testing: the hazard timing trial passed at 30% by luck. Hazards only tick inside
the object window and pit fireballs only launch while on screen, so a coast taken far before
the hazard could not move its phase; at 45% Star Light's fireballs lined up with every
arrival. The trial now brakes and waits just before the hazard, once it is running.

## Title background follows the zone picker (0.19.0)

Follow-up on `7be4f86ea7`, 2026-10-05. Request: the title background should match the zone
the picker shows.

The stock S1 title draws its GHZ Plane B inside `draw()`, between the backdrop and the
emblem, so a wrapper cannot replace it. Engine change: `Sonic1TitleScreenManager` takes an
optional `BackgroundOverride` (null keeps the stock path) that advances in the main-screen
update, draws Plane B under the title's fade and supplies the backdrop. Its art goes in the
upper half of the existing S1 title background atlas range (no new `PatternAtlasRange`, which
is Mod API surface). It may upload its own palette lines 0-3; the title re-uploads its
palette after the override has flushed, before Plane A. Rejected: a donor render context for
extra palette lines (global, session-scoped state for one screen).

Mod: `TitleBackground` loads each zone's act 1 once through its own `Sonic1` reader (calling
`createGame` would replace the session's PLC service, a rewind adapter), draws its background
layer with that zone's stock `Deform_*` handler at the title's 2 px/frame and a camera Y 112 px
above the act's start, and fades a newly picked zone in over 12 frames. Green Hill keeps the
stock title background (water palette cycle included). The wrapper marks the override active
only around its own calls into the stock title, so a session without the mod draws stock.
Verified with a temporary software render of each zone's plane (removed) and a regression test.

## CONTINUE ghost glide, READY hold and restart runway (0.20.0)

Follow-up on `2fd4004444`, 2026-10-05. Requests: a more gradual CONTINUE, perhaps a ghost
of Sonic moving back to the restart spot; and runs sometimes restarted right before a big
hole, so Sonic fell in before the player could react.

**Restart runway.** The last safe spot only needed floor 32 px behind and 64 px ahead. The
restart spot now also needs 448 px of pit-free floor ahead (about 1.4 s at the course top
speed), searching back from the last safe spot (or the death, if earlier) through the
retained window in 16 px steps, then forward from the screen's left edge as before.

**Sequence.** CONTINUE spends the life and revives Sonic at the spot hidden, object-controlled
(no physics) and invulnerable. Glide: 45-90 frames by distance, smoothstep; a ghost drawn with
Sonic's own `PlayerSpriteRenderer`, his last living mapping frame and facing, under the
engine's ghost render effect (as time-attack ghosts), plus two fainter echoes 4 and 8 frames
behind; it starts from the death point clamped to the screen (a pit death rises from the
bottom edge). The camera X follows the same curve; Y uses the camera's own tracking of the
ghost. READY: Sonic shows and waits; right or jump after 20 frames, or 120 frames, sets off
with the course's running start and GO!. The clock stays ended (1x steps) until then, so the
countdown and score wait; `ChallengeClock.resumeFrom` restores the death-time clock at 1x and
ramps back to the stage over 90 frames (a `step` field, in the snapshot, replaces the fixed
ramp until the stage is reached). Previously Sonic was revived at full speed on the frame
CONTINUE was pressed, at the full death-time pace.
