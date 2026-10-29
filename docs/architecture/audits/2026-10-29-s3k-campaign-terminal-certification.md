# S3K campaign terminal certification — 2026-10-29

Source base: `c4cd6ae0e8cdaf00bdf013c3c740586039c10ef8` (`develop`).
Worker: `bugfix/ai-trace-manifest-certification`, T3 checkout
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-218cf93f`. Only the manifest/walker
contract, directly associated tests, and this evidence/prose change belong to
this lane. No gameplay owner, native recorder, disassembly, fixture observation,
API publication, or release changes.

## Certification boundary

`complete_run` is a segment capture profile, not a declaration that a campaign
finished. Neither run/movie names nor the final segment's zone, elapsed movie
rows, or native recorder stamp establish the final game mode.

`TraceRunManifest.requireRecordedMovieEndMode()` rejects an unspecified endpoint
with the run/movie identity and missing field. The ordinary loader, structural
validator, and partial replay retain optional-endpoint compatibility.
`TraceRunReplayWalker.planCompleteCampaignMovieTail()` requires that observation
and derives the tail from the last segment and actual BK2 row count. Negative
declared bounds and a segment ending past the movie fail; an exact-end segment
still requires the terminal mode assertion.

The three full S3K chain methods now call `assertCompleteCampaignReplay`, which
checks this prerequisite before the engine boots, then uses the unchanged
continuous replay's mode, physics, boundary and terminal-art assertions. A
preflight success alone is not campaign certification. Prefixes, segment-local
frontier instruments, and partial multibonus/round-trip tests still use their
existing entry points. The current full campaigns fail closed on missing
endpoint evidence, before reporting a gameplay divergence. The preflight checks
that an endpoint value is present; it cannot authenticate the producer or value.
Native capture and input provenance must be verified separately.

## Five committed manifest audit

All five omit `expected_movie_end_mode`. Every segment metadata file declares
`recorder: native-bizhawk-headless`, `recorder_version: 3.0`, BizHawk `2.11`,
and `genesis_core: Genplus-gx`. Those declarations identify the published capture;
they do not authenticate an endpoint absent from the capture. All movie headers
also name BizHawk 2.11 / Genplus-gx. Input rows below count actual `|` rows in
the ZIP's `Input Log.txt`; segment ends are offset plus trace row count, not
endpoint observations.

| Run under `src/test/resources/traces/s3k/runs/` | Segments | Original movie availability | Movie rows | Final segment end | Status |
|---|---:|---|---:|---:|---|
| `s3-knux-multibonus-ss` | 25 | run-local `s3-knux-multibonus-ss.bk2` | 114622 | 114621 | Partial AIZ–HCZ–MGZ route; optional endpoint retained |
| `s3k-multibonus` | 25 | no run-local copy; identical movie in `s3k/_movies/` | 114622 | 114621 | Partial duplicate route; optional endpoint retained |
| `s3k-knuckles-complete-superemeralds` | 67 | run-local `s3k-knuckles-complete-superemeralds.bk2` | 434417 | 433942 | Full campaign certification blocked |
| `s3k-sonic-tails-complete-emeralds` | 63 | run-local `s3k-sonic-tails-complete-emeralds.bk2` | 539492 | 539166 | Full campaign certification blocked |
| `s3k-tails-full-chain-all-emeralds` | 70 | run-local `s3k-full-chain-tails-all-emeralds.bk2` | 499820 | 499678 | Full campaign certification blocked |

Original BK2 SHA-256 identities, in table order (the first two are identical):

```text
d7485e13f427d1b335cbbb3c405ff6136a77326239b79d3aed61e902078af45c
d7485e13f427d1b335cbbb3c405ff6136a77326239b79d3aed61e902078af45c
aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc
ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0
cb62a83a41de3273f12e5c1f9dbe7f60fe9eabb2c8505b76470e295315eee1f8
```

The available original locked-on ROM at the main repository root was read only:
CRC32 `63522553`, SHA1 `CFBF98C36C776677290A872547AC47C53D2761D6`.
The movie header's legacy `SHA1` token is not used to replace that ROM identity.

## Authentic endpoint acquisition blocker

This checkout's clean TraceChaser pin is
`703bd92d32634ce5ebac60625b48c60ef5500427`.
`bizhawk-headless/src/Recording/S3KRunManifestWriter.cs` passes `null` as the
shared writer's endpoint argument. The S3K capture runner calls that writer
without sampling/publishing a terminal mode. Re-running these movies with this
producer cannot supply the missing field. Fixing and independently qualifying
that native producer is separate work; it must precede any fixture update.

Additionally, `BIZHAWK_HOME` is unset, and the default
`.dependencies/BizHawk-2.11-linux-x64` installation and local native `bin/` are
absent in this checkout. The main checkout also lacks the default installation;
its dirty TraceChaser/disassembly trees and existing archive files were preserved.
No capture was launched, no passing endpoint was inferred or authored, and no
fixture bytes were changed. Future capture must follow `bizhawk-headless-trace`
with the original movies, verified 2.11 runtime/native provenance, and independently
validated terminal sampling, before accepting full-campaign qualification.

## Bounded existing gameplay evidence

These are retained prior measurements, not fresh observations of this patch:

- **HCZ:** runtime `0d7c25c68`, recorded in the
  [frontier log](../../status/trace-frontier-log.md): segment 11 closes all 14208
  rows with 62 residual differences (11 camera / 51 animation), first strict
  row 6640 animation `$13/$05`, and no non-camera physics error. Segment 13's
  first non-camera mismatch is row 2147 P1 Y `$04DA/$04DE`; SS7 results still
  stops at movie cursor 97208. This does not certify the whole campaign.
- **LRZ:** matched historical `ae025629e971e4ff9616d5a7c400834505741e1d`
  versus receiver `1237de4bc0d3d2c3c73d3335df91cfeff65bd099`: first-door
  route passes historically and dies at input 15323 on the receiver; middle
  corridor death at input 5027 is shared. First causal owner remains unknown;
  these are route assertions, not a native trace divergence measurement. See
  the [matched record](../plans/2026-10-22-native-gap-swarm.md#matched-lrz-knuckles-source-comparison--2026-10-23).
- **DEZ:** cold diagnostic base `c50876b184612428ccaf22763412f88ffe505432`,
  diagnostic source tree `0e5939c020ea5986e579eb29e054e2c5d3ee579c`, movie
  offset 468982: death after 1008 inputs. Main-player first differences were
  camera X row 0, rings 313, velocity 853, position/fractions 854; Tails Y
  already differed at row 160. The observation shell passed, not the route.
  See the [bounded attribution](../plans/2026-10-22-native-gap-swarm.md#post-223-same-movie-cold-dez1-attribution-2026-10-26).

No new gameplay check or sweep was needed for this contract change. None of
those historical numbers is attributed to `c4cd6ae0` or to this patch.

## Focused verification

The queued version probe reports Java 25.0.5.0.2. The exact focused command is:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py --normal -Dmse=off -Dtest=TestTraceRunManifest,TestTraceRunReplayWalkerControlFlow test -B
```

The first candidate run completed 56 tests: 55 passed, one failure, zero errors
or skips (Maven 1m30s; queue wait zero). All 15 manifest tests and all new terminal
contract checks passed. The sole failure was the unchanged
`TestTraceRunReplayWalkerControlFlow#metadataOnlySpecialStagePlanRejectsNonContiguousStoredRows`:
the S2 special-stage parser requires 48 columns, but that fixture supplies
`0,a` (two columns), so the test never reaches its intended row-gap assertion.
Its precise failure is:

```text
Segment 0 parser failed for profile 's2_special_stage': Expected 48 CSV columns, got 2: 0,a ==> expected: <true> but was: <false>
```

A matched baseline check restored all eight owned Java source files to exact
`c4cd6ae0e8cdaf00bdf013c3c740586039c10ef8` bytes in this checkout without
switching branches, ran the following single selector, then restored the entire
owned patch in `finally`. No foreign changes were present or modified:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestTraceRunReplayWalkerControlFlow#metadataOnlySpecialStagePlanRejectsNonContiguousStoredRows' test -B
```

Baseline: 1 test, 1 identical assertion failure, zero errors/skips, Maven 1m33s,
queue wait 17s. This defect is inherited and remains outside the terminal patch.
At initial ownership inspection, a separate gap-contract worktree had changes
to that test file and a live focused check; its checkout was no longer present
at final inspection. No changes were copied from that lane.

After adding a synthetic harness-level gate check, the final focused selection
also covers the existing coordinator's optional/declared terminal consumers:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py --normal -Dmse=off -Dtest=TestTraceRunManifest,TestTraceRunReplayWalkerControlFlow,TestTraceRunPlaybackCoordinator test -B
```

Final result: **82 tests, 81 passed, one identical inherited assertion failure,
zero errors/skips**, Maven 1m32s, queue wait 121s. All terminal contract cases,
including the real harness's preflight/delegation test, and all coordinator
cases pass. The failure identity/message matches the pinned baseline exactly.
The unchanged final eight-file Java source identity is SHA-256
`895f45d99d42ffb8c41ec07d76a02dae66bbeb3abb0e1f2c0f6dfce85eca23f7`
(sorted changed Java paths, each UTF-8 path plus NUL, file bytes plus NUL).
No owned jobs remain. Markdown file targets and `git diff --check` pass.

This is ROM-free contract validation, not a native capture, replay campaign,
startup/zone matrix, broad smoke, or release qualification. No missing fixtures
or ROMs were hidden by skips in these selections. The coordinator retains
combined validation and integration. The overall focused selection remains red
on its attributed baseline defect; no complete campaign is certified.

### Updated-base integration checks

On `27ae5f76a8064316f653b92ec084eea7ed80165c`, the upstream fix
`d1eae477c438fa0ac73c4d52efd38f195fdbda73` repairs the inherited S2 gap
fixture. The same three-class focused selection above passed all 82 tests, with
zero failures, errors or skips (336 seconds queued; Maven 36.788 seconds).

With the original S3&K ROM verified against SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`, the three actual full-campaign
methods were selected via the Maven queue:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py --normal -Dmse=off \
  "-Ds3k.rom.path=$S3K_ROM_PATH" \
  '-Dtest=TestS3kKnucklesSuperEmeraldRunChain#aiz1ToDoomsdayAcrossEverySpecialAndBonusStage,TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds,TestS3kTailsFullChainRunChain#tailsFullChainAllEmeraldsRoundTrip' test -B
```

Here `S3K_ROM_PATH` represents the verified absolute path used by the invocation.
All three stopped before engine boot
with `expected_movie_end_mode` missing: Knuckles
`aiz1ToDoomsdayAcrossEverySpecialAndBonusStage`, Sonic and Tails
`aiz1ToDoomsdayCollectingEverySevenEmeralds`, and Tails
`tailsFullChainAllEmeraldsRoundTrip`. The command exited nonzero with three
errors, zero failures and zero skips (388 seconds queued; Maven 34.779 seconds).
This verifies the intended missing-evidence gate, not campaign progress or a
new gameplay frontier.
