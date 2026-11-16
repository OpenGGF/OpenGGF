# Sonic CD authoring follow-up — bounded validation and continuation

Branch `feature/ai-scd-stageplay-editor`, parent
`origin/feature/ai-scd-mod-api` at `dc6b3c2b9edd621a0feecfe6d411628d51756f92`.
Owner: OpenAI Codex GPT-6.1-sol; no delegated workers.

This is an unfinished implementation checkpoint, not authoring certification.
No merge or destination-branch push is authorized or performed.

## Implemented source path

- Offline scene libraries open hosted media through the module's verified media
  capability. Libraries own opened discs; active-session sources are borrowed.
  An empty `GameDataSource.rom()` is never interpreted as standalone content.
- `MediaSceneArt.stage` resolves the full registry zone/act identity, which includes
  the CD period. R11A and R11B retain different program bytes and hashes. Detached
  native terrain and player art come from that selected disc program.
- Stageplay world construction and constructor-only object preparation borrow
  the source capability. Native CD performers obtain selected-program art and
  palettes without making CD a cross-game donor or enabling donor hosting.
- Worker descriptors carry verified media identity and an absolute input path,
  alongside existing exact cartridge windows. Reopening refuses changed identity.
  Preview, scene-route preparation and export use this same boundary.
- CD scene documents and authored scene-service registration admit native Sonic.
  Registration retains the existing bounded asset and derived-owner boundary.
- The ordinary cold-level route host opens a verified hosted source when the
  module declares that media kind. A CD Past-stage test offers a neutral row.
- Stageplay CLI simulation/rendering accepts `--scd-rom` as a supplied media path;
  the source is opened as a disc, never cartridge-detected.

## Failing-first evidence and focused verification

The queued wrapper reported `soniccd.disc.path` found and `missing=none` on all
runs. Display variables were unset and rendering selected `surfaceless-egl`.

1. `TestCdSceneLibrary` initially failed with “Supply one or more named stock ROMs”.
2. `TestCdStageplayWorld` initially failed with “Unknown gameId: scd”. A subsequent
   fixture error attempted to replace HeadlessGameBoot's owned world; the test now
   boots CD and explicitly adopts that same owned world.
3. `TestAuthoredStageplayServices#cdScenePreparationKeepsRegisteredOwnership`
   initially failed with “Authored scenes require a stock host”.
4. `TestCdRouteTape` initially failed because CHD was sent to cartridge detection:
   “No game module detected for ROM”.

Latest focused command (working tree, not a committed delivery SHA):

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCdSceneLibrary,TestCdStageplayWorld,TestCdRouteTape,TestAuthoredStageplayServices,TestStageplayRomTransport,TestStageplayRouteInputs,TestStageplayExportJob,TestStageplayCaptureTool,TestStageplayFacingGuard,TestNativeObjectFacingGuard,TestRawGlfwTestSetupGuard,TestModApiSignatureSurface' test
```

**53 tests, zero failures/errors/skips.** The descriptor's changed-identity
negative control and R11B native terrain assertion execute against the disc.
The native scene starts at centre x=800, away from the level entry. This does not
prove movement, all CD stages, CD object pixels or complete editor workflows.

The public normalized signature pin is unchanged; additions are internal source
capabilities and existing document admission. Candidate version/topology and
published-baseline policy are unchanged. Pin/surface tests pass in that selection.

## Required remaining work

- CD project selection, source-aware native project cloning/materialization and
  playtest. A portable flat level cannot silently replace `CdLevel`'s native
  program/PLC/object state; the parent's explicit flat-zone limits still apply.
- Content-import selection and CD project/level round trips.
- Stageplay and route-tape UI game choices, native route media discovery and
  source fingerprints. The low-level cold host test is not UI certification.
- Native CD Puppet/Pose pixels, logical facing in both directions and tile
  priority/occlusion evidence. Existing generic guards passing is insufficient.
- Complete object coverage including CD contexts.
- Master-title CD preview and isolated hub/menu audio.
- CD player ground-survey admission (`StageplayGround.Player` still lists three
  cartridge games), selected-program Poser/accessory lookup and scene music proof.
- Full movement and directional-control render review, documentation updates and changelog, change-based
  smoke and baseline attribution, commit trailers, push and stacked PR.

These are unfinished obligations, not evidence-backed capability refusals. No
claim of “everything” support is made. Rewind, widescreen and character donor
hosting remain outside this task's edits.

## Render checkpoint

Packaged current sources successfully with queued `package -DskipTests`.
`tools/stageplay/stageplay.py` compiled the source candidate
`examples/stageplay-demo/cd-past.screenplay.json` to `cd-past.stageplay` (v9).
The two-second R11B shot uses centre x=800, while native entry is x=80.
A terrain-only visual footprint query found surface y=568; native simulation
settles the player at y=549.3125 by frame 24. It is not a player-ground-query
certificate. The hold is not directional-control evidence: the sampled body
retains its left-facing pose through the authored face command.

Outputs are retained under `$AGENT_RUNTIME/task-outDTytw/`:
`cd-past.mkv`, `cd-past.jsonl`, `frame-01.png` (frame 0), `frame-02.png` (48),
`frame-03.png` (96), plus bounded ground/provenance logs. All three stills were
visually inspected: native Past terrain/background, Sonic, native enemies and
caption are visible. This is not a CD object Puppet/Pose occlusion certificate.
FFprobe reports FFV1 320x224 at 60/1 and stereo FLAC at 48000 Hz. Full-file
`ffmpeg -v error -xerror -i .../cd-past.mkv -f null -` decoding succeeded.
The initial extraction used removed FFmpeg `-vsync`; re-extraction used
`-fps_mode passthrough` successfully. No desktop/audio device was opened.

## Handoff state

All listed child processes have completed; no live Maven, renderer or encoder job
is owned by this checkpoint. Package and the 53-test selection passed. No smoke
run was launched: dry planning selects 4,979 classes, smoke plus guards, two
workers. Its expected cost is comparable to the parent's broad smoke; the
requested stopping bound is 40 minutes execution per Maven invocation, with
queue time separate. There is no smoke VERDICT or baseline attribution yet.

Changes are staged in `.worktrees/scd-stageplay-editor` and remain uncommitted.
HEAD is still the exact parent SHA above. No task branch was pushed and no PR
was created. The scratch directory has `.keep` because the task is unfinished.
Next action: finish CD native control/title-entry semantics, then source-aware
project/import/route/UI and master-title audio/preview obligations; add each
surface's failing-first disc test before implementation. Run the final focused
selection and requested smoke on the completed tree, attribute failures on the
parent, then perform documentation, trailer verification, commit/push and PR
against `feature/ai-scd-mod-api`. No delivery-complete claim is made.

## Continuation on 2026-11-16 (supersedes the handoff state above)

The inherited 53-test command was rerun: 53 tests, zero failures/errors/skips.
The checkpoint was committed as `76e92bdaa416f91463901ee04acae90be26daedd`
and pushed to `feature/ai-scd-stageplay-editor`. The resource-policy hook first
rejected the inherited absolute user-home path in this document; it was replaced
with `$AGENT_RUNTIME`. Hooks were installed and never bypassed.

### Native player and ground correction

Failing-first disc tests in `TestCdStageplayWorld` established:

- A rightward native movement request stayed at centre x=800. The diagnostic
  showed `locked=true`, `held=0`, `air=false`, `speed=0` at scene frame 80.
- `StageplayGround.Player("scd","sonic",0)` failed profile validation.

CD's fixed native title-card owner sets `ctrlLocked` at entry. Scene frame 0's
`CLEAR_OBJECTS` could delete that owner before it released the lock. The
`SceneEntryPreparation` capability now lets Stageplay run neutral native
iterations until that owner retires, before replacing/placing the cast or
clearing objects. It is bounded at 600 preparation iterations and fails rather
than inventing controller/clock/player state. Shared code does not branch on
CD. The native title-card lifetime determines readiness; no fitted wait is used.
CD's `CdFixedObjects.install` and `CdTitleCard.update` own lock acquisition and
release. Disc bytes still enter through the media source.

Ground profile capture admits a module's declared native run characters as well
as existing donor characters. It does not enable CD donor hosting. The R11B
centre-x=800 terrain-ground query executes against the disc and finds a native
player placement. Existing cartridge ground/landing tests are included in the
final focused selection.

A first facing assertion asked for Left while inertia still pointed Right;
native input correctly braked before turning. The fixture now uses STOP and a
settling interval before its explicit facing assertion. There is no velocity
write or facing/flip workaround in runtime code.

### Directional render

The updated four-second example starts at centre x=800 in R11B, away from native
entry x=80. It faces Right at 24, moves toward x=880 at 32, stops at 80, faces
Left at 144 and moves toward x=760 at 152. Native simulation observed x=800 at
0, x=874.18359375 at 80, and x=767.7265625 at 230; the direction changes are
native body presentation. Authored `flipX` fields were not introduced.

Current compiled `target/classes` plus the existing dependency classpath ran
`com.openggf.tools.StageplayTool` in a standalone JVM, with both display
variables unset, `-Dopenggf.headless.gl=surfaceless-egl` and the task scratch
as `java.io.tmpdir`. Commands were `compile --script ... --out ...`,
`simulate --scene ... --out ... --scd-rom ...` and
`render --scene ... --out ... --scd-rom ...`; each exited zero.
No desktop audio backend was opened.

Artifacts under `$AGENT_RUNTIME/task-outDTytw/`: `cd-walk.stageplay`,
`cd-walk.jsonl`, `cd-walk.mkv`, `walk-01.png` (25), `walk-02.png` (64),
`walk-03.png` (145), `walk-04.png` (200), and `walk-*.log`.
All four stills were inspected: native CD terrain and body, Right at 25/64,
Left at 145/200, and changed native positions. FFprobe: FFV1 320x224 60/1,
stereo FLAC 48000 Hz. Full-file FFmpeg `-v error -xerror ... -f null -`
decoding passed. This is directional scene evidence, not a Puppet/Pose object
occlusion certificate or a full-speed film review. Music is explicitly stopped;
the presence of an audio track does not prove CDDA/PCM scene music.

### Remaining delivery obligations

No capability refusal is claimed for unfinished work. This remains a partial
implementation, with these in-scope obligations still open:

1. CD object Puppet/Pose pixel equality and real foreground occlusion, including
   the non-occluding negative control.
2. Selected-program Poser/accessory lookup through validation and rendering;
   do not silently use default R11A art for another period.
3. Scene CDDA and PCM music synthesis proof.
4. Stageplay/route-tape game choices, CD native-route media discovery and source
   fingerprints, and CD contexts in StageplayObjectCoverageTool's all inventory.
5. Required change-based smoke and exact baseline failure attribution. Dry plan:
   4,979 classes, smoke plus guards, two workers. Prior parent evidence is in
   `2026-11-16-sonic-cd-mod-api.md`; its 29 failures and 2 errors are not this
   branch's smoke verdict. No smoke VERDICT has been produced for this branch.

The requested broad command remains:

```sh
python3 tools/testing/run_categories.py --base dc6b3c2b9edd621a0feecfe6d411628d51756f92 --workers 2 --headless-gl surfaceless-egl --run
```

Expected cost: the parent recorded about 2,121 summed execution seconds and
2,598 completed queue-wait seconds. Retain the existing 40-minute execution
bound per Maven invocation and report queue wait separately. Do not edit or
build during that run. New/worsened failure identities block delivery.

The parallel lane continues to own editor projects, content import, master-title
preview and hub/menu audio. Rewind, widescreen and donor hosting are untouched.
Keep this branch/worktree and the draft PR for continuation; do not merge.

### Final bounded verification of this continuation

Foreground command: the inherited 12-class focused command above, adding
`TestStageplayGroundRom` (and the two new methods in TestCdStageplayWorld).
The first expanded invocation ended with signal 143 after several passing
classes, without a Maven summary; it is not counted as a pass. Its replacement
executed **59 tests, zero failures/errors/skips**, with `BUILD SUCCESS`.
All verified ROM/disc properties were found (`missing=none`). Queue wait for
that replacement was 108 seconds; Maven execution was 60 seconds. User wait
was zero. Other elapsed task time includes investigation, rendering and unknown
scheduler overhead and is not inferred from those figures.

Exact focused selection:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCdSceneLibrary,TestCdStageplayWorld,TestCdRouteTape,TestAuthoredStageplayServices,TestStageplayRomTransport,TestStageplayRouteInputs,TestStageplayExportJob,TestStageplayCaptureTool,TestStageplayFacingGuard,TestNativeObjectFacingGuard,TestRawGlfwTestSetupGuard,TestModApiSignatureSurface,TestStageplayGroundRom' test
```

Source/test diff digest relative to checkpoint `76e92bdaa4` at verification:
`e9afb3839f53abc43d58ea822aad1a72c50f79a04b26a3eb6e4f3bf5d57aaf25`
(`git diff --binary -- src/main src/test | sha256sum`; the newly added internal
SceneEntryPreparation source also participated in compilation). Subsequent
changes are documentation and the regenerated canonical example only.
`git diff --check` and new documentation-link checks passed. The non-strict
repository citation scan exited zero while reporting existing catalogue debt;
it is not a clean-citation claim. No disassembly pin was moved.

All owned foreground Maven/render processes have completed. The scratch `.keep`
is removed after preserving the reviewed render paths here; scratch artifacts
remain subject to the ordinary runtime retention policy. The final branch
commit records this validation and retains the uncompleted obligations above.

## Object-priority continuation (2026-11-16)

Item 1 is complete on the source tree based on `ea410eccbf`. Added
`TestCdStageplayObjectPriorityRendering`: R11B disc-native Anton pictures equal
frozen and baked Puppet/Pose pictures at four fixed native canopy witnesses
(x=5376,5456,5520,5616; y=112). At each witness, removing foreground occlusion
exposes over 100 changed pixels. Visible native body pixels reject blank/tiles-only
comparisons. The test uses the existing cinematic renderer and no-device boot;
no shared runtime change was needed.

Failing first: the original x=688..880/y=520..616 test area produced zero hidden
pixels, so its negative control failed. Anton was confirmed low priority. A bounded
disc-layout probe found the priority-marked canopy; fixed witnesses replaced the
probe. This was an oracle-location correction, not a runtime workaround.

Foreground verification (display variables unset, verified media `missing=none`):
`env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCdStageplayObjectPriorityRendering,TestStageplayObjectPriorityRendering,TestRawGlfwTestSetupGuard' test`
passed **7 tests, zero failures/errors/skips**, Maven execution 43.222 seconds.
No render files were generated; assertions compare in-memory framebuffer pixels.
No smoke was launched and there is no smoke VERDICT for this branch.

Next: item 2, selected-program Poser/accessory lookup. Investigation found
`StageplayRenderer` and actor preflight still use default character art while set
terrain resolves the selected program. Add a failing disc rendering/preflight test
before changing those lookups. Items 3–5 remain as specified above. No live owned
jobs remain at this checkpoint; PR stays draft. No workers were delegated.

## Selected-program Poser continuation (2026-11-16)

Item 2 follows item 1 commit `ef5fe6c8f6`. Failing-first disc test
`TestCdStageplayPoser` recorded body drawing but observed three default-art calls
and no R11B `stageArt` lookup. Shared StageplayResources now caches art by complete
shot SetSource. Poser body frames, animation sampling and accessory lookup use
that art; preflight checks each shot's stage. Foreign actors retain donor lookup.
There are no game-name branches in this resolution. The resource owner clears
its cache on close.

The disc test checks R11B identity, records actual body draw operations, verifies
selected-stage resolution and rejects default-art calls during preflight/drawing.
CD Sonic has no separate accessory sheet: the selected program returns null,
asserted explicitly; no accessory is fabricated. Existing cartridge renderer
fixtures and native CD movement/ground checks remain included.

Command (display variables unset):
`env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCdStageplayPoser,TestStageplayRenderer,TestCdSceneLibrary,TestCdStageplayWorld,TestModApiSignatureSurface' test`.
The foreground run completed successfully, verified media `missing=none`; see
its test totals below. No file render was generated. Item 1 queue wait was
23 seconds; its execution was 43.222 seconds. User wait was zero; other overhead
is not inferred. Next: item 3 CDDA and PCM scene cues through offline production
capture, then item 4 UI/media/fingerprints/coverage, then item 5 smoke and exact
baseline attribution. No smoke VERDICT exists yet. PR remains draft; no owned
jobs remain at this checkpoint.

Final item 2 totals: **25 tests, zero failures/errors/skips**; queue wait
10 seconds, Maven execution 1 minute 44 seconds. Subsequent source change was
indentation only. Public signature pins are unchanged and their checks pass.

## Scene-music continuation (2026-11-16)

Item 2 was committed and pushed as `70ad8174f6ba4f9651b6d05781ecae394c1ea163`.
Item 3 adds `TestCdStageplayMusic`, using verified disc media, the native CD audio
profile, NullAudioBackend (no device), StageplayAudio preparation/dispatch and
the production live-capture lease. Scene command 15 selects actual CDDA track 3;
`SCMD_PASTMUS` synthesizes through R11B's loaded PCM bank. Each command must emit
sounding production packets in the latter second of a two-second capture. Before
cue dispatch the same producer must emit silence. No native track bytes or
samples are committed.

The existing runtime dispatch already supported both sources: the initial disc
test passed without a production change (one test, no skips, queue 87 seconds,
Maven execution 1 minute 31 seconds). This is not a claim of a missing runtime
feature seen failing. An explicit sensitivity run omitted only cue dispatch:
`TestCdStageplayMusic` then failed `Scene command 15 must synthesize sounding media`
(one failure, zero errors/skips; queue 139 seconds, execution 39.569 seconds).
Dispatch was restored before final validation, and a permanent no-cue silence
assertion added. No renderer or audio device was opened; no render files produced.

Final command:
`env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCdStageplayMusic,TestStageplayAudioLifecycle,TestCdAudioLivePath' test`.
Final outcome and timing follow below after foreground completion.

### Exact remaining scope and next step

Items 4 and 5 are not implemented or certified by this continuation. No smoke
VERDICT or baseline failure attribution exists for this branch, and the parent's
29 failures + 2 errors are not substituted for it. PR #351 must remain draft.
No capability refusal is inferred from this unfinished work.

Next: add failing-first disc tests for item 4 before implementation:

- `editor/stageplay/StageplayMenuPresentation` game choices still omit `scd`;
  route-tape `RouteTapeWorkspace.newForm` does too. Native CD roster must avoid
  the form's default Tails sidekick; CD is not a donor host.
- `Engine.routeTapeRoms` and `EngineRouteHosts.fromSystemProperties` still discover
  cartridges only. Use the module's HostedGameMedia configured locator for discs.
- `route.RomIdentity.standard` knows cartridges only; `ofFile` hashes container
  bytes. A CUE sheet hash alone cannot bind its tracks. Define a source-aware
  verified content fingerprint through the media capability, with shared code
  game-agnostic and no creator-reported identity authority. CHD and CUE should
  bind their verified underlying content, not just a pathname or sheet text.
- `StageplayRouteTool`'s scene planner still uses PhysicalImage/64-MiB cartridge
  resolution; complete its media snapshot/freshness path and negative controls.
  `StageplayRouteInputs.capture(..., RomManager)` already uses the media-capable
  worker descriptor, but that is not whole route UI/CLI certification.
- `StageplayObjectCoverageTool`'s `all` list and SampleObservation admission omit
  CD. Inventory all native zone/act/period contexts; registration-only rows must
  not imply pixel or behavior certification.

After item 4 is green, commit/push it separately. Then run the exact requested
change-based smoke against `dc6b3c2b9edd621a0feecfe6d411628d51756f92`, two workers,
surfaceless EGL and the 40-minute per-invocation execution bound. The inherited
plan was 4,979 classes; re-plan for the finished source selection and state its
count/cost/stopping rule. Do not edit/build during that run. Attribute failures
by identity/assertion/skips on the base, record the VERDICT, and mark ready only
if all five items are complete. Do not merge. Keep this worktree/branch while
PR #351 needs them. Parallel editor/import/master-title work remains out of scope.

Final item 3 outcome: **34 tests, zero failures/errors/skips**, `BUILD SUCCESS`;
verified media `missing=none`, queue wait 44 seconds, Maven execution 49.908
seconds. An intermediate 33-test run had one failure in the new no-cue silence
control: reusing the producer after CDDA left audible prior-source output before
the next command. The final fixture creates independently owned profile/capture
lifetimes for CDDA and PCM, retaining the silence assertion in both tests. The
production runtime is unchanged by item 3. No claim is made that null-backend
`stopMusic` alone resets external-source history.

Items 1–3 are green and committed/pushed as separate checkpoints. Items 4–5
remain open exactly as listed above. This is a partial delivery, not PR readiness.
No owned Maven/render/encoder jobs remain; user wait was zero. Queue wait and
execution are reported above; investigation and scheduler overhead are not
reconstructed. `git diff --check` and local links in the three changed guide/status
files were checked. Retain branch/worktree for continuation; no scratch captures
were generated in this continuation. The existing earlier render paths remain
historical evidence only, not new item 3 audio files.
