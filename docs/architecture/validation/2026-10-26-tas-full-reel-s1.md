# S1 full TAS test reel adapters and repair assessment — 2026-10-26

The S1 slice owns all 84 inventory invocations in the 14 classes whose names
contain `TasS1`, based on delivered `94b2a5bf1daeaaad550ff7b8091a8361d6a9094e`.
The parent owns shared recorder/tooling, scope registration, final assembly,
integration and push. This slice changes test observation only. Production
physics, assertions, fixture entry state, inputs and update counts are retained.

The complete per-invocation dispositions, source anchors and three unsupported
child assessments are in the task's external `full/s1/dispositions.json` and
`full/s1/supportability.json`, beneath
the external `tas-test-reel-2026-10-26` task directory.
These local files and ROM-derived frames are not repository assets.

## Coverage and ownership

| Fixtures | Invocations | Presentation |
| --- | ---: | --- |
| Movement | 21 | Existing native headless-world adapter; fixture bytes unchanged |
| Capsule, object, terrain, water, platform, slope | 21 | Same loaded S1 world at each original tick |
| Pause/PLC | 2 | Same world with actual Start edge, mode and existing iteration index |
| Emerald and special-stage contacts | 17 | Same production special-stage manager and ROM art |
| Damage, finish, push-block, continuous zip | 23 | Explicit non-drawable cards, one per actual JUnit invocation |

The 23 non-drawable cases remain real tests. Damage creates bare Sonic models
and callback-boundary spike contacts without ROM art or a registered render
owner. Finish uses a testable player, standalone signpost and stub services.
Push-block uses real contact logic with null art/renderer dependencies and
mocked wall/floor answers. Continuous zip supplies collision height data through
a mocked level, without palette, patterns, drawable chunks or object art.
Inventing a native level or wall texture would change their visual provenance.
The parent accepted these exact exclusions and owns their labelled cards.

## Adapter behavior

`S1ReelFrames` delegates each original headless step once, then invokes the shared
production renderer. Idle loops retain exactly their original count. Pause
hooks follow the existing production pause calls without advancing simulation;
they retain mode and Start input. The direct PLC wrapper's existing local loop
index identifies its samples because it does not advance the fixture runner.

`S1SpecialStageReel` opens configured EGL before the existing manager initializes,
so the manager's own loaders upload its original ROM assets. It calls `update()`
exactly once per original call, draws that same manager and flushes screen-space
commands. Before/after JSON snapshots include every field in its production
rewind snapshot, including layout, palette, input, camera and animation state.
Any drawing mutation fails the invocation. Held buttons use the normal reel
input encoding; native held/pressed words and the manager comparison state are
also retained in the input diagnostic text. Audio uses `NullAudioBackend`.
Manager/graphics cleanup precede owned context destruction.

The emerald test's existing `advanceToEntryPresentation()` convenience loop
contains real updates. Recording observes that loop with the same private hold
field and production maximum-update bound; normal tests call the original
helper directly. Initial blank manager frames remain blank. The fixture does
not own a prior gameplay scene to put underneath its initial whiteout phase.
Contact tests initialize their controlled layout in `@BeforeEach`; the global
recorder begins after that setup, so those setup updates remain outside the
invocation movie. No extra settling or display ticks are added.

## Qualification and rejected evidence

All captures use `tools/test_reel/test_reel.py capture`, original absolute ROMs,
Java 25, Lua 5.4, unset display variables and explicit `surfaceless-egl`. The tool
records exact queued Maven commands, source fingerprints, selected IDs, ROM
identities, JUnit outcomes, frame images and state. Test execution never bypasses
`maven_queue.py`. These are focused checks, not a smoke or release-suite claim.

| External capture directory | Selection | JUnit outcome | Frames | Disposition |
| --- | ---: | --- | ---: | --- |
| `capture-headless-001` | 21 | 21 pass, zero failures/errors/skips | 1,664 | Rejected visually: object atlas missing |
| `capture-special-pause-001` | 19 | 19 pass, zero failures/errors/skips | 470 | Special-stage 17 invocations / 438 frames qualified; pause superseded |
| `capture-nondrawable-001` | 23 | 23 pass, zero failures/errors/skips | 0 | Actual outcomes for the 23 card invocations |
| `capture-native-fixed-001` | 44 | 44 pass, zero failures/errors/skips | 1,818 | All loaded-world cases qualified, including unchanged movement |

The first capture was run over `94b2a5bf` plus owned hooks. The next two were run
over shared recorder API commit `4ffa30ed51aa0c3ba93d33073682bebf57989399` plus
owned hooks. Their `run.json` records contain actual per-file hashes; the
inventory's older fixture hashes are not substituted for those hashes. Final
assembly must regenerate its target inventory against committed hooks.

Image inspection caught a real omission despite passing assertions: SLZ3 showed
terrain but no capsule or animals. The shared adapter created a new GL atlas and
uploaded level patterns only. Existing headless object renderers retained their
allocated bases; `PatternSpriteRenderer.ensurePatternsCached()` returned at the
same base rather than uploading into the new atlas. This is a presentation
qualification failure, not a non-drawable fixture and not a gameplay defect.
The missing art was escalated to the parent for the shared recorder owner.
The rejected capture must not supply final loaded-world footage.

The parent corrected the shared test-side atlas bridge in
`7e1e53607fcda7849143c030a0c15987dee0e8ea`. It transfers existing cached pixel
versions, tile IDs, aliases and range registrations across graphics initialization,
without reloading an art owner or resetting animation. The corrected 44-case
capture ran over that commit plus the frozen owned hooks. Images now show the
actual capsule, animals, monitors, rings and HUD, in addition to terrain and
the original player. All 44 invocations passed with zero skips. The 1,696
recorded frames shared with the earlier 23 loaded-world invocations retain
identical gameplay/input observations; only the corrected direct pause-wrapper
input index is excluded from that metadata comparison. This compares the
recorder's listed observations, not every possible world field. Capsule animal
counts and exact RNG assertions also passed unchanged.

Final selection is 44 corrected native-world invocations, the 17 qualified
special-stage invocations, and the 23 non-drawable outcomes: all 84 passed, with
2,256 real image frames plus the parent's 23 one-frame cards. Earlier pause
records in `capture-special-pause-001` are superseded by the corrected native
capture. That does not require repeating the unchanged special-stage adapter.
The exact commands and fixture hashes are retained in each run's `run.json`.
The bounded capture selectors were:

```text
TestTasS1(Object|Terrain|Water|Platform|Slope|Capsule)
TestTasS1(PausePlc|Emerald|SpecialStageContacts)
TestTasS1(Damage|FinishVerdicts|PushBlock|ContinuousZip)
TestTasS1(Movement|Object|Terrain|Water|Platform|Slope|Capsule|PausePlc)
```

The final native-world Maven execution took 42.725 seconds after 30 seconds of
queue waiting. Earlier runs measured 1:31, 33.430 and 30.178 Maven seconds,
respectively, with 412, 1 and 61 seconds of queue waiting. These intervals are
per job, overlap other workers and do not sum to delivery elapsed time. No broad
engine suite was launched for these observational hooks.

Contact sheets sample first/middle/last images of every captured invocation.
Special-stage pictures show the exact controlled cells or original emerald
maze, Sonic and the native art; the full rewind-snapshot guard passed. Paused
state rows retain frozen clocks and genuine Start edges. Assertion-call source
comparison against delivered `94b2a5bf` found no changed assertions.

## Unsupported child repair assessments

These are source-based implementation assessments, not gameplay fixes or a
promise of full TAS route parity. Complexity describes code/system scope.
Publication IDs and exact final-report child identities are retained in the
external JSON, which was reconciled against both the S1 verdict source and
final report.

| Child ID | Remediation group | Supportability | Complexity |
| --- | --- | --- | --- |
| `S1_LEVEL_WRAP:1` | `unsigned-x-underflow` | Bounded parity repair | LOW |
| `S1_SLOPE_GLITCH:1` | `offscreen-solid-dispatch` | Bounded parity repair | MEDIUM |
| `S1_CAPSULE_RNG:2` | `pause-vint` | Bounded parity repair | MEDIUM |

Unsigned underflow is a fixed-width comparison omission in
`PlayableSpriteMovement.doLevelBoundary`, not a missing transport architecture.
Reproduce the native predicted unsigned word, fractional carry, lock extension
and comparison strictness; source-check shared-game behavior before generalizing.
The existing underflow and non-underflow controls anchor acceptance, supplemented
by word/equality/fraction edges and restore-forward checks.

The slope trigger needs FBlock's outer skip of the entire SolidObject call.
`ObjectExecutionController` currently runs the automatic checkpoint and
`ObjectSolidContactController` clears a rider outside its bounds. A semantic
checkpoint-admission contract must retain the native player/object standing
latch and support identity during that skipped call, then resume normal release
on visible reentry. Merely changing `isSolidFor` can invoke cleanup and is not an
adequate substitute. Rewind, deletion and existing cross-game gate contracts
must preserve their owners. This is shipped stale status, not arbitrary memory
corruption or a requirement for RAM emulation.

Pause currently services VBlank-only art while the normal object update owns
the VInt counter increment. Native PauseGame still reaches unconditional
VBlank_Exit, and Pri_Animals reads that phase modulo eight after resume. Increment
once per admitted paused VBlank without advancing gameplay/RNG or double-counting
existing transition/fade/trace counter paths. Existing counter snapshots provide
the state owner; a new once-only marker, if needed, must have rewind semantics.
Retain PLC drainage, frozen gameplay, modulo-eight capsule controls and
restore-forward acceptance across pause/resume. A global clock migration has
larger scope and is not presumed necessary for the named repair.
