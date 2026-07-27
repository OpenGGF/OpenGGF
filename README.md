# OpenGGF - The Open-Source Java-Based Speedy Erinaceidae Engine

> This project is a work in progress. For the current state, please see the latest version in the
> Releases section of this document.

## Introduction

OpenGGF is a community-made, fan-made, open-source Java game engine for research and preservation
of classic Mega Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog
series. It aims to faithfully reimplement the physics and rendering behaviour of the original
hardware using data loaded from user-supplied ROM images. The project's primary goal
is accuracy: physics, collision, and audio are all verified against community-maintained
disassemblies of titles in the Sonic the Hedgehog series. No copyrighted assets are included in
this repository; a legally obtained ROM is required to run the engine.

The engine also aims to provide modern tooling such as a level editor and an open framework for
modding and customisation.

> **Disclaimer:** OpenGGF is a community-made fan project. It is not affiliated with, sponsored by,
> approved by, or endorsed by Sega. Sonic the Hedgehog and all related characters, names, and
> trademarks are the property of Sega Corporation. No ROM images or other copyrighted game data are
> included in this repository. Users must supply their own legally obtained ROM files to use this
> software.
>
> The disclaimer is also shown in-engine on startup; it can be disabled by setting
> `startup.legalDisclaimer: false` in `config.yaml`.

## User Guide

A comprehensive user guide is available in [`docs/guide/`](docs/guide/index.md), covering:

- **Players:** [Getting started](docs/guide/playing/getting-started.md), [controls](docs/guide/playing/controls.md), [configuration](docs/guide/playing/configuration.md), [game status](docs/guide/playing/game-status.md), and [troubleshooting](docs/guide/playing/troubleshooting.md).
- **Contributors:** [Dev setup](docs/guide/contributing/dev-setup.md), [architecture overview](docs/guide/contributing/architecture.md), [adding zones](docs/guide/contributing/adding-zones.md), [adding bosses](docs/guide/contributing/adding-bosses.md), [audio system](docs/guide/contributing/audio-system.md), [testing](docs/guide/contributing/testing.md), and [trace replay testing](docs/guide/contributing/trace-replay.md).
- **Cross-referencers:** [68000 primer](docs/guide/cross-referencing/68000-primer.md), [mapping exercises](docs/guide/cross-referencing/mapping-exercises.md), [per-game notes](docs/guide/cross-referencing/per-game-notes.md), and [tooling](docs/guide/cross-referencing/tooling.md).

Contributor tests are JUnit 5 / Jupiter only. Do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

## Configuration

The engine reads runtime settings from `config.yaml` in the working directory. A legacy
`config.json` is migrated automatically on first run. Key bindings can be written either as GLFW
integer codes or as human-readable names such as `SPACE`, `Q`, or `F9`. See
[`CONFIGURATION.md`](CONFIGURATION.md) and the player guide for the full reference.

## Controls

Keyboard and standard GLFW gamepads are supported for gameplay and the basic
startup/title/data-select menus.

### Player Controls

| Key | Action |
|-----|--------|
| Arrow Keys | Movement |
| Space | Player 1 action A / jump |
| Right Shift | Player 2 action A / jump |
| Enter | Pause / unpause |

The bundled `config.yaml` exposes keyboard bindings under `input.pause`,
`input.player1`, and `input.player2`. Keyboard B/C are unbound by default;
gamepads map west/south/east face buttons to Mega Drive A/B/C. On Xbox-style
pads that is X/A/B; on PlayStation-style pads that is Square/Cross/Circle.
Additional bindable inputs, including Start and controller assignment, are
documented in [`CONFIGURATION.md`](CONFIGURATION.md); keys omitted from the
template still use the engine defaults until added explicitly.

### Debug Controls

| Key | Action |
|-----|--------|
| F1 | Show/Hide Debug Overlay (text and bounding boxes) |
| F2 | Show/Hide Shortcuts Overlay |
| F3 | Show/Hide Player Panel |
| F4 | Show/Hide Sensor Labels |
| F5 | Show/Hide Object Labels |
| F6 | Show/Hide Camera Bounds |
| F7 | Show/Hide Player Bounds |
| F8 | Show/Hide Object Points |
| F9 | Show/Hide Ring Bounds |
| F10 | Show/Hide Plane Switchers |
| F11 | Show/Hide Touch Response |
| F12 | Show/Hide Art Viewer |
| Page Up | Cycle Acts (`debug.keys.nextAct`) |
| Page Down | Cycle Zones (`debug.keys.nextZone`) |

`F9` is also the default level-select shortcut (`debug.keys.levelSelect`), so it
can both open level select and toggle ring bounds while debug overlays are enabled.

### Editor Controls

| Key | Action |
|-----|--------|
| Shift+Tab | Toggle between gameplay and the experimental editor overlay (`debug.flags.editor` must be `true`) |
| F5 | Restart the playtest from editor mode |

## FAQ

### What does "GGF" stand for?

Gotta Go Fast!

### Is this an emulator?

No. OpenGGF is an independent reimplementation of the game logic and physics, written in Java
from scratch. It does not emulate the Mega Drive CPU or VDP. Instead, it reads data (level
layouts, art, music) from original ROM images and runs its own implementation of the game rules.
The implementation is developed and verified against the community-maintained disassemblies
([s1disasm], [s2disasm], [skdisasm]) to achieve pixel-accurate behaviour. The audio engine is a
partial exception: it features software emulation of the YM2612 FM synthesiser and SN76489 PSG
chips (based on [libvgm] and [Genesis Plus GX] reference cores) driven by a reimplemented SMPS
sound driver.

[libvgm]: https://github.com/ValleyBell/libvgm
[Genesis Plus GX]: https://github.com/ekeeke/Genesis-Plus-GX

[s1disasm]: https://github.com/sonicretro/s1disasm
[s2disasm]: https://github.com/sonicretro/s2disasm
[skdisasm]: https://github.com/sonicretro/skdisasm

### Which games are supported?

| Game | Status |
|------|--------|
| Sonic the Hedgehog (S1) | Most complete. Includes all zones, bosses, special stages, title screen, ending, and credits. |
| Sonic the Hedgehog 2 (S2) | Broadly playable. Includes all zones, bosses, special stages, Tails AI, ending, and credits. |
| Sonic 3 & Knuckles (S3K) | Work in progress. The Sonic/Tails path has completed AIZ through LBZ coverage, but S3K remains the main active development area. |

Work is ongoing across all three games. See `CHANGELOG.md` for detailed, per-merge history.

### Where do I get ROMs?

We do not supply ROM images. You must provide your own legally obtained copies. The engine expects
these specific revisions, placed in the working directory:

| Game | Expected filename | Expected revision and hash |
|------|-------------------|----------------------------|
| Sonic 1 | `s1.gen` | World, Revision 01; CRC32 `AFE05EEE`; SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 | `s2.gen` | World, Revision 01; CRC32 `7B905383`; SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K | `s3k.gen` | World lock-on combined ROM; CRC32 `63522553`; SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` |

Other revisions (REV00, etc.) are untested and will likely produce incorrect results, as
ROM addresses are verified against these specific builds. ROM filenames are configurable via
`config.yaml` (see `roms.sonic1`, `roms.sonic2`, and `roms.sonic3k`).

### What is cross-game feature donation?

A feature that lets a donor game (S2 or S3K) provide player sprites, spindash mechanics, sound
effects, and the data select (save/load) screen while you play a different base game (e.g.
Sonic 1). This means you can play S1 levels with S2's Sonic and Tails sprites, spindash, and
sidekick AI — and when S3K is the donor, you also get the full S3K data select screen with
save slots and team selection before gameplay begins.
When S3K is the donor, that donated data select now also uses host-specific emerald presentation
and runtime-generated S1/S2 zone preview screenshots. Data select donation is only enabled when
`crossGame.enabled` is `true` and `crossGame.source` is `"s3k"`. Enable it in
`config.yaml`:

```yaml
crossGame:
  enabled: true
  source: "s3k"
```

Both the base game ROM and the donor game ROM must be present.

### Why Java?

We knew Java, and nobody had done it before. Every other Sonic engine reimplementation out there is
written in C, C++, or C#. A Java implementation proves it can be done on a managed runtime, and
the JVM's cross-platform nature means it runs on Windows, macOS, and Linux without platform-specific
builds (though a GraalVM native image is also available for those who prefer it).

### Will Sega shut this down?

This project contains no copyrighted material. No ROM data, sprites, music, or other Sega assets
are included in the repository. The engine is an independent reimplementation, developed and
verified against the community-maintained disassemblies, that requires users to supply their own
legally obtained ROM files. We have no affiliation with Sega and make no claim to any of their
intellectual property.

### What platforms does it run on?

Anywhere Java 21 and LWJGL run: Windows, macOS, and Linux. The engine uses OpenGL 4.1 core profile
(chosen for macOS compatibility). A GraalVM native image build is also supported for ahead-of-time compiled
binaries.

### Did you use AI to write this? / This is AI slop!

Various agents (Claude, Codex, and Gemini, in various models, versions and forms) have all been used at various points in the project's history, and
the commit history doesn't hide it; you'll see `Co-Authored-By` tags throughout. But the project
has been in development since 2013, long before AI coding assistants existed.

The pre-AI core — the engine framework and architecture, the rendering pipeline, the physics
engine and its subpixel movement model, and the sensor-based collision system — was designed and
coded by hand over years, long before any agent touched the repo. Other subsystems were built
with heavy AI assistance under direct human oversight; the SMPS audio engine, in particular, was
AI-built and steered against reference implementations rather than hand-written. AI was brought in for bulk analysis and research, to accelerate
object and boss implementation, debugging, validation, and unit tests; all with accuracy verified
against the original ROM disassemblies. Every commit is reviewed, tested, and corrected where
needed.

[You can't prompt your way to ROM accuracy (yet!)](docs/AI_JOURNEY.md). But we certainly prompted our way through object
implementations, research and boilerplate code a lot faster than would have been possible by hand.

For the visual version of that story, the [Development Timeline](docs/project/development-timeline.md) is a
captioned gallery of real dev builds — bugs and all — from a 2015 white-box prototype through to
the present, including the audio engine slowly un-mangling itself.

### How can I contribute?

The project is open source. Start with [`CONTRIBUTING.md`](CONTRIBUTING.md), then check the issue
tracker, OBJECT_CHECKLIST.md for unimplemented game objects, and CHANGELOG.md for the current state
of each game. The codebase uses a provider-based architecture that makes it relatively
straightforward to add new objects, zones, and game-specific behaviour.

## Releases

### v0.6.prerelease (Current development snapshot)

Development since `v0.5.20260411` is the active 0.6 prerelease line. The release focus is S3K playable vertical-slice parity, trace-driven ROM accuracy, release hardening, and gameplay-scoped rewind reliability.

Highlights:

- **Key bindings carry their own modifiers (2026-07-25):** a binding was a bare key code, so any shortcut that wanted a modifier hardcoded it at its call site — `capture.toggleKey` let a player change the key while the Shift it was really pressed with lived in `Engine`, where it could be neither seen nor changed. `KeyChord` now parses and formats `"CTRL+SHIFT+O"` and `"META+LEFT_BRACKET"`, matching a binding's declared modifiers exactly and requiring the others released, so a plain `"O"` no longer fires while Ctrl is down. Everything that parsed before still parses to the same key with no modifiers, so existing `config.yaml` files keep their meaning, and an existing `capture.toggleKey: O` migrates to the new `SHIFT+O` default automatically. Three review rounds each found the same failure class in the round before it — a fix applied wider or narrower than the defect it targeted, under a comment asserting the result was safe — so the closing round was scoped deliberately against that pattern, with every fix verified RED by reverting it. The debug-only Ctrl+P stats copy stays behind the `debug.viewEnabled` gate and the overlay toggle stands down for it only while it can actually run; promoting the copy above the gate to keep the keystroke alive had instead given every shipped install a shortcut that silently overwrote the OS clipboard. That copy also now requires the **left** Ctrl, because `CTRL` in a chord means either one and right Ctrl is player two's default Start — the same oversight as right Shift being player two's jump, one key over. The unbound-binding guard moved out of a single call site into `InputHandler.isKeyDown` itself, closing it for the six callers that never guarded: with `rewind.liveKey` unbound, both sides of the pad-substitution comparison were `-1`, so a held bumper reported every unbound binding as held, and player one's B and C ship unbound. And the guard that polices the per-binding modifier table now follows a read hoisted into a local, which was the dominant call style in the one method it existed to sweep.
- **Harness suite runs in parallel and can be gated by game or movie — 957s to 371s (2026-07-25):** the native differential suite replays ~1.4 million frames of real Genesis emulation, and ran them one capture at a time. It now runs across a worker pool: **371s at the default `--jobs 8`**, with a per-test outcome set identical to `--jobs 1`. The scheduling policy is deliberately one line — order longest-first from a recorded timings file — because the 466,334-row S3K complete-run gate takes 368s on its own and no concurrency makes a full run shorter than that; `--jobs 4` measures 388s, so 8 is comfortable headroom rather than a throughput claim. Memory is deliberately not modelled, since a capture is now flat in RSS regardless of movie length. New selection flags `--game`, `--movie`, `--gates-only` and `--no-gates` compose with the existing `--filter`, and a `--no-gates` tier runs in 4s. Parallelising surfaced three real hazards, all fixed rather than worked around: `NativeStandardOutputSilencer` `dup2()`s `/dev/null` onto fds 1 and 2 process-wide, so the first parallel run destroyed concurrent output and reported 34 of 352 tests while exiting 0 — the CLI-driving classes are now serial; gate scratch moved off `/tmp` (a 16 GiB tmpfs that concurrent gates filled, three failing with ENOSPC) to `.scratch/`; and the runner now fails a run whose result count does not match its selection, so a suite that loses a test can no longer exit 0.
- **A recording started on the master title screen keeps its audio into gameplay (2026-07-25):** entering a game rebuilds the audio presentation *twice*, and the first rebuild is not the backend swap — `Engine.exitMasterTitleScreen` runs `resetForGameplayFromMasterTitle`, and its `AudioManager.resetState()`, before the real backend is installed. The previous fix carried the recording's audio lease across the backend swap only, so `resetState` still retired it first and the recording was already dead by the time the carry could apply; the reported `IllegalStateException: Live capture audio handle is no longer attached` was never actually removed. The recorder caught it, logged one warning for the entire session and substituted phase-correct silence, so the recording did not fail — it just wrote a silent audio track, which is worse. A mode transition rebuilds the presentation; it does not end a recording of the window, so `resetState` now marks the lease for rebind on the same terms as a backend swap, and only `destroy()` — the genuine teardown — retires it. That also carries a recording across a level restart, for the same reason. The cause was found by measuring the real `AudioManager`, producer, music source and recorder across the transition *before* editing: that measurement ruled out both standing hypotheses (a packet-size change, then a sample-rate change — OpenAL negotiates the same 48 kHz the title screen assumes, so the geometry never moves) and reproduced the reported failure byte for byte. The same measurement surfaced a second defect: on a genuine rate change the carry threw `IllegalArgumentException: snapshot clock rate does not match this clock` straight out of the presentation builder, which most of `AudioManager` reaches. A recording is muxed at the rate captured when it started (ffmpeg `-ar`), so following the producer across a rate change would write pitch-shifted audio that looks like it worked; the lease is now retired with a warning naming both rates, and nothing escapes the rebind. The regression test was the other half of the problem — it asserted a *frame count* against a backend that produced silence anyway, so it passed identically whether audio flowed or not, which is why the partial fix looked verified. It now asserts the PCM the recorder is actually handed, driven by a real music source through a real producer, with recorder and speaker proven byte-identical on one packet.
- **Run captures stream to disk and gzip on the way, cutting peak memory 85% (2026-07-25):** `S1RunCaptureRunner` buffered a whole segment and `S2RunCaptureRunner` the whole run in UTF-16 `StringBuilder`s before publishing anything, so the S2 complete-emeralds capture peaked at 1.51 GiB RSS — an order of magnitude above the *larger* S3K complete-run pass, purely because that one already streamed. Both run machines were shown to guarantee every armed segment reaches exactly one finalize, so the buffers were protecting nothing and no staging-file fallback was needed either; both runners now write rows straight through a shared segment sink. Measured peak RSS: S2 complete-emeralds **1,581,460 kB → 232,716 kB** and slightly faster (3:41.8 → 3:34.0), S1 complete-run 620,728 → 231,532 kB (1:36.4 → 1:27.9). Memory is now flat regardless of movie length — the ~231 MB residue is the emulator core itself. Compression additionally moved *into* the stream: a streamed payload is written through a gzip stream into its staging file, so the uncompressed form never exists on disk (S1 complete-run staging 638 MB → 36.8 MB). Verify-before-destroy is preserved exactly rather than weakened for the streaming — the plaintext is SHA-256'd and counted on its way into the compressor, and the finished gzip is decompressed and compared by hash *and* length before it may join the publication set.
- **Trace replay test harness deduplicated (2026-07-25):** ~660 lines of comparison, diagnostic-string and report-writing code duplicated across `AbstractTraceReplayTest`, `AbstractCreditsDemoTraceReplayTest` and the three per-game special-stage bases are re-homed into `TraceFieldComparisons`, `TraceReplayDiagnostics` and `TraceReportWriter`, with two bespoke probes moved to `S2SkyChaseBadnikDiagnostics` / `S3kSidekickCylinderDiagnostics` and `TestS3kAizTraceReplay`'s repeated config save/restore replaced by an `AutoCloseable` scope. Behaviour-neutrality was proven on the divergence data rather than on pass/fail alone: running one replay per game through each refactored base on both trees produced whole-JSON-identical trace reports, including an 866 KB report carrying 3,258 individual error records. Also removes the dead `verifyFrame` component from `TraceCaptureTool.Args` (zero references repo-wide; the live field is `verifyFrames`).
- **Trace payloads compress at capture, and an uncompressed one can no longer be committed (2026-07-25):** trace payloads must never reach a commit uncompressed — an S3K complete-run aux stream is ~254 MB raw against ~12 MB gzipped (~21×), so an uncompressed one is past GitHub's 100 MB per-file hard limit, and hook-enabled full runs have breached this before. Two independent enforcement points, neither sufficient alone. The native harness now gzips `physics*.csv` / `aux_state*.jsonl` inside `NoReplacePublisher`'s all-or-nothing publication, porting `tools/traces/compress-traces.ps1`'s semantics — same name patterns, same 1 MiB threshold, and the same verify-before-destroy ordering, where the gzip is decompressed and compared by SHA-256 *and* length before the staging file is discarded. It runs before the first `link(2)`, so a verification failure publishes nothing at all. Compression is on by default (`--no-compress` opts out, `--compress-threshold` retunes) because an opt-in flag fails exactly when someone installing a fixture forgets it; pairing the default with the inherited threshold makes the harness and the repo's commit policy agree by construction. Separately, `TestTraceFixtureCompressionGuard` and a `.githooks/validate-policy` check reject an uncompressed payload committed under `src/test/resources/traces/`, covering the Lua and stable-retro routes the harness cannot, with the 36 pre-existing files grandfathered in an explicit baseline.
- **S3K recorders read the lives counter as a V-blank counter; all 39 fixtures regenerated (2026-07-25):** the second and last defect of the same class as the frame-counter fix below. Both S3K recorders sampled `0xFE12` for `vblank_counter`, which is `Life_count`; `V_int_run_count` is a `ds.l` at `0xFE0C`, so the correct low word is `0xFE0E` — exactly what the S1 and S2 recorders have always used. The column consequently held two to four distinct values across entire multi-hour runs, the lives count in the high byte. Both recorders are fixed, all 39 S3K fixture directories regenerated (35 carry the column; the four special-stage-profile dirs do not), the native port unified, and every S3K differential gate re-pinned. **No frontier moved**: all 15 replay classes report byte-identical first non-camera divergences, and the special-stage suite stayed green. The change was predicted before it was made and the prediction held — rows where the gameplay frame counter and lag counter both plateaued would flip `FULL_LEVEL_FRAME` → `VBLANK_ONLY`, forecast at 2,094 for `hcz_completerun` and 0 for five named fixtures, measured at 2,091 and exactly 0. It also turned a long-red test green: `TestTraceExecutionModel` asserts that a V-blank ran while `Level_frame_counter` did not, which was unprovable while the column read a frozen `0x0B00` — eleven lives. The assertion was right; the data was wrong.
- **S3K standard recorder read a dead RAM address; release-slice fixtures regenerated (2026-07-25):** `s3k_trace_recorder.lua` sampled `0xFE08` (`Debug_placement_mode`) instead of `0xFE04` (`Level_frame_counter`), so `gameplay_frame_counter` and every aux `vfc` / `level_frame_counter` field was constant zero for the recorder's entire life — meaning the AIZ→HCZ, CNZ and MGZ replay frontiers, the primary release slice, were being diagnosed against a value the ROM never produces. `6564667eb` had fixed the identical bug in the sibling complete-run recorder four days earlier; it did not propagate, because each of the six recorders carries its own copy of these constants. The recorder is fixed (v6.31-s3k), the three canonical fixtures are regenerated with every delta categorised against a named cause before installing, and the native port's now-redundant per-profile address fork is deleted. **All three frontiers held** — first divergences unchanged at f2696 `x_speed`, f185 `y_speed` and f5164 `air` — confirming no engine regression. The regeneration also exposed a test-harness defect: `TestS3kAizTraceReplay`'s private stepper mishandled `ADVANCE_ONLY` rows that a dead-zero counter had previously made unreachable, running non-ROM level ticks through the AIZ plane-intro prefix; routing scenario frames through the same closure driver the whole-trace loop already used took that class from 14 failures to 1. A cross-recorder audit of all six recorders' ROM constants now lives in `tools/bizhawk/SHARED_MODULE_HANDOFF.md` alongside the original extraction plan, together with one remaining defect of the same class — `ADDR_VBLA_WORD` reads `Life_count` in both S3K recorders — left queued so its own fixture regeneration stays attributable.
- **Native headless GPGX S3K complete-run recorder — the Lua recorder fleet is fully migrated (2026-07-25):** the native harness now records Sonic 3 & Knuckles complete-run, bonus-stage and special-stage traces, replacing `s3k_complete_run_recorder.lua` and completing the migration of **every** Lua recorder (S1, S2, S3K standard, S3K complete-run) to byte-parity-gated native ports. One untruncated pass over the 466,334-row playthrough movie reproduces all seven `*_completerun` segment fixtures byte-identically in 5m57s at 235 MB peak RSS, and `--run-id` reproduces the 25-segment Knuckles multi-bonus run plus its `run_manifest.json` and the four published standalone bonus/special-stage fixtures — physics, aux and manifest compared by raw sha256 with zero normalization, metadata differing only in `recording_date`. Adversarial review caught an input column indexed by last-applied frame rather than BK2 row, and a whole-run in-memory buffer. The legacy `runs/s3-knux-multibonus-ss/` fixture set — a 2026-07-19 Windows capture three recorder versions behind, un-reproducible by any current recorder because of CRLF line endings, a dead-zero frame-counter column and armed diagnostic hooks — was regenerated at 6.32 so it too gates byte-exactly. That recapture exposed and fixed a real engine defect: trace replay seeded `LevelManager`'s frame counter from the first driven row instead of the previous completed frame, running every frame-counter-keyed object phase one frame ahead of ROM, which a dead-zero counter column had hidden since the contract was written.
- **Native headless GPGX S3K standard trace recorder (2026-07-25):** the native Linux/Mono harness now records Sonic 3 & Knuckles standard traces in both canonical profiles — `aiz_end_to_end` (the AIZ1 → AIZ2 → HCZ handoff, surviving the in-level reload that ends other profiles) and `level_gated_reset_aware` (arm/discard/re-arm across soft resets, stopping on zone-leave). Output is byte-identical to `s3k_trace_recorder.lua` on all three canonical fixtures — AIZ end-to-end (20,798 frames), CNZ (42,253) and MGZ (35,912) — each locked by a permanent ROM-backed differential gate with zero normalization on `physics.csv` and `aux_state.jsonl`, and metadata deltas pinned exactly per fixture rather than by loose pattern. The hook-driven aux families are explicitly deferred rather than silently dropped: all three fixtures were captured with the diagnostic hooks unset, a test pins that absence against the fixture bytes, and the CLI refuses every unmodeled `OGGF_*` recorder variable instead of producing non-canonical output. Adversarial review widened that refusal from 3 variables to 11 and stopped non-discarding captures from buffering the whole run in memory. `s3k_complete_run_recorder.lua` and its `6.32-s3k-completerun` fixtures remain Lua-only.
- **S2 complete-game trace capture (2026-07-24):** `s2_trace_recorder.lua` v9.13-s2 run mode now survives in-level reloads (deaths, time overs, act and zone transitions) as `death_restart`/`level_advance` manifest transitions instead of ending the run, and its special-stage segments carry the standalone SS recorder's full aux event stream. The native harness mirrors all of it, accepts recorded Genesis FM chip models, and both implementations were proven content-identical on a new 259,590-frame canonical run — Sonic+Tails completing the game with all 7 emeralds, installed at `traces/s2/runs/s2-sonic-tails-complete-emeralds/` (35 segments, 34 transitions) with a permanent per-segment differential gate; the halfpipe fixture set was regenerated for the enriched SS aux.
- **Native headless GPGX S1 complete-run and run-mode recorder (2026-07-24):** the native harness now covers `s1_complete_run_recorder.lua`: one pass over the canonical 195,493-row complete-run movie reproduces all 19 `*_completerun` fixture segments byte-identically, and `--run-id` run mode reproduces the GHZ maze round trip (level → special stage → level, giant-ring transitions, `run_manifest.json`) plus the standalone special-stage fixture with zero normalization beyond `recording_date` and the pinned version-marker line. Nine permanent ROM-backed differential gates now guard S1 and S2 capture parity; adversarial review removed a duplicated stage-free capture engine in favor of the shared run-mode runner.
- **Native headless GPGX S2 trace recorder (2026-07-24):** the native Linux/Mono harness (`tools/bizhawk-headless/`) now records full canonical Sonic 2 traces in all three `s2_trace_recorder.lua` modes — plain `gameplay_unlock`, `level_gated_reset_aware` with `--gameplay-segment` selection, and `--run-id` run mode with special-stage detours and `run_manifest.json`. Output is byte-identical to the Lua on four canonical fixture sets (EHZ1 full run, ARZ segments 0 and 1 from one movie, and the EHZ half-pipe round trip's five segments plus manifest), each locked by a permanent ROM-backed differential gate; a second S1 gate against the canonical MZ1 full run was added alongside. Adversarial review also aligned the plain-mode movie-end stop ordering with the Lua's post-advance `on_frame_end` semantics on paths the fixtures cannot exercise. The Lua recorders remain the reference implementation and the non-Linux capture path.
- **Native headless GPGX S1 trace recorder (2026-07-24):** a Linux/Mono C# harness (`tools/bizhawk-headless/`) now drives BizHawk 2.11's GPGX core directly — no EmuHawk, X11, or Lua — and records full canonical Sonic 1 traces (`--mode trace`: physics.csv v7, aux_state.jsonl, metadata.json trace_schema 4) with auto-detected BK2 frame offset. Output is byte-identical to the Lua `s1_trace_recorder.lua` on the canonical GHZ1 fixture (only `recording_date` differs), gated by a permanent ROM-backed differential test; adversarial review also fixed a movie-exhaustion end-path off-by-one the fixture could not exercise. The Lua recorder remains the reference implementation and the non-Linux capture path.
- **S3K structural trace replay bootstrap (2026-07-23):** S3K replay no longer relies on `pre_level_intro_prefix`, `sidekick_seed_frame_prelude`, or `pre_trace_osc_frames` metadata, nor on frame-zero motion-shape heuristics. Fresh level starts preserve the ROM's grounded first-dispatch lifecycle, AIZ pre-level input-only rows no longer tick the resident level across headless/capture/live/rewind playback, and the regenerated AIZ CSV input column now follows the canonical BK2 offset contract. The standalone AIZ and CNZ traces clear their fixture/input/bootstrap regressions and reach later true parity frontiers at AIZ f2707 and CNZ f185; the broader remaining S3K fleet debt stays explicit in the frontier log.
- **S3K universal CSV v7 fixtures regenerated (2026-07-23):** standalone, complete-run, bonus, and special-stage recordings now install physics, aux, and metadata from the same BizHawk 2.11 capture instead of mixing v7 CSV/metadata with stale v5 aux. S3K recorder execution hooks are opt-in for focused diagnostics, Linux captures suppress Mono Lua-console repaint churn by default, and replay input sampling follows each profile's physical BK2-row convention. The consistent fixtures expose new AIZ/CNZ comparison frontiers for follow-up rather than failing at the former input-alignment guard.
- **Configurable recording codecs, container and ffmpeg commands (2026-07-25):**
  `capture.codec` selects `ffv1` (default), `h264` or `h265` — all three
  lossless — and `capture.audioCodec` selects `flac` (default) or the lossy
  `aac` / `mp3`, marked as lossy where they are configured. `capture.container`
  sets the file extension so MP4 can be written directly. For anything the
  codec keys do not cover, `capture.ffmpegPass1Args` and
  `capture.ffmpegPass2Args` replace either ffmpeg pass outright; emptying the
  second skips muxing and records video only. H.264 and H.265 encode RGB
  directly rather than the conventional `yuv444p`, which is lossless in the
  codec's own colour space but does not return the submitted pixels — a
  measured round trip guards that. The bundled configuration template is now
  also written to `config.yaml.example` on every run, so its comments and
  worked ffmpeg recipes stay visible next to a `config.yaml` that has already
  been written.
- **Recording tells you when it stops, and stops filling `/tmp` (2026-07-25):** a
  recording that ends for a reason you did not ask for — the window being
  resized, or a capture failure — now replaces the red-dot/`REC` indicator with
  a red `REC STOPPED: RESIZED` or `REC STOPPED: ERROR` notice for three seconds,
  instead of the indicator silently vanishing exactly as if you had pressed the
  toggle. Alongside it, three temporary-file faults that between them broke long
  recordings outright: the lossless intermediate was written to the system temp
  directory rather than beside the finished file, ffmpeg's own diagnostics were
  discarded so a full disk surfaced only as `Stream closed`, and building an
  input handler without a configuration leaked a directory per call — hundreds
  of thousands of them across test runs. Tests now clean up after themselves,
  with a guard to keep them honest and the test JVM's temp directory pointed
  inside `target/`.
- **Unified audio presentation (2026-07-25):** every audible source — SMPS music
  and SFX, fallback WAV, pitched SFX, and raw SEGA PCM — is now mixed by one
  allocation-free presentation producer that owns cadence, final PCM, history,
  rewind, and capture taps. Each presented frame chooses exactly one forward,
  silent, or reverse audio mode, and OpenAL became a bounded sink for that one
  final packet rather than a set of independent sources. The speaker and any
  recording receive independent views of the same producer-selected packet, so
  toggling a recording can no longer remove music, rings, or effects, and a
  recording started mid-rewind picks up the next audible reverse packet. The
  temporary deterministic-runtime and recording-lease switches this replaced
  are removed, and offline trace capture now records the same final packets as
  live recording. ROM-backed tests assert non-zero final PCM for Sonic 1, 2 and
  3&K across title, gameplay, ring and special-stage routes.
- **Lossless live viewport recording (2026-07-23):** press `Shift+O` to toggle a
  viewport-only MKV recording during normal play. OpenGGF writes synchronized
  FFV1 video and stereo FLAC audio—including pause/frame-step silence and
  rewind presentation—to `capture.outputDir`; `ffmpeg` must be available on
  `PATH`. A red-dot/white-`REC` indicator appears in the window while active
  but is excluded from both the recording and F12 screenshots. Changing the
  viewport or framebuffer size stops and finalizes the current file. If the
  audio tap fails mid-recording the video continues with phase-correct stereo
  silence rather than aborting the file. This is independent of the Shift+F9
  input/movie recorder; see
  [CONFIGURATION.md](CONFIGURATION.md#capture) for configuration and output
  details.
- **CPZ2 and DEZ trace regressions restored (2026-07-23):** Sonic 2 automatic Tails recovery flight now clamps its delayed Y target to the gameplay waterline using the effective ROM feature-zone key, matching `TailsCPU_Flying_Part2` and closing CPZ2's f7206 CPU-target mismatch. DEZ's title-card bootstrap now selects Tornado ordering only when a live ROM-loaded ObjB2 exists, preserving the native level-start anchor without hydrating trace snapshots; both complete level-select traces and the focused S2 bootstrap suite are green.
- **Sonic 2 sidekick trace regression cleanup (2026-07-23):** trace replay now respects the recorded per-frame sidekick-presence bit when SCZ/WFZ suppress the configured Tails sprite, while dormant CPU RAM remains available for diagnostics. The S2 fresh-render-entry counter delay is also constrained to its native lower-boundary state, restoring CNZ2, MCZ2, and OOZ1 without regressing OOZ2 and advancing CPZ2 to its later independent CPU target frontier.
- **ICZ replay frontier completed (2026-07-22):** the ICZ trace-fidelity lane closes its recorded replay frontier with ROM-state-driven corrections across the boss, frozen-block and freezer lifecycle, moving and tension platforms, snow/steam scheduling, terrain handoffs, and end-of-act ownership. The accompanying rewind lifecycle correction drops a freezer parent's detached capture-cloud identity when its slot unloads, preventing stale child references from being restored.
- **Sonic 1 100% whole-movie trace playback (2026-07-22):** the complete-run recorder and manifest-driven chain now carry a 225,104-input-frame movie through repeated level arms, deaths, all six emerald stages, glitch-heavy MZ/SLZ routing, Final Zone, credits, and the post-credits return to the title screen. The terminal 10,943-row tail reports `finalMode=TITLE_SCREEN`; ROM-state fixes made along the route cover object lifecycle, collision/contact cadence, event state, title-card/transition handling, rewind restoration, and Final Zone cylinder/plasma/boss timing. Comparator mismatches remain explicit parity frontiers rather than being hydrated or route/frame-carved out.

- **Develop red-suite remediation and origin integration (2026-07-21):** the frozen 36-method develop red set is green after rewind-graph closure, canonical touch/physics/lifetime ownership fixes, behavior-neutral manager extractions, ROM-timed CNZ/MGZ fixture corrections, and a bonus-stage transition coordinator that restores checkpoint, ring, water, and persistent respawn state before fresh object materialization. The branch also incorporates the latest `origin/develop` respawn-persistence work, including failure-safe one-shot cleanup. Verification completed with the owning guard/package matrix and two consecutive full-suite runs of 12,473 passed, zero failures/errors, and 15 fixture-dependent skips.

- **Respawn-remember table persists across the star-post bonus round-trip — seg2 chain 11651 -> 5968 comparator errors (2026-07-21):** objects the player broke or collected before entering a star-post bonus now stay broken/collected on return, matching the locked-on ROM where Respawn_table_keep shields the respawn and ring tables through the bonus reload. The triple-proven root (BizHawk PC-execute probes + ROM byte disassembly + the recorded break event): a monitor broken on the first AIZ pass reloaded intact and solid in the engine, forming a phantom wall the ROM's broken shell never presents — the recording's monitor reloads as an inert Sprite_OnScreen_Test stub. The placement controller's remembered/stay-active state is now captured at bonus entry and restored after the return reload. A matching death-respawn latent gap is documented as a cited follow-up.

- **Trace-run tooling cleanup + monitor pass-through parity (2026-07-21):** the trace capture tool now rejects run-manifest entries with a clear message instead of failing opaquely mid-capture, the run catalog defends against synthetic fixtures appearing under a future runs directory, and the chain harness's comparator-frame-base contract is consolidated into one authoritative javadoc (ending a class of repeated diagnostic misreads). Separately, monitors now model the locked-on ROM's Knuckles exemptions: a gliding or post-glide-sliding Knuckles passes through and breaks monitors instead of being blocked, with the character gate keeping Sonic's insta-shield unaffected. (The seg2 chain frontier's broken-monitor respawn-persistence fix — triple-proven root, design approved — remains in flight on its branch; the probe evidence is banked in tools/bizhawk.)

- **Helper-state rewind coverage guard (2026-07-21):** a third coverage lane in the rewind analyzer now verifies that every final helper-object field on a spawnable object either routes through the capturer's own public capture predicates (explicit codecs, RewindStateful, or the name-heuristic plain-state-holder path) or is deliberately policy-exempt — closing the blind spot where a helper class whose name misses the heuristic, or a future non-codec field knocking a holder off the in-place path, would silently lose state across rewind. A codebase-wide sweep confirmed zero existing gaps, so the guard's baseline starts empty and any future violation fails immediately. The guard's first dry run itself caught an under-specified filter in the design (the policy-registry gate), corrected before landing.

- **AIZ ride-vine held-player animation parity — mega-run chain AIZ segment 56 -> 4 comparator errors (2026-07-21):** the vine hold force-rewrote the hang animation every frame, but the ROM writes it once at grab and never again — so the first floor contact during a hold runs the player's touch-floor routine and its result latches for the rest of the grab. The fix models the disassembly's actual two-gate structure (verified from the full Knuckles touch-floor body): a Status_Roll-gated walk reset (fires only when the player grabbed while rolling) and a separate glide-family reset that a held vine player can never trigger, plus the swing branch's unconditional walk write. Rewind coverage rides the existing automatic plain-state-holder capture (no bespoke snapshot), with the new latch fields pinned by a test extension. Adversarially reviewed through two rejection-driven iterations that caught a real cross-character regression (a not-rolling grab must keep the hang animation) and corrected the capture-mechanism approach.

- **CNZ Act 2 rival-Knuckles encounters and magnetic end boss corrections (2026-07-21, parallel session):** both rival-Knuckles cutscenes run their native raw animation scripts with exact camera stops and facing bits, the subtype-6 button decodes its native start/width pairs and drives the whole-scene shake in sync with the Knuckles theme, the magnetic end boss runs the locked-on ROM encounter path with native SFX cadence, hover fans stop unrelated PLC refresh work, spring init-only SST execution is preserved, and the CNZ complete-run reaches full physics/animation green on its recorded route.

- **Knuckles push-animation parity — mega-run chain AIZ segment 168 -> 56 comparator errors (2026-07-21):** the Knuckles animation profile was missing the flag that routes Status_Push through the ROM's walk-script sub-handler, so pushing Knuckles either advanced held mapping frames early (at rest) or exposed a PUSH anim byte the ROM never publishes (while moving) — one omission, two symptoms across all twelve recorded push windows. The fix also models a genuine Knuckles/Sonic handler difference found in the disassembly: Knuckles reloads its push-freeze timer with a >>8 shift where Sonic uses >>6, captured as a per-character profile field with the default preserving byte-identical behavior for every other character and game. Character-profile-scoped; verified regression-free across the S1/S2 chains, S2 level replays, and all six stage comparators. Remaining AIZ-segment residuals (vine release timing, push release lag, glide-anim lifecycle, a roll-path ordering frame) are triaged with disasm-cited briefs and assigned to lanes; the frontier-log diagnosis narrative is calibrated to separate directly-observed evidence from inference.

- **Gumball chain interior 3709 -> 9 comparator errors + a live-play ring carry-over fix (2026-07-21):** the mega-run chain's first bonus interior closed on two roots — the BONUS title-card-exit fall-through frame is the interior's first gameplay tick, but the forced-input bridge armed only after the mode flip, so that tick read neutral input and the player free-fell instead of taking the recorded grounded nudge (the bridge now re-arms in the bonus branch of exitTitleCard); and bonus-stage exit now restores the returning level's rings from the interior's live HUD count, modeling the ROM's Ring_count -> Saved_ring_count exit copy (the gumball ring ball's transient +20 to the saved count is discarded exactly as the ROM discards it — the former reward-sum reconstruction over-carried by 10). The recovered interior RNG prime (segment-entry seed applied at the chain boundary via the standalone bootstrap seam) proved out: the ball series now replays faithfully. The chain clears the full gumball round trip and the following AIZ segment; the logged frontier is the ROM's ~150-frame post-catch exit choreography, which the engine still shortcuts.

- **Star-post bonus round-trips made ROM-faithful in the chain (2026-07-21):** three roots — the chain driver no longer pre-seeks the live-cursor bonus interiors (that was an SS-only need), star posts now keep a persistent activation mark modeling the ROM respawn bit (the ROM zeroes the checkpoint index on bonus entry and never restores it — the respawn bit is what prevents re-triggering), and the bonus return restores the player to the star post's recorded position rather than the live touch centre. The mega-run chain now clears the gumball interior's boundary/checkpoint/positional assertions; the remaining root is interior RNG fidelity under organic entry.

- **Knuckles glide activation freed from a Sonic-only gate (2026-07-21):** the glide branch sat behind the invincibility check modeling Sonic_FireShield's Status_Invincible test — the ROM's Knux_Test_For_Glide carries no such suppression, so gliding while star-invincible was wrongly refused. With the branch reordered, the mega-run chain replays the entire AIZ Knuckles segment into the first bonus stage; the frontier moved to the chain driver's bonus-interior exit handling.
- **Knuckles glide-slide landing parity (2026-07-21):** the glide->fall->slide landing now matches the ROM bit-exactly — the slide runs airborne-flagged as loc_1693E never clears Status_InAir, the floor probe applies Sonic_CheckFloor's odd-angle rule, and the fall/slide run move-before-accel in ROM order (the prior ordering biased every fall by one air-accel step per frame). Fixes the mega-run chain's vine-grab miss; the AIZ frontier advanced to a distinct glide-activation root now under investigation.

- **S2 halfpipe round-trip chain GREEN + a live-play oscillator parity fix (2026-07-20):** the ROM only advances the global oscillator inside `Level_MainLoop` — never during title-card wait loops — but the engine ticked it every locked title-card frame, phase-offsetting every oscillation-driven platform after any title card. Holding it at the ROM baseline through the title card (cited: s2.asm:4914-5108, mirrored in S1) closes the S2 chain end-to-end through both halfpipe cycles. Two of three chain tests are now green; the S3K mega-run chain remains at its Knuckles glide frontier. Documented follow-up: title-card duration parity for CPU-sidekick catch-up diagnostics.

- **Chain-replay foundation for trace runs (2026-07-20):** the run walker is now fully manifest-driven (no hardcoded segment counts), with per-entry-kind boundary assertion helpers spanning all four transition kinds, a derived step-cap that turns frozen-cursor hangs into diagnostic failures, and a named seam for per-frame special-stage comparison. Three chain tests consume the committed S1/S2/S3K runs (the S1 maze round trip reached green on its lane); the S2 special-stage-return handoff was rebuilt (cursor pre-seek, input-override release, fall-through comparator attach — the base-class supersets from both lanes reconciled), and the chains exposed genuine engine frontiers now logged: the SS-return title-card duration drifts the free-running oscillator phase versus the ROM, and the Knuckles mega-run surfaced glide/vine parity gaps (its lane banked Knuckles glide centre/sensor/anim fixes and interior reports now write before boundary asserts throw). Real fixes banked en route: manifest act indexing into level loads, Knuckles glide activation/anim fidelity, TraceReplayDriver ground-snap contract.

- **Pachinko bonus comparator GREEN — ALL SIX STAGE COMPARATORS AT ZERO (2026-07-20):** pachinko closed 391 -> 0 through the reward subtype/ring coupling (the ROM awards a shield, not rings, from the recorded orb), a two-bug bumper bounce compound, flipper catch/ride/launch fidelity, the bumper off-screen self-despawn, touch-response-path bumper collision, a bonus-exit frame skip in LevelFrameStep modeling the ROM Restart_level_flag branch (note: this also applies in live play for all S3K bonus-stage exits — ROM-faithful and review-verified), and rewind-policy registration for the new bumper fields. With gumball, slots, blue spheres, the S1 maze, and the S2 halfpipe interior, every stage trace comparator in the engine now reports zero errors.
- **Gumball and slots bonus comparators GREEN (2026-07-20):** gumball closed 74 -> 0 via the spring child's landing-snap override (the shared solid-contact override was clobbering SolidObjectFull2_1P relative placement) and a shared-animation fix — the SWITCH ($FD) end-action no longer eagerly syncs prev_anim, matching Animate_Sonic exactly. Slots closed 182 -> 0 across eight more roots: VBlank-true counter advancement, reward-drain ticking, cage release timing, tile-anchor reconstruction, bumper launch frame alignment, reel-wall flash cadence, and the goal-exit pair — with a routine-override seam so the comparator reads the slot player object routine faithfully.

- **Bonus-stage green campaigns round 2 (2026-07-20):** sixteen more disassembly-cited roots across the three bonus comparators. Gumball's frontier drove f380→f895 (ejected balls now self-poll the ROM's `Check_PlayerInRange` box instead of the generic touch framework; a single wrong push velocity decomposed into three compounding fidelity bugs; the dispense cadence now models the 29-frame `Animate_RawNoSSTMultiDelay` cycle). Pachinko halved again, 896→391, with nine flipper/orb roots (baseline slope, lock/launch ordering, catch animation restart, capture control bits, single-touch item-orb release gate). Slots surfaced a capture-data blocker: reel outcomes seed from `V_int_run_count`, which the recorder stored as a frozen placeholder — the recorder now captures it at bonus-segment arm, the deterministic re-capture verified byte-identical physics rows, and replay primes the reel counter from metadata — the cited reel divergence is fixed, with later reel-cycle roots unmasked for the next round.
- **S3K bonus-stage physics campaign: ten disassembly-cited roots (2026-07-19):** the gumball/pachinko/slots comparators drove out a shared bootstrap ground-snap bug plus nine stage-specific roots — slot-runtime subpixel truncation and fabricated angles, unclamped reversal-decel, cage-capture subpixel preservation, pachinko orbit negate-before-shift ordering and roll-entry height, gumball bumper fallback-bounce removal, inclusive right-edge contact, and RNG reseed provenance. Frontiers advanced from frame 0 deep into each run (pachinko f427+, gumball f380, slots f47+); remaining divergences are catalogued for the next campaign round.
- **S1 maze special stage near-green: 503 → 13 errors (2026-07-19):** eight campaign iterations modeled the ROM's 44-VBlank pre-physics hold (mined from the S2 halfpipe's TRACE_ACCURATE precedent), the mid-hold rotation-init boundary, setup-time palette-cycle advance, byte-truncation-ordered angle negation, the four-cell fall-probe scan, bumper flash-lockout, and the emerald-sparkle exit arming — every fix disassembly-cited. The 13 remaining errors are one exit-ramp/GOAL-approach cluster at the trace tail.
- **S3K blue spheres trace-GREEN (2026-07-19):** the first stage comparator to reach zero errors — four campaign iterations fixed the comparator's stale-RAM frame-0 basis, the player's turn-rotation early-return, per-frame vs stepped-frame routine pacing, and the bumper unlock/perfect-tally branch structure, all disassembly-cited. `TestS3kSpecialStageTraceReplay` now reports 0 errors over the 4,630-row Knuckles capture.
- **All six stage round-trip recordings captured and live (2026-07-19):** the three round-trip movies (S1 GHZ maze, S2 EHZ double-halfpipe, S3K Knuckles multi-bonus mega-run) are captured and committed — 33 segments across three run manifests, including two S2 halfpipe detours in one movie, five slot-machine visits, and three blue-sphere stages with emeralds 0→3. All six stage replay comparators (S1 maze, S3K gumball/pachinko/slots/blue-spheres, plus the S2 interior) now run against real traces; the five new baselines all frontier at frame 0 (spawn/bootstrap state) and seed the stage green campaigns. Recorders gained Player_mode-derived team metadata (Knuckles routes label correctly), multi-detour segment naming, and verified capture-launch documentation.
- **S2 halfpipe round-trip trace recording (2026-07-19):** the Sonic 2 level recorder gains an opt-in run mode (`OGGF_TRACE_RUN_ID`) that captures a level→special-stage→level star-post round trip as a manifest-backed trace run — per-segment directories, an embedded 48-column halfpipe writer with a real lag column, and boundary records carrying the ROM's saved return position and ring/emerald state. The existing level-select workflow is byte-stable without the flag, the hook-based interior special-stage recorder is untouched, and a synthetic three-segment fixture pins the emitter's exact output shape. This completes the multi-stage trace-run slate: every S1/S2/S3K special and bonus stage now has a recording + replay path awaiting its round-trip recordings.
- **S1 maze special-stage trace pipeline (2026-07-19):** the Sonic 1 rotating maze gains the same trace surface as the S3K special stage — a 15-field comparison snapshot, the `s1_special_stage` 14-column schema (16.16 player position, rotation, rings/emeralds), the S1 complete-run recorder v3.15 with the giant-ring detour state machine and run-manifest emission funneled through a single end-of-run finalize, and a single-player VBlank-paced replay harness with delta-based ring comparison. The provider is proven to boot headlessly with no new init hook; the replay test activates when the GHZ maze round-trip recording lands.
- **S3K slot-machine bonus replay (2026-07-19):** the slot machine joins gumball/pachinko in trace replay — the deferred-setup seam builds the slot runtime headlessly, and a camera-focus-keyed sprite seam aligns the comparator with the runtime's player swap (proven by a headless boot test). The replay test activates when a Sonic-solo slots recording lands. Two guard escapes inherited from the previous merge (a naming-rule violation and an unbaselined test setup) were also fixed.
- **S3K blue-spheres trace pipeline (2026-07-19):** the special stage now has a full trace surface — a 16-field comparison snapshot, the `s3k_special_stage` 20-column schema with a twice-re-derived phase-overlay RAM map, recorder v6.31 emitting real `ss/` segments for giant-ring detours, a VBlank-paced replay harness whose finish boundary anchors on the exit-spin completion (covering success and failure exits), and `fresh_load`-driven launch config. The special-stage provider is proven to boot headlessly through the real ROM art path; the replay test activates when the blue-spheres round-trip recording lands.
- **Visual trace-run playback (2026-07-19):** trace runs now appear in the test-mode picker as single entries and play back visually as one continuous session — per-segment comparator/HUD/camera swapping driven by game-mode flips, cursor re-seeks at segment boundaries, pause-on-first-divergence in every segment, and a per-game special-stage launch-config seam. This completes the multi-stage trace-run foundation (plans a-d); stage-interior schemas and the round-trip recordings activate it end to end.
- **Chained trace-run driver (2026-07-19):** trace replay can now drive one continuous engine through level → bonus-stage → level transitions — non-consuming transition peeks, a BONUS_STAGE playback bridge (recorded input + BK2 cursor advance during bonus interiors), a segment walker with boundary-window assertions observed inside the frame-observer callback, and per-segment cursor re-seeks. The round-trip chain test activates automatically once the named bonus recordings land.
- **S3K bonus-stage replay slice (2026-07-19):** the trace framework can now replay gumball/pachinko bonus segments headlessly — a bonus-entry bootstrap seam mirrors the live entry sequence (provider registration, ring restore, HUD, pachinko trap injection), skip-if-missing replay tests activate automatically once round-trip recordings land, and a ROM-backed smoke test proves both bonus zones boot on the level pipeline today. The recording procedure (with the corrected `giant_ring`-selector ring ranges) is documented in the BizHawk tooling README.
- **Multi-stage trace-run foundation (2026-07-19):** trace runs now bundle typed segments under a `run_manifest.json` (new `TraceRunManifest` schema + parser, per-segment run metadata, committed synthetic detour fixture), and the S3K complete-run BizHawk recorder (v6.30) gained a level-family mode guard — fixing silent segment pollution on special/bonus-stage detours — plus a stage-detour state machine that records bonus zones as `s3k_bonus_stage` segments and special-stage passages as merged `giant_ring` transition boundaries. Regenerating the AIZ segment from the committed movie stayed byte-identical; no trace frontiers moved.
- **S3K AIZ/HCZ/MGZ parity polish (2026-07-18):** AIZ waterfall and path-switch priorities, HCZ transition bubbles, MGZ2 collapse and end-boss sequencing, MGZ surprise Robotnik terrain occlusion, and level-transition music timing now follow the ROM more closely. The cross-game replay checkpoint retained its documented S1, S2, and S3K trace frontiers.
- **Merge verification (2026-07-18):** the S1, S2, and S3K replay suite was rerun before integrating this parity batch; its existing documented frontier failures and error remained unchanged.
- **Sonic 3 & Knuckles route coverage:** the Sonic/Tails path has completed AIZ through LBZ coverage, with ongoing work across bosses, events, objects, bonus stages, scroll/parallax, animated tiles, palette/PLC state, transitions, and rendering parity.
- **AIZ1 intro Tornado priority parity (2026-07-17):** Super Sonic and every Tornado aircraft piece now use the ROM's `$280` sprite bucket while the water splashes use `$100`, placing the propeller and rocket booster behind the waves as on original hardware.
- **AIZ trace parity closeout (2026-07-11):** both the focused level-select route and complete-run route now replay fully green through AIZ and the HCZ handoff. The campaign restored native object/RNG/SST cadence across the intro, traversal objects, act transition, battleship, miniboss and end boss, capsule/results control, Knuckles/button/drawbridge cutscene, mutable water flag, and CPU-Tails decision-time comparison while retaining green S1 and S2 trace fleets.
- **HCZ blocky waterfall/slide rendering fix (2026-07-14):** StillSprites (obj 0x2F) now live their ROM `Sprite_OnScreen_Test` lifetime instead of self-deleting on their first update, restoring HCZ's waterfall curtains, HCZ2's slide-crossing tube pieces, and decorative overlays across AIZ/MGZ/LBZ/MHZ/LRZ that previously vanished in sprite-sized blocks. HCZ2 additionally keeps Plane B a 512px VDP window through the whole act (following the wall BG camera during the chase like `DrawBGAsYouMove`, with ROM row-pointer overflow), and the BG high-priority replays now mirror the compositing pass's window base and plane-period wrap exactly. Verified against BizHawk reference frames; one shallow known knock-on (`s3k_hcz` complete-run f29096 scattered-ring re-collect) is recorded in `docs/status/trace-frontier-log.md`.
- **HCZ trace and rewind closeout (2026-07-13):** the complete Hydrocity route now replays green through its seamless act transition and the MGZ handoff. Turbo Spiker shells transfer lifetime ownership when launched, HCZ results/capsule/boss links restore through closed rewind graphs, CPU Tails consumes the ROM-recorded follower press byte, and the large-fan module queue retains its registered rewind-owned state across acts; all previously green S1, S2, and AIZ traces remain green.
- **Sonic 2 trace closeout:** the full S2 level-select trace suite now passes, including the late OOZ2, ARZ2, CNZ2, and MTZ3 boss/event frontiers and the S2 impatient-wait input gate.
- **Sonic 1 trace progress:** multiple complete-run frontiers advanced or turned green through ROM-order platform, camera, spring, ring, badnik, conveyor, seesaw, staircase, and collision fixes.
- **Sonic 1 bug batch (2026-07-05):** a 25-bug triage-and-fix pass closed 10 player-reported issues (special-stage jump input, egg-prison/lamppost/lavafall/glass-reflection visual lifetimes, Yadrin spike geometry, hurt-spring control recovery, bumper bounce direction, LZ1 door-gated current, and six rewind-state gaps spanning breath/conveyor/speed-shoes/invincibility-music/boss-spikeball state), confirmed 4 reports as genuine ROM behavior, and documented capture recipes for the 4 that need real-hardware evidence; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Sonic 1/2 bug batch wave 2 (2026-07-06):** play-testing follow-ups: starpost twirl now rests dead-centre (ROM 32-step terminal angle), lava geyser maker no longer flashes the prior cycle's ending frame, the dormant SYZ Roller is hidden exactly as ROM never displays it, the ROM-verified 62px glass-reflection shimmer is pinned by test, rewind is blocked while special/bonus-stage transitions are pending (S2 softlock fix), and boss child objects are re-adopted by identity with orphan reconciliation after rewind (EHZ boss desync fix); see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Rewind hardening wave 3 (2026-07-06):** boss children recreated by rewind now re-register with their parent through a single central mechanism (closing the EHZ wheel orphan gap and unifying DEZ/MTZ registration), trace-session rewind gained the same transition-freeze gate as live rewind, and rewind engagement is now blocked while any completion-bearing fade is in flight (closing silently-dropped or softlocked death/act/giant-ring transitions) while gameplay itself keeps ticking through those fades exactly as before; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Rewind relink hardening wave 4 (2026-07-06):** rewind recreate-time parent relinks are now bounded by per-object geometric radii (rejecting far same-class matches and dropping the child instead of silently adopting the wrong parent, e.g. a checkpoint twirl attaching to a different lamppost), with unbounded lookup kept only as a named opt-in where a parent legitimately roams; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Rewind and audio debt wave 5 (2026-07-06):** held rewind now clamps before committing to a keyframe captured mid-fade with an unrestorable completion callback (closing the scrub-through-fade softlock), the static-state rewind coverage guard now also audits per-GameModule services (four newly visible gaps baselined with justification), S1 SFX id 0xD0 dispatches through the ROM's special SFX pointer table, and the rewind round-trip probe report was regenerated; an empirical DEZ rewind test additionally uncovered (and documented for priority follow-up) an active child-state-loss bug for dynamically event-spawned bosses; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Dynamic-boss rewind reconstruction fix wave 6 (2026-07-06):** dynamically event-spawned bosses (SYZ3/GHZ bosses, S3K minibosses) no longer lose all child state on forced rewind reconstruction: phase-1 child adoption now parks unresolved entries and retries to a fixed point while parent reconstruction populates the scratch pool, and codec probe construction no longer leaks real wrongly-parented child objects; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Bonus-stage rewind (2026-07-07):** held live rewind now works *within* the Sonic 3 & Knuckles Gumball and Pachinko bonus stages via a per-provider `supportsRewind()` capability, a widened rewindable-mode gate, a per-frame capture hook in the bonus-stage update, and a coordinator adapter that snapshots the ring/life/shield reward accumulators; the timeline is severed at the mode boundary in both directions. The Slot Machine bonus stage stays non-rewindable pending a dedicated runtime snapshot (planned alongside Sonic 1's Special Stage); see `docs/architecture/plans/2026-07-06-bonus-stage-rewind-gumball-pachinko.md`.
- **Special-stage rewind (2026-07-08):** held live rewind now works within Sonic 1 special stages through a provider capability gate, special-stage replay stepper, and Sonic 1 runtime snapshot adapter. Level entry/exit boundaries intentionally remain rewind timeline boundaries.
- **Gumball rewind capture hardening (2026-07-09):** S3K Gumball Machine capture now ignores stale removed dispenser/spring object references and rebuilds those live links after restore, preventing `RewindIdentityTable` crashes during bonus-stage rewind.
- **Test-suite and rewind determinism hardening (2026-07-09):** the full Maven suite is green again after capturing fixed-skid dust cadence in playable rewind state, rebuilding ARZ Obj83 child slots through identity-preserving reconstruction, correcting Sonic 2 blink/get-up donor mappings and delayed sidekick jump edges, tightening singleton test isolation, and restoring architecture/test-quality guard baselines.
- **S3K Blue Spheres visual parity (2026-07-09):** solo Tails now uses the ROM's player-2 palette line for his special-stage body and tail appendage instead of Sonic's palette line.
- **S2 special-stage trace capture & replay (2026-07-09):** a new BizHawk Lua recorder and PowerShell driver capture Sonic 2 half-pipe special-stage traces (48-column physics schema with BK2 input-alignment verification, real lag-frame flagging, and aux checkpoint/message/results events), with a committed 5299-frame MVP trace for Special Stage 1 (Sonic+Tails). A headless trace-paced replay harness (`TestS2SpecialStageTraceReplay` plus a determinism test) boots the production special-stage provider and diffs every frame against the recorded ROM run via the new read-only `Sonic2SpecialStageManager.captureComparisonState()` accessor — divergences are recorded to the trace report (ratchet intentionally disabled at the MVP frontier; see `docs/status/trace-frontier-log.md`). The trace catalog and test-mode picker gained special-stage-aware profiles/labels, a `GameLoop` special-stage skip-gate enables live lag-paced visual SS trace sessions in test mode, and special-stage team resolution now uses the standard two-key character config (making Tails actually spawn in team play); see `docs/architecture/designs/2026-07-09-s2-special-stage-trace-design.md`.
- **S2 Special Stage 1 trace-green closeout (2026-07-10):** ROM-ordered bootstrap, deferred `RunObjects`, controller sampling, object collisions, checkpoint/emerald completion, per-player rings, swap/timer state, and refresh-gated rings-to-go comparisons now replay 3,228 compared frames with 0 errors and 0 warnings. Every comparator is release-blocking, the scheduled trace profile explicitly keeps the run green, and normal play uses a rewind-safe deterministic lag model derived from the committed 5,299-frame recording. Normal entry now compresses the hidden ROM bootstrap, keeps the transition opaque until the full scene is ready, and starts music with the reveal; trace validation retains the exact startup cadence.
- **Rewind reliability:** gameplay rewind now covers more object graphs, level-trigger/static manager state, child recreation, object identity links, audio history boundaries, and route-specific boss/ride/carry state, with guards for future coverage gaps.
- **Controller support:** gamepad bindings now cover gameplay controls, live rewind, frame advance, debug movement, pause, title/credits confirmations, level-select menus, and launch-option navigation.
- **Player-facing systems:** ROM-backed SEGA boot screens, S3K data select and save/load support, cross-game donation, ROM-derived master-title previews, the legal-disclaimer startup flow, display shaders, pause/HUD fixes, multi-sidekick behavior, user recording/playback, and level-editor plumbing all moved forward.
- **Tails flight, swimming, and carry parity (2026-07-11):** manual S3K Tails flight/swimming now follows the ROM state machine across native and cross-game play, including donor-aware availability, main-character-only grabs, underwater exhaustion, shared CNZ/MGZ carry handling, and rewind-safe flight/carry state. The shared animation pass preserves the native flying and swimming body sprites selected by `Tails_Set_Flying_Animation`, matching CPU recovery presentation; swim body frames suppress the redundant Obj05 tail overlay, and idle swimming uses only the native `+$08` flight gravity instead of the non-flight underwater reduction. Native Sonic 1/2 and Sonic 2-donated play retain their original no-manual-flight behavior.
- **Rendering and performance:** live rewind gained the VHS picture-search effect, rewind tilemap rebuilds were reduced, display/title rendering was tightened, trace ghost rendering was added, and the PSG/audio implementation was cleaned up around the single Genesis Plus GX-derived core.
- **Project-wide performance pass (2026-07-10):** deterministic audio command processing now avoids more than 98% of its measured steady-state allocations, audio rewind history ownership halves the default retained PCM memory, and stereo FIR resampling halves tap-window traversal. Render and special-stage hot paths now reuse frame-owned state, visibility/SAT/overlay buffers, decoded/static geometry, pooled deferred commands, palette uploads, and page-aware virtual-pattern batches; GL teardown is recreatable and verified across repeated reinitialization. The banked series passed the full 11,405-test suite plus the green Sonic 2 special-stage trace and replay-determinism gates.
- **Performance follow-up (2026-07-11):** scrolling background tilemaps now upload only the two entering columns (8,192 B → 256 B for a normal window, 96.875% less), inactive trace rendering eliminates 16 captured callbacks per frame, sprite suppression resolves once per render pass, and warm rewind hits bypass keyframe/callback expansion setup. Repeated source-guard scans were deduplicated, cutting the focused guard median by 15.4%. The integrated batch passed 11,452 tests with zero failures and preserved render ordering, rewind rollback, audio, boundary, SAT, and Gumball determinism checks.
- **Rewind snapshot memory follow-up (2026-07-11):** composite key layouts are now shared across captures while each keyframe owns only its aligned value storage and immutable compatibility view. Same-layout restores use direct indexed access; historical layouts retain key-based resets, delayed game-RNG restoration, and callback ordering. Identical escaping probes reduced 24-subsystem capture allocation from 2,752 to 192 bytes (93.0% less), and the batch passed 11,460 tests with zero failures.
- **Special-stage/render-rewind performance continuation (2026-07-11):** Sonic 2 special-stage object/player draw ordering now uses stable grow-only renderer scratch, reducing a 32-object/8-player escaping allocation probe from 472 to 0 bytes while preserving equal-key order and reset/exception cleanup. Empty special-render and advanced-render rewind captures now return canonical immutable snapshots, reducing their combined legacy-equivalent capture from about 600 to 0 bytes. The two-task batch passed 11,478 tests with zero failures.
- **Animated-tile phase performance (2026-07-11):** installed animated-tile channels now keep live phase state in indexed primitive arrays while preserving public map snapshots, fresh callback contexts, cross-layout rewind restore, and callback-time install/clear behavior. A 32-channel escaping-context oracle improved from 1,792 to 1,280 allocated bytes per update (28.6% less), and the full 11,490-test suite passed with zero failures.
- **Performance-pass closure (2026-07-11):** animated-tile rewind captures now share immutable key layouts and own compact primitive payloads, reducing capture allocation from 3,288 to 320 bytes (90.3%) and the identity-aware 1,000-snapshot estimate from 2,378,048 to 356,728 bytes (85.0%). SMPS driver snapshots now hash a shared external fallback once per capture rather than once per SFX, reducing a 32-SFX fixture from 4.96 ms to 0.179 ms (96.4%) while still detecting source mutation between captures. Proposed empty-stage render gates were measured and rejected when their apparent benefit failed isolated reproduction, closing the pass without speculative branch churn. Identical opt-in baseline and optimized rewind runs retained the same pre-existing 120-frame long-tail frontier and first `object-manager.dynamicId` divergence; the 1,200-frame gate remains open and was not weakened. The full 11,504-test suite passed with zero failures.
- **Time-attack verifier benchmarks (2026-07-07):** two non-gating JUnit benchmarks measure the replay verifier's real cost against the trace corpus (per-run single-core cost, per-level retained footprint, and warm pooled-reuse behaviour), giving future verifier work a measured basis; the warm-reuse probe surfaced a headless palette-write accumulator leak in the shared `LevelFrameStep` frame step (the per-frame `PaletteOwnershipRegistry` drain was owned solely by `GameLoop`, so headless replay grew O(n^2) writes), now fixed by draining at the single-source frame step; see `docs/architecture/plans/2026-07-04-time-attack-phase5-verifier.md`.
- **Release hardening:** policy hooks, trace and rewind invariants, BizHawk/stable-retro trace tooling, object-service boundaries, ROM-only runtime asset rules, singleton lifecycle checks, architecture guards, test quality gates, and agent workflow docs were tightened for the prerelease line.
- **Rewind object regressions (2026-07-11):** live rewind now restores Sonic 2's Masher badnik to its exact captured fixed-point trajectory (subpixel phase, jump origin, velocities) instead of resuming from a partially-restored state, and the held live-rewind monitor presentation gained dedicated coverage for player identity, release decay, and held/replayable boundaries; see `docs/architecture/plans/2026-07-11-rewind-object-regressions.md`.
- **Rewind reference-closure hardening (2026-07-12):** Sonic 2 and Sonic 3 & Knuckles trace replay now validates every compact-captured object reference on each compared gameplay frame, with lifecycle and schema fixes for MCZ rotating platforms, MHZ Knuckles, ICZ freezer clouds, and AIZ intro glow state plus guard coverage that detects future identity gaps before manual play-testing.
- **Playable animation trace verification (2026-07-13):** normal-gameplay BizHawk traces now record Player and Sidekick animation IDs plus displayed mapping frames. Trace replay can gate all fields together or maintain independent physics and animation frontiers with `-Dtrace.verification=all|physics|animation`, so animation fixes cannot hide movement regressions.
- **Sonic 2 Wing Fortress Zone object & boss polish (2026-07-16):** a batch of ROM-verified WFZ fixes — the Tornado plays the scatter sound (not the ring-loss jingle) when it is gunned down; the belt platform (obj 0xBD) uses its ROM palette line; the hook-on-chain (obj 0x80) renders its chain/hook art with the ROM's `_Fudge` tile base; the rivet (obj 0xC2) plays its explosion sound and drops the player into the room below; the vertical propeller's helicopter sound is localized on-screen; the palette-switcher debug box respects the live debug overlay; and the WFZ boss (obj 0xC5) now draws its lens behind the cover with a laser beam that actually harms the player, plus rewind hardening for the boss child recreate paths. In the WFZ ending, Sonic now hangs correctly on Robotnik's getaway ship (the invisible grabber no longer suppresses the touch pass, so the breakable-plating grab fires) instead of showing the standing pose, and the getaway ship's foreground graphics are fixed — WFZ shares the SCZ tileset and overlays a pattern supplement (`ArtKos_WFZ`, like HTZ) that the engine previously loaded only for HTZ, so the ship (built from those supplement tiles) had rendered as garbage. A `dev.cmd` fast launcher (incremental compile + run from `target/classes` via a `dev-run` exec profile) was added for rapid iteration.
- **Sonic 2 Wing Fortress ending visual parity (2026-07-17):** the post-boss sequence now preserves the Mega Drive's history-dependent Plane-B nametable instead of rebuilding the whole background from current layout data. Incremental 64×32 ring updates, rewind capture, runtime PLC refreshes, interleaved foreground/background layout-RAM writes, and CPU/GPU wrapped sampling keep the sky blue until the late horizon reaches space without early black or wrapped space strips. The boss barriers flicker, the background ship retains its hull and alternating thruster flames, and the ending Tornado carries its ROM rocket pod and flame; headless trace-video gates cover the ship cadence, booster, sky, horizon, and first-black timing.
- **Shared BizHawk trace-recorder Lua module & Linux tooling (2026-07-23):** the six per-game BizHawk trace recorders now `loadfile` one shared `tools/bizhawk/lib/oggf_trace_common.lua` for their byte-identical leaf helpers (`bk2_input_mask`, `hex`, `angle_to_ground_mode`, `read_speed`, `rom_joypad_to_mask`, `write_aux`, `json_escape`/`json_quote`, `INPUT_*`) instead of copy-pasting them, with a launcher-provided loader and the schema writers/fast-headless block deliberately left inline. New Linux launch scripts (`run_bizhawk_lua.sh`, `record_trace.sh`) mirror the Windows `.bat` templates and run EmuHawk under mono. Linux-compat fixes: `client.invisibleemulation` (removed from the Lua API in BizHawk 2.11.1) is guarded like the neighbouring `client.SetSoundOn`, and `s3k_complete_run_recorder` was kept under Lua 5.4's 200-local cap (`luac5.4` verifies it; the `luac 5.5` in `$PATH` does not). Every recorder was regenerated against the repo-local BizHawk 2.11.1 Linux build and produced `physics.csv`/`aux_state.jsonl`/`metadata.json` byte-identical (SHA256-matched) to the pre-refactor `develop` recorder.

For details, see [`CHANGELOG.md`](CHANGELOG.md); for trace frontier movements and evidence, see [`docs/status/trace-frontier-log.md`](docs/status/trace-frontier-log.md); for the previous verbose v0.6 merge ledger, see [`docs/changelog/v0.6-prerelease-detailed.md`](docs/changelog/v0.6-prerelease-detailed.md).

### v0.5.20260411 (Released 2026-04-11)

A primarily architectural release. The engine internals have been restructured to prepare for level
editor support, safe gameplay-mode teardown, and multi-instance play-testing, while Sonic 3 & Knuckles
gameplay coverage has expanded across Angel Island and Hydrocity. AIZ2 now has the Flying Battery
bombing sequence, end boss, post-boss capsule/cutscene flow, and AIZ-to-HCZ transition represented,
while HCZ now has a larger object/event pass and HCZ1-to-HCZ2 progression.

- **Two-tier service architecture:** all 180+ game object classes migrated from direct singleton
  access to a two-tier dependency injection pattern (`GameServices` global facade + `ObjectServices`
  context-scoped injection). NoOp sentinels replace null checks throughout.
- **Gameplay session ownership:** this release introduced the first explicit gameplay-state
  ownership layer, later superseded by `SessionManager`, `WorldSession`, and
  `GameplayModeContext`. Enables safe editor mode enter/exit and level rebuilds.
- **LevelManager decomposition:** the engine's largest class is now a thin compatibility coordinator
  over focused collaborators including `LevelTilemapManager`, `LevelRenderer`,
  `LevelPlayableArtInitializer`, `LevelDirtyRegionDispatcher`, `LevelWaterCoordinator`,
  `LevelCheckpointCoordinator`, `LevelActTransitionExecutor`, `LevelTransitionCoordinator`,
  and `LevelDebugRenderer`.
- **MutableLevel:** snapshot, mutation, and dirty-region tracking for level tile data — the
  foundation for the upcoming level editor's undo/redo and real-time tile editing.
- **Common code extraction (5 phases):** 15+ abstract base classes, 10+ shared utilities, and
  systematic deduplication across all three games, including `SubpixelMotion`, `AnimationTimer`,
  `FboHelper`, `AbstractMonitorObjectInstance`, `AbstractSpikeObjectInstance`,
  `AbstractZoneScrollHandler`, and more.
- **Knuckles** is now a playable character with full glide/climb state machine, ROM-accurate
  jump height, wall grab, ledge climb, and sliding physics. Works in S3K natively and via
  cross-game donation into S1/S2 with correct palette and HUD from the lock-on ROM.
- **Sonic 3&K** expands with title screen (SEGA logo, Sonic morph animation, interactive menu),
  level select screen (SONICMILES background, zone icons, sound test), AIZ miniboss completion
  (defeat flow, napalm attack, staggered explosions), AIZ2 Flying Battery bombing/end-boss work,
  signpost and results screen, Blue Ball special stages (WIP) with per-character art/palette,
  S3K bonus-stage work across Gumball, Glowing Sphere/Pachinko, and Slots, per-character physics
  profiles, palette cycling for all zones, HCZ water rush / conveyor / fan / block / door /
  miniboss coverage, and many new badniks/objects including CollapsingBridge, MegaChopper,
  Poindexter, Blastoid, Buggernaut, Bubbler, TurboSpiker, and InvisibleHurtBlockH.
- **Insta-shield** fully implemented with ROM parity: activation, hitbox expansion, persistent
  lifecycle, cross-game donation, and DPLC cache management.
- **Multi-sidekick system** with configurable sidekick chains, per-character respawn strategies,
  virtual VRAM bank allocation, and VDP-accurate sprite priority ordering.
- **Tails AI rework:** ROM-accurate respawn gating, PANIC mode rewrite, flying/despawn
  improvements, P2 manual override, and per-zone boss/event wiring.
- **Cross-game donation** now bidirectional: S1 can donate into S2/S3K, with `DonorCapabilities`
  interface, `CanonicalAnimation` vocabulary, and `AnimationTranslator` for any game pair.
- **Rendering pipeline:** PatternAtlas slot reclamation, batched DPLC updates, virtual pattern ID
  validation, SAT sprite-mask replay ordering for mixed-priority S3K bonus-stage art, and
  fail-fast shader error handling.
- **Trace replay testing:** automated accuracy verification that records per-frame physics state
  from the real ROM, then replays the same inputs through the engine and compares every field.
  First trace (S1 GHZ1, 3,905 frames) passes with 0 errors; the latest GHZ bridge pass fixes
  the F2967 rider Y divergence by keeping Bri_Solid's final `Plat_NoXCheck` width and updating
  the rider bend log before sag calculation (`docs/s1disasm/_incObj/11 Bridge.asm:98-114`,
  `135-152`, `_incObj/sub PlatformObject.asm:19-42`, `58-76`, `_incObj/sub ExitPlatform.asm:8-23`).
  A second baseline (S1 MZ1, 7,936 frames) now passes after the Obj52 Moving Block
  jump-carry fix: S1 `MBlock_StandOn` clears Sonic's on-object status via
  `ExitPlatform`, then still moves the block and applies one final `MvSonicOnPtfm2`
  carry on the jump-off frame (`docs/s1disasm/_incObj/52 Moving Blocks.asm:65-83`,
  `_incObj/sub ExitPlatform.asm:5-24`, `_incObj/15 Swinging Platforms.asm:177-194`).
  Supports both BizHawk (Windows, Lua) and **stable-retro** (cross-platform,
  Python) as recording backends — both produce identical output consumed by the same Java test
  infrastructure.
- Comprehensive user guide, 15+ design specs and implementation plans, and broad test coverage
  improvements including automated singleton lifecycle testing.

See CHANGELOG.md for full details.

### v0.4.20260304 (Released 2026-03-04)

A release-sized update focused on expanding playable coverage, ending sequences, and engine maturity.

- **Package rename** from `uk.co.jamesj999.sonic` to `com.openggf` across the entire codebase.
- **Master title screen** implemented: engine-wide PNG-based title screen with animated clouds, game
  selection, and pixel font renderer. Displayed on startup before entering game-specific title flow.
- **Sonic 1** has moved from initial support to feature complete: title screen flow, special
  stages, major per-zone event scripting, extensive object and badnik additions, multiple boss
  implementations (GHZ, MZ, SYZ, LZ, SLZ, FZ), Labyrinth water/drowning/splash behaviour,
  ending/credits work, SBZ post-level-end sequence, demo playback, edge balance and push block
  collision corrections, and slope crest sensor guard. Expect minor bugs, but the game should be playable
  from beginning to end.
- **Sonic 2** adds title screen support, major object passes for MTZ/SCZ/WFZ/OOZ, 9 boss fights
  (MCZ, MTZ, WFZ, and both DEZ bosses — Mecha Sonic and Death Egg Robot, plus Robotnik escape),
  a complete credits and ending cutscene system with ROM-accurate visuals, expanded per-zone event
  architecture, demo playback, signpost/badnik palette/stair block art fixes, and a systematic
  TODO resolution pass with disassembly validation.
- **Sonic 3&K** sees major AIZ progress including intro cutscene systems, hollow tree and vine
  traversal parity work, miniboss object set bring-up, initial badnik implementations, shield/PLC
  integration fixes, a full water system with provider architecture and underwater palettes,
  seamless AIZ fire transition flow, and related regressions/tests.
- **Cross-game feature donation** implemented: a donor game (S2 or S3K) can provide player sprites,
  spindash dust, physics, palettes, and SFX while the base game handles levels, collision, objects,
  and music. Now includes cross-game Super Sonic delegation.
- **Per-game physics** and Super Sonic state/control flow (implemented for S2, with cross-game
  delegation to S1 and S2 game modules).
- **Profile-driven level loading:** declarative `LevelInitProfile` system with 13 ROM-aligned
  steps per game, replacing the monolithic `loadLevel()` path.
- **Testability refactor:** `GameContext`, `SharedLevel`, `HeadlessTestFixture` builder, and
  profile-driven test teardown. Test grouping by level and 8-JVM parallel execution.
- **Engine fixes:** solid object edge jitter fix, S1 slope crest sensor guard, jump-while-airborne
  guard, fade transition flash fix, results screen rendering fix, HTZ earthquake fixes, SFX
  channel replacement fix.
- PLC/art-loader refactors, RomOffsetFinder/ObjectDiscoveryTool enhancements, configuration
  documentation, and broad audio/stability/performance hardening.

See CHANGELOG.md for full details.

### v0.3.20260206

A massive release covering 366 commits across every major subsystem.

- **Tails** (Miles Prower) is now a playable character with ROM-accurate CPU AI follower behaviour,
  input replay, flight, and configurable sidekick toggle.
- **Multi-game architecture:** The engine has been refactored to support multiple games via a
  provider-based abstraction layer, with initial Sonic 1 ROM support (level select, title cards, HUD,
  audio with S1-specific SMPS driver configuration) alongside the existing Sonic 2 support.
- **Physics:** The physics engine has been completely rewritten to match ROM behaviour.
- **Bosses and objects:** Boss fights are implemented for 5 zones (EHZ, CPZ, HTZ, CNZ, ARZ), along
  with 15+ new badniks and 50+ new game objects spanning all implemented zones.
- **Water:** A full water system with drowning mechanics is in place for CPZ and ARZ.
- **Graphics:** The graphics backend has been migrated from JOGL to LWJGL with a GPU-accelerated
  rendering pipeline (pattern atlas, tilemap shader, instanced sprite batching, priority FBOs).
- **Audio:** Major accuracy improvements to YM2612 FM synthesis (based on Genesis-Plus-GX reference)
  and the SMPS driver.
- **Infrastructure:** Per-game ROM configuration, a HeadlessTestRunner for physics integration
  testing, visual and audio regression test suites, a multi-game test annotation framework, GraalVM
  native build support, and significant performance optimisations throughout.

See CHANGELOG.md for full details.

### v0.2.20260117

Improvements and fixes across the board. Special stages are now implemented, feature complete with a
few known issues. Physics have been improved, parallax backgrounds implemented and complete for EHZ,
CPZ, ARZ and MCZ. Some sound improvements, title cards, level outros, etc.

### v0.1.20260110

Now vaguely resembles the actual Sonic 2 game. Real collision and graphics data is loaded from the
Sonic 2 ROM and rendered on screen. The majority of the physics are in place, although it is far
from perfect. A system for loading game objects has been created, along with an implementation for
most of the objects and badniks in Emerald Hill Zone. Rings are implemented, life and score tracking
is implemented. SFX and music are implemented. Everything has room for improvement, but this now
resembles a playable game.

### v0.05 (2015-04-09)

Little more than a tech demo. Sonic is able to run and jump and collide with terrain in a reasonably
correct way. No graphics have yet been implemented so it's a moving white box on a black background.

### v0.01 (Pre-Alpha, first documented 2013-05-22)

A moving black box. This version will be complete when we have an unskinned box that can traverse
terrain in the same way Sonic would in the original game.
