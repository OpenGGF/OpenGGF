# Native reference capture

[`capture_native_references.py`](capture_native_references.py) runs a supplied
Lua exporter in Linux BizHawk 2.11/GPGX with an isolated configuration. It works
with explicit ROM/BK2 inputs and a zone-owned plan; it supplies no zone selection,
checkpoint offsets or gameplay setup. Canonical trace recording remains in
the [TraceChaser capture workflow](../../.agents/skills/bizhawk-headless-trace/SKILL.md).

## Run a diagnostic

Use absolute paths. `ROM_SHA1` is the independently verified expected ROM hash
(see the root ROM identity table); `TASK_DIR` is an external task directory.
`OUTPUT` must not exist. Install Mono, `monodis`, and official BizHawk 2.11;
framebuffer content checks additionally require Pillow.

```bash
python3 tools/bizhawk/capture_native_references.py \
  --bizhawk-home "$BIZHAWK_HOME" \
  --rom "$ROM" --rom-sha1 "$ROM_SHA1" --movie "$BK2" \
  --exporter "$TASK_DIR/exporter.lua" --plan "$TASK_DIR/plan.lua" \
  --output "$OUTPUT" --require-output observations.csv --timeout 180
```

The plan is a Lua file returning the table the exporter needs, for example
`return {zone=0x0800}`. The host copies and hashes it without adding fields.
`--fixture-state "$STATE"` optionally identifies a native save state. The exporter
owns its loading/setup, input sequence, observations and `client.exit()` call.
A standalone save made after movie playback ended must be loaded **after**
`movie.stop()`; check the load result and actual zone before taking observations.
A loaded state can still contain an intro controller lock. Observe that state
instead of treating a running frame counter as evidence of admitted input.

The Lua environment is:

| Variable | Value |
| --- | --- |
| `OGGF_NATIVE_PLAN` | Copied plan path |
| `OGGF_NATIVE_OUTPUT` | Output directory |
| `OGGF_NATIVE_ROM_SHA1` | Verified ROM hash |
| `OGGF_NATIVE_BK2_SHA256` | Movie hash |
| `OGGF_NATIVE_FIXTURE_STATE` | Optional native save path; absent when not supplied |
| `OGGF_NATIVE_HOST_RECEIPT` | Host result path, written when the process ends |
| `OGGF_NATIVE_FRAMEBUFFER_PROBE` | Shared Python command for `--probe-framebuffer PATH` |
| `OGGF_NATIVE_PYTHON` | Python executable for that command |

`host.json` records source, plan, host and emulator hashes, process status and
failures. A timeout, nonzero emulator exit, logged Lua exception, or missing/empty
`--require-output` file makes the host exit nonzero. Repeat `--require-output`
for each required artifact. A file's presence does not validate its contents:
inspect row counts, state, screenshots and the intended observation boundary.
`execution_status: completed` is execution evidence only; acceptance remains
`pending-independent-pixel-and-state-review`.

`--probe-framebuffer` checks horizontal pixel content across separated active
rows in 320×224 or bordered 348×240 Genesis images. Its result says nothing about
which zone, event, or gameplay state the image depicts.

## Existing FBZ commands

[`capture_fbz_visual_references.py`](capture_fbz_visual_references.py) is the
compatibility profile. Existing flags, default exporter, reviewed manifest,
start-frame offsets and boundary recipes remain there. It delegates launching
and failure handling to the common host and supplies both the new environment
names and the historical `OGGF_FBZ_*` aliases for old external exporters.
Repository FBZ Lua exporters now use `OGGF_NATIVE_*`. The old command remains
valid for [recorded FBZ validation recipes](../../docs/architecture/research/s3k-zones/fbz-validation.md#reproducible-linux-native-capture).

## Trace checkpoint pixels

[`capture_movie_checkpoints.lua`](capture_movie_checkpoints.lua) saves framebuffer PNGs
and whole VRAM/CRAM/VSRAM dumps at planned BK2 frames during movie playback. Its plan can
save a state on a first pass (`save_state_frame`, `save_state_path`); later passes supply
it with `--fixture-state` and `state_frame`. For trace row r the frame is
`bk2_frame_offset + pre_trace_osc_frames + r`.
[`compare_trace_checkpoint_pixels.py`](compare_trace_checkpoint_pixels.py) pairs those
frames with a full-run `TraceCaptureTool` MKV at scale 1, skipping lag rows (the MKV has
no frame for them), and compares 3-bit Genesis colour with optional masked rectangles.
See the SOZ plan section "Presentation follow-up: native pixel checkpoint survey".

## Focused checks

```bash
python3 tools/bizhawk/test_native_capture.py
python3 src/test/resources/bizhawk/fbz_framebuffer_probe_test.py
lua5.4 tools/bizhawk/test_fbz_boundary_fixture.lua tools/bizhawk/capture_fbz_boundary_fixture.lua
```

The ordinary JUnit suite also invokes the shared host tests through
`TestNativeReferenceCaptureTool`; `TestFbzVisualExporterGuard` exercises the
existing Lua exporter and framebuffer guards.


## Native headless rendered observations

The optional pinned TraceChaser harness also exposes
[`IGpgxVideoCaptureHost`](../tracechaser/bizhawk-headless/src/Core/IGpgxHost.cs)
for owned headless diagnostics. Its [rendered-frame contract](../tracechaser/bizhawk-headless/README.md#diagnostic-rendered-frames)
uses `AdvanceWithVideo()` followed by `CapturePublishedFrame()` to copy actual
ARGB pixels and published VRAM/CRAM/VSRAM, with dimensions, frame/lag identity
and domain endianness. Ordinary `Advance()` stays video-disabled. Ordinary,
audio, failed advancement and same-frame state loading invalidate the previous
rendered generation; stale pixels cannot be relabeled as current.

This is an API for an explicit scene/input observer, not a new screenshot CLI
or canonical fixture publisher. Supply and verify original ROM/movie/runtime
inputs, declare setup writes before observation, preserve original pixels and
CPU/prepared/published identities, and keep artifacts in an external task
directory. Frame-end VDP memory does not prove every earlier scanline's data.
Use normal shared admission and the intended source/classes build lease for
compiler helpers, including external outputs; retain it through owned compiler
children and release it before queued tests. Engine captures continue through
GameplayCaptureTool. The Lua exporter host above remains available for its
explicit isolated diagnostic workflow.

### HCZ phase publication diagnostic

[`S3kHczPhaseProbe.cs`](S3kHczPhaseProbe.cs) preserves the read-only original-E
observation profile from 2026-11-10. It runs the original
`s3k-complete-sonic-tails.bk2` from power-on and observes completed frames
37,055–39,471. ROM and movie hashes, neutral P2 input and exact movie length are
checked; original named buttons and sync settings are applied. This is a fixed
diagnostic profile, not a canonical recorder or an engine input port.

[`S3kHczColdProbe.cs`](S3kHczColdProbe.cs) supplies the separate callback-free
producer. It accepts `ABSOLUTE_ROM ABSOLUTE_BK2 NEW_OUTPUT_DIR` and writes the
same fixed window with individual-button `input_mask`. Its optional checkpoint
at 37,054 invokes the pinned host's non-public `CloneSavestate` only to serialize
an owned state; it never loads that state. Both probes use the original movie
and sync settings from power-on.

Compile both against the pinned TraceChaser headless host and official BizHawk
2.11 DLLs. Own the source,
host classes and build-activity leases through compiler-child cleanup, even when
the output is external. Release them before ordinary shared native admission.
Bind and verify the actual host/runtime/source hashes and strict installation
preflight; a matching filename alone does not authorize reuse. No GUI or video
backend is needed. `CSC` is the reviewed Mono Roslyn compiler; `TRACECHASER`,
`BIZHAWK_HOME`, `TASK_DIR`, `ROM` and `BK2` are absolute paths. These are compiler
and runtime payloads for the owned launcher, not commands that bypass those
leases/admission and process-group cleanup:

```bash
export MONO_PATH="$BIZHAWK_HOME/dll"
export LD_LIBRARY_PATH="$BIZHAWK_HOME/dll"
bash "$TRACECHASER/bizhawk/preflight_bizhawk_2_11.sh" --bizhawk-home "$BIZHAWK_HOME"
mono "$CSC" -version
for probe in S3kHczColdProbe S3kHczPhaseProbe; do
  mono "$CSC" /nologo /deterministic+ /langversion:7.3 \
    "/out:$TASK_DIR/$probe.exe" \
    "/reference:$TRACECHASER/bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe" \
    "/reference:$BIZHAWK_HOME/dll/BizHawk.Emulation.Cores.dll" \
    "/reference:$BIZHAWK_HOME/dll/BizHawk.Emulation.Common.dll" \
    "/reference:$BIZHAWK_HOME/dll/Newtonsoft.Json.dll" \
    /reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll \
    "$PWD/tools/bizhawk/$probe.cs"
done
export MONO_PATH="$TRACECHASER/bizhawk-headless/bin/Release:$BIZHAWK_HOME/dll"
mono "$TASK_DIR/S3kHczColdProbe.exe" "$ROM" "$BK2" "$TASK_DIR/cold"
mono "$TASK_DIR/S3kHczPhaseProbe.exe" "$ROM" "$BK2" \
  "$PWD/tools/bizhawk/s3k_hcz_phase_sites.json" "$TASK_DIR/observed"
```

Compiler/preflight/version payloads require source/classes/build-activity leases;
each native payload requires its own ordinary shared admission after compiler
cleanup and lease release. Clear display variables and retain actual command,
runtime binding, source/input hashes, completion and owned-child cleanup receipts.
The prebuilt phase probe takes four positional arguments:

```text
S3kHczPhaseProbe.exe ABSOLUTE_ROM ABSOLUTE_BK2 ABSOLUTE_SITES_JSON NEW_OUTPUT_DIR
```

Use [`s3k_hcz_phase_sites.json`](s3k_hcz_phase_sites.json), whose eight registration
sites and optional guard byte sequences are checked against the original ROM
before callbacks are installed. Callbacks retain the registered trigger address
separately from independently read CPU PC: the host's Action wrapper discards the
backend callback-address parameter. Each event retains fourteen selected registers, stack,
five MainRAM regions, inflight input and host completed-frame identity. The probe
does not write RAM/registers or load states. Require `summary.json` completion,
all expected site hits and a separate cold-pass comparison; exit 0 alone is not
acceptance. The raw window does not establish timed shake, death, Knuckles,
unsampled callers, pixels or route parity.

The executed fixed profile had 2,417 samples and 26,179 events: ProcessSprites
2,404; HCZ screen and BG 2,387 each; wall move and diff publication 2,325 each;
shake setup 2,372; floor read 5,991; wall-Y read 5,988. These are source-specific
observations, not counts to manufacture in a producer summary.

[`compare_hcz_phase_observations.py`](compare_hcz_phase_observations.py) analyzes
saved files only. It decodes the thirteen publication values from raw bytes,
checks P1/Tails collision ordering and literal cold-pass bytes/identities, and
retains every difference. Both passes must cover the explicitly declared window.
Completed terminal summaries must bind actual file hashes, samples, event counts,
all eight positive site-hit counts, registration/disposal and the original movie.
The cold summary must declare zero callbacks, RAM writes and state loads.
It rejects empty, incomplete or discontinuous evidence and refuses
to qualify a comparison that has no changing publication control. Individual
A/B/C masks are compared with the cold observer's individual mask; they are never
normalized to the recorder's logical Jump mask.

```bash
python3 -B tools/bizhawk/compare_hcz_phase_observations.py \
  --frames "$TASK_DIR/observed/completed-frames.jsonl" \
  --events "$TASK_DIR/observed/execute-events.jsonl" \
  --sites tools/bizhawk/s3k_hcz_phase_sites.json \
  --cold-frames "$TASK_DIR/cold/completed-frames.jsonl" \
  --summary "$TASK_DIR/observed/summary.json" \
  --cold-summary "$TASK_DIR/cold/summary.json" \
  --first-frame 37055 --last-frame 39471 \
  --output "$TASK_DIR/new-comparison.json"
python3 -B tools/bizhawk/test_hcz_phase_observations.py -v
```

Older executed phase summaries lack `movie_sha256`; supply their original
`--phase-provenance "$TASK_DIR/phase-provenance.json"` containing the recorded
`movie.sha256`. The current producer writes that identity directly. Missing or
conflicting provenance is rejected; no movie identity is defaulted.

Exit 0 means this saved comparison has no reported value differences; 1 preserves
differences and 2 rejects invalid/incomplete evidence. `values_match_timing_partial`
explicitly means some instruction-read fields have no changing-value control.
Every field, including zero counts, and each site's actual read fields are shown.
In the original window EE40 (`camera_y_diff`) never changes at either read site:
Y timing is not discriminated. Floor X differs on 4,995/5,991 reads; the other
996 floor reads and all 5,988 wall-Y reads do not distinguish the generation of
the instruction's read values. Changes to unrelated fields cannot fill that gap.
Event order is separate evidence; end-transition output is descriptive rather
than an expected-transition assertion. The source-specific audit records
the executed native profile and preservation proof; reformatting/promoting the
probe is not another native run. All generated binaries and observations remain
outside Git.

### S2 fixed-tail pressure diagnostic

[`run_s2_fixed_tail_pressure.py`](run_s2_fixed_tail_pressure.py) builds the pinned
TraceChaser host and [`S2FixedTailPressureProbe.cs`](S2FixedTailPressureProbe.cs)
in an owned external directory. It uses the original S2 REV01 ROM and CPZ2 BK2,
explicit-installation preflight, build leases and ordinary shared admission.
It never rebuilds the optional checkout or uses an unverified existing harness
binary. Supply an absent external task root; later `run` reuses only that root's
matching ownership marker and prepared source/binary fingerprints. Preparation
also binds the canonical installation path, required preflight files, every file
under its `dll/` tree and potentially shadowing staged runtime dependencies.
`run` rejects changed/different dependencies and repeats the unmodified strict
installation preflight under build leases **before** native admission. It checks
the binding again after queue waiting and native execution. Older preparation
records without this binding require preparation in a new owned directory;
their existing observations remain evidence, without being treated as a launch
authorization for changed inputs.

```bash
python3 -B tools/bizhawk/run_s2_fixed_tail_pressure.py --phase prepare \
  --task-root "$TASK_ROOT" --bizhawk-home "$BIZHAWK_HOME" \
  --rom "$S2_ROM" --movie "$MAIN_CHECKOUT/src/test/resources/traces/s2/cpz2/s2-lvl-select-CPZ.bk2"
python3 -B tools/bizhawk/run_s2_fixed_tail_pressure.py --phase run \
  --task-root "$TASK_ROOT" --bizhawk-home "$BIZHAWK_HOME" \
  --rom "$S2_ROM" --movie "$MAIN_CHECKOUT/src/test/resources/traces/s2/cpz2/s2-lvl-select-CPZ.bk2"
```

All paths must be absolute. The six cases are `live-last`, `empty-last`,
`reinstall-last`, `live-two`, `empty-two` and `reinstall-two`; repeated `--case`
selects an explicit subset. Each fresh core replays original inputs/sync settings
for at most 11,000 ingress frames, then observes 360 frames after setup. There
is a 180-second process timeout. No display or sound device is opened.
During preparation `MONO_PATH` contains only the existing runtime DLL directory;
the built host directory is added for `run`. This avoids Mono warnings from an
absent build directory interfering with the exact compiler-version check.

This Linux launcher starts each command in its own session/process group. It
uses the existing TERM/KILL cleanup helper and a scoped child subreaper to reap
adopted compiler descendants in that group, including on timeout, cancellation
or a parent exiting before its helpers. Cleanup completes before build/admission
leases release. It never sweeps other process groups or queue state. Focused
harmless-process/runtime-selection controls run with:

```bash
python3 -B tools/bizhawk/test_s2_fixed_tail_pressure.py -v
```

`setup-manifest.json` declares the constructed parent SSTs, original positions,
synthetic unused remember entries, optional fixed-tail clear and separately
declared one-time Sidekick reinitialization trigger. Setup runs after Sidekick
dispatch, before the final dynamic slot's ID read. Every byte write records its
old/new value; all subsequent writes come from native execution. These cases
skip placement-loader admission and natural death/respawn and do not certify
their route. The separate DPLC cache is never initialized by the setup.

ROM-byte-checked execute hooks record allocator return, failure-tail writes,
ID-zero/live fixed-slot walks, actual Obj05 copies, DPLC decisions and DMA queue
effects, native installation, physical pair deletion and remember-bit release.
Complete parent/target bytes and CPU/caller context accompany observations.
Each case writes `observations.jsonl` and `summary.json`; the launcher writes
`provenance.json`, `host.json` and bounded launch logs under its external run
directory. Inspect exact events/counts and unmet assertions. `completed` requires
the asserted paths and complete window; missing installation/retirement is
`incomplete`, not evidence that the behavior is absent. Output is reference
research only, with no canonical fixture publication or engine hydration.

### Read-only execution and RAM observations

[`capture_execution_observations.lua`](capture_execution_observations.lua) records
actual Genesis execution callbacks, CPU registers and explicitly selected MainRAM
regions through the host above. It writes observation files and optional owned
savestates only. It never writes RAM or controllers and is not a canonical fixture
exporter. Origin: S2 Competition ring, camera and placement diagnosis, 2026-11-08.

The plan supplies `last`, optional `windows={{first,last},...}`, optional
`save_frame`, and `state_fields` / `event_fields` arrays. Each field has a CSV-safe
`name`, a byte `length`, and either fixed `address` or callback `register` plus
optional `offset`. RAM values are fixed-width hexadecimal bytes; frame/register
columns are decimal. Register pointers wrap into Genesis MainRAM's 64KiB domain.
`callbacks={{name="pickup",pc=0x121DA,min_hits=1}}` selects explicit verified ROM
execution addresses; each callback defaults to at least one required hit. No
address is discovered or supplied by the exporter. It logs the trigger address
separately from the observed PC, which BizHawk may report after an instruction.

First capture from the original movie with `save_frame` near the window and no
callbacks. Later passes use that **same movie's** owned state with
`--fixture-state` and matching `state_frame`. Require `done.txt`, `state.csv`, and
`counts.csv`; callback passes also require `events.csv`. Check counts and the host
receipt, not just exit status. A missing required callback fails before the done
marker. Compare cold/warm completed samples before accepting a warm observation.
NLua callable methods are userdata, so the exporter tests API presence and actual
registration rather than Lua's `function` type. All output stays in the external
capture root; observations never supply engine gameplay or timing authority.

### S2 Competition reference observer

`capture_s2_competition_reference.lua` observes original REV01 S2 RAM without
writes and saves raw framebuffer PNGs plus native states. Supply the ordinary
host and a two-controller power-on BK2. `inputs/*-route.script` supplies the four
menu entries; `ehz-flow.script`, `ehz-finish.script` and `ehz-zone.script` are
bounded traversal attempts, not certified complete routes. The scripts use the
InputLogAuthorTool grammar. Native movies must additionally carry the verified
S2 game identity and matching two-pad GPGX sync settings; the original native
six-button log orders A/B/C before Start, unlike the minimal engine log.

The Lua plan accepts `last` (absolute completed emulator frame), `frames` (explicit
screenshots), `every` (optional screenshot interval), `events` (first ring/life
changes per player and later mode changes), and `state_frame` when the host supplies
`--fixture-state`. Each PNG has a same-frame `.State`; `state.csv` records every
completed frame in the window. Load only a checkpoint whose original movie prefix
matches the supplied movie. `done.txt` and `state.csv` must both be required outputs.
Lua writes files only; console printing slows the Linux host.

The native 2P menu is a grid: Right selects MCZ, Down CNZ, Down+Right Special
Stage. Title entry is Down then Start after the title admits controls. Competition
level/title-card raw output measured **320×448** in BizHawk 2.11, while menu and
results output measured **320×224**, and special stage **256×224**. Retain those dimensions;
do not fold fields or rescale. The first frame after a state load can contain a
stale interlace field: inspect later frames and use a cold run for pixel acceptance.

Player level x/y are object centre words; velocities are signed 8.8 words. Camera
words include both foreground and background pairs. Time is the raw four-byte HUD
clock, score the raw score units (display scales by ten). `card0`–`card5` observe
reserved title-card slots; `anim_counter0`–`15` observe `Anim_Counters`; `slot_*`
observe the native slot-machine owner. Standard player slots and HUD fields have
special-stage-specific meanings in mode 0x10; do not infer level coordinates or
ring totals there. A menu-selected special stage does not prove a results-induced
tiebreaker. CSV/file presence proves execution; inspect the scene and state before
claiming an obligation is met. Keep generated movies, PNGs, CSVs and savestates
outside Git, with a manifest mapping exact movie/frame/state/window provenance.

### S2 competition goal observations

`capture_s2_competition_goals.lua` extends the native reference observations with
`goals.csv`: nonzero SlotMachineInUse plus a visible native D6 cage, and a complete
foreground cell whose resident VRAM bytes match a non-flat ROM AniPLC pose. It
reads controller/actor/ledger/scroll/VRAM/CRAM state only. It never authors input,
replans, changes RAM, supplies gameplay timing or treats a counter as displayed art.
It saves the first slot/flip goal screenshot and matching native state. Required
outputs are `state.csv`, `goals.csv`, and `done.txt`; inspect the host receipt too.

Generate the ROM-backed template Lua table with Java25 after compiling the engine:
`java -cp "$CP" tools/bizhawk/S2CompetitionGoalTemplates.java "$ROM" "$TASK/templates.lua"`.
The producer verifies S2 World REV01 SHA1, uses the production ROM/AniPLC pipeline,
and refuses to overwrite. Embed its returned table under `flip_templates` in the
native plan, rather than referencing an unhashed external asset. The plan also has
`anchor` (completed native ordinary-Level/LFC0 rows), `last`, and optional `frames`.
Use the common isolated2.11 host with the frozen planner-derived BK2. An observed
lag may end state comparison; goal reachability is checked independently. Record
explicit named qualification coverage and never label it full pixel/state parity.

Validate an entire cold movie with `python3 tools/bizhawk/check_s2_competition_goals.py
--capture DIR --movie ROUTE.bk2 --kind slot-flip --out VERDICT.json` (one shell
command). `slot` validates a slot-only prefix; `zone-results` and `tie-special`
require ordinary play in both EHZ acts and the corresponding native result/ledger
state. The validator binds the completed cold host receipt to the full movie,
ROM and observed row count. Stamp with `com.openggf.tools.RouteNativeStampTool
ROUTE.route.json VERDICT.json`; it regenerates and hashes the route's anchored
export before accepting named `qualification[].coverage`. The named checks do
not certify unlisted planner waypoints or state/pixel parity beyond native lag.

Tiebreaker start additionally requires native `SpecialStage_Started` ($DB23) and
initialized SonicSS/TailsSS actors ($09/$10, routine at least2), alongside mode,
ledger, stage, zone and results flags. The mode switch during a black load is not
sufficient. This is the same REV01 flag owner used by the canonical SS recorder.

### Question-mark monitor outcome captures

`capture_every_frame.lua` now records `monitors.csv` alongside every original
framebuffer PNG and `frames.csv`. It reads Obj2E content/parent/routine, the first
EHZ monitor shell, player power flags, lives, rings, timers and monitor banks.
It sets read-only movie playback. The common host also selects the native
`MovieEndAction.Finish` policy: the installed user's `Record` policy otherwise
extends movies even after `movie.setreadonly(true)`. The host rejects any movie
hash change during capture. No controller or RAM writes are used.

For each frozen `ehz-qmark-<content>` route, export its anchored BK2 with
`RouteFiles.exportBk2`, observe the shell break in a preliminary cold RAM pass,
then capture with `return {last=<break native frame + 400>}`. Validate with
`python3 tools/bizhawk/check_s2_qmark_goals.py --capture DIR --movie FILE.bk2
--content N --out VERDICT.json`, then use `RouteNativeStampTool` as above.
The named stamp proves the observed content, award, break before native level
lag and at least four seconds after the award. It does not certify full pixel
parity, other monitors, P2 breaking, act finishes or held rewind.
