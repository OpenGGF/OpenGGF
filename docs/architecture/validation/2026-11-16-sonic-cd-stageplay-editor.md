# Sonic CD authoring follow-up — implementation checkpoint

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
