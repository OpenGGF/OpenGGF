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
