# Agent Workflow

Support material to help agents implement OpenGGF objects/zones/trace-fixes with less context loss.

Zone delivery: [methodology v2](../architecture/designs/2026-09-15-zone-methodology-v2.md)
retains FBZ's route slices and adds early short native comparisons and compatibility
checks. Its first target is the [Sandopolis plan](../architecture/plans/2026-09-15-soz-methodology-v2.md).

## Delivery skills

- [S3K zone bring-up](../../.agents/skills/s3k-zone-bring-up/SKILL.md) is the
  end-to-end entry point: inventory, playable slices, visual/native evidence,
  compatibility and rewind, current acceptance status, media and integration.
- [Gameplay capture](../../.agents/skills/gameplay-capture/SKILL.md) records an
  individual ordinary gameplay scenario.
- [BizHawk native reference capture](../../.agents/skills/bizhawk-native-reference-capture/SKILL.md)
  runs a zone-specific Lua exporter in BizHawk for native screenshots and state
  observations used as ROM corroboration or visual references.
- [Gameplay highlights](../../.agents/skills/gameplay-highlights/SKILL.md) maintains
  the unified source archive and curates a reproducible, act-ordered moving reel.
  It also replaces outdated excerpts after follow-up fixes. Every delivered reel,
  promo or showcase must publish its production sources and external asset catalog
  to [OpenGGF-Media](https://github.com/OpenGGF/OpenGGF-Media), with the production
  link and archive commit included in delivery; large media files remain external.

These skills apply the methodology and current repository policy; plans retain
per-task decisions and evidence. Highlights do not certify uninterrupted routes.

## Tools

- [Seven-Chaos gateway diagnostic](../../src/main/java/com/openggf/tools/S3kSevenChaosGatewayCaptureTool.java): repeats the declared Tails/LRZ2 ring→sanctuary→sphere-stage→results→return scenario with verified ROM identity, fixed Limited/RAW_RGB, event/state CSVs and original PNGs; `--rom` and `--out-dir` require absolute paths, Linux uses surfaceless EGL with no display. This preserves the 2026-10-17 native RGB investigation; it is not a cold-route certificate.

- [Trace SST occupancy probe](../../src/test/java/com/openggf/tests/trace/SlotOccupancyProbe.java): optional comparison-only `OGGF_SLOT_PROBE=1` diagnostics over existing V5 slot dumps; `OGGF_SLOT_PROBE_FULL=1` prints physical owner/phase labels, fails closed on duplicate SST owners, and writes only below `target/` (2026-10-14 AIZ retained results/title audit). Labels alone do not certify native code parity.
- [AIZ1/HCZ1 obligation gate](../../tools/testing/check_route_obligations.py): verifies canonical ROM/fixture prerequisites and joins 26 explicit route, rewind and reload case identities to fresh Surefire results; rejects missing, skipped and duplicate obligations (2026-10-15 architecture debt remediation). Its scope and command are in the [AIZ1 matrix](../architecture/validation/levels/s3k-aiz1-sonic.md#explicit-junit-obligation-pilot-2026-10-15-candidate).
- [Editor HUD pictures](../../src/test/java/com/openggf/editor/project/TestEditorHudPictures.java): drive the controller-first editor HUD with a synthetic gamepad over ROM-backed frames at 1600x900 with `-Dtest=TestEditorHudPictures -Dopenggf.editor.hudPictures=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<absolute S2 REV01 ROM>` through the Maven queue; it asserts hotbar, stroke-undo, library, wheel and hierarchy behaviour and writes `target/editor-hud/`; review `world.png`, `block.png` and `chunk.png` there before copying them to `docs/guide/creating/images/level-editor/`.

- [Category negative-case comparison](../../tools/testing/compare_category_outcomes.py): compares completed bounded runner failures and causal skips with an explicit source/run baseline, rejecting incomplete evidence and requiring opt-in for verified SSZ blob hashes (2026-10-08 Mutator Lab expansion); [limits and usage](../../tools/testing/README.md#bounded-category-result-comparison).
- [68000 frame-time closure](../../tools/timing/m68k-closure/frame_closure.py): decodes native per-frame execution histograms from the original ROM bytes and checks fail-closed (PASS/FAIL/INCOMPLETE/ERROR exit codes) whether instruction cycles plus pinned Genesis Plus GX 051d430d stall rules close the exact frame period; Z80-bus, FIFO and VInt endpoint terms require observer counters, so it stays INCOMPLETE without them (2026-10-16 S3K ending Kos CPU-budget research); `refine.py` proves operand/branch widths from ROM semantics and `interrupt_phase.py` derives the 50–59-cycle GPGX acceptance cost per VInt (2026-10-17 kos46 v3 patch); [usage and limits](../../tools/timing/m68k-closure/README.md).
- [Native stall ABI v2 ledger checker](../../tools/timing/native-ledger/ledger_closure.py): validates a TraceChaser `--m68k-ledger-window` recording fail-closed (PASS/FAIL/INCOMPLETE/ERROR), proving exact per-interval master-clock conservation and per-PC GPGX 051d430d cost admissibility (exact, branch-mix or operand-bounded, never a solved path), with named negative controls; `DeclaredIngressStateProducerV1.cs` is the consumer-side probe that produces its declared-setup ingress (2026-10-17 S3K ending Kos CPU-budget research); [usage and limits](../../tools/timing/native-ledger/README.md).
- [S3K instruction-cost ledgers](../../tools/timing/s3k-instruction-ledgers/README.md): fail-closed ROM micro-execution models that price the instruction time of the VInt/H-int handlers, Kos and Nemesis queues, LevelLoop skeleton, sprite renderer, background deformation and palette fades from engine-owner inputs, checked against native per-PC histograms; diagnostics only, never runtime or readiness input (2026-10-16/17 kos46 rounds R6–R17); [layout, tests and limits](../../tools/timing/s3k-instruction-ledgers/README.md).
- [S3K load-time fingerprint inversion](../../tools/timing/s3k-instruction-ledgers/kos/invert_fingerprints.py): resolves `s3k-fast-v1.json`/`s3k-v1.json` Kos submission fingerprints to ROM Kosinski descriptors, including KosM module children (184 of 184 at `00f8ab78a`) (2026-10-16 kos46 R7); [usage](../../tools/timing/s3k-instruction-ledgers/README.md#kos).

- [Native Mutator stage probe](../../src/main/java/com/openggf/tools/MutatorStageProbeTool.java): bounded registered-mod interactive native clocks, input admission and offline PCM for all three special stages and S3K bonus stages (2026-10-08 Mutator Lab expansion); [setup and limits](../../tools/media/README.md#native-mutator-stage-observations).
- [Owned Engine window capture](../../tools/media/engine_window_capture.py): bounded X11 desktop video, Pulse monitor PCM and input-only JSON actions with isolated config, exact PID/title/visibility checks and owned cleanup (2026-10-07 Mutator Lab). See the [capture recipe](../../tools/media/README.md).

- [Eggman's Sky voice generator](../../tools/audio/eggmans_sky_voice.py) synthesizes
  original directed Alice announcements through OpenRouter, preserves raw takes outside
  Git, verifies literal words after the approved metallic/vocoder/DAC processing, and
  derives manifest entries and queue leases from the final PCM (2026-10-08).

- [Creator kit builder](../../tools/modding/build_creator_kit.py) exports matching
  engine/SDK/API-doc/testkit artifacts, pinned portable examples and launchers;
  [catalog probe](../../src/main/java/com/openggf/tools/modsdk/CreatorCatalogProbe.java)
  measures production discovery, validation and registration at explicit bounded
  catalog/asset shapes. Origin: mod framework readiness, 2026-10-07; see
  [probe limits and acceptance](../modding/tools/scalability.md).

- [Sitar Hero S2 native reference](../../src/test/java/com/openggf/mods/code/SitarHeroS2NativeProgram.java) (test scope): independently interprets supplied S2 ROM control flow, saved duration/frequency and service cadence; `TestSitarHeroS2SongCatalogue` compares complete requested attack streams and natural ends (2026-10-07 full-song catalogue).

- [Sitar Hero S3K song probe](../../src/main/java/com/openggf/tools/SitarHeroS3kSongProbe.java): executes both locked-on ROM music tables through the production S3K driver and reports complete jump candidates, tempo boundaries, real channel/sample attacks and natural stops; input is an absolute ROM path and optional hex music IDs (2026-10-07 full song catalogue).

- [Sonic 1 song-form survey](../../tools/audio/s1_song_forms.py): independently
  walks a supplied REV01 ROM's SMPS calls, counted loops, native tempo/divider
  changes and track stops to report complete forms and natural endings. Origin:
  Sitar Hero full-song catalogue, 2026-10-07; command and limits are in the
  [catalogue evidence](../architecture/designs/2026-10-07-sitar-hero-s1-song-catalogue.md).

- `GameplayAllocationTool`: ordinary BK2-driven loop/render bytes per frame, excluding PNG/readback and CSV allocation. Use `--rom <absolute-path> --input <bk2> --zone <numeric-id> --act <one-based> --out-dir target/alloc`; optional `--frames 6000 --warmup 600 --width 400 --main sonic --sidekick tails --rewind true --jfr true`. `--sidekick none` selects solo play; the console reports the live roster count. Loop allocation includes input-row decoding. Compare route state before interpreting allocation deltas; JFR includes other threads, whereas reported counters measure the gameplay thread. Keep raw diagnostics temporary.

- [Sprite sheet dump](../../src/main/java/com/openggf/tools/modsdk/SpriteSheetDump.java) (`ggfmod sprites`): renders every mapping frame of a ROM sprite (art, mappings, optional DPLC, palettes) or a playable character (`char=sonic`, with its animation scripts) into one numbered PNG grid; `char=sonic heads=true first=0 count=32 scale=150` adds production Big Head stock/head/body/composite review panels and explicit pose classifications, for choosing frames without booting a level (2026-10-05 Slay the Robotnik; a ggfmod subcommand since 2026-10-06).
- [Example mod capture](../../src/test/java/com/openggf/mods/code/ExampleModCapture.java) (test scope) with [ExampleModHarness](../../src/test/java/com/openggf/mods/code/ExampleModHarness.java): builds any `examples/` mod with a startup scene from source and records its scene headless with GL to PNGs, an MP4 (ffmpeg) and a WAV of its music and sound effects, from an input script whose `jump=` steps use the scene's `DebuggableScene` entry (2026-10-05 Slay the Robotnik capture, generalised 2026-10-06 for the example's highlight reel).
- [Mutator gameplay observation](../../src/main/java/com/openggf/tools/MutatorGameplayCaptureTool.java): runs the production input-only capture loop and adds admitted policies, head scale, live/slotless spill objects and badnik observations to CSV. Uses the ordinary capture CLI and requires `-Dopenggf.headless.gl=surfaceless-egl`; no display fallback. Origin: gameplay-first Mutator Lab promo, 2026-10-10.
- [Master title capture](../../src/main/java/com/openggf/tools/MasterTitleCaptureTool.java): renders the hub, Game Setup, Settings, Advanced, help and quit pages in a hidden GLFW window (or display-free EGL with `-Dopenggf.headless.gl=surfaceless-egl`) with scripted `InputHandler` key events (never desktop input) and writes one PNG per scene. Arguments: output directory, optional viewport width (default 320) and scale (default 3), and an optional paused-frame PNG (any `GameplayCaptureTool` frame) that adds the suspended-game scenes (paused card, End game, prompt before another Start, Quit, Resume transition, paused Game Setup and Settings, and start/mid/end freeze-to-card and resume zoom frames); restart scenes (unchanged restart-only row, restart-pending hub and Settings), crowded profiles, missing-ROM location and focused Save changes actions always render; run from a directory whose ROM images supply the title logos, with `-XstartOnFirstThread` on macOS. Origin: master title redesign, 2026-10-12; paused scenes from the hold-Escape suspend, 2026-10-14.
- [Aspect switch probe](../../src/main/java/com/openggf/tools/AspectSwitchProbeTool.java): `AspectSwitchProbeTool <game> <zone> <act> <out-dir> <native|switch|stale>` renders a level after a mid-level 4:3 to 16:9 change through Engine's display-width refresh hooks (`switch`), at a native 16:9 start (`native`), or with only the camera refreshed (`stale`, a negative control); compare each with `native.png`. One mode per JVM, `-XstartOnFirstThread` on macOS. Origin: in-flight settings for suspended games, 2026-10-15.
- [Menu audio probe](../../src/main/java/com/openggf/tools/MenuAudioProbeTool.java): `MenuAudioProbeTool [width=320] [hub|settings|launch]` measures native menu update, audio, draw and finish costs plus real speaker underruns during sequential cues and the 14-frame launch flash (without game bootstrap), separating 120 warm-up frames from 240 measured frames. Uses the capture classpath, an owned hidden window, and `-XstartOnFirstThread` on macOS; requires physical speaker output and nonzero cue PCM. Origin: menu breakup follow-up to `2596fbc40`, 2026-10-12.

- [Packaged MGZ arena witness](../../src/test/java/com/openggf/tools/TestWestMgzBossNativeArena.java) (test scope): opt-in `secondstrike.mgz.packaged.arena=true` replays the immutable 53676 artifact pair and genuine creator economics through native Knuckles hit/glide recovery. Requires exact engine/SDK/mod properties, the original ROM and a fresh absolute `OPENGGF_M4_CAPTURE_ROOT`; ordinary Maven discovery skips it, and enabling it with current `target/classes` deliberately fails its artifact authority checks. Origin: Second Strike M4, 2026-10-16; [source and coverage limits](../architecture/plans/2026-10-15-second-strike-campaign-expansion.md#compact-checkpoint).
- [Level layout dump](../../src/main/java/com/openggf/tools/LevelLayoutDumpTool.java): reads real ring/object placements and the resolved start position for planning controller routes. Arguments: game, original ROM, zone, one-based act, new output directory; requires the same display-free backend. Origin: Mutator Lab enemy-chain/ring-recovery shots, 2026-10-10. Inspect actual loaded identity; a requested zone alias alone is not proof.
- [Maven resource profiler](../../tools/testing/profile_maven.py): sample queued ordinary/guard runs for process-tree memory and CPU admission estimates (2026-09-15 Maven resource task).
- [Live Maven queue dashboard](../../tools/testing/queue_dashboard.py): `python3 tools/testing/maven_queue.py --dashboard` serves a read-only browser view of running jobs, waiting priority and existing resource samples on loopback (2026-10-16 throughput follow-up).
- [Per-test memory profiler](../../tools/testing/profile_test_memory.py): explicit capture/rewind/ROM tests in a repeated diagnostic JVM, with executing-thread allocation, sampled peaks and post-GC pass floors; see [measurement limits](../../tools/testing/README.md#per-test-memory-diagnostics) (2026-10-07 throughput task).
- [Ordinary-suite memory observer](../../tools/testing/profile_ordinary_memory.py): actual Surefire class/test peaks and post-teardown GC floors, with separate Maven RSS/swap/heap probes and bounded GC/native evidence (2026-10-07 memory-cause investigation).
- [Streaming JFR test summary](../../tools/testing/java/com/openggf/tools/JfrTestSummary.java): `java -Xmx128m --source 21 tools/testing/java/com/openggf/tools/JfrTestSummary.java target/recording.jfr [test-class-prefix ...]` separates sampled threads, test owners and capture phases without expanding event JSON; weights estimate churn, native samples include waiting (2026-10-08 SOZ test-throughput follow-up).

- [FBZ boundary comparator](../../tools/bizhawk/compare_fbz_boundary_fixture.py) supports `--sprite-publication` to compare the complete native CPU sprite table with the following consecutive frame’s VDP table; duplicate samples and gaps are explicit. The boundary exporter supplies the read-only CPU snapshots.

- [FBZ cadence pixel comparator](../../tools/bizhawk/compare_fbz_cadence_pixels.py) pairs native VRAM/SAT/CRAM pixels with reconstructable actual-GPU source masks, retaining occlusions and unmatched source pixels; requires Pillow.

- `FbzVisualCaptureTool` also records actual tilemap GPU uniforms and hash-bound descriptor, lookup and atlas readbacks, so camera-state acceptance can be separated from shader sampling defects. See [FBZ GPU sampling evidence](../architecture/research/s3k-zones/fbz-validation.md#gpu-sampling-and-retained-background-history-2026-09-14).

- [Movie checkpoint exporter](../../tools/bizhawk/capture_movie_checkpoints.lua) and [trace checkpoint pixel comparator](../../tools/bizhawk/compare_trace_checkpoint_pixels.py) capture native PNG/VDP checkpoints (with a reusable zone-entry state) and compare them with a full-run trace capture in 3-bit colour, skipping lag rows (2026-09-17 SOZ pixel checkpoints).

- [SOZ pushable-rock probe](../../tools/bizhawk/capture_soz_pushable_rock.lua) finishes the native entry jump, then records the positioned push/fall/track/stop sequence with the shared host (2026-09-15 SOZ methodology v2).

- [Native reference capture](../../tools/bizhawk/README.md) runs explicit zone-owned Lua exporters/plans with an isolated BizHawk 2.11 configuration, source hashes and failed-export detection. The FBZ command remains a compatibility profile (2026-09-15 SOZ methodology v2).

The following `com.openggf.tools` CLIs. Maven examples quote each `-D...` property. Runtime measurements invoke Java directly from an already-built classpath.

| Tool | Purpose | Invocation |
|------|---------|------------|
| `AgentWorkflowTool` | Preflight checklist for an object task: zone-set resolution, registry status, RomOffsetFinder commands, required guards, docs. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.AgentWorkflowTool" "-Dexec.args=object s3k MHZ 0x8A"` |
| `RomArtIntakeTool` | S3K ROM-backed art/mapping/PLC intake; wraps `RomOffsetFinder --game s3k`. Flags (caution, not a hard reject) `s3.asm`-sourced labels (Sonic 3 standalone / S3L half) — it classifies by source file, since a label search carries no ROM offset. Prefer an S&K equivalent; if an object has none, the S3-half reference is legitimate (rare; verify). Recommends StandaloneArtEntry vs LevelArtEntry and `Sonic3kConstants` / `Sonic3kPlcArtRegistry` hints. Processes multiple labels. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.RomArtIntakeTool" "-Dexec.args=ArtNem_AIZSwingVine Map_AIZSwingVine"` |
| `ObjectScaffoldTool` | Guard-friendly object/badnik skeleton + JUnit5 test shell (no `getInstance()`, no ctor `services()`, no `addDynamicObject`/`setDestroyed`; center-coord note). `--game s3k --badnik` emits the `...sonic3k.objects.badniks` package extending `AbstractS3kBadnikInstance`. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.ObjectScaffoldTool" "-Dexec.args=--game s3k --class MhzFooObjectInstance --id 0x8A --badnik"` |
| `TraceTriageTool` | Reads `target/trace-reports/<game>_<zone>_report.json` and prints a first-divergence brief (frame/field, ROM vs engine, likely owning subsystem, disasm search terms). Comparison-only; never hydrates engine state. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceTriageTool" "-Dexec.args=s2 mtz1"` |
| `ZoneSpecNormalizerTool` | Normalizes an `s3k-zone-analysis` spec into the stable 13-section layout (palette cycling vs mutation kept separate; `(not analyzed)` placeholders for gaps). | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.ZoneSpecNormalizerTool" "-Dexec.args=<path-to-zone-analysis-spec.md>"` |
| `TraceBenchmarkTool` | Replays a trace headlessly with no pacing and reports per-subsystem frame-time percentiles, for comparing JVMs or catching a performance regression. Writes a JSON report. Never quote its numbers without checking the trajectory digest matched. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceBenchmarkTool" "-Dexec.args=--trace aiz1 --json target/bench/temurin21-g1.json"` |
| `RuntimeInputPlan` | Compile v5 controller inputs, structural loop admission and dedicated hardware readiness into a comparison-free plan for the complete runtime benchmark (2026-10-12 task). | `java -cp "$OPENGGF_RUNTIME_CLASSPATH" com.openggf.tools.RuntimeInputPlan TRACE_DIR ZONE ACT START COUNT PLAN_JSON` |
| `RuntimeEngineBenchmarkTool` | Run the normal paced Engine window/render/audio/filled-rewind workload; reports total-process CPU and presentation intervals. External process sampling supplies RSS/peak and DRM residency. Optional final CSV is qualification-only; `--load GAME ZONE ACT ROM_ROOT REPORT_JSON` measures a separate LIVE fresh load through its first gameplay presentation. | `java --enable-native-access=ALL-UNNAMED -Xmx3g -cp "$OPENGGF_RUNTIME_CLASSPATH" com.openggf.tools.RuntimeEngineBenchmarkTool PLAN_JSON ROM_ROOT REPORT_JSON MEASURE_FROM MEASURE_TO` |
| `tools/performance/sample_runtime.py` | Sample an owned runtime process outside its marked measurement window and within filled history; append steady RSS/peak and deduplicated resident GPU counters to the runtime report. | `python3 tools/performance/sample_runtime.py --pid PID --log LOG --report REPORT_JSON` |
| `BenchmarkCompareTool` | Renders a Markdown comparison from two or more benchmark reports; the first is the baseline. Pure post-processing, so it can run under any JVM. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.BenchmarkCompareTool" "-Dexec.args=--out target/bench/comparison.md target/bench/a.json target/bench/b.json"` |
| `InputLogAuthorTool` | Compiles a short controller script (`60 R; 1 D+R; repeat 3 { 1 A ; 1 - }`) into a BizHawk `Input Log.txt` or minimal `.bk2`, then re-parses it with `Bk2MovieLoader` so the file is proven loadable. Skill: `bk2-input-authoring`. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.InputLogAuthorTool" "-Dexec.args=--inline '60 R; 1 A' --out target/capture/run.txt"` |
| `DezFinalRouteAuthorTool` | Observes the final DEZ encounter and authors controller-only input through hands, core and escape. Declared solo Sonic boot: 200 rings, seven Super Emeralds. Optional DEZ2 input starts at ($34B0,$300), traverses its boss/real final-arena load and continues without reseeding. A fifth argument (`cold-solo`, `cold-team`, `cold-team-emeralds`) instead plays an input prefix from the real DEZ1 start without position/ring seeds. Writes input script/state CSV; no post-boot gameplay-state injection or parity claim. | `DezFinalRouteAuthorTool <ROM> <width> <output-prefix> [input [cold-solo\|cold-team\|cold-team-emeralds]]` (same Java classpath as gameplay capture) |
| `DdzIncomingRouteAuthorTool` | Replays an actual DEZ incoming movie, then authors controller-only DDZ ring collection and both boss phases. The complete native movie supplies input buttons only; no state hydration. Declared positioned or cold-team boot; complete input script and observed state CSV. | `DdzIncomingRouteAuthorTool <ROM> <width> <incoming.bk2> <reference.bk2> <output-prefix> [cold-team]` |
| `GameplayInputBranchTool` | Cold-replays one input prefix, then tries controller-only scripts from its engine-owned rewind state. Writes full-prefix BK2/scripts/state CSVs, nearby object inventory, per-input riding/standing contact owners, read-only boss routine/health/position CSVs and sampled PNGs. Preserves both controllers and input-edge history; refuses existing outputs, cross-load restores, callback-bearing fade checkpoints and pending fresh assembly. Chosen routes require fresh uninterrupted verification. Origin: LRZ2 bring-up, 2026-09-24. | `GameplayInputBranchTool <ROM> s3k lrz 1 320 sonic tails <input.bk2> <prefix> <output-dir> '30 R+A;60 R' '12 R;30 R+A;60 R'` (gameplay-capture Java classpath) |
| `SszBossInputAuthorTool` | Cold-replays an SSZ input prefix, then tries controller policies from whole-registry rewind snapshots with held-input restoration. Supports GHZ/MTZ/Mecha (and LRZ1/LRZ3 bosses, `lrz1`/`lrz3`), selectable width and roster; writes complete script/BK2/state CSV and an explicit defeat/death/limit result. Refuses existing outputs and cross-load restore; selected inputs require fresh uninterrupted replay. `--boot ZONE:ACT` lets the prefix cross real loads (restores stay inside the last world); `mecha2` authors Knuckles' two-phase SSZ2 fight until `loc_7BCB0`. Origin: solo Tails S&K campaign,75c9caf16; cold LRZ→SSZ2 campaign 2026-10-15. | `SszBossInputAuthorTool [--boot 9:1] <ROM> <width> <main> <sidekick-or-none> <input.bk2> <prefix> ghz\|mtz\|mecha\|mecha2\|lrz1\|lrz3 <new-output-dir> [max-new-inputs]` (gameplay-capture Java classpath) |
| `ReferencePathInputAuthor` | Authors controller-only input along a reference run's P1 path where the reference inputs drift (RNG/V_int-phased platforms, clouds, columns). Plays the reference inputs while P1 tracks within 6 px; otherwise scores time-shifted/delayed reference inputs, reactive steer/jump policies, wait-then-jump and spin-dash variants from whole-registry snapshots by reference-path progress (hurt, ring loss and getting lost penalised), commits the best slice and repeats. Restores never cross a real load and touched giant rings count as deaths; the reference only scores positions and never seeds the world; selected inputs need a fresh uninterrupted replay. Origin: cold LRZ→HPZ→SSZ campaign (lane cold3, 2026-10-15), which carried the cold pair through SSZ1's cloud/wrap section to Mecha. | `ReferencePathInputAuthor [--boot 10:1] <ROM> <width> <main> <sidekick-or-none> <prefix.bk2> <prefix-length> <reference.bk2> <ref-zone:act>[/main/sidekick] <from-index> <to-index> <new-output-dir>` (gameplay-capture Java classpath) |
| `RewindFidelitySweep` | Dense rewind-fidelity sweep over a controller-only route movie in the production capture harness: every STRIDE inputs it captures the whole registry, plays HORIZON inputs, restores (checking the restore reproduces the capture), replays and compares P1 rows plus every registry key. Never restores across act/level/manager/mode/pending-fade boundaries. Found the MHZ rider/cap restore gaps, the Mushmeanie shell parent and the lost act-carried shield that 45-frame spot checks missed. Origin: mhzrewind lane 2026-10-16 (`902ba8a10`); `TestMhzRewindFidelitySweep` pins the fixed MHZ1 sites. | `RewindFidelitySweep <ROM> <width> <main> <sidekick-or-none> <movie.bk2> <zone:act> <from> <to> <stride> <horizon>` (gameplay-capture Java classpath) |
| `FadeWindowRetimeTool` | Retimes a controller movie authored before 8179ccd80, when direct-level captures advanced FadeManager twice per frame. Replays from a fresh boot with the production single fade owner, measures each frozen fade window (fade active, object VInt unchanged), inserts the remaining native window frames as repeats of the last authored window input, re-measures from boot per window, and ends with one uninterrupted verification replay reporting loads, death and endpoint. Writes BK2 plus `.script` (source provenance kept). Fade-ins that run with live gameplay are never edited; a death or a route-invariant mismatch afterwards needs real re-authoring. Origin: S3K cold LRZ→HPZ→SSZ lane, 2026-10-15. | `FadeWindowRetimeTool <ROM> s3k lrz 1 320 sonic tails <old.bk2> <new.bk2> [--emeralds DDDDDDD] [--authored-fade-advances 2]` (gameplay-capture Java classpath) |
| `DezMinibossInputAuthorTool` | Cold-prefix controller search for both native eight-hit DEZ1 phases. Reads the eye and native hit counter, restores held input with whole-world snapshots, and records complete script/BK2/state CSV. Includes jump release/repress policies; no position/ring/health seeds. A result requires independent uninterrupted replay. Origin: 2026-09-28 widescreen campaign. | `DezMinibossInputAuthorTool <ROM> <width> <main> <sidekick-or-none> <input.bk2> <prefix> <new-output-dir> [max-new-inputs]` (gameplay-capture Java classpath) |
| `GameplayCaptureTool` | Pictures or films any gameplay section: boots a zone/act on the production path (`HeadlessGameBoot` + `GameLoop.step()`), teleports the leader, drives it from an input log or `.bk2`, and writes PNG frames, `state.csv`, and an MP4. Widths, donors and teams are flags; `--mod <jar>` applies a packaged patch mod (via `DevelopmentPatchLoader`) to film mod gameplay. Skill: `gameplay-capture`. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.GameplayCaptureTool" "-Dexec.args=--game s3k --zone fbz --act 2 --x 0x1CF0 --y 0x76C --input target/capture/run.txt --out-dir target/capture/fbz2"` |
| `LevelTileUsageLocatorTool` | Lists the 128px layout cells (foreground and background) that place given 8x8 tiles or palette-line colours, so a capture can be aimed at AniPLC destination tiles or an AnPal-cycled colour that is off screen from the level start. Origin: HPZ bring-up demo captures. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.LevelTileUsageLocatorTool" "-Dexec.args=--game s3k --zone hpz --act 2 --tiles 0x2D0-0x2DB --colors 3:1,2"` |
| `S3kLoadTimeProfileSupplement` | Appends exact native FIFO v2 (`load-time-measurement-v2`) costs for selected missing fingerprints to an existing S3K manifest without retraining it: verifies the executed BK2, frozen setup SHA-256 and recorder commit, keeps every existing row/estimator byte-for-byte, and writes a hash audit TSV. Contract: [measurement stream](../architecture/designs/2026-07-29-native-load-time-measurement-stream.md#exact-fifo-v2-supplemental-authority-2026-10-15). Origin: S3K ending reunion, 2026-10-15. | `java -cp "$OPENGGF_RUNTIME_CLASSPATH" com.openggf.tools.timing.S3kLoadTimeProfileSupplement BASE.json EXECUTED.bk2 V2.jsonl INVENTORY.tsv SETUP.json SETUP_SHA256 RECORDER_COMMIT NEW.json NEW.tsv` |
| `tools/bizhawk/capture_hpz_route_reference.lua` | Native BizHawk exporter for Hidden Palace questions: records RAM (camera, players, event words, palette line 4, emerald state) and framebuffer images for plan-declared movie windows, and saves native states at planned frames so later probes load a state instead of replaying a complete-run movie. Origin: HPZ bring-up. | `python3 tools/bizhawk/capture_native_references.py ... --exporter tools/bizhawk/capture_hpz_route_reference.lua --plan plan.lua --fixture-state <state> --require-output observations.csv --require-output done.txt` |
| `tools/bizhawk/capture_ddz_route_reference.lua` | Native BizHawk exporter for Doomsday questions: records the DDZ controller/boss/body SST fields, autoscroll words (`_unkFA82..FAB8`), foreground-plane scroll and palette per frame; `plan.slots` logs every SST slot-occupancy change (load and allocation order) and `plan.boss_code` retargets the boss columns. Origin: DDZ bring-up (slot histories found the `Camera_X_pos_coarse_back` latch). | `python3 tools/bizhawk/capture_native_references.py ... --exporter tools/bizhawk/capture_ddz_route_reference.lua --plan plan.lua --fixture-state <state> --require-output observations.csv --require-output done.txt` |
| `tools/traces/aux_code_pointer_timeline.py` | Dates object code-pointer transitions in a V5 `aux_state.jsonl.gz` (`object_appeared` code pointers, `object_removed` slot releases) relative to an anchor pointer's first appearance, so player-independent boss or set-piece clockwork can be compared between native fixtures and against an engine probe. Comparison-only. Origin: round-2 solo Sonic lane, 2026-10-16 (`Obj_LRZEndBoss` takes its 14 mine hits at identical offsets from `loc_797FA` in the Tails-alone and pair fixtures). | `python3 -I tools/traces/aux_code_pointer_timeline.py <run>/hpz22/aux_state.jsonl.gz 0x797FA boss.active=0x797FA boss.defeat=0x79988 stream=0x79A70 mine.float=0x79B54` |
| `GameplayCaptureTool --native-pcm` + `tools/audio/compare_native_pcm_window.py` | Native-compatible PCM window tier. `--audio --native-pcm` with a `.bk2` `--input` opts the production presentation session into the 44.1 kHz native-compatible stage, configured from the BK2's own SyncSettings. Non-Nuked cores and PAL regions are refused. It writes `native-pcm.s16`, `native-pcm-frames.tsv` and per-frame `chip-writes.jsonl`. The comparator reads TraceChaser's `--final-pcm-window` output (pcm.s16/frames.tsv/metadata.json; TraceChaser `feaf6fb0c`) at a declared start row and reports exact equality or the first differing stereo frame. It applies no shift, tolerance or normalisation; its levels are descriptive. A capture that does not replay the native prefix is exploratory, not parity evidence. Origin: round-2 lane pcm-output, 2026-10-16. | `GameplayCaptureTool --game s3k --zone aiz --act 1 --main sonic --sidekick tails --input <movie.bk2> --input-start 577 --frames 600 --audio --native-pcm --no-video --out-dir <dir>`; `python3 -I tools/audio/compare_native_pcm_window.py --native-dir <native> --start-row 577 --engine-dir <dir>` |
| `tools/audio/native-pcm/stock_core_sin.c` | Executes a routine inside the shipped BizHawk GPGX core: it maps the unstripped `gpgx.wbx` load segments at their fixed addresses and calls the core's own musl `sin` for every 16-bit ThreeBand cutoff, on arguments formed with the core's own pi operand (its float `M_PI`). Use it as ground truth when a host-libm harness only happens to match. Origin: round-2 lane pcm-output, 2026-10-16; `TestNativeCompatiblePcmOutput` pins its dump hash. | `clang -O2 -o stock_core_sin tools/audio/native-pcm/stock_core_sin.c && ./stock_core_sin <gpgx.wbx> 0x36f00004e14 0x36f001c8a08 <out.bin>` (compiler and runtime leases per AGENTS.md) |
| `tools/audio/native-pcm/generate_vectors.sh` | Regenerates the native-compatible PCM component vectors from Genesis Plus GX's own `ym3438.c`, `blip_buf.c`, `eq.c` and `psg.c`, linked from an external BizHawk `427556b5` checkout whose files are SHA-256-verified. The self-authored driver `gpgx_pcm_vectors.c` replays the committed `strobes.csv` and `psg_writes.csv`. Origin: round-2 lane pcm-output, 2026-10-16; it regenerates the committed vectors byte for byte. | `generate_vectors.sh build <gpgx-core> <build-dir>` (compiler lease), then `generate_vectors.sh run <build-dir> src/test/resources/audio/native-compatible-pcm/v1 <out-dir>` (runtime lease) |

## Test harness helpers

- [`TestSecondStrikeHczAirRecovery`](../../src/test/java/com/openggf/tools/TestSecondStrikeHczAirRecovery.java) preserves the fixed, positioned HCZ1 maker114 oxygen hop/return and natural child-recreation/full-world suffix replay. Supply an actual paired jar with `-Dsecondstrike.hcz.air.mod=...`, the original S3K ROM and isolated EGL through the normal test queue; an outer owned-process deadline is separate from JUnit's limit (Second Strike, 2026-10-16).

- `tools/modding/native-linux/build_linux.py` uses a pinned rootless Ubuntu/CE
  toolchain for the experimental Linux ZIP, preserving the isolated Windows
  feature. Its native gameplay probe uses production loaders, real OpenGL,
  prepared music, scene lifecycle and module-state checks for every packaged mod.
  The shared `NativeGameplayCheck` also exercises every declared one-shot sample
  through the production session wrapper into final mixed PCM (`sfx-pcm.csv`),
  catching prepared-audio delegation gaps (2026-10-09 Eggman's Sky Vorbis refresh).
  Origin: [Linux friends ZIP, 2026-10-09](../../tools/modding/native-linux/README.md).

- `tools/modding/native-windows/build_inputs.py` rebuilds all friends mods with
  the real converters and package validator; `build_windows.py` compiles a
  separately labelled Windows native image, audits retained members, qualifies
  exact JVM/native registration, runtime-loaded JDK bootstrap controls and the
  actual engine boot path through `.bat` shortcuts, and assembles the ZIP with
  per-mod shortcuts and retained licences. Origin: [experimental Windows ZIP,
  2026-10-08](../../tools/modding/native-windows/README.md).
  `qualify_wine.py` consumes that immutable Windows ZIP, checks every extracted
  shortcut through representative OpenGL gameplay using original local ROMs,
  and adds explicitly labelled Wine compatibility evidence to a clean archive.

- `tools/modding/native-feasibility/probe.py` builds closed-world, Crema and optional
  JIT controls around the production mod classloader, then compiles two external
  fixture JARs after the images exist. `registration_probe.py` derives exact-class
  preservation from the canonical API and all packaged mod bytecode, audits
  types/fields/methods before creator execution, and checks real validation and
  owner transactions on JVM/native. The companion `NativeModMemberContractTest`
  covers dormant callbacks and missing linkage. Origin: [GraalVM native mod
  feasibility, 2026-10-08](../architecture/research/2026-10-08-graalvm-native-mod-feasibility.md).

- `SitarHeroCapture` (`src/test/java/com/openggf/tools/`) packages the real
  example and captures ROM-backed performers, stages, finite arcade behavior,
  career boards and labelled authored-story previews for explicit ROM subsets;
  its source header records inputs and evidence limits.
  Origin: Sitar Hero proof of concept, 2026-10-06.

- `src/test/java/com/openggf/tests/route/` — shared route primitives for headless
  route controllers: `InputProgram` (parse, derive from a BK2, and step pad runs),
  `RouteSteering` (steer, walk with a speed cap, brake distance, ordinary crossing
  budget), `ObjectLifetimeFrames` (spawn/despawn identity sets per frame),
  `RecentFrameLog` (failure diagnostics) and `SidekickAudit` (CPU team contract:
  identity, ownership, leader chain, death/respawn). Extracted from the FBZ2 native
  route in commit 610464952; see
  [live-state route controllers](../architecture/research/2026-09-13-live-state-route-controllers.md).
  They overlap only the failure-diagnostics part of backlog item LTS-04, which stays
  pending.
- `LevelSolidityMapProbe` (test scope, opt-in `-Dopenggf.solidity.map=<out file>` plus
  `openggf.solidity.game/zone/act/x0/x1/y0/y1/step`) writes an ASCII map of a level's
  foreground solidity from the engine's terrain sensors, so a BK2 route (glide targets,
  climbable faces, monitor perches) is authored against real terrain before any capture.
  Pair it with `tools/traces/assemble_bk2_from_input_log.py`, which packages an
  `InputLogAuthorTool` log as the BizHawk-keyed `.bk2` the TraceChaser headless harness
  accepts. Both come from the first Knuckles in Sonic 2 fixture (2026-09-14).
- `PlaneOpacityProbe` (`com.openggf.tools`, library not CLI) answers "can the plane behind
  this one show through here?" from the decoded layout / blocks / chunks / patterns alone --
  no renderer, no camera, no frame. `coverage(level, map, layer, x, y, w, h)` counts the
  see-through pixels of a rectangle and `pixelAt` resolves one, applying both flip levels.
  Use it whenever a background change produces byte-identical captures: a capture cannot
  separate "wrong pixels drawn" from "no pixels reachable", and this can. Always pair it with
  a control viewport that must report see-through pixels. Origin: LRZ slice 5 follow-up,
  2026-09-18, where it showed Lava Reef act 1's foreground is opaque across the whole dome
  (`TestS3kLrzForegroundOpacity`).
- `FloorSegmentSurveyProbe` (test scope, opt-in `-Dopenggf.floorsurvey.out=<out file>` plus
  `openggf.floorsurvey.game/acts/top`) lists each act's longest continuous, pit-free floor
  paths (per-column floor surfaces linked within 24px of height), the candidates for an arena
  or any stretch that must be walkable end to end; confirm them in a capture. With
  `openggf.floorsurvey.headroom=true` (and `minRise`, default 80) it also places the player on
  each path every 16px, jumps, and lists the spans with room to bounce. Origin: Sonic
  Survivors arena selection, 2026-10-06; headroom added 2026-10-07.
- `TestSonicSurvivors#balanceProbe` (test scope, opt-in
  `-Dsonic-survivors.balance=<csv>[,fresh|mid|max[,mode[,sonic|tails[,seed[,firstStage]]]]]`) plays
  whole Survivors runs with a scripted bouncing bot and writes the pressure curve every 30
  seconds (rings against toll, live badniks, hitpoints spawned against damage dealt, level,
  chests, evolutions). Compare across changes; it is not a human difficulty measure. Origin:
  Survivors balance pass, 2026-10-07.
- `ObjectArtContactSheetProbe` (test scope, opt-in `-Dopenggf.artsheets.out=<dir>` plus
  `openggf.artsheets.game/acts/keys`) renders every frame of the object art sheets a zone
  registers to labelled PNG contact sheets with the level palette, so mapping frames (walk,
  hurt face, projectile) are picked by eye. Origin: Sonic Survivors badnik and boss frames,
  2026-10-06.
- `FbzRouteEvidenceProbe` (test scope, opt-in `-Dmse=off -Dopenggf.fbz.evidence=true`)
  prints the `RouteCompletionEvidence` line of each of the eleven FBZ2 complete-route
  matrix rows without asserting; diff the output before and after a route-controller
  or primitives refactor to prove byte-identical behaviour.

## Docs

- [Release publishing](../project/release-publishing.md) — automatic publication on
  `master` push, skipped-job diagnosis, and publication verification;
  use the mirrored `release-publishing` skill for this task.
- [runbooks/README.md](runbooks/README.md) — step-by-step runbooks per task type
- [ci-guard-failure-explainer.md](ci-guard-failure-explainer.md) — guard test → correct fix
- [pitfall-catalogue-index.md](pitfall-catalogue-index.md) — known ROM pitfalls grouped by bug class
- [documentation-obligation-checklist.md](documentation-obligation-checklist.md) — trailers / TRACE_FRONTIER_LOG / changelog
- [delegation-prompt-templates.md](delegation-prompt-templates.md) — research/impl/triage/art/review prompt templates
- [briefing-trace-rounds.md](briefing-trace-rounds.md) — how to hand a trace divergence to an agent: supply the symptom, not your hypothesis.
  Also the accumulated round rules, with a scannable index: the evidence rules, the operational ones (the branch is the artifact; create worktrees copy-on-write; concurrent rounds are a budget), and a
  **measurement-hazard table** listing each hazard's signature and what it looks like. Read that table before reporting any suite number — every entry in it produces output indistinguishable from a real result.

## Worktree resource-link policy

The `post-checkout` hook may create convenience links in a linked worktree for
local ROMs, `config.yaml`, and reference-disassembly directories. Those links
are **filesystem-only scaffolding**: their targets are relative to the
worktree, so they remain portable on the same machine, but they must never
enter a Git tree.

Do not stage generated-resource entries. The policy rejects symlinks at
`config.yaml`, any path ending in `.gen`, and `docs/s1disasm`,
`docs/s2disasm`, `docs/kis2disasm`, `docs/scddisasm`, or `docs/skdisasm`.
Separately, the repository-wide ROM-like asset rule rejects added or modified
paths ending in `.gen`, `.smd`, `.bin`, `.sms`, `.gg`, or `.32x`. The sole
reviewed authored-content exception is the two-act Tide fixture's 22 original
assets, pinned by exact canonical path, mode `100644`, byte length and SHA-256 in
[the authored-fixture manifest](../../.githooks/authored-fixtures.json).
[The shared verifier](../../.githooks/authored_fixture_policy.py) checks Git blobs
in staged, historical-commit and delivered-snapshot admission; it never runs a
candidate generator. Changed bytes, moved/case-varied paths, other binaries,
executable files and symlinks receive no exception. Prefer the existing
`binary-assets.properties` text-source convention for future authored fixtures;
this manifest is not a directory or filename-pattern exemption.

The policy also rejects absolute symlink targets anywhere in the repository. If a broad
`git add` includes one, unstage it, keep or recreate the local link only in the
filesystem, and inspect `git diff --cached` before committing. The ignore rules
cover both a real reference directory and a hook-created link; use `git add -f`
only for an intentional policy test fixture, never to commit local resources.

The policy also rejects new textual machine-local user-home paths. Use a
repository-relative path, `$HOME`, an environment variable, or a neutral
placeholder such as `<user>` in documentation, commands, and reports. Put
intentional architecture audits and handovers in their matching
`docs/architecture/` category instead of committing root-level
`MERGE-STATUS*.md` or `HANDOVER*.md` scratch files.

`pre-commit` gives immediate staged-content feedback, and `commit-msg` repeats
the check for merge resolutions before the normal merge-specific policy path
can return. `pre-push` checks every non-deletion update: an existing branch is
checked over its outgoing range, while a new branch is checked for commits
unique to that remote as well as its tip. A clean tip does not make an earlier,
newly published violation acceptable; remove the bad commit from unpublished
history, then run the hook again. CI applies the same commit-range and
delivered-tree checks on every branch push.

## Direct Maven output

OpenGGF build and test commands invoke Maven directly. Each worktree owns its
`target/` tree, including Surefire reports, trace reports, diagnostics,
temporary files, and per-fork LWJGL extraction. Do not redirect these outputs
into shared session storage. Put durable captures in an explicit external task
or archive directory and retain only the evidence the task requires.

## Start here

Run `AgentWorkflowTool` for a preflight, read the matching runbook, scaffold with
`ObjectScaffoldTool`, intake art with `RomArtIntakeTool`, and triage traces with
`TraceTriageTool`. For performance work, start from
[`runbooks/runbook-jvm-benchmark.md`](runbooks/runbook-jvm-benchmark.md) rather
than the benchmark CLIs directly — the numbers are easy to misread.

Local Maven tests: [`tools/testing/maven_queue.py`](../../tools/testing/maven_queue.py) waits automatically for a shared execution slot across linked worktrees; category runs use it too; `--stats` summarises test wait/hold/memory telemetry. Recognized build-only commands bypass shared test admission, protect their own worktree, and hold an activity lease that permits isolated measurements to drain builds. Direct Maven builds are allowed when that worktree is otherwise idle; see the [build and test guide](../../tools/testing/README.md#build-only-maven-execution-and-queued-tests).

## Test harness helpers

- `com.openggf.tests.route`: reviewed input-program, steering, object-lifetime,
  recent-frame and CPU-team audit helpers imported from `435ec2e68` for the
  route-controller continuation. `TestS3kAiz1RoutePilot` is the native AIZ1
  representative route; `TestS3kAiz1RouteRewind` checks independently reached
  live spots. The [AIZ1 matrix](../architecture/validation/levels/s3k-aiz1-sonic.md)
  records their evidence and remaining coverage.

- `tools/bizhawk/capture_fbz_boundary_fixture.lua`: declared-write native FBZ boundary6 pilot; records every redraw frame with RAM/VDP bytes and bounded failure, launched by the visual host with `--fixture-state` (2026-09-14 FBZ completion).

- `FbzBoundaryFixtureCaptureTool` and [boundary comparator](../../tools/bizhawk/compare_fbz_boundary_fixture.py) reproduce declared native fixture setup through production frames and compare actual retained Plane-B descriptors, uploaded palettes and framebuffers; acceptance remains independently reviewed.

- [Fresh native FBZ entry](../../tools/bizhawk/capture_fbz_fresh_entry.lua), launched by the visual host with `--fresh-entry-act 1|2`, uses the complete BK2 reset opening, AIZ vine cheat and ordinary title/level-select inputs; it never writes RAM. This removes retained cloud-history residue from complete-run FBZ states.

- [`capture_lrz_boulder_reference.lua`](../../tools/bizhawk/capture_lrz_boulder_reference.lua)
  records read-only native LRZ2 cutscene, rider and fresh-load carry observations from an original movie save.
- [`capture_lrz_traversal_reference.lua`](../../tools/bizhawk/capture_lrz_traversal_reference.lua)
  observes LRZ turbine and chained-platform slots during unchanged native BK2 playback;
  use the common native capture host with explicit frame windows and its own movie save.

- `tools/bizhawk/capture_lrz_boss_reference.lua`: read-only original-movie LRZ3
  camera/event and object-graph observer, with optional lava/VScroll/HScroll export;
  `palette=true` also observes the shared LRZ post-boss script cursor, timer and colors;
  `background=true` observes the LRZ2 Death Egg slot, screen coordinates, priority and art-load flag;
  native save and frame-window plan inputs
  (September 22 S&K completion campaign).

- `tools/bizhawk/capture_ssz_arrival_reference.lua`: read-only original-movie SSZ1 cutscene, Death Egg child timers/animation, palette and Knuckles landing observer; explicit native save and frame-window plan (September 24 S&K completion campaign).
- [SSZ arena-static presentation demo](../../tools/visuals/ssz_arena_static_demo.py): composites deterministic side-only noise over verified widescreen boss footage and emits a local comparison page; originating [2026-09-24 design study](../architecture/designs/2026-09-24-widescreen-boss-arena-static.md), not an engine feature.

- `tools/bizhawk/capture_mhz_pillar_reference.lua` samples native MHZ2 pillar scroll words and their collision helpers from ordinary movie playback; use the common native capture host and an explicit movie-state plan.

- [Starpost placeholder generator](../../examples/starpost-valley/generate_resources.py): authors the flat town placeholder
  into packaging output, without ROM bytes (Starpost Act Bridge, 2026-10-09).

- `GameplayCaptureTool --title-screen --audio` (Mutator Lab, 2026-10-07) observes
  production title-to-play initialization and exactly one final SMPS PCM packet per
  captured outer frame. InputLogAuthorTool compiles the maintained example script;
  synchronized CSV adds host pause and effective mutator revision. Captures belong
  outside Git, and the silent video may be muxed with the generated `audio.wav`.

- `MutatorGameplayCaptureTool --showcase` / `--interactive-pacing` preserves the
  all-eleven Mutator Lab film's native stage drawing, production presentation
  input/tick pump, offline PCM and policy/checkpoint/monitor observations
  (2026-10-10, source `379b66413`); see [capture contract](../../tools/media/README.md#mutator-gameplay-showcase-captures).

- `GameplayCaptureTool --interactive-pacing` reuses that production presentation
  owner for ordinary capture, including title menus and Chaos Mode scheduling
  (Sonic3K Chaos Mode, 2026-10-13). Canonical one-native-tick capture remains the
  default. A canonical capture freezes presentation observers and cannot qualify
  their countdowns or menus; use the explicit flag and inspect `state.csv` before
  selecting images. See [gameplay-capture](../../.agents/skills/gameplay-capture/SKILL.md).

- `ggfmod head-inventory` and `object-head-inventory` (Big Head polish,
  2026-10-11) export original-ROM indexed pixels, mapping coordinates and CPU
  production mask panels without GL, windows, input or audio. Player output also
  reviews ROM animation-frame sequences in fresh DPLC banks; standalone mapping
  order is diagnostic and can contain stale art in no-upload frames.

- `TestEggmansSkyArtPreparation`: bounded owned-visit Egg preparation timing probe (three seeded fresh scenes; originating mod audit remediation 2026-10-12); headless warmed measurements only.

- `com.openggf.tools.RuntimeEngineVisualControl`: lossless production-presented framebuffer and PCM qualification with compiled controller plans; separate from runtime acceptance timing (2026-10-12 performance series).

- `tools/performance/run_isolated.py`: OS-leased exclusion of linked-worktree builds/tests plus a bypass-process guard for prebuilt measurements; writes an isolation audit (2026-10-13 P5 timing correction). Exclusive test admission precedes a separate active-build lease; idle retained category worktrees do not block it (2026-10-16 queue liveness correction).

- [Native published-video diagnostic port](../../tools/tracechaser/bizhawk-headless/README.md#diagnostic-rendered-frames): opt-in rendered GPGX frames with owned pixels/VDP domains and stale-generation guards; explicit ROM/movie/scene inputs, no canonical fixture publication (2026-10-14 S3K visual parity task).

- [Positioned S3K ending sampler](../../src/main/java/com/openggf/tools/S3kEndingSamplerTool.java) and [fail-closed native RGB comparator](../../tools/bizhawk/compare_native_rgb.py): explicit Tails solo/+2/NTSC 320×224 title-owner ingress, complete requested picture pairs and raw RGB with declared observation mapping/crops (2026-10-17 recurring ending parity); [protocol, commands and limits](../architecture/plans/2026-10-13-s3k-playable-completion.md#2026-10-17-maintained-positioned-ending-sampler).
