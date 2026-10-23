# Stageplay live-world demonstrations

Date: 2026-10-21. Owner: root delivery lead; implementation: GPT-6.1-Sol.
Foundation: engine commit `508915e67d6646b03f27a5e41ff5124b2d926799`.
Destination: the engine main workspace's current `develop` branch; do not switch it.

## Outcome

Stageplay directs multiple native players through a living ROM-backed level:
moving platforms carry them, rings can be collected, hazards cause real hurt and
ring loss, and Tails/Knuckles use their native airborne abilities. A following
camera makes a moving ensemble readable. Both editors and the offline authoring
tool consume the same portable scene contract.

Deliver a small set of reproducible demonstrations plus one cohesive 60–90 second
cutscene, provisionally **One More Take**: the cast attempts a platform relay,
recovers from a ring-spilling mishap, uses flight/gliding to regroup, and runs
together into the closing shot. Dialogue identifies each speaker and stays clear
of the action. Do not stretch the duration with idle actors. Use zoom 1 throughout.

## Why the next layer is necessary

The foundation has native Puppet physics and manual Poser animation, but each
Puppet owns a private static-terrain context. `StageplayRenderer` draws ROM terrain
and character mappings; it does not run/render placed level objects or rings.
Adding isolated animation switches cannot demonstrate object interaction.

Introduce a session-owned live-level path for a shot, reusing production owners:
`GameplayModeContext`, `GameplaySessionFactory`, `GameplayTeamBootstrap`,
`LevelFrameStep`, `SpriteManager`, `ObjectManager`, `RingManager`, and the existing
rewind registry. Inspect `ControlledFrameRuntime` and `HeadlessGameBoot` for the
existing host/input/boot seams. Do not copy their update order into Stageplay or
move logic into Engine. Avoid replacing the user's active world to preview a scene.
If existing managers cannot safely coexist, extract an explicit scoped owner or
use an isolated offline process for that host path; do not silently mutate live
gameplay state or falsely advertise an in-process detached session.

## Contract and behaviour decisions

1. **World policy.** Add an explicit portable shot/world choice for live level
   objects and interactions. Existing documents preserve their established
   terrain-only behaviour; fresh live demonstrations opt in. Keep Puppet and Poser
   as actor modes. A Poser remains an authored presentation actor unless an explicit
   future interaction policy says otherwise. Preserve older file migration.
2. **Shared world.** All Puppets in a shot share one object/ring population, native
   clocks, collision owners, RNG, and participant registry. Objects tick once per
   world frame, not once per actor. Each performer receives its own scripted input;
   default sidekick AI must not override it. Use native participant rules and stable
   actor identities. Capability validation must reject unsupported combinations.
3. **Movement and routes.** Keep destination/urgency as input intent. Add authored
   waypoints and bounded wait/condition support sufficient for boarding a moving
   platform, riding, and jumping off. Allow targets anchored to a verified placed
   object plus an offset. Resolve the placement by stable level-data identity, not
   an ephemeral object pointer. Missing, despawned, blocked, and timed-out targets
   have explicit diagnostic outcomes. This iteration does not promise a universal
   pathfinder: scripted route decisions are fine; teleporting to make them pass is
   not. Runtime positions, velocities, floor contact and platforms remain native.
4. **Abilities and states.** Expose semantic intent for jump, flight to a target,
   hover for a bounded duration, glide toward a target, and release/land. Implement
   these through native button edges/holds and existing movement controllers.
   Honour native exhaustion, gravity and landing. S2 Tails must not gain S3K flight
   by an animation override. Expose supported idle/look/crouch/roll intents where
   ordinary native input already supplies them. Report actual animation and ability
   state. Poser retains direct animation/frame selection. Hurt is real gameplay
   state with native knockback/recovery; demonstrate it through a placed hazard,
   with an explicit gameplay-event command only if it invokes the existing damage
   owner and has a justified authoring use.
5. **Rings and objects.** Read placements, art, mappings and ring layout through the
   ROM pipeline. Render their evolving native states, including foreground priority,
   animation, collection sparkle and scattered rings. Collection is collision-based,
   once per ring, credited according to native participant semantics. Show counters
   and event telemetry without synthesizing a pickup by editing the counter.
6. **Director camera.** Add actor-follow and group framing at fixed scale, with
   bounded dead zone, look-ahead, offsets, smoothing and target handoff. Resolve from
   actual simulated positions, then layer authored pan/earthquake effects. Keep
   gameplay activation/focus semantics explicit: a cinematic offset must not cause
   platforms under participating actors to stop ticking or disappear unexpectedly.
   Bounds, handoff and seek must be deterministic; no wall-clock interpolation.
7. **Seek and lifetime.** Snapshot the complete world and director state using the
   existing rewind owners. Repeated sampling of frame F cannot tick again. Backward
   seek + forward replay must reproduce actors, platform phase/contact, rings,
   damage state, camera and clocks. Shot/act/game boundaries create clean ownership
   and clear history where required; close/error paths release resources. Preview
   seeking suppresses one-shot audio; forward playback/capture emits it once.
8. **Authoring.** Extend the screenplay compiler, validation, tool help and both
   editors' shared workspace contract. Add a machine-readable level inspection
   operation for placed-object identity/type/position and rings, plus character
   capability/animation discovery, so agents can author routes without menus.
   Extend simulate JSONL with object contact/target status, rings/deltas, hurt,
   ability/native animation state, and resolved camera. Validation rejects invalid
   actors/targets/abilities before capture. Document the input schema with examples.

## Implementation slices and ownership

### A — Shared world and portable contract (Sol)

Inspect the listed owners and choose the smallest reusable production seam. Add
live-shot configuration, per-actor input ownership and world lifecycle. Update
document migration, store/compiler validation and candidate API signature pins
together. Preserve existing constructor/behaviour compatibility where practical.
Demonstrate two actors sharing one placed moving object and ring population before
building the cinematic. This is the highest-risk dependency for all other slices.

### B — Directing controls and state (Sol)

Implement routes/object targets, native ability intents, actual state observations,
fixed-scale camera following, and deterministic seek. Extend the shared in-engine
workspace/HUD authoring controls. Keep aliases and semantic rules in the correct
owners; do not add game/zone-name exceptions in shared runtime code.

### C — Demonstrations and offline authoring (Sol)

Add three concise source demonstrations and a coherent combined screenplay:

- **Platform relay:** board, ride and leave at least one genuinely moving placed
  object; navigate at least two consecutive obstacle decisions with multiple
  actors. Known investigation anchors: S2 CNZ1 Big Block lift regression near
  centre (3311,1305), and S3K DEZ2 horizontal floating platform placement record 179
  near (0x1540,0x790), covered by `TestS3kDezFloatingPlatformHeadless`. Discover the
  surrounding route from real level data before selecting the final location.
- **Air traffic:** S3K Tails takes off, flies, visibly hovers, and lands; Knuckles
  jumps, glides, releases and lands. Include idle/run/jump/landing transitions and
  actual accessory animation. Show exhaustion/failure honestly if it occurs.
- **Ring run:** the moving cast collects placed rings, one actor takes a real hit
  and recovers, and the camera follows a lead actor then hands off or frames the
  group. Use a verified native hazard route, not a fake hurt pose.

Use inspect/simulate/capture commands to refine the sources. Store durable outputs
outside the repository in a new `stageplay-delivery/revision-5` task directory.
Commit reusable source scenes, tool changes and compact documentation; no ROMs,
raw logs or generated movies. Update mirrored stageplay-authoring and relevant
promo/capture skills with live-world instructions and evidence requirements.

### D — External editor, promo and integration (root lead)

Root owns OpenGGF-Editor and OpenGGF-Media, final combined smoke/guards, visual
review, media provenance/catalogues, integration, pushing destination branches,
and cleanup. Sol must supply stable contract changes and exact CLI recipes so the
editor exposes the same controls and the rendered promo proves the stated events.
Existing revision-four movie remains the Puppet/Poser foundation preview.

## Acceptance and verification

- Portable round-trip/migration and strict compiler tests for all added fields;
  actionable rejection of unknown actor/object/ability and unbounded waits.
- ROM-backed shared-world tests: platform moves once per tick with two puppets;
  boarding/riding/disembarking follows native contact and displacement; collected
  rings disappear once; hazard produces hurt, knockback, ring loss and recovery.
- Native ability tests: real Tails flight/hover limits and Knuckles glide/landing;
  unsupported character capability rejected rather than silently posed.
- Camera tests from resolved actor positions: dead zone, bounds, handoff, shake
  composition and deterministic seek, with fixed zoom.
- Rewind/replay checks crossing boarding, pickup, hurt, airborne ability and scene
  transition. Compare actual states/events, not only image existence or counts.
- Fresh-process offline simulate and surfaceless-EGL capture; no engine menus or
  ambient desktop focus. Both editors preview the same sampled world/camera state.
- Watch the complete final movie and inspect contacts at platform boarding/leaving,
  pickups, impact and ability transitions. Actual telemetry must corroborate each
  advertised feature. Inspect dialogue occlusion, tails, object priority and audio.
- Root runs change-based smoke from the pinned destination base after focused
  regressions. Timing/physics/shared runtime changes justify broad selection; no
  release-only full suite. Preserve exact failure attribution and inspect skips.

## Work coordination and current checkpoint

The foundation tree `.worktrees/ai-stageplay-dialogue` is frozen during category
run `20261021T201324Z-6b2c93b4` (session 63910), testing commit `508915e67`.
Do not edit it or duplicate that run. Sol receives a separate T3 worktree based on
the parent's local main commit, then fast-forwards its own branch to foundation
commit `508915e67` before implementing. Root continues that existing verification
and foundation delivery independently. Sol runs focused queued checks in its own
tree only; root owns combined validation and all integration pushes.

At handoff record current SHA, changed files, focused commands/results/skips,
live job handles, source/demo paths, and any remaining limitations. Follow AGENTS
documentation/trailer/mirror rules. Do not silently reduce the requested outcomes;
report an architectural blocker with evidence and the smallest next step.

### Implementation checkpoint — isolated Sol tree

- Tree `$STAGEPLAY_WORKTREE` (T3 checkout `t3-1cc0587d`), branch
  `feature/ai-stageplay-live-world`, fast-forwarded from `79e5ebea2` to
  `508915e67` after verifying ancestry; hooks installed. No main/frozen-tree edits.
- SessionManager's open path destroys the current gameplay/editor mode. Therefore
  live simulation uses an explicitly owned offline process/session. Default
  StageplayResources creates a persistent isolated preview worker; offline
  simulate/capture supply their own disposable owner. An in-process detached
  live-session claim would be false. The shared editor consumes the worker
  protocol while preserving its active session.
- Portable v5 adds opt-in LIVE set policy, stable placement targets, bounded waits,
  native ability intents and fixed-scale actor/group camera following. Legacy v1–4
  migrate to terrain-only; old constructors remain available.
- Runtime draft delegates once to LevelFrameStep per story frame, publishes separate
  controller bytes through SpriteManager, and snapshots the existing rewind registry
  together with director/control state. No object tween, ability activation, hurt
  pose, velocity write or pickup counter assignment is used for runtime directing.
- Initial focused job: session 7520, exact selectors
  `TestStageplayLiveWorldRom,TestStageplayDocument,TestStageplayStore,TestStageplayScriptCompiler`,
  Java 25, Lua 5.4, supplied absolute S3K ROM. Pending; no broad run submitted.

### Native foundation evidence

Focused command from this tree completed: `maven_queue.py -Dmse=off
-Dtest=TestStageplayLiveWorldRom,TestStageplayDocument,TestStageplayStore,TestStageplayScriptCompiler
-Ds3k.rom.path=<absolute supplied locked-on ROM> test -B`: 28 tests, zero
failures/errors/skips. New ROM tests cover two actors sharing placement 179,
matching platform/player displacement, repeated sample no-op, backward seek/replay,
and rejecting a world replacement before mutation. This is focused validation,
not combined smoke. Current work remains uncommitted on foundation `508915e67`.

Fresh-process platform simulation (240 frames) corroborated: both actors boarded
DEZ2 placement 179 on frames 15/16, its X moved 5461 → 5495 from frames 35 → 100,
and each actor moved by the same 34 pixels. Three placed rings became collected
once; native shared participant counters each report the shared total 3. No ring
counter assignment is present. The reusable source is now `live-platform-relay.screenplay.json`; final state
and capture outputs are listed in the delivery checkpoint below.

Initial render failed at `AudioManager.drainCaptureFrame`: opening the live level
had replaced the audio producer after the capture lease was attached. Capture now
resolves the initial live world before opening its lease and renews the lease at
shot boundaries. This needs a new render; the failed movie is not evidence.

### Directing/render checkpoint

- Refined air steering uses native `2 * runAccel` countersteering while airborne,
  rather than ground deceleration. Both actors now transfer 179 → 166 → 179,
  boarding at 171/176 then 368/374; no timeout/hurt/death in the 600-frame probe.
- S3K Tails flight and Knuckles glide/landing are native. The first Tails pass
  lands at 428; Knuckles releases glide at 180 and is grounded at 194. Hover
  uses native flaps and visibly oscillates within a small altitude band, consuming
  its native timer. No ability controller is activated directly.
- The ring probe collects three original placements at 14–15, takes placed
  hazard contact at 29, loses the shared three rings at 30 (three native scattered
  ring objects), and recovers at 119. Counters are native shared totals, not
  additive per-actor pickup credits.
- Cinematic rendering needs both a native priority view and graphics camera/fade
  binding. A populated SAT and nonblank scenery initially hid the missing-body
  error. After binding, surfaceless readback shows both characters, their moving
  platforms, placed rings and HUD at the resolved director coordinates. New pixel
  regression compares shown/hidden native bodies around the sampled centre.
- Focused second job (session 24484) ran 77 tests, 0 skips: five failures and one
  error. Owned corrections: allow the live host's data dependency; preserve old
  action-form titles; explicitly install logic graphics after a GL fixture closes.
  Three architecture-rule failures concern unchanged foundation source:
  `session_contexts_are_only_constructed_by_session_composition_roots` (PlayablePuppet),
  `shared_code_does_not_construct_concrete_sonic_provider_classes` and
  `shared_layers_do_not_depend_on_game_specific_packages` (HubMusicController).
  No baseline test was run in the frozen tree; root must reconcile these against
  its foundation/combined guards. They are not reported as attributed passes.

### Source demonstration checkpoint

All four fresh-process source simulations completed with no timed-out waits or
actor deaths: `live-platform-relay` (480 frames), `live-air-traffic` (540),
`live-ring-run` (240), and `one-more-take` (3600, exactly 60 seconds). Sources and
compiled portable scenes are under `examples/stageplay-demo/src/main/resources/scenes/`;
actual state JSONL is in external `revision-5/`. Combined story has five shots:
relay, ring-spill mishap, airborne regrouping, a clean retry, and all-three closing
run. Root still owns complete final promo review and external editor integration.
New focused demo regressions assert platform identities across both transfers,
flight/glide/timer/accessories/landing, once-only placement collection and native
spilled rings, and seek/replay through these events and shot/roster changes.

Live preview also carries synthesized native SFX PCM from its no-device worker.
Editor forward playback admits each packet once through existing SceneAudioSamples,
retaining at most two short packet scopes beside authored voice/music ownership.
Seeking and drawing do not admit PCM; stop/release closes owned voices. Camera
pan drafts cross the same worker protocol without ticking a repeated world frame.
Preview tests cover parent-world preservation, draft camera, worker cleanup and
actual nonzero native ring SFX synthesis, rather than guessed event-to-sound mapping.

Focused third job (session 69781) completed 138 tests, zero skips/errors. All new
native route, world/camera/seek, strict contract, rendering, workspace and mandatory
S3K checks passed except the preview test's final process-discovery assertion.
Pixels, native state, camera draft and synthesized SFX assertions passed; Linux
ProcessHandle command-line discovery did not identify the worker. The test now
uses the preview owner's exact PID, then checks that exact handle exits on close.
The same three foundation architecture-rule failures remain, with identical owning
source/methods; no unrelated foundation code was changed to hide them. The targeted
architecture ownership rule for this phase now passes.

Capability boundary: LIVE presentation currently uses native height 224 (width
320–1920); taller/shorter authored views fail validation, and export scale enlarges
the resulting picture. Terrain-only documents retain their existing dimensions.
This avoids advertising a native camera height that the gameplay pipeline does not
use. Target resolution queries the canonical placement owner, excluding dynamic
children that copied a parent spawn. Missing layout identities fail preflight;
NOT_ACTIVE and DESPAWNED are distinct runtime diagnostics.

### Final frame-review correction

The first complete capture set decoded successfully but air-traffic/closing cast
was outside the picture. Simulation and a platform-only pixel check had missed
this: `Level.getMaxY()` is already a ROM camera-origin limit, as used directly by
`Camera.updatePosition`; subtracting 224 again clamped AIZ at 688 instead of 912.
The director now clamps to native camera origins (only additional viewport width
adjusts the horizontal maximum). Airborne actor centre-in-picture and actual
shown/hidden native-body pixel checks cover the corrected path. The rejected
capture set is regenerated; successful decode alone is not visual acceptance.

Complete-cutscene frame review also exposed stale per-world GPU state on the
return from the Tails/Knuckles AIZ shot to Sonic/Tails DEZ: black terrain and the
previous roster's colours, despite valid world state. New shots retire the old module's scoped state. The first attempt used
`GraphicsManager.resetState()`; its kill evidence was black terrain in every shot,
because it drops GL terrain renderers without recreating them. A body-only/equal-
blank-image regression still passed, so a visible-terrain positive control was
added. The final boundary matches Engine's production game entry: reset
`RenderContext` sidekick/donor banks and clear palette textures, retaining initialized
atlas/tile renderers. A boundary pixel regression compares equivalent native views
before and after the act/roster round trip. Standalone scene captures alone had not
exercised this return path.

### Focused delivery verification

The pre-final palette-boundary source in this isolated tree passed the exact command below (Java
25, Lua 5.4; absolute original locked-on ROM; surfaceless EGL; desktop display
variables removed; `STAGEPLAY_ROM` names the supplied original locked-on file): **33 tests, zero failures/errors/skips**. Its body-only render check did not detect
missing terrain; the corrected boundary and positive control require the narrow
repeat recorded below. Maven execution was
32.761 seconds after 1126 seconds waiting for admission. That wait included
capacity/worktree contention while this task packaged fixes; it is not attributed
solely to another task. No broad/category-all/full-suite run was submitted.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestStageplayLiveWorldRom,TestStageplayLiveWorldRendering,TestStageplayLivePreviewRom,TestStageplayLiveDemosRom,TestStageplayLiveContract,TestStageplayAudioLifecycle,TestStageplayAudioSamples,TestStageplayPuppets' \
  "-Ds3k.rom.path=$STAGEPLAY_ROM" test -B
```

Separate unchanged-path input regressions passed **21 tests, zero failures/errors/
skips**, Maven execution 29.102 seconds:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestSpriteManagerGameplayInputFilter,TestSpriteManagerUpdateOrder,TestSpriteManagerCollisionOrder,TestSpriteManagerMainTailsTailsDispatch' test -B
```

Earlier bounded selections also passed the two shared workspace classes (18
tests), CLI (10), document/store (15), compiler (11), candidate API surface (11),
and required S3K load/bootstrap/decoding/AIZ/DEZ checks (63), all with zero skips.
Those were parts of the recorded focused jobs, not a separate combined suite.
Later changes are confined to live-host rendering/audio, its tests and guidance;
root owns combined smoke and guard attribution. The three architecture findings
listed above remain unresolved and baseline attribution is not asserted.

Native PCM packet admission now belongs to StageplayAudio for both editor and
registered cutscene playback. Existing sample leases bound retained packets;
repeat/backward frames do not admit another packet, seek/stop close tails. LIVE
registered scenes reject active-player binding before mutation: their players
belong to the isolated world, while the legacy terrain-only binding is preserved.

Root checkpoint (reported by delivery lead, not rerun in this tree): foundation
`508915e67` smoke finished with 37,338 ordinary tests (2 failures, 3 errors) and
718 guards (10 failures). Root owns fixes for the reflective GroundSensor
fixture, bootstrap-access audit, PlayablePuppet composition seam, large-source
extractions and SceneRomLibrary temporary files, plus EGL/JAVA_TOOL_OPTIONS
attribution. Do not duplicate those repairs. Root will supply a compact commit
for integration; five unrelated baseline guard findings are reported remaining.

The corrected palette-context boundary passed the same exact native selection
again: **33 tests, zero failures/errors/skips**, 86 seconds Maven execution after
66 seconds admission wait. Rendering checks now require coloured terrain as well
as native bodies and compare the complete native picture before/after an act and
roster round trip. No additional source fix remains from that run. S3K ROM identity
was independently verified: CRC32 `63522553`, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`.

### Rendered handoff and contract consumers

Final source/package checks: `maven_queue.py -Dmse=off -DskipTests package -q`
completed successfully; reflection snapshot matches the normalized candidate
`0.7` pin exactly. All four `.stageplay` files match fresh screenplay compilation
byte for byte. `level --game s3k --rom <verified absolute ROM> --zone 11 --act 1`
returns 198 placed rings and confirms placements 179 `(74,1)` and 166 `(74,4)`.
Tails/Knuckles catalogues expose the native flight/hover/glide intents and actual
ROM animation mappings. Skill mirrors, source JSON, guide/example local links
and `git diff --check` passed.

Current durable outputs (all in
`$STAGEPLAY_OUT` (the external `stageplay-delivery/revision-5` task directory)):

| Prefix | Movie / state rows | Evidence |
| --- | --- | --- |
| `live-platform-relay` | `-review.mkv`, 480 frames / `-final.jsonl`, 480 rows | Board 179 at 15/16; both carried 34 px from 35→100; transfer to 166 at 171/176, return to 179 at 368/374. |
| `live-air-traffic` | 540 frames / rows | Native ability onset 94 (Tails), 114 (Knuckles); landing 427/193, hover Y 917.34–941.00; native tail accessory/mapping state. |
| `live-ring-run` | 240 frames / rows | Three original rings collected at 14–15; real hurt at 29, shared ring loss at 30, three LostRingObjectInstance objects at 31, recovery at 119. |
| `one-more-take` | 3600 frames / rows | 60 s, five shots; clean act/roster return and final three-character run. |

Every movie is 640×448 (native 320×224 at export scale two), FFV1 at 60 fps plus
48 kHz stereo FLAC. `ffprobe -count_frames` confirmed exact frame counts and
8/9/4/60-second durations; `ffmpeg -v error -xerror -i MOVIE -f null -` decoded
each complete film successfully. State rows are contiguous and all four full
simulations have no actor deaths or timed-out commands. `-qa.png` contact sheets
show six event frames per short demo and every two seconds of the cutscene.
All sheets were inspected, including native bodies, terrain, palette/roster return,
dialogue placement and closing motion. Root owns full moving/audio review and the
final edited promo/catalogue/archive; these are reproducible review captures.
Rejected probe sources/movies and earlier invalid capture sets were discarded.

Shared consumers: StageplayDocument/Store v5, screenplay compiler v3, timeline
native observations, StageplayTool inspect/level/catalog/simulate, offline capture,
shared StageplayWorkspace/EditorSession preview, and registered StageplayPlayback
all use the same portable fields. Existing constructor/legacy-file migration and
Puppet/Poser paths remain. External Editor/Media integration is root slice D.

Bounds remain explicit: LIVE native height 224, width 320–1920, fixed zoom one;
same-donor native Puppets and native roster limits; preview workers require
configured original whole-image ROM paths; cast stays in the native activation
window. Authored waypoints/waits are bounded route decisions, not general
pathfinding. No main branch integration, task push, external repository edit or
root revision-four source change was performed here.

All short-demo event frame numbers above are zero-based scene frames.

### Local commit / stopping checkpoint

Implementation slices A–C are committed locally as
`1e5420f10123a20eb4ebb02b97791957eca7ec57` on
`feature/ai-stageplay-live-world`, based on `508915e67`. That commit contains the
engine/tool/shared-workspace additions, focused regressions, mirrored skills,
four reusable source/compiled scenes and the evidence/decision record above.
No live validation/capture/preview job remains. Consumed task-owned target logs,
inspection JSON and one-off probes were removed after their results were recorded.
The external directory retains only the twelve current movie/state/review artifacts.

The descriptor remains unchanged: ordinary mutable candidate pin regeneration
must not edit release policy. Current API is still unpublished `0.7.0`; runtime
description and normalized `0.7` inventory cover the actual added surface. Both
commit co-author trailers were validated with `git interpret-trailers --parse`;
repository hooks ran without bypass. Committed recipes use portable variables
and placeholders to satisfy the repository's machine-local-path policy. The
delivery lead receives exact local paths in the handoff.

Root's announced foundation repair commit has not yet been supplied/merged in
this tree. Root continues slice D: that reconciliation, combined validation and
guard attribution, external Editor/Media work, full moving/audio promo review,
integration, pushes and eventual T3 worktree/branch cleanup. The earlier observed
architecture findings are not claimed fixed or baseline-attributed by this task.

### Integration-support slice — foundation repair

Received and reviewed `556db2cecf843c5382ea8d7fa6b40909a6e8d361`, descending
from `508915e67`. Its factory composition, geometry cache and boss-state helper
extractions merged without source conflicts; the geometry-width calculation reads
only level/block data, so its earlier argument evaluation does not change cached
state semantics. Root's bounded native/foundation followup already covers those
copies. Only two prose conflicts occurred: retain both measurement hazards, and
combine the reviewed rev4 performance description with the LIVE demos section.
Clarify that the bundled legacy scene is v4 while the current compiler emits v5.
All four new source/compiled scenes and schemas remain intact; root's reviewed
location-remote pair is retained byte for byte. No demonstration is invalidated.

Next check: one queued full guard lane on the merged source, Java25/Lua5.4,
JAVA_TOOL_OPTIONS/DISPLAY/WAYLAND_DISPLAY unset. Root owns combined ordinary
smoke and external editor/media; no broad ordinary or duplicate foundation
selection is authorized in this support slice. New guard violations will be
fixed without weakening policy and then checked narrowly.

### Integration-support findings and source freeze

Foundation repair merged as `c391d5c08e881b0313044b4b548c7e2421852399`.
The single full guard invocation on that exact merged state was:

```bash
env -u JAVA_TOOL_OPTIONS -u DISPLAY -u WAYLAND_DISPLAY -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

Observed **718 tests, 7 failures, 0 errors/skips**, Maven execution 5:39.
Five failures match root's reported baseline by identity and owner:

- `TestArchUnitRules.shared_code_does_not_construct_concrete_sonic_provider_classes`
  and `shared_layers_do_not_depend_on_game_specific_packages`: HubMusicController.
- `TestProductionSingletonClosureGuard.productionCodeDoesNotUseForbiddenProcessSingletonsOutsideEngineServices`
  and `productionCodeOnlyUsesRawGetInstanceAtEngineServicesBootstrapBridge`:
  MenuAudioProbeTool's AudioManager singleton.
- `TestNativeImageResourceGuard.everyRuntimeResourceIsReachableFromTheNativeImageConfig`:
  AuthoredLevelDecoder service declaration.

The two new architectural source violations were attributable and corrected:
remove LIVE's redundant bootstrap `GameModuleRegistry.setCurrent` write (the
WorldSession already owns the active module), and finish extracting background
width/cache initialization into `LevelManagerInitializationSupport`. The copied
geometry calculation and log category are unchanged. No policy, exclusion,
budget or approval scope changed. A native lifecycle assertion preserves the
pre-session bootstrap module through LIVE setup/replay/close.

Root's moving review found black terrain at frame zero and gameplay HUD glyphs.
A new first-draw regression reproduced only 1,507 coloured pixels. Texture IDs
were stable before/after drawing: missing texture initialization was rejected.
The owning S3K animator instead starts the real level-load palette fade with
lines 1–3 black while player line zero remains visible. Cinematic rendering now
scopes an unfaded display through draw/flush and restores the native requested
fade, displayed generation, pending publication and prior hold. It does not tick
actors, clocks or the fade timer. Authored transitions own the film's reveal.
Cinematic options omit gameplay HUD/debug/art-viewer passes but retain world
sprites, rings and water. Rendering regressions cover first/repeated draw,
unchanged clocks/position/fade publication and a real HUD-pixel positive control:
removing all HUD rows changes ordinary rendering but leaves film pixels identical.
All five native pixel cases passed, including the previous act/roster round trip.

The shared workspace's unkeyed camera now uses resolved LIVE follow framing,
without recursing through previewFrame. Pan/capture/record starts at that view;
Capture/Record explicitly stores authored framing and clears follow at those
keys. The camera form retains follow: displayed position edits adjust offsets,
while an unchanged submit preserves the authored seed and smoothing history.
LIVE controls keep zoom one and omit higher-zoom menu choices. A host regression
returns a follow view far from the authored origin and verifies those semantics.

Each new demonstration shot now has an explicit local-frame-zero ROM_MUSIC cue,
so parent preview and export share directed music while preserving native SFX.
`catalog --game s3k --rom "$STAGEPLAY_ROM" --character sonic --zone 11 --act 1`
verified locked-on SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6` and music IDs
DEZ1=22, DEZ2=23, AIZ1=1. Canonical scenes are regenerated from those screenplays.
No new audio API, public Mod API surface or version/pin change is needed.

Promo-direction, its lessons, gameplay-highlights and Stageplay authoring skills
now distinguish authored UNRESOLVED samples, TERRAIN_ONLY native actor simulation,
LIVE native world telemetry and normal gameplay CSV. All edits are mirrored.
LIVE telemetry substantiates observed native interactions from authored shot
setups; cold continuous completion and ROM parity need separate evidence. Root
owns general stageplay.md, next-line-subsystems and changelog wording; this slice
leaves those files alone.

Focused native/palette/geometry checks in the combined command below passed
**27 tests, 0 failures/errors/skips** (plus ten legacy Puppet workspace tests).
The initial workspace test had a stale-error assertion after a successful closed
form; the menu intentionally retains its last error text when closed. The
assertion now checks successful closure. An earlier attempt stopped at test
compilation because the pixel test reused x/y locals; no tests ran in that attempt.
The final shared-workspace rerun passed **20 tests, 0 failures/errors/skips**.

```bash
env -u JAVA_TOOL_OPTIONS -u DISPLAY -u WAYLAND_DISPLAY -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$STAGEPLAY_ROM" \
  '-Dtest=TestStageplayLiveWorldRendering,TestStageplayLiveWorldRom,TestStageplayLiveDemosRom,TestStageplayLivePreviewRom,TestStageplayDirectingWorkspace,TestStageplayPuppetWorkspace,TestPaletteUploadLatch,TestLevelBoundsMaskGeometry' test -B
# Final workspace check after the resolved-camera correction:
env -u JAVA_TOOL_OPTIONS -u DISPLAY -u WAYLAND_DISPLAY -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestStageplayDirectingWorkspace,TestStageplayPuppetWorkspace' test -B
```

The exact `-Dmse=off -Pguards -Dtest=TestArchitecturalSourceGuard test -B`
rerun passed **72 tests, 0 failures/errors/skips**, Maven execution 30.229 s.
All four canonical scenes were regenerated: only audio cue lists changed
(1/1/1/5 cues); every other canonical field equals the preceding scene.
Syntax, skill mirrors and whitespace checks passed.

Remaining at this checkpoint: local source-freeze commit, then build without
tests and regenerate four review MKVs/QA images. Updated JSONL will retain
identical native samples while reflecting the added authored music cues. Preserve
the former review set under a clearly superseded external subdirectory; leave
root-created MP4s/scripts/layout review intact. Root may merge the frozen commit
and begin combined ordinary smoke independently while this worktree captures.

### Final support handoff — capture completion

Frozen engine/example source is `bd4feec2f17b883766145bf01e7f27ec6d699a51`;
root received it before captures and is integrating/validating independently.
No further engine, test, schema or example-source edits followed that freeze.
The build-only queued `-Dmse=off -DskipTests package -B` passed (44.509 s).

All four revised `-review.mkv`, `-final.jsonl` and `-qa.png` outputs are published
at their original names under `$STAGEPLAY_OUT`. The twelve former artifacts are
retained under `$STAGEPLAY_OUT/superseded-before-bd4feec/`. Root-created viewing
MP4s, scripts, metadata and layout review remain untouched. Root owns refreshing
those views, complete moving/audio review, the edited promo and Media archive.

Every one of the **4,860** regenerated `sample` values is exactly equal to the
corresponding preceding sample, including native world clocks, actors, objects,
rings, events and resolved cameras. The added top-level authored music cues are
the only JSONL differences (1/1/1/5 cues). Thus no native outcome was substituted
or moved by the presentation repairs. All four movies passed complete
`ffmpeg -v error -xerror -i MOVIE -f null -` decoding and exact `ffprobe`
frame/dimension/audio inspection: 640×448, 60 fps, 480/540/240/3600 video frames,
48 kHz stereo FLAC. Half-second decoded PCM windows at each shot's first 0.1 s
were nonzero (RMS 934–2175; peaks 3969–7858). This is synthesis evidence, not a
claim of complete listening review.

All four QA sheets were inspected. First-frame terrain is visible, HUD glyphs
are absent, and actor/terrain/palette return remains intact. The film's black
frame at 960 is its authored FADE_BLACK cover, not an uninitialized level.
Short-sheet frame selections are relay 0/35/100/190/374/479, air
0/95/115/300/360/440, rings 0/15/29/31/119/239. The film sheet uses frames
0..3480 at stride 120 plus 3599. Contact sheets supplement the pixel regressions;
root still owns the complete moving creative review.

No live job remains in this worktree. Its branch remains local and retained for
root; no main branch, external repository or push was touched. This final
checkpoint is documentation only and does not change the frozen tested source.
The five baseline guard identities above remain reported; combined ordinary
smoke/integration/release claims belong to root's completed runs, not this slice.

### Ring-spill vertical-camera review — 2026-10-22

The user spotted the vertical defect in the delivered preview from frozen
`bd4feec2f`; root then reproduced grounded Tails/skid dust floating above absent
terrain at recovery and the final landing (frames 120/239).
This follow-up is isolated in the retained feature worktree at `154cb7fb8`;
root's unchanged combined smoke continues independently. Revision-5 media is
frozen. Root prepared `revision-6` for corrected captures and owns promo/archive.

Hypothesis: LIVE draw changes camera X/Y and sprite projection but foreground
VScroll/mask still consumes ParallaxManager output computed for native focus.
The camera centres and native simulation can both be correct while those two
coordinate spaces disagree. DEZ's background is intentionally stationary:
`SwScrlS3kDez` documents and implements zero background scroll. Do not fit the
screenplay, force physics, retick native scroll handlers or turn that backdrop
into a foreground plane to hide the fault.

A ROM/EGL pixel regression uses the existing ring-run source at two independent
native grounded contacts. It checks decoded foreground art below actual native
feet and the real GPU priority mask against ROM tile/pattern/palette pixels,
with multiple directed vertical offsets. It also checks full camera register,
native scroll/presentation preservation and backward replay after repeated draws.
The exact focused selector is `TestStageplayCinematicCameraRendering`, with
Java25/Lua5.4, absolute original S3K path, explicit surfaceless EGL and launcher/
display environment removed. The first floor oracle incorrectly included an
off-screen performer and assumed visible floor art beneath every grounded actor;
it failed its own positive control and was not renderer proof. The corrected test includes the
former camera-only draw as a negative control. That path demonstrably disagrees
with the decoded world-coordinate priority mask at both contacts. The first
candidate run passed all frame-239 obligations; frame 120 reached/passed the
floor comparison, then exposed over-strict oracles: Sonic was in native
post-hurt blinking, and the ROM foreground is transparent directly beneath
Tails' footprint at frame 120. The oracle now requires decoded art at a native
grounded contact and a visible grounded body in each view, compares every decoded
contact-art pixel, and checks the whole viewport's priority mask. It neither
invents art beyond the ROM's visible ledge nor overrides native blinking.
The final focused rerun passed.

Implementation: an internal LevelSpritePresentation bridge scopes the camera and
a private immutable LevelScrollPresentation. Rebase foreground scalar/active
override Y and packed foreground HScroll words by the director/native delta;
retain shake and deformation. Column scroll values are deltas, not another
absolute origin. The visible foreground and its GPU priority mask consume the
same sample. Restore camera registers/copies and prior draw scroll/render mode
in finally, including callback failure; discard aborted queued drawing.
Published/pending SAT, scroll, CRAM, native parallax owners and gameplay stepping
are untouched. Advanced render policy resolves under the native camera before
projection; retain an owned copy because controller results are double-buffered.

Bound: background registers, line/column deformation and effect phase remain
anchored to the native gameplay camera in every zone. In AIZ a manual pan or
secondary-actor follow reframes terrain and sprites against that native-phase
backdrop. This is intentional for this bounded correction, not arbitrary-view
parallax recomputation; no background handler is reticked or inferred from one
sample. DEZ's deliberately stationary background stays stationary. Script,
physics, native camera following and movement constants are unchanged.

The bounded verification selector adds TestLevelSpritePresentation,
TestLevelRendererBackgroundViewport, TestStageplayLiveWorldRendering,
TestStageplayLiveWorldRom, TestStageplayLiveDemosRom, TestModApiSignatureSurface,
TestModApiPinPolicy and TestArchitecturalSourceGuard. No ordinary broad run or
guard-policy/pin relaxation is requested. Root owns combined smoke attribution.

Source-freeze verification: the eight companion classes passed **122 tests,
0 failures/errors/skips** on the final production patch (the combined invocation
was red only for the contact-oracle assumption subsequently corrected). The
final exact pixel selector passed **2 tests, 0 failures/errors/skips** in 27.850 s
Maven execution after 500 s queue wait. No production source changed between
those checks. API signatures/pins and 72 architectural-source guards passed;
the new bridge is internal and does not change the Mod API descriptor/version.

Reproducible commands, from this worktree with Java25/Lua5.4 and original ROM:

```sh
env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u MAVEN_ARGS -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$STAGEPLAY_ROM" \
  '-Dtest=TestStageplayCinematicCameraRendering,TestLevelSpritePresentation,TestLevelRendererBackgroundViewport,TestStageplayLiveWorldRendering,TestStageplayLiveWorldRom,TestStageplayLiveDemosRom,TestModApiSignatureSurface,TestModApiPinPolicy,TestArchitecturalSourceGuard' test -B
# After the test-only contact oracle refinements, rerun that exact class:
env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u MAVEN_ARGS -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$STAGEPLAY_ROM" \
  '-Dtest=TestStageplayCinematicCameraRendering' test -B
```

Cause/evidence: native/director/foreground Y is 2228/2193/2228 at frame 120,
and 2316/2547/2316 at frame 239. The old camera-only path produces **1914/8689**
wrong priority-mask samples. The corrected path has zero mismatches across
**104280** viewport samples over six views (Y offsets 0/-32/+32 paired with X
offsets 0/-16/+16). Frame 120 Sonic's 33 contact-art pixels all match;
Tails shows 205–206 body pixels but zero decoded art below his footprint.
Both of his native floor sensors report tile 255, distance 0: a native
collision/art distinction, not missing renderer output to fabricate.
Frame 239 Tails has **231/231** floor pixels and **201** body pixels at every
offset. Camera copies/registers, native parallax, pending/published SAT/scroll/
CRAM remain equal after repeated draws and an exception with queued commands.
Ordinary gameplay pictures remain byte-identical, and backward seek/replay
reproduces the same frame and actor/terrain picture.

Remaining at freeze: commit this bounded source/test/plan change locally; notify
root before rendering; package without tests; regenerate four MKVs/JSONL/QA
images under revision-6, compare state with revision-5, decode and inspect them.
Revision-5, root-owned scripts, MP4s, promo and external repositories stay owned
by root. No ordinary broad run, integration or push in this worktree.

### Camera correction — rendered handoff

Frozen runtime/test source is **efa7875f8c60885dad0458a2cfbe77c0cd49a4de**.
The build-only queued `-Dmse=off -DskipTests package -B` passed in 43.689 s;
the packaged `version.properties` reports `app.commit=efa7875f8`,
`app.dirty=false`, built with Java25. Jar SHA-256:
`2c93d2a1bd3a1d19f63ab4b372e9f964399593260582e2aec1af4125b6f396cb`.
No runtime/test/example changes followed the freeze. This checkpoint is prose only.

Root's unchanged revision-6 `capture.py` generated all four takes with this jar,
original S3K ROM and a fresh external subdirectory. Canonical compilation matched
all four retained scenes. After successful completion, the eight MKV/JSONL files
were moved to the requested revision-6 top-level names; identical temporary source
copies were removed. Four newly labelled `-qa.png` sheets complete the twelve
requested outputs. No revision-5 or root-owned input/script/MP4 was overwritten.

Every one of **4860 parsed JSONL records** equals revision-5, including authored
camera/audio cues and all sampled native actors, objects, rings/events and clocks.
Raw bytes differ only in JSON object key ordering; do not mistake map iteration
order for changed native behavior. All four MKVs passed complete
`ffmpeg -v error -xerror -i MOVIE -f null -` decoding and `ffprobe -count_frames`
checks: 640×448, 60 fps, 480/540/240/3600 frames respectively, 48 kHz stereo FLAC.
The QA extraction uses `select` plus nearest-neighbour scale to 320×224 and
`-fps_mode passthrough`, with labelled native frame numbers. Short sheets select
relay 0/35/100/190/374/479, air 0/95/115/300/360/440, rings 0/15/29/31/120/239;
the film selects 0..3480 at stride 120 plus 3599. All four sheets were inspected.
The frame-239 landing now meets its rendered foreground floor; frame-120's
native collision/art distinction above remains visible. Frame 960 is authored
black transition cover. Background anchoring remains as bounded above.

The retained package and shared StageplayLiveWorld draw path supply capture,
worker preview and registered playback; no Editor/Media repository or creator
API surface changed. Root owns rebuilding external consumers, moving review,
promo/MP4 replacement, archive and integrated validation/push. This worktree has
no remaining live job. Temporary consumed diagnostics/QA helper are removed;
branch/worktree and packaged build are retained for root.


### Combined delivery verification — 2026-10-22

The combined source at `1e0f02519117a225b270c8b967ac3dbb9d74f798`
completed run `20261021T225019Z-88c7aa64`: 3,564 candidate classes,
all 72 ordinary batches and the separate guard batch. The tree stayed unchanged
through the run. Preparation took 79.41 seconds. Command:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/run_categories.py \
  --base ca58b4691bbc92bfee66b4b18dfed8cad68beed6 \
  --rom-directory ../.. \
  --max-minutes 180 --run
```

The ROM directory is shown as the equivalent `../..` path from this worktree;
the executed command used that directory’s absolute path.

Ordinary: **37,365 tests, 2 failures, 2 errors, 93 skips**, 6317.97 seconds recorded lane execution.

Guards: **718 tests, 5 failures, 0 errors, 0 skips**, 269.31 seconds recorded lane execution.

These execution totals exclude queue waiting and are not total delivery time.
The run remains red for the previously matched baseline failures below; no new
or worsened failure remains. This is bounded smoke coverage, not a release full
suite or an all-green claim.

- `com.openggf.game.sonic3k.objects.TestHczMinibossNativeRendering.allOrbitPhasesMatchRomSpriteCompositionInNativePixels` (failure).
- `com.openggf.tools.TraceCaptureBootDimensionsTest.realBootKeepsNativeGameplayCameraAndWidePresentationCaches(int)[1]` (error).
- `com.openggf.tools.TraceCaptureBootDimensionsTest.realBootKeepsNativeGameplayCameraAndWidePresentationCaches(int)[2]` (error).
- `com.openggf.tools.audio.completerun.TestCompleteRunAudioCli.freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput` (failure).
- `com.openggf.game.TestProductionSingletonClosureGuard.productionCodeDoesNotUseForbiddenProcessSingletonsOutsideEngineServices` (failure).
- `com.openggf.game.TestProductionSingletonClosureGuard.productionCodeOnlyUsesRawGetInstanceAtEngineServicesBootstrapBridge` (failure).
- `TestArchUnitRules.shared_code_does_not_construct_concrete_sonic_provider_classes` (failure).
- `TestArchUnitRules.shared_layers_do_not_depend_on_game_specific_packages` (failure).
- `com.openggf.tests.TestNativeImageResourceGuard.everyRuntimeResourceIsReachableFromTheNativeImageConfig` (failure).

The ordinary failures match the baseline assertion locations: HCZ native pixels
at line 53, both trace-boot parameters at line 41 and fresh-JVM audio CLI at
line 182. The latter three reproduce with the baseline's backend-property and
JAVA_TOOL_OPTIONS environment; the earlier clean-environment focused followup
passed both boot cases and all audio CLI cases. Guard failures match the existing
HubMusic constructor/package dependencies, MenuAudioProbeTool singleton calls
at line 41, and missing AuthoredLevelDecoder native-image resource. The baseline
evidence and exact focused commands are recorded in the foundation plan.

All 93 ordinary skips were inspected: opt-in measurements/captures/soaks,
graphics prerequisites, external mod/BizHawk fixtures, absent route placements
and the CPZ spin-tube prerequisite. There are no missing-ROM skips. Required
S3K loading/bootstrap/decoding/AIZ checks executed without failure. Guards skip
none. Consumed category diagnostics are acknowledged and removed before delivery.

After this frozen run, the camera fix and rendered handoff `efa7875f8` /
`08254a5da` were reconciled without conflict. Its four production-file changes
are confined to the cinematic projection scope. The 122 companion checks and
two final pixel regressions above cover affected consumers, state restoration,
ordinary-render preservation, replay, API pins and architectural source guards.
That focused evidence covers the bounded post-smoke correction; an unchanged
broad suite is not repeated on the integration tree. Subsequent edits only
document foreground alignment, native-phase backgrounds and the review lesson.
Mirrors, relative links, whitespace and directing scenarios are checked separately.

External Editor source `e4596f00b0e7de058791108ed1db429353fd2c17` passed
all 22 tests with no failures/errors/skips against the frozen corrected runtime.
The check used Java 25, Lua 5.4, explicit surfaceless EGL, original absolute S2/S3K
ROM properties, `-Dopenggf.stageplay.desktopPictures=true`, and the corrected
`live-air-traffic.stageplay` as `openggf.stageplay.liveScene`, through the queued
Maven wrapper. Maven execution took 5.264 seconds. Its clean merge into Editor
main is `dbf9761def66a0223cad508be8f1799a0cd152a1`; tracked source matches
the tested tree exactly.

Revision-six production sources are published in OpenGGF-Media main at
`bb503d931af607167acfbbec24487758e61074c6`:
[Stageplay archive](https://github.com/OpenGGF/OpenGGF-Media/tree/main/projects/stageplay).
All nine MKV/MP4 files decoded fully at 60 fps; all 4,860 parsed native records
equal revision five. Normal-speed browser playback reached all four MP4 ends.
The promo reported two browser presentation drops out of 3,600 frames; companions
reported zero. Exact source decoding retained every frame. Contact frames 120/239,
the 16-frame boundary sheet and representative moving snapshots were inspected.
The 60-second promo measures −18.0 LUFS and −4.8 dB true peak; physical listening
and creative acceptance are not claimed. Camera contact is corrected without
changing native physics or inventing art under the transparent native ledge.

Catalog verification passed for 6,896 source files and 14,348 external entries.
All 17 new source copies, 24 external artifact hashes, JSON/Python syntax,
index links, original CSV byte prefix and public-exposure checks passed.
The Media task worktree/branch and owned preview server are cleaned up. Engine
and Editor final packaging, destination pushes and accounted worktree cleanup
follow this checkpoint; their resulting commit IDs belong to the delivery reply.


## First-creator tutorial follow-up — 2026-10-22

The user accepted the working features but found the earlier story incoherent and
requested a tutorial/promo for first-time creators. The new `wait-for-me` sample
uses one 26-second LIVE shot: Sonic crosses alone, responds to Tails, returns and
counts them into a shared crossing. A 124-second film explains actual directing
choices before playing the complete short. Editorial notes are not editor UI.
Production sources and rejected takes live in the existing Stageplay media archive,
revision seven; no Java runtime or editor behavior changes in this follow-up.

The first tutorial cut clipped Sonic's return-jump apex (centre Y 7.71 at frame
687). Raising the group camera by 15 pixels and ending his line before takeoff
keeps the movement readable. Keep full moving review and frame-specific checks: a
successful native landing alone cannot certify the framing.

Validation uses the delivered runtime `efa7875f8c60885dad0458a2cfbe77c0cd49a4de`
(integrated as `38c2736ccf1a0372cf3df3edbd7ba1d6393e5340`). Compile, native simulation,
lossless rendering, generator equivalence and full encoded-media checks validate
the new content. The change-based plan selected 3,565 smoke classes because the
example paths are unclassified. That is disproportionate for scene data and prose
with unchanged Java/POM/selection policy; the direct content checks replace it.
No smoke/full-suite pass is claimed, and prior runtime baseline failures remain
as recorded above. Exact production commands and observed results are retained
in the revision-seven README, capture/edit drivers and provenance.


## Complete-cast continuation — 2026-10-22

Task base: `7d35d4a7b`; engine and separate desktop Editor task branches are both
`feature/ai-stageplay-complete-cast`. No merge, push or PR belongs to this handoff.
The native-image decoder service resource is fixed first in
`eb9b6138ccd182fd05cb6dee3c84d51cd15bdb44`; its exact focused resource guard passes
1 test, no failures/errors/skips. This removes that recorded baseline failure.

Phase 1 introduces canonical version 6 and screenplay version 4. New LIVE and
TERRAIN_ONLY scenes share the native world; the latter clears initial objects
and rings. Strict migrations retain explicit LEGACY_TERRAIN_ONLY/LEGACY_LIVE
behavior for v1–v5 documents, including original physics/presentation limits.
Five-argument Java SetSource calls retain legacy terrain semantics. New native
scenes support supplied-ROM mixed player donors, authored-level decoder snapshots,
empty/posed-only shots, arbitrary supported film dimensions, fractional held zoom
and registered native binding. Binding still validates its same-set, same-character,
unit-scale value handover against the existing visit/lease contract.

Native art, body/tail/dust animation and intrinsic abilities follow each performer's
donor over shared terrain/clocks/collision. Input edges use observed landing and
native ability readiness rather than a goal-age guess. Scripted follower flight is
admitted only for exact shared-world bodies currently owned by scripted input;
ordinary gameplay and legacy LIVE retain their existing gates. Rewind captures
the native world plus director and held-input state. Empty scenes retain a hidden,
non-interacting native focus body; native AI may observe that focus.

Preview/export transmit bounded original logical-ROM window descriptors and
fingerprints, reconstructing composites without joined-ROM files. Authored sets
use a confined editor root or original bounded project bytes admitted through the
registration transaction. Streaming readers reject oversized/unterminated records
before unbounded allocation; producer and consumer admission bounds agree.

The bounded opaque world presentation pass tiles existing native viewports, keeping
native 224-line owners unchanged, and filters the completed picture separately from
text. Posers join the world before sampling. Nested FBO helpers restore their caller
rather than binding framebuffer zero. Source bounds are 4096 per side and 8 MiPixels.
The native-sized 1x path preserves the direct picture. Animated zoom, directed
background phase and activation beyond the native window remain phase 3 work.

Rejected approaches: silently migrating legacy physics; forwarding only whole-image
paths; writing joined ROMs; global GL_LINEAR; straight-alpha sprite filtering;
resizing or reticking native deformation/physics owners during rendering; fixed
goal-age takeoff/repress timing. The ability regression showed an initially falling
Knuckles lost the first press before landing, then received no native second edge.
No physics constant or position correction was used.

Focused validation (all from the task worktree, Java 25, queued Maven):

- `python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestStageplayLiveBinding,TestStageplayPlayback test -B`: 13 pass, no failures/errors/skips.
- Initial transport/export/capture selector: 15 pass, no failures/errors/skips.
- EGL Stageplay/API/background/four-required-S3K selector: 302 tests, 3 failures,
  10 errors, 1 opt-in HUD-picture skip. Failures were stale detached/default fixtures
  and a leftover fixed-zoom follow check. Corrected fixture checks passed narrowly;
  the new ability regression initially failed and was diagnosed as described above.
  Actual native-world, composite-preview, compositor, binding, API and four S3K
  classes passed without missing-ROM skips. This red run is not a green suite claim.
- Final combined selector `TestStageplayUnifiedWorldRom,TestPlayableSpriteMovementTailsFlight,TestPlayableSpriteMovement,TestStageplayRomTransport,TestStageplayPreviewAssets,TestStageplayProcessInput,TestStageplayCaptureTool,TestStageplayExportJob`
  with `-Dmse=off`, explicit absolute S2/S3K ROM properties,
  `-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true test -B`: **233 pass, zero
  failures/errors/skips**, 81 seconds execution, zero queue wait.
- `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package -B`:
  package/SDK/Javadoc/bundled-mod validation passes; tests explicitly skipped.
- Desktop `TestStageplayChrome`: 13 pass, no failures/errors/skips. Picture fixtures
  were corrected to explicit legacy mode and the real shipped sample path; exact
  `TestStageplayChromePictures` rerun with opt-in property and EGL passes 2 tests,
  no failures/errors/skips. The first attempt's failure/error and a fixture method
  typo compilation failure are recorded as development failures, not baseline.

Evidence root: `$TASK_EVIDENCE/stageplay-complete-cast/`.
`phase1-unified-world-v2.mkv`, `.mp4` and `.png` come from committed-source candidate
`examples/stageplay-demo/unified-world.script.json` / `.stageplay`: 384×216, held
zoom 1.2, three mixed native performers plus one Poser, populated then cleared
world. Each action has at least 0.5 seconds lead-in/out. FFprobe verifies 60 fps,
360 frames and 6.000 seconds; complete `ffmpeg -xerror` decode passes. Normal-speed
browser playback reached the end with 360 frames and zero presentation drops; moving
performances/transition were inspected. The first take clipped its long caption;
the revised source shortens it. Desktop offscreen picture evidence is copied below
the same evidence root. These samples do not certify general ROM parity.

The user explicitly selected native Free boss arena behavior: no object-specific
spatial adapters. Coverage and both editors must label restrictions exactly
“Free: native arena position, anchor ignored”, with the owning routine citation.
Generic factory/context admission rejects unavailable native sets actionably.
Puppet/Pose must relocate native child relationships together. Phase 2 implementation
and generated complete registry coverage remain pending.

Final phase hashes, donor effect follow-up validation and combined smoke attribution
are added at their checkpoints. STATUS.md in the external evidence directory is the
current handback, including live job handles and remaining work.

Phase 1 desktop commit: `bc8d61ecd02fc9a7f9fdee9c8bd7c932646294ee`.
Its shared API consumer controls and picture-fixture compatibility are tested as
above; no merge/push was made. Engine final review additionally found intrinsic
donor effects need their own ROM factory/art/audio scope. Preparation now precedes
roster publication, but effect admission follows correct CPU role registration;
follower overlays use rewindable auxiliary identity and explicit native effect
recreation. World pickups retain world semantics. Only intrinsic ability audio
uses the donor route; seek remains silent and close restores previous bindings.
The native shield routine also needs its structural-parent completion pointer,
matching Obj_InstaShield_Main rather than the update caller's main-player argument.
Its focused regression and actual effect pixels are required before phase 1 closes.

### Directed boss decision: visual puppetry, no native side effects

The user selected visual puppetry after the native write audit. **Free alone runs
the native routine**, including native player forcing, water/palette changes, music
and arena locks. Puppet/Pose suspend the object and child state machines. Pure
Animate_Sprite-style ROM scripts and mapping frames supply appearance; child parts
follow their native spatial relationships as render/transform followers. Any
state-machine-only appearance falls back visibly to a held/authored frame and
receives a per-object exclusion with the owning routine citation in coverage.
A relocated picture with native effects still acting in the shared world is
explicitly rejected. Capture EHZ's car parts and an S3K composite, including a
listed fallback, to make this restriction reviewable.

Deferred follow-up: complete isolated native graphs for directed performers.
A small ObjectServices facade cannot provide this: it exposes concrete writable
camera, level, sprites, zone runtime, water, palette, hardware and art owners.
S3K CNZ/LBZ routines directly force player controls and positions, bypassing
contact gates; HCZ initialization changes flags/music/palettes/camera. EHZ/S1GHZ
escape changes camera/music/PLC/boss state. A read-only PlayableEntity proxy also
fails because routines cast it to AbstractPlayableSprite. Continuous isolation
would need private native managers, players, mutable terrain/runtime state, queues
and art/palette ownership, sharing only immutable ROM inputs. Restoring a camera
or flags after drawing cannot retract already-emitted native work. This subsystem
is deliberately outside the current phase; no spatial adapters or unsafe continuing
routines are substituted for it.

Final donor/source correction selector passes **358 tests, zero failures/errors/skips**
(91 seconds Maven execution, 201 seconds queue wait):
`TestStageplayDonorEffectsRom,TestStageplayUnifiedWorldRom,TestStageplayAudioLifecycle,TestScopedDonorAudio,TestDonorAudioRouting,TestPlayableSpriteMovement,TestInstaShieldHitbox,TestInstaShieldGating,TestShieldRewindRestore,TestShieldRewindPendingRestore,TestShieldPublicationBanks,TestTailsFlightController,TestPlayableSpriteMovementTailsFlight,TestTailsTailsFlightSelection,TestStageplayRomTransport,TestStageplayLivePreviewRom,TestModApiSignatureSurface`.
The queued command used `-Dmse=off`, explicit absolute S1/S2/S3K ROM properties,
`-Dopenggf.headless.gl=surfaceless-egl`, one reused fork, and unset display variables.
Four previously omitted S1 publication cases now execute. The legacy GLFW-only
Insta-Shield picture class was excluded from this final selector; actual effect
pixels use the Stageplay EGL capture below, without a desktop fallback.

The first donor attempt failed compilation at an inaccessible manager service;
reuse the existing spawner construction seam instead of exposing that manager.
The next attempt reported 111 tests, one failure, three errors and five skips:
custom donor alias keys lacked native coordination handlers, and the fixture tried
to jump during initial falling. Rejected: passing an invented donor identity into
the native sound driver, or changing physics to fit the early edge. Final source
uses native game keys with library-owned reference-counted leases and profile
epochs; the fixture has an observed grounded lead-in matching the capture.
All 17 movement intrinsic sounds use actor-injected donor routing; ordinary bodies
retain the original captured audio-manager fallback. Normal donor capabilities
preflight actual ROM sound maps. S3K's powered-wall THUMP has no mapped sound and
no authored Hyper-state support here; reaching it fails explicitly, without a
guessed ID or WAV fallback. World pickups/hurt/splash/events retain world ownership.

Source lease regressions cover non-LIFO close, co-owner abandonment, same/cross-game
producer retirement, actual cue routing after boundaries, once-only forward sounds,
silent seek and cleanup failures across independently owned sources. Transported
libraries also retain the explicit opening game rather than map iteration order.

Final phase-1 package (`maven_queue.py -Dmse=off -DskipTests package -B`)
passes (44.5 seconds; tests intentionally skipped). Final donor evidence is
`phase1-donor-ability.{stageplay,mkv,mp4,png}` in the external task directory,
with the screenplay and 150-sample simulation alongside it. Two S3K Sonics on
an initially cleared S2 set jump after 30 grounded lead-in frames and independently
trigger their ROM Insta-Shields at frames 45 and 48. The 384×216, 60-fps MP4 has
150 frames/2.5 seconds, a clean full decode, and at least 0.5 seconds before and
after the ability. Reviewed moving playback through completion, then slower
playback through the short shield arcs; frame 49 supplies the still. Frame 53
was rejected as the representative still because the pure animation has already
reached its empty mapping frame. The contact sheet at frames 45–57 confirms both
independent parent attachments. The earlier six-second unified-world clip shows
mixed S1/S2/S3K performers, the LIVE/initially-cleared boundary, held fractional
zoom and non-native output dimensions; reviewed through completion as recorded
above. Smooth animated zoom, directed background phase, distant ensemble
activation and object casting remain subsequent phases.

Candidate API policy correction at phase-1 commit: the hook rejected a prose edit
to the release descriptor during ordinary mutable-pin regeneration. The user
clarified that descriptor and runtime version stay candidate `0.7.0`. The descriptor
is byte-identical to the base; the compatibility change log records the two additive
legacy policy enum values. Classification helpers moved out of the annotated enum
into an unannotated engine utility. Both simple and fully qualified `@ModApi`
spellings were checked; new runtime helpers are unannotated. The affected queued
lean selector `TestModApiSignatureSurface,TestStageplayUnifiedWorldContract,TestStageplayDocument,TestStageplayLiveBinding`
passes 26 tests, zero failures/errors/skips (90 seconds execution, three seconds
queue wait). No hooks were bypassed.

## Complete-cast phase 2 implementation checkpoint (2026-10-22)

Phase 1 engine landed as `bb9e30002`; desktop controls as `bc8d61e` with internal
classification follow-up `f64acb8`. Phase 2 source builds on that boundary; final
hashes, focused results, actual footage and remaining exclusions follow after
validation. No merge/push was made.

Canonical v7 and screenplay v5 append separate object roles and timed world
commands, retaining strict old migrations and earlier constructors. The shared
workspace owns all object forms in both frontends: native source, portable placed
tuple, mode/position/frame keys, child part overrides, visual move/stop, clear and
removal cues, and actual cached-part/exclusion inspection. Inspector transport is
unannotated engine data; registry/graph/GPU owners do not enter the creator API.
The mutable 0.7 candidate pin adds only document data, keeping descriptor/version
unchanged under the clarified candidate policy.

The first constructor-free coverage inventory generated **22,784 ID/context rows
across 89 contexts**, grouped into ten distinct metadata sets. Every byte ID is
present, including unavailable factories and context restrictions. S2/S3K released
animals are separate existing native factories with source-context species/art
inventory, rather than a fabricated S3K layout ID. This initial report is metadata,
not ROM proof: registered but unobserved visuals require preflight, while a known
missing factory/context/visual output is explicitly unavailable. Final reporting
must retain that distinction alongside actual sample evidence.

Free uses native factories/routines and rejects an incompatible native game/zone/
act with an actionable source-set error. Verified arena resets are game-owned
metadata; a boss role alone does not imply an ignored anchor. Directed modes freeze
all native graph routines and contacts. New constructor-ready visual preparation
uses a disposable source world before opening the final shot. The target never
updates there; source level assembly may run its own initial owners, all retired
with that world. A constructor-gated graph must initialize as Free before a later
mode key captures it. This is not a continuously ticking private boss subsystem.
Existing Free effects already committed to world state are not rewound by changing
a visual mode; suspended routines emit no further native work.

Clear/removal flags belong to the scene's typed world policy so replacement object
managers inherit them before assembly. Opening clear cues run before explicit role
admission; later clears remove existing roles until an authored visibility re-entry.
Explicit Free roots and their child graphs inherit their authored admission. Native
layout admission can be rejected before its factory; a native external dynamic
producer may construct before submitting its object, so registration rejection
cannot retract constructor work already emitted. Clear controls population and
routines, not independent native zone events. This distinction matters for EHZ,
whose zone event would otherwise create a second boss alongside the authored one.

Rejected implementation approaches and review findings:

- A single mutable policy port alongside `NativeObjectPolicyPort` would shadow
  existing behavior under first-match service lookup. Use an independent typed
  scene load/direction owner.
- Eager child tracking created a Stageplay controller in ordinary gameplay and
  retained native graph references. The hook now records only when a Stageplay
  manager already has its controller, before initial construction.
- Treating every `AbstractBossInstance.childComponents` entry as inline art drops
  EHZ's independently rendered car, drill and wheels. Capture registered child
  render entries and unmanaged folded parts through their actual native producers.
  Preserve native bucket/class/slot ordering; stable structural part IDs are assigned
  before sorting and never derive from runtime slots.
- A trail bank reserved 6,144 patterns per graph, limiting even tiny directed casts
  to ten graphs. Use actual-size, content-shared leases inside the governed range;
  shared palette contents reuse their bank. Capacity remains bounded, including at
  most fifteen distinct scoped palette snapshots, with explicit admission errors.
- Whole-tile replay discarded native scanline clipping. Preserve the captured row
  range in SAT and painter paths. Residual primitive/tile ordering cannot yet be
  faithfully captured, so reject that appearance instead of replaying a wrong picture.
- Provider animation sets are not automatically ROM-backed: some are synthesized
  constants. Require parsed trailing ROM bytes and pure animation terminators;
  routine-control terminators and unsafe SWITCH targets remain held/authored.

The EHZ ROM audit used verified S2 REV01 SHA-1
`8bca5dcef1af3e00098666fd892dc1c2a76333f9`. Eight of eleven existing hardcoded scripts
match the ROM; three contain an extra zero/frame (propeller animations 1/2 and
vehicle-top animation 4). Replacing native tables would change ordinary Free timing,
so native behavior is retained. Also rejected: registering a single aggregate EHZ
animation table, which would apply propeller animation to car parts. Coverage lists
the held multipart state/animation exclusion with `loc_2F262` and
`Ani_obj56_a/b/c` citations. The external `phase2/ehz-animation-rom-audit.json` and
recipe README preserve the comparison and capture gates. No behavior constants
were fitted or changed.

Initial build-only validation caught one missing `ObjectInstance.isHighPriority`
implementation on the synthetic visual handle (55.7 seconds, no tests). After
fixing it, production compiled; test compilation exposed two fixture type errors
(85 seconds, no tests). These are compilation outcomes, not test failures or
suite results. The next combined focused selection covers the corrected source,
object-manager consumers, API pins and required S3K checks with explicit ROM paths.

The first focused request was cancelled before admission (271 seconds waiting,
exit 130, no Maven execution) after review found that inactive placed Puppet/Pose
roles could still call a factory in the shared world. Initially directed or hidden
placed tuples are now suppressed before final level assembly; constructor-ready
visuals use the exact original ROM spawn in the disposable source world. Returning
a proxy to Free explicitly materializes its original portable placement. Already
running Free graphs remain capturable before suspension.

A second request was cancelled before admission (151 seconds waiting, exit 130,
no execution) after the bounded renderer review found unused palette reservations,
lost custom occlusion masks, child-edge sorting that could change structural paths
after rewind, and silently omitted SAT control children. SAT controls require an
explicit unsupported-appearance result until their complete semantics can be
represented. Draw-owned MGZ/Pachinko counters also need a read-only capture boundary;
capturing a visual must not advance the native Free state. These are pending focused
verification, not attributed baseline failures. No full suite was requested.

Continuously driven isolated native boss graphs (the user's option 1) remain deferred.
They would need separate world/session, audio, palette, water, terrain and camera
ownership, exact child graph lifecycle, cross-world asset leases, deterministic
rewind, and a verified transfer of render output. Running a relocated native graph
against the shared world would allow invisible arena effects and was rejected.
The selected visual-puppetry contract runs no directed root/child AI, holds stateful
appearance, and admits only pure ROM animation scripts. Constructor-only preparation
is a bounded asset/graph observation, not that deferred continuously running system.

The bounded shared-editor review also caught replacement-by-append changing native
cast allocation order, placed-role visibility crossing foreign sets, and a single
bulk field unable to represent every legal part-override list. Preserve role order,
scope portable placements to their source at boundaries, and use per-part editing
for long lists. Both frontends delegate to that shared form layer. Static catalog
appearance exclusions must be reachable there as well as in the generated report.

### Research retained for remaining phases (not implemented at this checkpoint)

Directed parallax needs an immutable presentation phase captured with the native
scroll publication, sampled through an unannotated game-owned service. Do not
rerun live handlers, clone only their rewind mementos, or call a provider's normal
zone initialization under the director camera: handlers can publish camera,
terrain, PLC, runtime and shake changes. Reuse existing arithmetic/composer kernels;
separate advance/publication from composition in the owning handler. At native
camera coordinates the sampled registers/arrays must match the published picture.
For AIZ capture native frame/act, wave origin, shake, intro offset, fire coordinates
and curtain phase, post-fire haze, battleship smooth offsets, and world water Y.
Freeze event-mode gates from native state; director X must not trigger an event.
`ScrollEffectComposer` and deformation plans already support arbitrary line counts;
AIZ's two fixed-224 writers need that same viewport contract. DEZ's stationary
origin belongs to its own provider. Base director camera and raster tile origin
must be separate: treating each 224-line strip as another camera produces seams.

Cinematic background residency must be privately owned as well. The current
`ensureBackgroundTilemapData()` rereads native parallax values, undoing a directed
window selection. Preserve authoritative persistent descriptors/ring origins;
a stateless rebuild loses native event history. Sampling must neither shift the
native CPU cache nor overwrite its GPU texture.

Lossless decompilation requires exact IDs and list ordering, including intervals
that cross shot boundaries. A typed screenplay revision can preserve explicit IDs,
transition IDs, global event ordinals and puppet-track order; earlier screenplay
versions retain their generated IDs/bounds. Do not hide a canonical document blob
inside the screenplay. Verify all shipped examples and editor-style mutations.

A route helper can return existing intent commands without a canonical schema bump.
Use actual collision and native movement in a disposable worker; speculative native
branches must restore audio logical state as well as the world registry. Static
`PlayablePuppet` probes cannot certify live objects/events/water. Cold replay the
emitted commands before reporting arrival. Bound nodes/ticks/memory and report
search exhaustion without claiming a route is impossible. Object Puppet routing
is visual floor/headroom following only; reject jumps or controller-specific
movement rather than running its suspended native AI.

Phase-2 compilation passed production/test sources (88 seconds). The subsequent
51-class focused selector completed **438 tests: 435 passed, two failures, one
error, zero skips** (45.447 seconds execution; 330 seconds queue wait). API pins
passed. All mandatory S3K checks executed green: AIZ 8, game loading 36, legacy
loading 7, bootstrap 6, decoding 3. The three new-case findings are scoped palette
capture selecting the native row instead of its trusted presentation resolver,
missing explicit placed-role observation after initial clearing, and no published
palette row 1 in a logic-only constructor-preparation world. They are development
findings, not baseline-attributed failures. The palette correction must preserve
the native presentation subject while enumerating mappings and use the existing
level palette publication owner before source capture; it must not fabricate art.

The generated registration-only inventory now exists in JSON and Markdown under
`$TASK_EVIDENCE/stageplay-complete-cast/phase2/object-coverage-v2.*`: 89 contexts,
22,784 ID/context rows, ten metadata groups. Each mode reports 8,861 unavailable
rows and 13,923 requiring preflight; no unobserved visual is falsely certified as
available. Known SKL SAT-control exclusions are included. All six source recipes
compiled structurally to canonical v7; this is not yet moving visual evidence.

The parallel `feature/ai-editor-authoring-parity` lane owns level-editor features
and a placed trigger calling existing playback APIs. This branch preserves the
shapes of `StageplayPlayback.register` and `StageplayControl.play()`. Its phase-1
LIVE binding extension permits visit/lease-bound player handover, with unchanged
KEEP_FINAL_POSITION / RESTORE_ENTRY choices and stricter native source matching.
The lead should reconcile shared chrome changes and those trigger expectations.

The subsequent palette/placement diagnostics completed 27 tests (25 passed, two
failures, no errors/skips; 89 seconds execution, no queue delay), then two ROM
methods (one passed, one failed; 86 seconds execution, 404 seconds queue wait),
then six focused classes (46 tests: 45 passed, one failure, no errors/skips;
88 seconds execution, two seconds queue wait). The scoped palette replay fixture
was drawing bucket 4 instead of its captured native minimum bucket; correcting
the request preserves the source-lease retirement, exact raw color and no-resolver
reuse assertions. All five strict report-import tests and all six cast unit tests
passed in the last run. The remaining placed-object case proved admission before
execution and camera/latch X=860, rejecting the stale-camera hypothesis.

Its source cause is initial `MutableLevel.restoreObjectBackedRingState` marking
the transformed population dirty: the first runtime dirty-region flush resynced
placements and cleared active placed actors. Initial Stageplay decoding consumes
its own just-created object/ring notifications before managers assemble; ordinary
editor/runtime edits retain their resync contract. An independent admission-order
review also moved explicit authored ownership before the persistent clear filter.
These latest fixes await the affected ROM regression; no native unload constants
or routines changed. Temporary diagnostic logging/reflection was removed.

All six provisional capture recipes reached their directed transition during
logic-only CLI simulation, but none completed: five lacked native CPU palette
publication when capturing a running Free graph; S1 GHZ boss encountered a missing
pattern while freezing its renderer. Constructor-only preparation already publishes
its palette successfully. Fixing the later capture boundary must consume current
native ROM palette/art ownership without inventing colors or patterns. No phase-2
film or sample-specific availability is claimed at this checkpoint.

Arena-context choice: the generic Free guard matches the authored cast source to
the current set and preserves registry compatibility checks. It does not infer a
canonical arena act from the boss category. A global factory admitted in another
source context still executes at its ROM routine's native coordinates and may
apply world effects there; the exact anchor restriction remains visible. This
uses the user's permitted native-coordinate behavior, with no spatial adapter.

State review after six successful provisional simulations found two clear-command
issues. S3K auxiliary animals use a non-layout source with transport ID 0; treating
that numeric ID as a ring incorrectly preserved an animal. Require portable layout
identity for numeric placement classification, and use game-owned instance traits
for actual native dynamic rings. Inactive badnik inventory also needs semantic
factory traits before activation: tag the existing registrations, including S3K
context-selected aliases, rather than constructing objects or inspecting lambda
classes. Native constructor expressions remain unchanged. Non-base enemy roots
also need the same registry-trait fallback in clear/admission consumers.

After the first repair,43 affected tests passed (zero failures/errors/skips;
87 seconds execution,138 seconds queue wait). Packaging passed in43.655 seconds
with tests skipped. The updated engine installed into the owned desktop editor's
private repository;14 editor cases plus one EGL picture case passed (15 total,
zero skips;3.490 seconds, no queue wait). Both frontends' object command, arena
restriction and mode forms are captured under `$TASK_EVIDENCE/stageplay-complete-cast/phase2/{desktop,hud}/`.

The later clear/registry/API/mandatory-S3K/HUD selection completed133 tests:
132 passed, one failed, no errors or skips (97 seconds execution,27 seconds queue
wait). Every required S3K class, API pin and HUD capture passed. The new S3K animal
clear case failed its pre-clear presence assertion in an unverified AIZ setup;
its exact native retirement branch was not measured. It now uses the ICZ source,
anchor and camera already observed in actual scene simulation, with unchanged
presence/clear/ring/rewind assertions. No native lifetime constants were tuned.

The complete badnik trait audit converted14 S1,28 S2 and31 S3K existing factory
declarations, covering14/26/40 native root classes respectively. Context-dependent
S3K roles follow selected factory entries, including stock overrides; they do not
leak to machinery, cutscene or boss aliases. Non-base enemies use that same trait
at runtime after class/head-role checks. Exact native constructor expressions
remain unchanged. Added Java citation strings were separately parsed as prose
(the ordinary scanner skips Java string literals): all26 resolve to their owning
pinned labels. The two new repeated-animation-frame pitfall citations also resolve.

The corrected ICZ clear fixture and native read-only capture/mask consumers passed
all seven affected tests (zero failures/errors/skips;82 seconds execution,55 seconds
queue wait). Final packaging passed in43.988 seconds with tests intentionally
skipped. The unchanged final production-source fingerprint is
`088f5ff1d4aeef0c2fc6f0c48483e20c91ed9be55f458d372dbc8f6b2e530ff0`.
All six final supplied-ROM scenes simulated and rendered successfully; ordinary
samples are450 frames, GHZ boss690, EHZ/ICZ bosses750, all60fps with audio.
Full lossless-file decode passed. Clear-command state samples confirm removal of
authored/inactive badniks at330 and objects at390, including S3K auxiliary animals;
ring counts remain unchanged. S1 ring-layout object entries intentionally remain.

Evidence lives under `$TASK_EVIDENCE/stageplay-complete-cast/phase2/`: the six
`*-final.mp4`/stills/state samples, with ICZ's further framing revision named
`*-take4.*`; source recipes and canonical scenes are retained alongside them.
Ordinary samples and multipart EHZ/GHZ/ICZ graphs were reviewed through normal and
quarter-speed browser playback plus phase-boundary stills. Captured held animation
is labeled honestly. GHZ's tall background repeats and the ICZ lower background
edge remain phase-3 presentation work; these films certify cast behavior only.
The exhaustive generated `object-coverage-final.{json,md}` includes exact scoped
sample observations; it does not promote untested ID/subtype/context combinations.

The final desktop dependency recheck first installed the thin jar by mistake:
14 tests had five passes and nine missing-Jackson errors. Installing the packaged
jar restored14/14 passes, zero skips (1.453 seconds, no queue wait). This was local
consumer setup, not a source regression; the owned editor repository is repaired.

## Complete-cast phase 3 implementation checkpoint (2026-10-22)

Phase 2 committed engine `70f0e910d0ea7cab10a6c31371b293816ea1a0ff`, Editor
`dcd54cf`. Its final inventory contains 89 contexts, 22784 ID-context rows and
27 supplied-ROM role/mode observations. ICZ final cast evidence is take 4;
take 2 cropped the Free ship, take 3 overlapped foreground occlusion. Those
framing changes did not change native routines or the production fingerprint.

Phase 3 supersedes the earlier camera-correction Bound for current native policies,
while preserving it for legacy imports. Pure game-owned composition covers all
39 stock scroll kernels. Native updates freeze animation/event phase; drawing
samples that phase once for the complete bounded raster and slices the result.
Rejected approaches: reticking live handlers, inferred parallax ratios, cloning
mutable worlds, repeating a 224-line phase, and changing global texture filters.
Persistent native descriptor history and physical ring origins are retained in
private FG/BG CPU and GPU residency. Stateless reconstruction would discard event
history; using native FG residency would advance AIZ2's retained forest ring.
Success and aborted drawing must preserve native histories, textures, dirty
versions, queued uploads, scroll publications and ordinary picture bytes.

An internal Stageplay module service opts into phase/descriptor capture. Ordinary
gameplay returns before querying these snapshot owners or allocating their images.
Descriptor snapshots are bounded to 2097152 integer cells (8 MiB) per plane.
The completed-world framebuffer retains its 4096-side/8-MiPixel bound. Magnified
output pixels integrate their coverage of opaque world texels, keeping aligned
integer scales crisp without switching filters at integer crossings. Minification
uses a mip chain only on that completed-world texture; its additional storage is
bounded by one third of the base image. UI and native art sampling stay separate.
Independent EGL pixel oracles cover coverage integration, integer crossings,
half-scale averaging, opacity and sharp subsequently drawn UI.

Ensemble activation uses captured, disjoint performer windows and the existing
native factories/SST. The canonical layout remains available separately from
filtered initial contents, preserving S1 respawn-counter order when objects are
cleared or posed. Supplemental admission projects the ROM counter from both held
loader cursors and preserves lower bits, loaded/kill and remembered state.
Counter aliases or shipped FixBugs=0 loader-counter drift produce an actionable
bound rather than renumbering identities or clearing native bytes. Geometric
visibility/culling admits the extra windows. Intercepting
`setDestroyedByOffscreen` was rejected because native routines also use it for
semantic part cleanup; semantic retirement stays unconditional. Native process
owners with no spatial spawn retain their native lifetime.

A bounded source review found that the separate ending-background publisher could
retain an older directed snapshot. It now invalidates phase/build/column snapshots
without changing native ending words. Its regression requires explicit unsupported
failure for directed ending presentation rather than stale sampling. Diagnostic
`LevelScrollPresentation` equality still omits function-valued directed metadata;
rewind retains the complete immutable publication reference, with no observed
restore loss from that diagnostic limit.

Validation checkpoints (no full suite):

- Build 38758 failed after 59.275 seconds: the CPZ ripple-reader parameter collided
  with a local integer. Renaming the reader preserved native arithmetic. No tests ran.
- Build 12283 failed after 59.821 seconds: detached LBZ code used nonexistent
  `PlayerCharacter.SONIC`. Production and fixtures now use `SONIC_ALONE`. No tests ran.
- Submission 74736 waited 172 seconds and executed 82 seconds. Production compiled;
  three test-compilation arguments used int instead of Camera's short API. Corrected;
  no tests ran.
- First behavioral submission 47457 selected 17 classes with all three absolute ROM
  properties and surfaceless EGL. It completed 183 tests: 155 passed, one failed,
  27 errored, zero skipped; 96 seconds execution after 863 seconds queue.
  New findings: ripple mock boxed Byte instead of Integer (15 errors), nested Mockito
  session construction (seven), unassembled fixture's null base module (three), and
  querying coordinates on a non-spatial native process (two). The projection failure
  exposed SSZ Act 1 cloud mode using Act 2's 8192-pixel period instead of native 512.
  These findings are implementation/fixture defects, not inherited baseline failures.
- Revalidation 76839 selects those five affected classes plus the new HUD animated
  camera-form capture, with the same ROM/backend flags. It completed 75 tests:
  73 passed, two failed, zero errored/skipped; 87 seconds execution after 618 seconds
  queue. All projection/publication cases passed. One remaining activation case
  exposed geometric unloading clearing a far actor's already-published kill before
  native destruction dispatch. Stageplay now preserves that dispatch and rejects
  rematerializing dead owners; the regression checks native byte `0x88` before and
  after cleanup. The other failure was GL error 1281 at the end of AIZ2 drawing;
  all ownership and ordinary-picture comparisons passed. Scoped, bounded GL debug
  diagnostics and stage assertions now localize the owning operation.
- Submission 71714 combines those affected checks with the required S3K families,
  affected native scroll/placement consumers and cast clearing. The compositor
  also now owns writable depth for each private tile clear and restores the host
  depth-write mask, with an extended pixel regression. The prior compiler had
  already read the old compositor when this late edit landed; class-byte inspection
  confirmed it absent, so source timestamps were refreshed after completion and
  this submission performs fresh compilation. No depth-mask-fix pass is claimed yet.
  Unchanged passing projections and API checks are not repeated without a reason.

Ordinary gameplay comparison inputs were captured from the committed Phase 2 fatjar
(SHA256 `b3f665d43e4b71b74a65e89a93426da1d12805240db1e773c330c2fa948f0994`):
150 neutral frames each for S1 GHZ1, S2 EHZ1, S3K AIZ1 and S3K DEZ1, using original
absolute ROM paths and surfaceless EGL. All four captures exited zero, retaining
per-frame PNG/state and 2.5-second, 60-fps MP4s. These are before-comparison inputs;
no Phase 3 gameplay-preservation or moving-visual pass is claimed yet.

Pending-phase preparation, read-only during queue waits: screenplay v6 can preserve
literal supplied IDs and transition IDs, plus a typed optional order object of
exact ID permutations for puppets/captions/audio/effects/dialogue. Canonical documents
already require boundary keys, so decompile those directly. An interval belongs to
its start shot but retains its full end time across later shots. Versions 1–5 keep
their generated IDs and interval bounds. Account for bounded screenplay expansion
beyond an 8-MiB canonical document; do not embed a canonical payload blob.

Route preparation found that `StageplayLiveWorld.Saved` captures the world registry,
director/controllers and object cast, but not `AudioLogicalSnapshot`. Speculative
LIVE branching must restore producer/timeline logical state too; `beginRewindReplay`
changes suppression, not that cursor. Detached `PlayablePuppet` can propose terrain
edges but cannot certify live objects, events or water. A dedicated route process
must cold-replay emitted intents in a fresh real LIVE world, bind the result to the
source document/assets/ROMs, and reject stale editor results before one undoable edit.
Object Puppet terrain routes remain visual floor/headroom paths with AI suspended;
no synthetic jump or controller claim.

Phase 3 qualification completed: combined submission 71714 ran 157 tests,
156 passed with one GL failure, zero errors/skips (86 seconds execution, 419 seconds
queue). All mandatory S3K checks passed: AIZ eight, loading 36 plus seven, bootstrap
six, decoding three. Native scroll/placement, cast clearing/ring provenance,
workspace and six EGL world-sampling regressions passed, including the new depth
write-mask success/failure checks. The remaining GL failure was precisely localized
to the first ordinary AIZ2 draw before directed sampling: ArenaMaskRenderer retained
a retired context's program/VAO. Fixture-owned graphics cleanup now occurs between
world retirement and EGL teardown. Final two-case rerun 88811 passed both cases,
zero skips/errors/failures (29.891 seconds execution, 1069 seconds queue). Native
RAM, pending/published scroll/SAT, FG/BG residency and ordinary picture assertions
pass for both successful and aborted multi-tile AIZ1/AIZ2 draws.

Packaging with `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package -B`
passed in 45.034 seconds. The packaged dependency was installed into the owned
Editor's private repository. Its normal-lane Chrome plus animated-camera-picture
selection passed all 15 tests, zero skips (2.953 seconds, no queue wait). Explicit
lean mode was rejected before execution because the Editor has custom Maven
configuration; no flags or admission rules were bypassed.

Final evidence is outside the repository at
`$STAGEPLAY_EVIDENCE/phase3/` (the task’s external evidence directory):
`zoom-ramp.{mkv,mp4}` plus `aiz-directed-pan-take2`,
`dez-stationary-background-take2` and `ensemble-activation-take2` films, matching
canonical scenes/recipes, state JSONL, stills and UI images under `hud/` and
`desktop/`. The zoom film is 600 frames/10 seconds; AIZ and DEZ are 360 frames/six
seconds; ensemble is 300 frames/five seconds. All are 60 fps with 48-kHz offline
audio, at least half a second lead-in/out, and complete lossless decode checks.
Normal and quarter-speed browser playback was reviewed with intermediate/boundary
stills. AIZ shows separate foreground/background movement; DEZ's city backdrop
stays fixed; both separated native performers jump and land. Captions stay crisp
through the zoom ramp. The first AIZ waterfall-dominated view, DEZ foreground-wall
view and overly tall ensemble were rejected framing; their drafts and the AIZ
framing survey remain identifiable, not final certificates.

All 600 decoded RGBA frames from four ordinary 150-frame gameplay captures
(S1 GHZ1, S2 EHZ1, S3K AIZ1, S3K DEZ1) match the Phase 2 build byte for byte;
all four state CSVs also match exactly. Compare every RGBA byte: an RGBA
`ImageChops.getbbox()` can miss RGB changes when the alpha-difference channel is
zero. `ordinary-comparison.json` records the actual comparison and both source
fingerprints. Captured production fingerprint is
`7bf07978ac1c7cdb9862cce5cebf361515f4b62498cf93addfde5bf7b94f2cb8`;
packaged jar SHA256 is
`becc3f0c99fc333860d9466b7c56ef2e249504d5b7a9ccffe94ffa456bc91ac4`.
No production source changed during captures. Final change-based smoke remains
pending after phases 4 and 5; these focused results are not a full-suite claim.

Before committing Phase 3, the policy hook detected declaration-only differences
that the normalized signature test did not consider API changes: reformatted
existing overloads and private residency records inside an annotated renderer.
Restore the overload formatting and keep the two snapshots in an unannotated
package-private helper. No public declaration changes, candidate-pin regeneration
or release-descriptor edit is required. Captures above retain their exact prior
source fingerprint; this mechanical extraction is qualified separately with the
full directed-background ownership/pixel regressions and normalized API test.

Final helper extraction check: queued normal selection
`TestStageplayDirectedBackgroundRendering,TestModApiSignatureSurface`, absolute S3K
ROM plus surfaceless EGL, passed 13/13 with zero failures/errors/skips (88 seconds
execution, ten seconds queue). This preserves the captured pixel implementation
while keeping all new residency helpers outside the annotated API type.

## Complete-cast phase 4: two-way authoring

Phase 3 engine commit `74691157e`; Editor `d446650`. This closes the
previously one-way screenplay limit. Canonical version seven stays unchanged;
screenplay version six preserves literal IDs, exact typed track permutations and
whole cross-shot intervals. A deterministic decompiler checks compilation equality
before publication. Versions one through five keep their old migration, ID and
interval behavior. Version-six input is bounded at 32 MiB to allow expansion from
a canonical file still capped at 8 MiB.

Rejected: a canonical payload blob inside screenplay (not authorable), splitting
intervals at cuts (restarts reveal/phase), prefixes on supplied IDs (breaks legal
64-character IDs), and relying on sorted cue lists (loses supported document order).
Both frontends share import/export; import starts an unsaved canonical document,
export preserves save/undo state, and relative assets retain their root. Public
playback and candidate API shapes are unchanged.

Planned focused qualification: all eight shipped canonical examples and seven
shipped screenplay examples, strict malformed-v6 rejection, old-version behavior,
arbitrary IDs/order/cross-shot cues, editor refinement round trips and failure
atomicity. CLI tests cover bounded no-overwrite publication and relative roots.
Offscreen evidence will render the editor-refined canonical document and its
export/recompile result, compare complete decoded pixels/audio, and review moving
footage plus both frontend forms. Final results follow after execution.

First Phase 4 qualification: queued lean exact selection
`TestStageplayScriptCompiler,TestStageplayScriptDecompiler,TestStageplayScriptRoundTrip,TestStageplayTool,TestStageplayWorkspace,TestStageplayController,TestStageplayStore`
with `-Dmse=off test -B` passed all 96 tests, zero failures/errors/skips
(89 seconds execution, 841 seconds queue). This includes all eight canonical and
seven screenplay examples, stable second-generation export, typed ordering,
cross-shot intervals and frontend-independent import/export history contracts.

Final Phase 4 qualification and evidence:

- Normal exact HUD picture selector
  `TestStageplayHudPictures#captureScreenplayImportRefineAndExportForms`,
  `-Dopenggf.stageplay.hudPictures=true`, surfaceless EGL and absolute S2 ROM:
  one pass, zero failures/errors/skips (28.102 seconds execution, 75 seconds queue).
- Build-only `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package -B`:
  success, 45.833 seconds; tests intentionally skipped. Installed the dependency
  jar into the owned Editor's private Maven repository before its checks.
- Editor normal exact selection
  `TestStageplayChrome,TestStageplayChromePictures#captureScreenplayImportRefineAndExportForms`,
  `-Dopenggf.stageplay.desktopPictures=true`, EGL and absolute S2 ROM:
  16 passes, zero failures/errors/skips (3.091 seconds, no queue wait).

Both actual UI flows import a version-five screenplay, edit actor keys at frames
90 and 119, export version-six JSON, and save the refined canonical scene. Their
canonical files match byte for byte. Recompilation retains the complete document.
The refined and exported/recompiled scenes were rendered separately through the
production offscreen capture host: all 120 RGBA frames and all 384,000 decoded PCM
bytes match. Both movies pass complete lossless decoding. The two-second film is
60 fps, with 48-kHz stereo audio and 30-frame lead-in/out. Normal and quarter-speed
playback was reviewed, including six intermediate samples during continuous slow
playback; all 120 callbacks were observed in the slow pass. UI forms and boundary
stills were inspected.

Evidence: `$STAGEPLAY_EVIDENCE/phase4/`, including
`{hud,desktop}/ui-refined.stageplay`, `ui-refined.screenplay.json`,
`recompiled.stageplay`, both import/export form PNGs,
`editor-refined.mkv`, `export-recompiled.{mkv,mp4}`, frame 15/60/105 PNGs,
`roundtrip-comparison.json`, film metadata and `capture-source.json`.
The driver `capture_roundtrip.py` records the exact production tool invocations.
The main-player playback API and hand-back semantics remain as in Phase 1; this
phase adds no candidate Mod API surface. Final combined smoke remains pending
after Phase 5.

## Complete-cast phase 5: bounded terrain route authoring

Phase 4 commits: engine `e544f1587`, Editor `a189943`. Implement route planning
without a new document version or opcode. Unannotated request/result helpers keep
authoring/process internals out of the candidate API. Player search begins at a
shot entry and uses real private-playable collision/physics, shared exact LIVE
steering and bounded snapshots. Its emitted candidate must pass a fresh full
linear LIVE replay before reporting VERIFIED. This avoids speculative LIVE
branching: its existing Saved state lacks AudioLogicalSnapshot, and rewind replay
suppression alone cannot restore a producer cursor or arbitrary branch history.

Private PlayablePuppet already creates AudioManager.createSilent(), whose
silentSimulation contract suppresses command admission as well as output. Preserve
that behavior and capture/restore logical producer state where needed; do not add
a broad public audio-history API for a probe whose history is empty by construction.
Real LIVE effects run only during isolated cold verification.

Object walking paths use native injected floor/wall/headroom queries and explicit
authored visual clearance. Share the existing movement kernel and verify every
emitted fractional step. Emit MOVE_TO/STOP commands, never intermediate keys,
initial snaps or native AI. VISUAL_TERRAIN is an honest geometry-constrained picture
route. Reject unsupported gaps, moving collision, mode/key/clear boundaries and
conflicting intent windows rather than silently changing the scene.

The worker owns immutable bounded document/request/logical-ROM/content-asset
inputs. Root-directory strings and memoized ROM identity are insufficient freshness
proofs: reverify content before one undoable Apply, off the UI thread. Generation
and strict requested-track patch checks reject cancelled or stale responses.
Both frontends share the same form/service; the active gameplay/preview/audio
owners remain untouched.

Rejected: straight-line target heuristics presented as pathfinding; fitting jump
parabolas; raw directional probes with different MOVE_TO braking; hydrating a
live body from observations; teleporting a goal; running relocated object AI behind
a picture; claiming an exhausted bounded search proves impossibility. Continuously
isolated native directed graphs remain deferred as decided in Phase 2.

Focused validation and offscreen route evidence are recorded below. The final
once-only change-based smoke follows the Phase 5 commits; its combined result
will be recorded after that run.

Phase 5 review corrections before qualification:

- Preview asset transport snapshots only level projects. Route freshness must also
  bind portrait/skin images and voice bytes; add a route-only union descriptor and
  bounded reader rather than loosening the preview contract.
- A collision-only probe injected with FOREGROUND_ONLY cannot prove the native
  visit has no background collision. Inspect an actually loaded, linearly replayed
  source visit and its injected provider. Reject active background collision,
  then qualify the emitted visual positions against the actual foreground terrain
  on every candidate tick. Moving object solids remain outside this visual graph.
- Account for native prefix/preparation/qualification ticks as well as search work
  in the reported/bounded attempt. Preserve narrow existing-file formatting.

These findings do not change the phase scope or introduce isolated directed
native graphs. They close concrete freshness/geometry ownership gaps in the
existing shared-world worker. Results and captures remain pending.

Phase 5 first focused iteration executed 99 tests: 97 passed, two failed, none
skipped or errored. A 64-pixel empty span was traversable by native walking and
floor reacquisition; the jump fixture now checks an independent no-jump pilot
against a 128-pixel gap and raised ledge. No physics was tuned. The protocol
failure exposed a real validation defect: restoring an entire original PuppetTrack
masked worker edits to snapToFloor/collisionPath. Restore only original commands
before comparing the whole candidate, preserving those changed policies so they
are rejected. Add both policy mutations to the regression. Earlier build failures
were a dangling preview-asset call after the route-only union transport and an
invalid compound var declaration; neither executed tests. Focused rerun pending.

The affected rerun passed 31 tests, zero failures/errors/skips:
`python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestStageplayRoutePlanner,TestStageplayRouteProtocol,TestStageplayRouteJob,TestStageplayRouteWorkspace,TestStageplayRouteTool,TestStageplayTool' test -B`
(97 seconds execution, 279 seconds queue). The earlier 97 passing checks cover
object floor planning, native probes, full asset content binding, private audio
checkpoint isolation and object direction/cast. A separate bounded read-only audit
of the worker/job/apply path found no additional actionable defect. ROM/domain
qualification and captures remain pending.

The first ROM batch ran 94 tests: 91 passes, two failures, one error, no skips
(36.028 seconds execution, 35 seconds queue). Required S3K families, API signatures,
HUD route forms, S2 player route and S1/S2 object routes passed. Player fixture
scans had confused camera maxima with terrain bounds and absolute versus
minimum-relative X: GHZ's floor lay below the old scan, and AIZ's positive minimum
X made its old scan empty. Fix only discovery bounds, retaining native arrival.
Rhinobot's Map_Offscreen is intentionally empty until native wait/init dispatch;
its sample now explicitly initializes as Free before a Puppet key. This exposed
an authoring gap in the global shot-entry restriction: allow object routes at an
existing visible Puppet key after that Free prefix, while preserving shot-entry
only player routes. Full cold replay owns the prefix and checks every directed
tick. Never manufacture an initial visual by secretly running directed AI.

The corrected native/entry batch ran 16 tests: 14 passed, zero failures, two fixture
errors, no skips (83 seconds execution, 116 seconds queue). All six S1/S2/S3K
player/object ROM routes passed. The S3K object case additionally runs the actual
shared facade: retain the Free prefix, cold-replay it, qualify 150 suspended visual
samples and retire the native world. UI five cases and protocol three cases passed.
The two new contract cases constructed their later set with the legacy five-arg
SetSource constructor; use explicit current TERRAIN_ONLY there. Only those two
checks are rerun; successful unchanged native coverage is retained.

The final contract-only lean rerun passed both cases, zero failures/errors/skips
(29.896 seconds execution, 67 seconds queue). Exact selector:
`TestStageplayRouteContract`. Frozen Phase 5 source is packaged next; no remaining
attributed focused failure. All native route cases used the three absolute ROM
paths above, and no ROM requirement skipped.

Capture qualification caught a genuine private-probe entry mismatch on the native
EHZ hill. The first goal (400,656) was below the actual floor centre (400,624) and
exhausted 120,000 ticks without publishing a candidate. Correcting the goal produced
a proposal but cold verification rejected it. A 300-tick private/native comparison
proved the cause: detached snap explicitly sets grounded, whereas current LIVE
entry sets airborne and uses injected ground attachment before the first native
tick. The proposed frame-30 jump executed privately but LIVE ignored that held edge
until landing. Final centres were private (403.625,624.137) and native
(321.281,633.004). A suspected terminal slope-drift explanation was rejected by
this first-frame evidence; no STOP termination or physics constants were changed.

Route probes now use a narrow native-world-entry factory with the existing LIVE
injected attachment policy. The detached factory and legacy behavior remain
unchanged. Add entry/rewind/controller-edge unit coverage, exact per-tick
private-versus-LIVE ROM comparisons and the actual shot-30 hill regression before
recapturing. Cold verification remains mandatory. The new HUD route picture also
uses the production offscreen boot's GL setup instead of a bare component context;
the desktop production host did not reproduce a component-only submit-prefix
artifact. Focused qualification is pending.

Entry-fix qualification ran 23 tests: 22 passed, one unit-fixture assumption failed,
zero errors/skips (90 seconds execution, 3 seconds queue). All five player ROM
checks passed, including the real hill route and exact 50-tick private/LIVE entry,
held-jump, release/repress position/velocity/air comparison. Existing detached
Puppet checks and the production-boot HUD capture passed. The unit expected landing
on the very first tick; native collision legitimately lands after several ticks.
Retain the no-invented-upward-impulse check, snapshot replay, bounded held-edge
landing and fresh repressed jump. Its exact-method lean rerun passed one test,
zero failures/errors/skips (29.676 seconds, no queue wait). Both HUD/desktop route
forms now show the complete submit label in production boot captures.

### Phase 5 final focused and visual qualification

Build-only packaging passed (45.857 seconds); tests were intentionally skipped.
The refreshed fat jar was installed into the owned Editor's private repository
(0.223 seconds, no queue), and final dependency compilation passed (1.018 seconds).
Desktop chrome/route-form qualification passed 17 tests, zero failures/errors/skips
(2.759 seconds, no queue), using `TestStageplayChrome,TestStageplayChromePictures#captureCollisionRouteAndVisualFootprintForms`
with `-Dopenggf.stageplay.desktopPictures=true`, EGL and the absolute S2 ROM.
The later engine-only probe-entry correction adds no frontend API or form change;
its native behavior is covered by the five ROM/player checks above.

Accepted capture: `$STAGEPLAY_EVIDENCE/phase5/take3/`. The actual CLI plans
`route --scene route-source.stageplay --actor hero --frame 30 --x 400 --y 624 --frames 300 --out routed.stageplay --s2-rom <absolute S2 ROM>`.
It reports VERIFIED after 65 nodes and 5,562 total simulated ticks. Fresh native
playback reaches (403.87890625,624.49609375), grounded and stable. The emitted
commands are ordinary MOVE_TO, a native six-frame JUMP at frame 42, and STOP at
148; anchors, modes, set and unrelated document data remain unchanged. Decompile
returns the routed scene to ordinary screenplay six. Simulation records all 330
frames independently before rendering.

The 5.5-second 640x224 film is 60 fps with 48-kHz stereo offline audio, a 30-frame
lead-in and more than 30 frames of settled lead-out. Both MKV and MP4 pass complete
FFmpeg `-xerror` decoding. Continuous normal and quarter-speed playback, eight
chronological samples and frame 15/60/120/210/315 PNGs were reviewed: native jump,
hill contact, braking, final idle, camera/background motion and caption exit are
visible. Normal playback displayed 300 callbacks; the visible-video quarter-speed
pass observed all 330. Preview payload/time limits were handled through on-page
samples and nonblocking playback; no desktop/window/input fallback was used.
Both frontends' production-boot route forms are in `phase5/{hud,desktop}/`.
The capture driver, typed result, routed canonical/screenplay, state JSONL, movie
metadata and source/jar fingerprints are retained beside the film.

No new candidate Mod API surface is exposed. The descriptor, candidate version and
signature pin remain unchanged in this phase. The main-player register/play shapes
and Phase 1 hand-back semantics remain unchanged. Final combined smoke is pending.


### Complete-cast phase commits and combined validation (2026-10-22)

Phase 5 is committed as engine `c0a4c2060f11affa645a273dbde2bc5e11fc9746`
and Editor `6c604e151e90310feae79875547b633a24d55d6f`. These follow
phase 0 `eb9b6138ccd182fd05cb6dee3c84d51cd15bdb44`, phase 1
`bb9e30002`, phase 2 `70f0e910d0ea7cab10a6c31371b293816ea1a0ff`,
phase 3 `74691157eb1a885f327efe158e68f409d0f76f3a`, and phase 4
`e544f1587c5e10359bb58ef486ac08857cd95e71`. Editor phase commits are
`bc8d61ecd02fc9a7f9fdee9c8bd7c932646294ee` plus `f64acb8`, `dcd54cf`,
`d446650`, and `a1899436ca4ceda4c7998d91047f331d01393f0f` respectively.
Both branches remain `feature/ai-stageplay-complete-cast`; no merge, push or PR.

Before the required once-only combined smoke, the change-based plan selected
3,614 ordinary candidate classes across all ten smoke categories, partitioned into
73 batches of at most 50, then guards. Expected execution was about two hours
from the recorded baseline, plus unknown queue time. The stopping bounds were
180 minutes per invocation and ten minutes without output. Preflight passed
Java 25, Lua 5.4 and PowerShell in the actual launch environment. Exact launch:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/run_categories.py --base 7d35d4a7b \
  --rom-directory /mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5 --max-minutes 180 --run
```

Run `20261022T173512Z-2da8e553` tested engine `c0a4c2060f11affa645a273dbde2bc5e11fc9746`
with working-tree fingerprint
`58f24f42855d7c2f63f58fd38de177e99145a44b7d27f1978bf117a4cd73ce5c`.
Preparation passed in 28.88 seconds. The runner records 44 completed batches,
but batch 44 crashed during its third class. Reported tests: **17,264 total,
17,239 passed, one failure, zero errors, 24 skipped**; aggregate execution
934.95 seconds. This excludes queue intervals and the crashed test's unreported
work. The JVM exited 134; the runner exited 1 with incomplete status, before
29 remaining ordinary batches and guards. It did not hit either timeout. This is
an incomplete smoke result, not a green delivery or a full-suite run.

The assertion failure is the exact recorded baseline case:
`TestHczMinibossNativeRendering.allOrbitPhasesMatchRomSpriteCompositionInNativePixels`,
line 53, GLFW initialization expected true/was false. All 24 skips were inspected:
18 are explicit capture/benchmark/soak/route opt-ins or isolation prerequisites;
six require unavailable graphics/visual environments. None is a missing-ROM skip.
The actual argv contained all three verified absolute original-game ROM paths
and the existing KIS2 ROM discovered by the runner. Root symlinks resolved to
read-only ROM paths in the main checkout; no main-checkout file was changed.

The new abort belongs to `TestFboReleaseFailure`, whose mock-only fixture covered
GL11 and GL30 but not the new GL15 unpack-buffer preservation used by `FboHelper`.
This leaves a real native call without a GL context and can terminate the JVM,
so it cannot appear in the JUnit failure totals. Extend the mock boundary to
GL15 and assert that nonzero caller draw/read framebuffer, texture, renderbuffer
and unpack-buffer bindings are restored even when FBO creation fails. The stale
expectation of binding framebuffer zero is replaced with the actual caller-state
contract. No production rendering change is required. Affected graphics checks
and the previously unreached guard lane are qualified separately; the broad
selection is not repeated after this red run.

Consumed diagnostics were removed with
`python3 tools/testing/run_categories.py --acknowledge 20261022T173512Z-2da8e553`.
No raw logs, XML or validation receipt were archived. Focused repair and guard
results follow below.

The later cross-thread unpublished-content ruling arrived after all strict
v1–v5 migrations were already implemented and committed under this brief. No
additional migration work is proposed. The lead can reconcile future policy;
this task retains its explicitly requested compatibility behavior. Candidate
0.7.0 metadata follows the earlier explicit hook ruling: descriptor/version stay
unchanged, the mutable pin changes only for actual additive surface. Phase 5
adds no candidate surface. Playback register/play signatures remain unchanged;
only the leased player result transfers on hand-back, not wholesale isolated
world mutations. The parallel level-editor trigger lane must preserve those
source/visit/lease checks.


Focused FBO-fixture repair qualification passed all ten tests, zero failures,
errors or skips (34.376 seconds execution, 155 seconds queue). Exact command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestFboReleaseFailure,TestStageplayWorldPresentation,TestStageplayDirectedBackgroundRendering' \
  '-Dsonic1.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5/Sonic The Hedgehog (W) (REV01) [!].gen' \
  '-Dsonic2.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5/Sonic The Hedgehog 2 (W) (REV01) [!].gen' \
  '-Ds3k.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5/Sonic and Knuckles & Sonic 3 (W) [!].gen' test -B
```

The independently queued guard lane selects 90 candidate classes. Expected cost
was the baseline's roughly 4.5 minutes plus unknown queue wait. Exact command:
`env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B`.
Results are pending. Remaining ordinary smoke coverage is explicitly incomplete;
the repaired fixture is not grounds to claim the unexecuted batches passed.


The separate guard lane completed 718 tests: **709 passed, nine failures,
zero errors/skips**, 4 minutes 45 seconds execution and 782 seconds queue.
The native-image resource guard now passes. Four failures exactly match the
recorded baseline: both `TestProductionSingletonClosureGuard` methods naming
`MenuAudioProbeTool.java:41`, and the two HubMusic provider/package ArchUnit rules.
Five failures are new and require correction, not baseline attribution:

- `TestObjectUpdateClockTerminologyGuard.objectUpdateClockUsesVIntRunCountTerminologyAcrossBoundaryAndFrameworkHooks`:
  four new object update arguments were called `clock` rather than `vIntRunCount`.
- `TestArchitecturalSourceGuard.objectManagerFacadeStaysWithinExtractedCollaboratorBudget`:
  3,327 effective lines exceeds the existing 3,086 budget.
- `TestArchUnitRules.low_level_layers_do_not_depend_on_runtime_layers`:
  two GraphicsManager SAT-control notifications called level-owned ObjectSpriteCapture.
- `TestArchUnitRules.core_runtime_cycle_cluster_does_not_gain_top_level_edges`:
  editor -> tools (the pure screenplay codecs) and stageplay -> util (the FBO helper).
- `TestArchUnitRules.stageplay_boundaries_have_explicit_owners`: the registry,
  donor, native-cast, worker, route and presentation adapters were absent from the
  original named-owner rule. This is an executable architecture contract, so
  every new adapter and its service boundary must be reviewed and named.

The correction keeps the size and frozen-violation budgets unchanged. Move the
existing stateful plane-switcher controller out of ObjectManager into a package
private collaborator, including capture/restore. Its body compares exactly after
class-name/deindent normalization and qualifying the unchanged shared spawn-order
function. That portable ordering moves to ObjectRewindOrdering for both snapshot
owners. Extra ensemble-window admission belongs to ObjectPlacementController;
retain the per-success bucket/cache invalidation at the manager callback. Ring
classification and suspended-routine filtering move to ObjectDirectionAccess.
These helpers remain outside annotated API types and add no candidate surface.

SAT-control observation becomes a graphics-owned scoped callback with exception
cleanup; ObjectSpriteCapture delegates to it. Graphics no longer depends on a
level capture renderer. The explicit Stageplay adapter allowlists name each owner
and its permitted slices; they never admit runtime -> editor/tools. The two new
top-level edges are explained and checked by exact owners, including only
StageplayWorkspace -> StageplayScriptCompiler/StageplayScriptDecompiler for the
pure screenplay codecs. Reject loosening whole packages, raising frozen counts,
or increasing ObjectManager's size budget.

The first affected qualification build (session 16826) stopped at compilation:
plane-switcher sorting still referenced the former outer private stableSpawnKey.
Zero tests ran (56.246 seconds execution, nine seconds queue). Extract its unchanged
implementation as the shared internal ObjectRewindOrdering function; rerun the
same 21-class focused selection, including all four mandatory S3K classes and
native route/render/rewind/API consumers. Current session: 23456. Results follow.


Final affected qualification (session 23456) completed **259 tests: 257 passed,
two failures, zero errors/skips**, 2 minutes 48 seconds execution, two seconds
queue. Both failures are the unchanged HubMusic provider/package ArchUnit cases
already recorded above. All five new guard failures are resolved; no baseline
budget was raised. All 72 architectural source checks pass, along with the
V-int terminology guard, the three repaired ArchUnit rules, cast/direction,
ensemble admission, plane-switcher/player isolation, object rewind, SAT capture,
all eight native route checks, both directed-background pixel checks, six world
presentation checks, 11 candidate API signature checks and all 60 mandatory S3K
cases. Exact command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestObjectUpdateClockTerminologyGuard,TestArchitecturalSourceGuard,TestArchUnitRules,TestObjectSpriteCapture,TestStageplayObjectDirection,TestStageplayActivationPolicy,TestStageplayObjectCast,TestPlaneSwitcherStateIsolation,TestSlotOwnedPlaneSwitcherDispatch,TestLevelManagerPlaneSwitcherDispatch,TestObjectManagerRewindSnapshot,TestStageplayPlayerRouteRom,TestStageplayObjectRouteRom,TestStageplayObjectClearingRom,TestStageplayWorldPresentation,TestStageplayDirectedBackgroundRendering,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface' \
  '-Dsonic1.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5/Sonic The Hedgehog (W) (REV01) [!].gen' \
  '-Dsonic2.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5/Sonic The Hedgehog 2 (W) (REV01) [!].gen' \
  '-Ds3k.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5/Sonic and Knuckles & Sonic 3 (W) [!].gen' test -B
```

Build-only packaging then passed in 1 minute 39 seconds:
`python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package -B`.
Tests were intentionally skipped. Only two stale owner comments changed after
the focused run; all executable code stayed frozen. The final fat jar was
installed in the owned Editor's private repository (0.215 seconds, zero queue)
with the absolute engine wrapper and `-Dmse=off install:install-file`,
`-Dfile=<engine worktree>/target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar`,
`-DgroupId=com.openggf -DartifactId=OpenGGF -Dversion=0.7.prerelease -Dpackaging=jar -DgeneratePom=true -B`.
Its dependency `compile -B` passed in 1.090 seconds, zero queue. There is no
frontend/API behavior change, so its already-passing UI checks are not repeated.

Final extraction evidence is in `$STAGEPLAY_EVIDENCE/final-qualification/`.
`compare_route.py` renders the accepted Phase 5 routed canonical document through
the packaged EGL host and simulates it afresh, then compares complete decoded
streams with the already moving-reviewed Phase 5 film. All **330 RGBA frames
(189,235,200 bytes)** and **1,056,000 PCM bytes** match exactly; all **330 parsed
state records** match. The initial raw JSONL byte comparison rejected object-key
order differences across JVM launches; no state value differs. Record both byte
inequality and semantic equality in `comparison.json`; do not hide the distinction.
The final still was inspected. Identical decoded footage retains the previous
normal/quarter-speed moving review, lead-in/out and contact/braking observations.
Capture, state, still, driver and production/jar fingerprints are preserved.
Production source SHA-256:
`3570b783f8980de472a71669308d587fe4f197539bddb2a25adbe9c329a3b7f5`;
fat jar SHA-256:
`fc99a34433f8f2c7db74d6191eb467fb963d8de6fb5c1aea036981678053c735`.

Final status: Phases 0–5 are committed with reviewed offscreen evidence; all
newly discovered focused/guard defects are repaired. The combined ordinary smoke
remains incomplete: part of batch 44 and all 29 later batches did not execute.
The independently completed guard lane retains four exact baseline failures;
the affected rerun retains only its two selected baseline ArchUnit cases.
Do not add overlapping run totals or claim a complete green smoke/full suite.
No merge, push, PR, main-checkout mutation or unknown Editor-file cleanup occurs.


### Stopping condition: candidate hook false positive needs lead decision

All six numbered phases remain committed. The additional verified ownership/test
corrections above are **staged, not committed**. The final commit attempt exited
1 because `.githooks/validate-policy.sh:296` reports:

> detectable @ModApi surface changes require updating the current candidate signature pin.

The exact normalized signature checks passed all 11 cases on the corrected
compiled production source; the candidate pin has no actual change. Running the
hook's read-only declaration extractor against HEAD and INDEX isolates its entire
detected difference to these two removed lines in ObjectManager:

```java
    static final class PlaneSwitchers {
        private static final class PlaneSwitcherState {
```

The parser includes non-public nested type declarations as possible API surface,
although this extraction changes no exported signature. Neither descriptor,
version nor pin was modified to satisfy this false positive. The hook scripts
were not edited or bypassed. All job sessions have ended; the owned Editor is
unchanged except for its refreshed private dependency and the preserved unknown
untracked config.yaml.example.

**Decision needed from the lead:** permit a bounded shell/PowerShell hook-parser
correction with regression tests for internal nested-type extraction (while still
rejecting public/protected API changes without the mutable pin), or choose a
different extraction. A fabricated pin change is rejected. This is a concrete,
fully staged implementation with focused/native/render qualification and final
capture parity, ready for review. Preserve its worktree/index until that decision.
The outside-repository STATUS.md records the commit list, tested state, exact
commands, results, evidence and this blocker before handback.


### Approved candidate-hook correction and qualification (2026-10-22)

The user approved the bounded hook correction. Commit
`9aec4bce0e12815a0548cea1a59c2fb8205a5e3c` excludes non-public/protected nested
type declarations and their bodies from class surface extraction in both shell
and PowerShell. Exported nested types, top-level declarations, annotations and
public/protected members remain detectable; non-class whole-text handling is
unchanged. No descriptor, version or signature pin changed. The original 18-file
staged correction diff remained byte-identical through the restricted hook
commit (SHA-256 `85ca7ab28b0814a7d38e10e8093dfc873653e431c3f9fc82e6ebcd223c0477f0`);
this entry is the only subsequent addition to that correction set.

Focused checks ran only in `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5`,
with `PATH="$HOME/.local/bin:$PATH"` and Maven
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`. PowerShell was found at
`$HOME/.local/bin/pwsh`; both hook variants actually executed.

- `python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestModApiHookPolicy,TestModApiSignatureSurface,TestBuildToolingGuard' test`:
  157 tests, 156 passed, one new hook regression failed, zero errors/skips.
  Signature surface passed 11/11 and build-tooling passed 123/123. The regression
  exposed visibility-prefix matching that discarded the space before `interface`;
  preserve an end-of-prefix visibility boundary in both implementations.
- `python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestModApiHookPolicy' test`:
  corrected hook passed 23/23; final rerun with all expanded exported-type cases
  compiled passed 23/23, zero failures/errors/skips (44.590 seconds execution,
  three seconds queue). Tests cover adding/removing private/package-private
  nested helpers, their public members/types, one-line bodies, brace literals,
  continuation declarations, and public/protected exported types/public methods.
- `python3 -m unittest discover -s tools/testing -p test_release_snapshot_policy.py`:
  final run passed 36/36, zero failures/errors/skips.
- `bash -n .githooks/validate-policy.sh` and PowerShell
  `System.Management.Automation.Language.Parser.ParseFile` syntax checks passed.
  Both hooks also accepted `commit-msg target/stageplay-correction-message.txt`
  against the actual staged correction set without a pin edit.

These bounded checks resolve the candidate-hook blocker; the earlier incomplete
combined smoke coverage and recorded baseline failures remain as documented.


## Review correction round 2 (2026-10-22)

The user rejected migration for all unpublished formats and the candidate API.
Step 0 landed the previously qualified staged correction as `d8e441fbf`, after
replacing the machine-local PowerShell path with `$HOME/.local/bin/pwsh`; no
other staged implementation changed. The existing hook correction remains
`9aec4bce0`.

### Verified findings and design decisions

- F1 / presentation 6,9: canonical v7 and screenplay v6 are the sole accepted
  shapes. Remove migrations, legacy policies, held/fixed zoom, roster/donor and
  single-window exceptions. `WorldPolicy` contains only `LIVE`; a cleared shot
  carries `CLEAR_OBJECTS`, which preserves separate ring ownership. All eight
  canonical scenes and seven screenplay examples are reauthored directly.
- F2–F5: graph admission rejects unaccepted children and prunes retired members;
  placement identity includes game/zone/act, lookup uses the placed-object owner,
  stale materialization fails and a native source change requires a new shot.
  Native holder release is explicit, before suspension/retirement, using native
  cleanup paths. No blanket onUnload with world side effects is introduced.
- F6–F11: admission/materialization/retirement and graph/capture/draw/rewind have
  collaborators; remove speculative catalogue observations and duplicate roles.
  LoadPolicy alone owns removed/clear state. Normal draws avoid capture lambdas
  and Stageplay service lookups. Cache stock catalogues/classification. Restore
  formatter-only changes/import ordering in shared files.
- The already-landed plane-switcher extraction remains: restoring its original
  nested block after extracting Stageplay behavior would leave about 3,198
  effective facade lines against the existing 3,086 limit. Do not compact source
  or raise the budget to hide this; native plane behavior remains the verified
  identical collaborator. Current facade remains below the unchanged budget.
- Camera interpolation follows framing centres; composition retains fractional
  origin, grows the framebuffer allocation, and crops by source extent/origin.
  The reviewed EHZ ensemble uses zoom .625, giving 359 source rows in a 224-high
  film. Tall backgrounds preserve the native logical 224-row picture and extend
  its edge, rather than reading another wrapped native plane. Native comparison
  and motion acceptance remain required below.
- Reject snapshotting initial camera bounds as an ICZ fix: Level bounds already
  are loaded ROM bounds; boss events change Camera locks, a different owner.
  Directed framing may extend beyond gameplay-origin bounds under the existing
  finite coordinate/zoom/raster limits. Native camera, physics, events and arena
  positions remain authoritative.
- The HUD cut-at-zoom wording in presentation item 4 was stale against the
  reviewed source: it already said pans and zoom animate. Legacy fixed-zoom
  language elsewhere did survive and is removed from both frontends.
- Object routing supports slopes and bounded 8-pixel steps via checked
  lift/cross/lower corners. Larger steps, gaps, walls and headroom failures are
  reported; no general planner or native-AI controller is claimed. A clear at
  shot entry precedes initial role admission, so the helper accepts that cue;
  later clear/removal boundaries remain unsupported.
- Retain content fingerprints: unchanged documents can point to changed ROM or
  authored-level bytes. Document equality alone cannot preserve the evidence
  underlying a verified native route. The worker isolation remains necessary
  because engine sessions have global ownership. Shipped workers use ordinary
  Java classpaths, with test launch behavior injected through the Launcher seam.
- F12 palette tests now compare captured ARGB values with authoritative native
  palettes, including nonblack values. Desktop picture tests remain opt-in and
  will be explicitly enabled for this round, not counted as default coverage.

The mutable candidate pin removes three WorldPolicy constants and adds the
native holder-release hook on ObjectInstance. The descriptor/runtime API were
reviewed: unpublished candidate 0.7.0, no published baseline, no independent
version increment, consistent with the user's engine-aligned version rule.

### Validation checkpoint

First `maven_queue.py -Dmse=off test-compile -B` failed main compilation with four
introduced extraction errors: three malformed fully qualified SpriteCaptureControl
references and an undefined CPZ frameCounter name. Zero tests ran; 58.538 seconds.
After those repairs, the second identical command passed main compilation and
failed test compilation only at three remaining TERRAIN_ONLY route fixtures.
Zero tests ran; 1 minute 22 seconds. Those fixtures are reauthored with explicit
clears. Native release audit extensions are not yet compiled or tested here.
No broad smoke or guards have been launched in this round yet.

Evidence root for this round is `$STAGEPLAY_EVIDENCE/round2/`. Capture acceptance,
exact focused selections/results, final commit IDs and complete smoke/guards
will be recorded after execution. Earlier historical results above are not
claimed as qualification of this changed state.


### Round-2 focused execution and ownership audit

The clean build exposed seven introduced compile errors (57.374 seconds, zero
tests): three hooks were nested in rewind adapters and the MHZ lift release
extraction retained instance-only completion calls. Hooks now belong to their
native objects; player-only lift release is separate from movement/story completion.
The following test-compile passed in 1 minute 21 seconds.

The first focused selection completed 783 tests:756 passed, 14 failures, 13 errors,
zero skips. Maven execution was 1 minute20 seconds after 561 seconds of measured
queue waiting. Selection: `TestStageplay*`, object capture/composition/FBO/mask/API
checks, the four required S3K checks, and adjacent intro/arrival/HPZ/capsule/CPU
rewind regressions. All three supplied ROMs used absolute original paths;
`-Dopenggf.headless.gl=surfaceless-egl`,
`-Dopenggf.stageplay.hudPictures=true`, and native-background evidence output were
explicit. All five HUD pictures and three native-background comparisons executed;
all four required S3K checks passed. This red run is development feedback, not
baseline attribution or final qualification.

Introduced failures include stale legacy menu/interval expectations, mock source
identity and construction context, admission/graph policy assumptions, contextual
badnik caching, palette authority, and changed native-world example/route behavior.
These are repaired with bounded affected checks; no unrelated native physics is
changed to satisfy a fixture. Current screenplay intervals may cross cuts within
film bounds; keys and point events remain shot-local. The old rejection at local
end 41 inside a 90-frame film is replaced by a true film-overrun rejection and an
explicit cross-cut acceptance assertion.

The same-camera BG-only stills under `$STAGEPLAY_EVIDENCE/round2/native-background/`
show the native ICZ pale bottom band. It is retained; treating that native band as
a projection defect was rejected. Native top 224 rows compare byte-for-byte for
GHZ/EHZ/ICZ; the next 160 rows extend the native bottom edge. The remaining 64 rows
of the lower comparator tile are outside the 384-row film and are not acceptance
coverage. Actual composed films still require motion review.

The extended holder audit covers acquired P1/P2, handoffs to surviving helpers,
and queued sidekick ending poses separately from already-applied control. A
queued pose can reacquire control on the next physics pass; clearing only current
player flags was rejected. The narrow CPU cancellation preserves unrelated CPU
state. Acquired references rewind through stable PlayerRefId extras. No interrupt
hook runs native completion transitions, camera/music/palette changes, explosions,
checkpoint writes or end-level signals. Native CNZ teleporter route completion
remains a pre-existing incomplete routine; this work only releases its owned
player state on interruption.


Air-traffic reauthoring preserves every native action, target, timing and wait.
The current Tails_Move_FlySwim routine (sonic3k.asm:27632-27674; loc_14860 /
loc_1488C) starts another flap only on pressed jump and applies native gravity;
releasing buttons does not cancel flight. The every-five-frame diagnostic finds
Tails airborne at 515 and grounded at 520 (landing bounded 516..520, not an exact
frame claim). RELEASE remains at 360. The unchanged native performance needs more
than 215 world pixels of vertical separation, which cannot fit the 184-pixel safe
area at 1x. The current demo uses a smooth 0.7x group view, returns to 1x after the
landing, and lasts 600 frames with the reply at 530..595. No fitted physics or
legacy controller schedule is restored. Both canonical/source examples agree.

Additional focused corrections inject the nullable activation policy directly from
ObjectManager instead of looking up services during ordinary setServices; the
negative regression asserts zero service calls. Badnik classification cache keys
include zone/act. Tests explicitly register admitted child ownership, snapshot the
single LoadPolicy owner, use real source identities, and compare palette values
at capture entry before the following native palette cycle. Cleared-world tests
permit only the exact native S1 ring class/ID and the directed graph, preserving
ring population and rejecting other free objects. Mock constructor/holder fixtures
now acquire the state they assert is released.


### Focused repair qualification

The affected 19-selector run completed 216 tests: 212 passed, four failures, zero
errors/skips. Maven execution was 1 minute33 seconds after 483 seconds queue wait.
The remaining failures separated two stale assertions (the pulley's native
release seam and inactive Ring observations) from two actual bugs. Inactive
badnik placement admission now applies CLEAR_BADNIKS through the same cached
registry classification as active admission. Group-follow zoom now computes
extra authored pan from framing-centre displacement, rather than adding its
zoom-dependent origin shift twice; follow smoothing preserves the previous
centre when scale changes.

The new native camera regression compares fixed and animated zoom at every tick,
with smoothing 1 and 0.2, identical native actor movement, and rewind/forward replay.
The final affected command selected `TestStageplayObjectDirection`,
`TestStageplayObjectRouteRom`, `TestStageplayLiveDemosRom`,
`TestStageplayObjectCastRom`, and `TestStageplayUnifiedWorldRom` via the Maven queue,
with surfaceless EGL, no DISPLAY/WAYLAND_DISPLAY/JAVA_TOOL_OPTIONS and all three
absolute original ROM paths. It passed 49 tests with zero failures/errors/skips;
Maven execution 1 minute26 seconds, queue waiting 271 seconds. Earlier successful
focused coverage is not rerun merely as a ceremony. Combined smoke, independent
guards, the desktop opt-in pictures and actual movie review remain required.


### Motion review correction: preserve the native pattern band

Engine correction `2dcd4dffa` and Editor parity `7cfd3b5` are committed. The engine
packaged with tests skipped in 39.069 seconds. The Editor used that fatjar/POM in
its private repository: 16 behavioral tests passed; all five desktop picture tests
passed explicitly enabled, zero skips. Initial picture failures exposed missing
fixture configuration (logical S2 ROM and explicit liveScene), not a production
fallback to preserve. The picture host now resolves native frames and asserts
world availability before drawing. Durable UI stills are under
`$STAGEPLAY_EVIDENCE/round2/{desktop-pictures,hud-pictures}/`.

Actual first-take playback at intended dimensions rejected the last-scanline
extension from `2dcd4dffa`: GHZ 400x384 no longer stacked the sky, but stretching one
water scanline over 160 rows produced long vertical stripes. This is why native
comparison/tests alone were insufficient for visual acceptance. Verified first-
take sources and movie hashes remain under `$STAGEPLAY_EVIDENCE/round2/first-take/`;
`ghz-tall/film.mp4` is the kill evidence. The accepted next implementation keeps
the first 224 native backdrop rows unchanged and repeats the bottom
`Pattern.PATTERN_HEIGHT` band (eight ROM scanlines) for extra rows. This preserves
the native texture instead of stretching individual pixels. Foreground height,
reverse-plane semantics and native state ownership remain unchanged; no zone test
selects the rule.

Queued focused validation selected `TestCinematicScrollPublication`,
`TestStageplayNativeBackgroundProjection`, and `TestStageplayDirectedBackgroundRendering`
with surfaceless EGL/all three original ROM paths. Nine tests passed, zero
failures/errors/skips; queue 559 seconds, Maven 1 minute 21 seconds. GHZ/EHZ/ICZ
comparators assert unchanged native top 224 rows and the correct bottom-eight-row
RGBA samples over all 160 additional film rows. The refreshed GHZ lower-band still
shows native water texture without the long columns. New movies still require
actual playback before acceptance.

The far-left AIZ bird shot was also rejected: simulation failed before rendering,
so the earlier claim of a simulation/render discrepancy was incorrect. Moving
the unplaced animal near the logged native start (5104,1030; camera 4944,912) made
the same packaged code complete simulation and capture its graph at Puppet 60 and
Pose 210. No animal implementation changed. Final captions identify real bird flap
cycles and are shortened to fit the film. Walking/state-owned animal frames keep
the documented native held fallback.


### Interrupted smoke repair resumed (2026-10-23)

The completed run `20261022T213349Z-cb3f5e38` tested `a60509304` against
integration base `7d35d4a7b`: all 73 ordinary batches completed, 37,825 tests,
4 failures, 2,369 errors and 97 skips; the guard lane completed 718 tests,
9 failures, zero errors/skips. Execution totals were 5,909.2 seconds ordinary
and 259.95 seconds guards; queue waiting was additional. The first 1,000
failure records were capped (1,373 omitted), so counts in that sample were
not treated as a complete distribution. The dominant error was missing player
identity context, not thousands of independent gameplay defects. After reading
results, skip reasons, bounded logs and guard details, diagnostics were deleted
with `run_categories.py --acknowledge 20261022T213349Z-cb3f5e38`.

The interrupted repair's focused job (PID 1768863) had completed: 264 tests,
5 failures, zero errors and one optional performance skip. Four remaining
ownership-restore failures and the final-reference audit exposed the same
incomplete repair: registering a `PlayerControlOwners` codec did not add the
final mutable helper to the default object-field capture policy. The helper is
captured in place by the central codec, using stable team IDs rather than raw
sprite pointers. Empty ownership needs no identity table. Context-aware manager
dispatch takes precedence over no-context wrappers at the nearest declaration;
legacy-only overrides retain their existing path. Added story-owner checks
exercise every variant and the surviving giant-ring flash handoff.

The inherited GameId correction tolerates an untyped test module without a game
ID and retains the existing test-source identity. The Tails body-pixel oracle
now transforms actor offsets by actual camera zoom before probing the film.
Neither correction changes native movement or rendering. Constructor injection
removes service lookup from sprite-capture construction, and Stageplay retirement
uses the existing lifecycle operation. No visual algorithm changes are planned
in this repair; the accepted round-2 films remain the visual evidence for the
unchanged presentation code. Qualification results and repair commit follow
below after execution.


### Final disposition of the original review findings

The accepted presentation evidence remains under
`$STAGEPLAY_EVIDENCE/round2/`.
The three original review documents remain in the sibling Claude task directory.
These dispositions describe the complete round (`d8e441fbf`, `2dcd4dffa`,
`a60509304` and the resumed repair), not only this repair diff.

| Finding | Disposition and evidence |
| --- | --- |
| F1 | Fixed: one LIVE world; current canonical v7/screenplay v6 only; timed clears express authored absence. Legacy formats and held zoom removed. |
| F2 | Fixed: retired/rejected graph members and strong references are pruned; graph rewind follows live membership. Direction/graph tests cover retirement. |
| F3 | Fixed: placed identities carry game/zone/act; source transitions require another shot; stale materialization is rejected. |
| F4 | Fixed: native player-only release precedes suspension/removal; acquired-player ownership, hidden-player handoffs and queued poses rewind by stable identities. Central codec/policy and holder/story/capsule tests cover the repair. |
| F5 | Fixed: placed-object map resolves placements; shared scoped identity replaces tuple scans over dynamic children. |
| F6 | Fixed: materialization/retirement, admission, graph, capture, draw and rewind have collaborators. Rejected undoing the verified verbatim plane-switcher extraction: restoring it gives 3,199 effective facade lines versus the unchanged 3,086 budget after the one-line source-identity repair. Current facade is 3,020; no budget increase or source compaction. |
| F7 | Fixed: speculative catalogue observations, duplicate role/version and dead forwarder/return removed. |
| F8 | Fixed: LoadPolicy alone owns persistent placement removal and clear state; authored visibility remains timeline state. |
| F9 | Fixed: observation guards precede capture lambda construction; nullable cached activation policy skips ordinary-gameplay service lookup. |
| F10 | Fixed: unrelated shared-file import/format churn restored; existing CRLF animal source preserved. |
| F11 | Fixed: stock catalogue and badnik classification cached. |
| F12 | Fixed: native ARGB arrays and nonblack palette colors asserted, not only part counts. |
| P3-1 | Fixed: interpolation uses framing centres; centred keys retain their centre between keys. |
| P3-2 | Fixed: fractional source origin survives raster composition/resampling; zoom movie watched at 1x and quarter speed. |
| P3-3 | Fixed: grow-only bucketed framebuffer; crop source extent/origin without per-frame recreation. |
| P3-4 | Fixed. The reviewed source did contain the quoted HUD cut-at-zoom help; the earlier rejection was incorrect. Help/status now describes animated zoom in both editors. |
| P3-5 | Fixed: no unpublished held-zoom compatibility branch. |
| P3-6 | Fixed: shared parallax/scroll formatter churn restored. |
| P3-7 | Fixed: DEZ native and directed projection share the planet silhouette/projection implementation. |
| P3-8 | Fixed: directed background uses LevelManager's native window-selection policy. |
| P4-1 | Fixed: sole current screenplay/canonical formats; no version ladder/migrations. |
| P4-2 | Fixed: one screenplay VERSION constant feeds tool help/results. |
| P5-1 | Fixed: spaces in user-facing limits and errors restored. |
| P5-2 | Fixed: ordinary Java classpath in production; tests inject launcher behavior. |
| P5-3 | Fixed bounded walking-route behavior/docs with slopes and steps <=8px; no general object pathfinder claimed. Rejected dropping content fingerprints: unchanged documents can reference changed ROM/level bytes, invalidating a verified route without any document change. |
| P5-4 | Fixed: route sources follow nearby four-space Java style. |
| Editor parity | Fixed: shared current-only runtime with thin desktop shell. Prior refreshed build passed 16 behavioral tests and five explicitly enabled pictures, zero skips; evidence desktop-pictures/ and hud-pictures/. No Editor edits needed for the central rewind repair; unknown config.yaml.example preserved. |
| Visual 1 | Fixed: native 224-row EHZ background preserved; extra height extends the native bottom pattern band. Native pixel comparison and ensemble 2.0-2.5s playback accepted. |
| Visual 2 | Fixed: GHZ 400x384 retains one sky and textured water. Rejected single-row clamping after stripes were visible in first-take/ghz-tall/film.mp4; bottom-eight-row band passes comparison and final playback. |
| Visual 3 | Fixed Free-mode ICZ framing so the boss stays visible. Rejected removing the pale-cyan band: same-camera native RGBA comparison proves it is native. |
| Visual 4 | Rejected further filtering changes: reviewer accepted the appearance; native-size zoom playback at 1x and quarter speed shows no alpha fringes; text remains crisp. |
| Visual 5 | Fixed native timer-driven bird cycles in S1/S2/S3K Puppet/Pose. State/physics-owned walking animation remains an explicitly labelled held fallback; general free-running state animation is deferred, not claimed. |
| Visual 6 | Fixed staging: Penguinator Puppet/Pose stays above/left of the cage; final s3k-badnik playback confirms visibility. |

Each of the nine evidence sets (zoom, ensemble, ghz-tall, icz-boss,
s1-animals, s2-animals, s3k-animals, s3k-badnik, air-traffic) contains films,
state and stills. `capture-source.json` pins the accepted a605 source/jar,
ROM and scene hashes. All final films previously reached ended at intended
320x224, 640x224 or 400x384. The repair changes rewind, lifecycle dispatch,
construction wiring and test projection; it does not change the accepted
rendering algorithm or staged takes. These captures establish those earlier
visuals, not exhaustive native physics or every possible object graph.


### Resumed repair qualification before broad smoke

The newly added null-reference regression first failed test compilation because
its assertNull import was missing (zero tests); the import was corrected. The
first 20-class run completed 295 tests, 1 failure, 2 errors, 1 optional skip:
the inherited five failures were gone, the null-player restore requirement was
reproduced both directly and through an inactive native helper, and the expanded
MHZ P2 fixture exposed its unregistered pre-existing object-parent reference.
The codec now returns null before consulting an identity table for a null ID.
The MHZ fixture registers the real parent in both capture and restore contexts;
it does not suppress or weaken the native object-reference contract.

Corrected focused command (all three original ROM paths were absolute under the
engine worktree, hashes matched the repository SHA-1 table):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=com.openggf.game.rewind.TestRewindInPlaceObjectRestore,com.openggf.game.rewind.schema.TestRewindPlayerReferenceCodecs,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.mutators.TestNativeMutatorRuntime,com.openggf.game.sonic3k.objects.TestStageplayCapsuleControlRelease,com.openggf.game.sonic3k.objects.TestStageplayIntroControlRelease,com.openggf.game.sonic3k.objects.TestStageplayLateHolderRelease,com.openggf.game.sonic3k.objects.TestStageplayStoryControlRelease,com.openggf.level.TestLevelLostRingSpawnCoordinator,com.openggf.level.TestLevelRingfallIntegration,com.openggf.level.TestMutatorStageAdmission,com.openggf.level.objects.TestObjectManagerLifecycle,com.openggf.level.objects.TestObjectRewindTypeSafetyDispatchPerformance,com.openggf.level.objects.TestStageplayObjectDirection,com.openggf.mods.code.TestNativeObjectFieldCallbacks,com.openggf.stageplay.runtime.TestStageplayLiveWorldRendering,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading' \
  '-Dsonic1.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5/Sonic The Hedgehog (W) (REV01) [!].gen' \
  '-Dsonic2.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5/Sonic The Hedgehog 2 (W) (REV01) [!].gen' \
  '-Ds3k.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5/Sonic and Knuckles & Sonic 3 (W) [!].gen' test -B
```

295 tests: 294 passed, zero failures/errors, one skip (the opt-in warm-dispatch
wall-clock measurement, not its behavioral assertions). Maven execution 35.637
seconds; queue wait 207 seconds. Required S3K AIZ skip, both level-loading
classes, bootstrap and decoding checks all executed. The five real live-world
rendering checks passed, including the Tails pixel assertion at fractional zoom.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestRewindArchitectureGuard,TestRewindCoverageGuard,TestObjectPhysicsStandardizationGuard,TestNoServicesInObjectConstructors' test -B
```

49 tests passed, zero failures/errors/skips; Maven execution 1 minute 32 seconds,
queue wait 94 seconds. These checks cover central-policy/codec preference,
per-object override triage, coverage gaps, lifecycle operations and construction
injection. The two retained custom paths are documented in guard triage: AIZ's
embedded post-defeat owner is not an SST member, and sanctuary already has a
bespoke native runtime snapshot. General holders use the central helper codec.

`run_categories.py --base 7d35d4a7b --preflight` passed in the same EGL/Java25/
Lua5.4 environment with `$HOME/.local/bin` on PATH for PowerShell. The change-based
plan selects 3,618 candidate classes, 73 ordinary batches plus the separate guard
lane. Expected execution is roughly two hours based on the previous complete
run; queue time is unknown. Stopping rule: let the one detached run complete,
inspect exact failures/skips and acknowledge diagnostics; any new or worsened
failure blocks handback as qualified. No full-suite selection is requested.
Doc/skill mirrors and `git diff --check` passed. No visual algorithm changed;
round-2 captures remain accepted with their original provenance.


### Completed once-only smoke and the callback-router correction

Run `20261023T022509Z-503104a2` completed on clean `0a13b84ab`, with the
requested `python3 -u tools/testing/run_categories.py --base 7d35d4a7b --run
--max-minutes 360` in the documented EGL/Java25/Lua5.4 environment. All 73
ordinary batches completed: **37,839 tests, 2 failures, 2,295 errors, 97 skips**,
5,968.31 seconds execution. The fresh guard lane completed **718 tests,
4 failures, zero errors/skips**, 269.79 seconds execution. Rounded queue waits
reported by the wrapper sum to 3,603 seconds, additional to execution; this is
not total delivery time. Preparation passed in 26.74 seconds.

All new guard regressions are cleared. The four guard failures exactly match
both MenuAudioProbeTool singleton methods at line 41 and both HubMusic
constructor/package ArchUnit rules. Ordinary HCZ native-pixel failure still
matches its GLFW initialization assertion at line 53. The remaining 2,293
identity-table errors occur in the HPZ ceremony, LRZ2 seven-chaos gateway,
sanctuary origin-return and pedestal lifecycle matrices. The first 1,000
failure records were capped (1,297 omitted overall), so the two later boot
dimension errors and extra audio CLI failure require bounded confirmation,
not attribution from matching counts alone. Skips include missing platform
opportunities, unavailable GLFW checks, explicitly opt-in pictures/measurements,
optional fixture jars and soak profiles; no missing required ROM was inferred
as a pass.

The first repair fixed the cached dispatcher but missed production's second
implementation in ObjectCallbackRouter. That router still detected any legacy
wrapper and invoked it without the manager's prepared identity context.
HPZSSEntryControlObjectInstance therefore lost the table even though its direct
context-aware tests passed. Capture and restore now delegate to the one cached
ObjectRewindTypeSafety resolver inside their existing callback call/run fault
boundaries; the duplicate reflection dispatcher is removed. A router-path
regression covers default snapshot state, legacy-only overrides, and explicit
context beside convenience wrappers, cold and warm. No per-object exception,
annotation, native routine, identity value or comparison fixture is added.

The failed broad snapshot is not a green qualification of this later change.
Following the repository's red-smoke policy and the task's once-only broad
request, qualification continues with all four entire affected production
classes, callback/ownership regressions and fresh guards. Environment-sensitive
cases are checked separately with the original grouped setup and a clean
isolated setup. Results follow after execution. No presentation algorithm or
forward-motion take changes in this correction.


### Final bounded qualification of the callback correction

The correction passed **2,591 tests, zero failures/errors, one skip** (the
explicitly opt-in dispatch timing measurement). All four entire affected native
classes executed with no skips: HPZ ceremony **285**, LRZ2 gateway **4**, sanctuary
origin return **580**, pedestal lifecycle **1,426**. These **2,295** cases include
all **2,293** previously failing sanctuary cases plus two gateway cases that
already passed. The first 20-class ownership/codec/object-manager/rendering
selection also reran, including required S3K checks, alongside the new actual
callback-router default/legacy/context test. Maven execution including lifecycle
setup was 10 minutes 46 seconds; this request waited 405 seconds.

Fresh guards on the same corrected source: **718 tests, four recorded baseline
failures, zero errors/skips**, Maven 6 minutes 44 seconds including recompilation,
zero seconds admission wait. Exact failures remain:

- TestProductionSingletonClosureGuard.productionCodeDoesNotUseForbiddenProcessSingletonsOutsideEngineServices,
  MenuAudioProbeTool.java:41 / AudioManager.getInstance.
- TestProductionSingletonClosureGuard.productionCodeOnlyUsesRawGetInstanceAtEngineServicesBootstrapBridge,
  the same MenuAudioProbeTool.java:41 singleton call.
- TestArchUnitRules.shared_code_does_not_construct_concrete_sonic_provider_classes,
  the same three HubMusic audio-profile constructor edges.
- TestArchUnitRules.shared_layers_do_not_depend_on_game_specific_packages,
  the same nine HubMusic provider/package edges.

The environment reproduction completed **18 tests, one failure, two errors,
zero skips**, Maven 27.227 seconds, request wait 1,093 seconds. Both
TraceCaptureBootDimensionsTest.realBootKeepsNativeGameplayCameraAndWidePresentationCaches
parameter rows fail with Unable to initialize GLFW at line 41 after
TestSurfacelessEglContext clears the backend property. The audio failure is
TestCompleteRunAudioCli.freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput
at line 182: the fresh child stderr must start with PRODUCER_UNAVAILABLE, but
JAVA_TOOL_OPTIONS prepends its launcher notice. These match the previously
recorded baseline identities, assertions and causes, not merely totals.

The clean isolated check, without that environment variable and with explicit
surfaceless EGL, passed **15 tests, zero failures/errors/skips** (two dimension
rows and all thirteen audio CLI tests), Maven 28.464 seconds, request wait
1,052 seconds. These per-request waits overlap; their sum is not delivery time.
No unrelated backend, singleton or hub policy was changed to make counts green.

Exact qualification command recipe (run from the authorized engine worktree;
the variables only abbreviate the literal arguments):

```bash
cd /mnt/LinuxData/T3/worktrees/OpenGGF/t3-d68f46c5
ENGINE_WORKTREE="$PWD"
TEST_ENV=(env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS -u MAVEN_ARGS -u JAVA_TOOL_OPTIONS
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 "PATH=$HOME/.local/bin:$PATH")
ROM_FLAGS=(
  "-Dsonic1.rom.path=$ENGINE_WORKTREE/Sonic The Hedgehog (W) (REV01) [!].gen"
  "-Dsonic2.rom.path=$ENGINE_WORKTREE/Sonic The Hedgehog 2 (W) (REV01) [!].gen"
  "-Ds3k.rom.path=$ENGINE_WORKTREE/Sonic and Knuckles & Sonic 3 (W) [!].gen"
  "-Dkis2.rom.path=$ENGINE_WORKTREE/kis2.gen"
)
FOCUS='com.openggf.game.rewind.TestRewindInPlaceObjectRestore,com.openggf.game.rewind.schema.TestRewindPlayerReferenceCodecs,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.mutators.TestNativeMutatorRuntime,com.openggf.game.sonic3k.objects.TestStageplayCapsuleControlRelease,com.openggf.game.sonic3k.objects.TestStageplayIntroControlRelease,com.openggf.game.sonic3k.objects.TestStageplayLateHolderRelease,com.openggf.game.sonic3k.objects.TestStageplayStoryControlRelease,com.openggf.level.TestLevelLostRingSpawnCoordinator,com.openggf.level.TestLevelRingfallIntegration,com.openggf.level.TestMutatorStageAdmission,com.openggf.level.objects.TestObjectManagerLifecycle,com.openggf.level.objects.TestObjectRewindTypeSafetyDispatchPerformance,com.openggf.level.objects.TestStageplayObjectDirection,com.openggf.mods.code.TestNativeObjectFieldCallbacks,com.openggf.stageplay.runtime.TestStageplayLiveWorldRendering,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.tests.TestS3kHpzSanctuaryLifecycleProduction,com.openggf.tests.TestS3kLrz2SevenChaosGiantRingGateway,com.openggf.tests.TestS3kSanctuaryOriginReturnLifecycle,com.openggf.tests.TestS3kSanctuaryPedestalLifecycle'
"${TEST_ENV[@]}" JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 -u tools/testing/maven_queue.py -Dmse=off "-Dtest=$FOCUS" "${ROM_FLAGS[@]}" test -B
"${TEST_ENV[@]}" python3 -u tools/testing/maven_queue.py -Dmse=off -Pguards test -B
"${TEST_ENV[@]}" JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 -u tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=com.openggf.tools.TestSurfacelessEglContext,com.openggf.tools.TraceCaptureBootDimensionsTest,com.openggf.tools.audio.completerun.TestCompleteRunAudioCli' "${ROM_FLAGS[@]}" test -B
"${TEST_ENV[@]}" python3 -u tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=com.openggf.tools.TraceCaptureBootDimensionsTest,com.openggf.tools.audio.completerun.TestCompleteRunAudioCli' "${ROM_FLAGS[@]}" test -B
```

The broad run is retained as a red historical snapshot at 0a13b84ab. It was not
repeated after this small correction, honoring the requested single broad run
and repository policy to verify red-smoke repairs narrowly. The final corrected
candidate has no remaining newly failing affected checks; this is bounded
qualification, not a fully green re-run of all smoke and not a release full-suite
pass. Both category diagnostic directories were acknowledged and deleted.
The final source/record commit IDs are recorded below and in the task STATUS.
No forward-rendering algorithm changed; the prior a605 round-2 films retain
their original source provenance. Rewind ownership restoration is established
by the production lifecycle matrices, not inferred from those earlier films.


### Source and handback record

First resumed repair: `0a13b84ab` (central codec/policy, null reference, source
identity and test/guard corrections). Final production-router correction:
`cc254dabfce972811b3d302266dbf95b39f3ee49`. The final 2,591-test focus, 15-test clean environment check and fresh
718-test guards above exercised the same two Java source changes committed in
that correction; only documentation was added after those checks. The earlier
round commits remain `d8e441fbf`, `2dcd4dffa` and `a60509304`, with hook correction
`9aec4bce0` retained. No merge, push or pull request was performed.

The instruction/skill mirrors, diff whitespace, added evidence link/anchor and
qualification recipe's Bash syntax were checked. The prerelease changelog folds
ownership into its existing Stageplay entry; no public API, configuration key,
format, native routine or version changed in this repair. The task worktree is
retained for its lead's integration. Editor `7cfd3b5` and its unknown untracked
configuration example remain untouched. Consumed category diagnostics were
acknowledged; temporary logs/messages from the resumed commands are discarded
after their results are recorded here. The final outside-repository STATUS
contains the exact final HEAD and remaining limits.

### Interrupted round-3 continuation (2026-10-23)

N1: evaluated native-envelope framing covers authored keys, follow, shake and paused
drafts without rewriting keys; both frontends share the resolved preview. N4:
the one-value WorldPolicy field is removed directly from unpublished formats and
the candidate API pin, with examples updated and no migration. P4-2: scaffolds use
the screenplay version constant. Focused validation and later holder audit follow.

N2 acquisition audit: parse Java method calls by lexical class scope (including nested
P2/ending helpers), then resolve inherited hooks against compiled ObjectInstance
classes. Scan control locks, object-control states, movement suppression, forced input
and controller-2 writes. The guard rejects a writer with only the interface no-op.
Non-object helper acquisition paths were followed to their object callers (AIZ vine
logic, boss signpost flow and AIZ end sequence); their owning hooks already delegate
release. Added exact acquired-identity tracking to 42 owners, including 8 nested
owners, and native interaction-release hooks for flippers, turbine, magnet and loop.
The S1 prison regression exercises direction, removal and CLEAR_OBJECTS after its
actual forced-right acquisition. Native release tails relinquish tracked identities;
rewind restores them without relying on a newly focused player. N6 remains a separate
native-state preservation pass; the complete audited class list is recorded below
with the final guards result.

Focused checks at this slice: original holder/signpost/FBZ group 155/155; acquired
identity/rewind + interaction + direction group 74/74; source guard 2/2, no skips.
The new acquisition harness exposed a real S2 main-ending gap: the existing release
method was gated by sidekick membership. Extracting its native input release tail
fixed main-player release too. Parent references in the rewind fixture must be
registered independently; the first harness version omitted that registration.

N6: release hooks preserve native routine phases/timers/positions. Removed directed-only
resets from the crane/spider/pendulum, HPZ entry, CNZ cannon/teleporter, SSZ teleporter,
OOZ launcher and AIZ2 capsule. S1 junction/teleporter now share the native player-only
tail with cancellation while preserving waypoint/routine progress on direction.
Acquisition references/latches clear, and existing ROM release cooldowns remain in
their native release paths (for example loc_4436E turbine drop and loc_4A4F0 magnet
release); neither respawning nor resetting an object implements visual suspension.
The 42 acquired-owner cases compare native scalar state before/after release; direct
regressions also cover crane/teleporter phases, cannon/HPZ and capsule progress.
The misleading AIZ/ICZ ownedPlayer transient annotations are removed: exact CAPTURED
policies now agree with reflective fallback instead of silently depending on compact
schema precedence. Inline release/rewind qualifiers use ordinary imports.

Cleanup: portable source identity, ring classification, suppression and activation
admission now live in ObjectStageplayController (ObjectManager sheds 25 net lines).
Absent game identity is rejected when a portable placement is requested; ordinary
untyped managers still construct. The obsolete alternate PlayablePuppet factory and
128-pixel detached floor search are removed, leaving the native-world route probe.
Tests now use its actual entry and ordinary landing. Focused combined checks pass
112/112 with no skips. Old assertions for the removed detached entry and a stale
internal manager-field assertion were corrected; no production branch was retained
for those tests. Remaining verification: ROM route/preview, mandatory S3K and guards.

#### Complete round-3 holder audit class list

The AST guard checks these 138 direct acquisition-writer classes (nested
classes are separate; inherited hooks resolve through reflection).

- sonic1: `objects.Sonic1EggPrisonObjectInstance`, `objects.Sonic1JunctionObjectInstance`, `objects.Sonic1PoleThatBreaksObjectInstance`, `objects.Sonic1RingFlashObjectInstance`, `objects.Sonic1SignpostObjectInstance`, `objects.Sonic1TeleporterObjectInstance`, `objects.bosses.Sonic1FZBossInstance`.
- sonic2: `objects.BreakablePlatingObjectInstance`, `objects.CPZSpinTubeObjectInstance`, `objects.FlipperObjectInstance`, `objects.GrabObjectInstance`, `objects.LauncherBallObjectInstance`, `objects.LauncherSpringObjectInstance`, `objects.MTZSpinTubeObjectInstance`, `objects.MovingVineObjectInstance`, `objects.OOZLauncherObjectInstance`, `objects.OOZPoppingPlatformObjectInstance`, `objects.PointPokeyObjectInstance`, `objects.TornadoObjectInstance`, `objects.VineSwitchObjectInstance`, `objects.badniks.GrabberBadnikInstance`, `objects.bosses.Sonic2DeathEggRobotInstance`.
- sonic3k: `ending.EndingKnucklesFlightObjectInstance`, `ending.EndingRescueDirectorObjectInstance`, `objects.AbstractS3kFloatingEndEggCapsuleInstance`, `objects.AbstractS3kUprightEggCapsuleInstance`, `objects.Aiz2BossEndSequenceController`, `objects.Aiz2EndEggCapsuleInstance`, `objects.AizHollowTreeObjectInstance`, `objects.AizPlaneIntroInstance`, `objects.AutomaticTunnelObjectInstance`, `objects.CnzCannonInstance`, `objects.CnzCylinderInstance`, `objects.CnzSpiralTubeInstance`, `objects.CnzTeleporterInstance`, `objects.CnzWireCageObjectInstance`, `objects.CutsceneKnucklesCnz2BInstance`, `objects.CutsceneKnucklesLbz1RangeHelper`, `objects.CutsceneKnucklesMhz2Instance$Mhz2KnucklesLiftChild`, `objects.CutsceneKnucklesMhz2Instance`, `objects.CutsceneKnucklesSkIntroInstance`, `objects.DdzEndBossObjectInstance`, `objects.DdzFlightControllerObjectInstance`, `objects.DezFinalBossController`, `objects.DezMinibossTransport$P2Hold`, `objects.DezMinibossTransport`, `objects.FbzChainLinkObjectInstance`, `objects.FbzEndBossInstance`, `objects.FbzEndEggCapsuleInstance`, `objects.FbzMagneticPendulumObjectInstance`, `objects.FbzSpiderCraneObjectInstance`, `objects.FbzSpinningPoleObjectInstance`, `objects.FbzWireCageObjectInstance`, `objects.FbzWireCageStationaryObjectInstance`, `objects.HCZBreakableBarObjectInstance`, `objects.HCZConveyorBeltObjectInstance`, `objects.HCZHandLauncherObjectInstance`, `objects.HCZSpinningColumnObjectInstance`, `objects.HCZTwistingLoopObjectInstance`, `objects.HCZWaterWallObjectInstance`, `objects.HPZSSEntryControlObjectInstance`, `objects.HPZSuperEmeraldObjectInstance`, `objects.HczMinibossInstance`, `objects.HpzEmeraldTheftPlayerControlObjectInstance`, `objects.HpzTeleporterRouteHelperObjectInstance`, `objects.IczFreezerObjectInstance$CaptureCloud`, `objects.IczFreezerObjectInstance$FrozenPlayerBlock`, `objects.IczSnowboardIntroInstance`, `objects.Lbz1GroundLaunchIntroInstance`, `objects.Lbz2RobotnikShipInstance`, `objects.LbzCupElevatorInstance`, `objects.LbzLoweringGrappleObjectInstance`, `objects.LbzRideGrappleInstance`, `objects.LbzTubeElevatorInstance`, `objects.LevelIntroPlayerRunInstance`, `objects.LrzAutoscrollObjectInstance`, `objects.LrzBoulderCutsceneObjectInstance`, `objects.LrzCorkscrewObjectInstance`, `objects.LrzCutsceneBoulderObjectInstance`, `objects.LrzTurbineSpritesObjectInstance`, `objects.LrzWallRideObjectInstance`, `objects.MGZPulleyObjectInstance`, `objects.MGZTopPlatformObjectInstance`, `objects.MGZTwistingLoopObjectInstance`, `objects.MgzEndBossKnuxInstance`, `objects.Mhz1CutsceneKnucklesInstance$Mhz1CutscenePlayerTwoStopper`, `objects.Mhz1CutsceneKnucklesInstance`, `objects.MhzMushroomParachuteObjectInstance`, `objects.MhzPulleyLiftObjectInstance`, `objects.MhzSwingBarHorizontalObjectInstance`, `objects.MhzSwingBarVerticalObjectInstance`, `objects.MhzSwingVineObjectInstance`, `objects.PachinkoEnergyTrapObjectInstance`, `objects.PachinkoFlipperObjectInstance`, `objects.PachinkoMagnetOrbObjectInstance`, `objects.S3kDezGravityHubObjectInstance`, `objects.S3kDezGravityRoomObjectInstance`, `objects.S3kDezGravityTubeObjectInstance`, `objects.S3kDezHangCarrierObjectInstance`, `objects.S3kDezTeleporterObjectInstance`, `objects.S3kDezTunnelLauncherObjectInstance$Controller`, `objects.S3kDezTunnelLauncherObjectInstance`, `objects.S3kNativeP2LockInstance`, `objects.S3kSignpostInstance`, `objects.S3kSlotBonusCageObjectInstance`, `objects.SSZHPZTeleporterObjectInstance`, `objects.Sonic3kSSEntryRingObjectInstance`, `objects.SozEndBossChild`, `objects.SozEndBossInstance`, `objects.SozFallingIntroInstance`, `objects.SozLightSwitchObjectInstance`, `objects.SozLoopFallthroughObjectInstance`, `objects.SozMinibossChild$Alignment`, `objects.SozMinibossChild$P2Hold`, `objects.SozRapelWireObjectInstance$Segment`, `objects.SszArrivalControllerObjectInstance`, `objects.SszCraneClaw`, `objects.SszElevatorBarObjectInstance`, `objects.SszLaunchControllerObjectInstance`, `objects.SszRotatingPlatformCarrierObjectInstance`, `objects.SszRotatingPlatformObjectInstance`, `objects.SszSwingingCarrierBarObjectInstance`, `objects.badniks.MadmoleBadnikInstance$SideDrillChild`, `objects.bosses.CnzEndBossInstance`, `objects.bosses.HczEndBossEggCapsuleInstance$HczEndBossResultsScreenObjectInstance`, `objects.bosses.HczEndBossEggCapsuleInstance`, `objects.bosses.HczEndBossGeyserCutscene`, `objects.bosses.HczEndBossWaterColumn`, `objects.bosses.IczEndBossInstance`, `objects.bosses.LbzFinalBoss1Instance`, `objects.bosses.LbzFinalBoss2Instance$GrabOwnerChild`, `objects.bosses.LbzFinalBoss2Instance`, `objects.bosses.MhzEndBossInstance$MhzEndBossSidekickLockChild`, `objects.bosses.MhzEndBossInstance$MhzEndBossWalkoffPrepChild`, `objects.bosses.MhzEndBossInstance$MhzEndBossPlayerTwoCarryChild`, `objects.bosses.MhzEndBossInstance`, `objects.bosses.SszMechaSonicActEndObjectInstance`, `objects.bosses.SszMechaSonicObjectInstance`.

Additional hook owners/helper paths checked for inherited behavior, native state
preservation, constructor handoff or per-player support ownership:

- sonic3k: `objects.AizEndBossInstance`, `objects.AizGiantRideVineObjectInstance`, `objects.AizRideVineObjectInstance`, `objects.CnzBarberPoleObjectInstance`, `objects.Sonic3kSSEntryFlashObjectInstance`, `objects.SszCranePlayerRelease`, `objects.SszTailsArrivalHelperObjectInstance`.

AIZ vine acquisition is implemented by `AizVineHandleLogic` and released by its
ride/giant-vine owners. `S3kBossDefeatSignpostFlow` and the shared signpost pose
helpers were checked alongside their caller hooks; queued native P2 poses remain
owned by the upright/HCZ capsule paths. This is a source/hook audit plus focused
regressions, not an exhaustive native route or ROM-parity certification.

#### Round-3 final dispositions and bounded verification

| Finding | Disposition |
| --- | --- |
| N1 | Fixed: evaluate authored/follow/shake/draft cameras inside the loaded native envelope, accounting for viewport/zoom; keys stay authored. Both editor picture paths show the resolved view. Animals re-shot and reviewed in playback at resolved Y=768, authored Y=832. |
| N2 / F4 | Fixed across the audited holder set: native acquisition writers, nested/P2/ending helpers and shared acquisition helpers have owned player-release paths; new structural guard passes. Complete class lists above. |
| N3 / F9 | Fixed in 482916ad1: native service reinjection keeps the admitting activation policy; remote visibility/culling/retirement regression passes. |
| N6 | Fixed in ee17589b1: native routine/transport progress survives direction; acquired-player state and existing ROM release cooldowns remain separate. |
| WorldPolicy / API | Removed directly in a070bbbc1 from unpublished formats, editor field, examples, compiler/decompiler and candidate pin; no migration. Signature checks pass. |
| Probe / source identity / duplicate check | Obsolete detached probe factory/floor search removed; route probes use native entry. Missing source game identity rejects portable placements. Duplicate source check removed; placement policy moved out of the manager facade. |
| N5 / style / scaffold | Misplaced Javadocs repaired, release/rewind qualifiers use imports, the warning-string rewrap is reverted, and scaffold version uses the compiler constant. |
| P3-4 | The earlier rejection rationale was false; the original disposition row is corrected to Fixed. |

Production source is pinned to `507056604eceaed3afbb5dc45ec00a0aae4dbb19`.
The subsequent commit adds only the corrected native-entry ROM discovery fixture
and this review/measurement record. Editor source is `01bf8ae`.
No merge, push or PR was performed; the lead owns broader smoke/integration.

All Maven invocations used `python3 tools/testing/maven_queue.py -Dmse=off`
from this engine worktree (the Editor used its absolute path). ROM invocations
passed the existing absolute S1 REV01, S2 REV01 and S3K locked-on paths in this
worktree; no ROM files/links were created or changed. Rendering used
`-Dopenggf.headless.gl=surfaceless-egl`; opt-in picture flags were explicit.

| Exact focused selector / command suffix | Result |
| --- | --- |
| `-Dtest=TestStageplayDocument,TestStageplayStore,TestStageplayUnifiedWorldContract,TestStageplayScriptRoundTrip,TestStageplayLiveContract,TestStageplayObjectScriptCompiler,TestStageplayWorkspace,TestStageplayDirectingWorkspace,TestStageplayObjectWorkspace,TestStageplayCameraBounds,TestStageplayTimeline,TestStageplayObjectDocument,TestStageplayLiveBinding,TestStageplayObjectCast,TestStageplayRouteContract test -B` | 136, 0F/0E/0S |
| `-Dtest=TestStageplayActivationPolicy test -B` | 13, 0F/0E/0S |
| `-Dtest=TestStageplayIntroControlRelease,TestStageplayStoryControlRelease,TestStageplayCapsuleControlRelease,TestStageplayLateHolderRelease,TestS3kSignpostInstance,TestFbzMinibossChildren test -B` | 155, 0F/0E/0S |
| `-Dtest=TestStageplayAcquiredControlRelease,TestStageplayInteractionRelease,TestStageplayObjectDirection test -B` | 74, 0F/0E/0S |
| `-Dtest=TestStageplayAcquiredControlRelease,TestStageplayLateHolderRelease,TestStageplayIntroControlRelease,TestStageplayCapsuleControlRelease,TestStageplayObjectDirection test -B` | 126, 0F/0E/0S after native-state changes |
| `-Dtest=TestPlayablePuppet,TestStageplayNativeStateRelease,TestStageplayObjectDirection,TestStageplayObjectCast,TestStageplayObjectRingProvenance,TestStageplayActivationPolicy,TestObjectManagerLifecycle,TestStageplayRoutePlanner,TestStageplayLateHolderRelease test -B` | 112, 0F/0E/0S on final cleanup |
| `-Dtest=TestPlayablePuppetRom,TestStageplayPlayerRouteRom,TestStageplayObjectRouteRom,TestStageplayLivePreviewRom,TestStageplayLiveWorldRom,TestStageplayS1EnsembleRom,TestStageplayHudPictures#captureStageplayCastTimelineAndCommands,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface test -B` | 94, 1F/0E/0S: native-entry discovery still assumed detached snapping; all mandatory S3K tests, signature checks and picture capture passed. |
| `-Dtest=TestStageplayPlayerRouteRom test -B` | Corrected shared discovery fixture: 5, 0F/0E/0S across all games. |
| `-Pguards test -B` (once; Java 25, Lua 5.4, displays unset) | 720, 4F/0E/0S; exact recorded baseline identities below. New holder guard 2/2 passes. Execution 5:23, admission wait 0s. |
| Editor: `-Dtest=TestStageplayChrome,TestStageplayChromePictures#captureActualDesktopStageplayWithRomSetAndActors test -B` | 18, 0F/0E/0S with current engine privately installed and explicit desktop picture flag. |
| `-DskipTests package -B` | Build succeeds; no tests run. |

Earlier ROM preview/route group: 41 tests, zero failures/errors, five opt-in HUD
refresh skips. The actual bounded HUD capture was enabled in the final ROM run;
no required ROM test skipped. Counts above overlap and are per completed invocation,
not a unique-test total. An initial tool preflight selected a wrong default Lua;
explicit `LUA_BIN=/usr/bin/lua5.4` passed, with no tests launched by preflight.

The four guard failures exactly match the baseline identities/assertions already
recorded in this plan: both `TestProductionSingletonClosureGuard` methods at
`MenuAudioProbeTool.java:41`, plus the three HubMusic construction edges and nine
HubMusic package edges in the two `TestArchUnitRules` methods. No new guard failure,
error or skip. This is bounded validation with known baseline failures, not a green
full-suite or a new broad smoke result.

Evidence root: `$STAGEPLAY_EVIDENCE/round3/` (the external task capture directory).
`capture-source.json` pins production SHA, source/scene/JAR fingerprints and ROM
identities; `capture_round3.py` records reproducible commands. `s1-animals/film.mkv`
is 450 frames / 7.5s, 320x224 at 60 fps, FFV1 + 48 kHz stereo FLAC. Both MKV and MP4
passed complete `ffmpeg -xerror` decoding. All sampled state cameras are `(0,768,1)`;
the stored scene key remains Y=832. Browser playback completed at rate 1 with
1350 decoded frames across three plays and zero reported dropped frames. Reviewed
the Free/Puppet/Pose/flip and clear beats; native bird flap remains animated, and
the accepted held walking/state-machine appearance limitation remains. The review
browser and local HTTP server were closed. `editors/hud-camera-{720p,small}.png`
and `editors/desktop-camera-{720p,small}.png` show actual bounded paused drafts;
the desktop inspector shows resolved position `(120,916)` at zoom 2 rather than
the requested 100000-pixel pan. Authored keys remain unchanged in both checks.

The optional global `disasm_citations.py check` reports the repository's existing
citation backlog: 3370 stale, 122 missing-file, 35 out-of-range, 179 unresolved-file,
plus historic/unverifiable references. This round introduces no new line-numbered
disassembly citations and moves no reference pins; those unrelated citations were
not rewritten. Changed-document whitespace, skill mirrors and API signature checks
pass. No category run directory was created (preflight only); consumed task Maven
diagnostics are removed before handback.

## Stageplay route viewport overlay qualification (2026-10-23)

Engine base `657bbae86`, implementation `e4e4023d1`; desktop base `01bf8ae`,
implementation `4c4ec6d`. The engine qualification changes are committed with this
entry. Both task branches are local `feature/ai-stageplay-route-overlay` worktrees;
no integration, push or PR was authorized. The lead owns combined smoke coverage.

`StageplayRouteOverlay` is an unannotated, transient authoring model, consumed by
`StageplayWorkspace`, the engine HUD and the desktop ImGui viewport. Shared geometry
uses the evaluated camera and fitted film rectangle. Ground/air colours, takeoff
chevrons, start square, separate goal diamond and failed endpoint cross keep constant
UI sizes. Applied plans stay visible for the selected performer in the same shot;
player playback adds a white actual-centre cross. Changes to the accepted document
or asset root hide the old plan. Reopening a scene does not restore overlays.
Walking objects use this same model and are selectable in both cast lists.

Successful lines use cold replay samples. Failed native searches retain an observed
best branch with every tick, while visual object failures simulate only the checked
prefix through the existing movement kernel. Cold qualification failures retain
observations up to rejection. Missing frames break the line. Rejected approaches:
connecting sparse search-node endpoints, drawing a destination chord, fitting an
arc, persisting route data in a scene, advancing preview from painting, or treating
budget exhaustion as proof of an obstruction. A failure before observations has no
invented start/stop. No gameplay values are hydrated from these comparison samples.

Focused queued checks (all zero errors/skips):

- Initial model/workspace/controller/player-search/object-search/protocol selection:
  36 tests passed. Final overlay/workspace/explicit engine-picture selection:
  13 passed, including walking-object proposal/apply/selection lifetime.
- Final engine and desktop picture refreshes: one test each passed. Initial desktop
  picture plus chrome selection: 18 passed. Both picture tests use the production
  route worker, real native preview, real frontend drawing and an opaque film redraw
  assertion proving that frontend route pixels cannot enter the film surface.
- Mandatory S3K selector (`TestS3kAiz1SkipHeadless`, both matching
  `TestSonic3kLevelLoading` classes, `TestSonic3kBootstrapResolver`,
  `TestSonic3kDecodingUtils`): 60 passed against implementation `e4e4023d1` plus
  the initial picture fixture. Later production edits only shorten overlay text.
- Once-only `-Pguards test -B`: 720 tests, five failures, no errors/skips. One was
  owned: the explicit Stageplay-consumer registry needed the new model. It now
  names the two frontend owners and prohibits runtime/export dependencies on the
  overlay. The bounded `TestArchUnitRules` recheck executes 32 tests with two
  unchanged HubMusicController failures; Stageplay ownership passes.
- Baseline attribution: the two singleton source checks reproduce exactly against
  archived `657bbae86` production sources (`MenuAudioProbeTool.java:41`). A separate
  compilation of all 4,252 original baseline production classes under this tree's
  temporary target directory, with the same current ArchUnit assertions, reproduces
  the two HubMusicController rules exactly: three provider constructors at line 118
  and nine provider/music dependencies at lines 118/120. Current and baseline
  assertion messages and test identities match. These four inherited failures are
  preserved; the guard suite is not described as green. No broad rerun was made.

Preflight passes with Java 25, `LUA_BIN=/usr/bin/lua5.4` and PowerShell. The default
Lua resolves to an older version and was rejected before tests. Supplied absolute
S1/S2/S3K ROM CRC32/SHA-1 identities match the repository table. Both engine packages
built with tests skipped; the desktop private dependency repository contains this
worktree's final engine jar. Main checkouts and the lead's smoke tree were untouched.

Evidence lives in the external task directory (`$STAGEPLAY_ROUTE_OVERLAY_EVIDENCE`):
`engine-route-overlay.mp4`, `desktop-route-overlay.mp4`, and each frontend's
`*-proposed.png`, `*-failed.png`, `*-applied-jump.png`, `*-applied-arrived.png`,
`*-pan-zoom.png`. Each silent clip is H.264, 1280x720, 60 fps, 480 frames/eight seconds;
FFprobe and complete `ffmpeg -xerror` decoding pass. Both final clips played to the
end at 1x in an isolated browser, with sequential observations of native jump,
landing, arrival, failure and camera pan/zoom. Each proposal has 300 actual replay
samples and 12 commands, including jumps; each failed budget attempt has 49 samples.
The first capture's final zoom placed its failure marker under the legend; it was
rejected and reframed with a visible-header-clearance assertion. The final clips
keep the cross clear and the route legible over busy ROM art. Browser/server and
worker cleanup completed. Commands, exact final commits and limits are in external
`STATUS.md`; temporary baseline sources/builds and consumed diagnostics are removed.

Limits: this is bounded editor/model/ROM evidence, not broad smoke or release
validation. Object paths retain visual walking limits with native AI suspended;
only players get an actual-centre marker, not a second recorded movement trail.
A retained applied line references the accepted attempt; external ROM/assets still
require replanning, and it is not a certification of arbitrary future world changes.


### Route overlay presentation polish (2026-10-23)

Follow-up to engine `e5a438131` and desktop `4c4ec6d`: review rejected the
viewport legend because it obscured the scene, clipped at the desktop top edge
and truncated failure prose. The painter now exposes only strokes. State,
colour/marker keys and explanations live in the HUD workbench and desktop
Direction inspector; short status lines use ordinary language. HUD explanations
wrap on spaces, and desktop explanations use the inspector's normal wrapping.
The original result/protocol diagnostics remain intact for research. Budget
exhaustion still explicitly leaves reachability unknown.

Geometry, dark outlines and fixed screen-space stroke weights are unchanged.
A draw-on animation was considered and omitted: the complete proposal is easier
to review immediately, and native actor playback already supplies useful motion.
The proposal, failed prefix and accepted playback remain transient editor state.

The explicitly enabled engine picture/model/workspace/HUD selection passes
25 tests; the desktop picture/chrome selection passes 18, both with zero
failures/errors/skips. Updated picture assertions cover shared human-readable
state, a route-label-free film header and clean film redraw. New unit coverage
checks all result states and word wrapping without losing explanation text.
Both picture fixtures retain the same S2 jump, failure, applied playback and
pan/zoom source; refreshed evidence lives under the external evidence directory's
`polish/` child. Full command lines and final commits are recorded in external
`STATUS.md`. This is focused presentation qualification; no broad smoke or
release suite was submitted in this follow-up.

Both refreshed eight-second, 1280x720/60 fps clips passed FFprobe and complete
`ffmpeg -v error -xerror` decoding, and were reviewed at 1x through jump, landing,
arrival, failure and final pan/zoom in an owned browser. The route reads clearly
against the busy Emerald Hill scenery at the HUD and desktop viewport scales;
the dark outline holds the cyan/gold strokes and the scene stays clear of labels.
The wrapped explanation remains readable in each frontend's own visual style.

The refreshed source scenes are byte-identical to the original evidence, and all
four success/failure path arrays and protocol messages compare equal. This polish
changes presentation only; the before/after route observations are unchanged.
Desktop polish is committed as `32ec954`; engine polish is committed with this
qualification update.

The polish's once-only guards run executed 720 tests with five failures and no
errors/skips. Four match the previously attributed MenuAudioProbeTool singleton
and HubMusicController provider/package failures above. The owned fifth exposed
a HUD dependency on `StageplayRouteResult.success()` for status colour. Routing
that query through the overlay's `successful()` accessor preserves the explicit
frontend boundary. The affected picture/model/workspace/HUD plus ArchUnit recheck
executes 57 tests: 55 pass, with only the two inherited HubMusicController failures
and no errors/skips; the Stageplay ownership rule passes. The full guards selection
was not repeated. The additive accessor changes neither geometry nor UI output.


### Route motion settings (2026-10-23)

The agreed route design replaces speed-seeking jumps with explicit starting action,
travel speed, ending action and facing. Settings live on the route MOVE_TO intent,
so scene persistence, screenplay round trips and editing preserve them without a
parallel mutable route store. Ordinary hand-authored intents retain native-speed
countersteering defaults; planned routes default to Walk / Gentle Stop / Inherit.
Actor movement-ability declarations are intersected with donor native support;
creator definitions can declare or decline moves independently of character names.
The unpublished canonical command format changes directly; no migration is added.
Implementation and native-input verification continue in the following slices.


Schema/ability slice: `7e780144d`. Stable compile and the focused schema/compiler/
decompiler/API selection passed: 36 tests, zero failures/errors/skips. The release
policy descriptor remains unchanged because API version/status are unchanged;
the hook rejects descriptor edits for ordinary candidate-pin regeneration.

Controller/search decisions: the same input-only steering policy serves the
private terrain probe and LIVE playback. Gentle predicts coasting from live
friction, Hard countersteers using native deceleration, Overshoot captures its
return phase in control rewind state. Grounded starts and spindash are replayed
as ordinary controller edges. The chosen initial stroll policy is `$180` (1.5 px
per tick), one quarter of normal `$600` native top speed; using the animation
threshold alone was rejected because almost-top-speed travel still reads as Run.
The search retains one authored destination, penalises each non-Jump-start jump
by 2000 score units and accepts arrival only when popped from the priority queue.
Immediate acceptance of a generated jumping child was rejected because a cheaper
grounded path may still be pending. Per-edge MOVE_TO emission and +64/+128 goals
are removed. Final film/motion qualification is recorded after focused checks.


Controller/UI slice: `71e128275`; desktop motion summary: `7833420` in the editor
repository. The build-only package of the following focused refinements passed:
Run keeps native overspeed/coasting behavior; only Walk actively holds its stroll
cap. Land-first waiting needs no extra RELEASE command at a clean shot boundary.
Editing a generated SPINDASH keeps its fixed charge hold. The picture fixtures
now require a grounded default rehearsal with one MOVE_TO and record the settings
form before the observed route. Focused native/ROM/UI checks remain queued at
this checkpoint; packaging success is not evidence of motion correctness.


The initial conservative stroll-margin policy was rejected by real EHZ rehearsal:
1024 nodes / 16748 ticks exhausted, with the retained prefix at x398 still moving.
It braked repeatedly on flat ground instead of holding the cap. The corrected
policy predicts the next native slope impulse from the actor's live slope factor
and angle (`Sonic_SlopeResist`, s2.asm:37709-37732), then chooses coast/drive/brake.
Search velocity keys retain native precision so friction-to-rest states are not
collapsed. The same 300-frame EHZ shot-entry request then cold-qualified in 22
nodes / 1340 ticks, with exactly MOVE_TO (frame 34) and STOP (282), no JUMP, final
centre x397.80078125 / y624.61328125 for goal 400/624. The search never changed
native physics or adopted a fitted speed. Build-only package passed; focused
Maven coverage remains queued at this checkpoint.


Native warmup recheck: all eight EHZ variants passed cold replay, cap/no-skid,
settle/facing and backward-seek/forward replay assertions. Spindash needed three
down ticks after its facing tap before the first fresh jump pulse; an earlier
pulse initiated an ordinary jump before crouch selection. The remaining synthetic
spindash assertion was rejected as an invalid oracle: `SpriteArtSet.EMPTY` has no
animation profile and cannot enter native Duck. Actual spindash charge/release
is asserted with supplied-ROM art instead. A changed actor ID in the interior
object-route form now receives the correct shot-entry rejection before parsing
playable-only settings. S3K mandatory four passed 60 tests, HUD/picture 12 and
desktop picture/chrome 18, all with zero skips. Final modal-picture refresh uses
the actual engine-owned menu renderer; drawing chrome alone omitted that layer.


The repeated-rev default was then rejected: native charge reached `$C00` and a
short Spindash/Hard Stop route passed the point by 174 pixels before returning.
That behavior belongs to Overshoot, not Hard Stop. The fixed 24-tick default now
uses one fresh press after crouch warmup, selecting the native minimum charge;
no launch speed is written by the director. Cold qualification rejects a
non-Overshoot route that passes the goal beyond tolerance, with advice to choose
Walk start or move the destination farther away. The final ROM selection adds
the reverse EHZ downhill route and asserts no unintended pass for every other
ending. Its new result is pending at this checkpoint.


Final single-press native charge and actual EHZ downhill selection passed all nine
variants (one JUnit test, zero skips). Spindash now peaks at native `$800`, charges
for 21 sampled ticks and settles at x396.78515625 without an unintended pass.
Downhill Walk peaks at exactly `$180`, never skids and settles at x95.94921875 for
x100. All non-Overshoot variants remain within the six-pixel pass tolerance.

The once-only guards run executed 720 tests: 715 passed, five failed, zero skips.
Four match the already attributed baseline: the two singleton guard methods on
MenuAudioProbeTool.java:41 and the two concrete-provider/shared-layer ArchUnit
rules on HubMusicController.java:118/120. Those source and guard files have zero
diff from pre-task `d8099d53f`; prior matched baseline evidence remains in the
overlay qualification record. The owned fifth failure was direct native trig
access from StageplayRouteSteering. Physics-derived slope impulse now crosses
from the native observation adapters as an integer; steering stays value-only.
Native rolling coast drag also reads live acceleration / 2, per
`Sonic_RollSpeed` (s2.asm:37009-37013). Only the affected ArchUnit and native ROM
selection is being rechecked, with no repeated broad guards or smoke run.


### Final route-motion handback (2026-10-23)

Implementation is local on the two requested feature worktrees, with no merge,
push or PR. Engine source is `d29514a89`; desktop source/fixture is `8c5d6b4`.
Engine slices are `7e780144d`, `71e128275`, `f6ce13498`, `c4710109c`,
`e09f74950`, `9f61bd788`, `f6b405068`, `d29514a89`; desktop slices are
`7833420`, `aca3592`, `8c5d6b4`. The qualification commit changes prose only.
An already-reached point keeps one motion-bearing MOVE_TO and a later STOP;
a STOP-only special case was rejected because replanning recovers settings from
the MOVE_TO. Budget-limit chrome now retains the planner's Run/Spindash advice.

Completed bounded validation, all through the Maven queue from the intended tree:

- Schema/document/compiler/decompiler/API selection: **36 passed**, zero skips.
  Selector: `TestStageplayRouteMotionDocument,TestStageplayDocument,TestStageplayScriptCompiler,TestStageplayScriptDecompiler,TestModApiSignatureSurface`.
- Final planner/native selection on the delivered source content: **14 passed**,
  zero failures/errors/skips: `TestStageplayRoutePlanner,TestStageplayRouteMotionRom`.
  The ROM test checks nine variants: Walk, Run/Gentle, Run/Hard, Run/Overshoot,
  Jump start, Spindash start, actual EHZ downhill Walk and both final facings.
  The 13 synthetic/native-physics checks include a real collision gap requiring
  Run/jump, downhill cap control, all endings/facings, deterministic replay,
  budget/cancellation, parent-world/audio ownership and already-arrived commands.
- Workspace/settings recheck: **9 passed**; controller **4**, protocol **3**,
  input snapshot **1**, route contract **2** also passed in the focused selection.
  Final overlay/advice selection: **7 passed**, zero failures/errors/skips.
  The earlier red selections and causal fixes are recorded above; these are not
  added together as a unique total or described as a full-suite pass.
- Mandatory S3K four: **60 passed**, zero failures/errors/skips, using
  `TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
- Explicitly enabled HUD/picture selection: **12 passed**; desktop/picture/chrome:
  **18 passed**, zero skips. Final picture-only refreshes: **1 passed each**,
  with actual entered destination, four motion settings and two-second form hold.
- Once-only guards: **720 tests, 715 passed, five failures, zero errors/skips**.
  The one owned Stageplay boundary failure was corrected. Narrow
  `TestArchUnitRules,TestStageplayRouteMotionRom` recheck: **33 tests, 31 passed,
  two inherited HubMusicController failures, zero errors/skips**; the Stageplay
  owner boundary and nine ROM variants pass. The other two inherited singleton
  failures remain exactly as recorded above. No repeated broad guard run.
- Stable build-only packaging, including candidate SDK/Javadoc, passed. The
  desktop private dependency was refreshed from that task engine jar. Tool
  preflight passed with Java 25, Lua 5.4 and PowerShell; no tests ran in preflight.
  The explicit-policy live-ring-run screenplay compiles to its exact maintained
  canonical scene. Modified JSON, diff whitespace and skill mirrors were checked.

Exact final launch shapes (ROM variables refer to the existing verified images;
`MOTION` is the task's external evidence directory):

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dsonic2.rom.path=$S2_ROM" -Dopenggf.stageplay.motionEvidence="$MOTION" '-Dtest=TestStageplayRoutePlanner,TestStageplayRouteMotionRom' test
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestStageplayRouteOverlay' test
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$S3K_ROM" '-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards test -B
python3 tools/testing/maven_queue.py -Dmse=off "-Dsonic2.rom.path=$S2_ROM" -Dopenggf.stageplay.motionEvidence="$MOTION" '-Dtest=TestArchUnitRules,TestStageplayRouteMotionRom' test
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.hudPictures=true -Dopenggf.stageplay.routeEvidence="$MOTION" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestStageplayRouteOverlayPictures' test
# Desktop cwd: same wrapper by absolute task-engine path; desktopPictures=true replaces hudPictures.
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package
```

The initial broad citation checker reports inherited stale/missing/unverifiable
references elsewhere in the repository. Only the task's S2 labels/ranges were
manually verified against pinned `34fc7f130a96`; unrelated citation debt was not
changed. S2 CRC32/SHA-1 and locked-on S3K CRC32/SHA-1 match the repository table.

Evidence lives in the task's external `motion/` directory:

- `walk-vs-run.mp4`: 1920x540, 60 fps, seven seconds / 420 frames.
- `ending-actions.mp4`: 1920x360, 60 fps, seven seconds / 420 frames.
- `spindash.mp4`: 960x540, 60 fps, seven seconds / 420 frames, native offline audio.
- `engine-route-overlay.mp4`, `desktop-route-overlay.mp4`: 1280x720, 60 fps,
  ten seconds / 600 frames each; real frontend settings, proposal, Apply/native
  walk, failed attempt/advice and pan. Both retain opaque-film redraw assertions.
- Lossless FFV1/FLAC raw takes (`walk`, `run-gentle`, `run-hard`,
  `run-overshoot`, `spindash`), their canonical sources and per-frame native
  telemetry remain beside the comparisons. Raw takes are 960x540, 60 fps,
  seven seconds with 48 kHz stereo audio. Comparison labels/goal guides are
  post-production marks; they are absent from the scene and raw film.

Capture command shape, once per named canonical source:

```bash
python3 tools/stageplay/stageplay.py render --scene "$MOTION/walk.stageplay" --out "$MOTION/walk.mkv" --scale 2 --s2-rom "$S2_ROM"
ffmpeg -v error -xerror -i "$MOTION/walk.mkv" -f null -
```

All ten final movie files passed FFprobe metadata inspection and complete
FFmpeg `-xerror` decoding. All five presentation clips were watched at 1x to
completion in the owned browser, with sequential observations of charge, burst,
walk/run, coast/brake/return, form readability, drawn path and actual cross.
Walk reads as a slow stroll; Run accelerates visibly. Gentle coasts, Hard stops
sharply, Overshoot visibly passes and turns back. The S2 default Walk/Dowhill
peak is exactly `$180`, with zero skid samples; the default native spindash peak
is `$800`, not a director-written velocity. Final Left/Right taps shift the
centre by one native acceleration quantum (`0.046875` pixel) and hold facing.
The native point tolerance is six horizontal / twelve vertical pixels; default
EHZ Walk settles at x397.80078125 for x400, reverse downhill at x95.94921875 for
x100. Unsupported physical combinations fail with advice rather than forcing
motion. Search is bounded, higher-speed advice is advisory, walking-object
behavior is unchanged and multi-waypoints remain deferred. Current Stageplay
stock-cast/asset limits remain; the ability declaration contract can extend to
future mod actors. No broad smoke/full-suite or all-zone certification is claimed.


Native raw Walk/Run/ending takes used packaged `c4710109c` source content;
Spindash used packaged `e09f74950` minimum-charge content. Later observation
relocation preserves their input decisions, and the delivered `d29514a89` native
replay selection checks the same canonical routes. Final frontend takes use the
delivered engine content and desktop `8c5d6b4`. Scene sources and telemetry stay
with the clips; comparisons can be regenerated from those inputs in minutes.

Consumed task Maven logs/reports, scratch diagnostics and the task-generated
desktop config example were removed. The owned review browser tab and loopback
server were closed; process inspection found no remaining task-tree Java workers
or Maven requests. No category execution run was created, so no run-id needs
acknowledgment. Build outputs and the editor's private dependency remain in their
own worktrees for the lead. Both local task branches/worktrees are preserved;
no unknown or user-authored files, main checkouts or prohibited checkout were
changed. The only remaining red checks are the four inherited guard identities.


### Route review corrections, slice 1 (2026-10-23)

Verified F1/F2/F5/F6/F7/F8 against `aa9b47470`: non-Gentle Walk delegated
without acceleration headroom, SPINDASH+WALK had no contract rejection, the
saved start could be absent from form choices, rolling/slope formulas were
duplicated, capability expectations read the production donor gate, and the
engine host indentation mixed tabs/spaces. Every Walk ending now gates native
acceleration (double in air); both physics readers share drag/slope helpers.
Spindash requires Run in the document contract (also screenplay compilation),
and selecting it sets Run through the shared form model. Unavailable saved
starts remain visible and reject submission until repaired. Explicit per-game,
per-character expected capability values replace the circular assertion.
Focused tests and slope Hard Stop/Overshoot ROM cases are pending qualification.
No broad smoke is requested; the lead owns that independent run.


### Route review corrections, slice 2 (2026-10-23)

F3 verified: production collected but never consumed `spindash()`. Fixed cadence
could jump before Duck on a mild slope. Replaced it with native-state gating:
brake facing-tap inertia, establish Duck, press jump, release down. Both search
and cold LIVE replay require charge and release. Native routines and shipped
fixBugs=0 remain authoritative. Downhill ROM rehearsal added. F6 command button
selection now lives in one production kernel used by LIVE and the test replay;
command scheduling remains owned by the live world (WAIT/ability/object rules).
The named fixed charge-window constant belongs to PuppetCommand and is included
in the normalized unpublished candidate pin. No API version/status changes.


### Route review corrections, slice 3 (2026-10-23)

F4/F8 verified: the removed ±64/±128 goals narrowed reachability, and every Walk
failure received unqualified speed advice. Direct search now gets the first
bounded pass. Failure alone enables a separately budgeted fallback with penalised
64/128-pixel run-up goals. Emission merges consecutive identical goals and emits
only actual fallback steering changes; simple routes retain one destination.
A reserved matched Run search from the original checkpoint can justify only
“terrain near x=…: needs Run”; there is no Spindash inference or advice on
unsupported abilities. Budgets count all passes and diagnostic ticks. Sealed-wall
and speed-gap assertions now distinguish real supporting evidence from generic
failure. A gap at the starting feet explicitly compares direct-only failure
with fallback success and replays its commands using production button selection.

Checkpoint: engine commits a6fa8fdcc, 461104cab; current uncommitted source is the
detour/diagnosis slice. Build-only test-compile passed before that slice. Focused
selection is queued (one request, one observer); no broad smoke. Version probe is
also queued. Final ROM/frontends/mandatory S3K/guards and motion review remain.


### Qualification correction checkpoint (2026-10-23)

The first lean selection exhausted 1 GiB after 24 completed passes (workspace
11, motion document 2, API surface 11). The dump allocates Mockito invocation
records while scanning synthetic terrain. The terrain mock is now stub-only;
its behavior is unchanged and expectations use no invocation verification.
A normal-heap rerun is required. No lean result is called passing validation.

Native movement inspection also rejected unconditional opposite-input settling:
Sonic_Move floors a sign-crossing brake at reverse $80, permitting oscillation.
Coast while friction can erase the facing tap. If downhill slope pull defeats
friction, brake uphill once and coast to Duck. The observed crouch/charge/release
contract remains mandatory. A direct screenplay rejection test is added.
Combined normal-heap focused + ROM selection is queued with stable source;
previous pending selections were superseded before execution by these edits.
Version probe passed Java 25.0.5; actual tool preflight passes Java/Lua/PowerShell.
Targeted steering disassembly checks report six valid citations and one
unverifiable short-form inherited comment; the all-repository check is red on
existing stale/missing/unresolved references (no disassembly pin is changed).


Normal-heap qualification completed 54 tests, 51 passes, three owned failures:
the speed-gap proof ran out of reserved nodes; the unrestricted detour graph
exhausted its budget; and reverse Spindash/Hard Stop could not stop before a
short point in cold replay. No OOM with the stub-only terrain fixture. Fallback
now chooses one run-up at the root, reaches/settles there without jumping, then
searches toward the destination. Its penalty is once per detour, not per edge;
visited keys distinguish active run-up goals. The speed fixture supplies the
combined search budget and its matched Run probe uses direct steering.

The downhill charge source is moved from flat x=400 to measured incline x=340,
y=628, using Overshoot to admit the native burst's stopping distance. It must
observe charge on a nonzero native angle. Added native jump/cap checks for every
Walk ending, a ROM-art-backed backup-runup regression, and retained ordinary
HOVER/CROUCH/ROLL stopping in the shared kernel. Focused affected recheck queued.


Affected recheck: **19 passed**, zero failures/errors/skips (planner 16, ROM 3),
including every Walk ending across native jumps, both slope endings, actual
incline charge/release and ROM-art-backed run-up. Desktop enabled picture test
passed; its 17-test chrome selection had one repair-path failure: a successful
resubmission retained the earlier error string. Shared form submission now clears
that stale error before calling validation. A negative EMPTY-art charge test and
search-side cap-branch pruning strengthen the qualification boundary. Final
focused/frontends/S3K/guards remain pending on this last source slice.


Final focused selection: **110 passed**, zero failures/errors/skips, on the
source in the final correction slice. Coverage includes route native physics,
real ROM motion (12 rehearsals plus contract rejection and ROM-art run-up),
negative missing-charge qualification, LIVE world replay/abilities/waits,
workspace form recovery, controller/protocol/input/contract/overlay/job, both
screenplay directions, document/API values and native Puppet ownership.
Selector: `TestStageplayRoutePlanner,TestStageplayRouteMotionRom,TestStageplayLiveWorldRom,TestStageplayRouteMotionDocument,TestStageplayRouteWorkspace,TestStageplayController,TestStageplayRouteProtocol,TestStageplayRouteInputs,TestStageplayRouteContract,TestStageplayRouteOverlay,TestStageplayRouteJob,TestPlayablePuppet,TestPlayablePuppetRom,TestStageplayDocument,TestStageplayScriptCompiler,TestStageplayScriptDecompiler,TestEditorProjectMenu,TestStageplayHud`.
All supplied S1/S2/S3K absolute ROM paths were explicit. Queue wait 755s; Maven
execution 41.011s (not summed with overlapping jobs as delivery elapsed time).
Desktop chrome **17 passed**, zero skips, on 146e076 with the final packaged
engine; enabled desktop picture **1 passed**, refreshed against that same jar.
Mandatory S3K + enabled engine picture is running; once-only guards are queued.


### Architecture qualification correction (2026-10-23)

Once-only guards on 294196b83: **720 tests, 713 passes, seven failures**, zero
errors/skips. Four match the established baseline (two singleton assertions at
MenuAudioProbeTool:41, two HubMusicController ArchUnit assertions). Three are
owned regressions: `package_slices_are_free_of_cycles`,
`core_runtime_cycle_cluster_does_not_gain_top_level_edges`, and
`stageplay_boundaries_have_explicit_owners`. The shared-helper extraction put
sprite/physics reads in route policy and added sprites→stageplay edges. That
approach is rejected by the existing ownership contract; no guard is widened.

Correction: `PlayablePuppet.observe()` is the sole native view (rolling drag,
deceleration and slope facts). LIVE's production `routeInput` adapter copies
scalar facts into a pure route-policy Motion value; the replay test uses that
same adapter. The steering kernel has no sprite/physics/service dependencies.
Affected native/Puppet/LIVE tests and TestArchUnitRules are queued. All 12
rehearsal states and canonical command sources will be compared across this
structural change; no extra broad guards run is requested. Prior bounded passes:
110 focused, 60 mandatory S3K + one enabled HUD picture, 17 desktop chrome + one
enabled desktop picture, all zero skips. Both editor clips and four motion clips
were watched at 1x; full decode passes. The apparent HUD media-format problem was
a stopped loopback evidence server, confirmed by ERR_CONNECTION_REFUSED; the
original encoded file plays after restart. No codec/runtime repair was needed.


Boundary recheck completed: **69 tests, 67 passed, two inherited failures**,
zero errors/skips. All **37** affected native tests pass (planner 17, motion ROM
3, LIVE ROM 7, Puppet 9, Puppet ROM 1). ArchUnit is **30/32**; only the existing
HubMusicController provider constructors and music dependencies fail. All three
owned architecture assertions now pass without a guard/allowlist change.
Exact selector: `TestStageplayRoutePlanner,TestStageplayRouteMotionRom,TestStageplayLiveWorldRom,TestPlayablePuppet,TestPlayablePuppetRom,TestArchUnitRules`.
Queue wait 1507s; Maven execution 46.400s. Twelve canonical rehearsal scenes and
twelve complete per-frame state streams (JSON key order normalized), plus the
two frontend source scenes, are unchanged across the scalar-adapter refactor:
**26 compared, zero changed**. Existing watched captures therefore retain their
recorded native motion. No broad guards or mandatory-bootstrap rerun was added
for this pure observation-boundary repair. Desktop chrome passes 17/17 again
against the newly packaged engine. No live task jobs remain.
