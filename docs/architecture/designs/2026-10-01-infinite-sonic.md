# Infinite Sonic procedural challenge

Task: create a separate Infinite Sonic mod on a new branch, in the main checkout
without worktrees, then install it for the local IntelliJ engine launch.
Base: `67c850fc5132156acede8687ca169ada074f795f` (`develop`).
Branch: `feature/ai-infinite-sonic`. The user initially selected terrain-only scope, then requested terrain-aware badniks
and explicitly kept the zero-ring challenge. Terrain prototype commit: `ff18f7ffd4`.

## Implementation

The independent source project is [examples/infinite-sonic](../../../examples/infinite-sonic/README.md).
Its owner-scoped registration contributes a Sonic 1 patch and a namespaced invisible
controller. Only solo Sonic activates the terrain patch, and only GHZ1's registry
level identifier is replaced. Other acts retain their stock events and layouts.
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
48px right-hand margin; the camera then follows his position. It follows world
recycling with Sonic. The initial velocity-matching policy at `841fb3d98b` consumed
every burst of downhill speed immediately but still took away ground on slowdowns,
preventing the player from rebuilding a lead. Position-based following removes
that ratchet without changing Sonic’s physics or the whole-game pacing contract. A running start supplies the opening reaction buffer. If Sonic's right edge
is left of the camera, or another lethal event kills him, the controller ends the
run and clears remaining lives to enter the stock game-over flow. Rings and hit
invulnerability cannot prevent scrolling failure. Scoring earns one point per
minimum-scroll pixel, retaining fractional credit: 270 points per real second at
1× and 405 at 1.5×. Death freezes challenge scoring, progression and scrolling.

The mod suppresses the stock HUD and draws score, speed/countdown, rings and
game-over text using code-drawn glyphs through the CPU presentation primitive path.
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
