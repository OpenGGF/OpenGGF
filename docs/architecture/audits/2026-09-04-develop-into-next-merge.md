# Develop into next integration — 2026-09-04

## Branches and scope

Local `develop` was fast-forwarded to fetched `origin/develop` at
`4296bc291`. Local `next` was fast-forwarded to fetched `origin/next` at
`f254e357f` before merging. Their merge base is
`c1774bde1f07e882931c7891a1e9c38936453fb8`. The merge is local only; no push
was requested or performed.

The initial worktree was clean. Git reported 110 conflicting paths. Both
branches contain substantial independent work, including incompatible audio
implementations. The user explicitly selected develop's newer audio work.

## Resolution decisions

- Adopt develop's session-owned SMPS implementation, Nuked OPN2 core,
  per-game audio policies, reference tools and corresponding tests. Retire
  next's replaced source-timing cores, diagnostic tools and tests of those
  removed APIs. Historical audio investigations remain historical records.
- Preserve next's mod-provided streamed music, namespaced audio, editor,
  time-attack and mod SDK features. Adapt streamed voices to session-owned
  audio presentation, including cursor retirement, override restoration,
  speed changes and rewind snapshots. Restore-fade SFX admission follows
  the active audio profile.
- Keep develop's game-over flow, apparent level music publication,
  suppressed-row clocks and dynamic-object ownership. Preserve next's mod
  callback context and rewind metadata alongside the newer dynamic-object
  ownership model.
- Retain next's save-provider boundary and apply develop's saved-life
  normalization within the S3K provider. Preserve older emerald payload
  compatibility without accepting malformed current payloads.
- Classify the new audio session, preparation and diagnostic types as
  engine internals at the existing mod API boundary. Expose the new driver
  configuration enums and module/input contracts where creators consume
  them. Refresh the unpublished 0.7.0 candidate signature and the explicit
  internal-type inventory; no published baseline changes.

## Validation

Validation uses Amazon Corretto 21.0.11 and Maven, with output under this
worktree's `target/`. Lua 5.4.7 and PowerShell 7.4.6 supply the TraceChaser forwarder guards.
Both are temporary local tools; their caches remain below `target/test-tmp`.
ROM-backed focused checks use the existing absolute Sonic 1, Sonic 2 and
locked-on Sonic 3 & Knuckles ROM paths in the project root. The legacy
`sonic.rom.path` property also points to Sonic 2, because older audio tests
still use that lookup. Sonic 1 and Sonic 2 match the reference hashes in
`AGENTS.md`. The supplied S3K file has CRC32 `0C06AA82` and SHA-1
`B711A909CCE238CA4AF3E517A2EDCA306228EFA5`, differing from the documented
reference; passing checks against this file do not certify canonical-ROM
parity. No ROM was modified.

The initial focused run executed 393 tests with no skips. It exposed six
failures: one source-assertion mismatch, one obsolete tool method name
caught by an audio architecture guard, one command-count assertion, and
three mod API classification/signature checks. Streamed-music tests passed.
These findings were used to finish the integration; that initial run is
not a passing validation claim.

The first complete guard run executed 646 tests and identified stale S3K
object-profile metadata, an output-inventory entry for the removed audio core,
a missing explicit run-order setting in the TraceChaser profile, and numeric
Java arrays mistaken for legacy trace CSV by a textual guard. The profile
metadata was transcribed from the merged registry, including FBZ's three
zone-gated ids. The numeric arrays were formatted with spaces. PowerShell was
then supplied for the one environment-dependent guard error.

The broader focused run also confirmed that develop's three game-over card
classes pass isolated rewind round trips: the merged inventory is 1009
concrete classes, 789 isolated passes, 220 graph-covered classes and no
missing codecs. The FBZ art-plan count includes the new shared game-over
card. Mod API hook fixtures now put their version declaration on its own
line, matching production's declaration. Signature generation disables JVM
performance-data output to keep incidental launcher warnings out of the pin.

## Final validation scope

- The final broad focused invocation selected audio presentation, SMPS,
  YM2612/PSG, streamed and namespaced music, mod API/SDK, GameLoop,
  LevelManager, affected rewind tests and S3K art registries. It executed
  942 tests: 938 passed, three art-plan count assertions failed and one
  opt-in repeated-playback benchmark was skipped. All audio and mod API
  checks passed. Supplying `sonic.rom.path` eliminated the five legacy
  ROM-lookup skips. The three remaining assertions came from applying the
  FBZ count adjustment too broadly; their existing counts were restored,
  with the new shared game-over card included only in FBZ's changed count.
  Log: `target/merge-focused-verified.log`.
- The complete fresh-JVM guard invocation (`mvn -Dmse=off -Pguards test -B`)
  executed 646 tests: 645 passed, one numeric-list formatting false positive
  remained, and none were skipped. The PowerShell forwarder checks passed.
  The final compact `List.of` initializer was formatted with spaces.
  Log: `target/merge-guards-final.log`.
- The affected art registry and formatting guard are rechecked together with
  `mvn -Dmse=off -Pguards
  -Dtest=TestSonic3kPlcArtRegistry,TestTraceV5PositiveInputGuard test -B`,
  supplying the same absolute S3K ROM path: **77 passed, zero failures,
  errors or skips; BUILD SUCCESS**. Log: `target/merge-recheck.log`.
- Commit policy runs through the installed `.githooks` without bypasses.

This is a merge integration check, not a full ordinary-suite, trace-replay or
release evidence sweep. The broad runs above are recorded with their actual
results; passing targeted rechecks establish the corrections without claiming
that those earlier invocations were green.
