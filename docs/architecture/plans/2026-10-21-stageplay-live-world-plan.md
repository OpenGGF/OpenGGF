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
