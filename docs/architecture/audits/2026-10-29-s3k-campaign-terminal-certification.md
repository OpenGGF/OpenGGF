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

## Current HCZ bounded remeasurement — 2026-10-29

Worker `bugfix/ai-hcz-campaign-frontier-20261029`, T3 worktree
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-24cc4d6d`, fast-forwarded its provisioned
`c4cd6ae0e8cdaf00bdf013c3c740586039c10ef8` to the requested destination
`3835ee568b4836d26aa476a7a0722753588fa72c` before measuring. Runtime and fixtures
were unchanged. The prior HCZ worker was completed and no matching live HCZ
job existed. Unrelated jobs and main-checkout reference changes were preserved;
this worktree's disassembly and TraceChaser were clean at pins
`8b2cf3c4178f2382f844da67798de4befc50c5e0` and
`703bd92d32634ce5ebac60625b48c60ef5500427`.

The complete-campaign selector now deliberately fails before boot on missing
endpoint evidence, and the three committed Sonic+Tails prefix methods stop
before the affected returned-HCZ interval. A temporary package-local
`TestS3kHczCampaignFrontierProbe extends AbstractRunChainTest`, with the ordinary
`@RequiresRom(SonicGame.SONIC_3K)` annotation, therefore called the unchanged
`assertChainReplayThroughSegmentRow(RUN_DIR, 15, 1)` from
`continuousHczThroughSeventhSpecialStageReturn`. `RUN_DIR` is the committed
`src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds` directory.
This targets the first HCZ2 return row after SS7 while retaining the continuous
walk, all comparator axes and production boundary guards. It neither supplies
a campaign endpoint nor bypasses its certification prerequisite. The temporary
wrapper's SHA-256 was
`1897c5a83f94481c3e356e5379240367969b3a003f2c52fa5ca56a3020a0c7fe`.

Exact invocation (all local Maven execution used the queue). The ROM variables
below stand for the discovered original absolute paths, with identities verified
against the root ROM table; they are not checkout-local aliases. Machine-local
home paths are omitted under the repository content policy. The handoff supplies
the actual local values:


```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestS3kHczCampaignFrontierProbe#continuousHczThroughSeventhSpecialStageReturn' test -B
```

The original ROM is CRC32 `63522553`, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`. Original run-local BK2 SHA-256 is
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`;
manifest SHA-256 is
`e2bcf1dfdcf3952ec07f61205e765f90fcb5405ab2ed7d8f054fde742a2f8f46`.
The segment-11 and segment-13 compressed physics payloads respectively hash to
`519cd5166db70cd58c2a173ba0859d3bd034395ea1c7178ee2d6fc89ef67ad57` and
`481239ab09f3847c4892e20d3d7395b0b4b54b5807de710367247f873b7ae7f7`;
their compressed aux hashes are
`453f2d04f3a66e25724428b4184afc7b1f4a27f365140c0d944c472142241505` and
`c035455f6ce5bfa6b0ebdbd7894e55dddf1d81ab49ee13e9e8af6b2598b64663`.

The completed run is **1 test / 1 failure / 0 errors / 0 skips**, queue wait
205 seconds, Maven 2m03s including a clean compile. The fresh Surefire XML and
session-owned JSON were inspected. Segment 11 (`hcz_2`, movie offset 63075)
consumes all 14,208 stored rows and crosses into SS6: 62 residuals, split 11
camera physics / 51 animation, 59 lag rows, zero bootstrap errors, no
non-camera physics mismatch. Segment 13 (`hcz_3`, offset 85097) consumes all
5,393 stored rows and enters SS7: 4,145 residuals, split 3,795 physics / 350
animation, seven lag rows, zero bootstrap errors. Its first non-camera physics
mismatch is row **2147, P1 Y native `$04DA` / engine `$04DE`**; the preceding
non-camera physics matches through row 2146. Segments 0/2/4/6/8/9 have zero
reported errors. The bounded target is not reached: the physical boundary guard
stops in `ss_7`, mode `SPECIAL_STAGE_RESULTS`, movie cursor **97208**, before
`hcz_4` admission. The partial gap report has 14 observed gaps and zero failures.
These counts and the first non-camera mismatch match the prior HCZ receiving
measurement; they are a fresh current-source observation, not a campaign pass.

The separate queued version probe
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -v`
reports Maven 3.10.0 and Java 25.0.5.0.2, exit zero, after 408 seconds waiting.
Its wait overlaps the replay and must not be added to replay elapsed time.

### Fan phase attribution and independent controls

The baseline's first strict segment-11 error is row 6640
`player_animation_id`, native `$13` / engine `$05`. Segment 13's first strict
error is earlier than its motion frontier: row 1413 `sidekick_animation_id`,
native `$00` / engine `$18`. These animation observations are distinct from
the first non-camera physics error at 2147.

One matched, read-only retry retained the exact command above and added reads
through the existing `productionOutputRowObserver` / `afterProductionStep`
callbacks. The temporary wrapper SHA-256 became
`ef9dd11e8def3a509b0aa005912f09cb4fce658607095d4136697013ad9af12a`.
It read the fan's timer/toggle via reflection and logged its slot, coordinates,
oscillation, level counter and player state. It neither loaded comparison rows
nor wrote gameplay. Result **1T/1F/0E/0S**, identical segment counts and SS7
stop; queue 377 seconds, Maven 1m06s, JUnit case 31.870 seconds.

Native `hcz_3` aux `object_appeared` at row 1723, level counter 1718, places
the `$29C8,$04F4` subtype `$06` fan in slot 20 with its newly installed Main
pointer `$0003064E`. Original ROM bytes at `$30580` and `$3064E` match the
initializer and Main entry. `HCZCGZFan_InitFan` / `HCZCGZFan_Return`
(sonic3k.asm:65382-65400) install Main and return before its pulse/player work.
`HCZCGZFan_CheckSpin` (sonic3k.asm:65412-65423) decrements a word, reloads
120 on the first idle transition and 180 on activation, with zero retained
until the next negative tick. There are no lag iterations between that entry
and row 2147: the recorded level counter advances from 1718 to 2142.

The existing Java owner already performed Main at row 1723 and stored idle
timer 121. This compensated its early initialization dispatch for the first
activation, but added another idle tick on every subsequent pulse. Its observed
first activation is row 1845, subsequent idle reload is row 2026 (timer 121),
and row 2147 still has idle timer zero. It activates only on 2148. The ROM
initializer return plus 120/180 reloads instead derive first activation 1845,
next idle 2026 and next activation 2147. This is a ROM-owned state-machine
derivation, not a fitted countdown or fixture-specific production predicate.

At the disputed row, the observer's movie input index is 87244, post-step
cursor 87245, stored trace row 2147, level counter 2142. Native and Java
oscillation byte `$14` agree at 12 (native table includes its two-byte control
word). Native Y `$04DA`, Y velocity zero, ground velocity one and animation
zero identify the fan lift; Java leaves Y `$04DE`, Y velocity `$0488`, ground
velocity `$FDDC`, animation two while idle. The first motion difference is the
consequence of this earlier local dispatch/reload discrepancy. No shared
engine, input, timing, audio, rewind or comparator owner was edited.

Two independent ROM-free controls were added to `TestHCZCGZFanObjectInstance`:
fresh initialization must preserve the player before Main and replay both
sides of that boundary after capture/restore; repeated pulses must preserve
the ROM's 121-dispatch idle and 181-dispatch active intervals over three cycles,
also after restore. The old runtime fails both, without errors or skips:
initial Y becomes 4094 instead of staying 4096, and dispatch 424 remains idle
instead of active. Exact old-red command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestHCZCGZFanObjectInstance#freshFanReturnsFromInitializationBeforeApplyingLiftAndRestoresThatBoundary+repeatedPulsesKeepNativeIdleAndActiveDurationsAfterRestore' test -B
```

Old-red result **2T/2F/0E/0S**, queue 26 seconds, Maven 35.238 seconds.
The correction belongs solely to `HCZCGZFanObjectInstance`: retain the
initialization return and use the native 120 idle reload. Its scalar phase is
captured by the existing generic object snapshot owner. Existing always-on
component fixtures explicitly execute initialization before asserting Main's
clock, RNG, participation and subpixel behavior. Platform child dispatch keeps
its existing direct call of the installed fan Main.

Focused qualification uses the verified original S1, S2 and S3&K ROMs:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM_PATH" \
  "-Dsonic2.rom.path=$S2_ROM_PATH" \
  "-Ds3k.rom.path=$S3K_ROM_PATH" \
  '-Dtest=TestHCZCGZFanObjectInstance,TestS3kHczCgzFanGraphRewind,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test -B
```

Result **76T/0F/0E/0S**, queue 323 seconds, Maven 1m37s including compilation.
All seven physical XML suites were inspected: fan 14, fan graph restore two,
AIZ skip eight, both `TestSonic3kLevelLoading` classes 36 and seven, bootstrap
six, decoding three. The short class selector intentionally selects both level
loading classes. The source was the local fan/test delta over `3835ee568`;
the only subsequent Java edit before route qualification corrects an existing
allocation Javadoc citation from the callee's label to its actual call site.
The citation check now reports zero stale references in the changed class and
this audit; unlabelled legacy numeric citations remain unverifiable by that
checker and are not newly qualified by its success.

### Corrected route and second-movie control

The final corrected fan/test source retained the observer above. The affected
continuous prefix and a separate original HCZ movie were selected together:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM_PATH" \
  "-Dsonic2.rom.path=$S2_ROM_PATH" \
  "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestS3kHczCampaignFrontierProbe#continuousHczThroughSeventhSpecialStageReturn,TestS3kHczZoneSliceTraceReplay#replayMatchesTrace' test -B
```

Both physical test identities ran: **2T/2F/0E/0S**, queue 550 seconds, Maven
2m25s including compilation. The continuous case took 33.061 seconds and the
alternate movie 11.793 seconds. Segment 11 retains exactly its 62 residuals,
first strict animation row 6640, all 14,208 stored rows consumed, 59 lag rows,
zero bootstrap errors and no non-camera physics mismatch. Segment 13 consumes
all 5,393 stored rows with **364 residuals (182 physics / 182 animation)**,
seven lag rows and zero bootstrap errors. Its first strict error remains the
row-1413 sidekick animation observation, while first non-camera physics moves
from **2147 Y `$04DA/$04DE` to 2500 Y `$026E/$026B`**. Non-camera physics now
matches through row 2499. No comparator, tolerance or fixture observation was
changed to obtain this reduction from 4,145 residuals.

The corrected observer confirms timer zero / no Main at row 1723, idle reload
120 at 1724, unchanged first activation 1845 and next idle 2026, then activation
at 2147 with native Y `$04DA`, Y velocity zero, ground velocity one and
animation zero. Adjacent rows 2148/2149 also match native Y `$04D6/$04D2`.
This qualifies the specific pulse consequence. It does not attribute the next
row-2500 disagreement: native records multiple platform/fan pairs there, and
their ordering/latch state has not been discriminated. No fitted follow-up was
made. The chain still enters SS7 and stops in `SPECIAL_STAGE_RESULTS` at movie
cursor **97208** before `hcz_4`; the later results/return cause remains unproven.

The separate original movie is
`src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2`, SHA-256
`82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf`.
Its `hcz_completerun/physics.csv.gz` SHA-256 is
`f5b2f7b97951a3b8897d2e3f96c1a8155cd2bcf5d82410a441f8b376c2020337`.
The corrected selector reports 4,354 errors (3,425 physics / 929 animation),
zero warnings/bootstrap errors, first row 9482 `air` native one / engine zero.
Its reported comparison extent is 29,302 frames against 31,482 planned trace
rows; it does not establish a completed full fixture. Eight advertised auxiliary
observation families are absent according to its report. This is bounded
physics/animation evidence, not native slot-identity or complete observation
qualification.

To attribute that failure, the worker temporarily restored only the fan file to
exact `3835ee568b4836d26aa476a7a0722753588fa72c` bytes (SHA-256
`cd06fcd8b6f22d81144c3af3e635c5ec8d337c7cfde469a07eb39247a63db566`).
All other production sources remained at that same base. A local Python driver
retained the corrected bytes in memory and restored them in `finally`, without
switching branches or modifying fixtures. It ran the preceding route command's
identical environment, ROM paths, profile, fork and ordering arguments, replacing
only the selector with:

```text
-Dtest=TestS3kHczZoneSliceTraceReplay#replayMatchesTrace
```

Baseline **1T/1F/0E/0S**, queue 539 seconds, Maven 1m41s, JUnit suite
13.660 seconds. All **4,354 complete error-span dictionaries are identical**
to the corrected run, not merely their totals or leading field: zero
candidate-only and zero baseline-only spans. Group counts, comparison extent,
first assertion, bootstrap counts and missing auxiliary families also match.
The corrected alternate was run after the continuous prefix in a reused JVM,
whereas the baseline ran alone; equality of the entire observed error-span
sequence establishes this bounded inherited outcome, not general order
independence. No alternate replay or campaign pass is claimed.

The corrected fan file was restored and checked at SHA-256
`1d86142ba7feced4d3644d4d1f2e69f18f6eebce4cc94838f11f2fe54b55861f`.
No subsequent runtime or regression-test edits followed the completed
qualification. The temporary prefix wrapper and comparison input, raw test
reports and task logs are consumed diagnostic outputs, removed before handoff;
no pass cache, validation receipt or retained log archive is created. This
checkout started without `target/`; its task-generated build output is also
discarded so the temporary baseline compilation cannot be mistaken for the
restored source. No owned jobs remain. The task branch/worktree are retained
for parent review, combined validation and integration; nothing was pushed.

## Remaining platform/fan dispatch investigation — 2026-10-29

Worker `bugfix/ai-hcz-campaign-causal-20261029`, T3 checkout
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-55adf9be`, fast-forwarded the clean
provisioned `c4cd6ae0e` tree to `b0dbd48258417efc60b07d8943b1418b893dae02`.
The disassembly and TraceChaser pins remain unchanged and clean. The original
absolute S3&K ROM was read only, verifying CRC32 `63522553` and SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`. No matching HCZ job existed at
submission; unrelated queued/running jobs were preserved.

### Native ownership at the row-2500 frontier

The committed segment-13 aux observations identify upper platforms in native
slots 14/15/48/49 and their fans in slots 50/51/52/53. Lower platforms 16/18
precede their fans 28/29. At row 2258 the upper platforms appear at Y `$02A0`
and the fans at `$0284`; all four platform slots are assigned before any of
their four fan slots. At row 2500 the upper-left platforms remain at X
`$2950/$2970`; the upper-right pair has started retracting to `$2A08/$2A28`.
Native P1 Y is `$0270` at row 2499 and `$026E` at 2500, with zero Y velocity.

`Obj_HCZCGZFan` (sonic3k.asm:65354-65400) calls AllocateObjectAfterCurrent
from the original object's initializer, returns unchanged on rejection, then
turns a0 into the solid platform and initializes a1 as the fan. The platform's
initializer returns without running its latch/solid routine; the installed fan
Main can execute when Process_Sprites reaches the later a1 slot in the same
pass. The Java baseline instead creates a platform during fan construction,
retains even a rejected nonnull result, and combines platform and fan Main at
the allocated platform's later slot.

`HCZCGZFan_Platform_PlayerAbove` (sonic3k.asm:65590-65604) retains the latch
at exactly `-$30` and clears it only below that threshold. Thus a platform
at Y `$02A0` reading P1 `$0270` must remain latched before lower fans execute.
An interleaved later platform observing the already lifted `$026E` can clear
its latch prematurely. The native left/right asymmetry is useful discrimination:
slots 14/15 precede lower fan slots 28/29, while right platform slots 48/49
follow them and legitimately start retracting in that same pass. This is a dispatch/visibility hypothesis to discriminate
with the unchanged baseline observer, not a fitted delay or stored trace input.

The spin-down path also skips native fan sound/bubble side effects after its
frame advance: `HCZCGZFan_SpinUp` branches through
`HCZCGZFan_CheckFanSfx` / `HCZCGZFan_SpawnBubbles`
(sonic3k.asm:65429-65471). The relevant platform/fan subtypes `$A4/$94` and
`$A6/$96` have bit 6 clear, so that missing bubble branch cannot allocate or
consume RNG at this frontier. Sound omission alone does not write P1 position.
That separate inherited effect defect is not changed by this investigation.

### Independent old-source controls

`TestHczFanNativeDispatch` uses generic coordinates and real registry/allocation
owners rather than trace rows. Its three cases check original-slot solid
ownership and initializer return, saturated allocation with capacity recovery
and destructive graph restore, and an exact `-$30` platform threshold before
a later lower fan executes, again across destructive restore.

On unchanged runtime `b0dbd4825` the exact queued command was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestHczFanNativeDispatch' test -B
```

Result **3T/3F/0E/0S**, queue 602 seconds, Maven 33.402 seconds. The failures
respectively identify the original object as a nonsolid fan, find two objects
where only the unexecuted original should exist, and reject fan Main being
absent from the allocated later slot. These are old-source contract failures,
not a claim that the complete new continuation already passes.

A preceding continuous-probe attempt waited 554 seconds and spent Maven 1m29s
before test compilation rejected an incorrect six-argument ObjectManager.update
call in the new control. That overload was corrected to the existing
seven-argument API. No replay or gameplay result is attributed to that attempt.
The separate queued version probe passed with Maven 3.10.0 / Java 25.0.5.0.2,
waiting 563 seconds. Its wait overlaps the replay requests; those intervals
must not be added as elapsed delivery time.

### Live baseline discrimination and object repair

The read-only continuous wrapper retained the exact method and environment from
[the earlier bounded measurement](#current-hcz-bounded-remeasurement--2026-10-29).
Its observer reads only existing production player/object state after each
step; it never loads comparison rows into gameplay. The corrected invocation
completed **1T/1F/0E/0S**, queue 446 seconds, Maven 1m03s, case 30.19 seconds.
Segment 11 remains 62 residuals (11 camera physics / 51 animation), complete
14,208 rows, 59 lag rows and zero bootstrap errors. Segment 13 is complete
5,393 rows, 364 residuals (182 physics / 182 animation), seven lag rows and
zero bootstrap errors; first non-camera physics is unchanged at row 2500,
Y `$026E/$026B`. The strict SS7 boundary again stops at cursor 97208 in
SPECIAL_STAGE_RESULTS.

At row 2499 Java P1 Y is `$0270`, oscillator byte `$14` is 12, and its second
upper-left fan/platform pair occupies slots 48/49 at X `$2970`, slide offset
64, latch true. At row 2500 the oscillator byte is 13 and that pair has moved
to X `$2978`, slide 56, latch false; P1 is `$026B`. Native uses platform 15
and fan 51 for that pair and keeps X `$2970` at the same row. The lower fan
at Y `$0314` derives a two-pixel push from the native arithmetic; the prematurely
unlatched upper fan at Y `$0284` derives another three pixels. This identifies
`Obj_HCZCGZFan`'s reversed slot roles and constructor-time allocation as the
first causal owner. Neither a global one-frame delay nor a latch tolerance
would reproduce the source's left/right slot distinction.

The repair selects a solid platform for the original bit-7 placement and
allocates a separate fan only from the original initializer. Rejected helper
results are not retained: the original stays uninitialized and retries on its
next dispatch. A successful initializer installs the child's Main for later
same-pass execution and returns without running platform Main. Platform Main
now performs only its own latch, slide and solid checkpoint; fan Main executes
from the allocated fan slot. The existing object manager and generic snapshot
owners are used unchanged. Platform recreation restores its captured exact fan
reference rather than choosing a nearest same-class parent. This restores ROM
semantics without adding a FixBugs exception or any hardware simulation.

All four upper pairs are present in the baseline observer. A missing/rejected
child is therefore not the demonstrated row-2500 cause. The stale rejected
handle is a source-confirmed risk removed by restoring this same initializer;
no allocation-starvation frontier is claimed. Likewise, the premature platform
Main at birth is corrected by the native initializer return, but its independent
position consequence at birth is not inferred from the later row-2500 result.

The first candidate focused invocation selected `TestHczFanNativeDispatch`,
`TestHCZCGZFanObjectInstance`, `TestS3kHczCgzFanGraphRewind`, and
`TestNativeObjectFacingGuard` with the same display-free Java 25 queue wrapper.
It ran **22T/2F/1E/0S**, queue 453 seconds, Maven 1m32s. All 13 fan and four
facing-guard cases passed. The failures exposed fixture assumptions: snapshot
occupancy intentionally excludes anonymous reserved slots, the platform is now
placed rather than dynamic, and full manager updates require a camera. The
controls were corrected to fill capacity with actual rewind-recreatable objects,
retire the placed test object through its destruction state, and provide a camera.
No production or shared snapshot change followed that result. Only the affected
five native-dispatch/graph cases are repeated before route qualification.

The downstream creator-callback checkpoint regression now follows the actual
spawnless dynamic fan (preserving its identity and engine ownership assertions).
The historical `FanPlatformChild` class name is retained for the existing
rewind inventory. No shared engine, loop, timing, loading, audio, rewind or
creator runtime owner was edited. The original ROMs for mandatory anchors also
match their root-table CRC32/SHA-1 identities. Citation checks introduce no new
findings; all 25 stale pitfall references match the base by exact citation and
failure, and the new mirrored entry is identical in both skill trees.

The corrected narrow command was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestHczFanNativeDispatch,TestS3kHczCgzFanGraphRewind' test -B
```

Result **5T/0F/0E/0S**, queue 556 seconds, Maven 35.358 seconds. Fresh physical
XML confirms all three allocation/dispatch controls and both graph cases run.
Combined with the unchanged runtime's 13 fan and four facing cases above, all
22 distinct focused cases pass; this is not represented as one 22-case green
invocation. The two production files, sorted by repository path with each path,
NUL, file bytes and NUL, hash to SHA-256
`8403ffa05b12f1138592313b81233f4078b2e7d6b0350f5000032a37039a12a5`.
Only test-fixture edits followed the first focused result; the runtime is the
same for its passing cases and this retry. The actual failure/retry reservation
barrier is now carried by real rewindable occupants, rather than anonymous
helper reservations that are intentionally excluded from captured occupancy.

### Slot-role qualification and the next causal boundary

The combined candidate command used the same display-free Java 25 queue wrapper,
original absolute S1/S2/S3&K ROMs, EGL selection, trace profile, one fork and
alphabetical ordering, with this selector:

```text
-Dtest=TestCourseRestoreCreatorOwnerScope,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kHczCampaignFrontierProbe#continuousHczThroughSeventhSpecialStageReturn,TestS3kHczZoneSliceTraceReplay#replayMatchesTrace
```

It completed **66T/2F/0E/0S**, queue 754 seconds, Maven 1m21s. All 60 mandatory
S3K anchor cases and all four physical creator-restore cases pass. The alternate
movie retains 4,354 differences (3,425 physics / 929 animation), first row 9482
`air` one/zero, 29,302 reported frames, zero bootstrap errors and the same eight
absent advertised observation families. Its complete 4,354 error-span dictionary
list, canonical JSON with sorted keys and compact separators, hashes to
`b830211cdaa8f2e6969d3b45a3d95d7df1b80ecc1252a78f792ec9f2d071b72e`.
This remains a bounded red alternate control, not complete-route or slot-identity
qualification.

The primary observer accidentally called a default coordinate getter on a
null-spawn support object at row 3593. The resulting walk failure and undrained
future completion messages are consequences of that observer exception, not
attributed production/timing defects. Segment 13's resulting 182-animation-only
report is incomplete and is not counted as a finished replay. Native row-2500
Y `$026E`, platform slots 14/15 and fan slots 50/51 already match there. The
observer was restricted to the known column implementation for the later window;
only that primary method was repeated, without a runtime change.

The completed primary retry is **1T/1F/0E/0S**, queue 482 seconds, Maven 1m07s,
case 33.28 seconds. Segment 11 retains its complete 62 residuals. Segment 13
consumes all 5,393 rows: **190 differences = eight physics / 182 animation**,
seven lag rows, zero bootstrap errors. Its first non-camera physics error is
now **row 3596, P2 ground speed native `$0000` / engine `$00C3`**, after matching
non-camera physics through row 3595. The physical walk still reaches SS7 results
and stops at cursor 97208 before `hcz_4`.

At row 3596 both engine and native have column slot 24 at `$37CF,$0260` and P2
at `$37B4,$0288`, X velocity zero and Y velocity `$FB08`. P2 had X `$37B5` on the
preceding row. `HCZSpinningColumn_Main` (sonic3k.asm:68176-68198) calls
SolidObjectFull with its updated X, half-width `$1B`, and vertical halves
`$20/$21`. `loc_1E042` / `loc_1E056` (sonic3k.asm:41513-41528) separate a
nonzero left-side overlap and clear ground/X velocity unless X velocity is
negative; zero is accepted. The opposite side rejects zero, and exact zero
overlap bypasses clearing. Original ROM bytes at `$326B6` include the call to
`$1DC56`, and `$1E042` contains that sign test and ground-velocity clear.
The column lacks the already-existing `zeroXSpeedStopsOnLeftSideContact`
provider opt-in, whose default instead requires positive X velocity. This is
an object-owned missing semantic declaration; the shared solid controller need
not change. Five independent actual-ROM contact/restore cases discriminate
zero/positive/away velocity, the opposite side and exact-edge admission.

### Matched current-base witnesses and exact alternate comparison

A temporary local Python driver retained five owned candidate files in memory,
replaced only the fan, registry, column and their two constructor-dependent fan
fixtures with exact `b0dbd48258417efc60b07d8943b1418b893dae02` bytes, and restored
all candidate bytes in `finally`. It neither switched branches nor touched
unrelated files or reference trees. The new generic fan controls and five new
column contact controls remained as the independent observers. The command used
the preceding combined environment/profile/ROM/fork/ordering arguments with:

```text
-Dtest=TestHczFanNativeDispatch,TestHczSpinningColumnCachedRelease#zeroXLeftSideStopsInertiaThroughFreshWorldRecreation+sideStopDirectionAndExactEdgeControls,TestS3kHczZoneSliceTraceReplay#replayMatchesTrace
```

Result **9T/5F/0E/0S**, queue 382 seconds, Maven 1m48s, Maven exit one. All three
final real-allocation fan controls fail on the base, without errors/skips. The
column zero-X left-side witness fails with ground speed expected zero / actual
1024; its four positive/away/opposite/exact-edge controls pass, including fresh
world recreation. The fresh alternate again reports 4,354 differences, 29,302
frames and zero bootstrap errors. Its complete error-span list has the identical
`b830211c...71b72e` hash above: this qualifies equality of all 4,354 dictionaries
against the slot-role candidate, not merely totals or first-error identity.
It does not establish order independence or complete auxiliary/route coverage.
The candidate files were restored byte-for-byte and checked before continuing.

The column correction now returns true from the existing zero-X left-side stop
provider contract, with the owning native call and sign-test citations. No
shared collision or execution code changes. The five new cases check actual
ordinary registry allocation, P2 body contact through the normal inline
FullSolid call, separated side/edge controls, restored pre-contact inertia and
fresh world/object recreation. They use the existing generic component region,
not the movie's coordinates or frame. The first column candidate's three production paths (fan,
column, registry), sorted and hashed with path/NUL/bytes/NUL, have SHA-256
`160e4584ad70326d0f35d798d2e8d8b7ce6d3e4758b24b72d631827568fafa78`.

### Column edge control and completed HCZ comparison

The first column qualification used the same environment/profile/ROM/fork/order
arguments and this selector:

```text
-Dtest=TestHczSpinningColumnCachedRelease,TestS3kHczSpinningColumn,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kHczCampaignFrontierProbe#continuousHczThroughSeventhSpecialStageReturn,TestS3kHczZoneSliceTraceReplay#replayMatchesTrace
```

Result **80T/3F/0E/0S**, queue 603 seconds, Maven 2m18s. All 60 anchors and the
eight existing column unit cases pass. The actual route consumes every segment-13
row with **182 animation differences and zero physics differences**, seven lag
rows and zero bootstrap errors. Read-only row 3596 now has P2 ground speed zero;
X/Y and both velocity words match the native observation. Segment 11 retains
62 residuals, and SS7 still stops at cursor 97208. The alternate's entire
4,354-dictionary hash remains `b830211c...71b72e`, matching the fresh exact-base
control.

The third failure is the exact-zero-overlap negative control: expected retained
inertia 1024, actual zero. Enabling the zero-X stop made the shared pre-movement
compensation classify that boundary as moving into the object. Native
`loc_1E042` (sonic3k.asm:41513-41519) instead branches around both velocity writes
for d0 zero. The column therefore also needs the existing
`preservesZeroDistanceSideContactMotion` declaration. This remains an owner-only
profile correction; the shared controller is unchanged. The control was retained,
not weakened, and extended with positive-X exact-edge velocity preservation.
The final six new contact cases assert both velocity words, restored pre-contact
values and fresh world recreation.

Only the affected column/route selection is repeated after this local profile
edit; bootstrap/loading/creator/fan checks already completed above are not
repeated as a ceremony. The final three-production-file fingerprint is SHA-256
`67c4eaa1e93f568be70ddac565cc60e4e2a86b8c9251aa7c9971b72505a3d880`.

### Remaining SS7 ownership/evidence overlap

The HCZ fixes now reach the already-known SS7 physical boundary with clean
segment-13 physics, but do not establish the cause of its results/return stop.
`ss_7` is segment 14, original movie offset 90491 with 4,992 represented
special-stage rows (ending exclusively at 95483). Its aux gzip is empty. The
next represented HCZ2 segment begins at 97208, leaving 1,725 unrepresented movie
rows between the represented stage and `hcz_4`. The final stored special-stage
row has spheres zero and clear routine three; it is not a results-state/timer
observation. The engine's SPECIAL_STAGE_RESULTS mode at cursor 97208 alone
cannot distinguish delayed results entry, a results routine or handoff readiness.

The next attribution needs a native phase/result observation and the S3K special
stage/results owner together with the shared SpecialStageLifecycle/GameLoop/load
handoff. That ownership overlap is reported rather than changing those owners
in this leaf. No extra wait, recorder/game/fixture predicate, trace hydration,
comparator relaxation or inferred endpoint is introduced. SS7 remains explicitly
unresolved; neither whole-HCZ outgoing continuity nor a campaign is certified.

### Final local qualification and handoff

The final affected command is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestHczSpinningColumnCachedRelease,TestS3kHczSpinningColumn,TestS3kHczCampaignFrontierProbe#continuousHczThroughSeventhSpecialStageReturn,TestS3kHczZoneSliceTraceReplay#replayMatchesTrace' test -B
```

Result **21T/2F/0E/0S**, queue 333 seconds, Maven 2m22s. All 11 actual-ROM
column contact/lifecycle cases (including the six new contact/restore cases)
and eight existing column unit cases pass. Fresh XML identities and the
current run's JSON were inspected. The two failures are the unchanged strict
replay boundaries, not component failures. The primary case takes 31.09 seconds;
the alternate takes 11.492 seconds. Maven logs retain 494 presentation-shadow
SMPS warning lines and 20 FAST-profile warnings; these are distinct from the
trace report's zero warning count, and no audio owner is changed.

The final continuous result consumes all 14,208 segment-11 and 5,393 segment-13
stored rows. Segment 11 is unchanged: 62 differences (11 camera physics / 51
animation), 59 lag rows, zero bootstrap errors, no non-camera physics mismatch.
Segment 13 is **182 differences, all animation; zero physics differences**,
seven lag rows, zero bootstrap errors and no first non-camera mismatch. The
original row-2500 lift and subsequent row-3596 inertia frontier are both closed.
The partial dynamic-art journal has 14 observed gaps and zero failures. SS7
results still stops at cursor 97208 before `hcz_4`, as described above.

The final alternate remains strict-red at row 9482 `air`, with 4,354 differences,
3,425 physics / 929 animation, zero report warnings/bootstrap errors and 29,302
reported frames against 31,482 planned rows. Every error-span dictionary still
matches the fresh exact-base control: canonical SHA-256
`b830211cdaa8f2e6969d3b45a3d95d7df1b80ecc1252a78f792ec9f2d071b72e`.
No new/worsened alternate failure is hidden behind equal totals. Its missing
auxiliary families and incomplete full-fixture extent remain limits.

Earlier completed coverage is retained by its actual source/selection: the fan,
allocation, graph and facing checks cover 22 distinct cases, creator callbacks
four cases, and the required S3K anchors 60 cases. The latest narrow edit only
declares the column's native exact-side-edge behavior and is covered by all 19
column cases and both affected movies. It changes no loader/bootstrap, creator,
fan or shared owner, so those completed selections are not repeated. This is
bounded focused/route validation, not broad smoke, full-suite, native pixels,
all viewport/donor/team combinations, outgoing HCZ continuity or a campaign pass.
Lead-owned combined validation and integration remain separate.

The final source is the three-file production fingerprint `67c4eaa1...05a3d880`
over `b0dbd48258417efc60b07d8943b1418b893dae02`, in the worktree named above.
The final temporary prefix/observer wrapper hashes to SHA-256
`246f4a1496f867a6121a831b22d13117d20cf9a5f47965eb6868e2e74e22919a`.
Its only production observation is through the existing callbacks; the wrapper,
matched-baseline driver and consumed raw diagnostics are removed before local
handoff. No trace fixture or submodule pin changed. No owned jobs remain after
this completed run; queue waits overlap other recorded requests and are not
summed into delivery elapsed time. Unmeasured task time is not assigned to queue
or execution. The local branch/worktree are retained for the lead; no push, PR,
integration or release is performed by this worker.


## SS7 results countdown attribution — 2026-10-29

Worker `bugfix/ai-s3k-hcz-ss7-results`, provisioned T3 checkout
`t3-92d6f032`, fast-forwarded to pushed develop
`2e35b5814db853c592345464ef15e6bb27f0d862` before measuring. This leaf owns
only the special-stage results correction, focused tests and associated evidence.
Shared Engine/GameLoop, timing, loading, audio and rewind implementations remain
outside its edit scope. No matching active SS7 replay/capture was found before
launch; unrelated queued jobs and dirty main-checkout references were preserved.
This checkout's disassembly and TraceChaser are clean at the pins already named
above (`8b2cf3c4` and `703bd92d`).

### Native body and input boundaries

The original absolute locked-on ROM and run-local
`s3k-sonic-tails-complete-emeralds.bk2` were read without copying/linking ROMs.
Their verified identities remain CRC32 `63522553`, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`, ROM SHA-256
`fba0677fde9f76df93f3e98d6310d8af68b9847bde16e253d73cd4dd8134ed23`,
movie SHA-256
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`,
and manifest SHA-256
`e2bcf1dfdcf3952ec07f61205e765f90fcb5405ab2ed7d8f054fde742a2f8f46`.
The original movie has 539,492 input rows. No endpoint or fixture is changed.

An external C# witness links the clean pinned native harness, uses the verified
stock BizHawk 2.11 installation documented in the
[native evidence audit](2026-10-29-s3k-native-terminal-evidence.md#original-inputs-and-runtime),
and feeds original rows from power-on through completed frame 97,300. It writes
no RAM, loads no state and adds no movie inputs. End-of-frame RAM/register reads
cover completed frames 95000–97300. Execute callbacks are registered only at
95000, at ROM-byte-verified routine entries, to observe the actual results body
and its owner (`a0 = $FFFFB940`, code `$2E3B8`). Capture completed with exit zero,
97,300 exact host advances and 89.3628396 seconds measured execution. The bounded
witness source/executable SHA-256 values are respectively
`39247281ab1c95504b4b8eb5680ec3b2570ae5c448126d37bd80385a197488af`
and `44ae9ecd08570e28f0c9aa9b9468fd7ac02411992093d7515a22de07c388a681`;
the output hashes to
`4be9e26cab7e2c9b1600a3a372133e61bcf5a7a099a6a11066d90286babb5161`. The prior
callback-from-boot attempt was terminated as task-owned instrumentation overhead;
it produced no usable phase rows and is not evidence. An earlier launcher lacked
`BIZHAWK_HOME` and failed before playback; two compiler dependency/API errors
also preceded the successful witness. None is counted as successful capture.

Coordinates in this table are **completed native input-frame counts**, not
zero-based movie indices, trace rows or `Level_frame_counter`:

| Completed frame | Observed native boundary |
| --- | --- |
| 95484 | Emerald collection writes Game_mode `$48`; 7 emeralds, 74 rings, zero spheres/rings left. This is still the stage's exit tail. |
| 95544 | `SpecialStage_Results` executes at `$2DF64`, after the sixty exit waits. |
| 95558 | `loc_2E226` installs the results object after the resource wait. |
| 95559 | `loc_2E3DA` initializes it: routine 2, timer 360, time bonus 5000, ring bonus 740. |
| 95920 | `loc_2E484` executes the first tally pass after the zero-reaching wait returned. |
| 96420 | `loc_2E4C4` falls through to routine 4; timer 120 becomes 119 that pass. |
| 96540 | Routine 6 starts the continue wait at 270 and decrements to 269 that pass. |
| 96810 | `loc_2E540` / `loc_2E58C` select the Sonic 3-side reveal and create five cleanup sliders. |
| 96824 | `loc_2E5C0` creates the reveal, sets routine `$A` and arms **one** timer at 240. |
| 97065 | `loc_2E5E0` sees zero and reaches `loc_2E5EC`; Game_mode becomes `$8C` as returning Level entry begins. |
| 97208 | Game_mode reaches `$0C`, the next represented `hcz_4` boundary. |

All 1,507 native results-object rows (95559–97065 inclusive) are non-lagged,
with zero original movie buttons and zero raw/logical controls/locks. The saved
origin is `$0101`, `SK_special_stage_flag = 0`, Chaos count seven. Native
`Level_frame_counter` is 10222 on initialization, 11487 on reveal creation,
and 11728 on release; `V_int_run_count` is respectively 94720, 95985 and 96226.
This identifies the owning branch and executed-body clocks without inferring
anything from the cursor stop or hydrating engine state from observations.

### Matched unchanged engine boundary

The temporary package-local `TestS3kSs7ResultsProbe extends AbstractRunChainTest`
uses the existing strict continuous prefix helper targeting segment 15 row 2.
Its observer only reads live state after production; source SHA-256
`e3cac116d174603e47d5283a156ee6cae8d6761c554c871ebd6275449529ba3b`.
Java observer `row=N`
names the zero-based physical movie input being executed; compare it with native
completed frame `N+1`, not the same printed integer.

At those aligned boundaries the engine enters results preparation on row 95543,
installs the screen on 95557 and initializes it on 95558. Its initial 360 wait,
500 tally passes, continue timer fallthrough and cleanup/reveal creation on row
96823 agree with native. It retains the correct origin, seven emeralds, 74 rings
and perfect bonus. On row **97063** it finishes its first 240 elapsed ticks but
switches to `STATE_EXIT` and resets another 240-tick wait. Native only decrements
its one timer to zero on completed frame 97064, then releases on **97065**;
Java instead remains in results on aligned row **97064**. This is the first
observable release/admission disagreement in the matched results body. The
extra state/wait is the causal local defect; no input, origin, results-entry or
resource-wait mismatch is needed to explain it.

The exact baseline command is the prior HCZ prefix command with
`-Dtest=TestS3kSs7ResultsProbe#throughSs7Return` and the same absolute ROM,
`--normal`, `-Ptrace-replay`, `-Dsurefire.forkCount=1`, alphabetical order,
`-Dopenggf.headless.gl=surfaceless-egl`, and both desktop display variables unset.
It completes **1T/1F/0E/0S**, queue 505 seconds, Maven 1m58s, test 30.954s.
Its three failing axes are the physical SS7 results stop at cursor 97208 and
unchanged segment-11/13 comparisons. Segment 11 retains 62 differences
(11 camera physics / 51 animation), 59 lag rows, first strict row 6640
`player_animation_id` `$13/$05`; segment 13 retains 182 animation differences,
zero physics differences, seven lag rows, first strict row 1413
`sidekick_animation_id` `$00/$18`. Both consume their complete stored rows;
bootstrap/report warnings are zero. The partial journal has 14 gaps / zero
failures. These are fresh current-base observations, not whole-HCZ certification.

The 1,725 unrepresented source movie frames retain their existing meaning.
The empty SS7 aux file is not replaced with inferred observations, and no
readiness or comparison authority is added. The native witness is diagnostic
comparison evidence only; strict timing-port job matching and admission remain
owned by their existing implementations.


### Local correction and independent controls

`S3kSpecialStageResultsScreen` now arms `romTimer = 240` when it creates the
reveal and consumes it with the shipped `tst.w / beq / subq.w / rts` order.
It removes the redundant second exit state. The release is the 241st subsequent
object pass, as observed at `loc_2E5E0` / `loc_2E5EC`
(sonic3k.asm:63546-63555); both `loc_2E5C0` and `loc_2E5F4` arm that same
countdown. There is no route/frame/game predicate, wait fitted to the gap,
input padding, inferred trace observation or comparator change.

Four `TestS3kSpecialStageResultsReveal#chaosRevealReleasesAfterOneCountdown`
parameter cases use generic stage/character context with Sonic 3-side and S&K-side
origins, each below and at the 50-ring continue threshold. Each follows the
production tally/cleanup until its actual message exists, checks the correct
message, rejects release on all 240 decrement passes and requires release on
the following zero-timer pass. Before the production edit all four fail at that
last assertion: **4T/4F/0E/0S**, queue 670 seconds, Maven 34.370s, cases 0.488s.
The exact queued command is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME="$JDK25" \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestS3kSpecialStageResultsReveal#chaosRevealReleasesAfterOneCountdown' test -B
```

Results remain explicitly non-rewindable; no new snapshot owner is introduced.
The existing manager exit/restore cases cover the adjacent sixty-wait stage
exit, including capture halfway through the spin, completion, restore and exact
forward completion. Those cases are included in candidate verification.


The candidate retains the same prefix/observer but targets segment **16 row 1**,
so `hcz_4` must close and receive its ordinary strict report before the first
MGZ row can stop the probe. It does not claim that merely reaching a prefix
grades the active destination segment. Exact candidate invocation:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME="$JDK25" \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestS3kSpecialStageResultsReveal,TestS3kSpecialStageResultsTally,TestS3kSpecialStageResultsPreparation,TestSonic3kSpecialStageManagerSnapshot,TestGameLoopSpecialStageResultsHandoff,TestHardwareTimingAuthorityGuard,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kSs7ResultsProbe#throughSs7Return' test -B
```


### Candidate result and remaining frontier

Completed result: **141T/1F/0E/0S**, queue **416 seconds**, Maven **2m17s**;
the continuous method executes in **37.713s**. All **140** focused checks pass:
60 S3K anchors, 20 reveal cases (including the four old-red cases), 23 tally
cases, one actual-ROM preparation case, ten manager snapshot/exit/restore cases,
one results handoff case and 25 hardware-timing authority guards. No broad
smoke/full suite, public release, push, PR or integration is performed here.
The lead retains combined validation and delivery ownership.

On aligned engine row **97064**, the corrected screen reports `romTimer=0`,
`stateTimer=241`, `frameCounter=1507`, `complete=true`: native completed frame
97065 is the release. The source's 1,725 unrepresented frames are consumed with
unchanged semantics, and `hcz_4` is admitted with zero bootstrap differences.
An earlier engine `LEVEL` label during returning load is not equated with native
`$0C`; the actual destination admission and represented-row comparisons remain
the boundary evidence. The next source consumes all **3,687** stored rows and
its report is complete. It is **not** a clean outgoing-HCZ route:

- First strict segment-15 difference: row **940**, `camera_x`, native `$3941`
  / engine `$3940`.
- First non-camera physics difference: row **1748**, `y`, native `$0777`
  / engine `$0772`. No non-camera physics mismatch is reported through 1747.
- Segment 15 totals **27,401** differences: 22,209 physics / 5,192 animation,
  145 lag rows, zero bootstrap errors and zero report warnings.
- The next physical stop is plain MGZ destination admission at cursor/offset
  **100896**, before its first comparison row. Closing timing authority also
  rejects unmatched recorded completions, beginning with segment-15 raw row
  **1258**, `PRE_MAIN_LOOP`, `KOS_DECOMPRESSION_QUEUE#269`, fingerprint
  `107442b529cf8edafb0750d0198606ee2c4a667f4f99bb3c68065188177cf1e6`, engine
  pending `<none>`; then raw 1259 `KOS_MODULE_QUEUE#189`. Missing submitted work
  is evidence for the next investigation, not permission to create it from
  trace rows or weaken matching. Neither the queue owner nor the row-1748
  causal gameplay owner is attributed by this leaf.

The four failed axes are that later admission rejection, unchanged segment 11,
unchanged segment 13 and newly reached segment 15. The former SS7 stop is gone.
Every completed report for segments 0/2/4/6/8/9/11/13 is canonically identical
to the fresh baseline JSON, including the retained mismatches, fields, repeats,
counts and groups; this is stronger than matching totals but does not invent a
full mismatch stream absent from the bounded report. Segment-11 and -13
canonical JSON SHA-256 values remain respectively
`b4fd8b192b68723d992ad48b9b097b2525e2804a1094dd4c1a1aa2faf12bb632` and
`dadcda480fe7c69683352f9dd8967e6c6b8ff4e94136c20e829b3ffeffdadde4`.
The new segment-15 summary hashes to
`499e398d12989301b9f337861ddfd3081f9b4a41563d09b913b2b4c49d50a9af`.
The partial dynamic-art journal now has 15 gaps and zero failures. Existing
presentation-audio and FAST-profile Maven warnings remain distinct from the
reports' zero warning fields. No animation, later HCZ gameplay or shared owner
is changed to conceal those remaining failures. Base develop cannot reach this
new interval continuously; its later mismatches remain newly visible and
unattributed, not falsely classified as either regressions or baseline passes.

Tested production source is base `2e35b5814db853c592345464ef15e6bb27f0d862`
plus the single results file, SHA-256
`d82210940ec7ec02e56aa2cb2963109f1d0826c01caab2c876e918a1a74ac2b7`.
The committed reveal test file hashes to
`ca1044d441e21379caab936722702d7adc3c47635a31b67bd6be6ccc4bc270a9`;
the final temporary prefix/observer hashes to
`023ff30c8c439eceae20b83e9048e76e021a35f5dc72afaf35f6f410c4327db4`.
The temporary driver and consumed Maven/JSON/XML diagnostics are removed before
handoff. The external task directory retains the bounded native C# source,
executable, capture and native launch log, identified above. No owned jobs remain.
Queue/execution intervals overlap and are not added into delivery elapsed time;
other task time is unmeasured. The local branch/worktree are retained for the
lead, with shared implementations, fixtures, submodule pins and contracts intact.

## Post-SS7 first missing work — starpost invitation owner (2026-10-29)

Implementation worker `bugfix/ai-s3k-post-ss7-causal`, provisioned checkout
`t3-3e35c0ca`, fast-forwarded its original `c4cd6ae0e` checkout to the reviewed
SS7 merge `4bfc262b50cabfecace02bedfc194901d74a3c0a`. That merge includes
develop `430051ee4dee4f5e1f629a8a73467a6d76537f11`; the SS7 correction remains
attributed to its original author. No main checkout or foreign worktree was
modified. The local disassemblies and TraceChaser remain clean at their pinned
commits, including `skdisasm` `8b2cf3c4178f2382f844da67798de4befc50c5e0`
and TraceChaser `703bd92d32634ce5ebac60625b48c60ef5500427`.

### Original-input native attribution

An external read-only C# witness links this checkout's rebuilt pinned native
harness and the previously verified official BizHawk 2.11 runtime. It advances
exactly 99,000 original movie rows from power-on, with no state load, RAM writes,
input changes or synthesized tail. Completed-frame observations cover
98,000–99,000 inclusive. Execute callbacks are attached only for that interval;
the decisive entries are ROM-byte-verified `Queue_Kos_Module` `$001AD2`,
`Load_PLC` `$0016FA`, `Obj_HCZEndBoss` `$06AEB6`, and the ordinary boss-hit
velocity-negation branch at `$010134`. Exploratory callbacks at the boss main
and flash interval are not used as independent timing authority.

The capture completed with exit zero, 99,000 checked host advances and
**86.4282945 seconds** execution. Two initial C# compiler failures omitted
assembly references (Cores, then netstandard); they produced no native evidence.
The rebuilt harness SHA-256 is
`dad8d30f427978166fcc048dafc8a8d41e6dfce9615a664f33b159cd2f6c44a3`.
Original ROM/movie/manifest hashes are unchanged from the SS7 section above.
The durable external task directory is `s3k-post-ss7-causal-20261029`
(absolute location supplied in the worker handoff).

At **completed input frame 98467**, native `Queue_Kos_Module` receives
`a1=$187B8A`, `d2=$BD80`, caller return `$02D0D0`, owner `a0=$FFFFB818`
(absolute SST 28), code `$02CFA8` (`Obj_StarPost`), routine 2, position
`$3F80,$07EC`. Native `Level_frame_counter` is 1258 and `V_int_run_count`
is 97626. The original run's segment-15 row 1258 names that completed frame:
offset 97208 + zero-based row 1258 + one completed input advance.

The existing auxiliary rows independently show that post transitioning
routine 2 → 4 and four real routine-8 bonus-star children appearing at slots
10–13 on row 1258. Its ordinary orbiting child occupies slot 9. This identifies
the actual owner rather than deriving a producer from a completion ordinal.

A follow-up narrows observations to completed frames 98460–98475 and adds
the missing eligibility operands at that same enqueue callback. It explicitly
reads **seven Chaos emeralds and 180 rings** at frame 98467, with the same
owner, call return, art source, destination and clocks. It advances exactly
98,475 original rows, exits zero and measures **79.5255385 seconds** execution.
This additional selection resolves the first witness's absent emerald-count
column; it is not a duplicate queue/readiness qualification.

The native source is `sub_2D028` (sonic3k.asm:61648-61692): after allocating
the ordinary child it checks **20 rings**, then calls `sub_2D3C8`. There is no
emerald-count test on this path. `sub_2D3C8` / `loc_2D436` /
`loc_2D474` (sonic3k.asm:61870-61926) allocate four ordinary SST children,
select star art from the ring-count quotient and locked-on divisor, and submit
the selected KosM parent. With 180 rings, `((180-20)/15)%3 = 1` selects
`ArtKosM_StarPost_Stars1`, the verified ROM source `$187B8A`.

A small external inspector calls the pinned producer's own descriptor scanner
and fingerprint function on those ROM bytes, without creating gameplay work:

- Parent source `$187B8A`, destination `$BD80` reproduces fixture
  `KOS_MODULE_QUEUE#189` fingerprint
  `0fbbcb25822bda53fc0b212780f2218200ef117c753fa623fa1a05c66379f152`.
- Its direct child source `$187B8C` has 84 compressed bytes, 192 output bytes,
  and destination `Kos_decomp_buffer=$FFFFD000`. It reproduces fixture
  `KOS_DECOMPRESSION_QUEUE#269` fingerprint
  `107442b529cf8edafb0750d0198606ee2c4a667f4f99bb3c68065188177cf1e6`.

These are exactly the first two unmatched completions in the reviewed SS7
result, at row 1258 PRE_MAIN_LOOP and row 1259 POST_OBJECTS. This attribution
does not authorize constructing their jobs from the recorded completions.

### Edit boundary

`Sonic3kStarPostObjectInstance.shouldSpawnBonusStars` contains an explicit
`emeralds >= 7` early return before the ring-count check and
`spawnBonusStars`/`queueStarPostBonusArt`. The native owner just observed does
not contain that predicate. It is a S3K-wide checkpoint/bonus invitation owner,
not an HCZ zone/results routine. This leaf was authorized to correct proven
local zone/results behavior and to report a shared-owner requirement to the
lead. No starpost, loading, collision, GameLoop, timing-port, input, audio,
rewind, comparator or fixture contract is changed here. The next authorized
implementation should address the native invitation owner and its allocation,
art, return and rewind consumers together, then repeat the same continuous
outgoing-HCZ boundary before attributing the later first-hit physics cascade.

### Matched baseline route and evidence identities

The first temporary package-local `TestS3kPostSs7CausalProbe` uses the existing
continuous prefix helper targeting segment 16 row 1. It changes no production
driver and does not use observations as engine inputs. Its printed `row` was
originally described as the completed input count. That attribution is
superseded by the call-site correction in the later candidate section: the
callback runs before `loop.step()`, with one fewer input completed than its
printed physical movie index + 1.

The command is the SS7 candidate command above with only
`-Dtest=TestS3kPostSs7CausalProbe#throughHczReturn`. It completes on unchanged
production source `4bfc262b5`: **1T/1F/0E/0S**, queue **729 seconds**, Maven
**2m09s**, method **34.246s**. Fresh XML verifies Java **25.0.5.0.2**,
`java.home=/usr/lib/jvm/java-25-openjdk`, and `surfaceless-egl`; both desktop
display variables were unset. A separate queued version request was cancelled
before execution once that actual fork established Java 25. It supplies no
additional version/test result.

The four failed axes remain MGZ admission at cursor 100896 and segment
11/13/15 strict comparisons. All 3,687 outgoing-HCZ rows are consumed; segment
15 is complete with **27,401** differences (22,209 physics / 5,192 animation),
145 lag rows, zero bootstrap/report warnings, first non-camera row **1748 Y
`$0777/$0772`**. Segment 11 retains 62 differences and segment 13 retains 182.
Their canonical JSON hashes exactly match the prior SS7 section, including
segment 15's `499e398d...50a9af`; the other completed level segments retain
zero differences. The partial dynamic-art journal has 15 gaps / zero failures.
This is an unchanged strict-red frontier, not a campaign or parity pass.

At completed count 98956 / segment row 1747, both native and engine end at
Y `$0774` with vertical velocity `$0300` after the ordinary boss hit. The next
native end is Y `$0777`, velocity `$0338`; the engine end is Y `$0772`,
X velocity `$0200`, Y velocity `$FC00`. Boss phase/position also differs in
this interval. This bounds the later symptom but does not establish a second
independent causal owner while the earlier invitation work/graph is absent.
No fitted boss delay or collision change is made.

The production `src/main` Git tree is
`063c98ee577a2ccf4a294872cf4c83de158f3ed9`; the original run fixture Git tree
is `b9c0bf21fc5b95868a6383bcf46afa5a7b7a8c96`. Both remain exact baseline
bytes. The first temporary Java observer SHA-256 is
`b03cac532c49afac68c83db42dcbb3666580f4dcf985f96c38e25940390b60d4`.
External native evidence identities (SHA-256):

| File | SHA-256 |
| --- | --- |
| `PostSs7Witness.cs` | `be7dd84f719a84267f398e23c944b4228a2858df16ea49bf7d9853874148df54` |
| `PostSs7Witness.exe` | `3e16aa23074606aeae72e36c661dc01cae67a16c888aad08539729861e321c14` |
| `native.csv` | `039389b42bfd87befd8f7fe240377c6d67e6edce4446aefac4709d9151135b39` |
| `StarpostEligibilityWitness.cs` | `e5ede80904fcc50ab7548edaa682ca09bee6cf82828d1bec983e468461caea31` |
| `StarpostEligibilityWitness.exe` | `869e9049404bd32bc4242b9d6531000b26ae40d3d2d9a55ef3aa83949914b0e3` |
| `native-eligibility.csv` | `c8d863370a6945e64503a033f18b319451f06344ac7f972a41e9a2a27a89c55c` |
| `Fingerprint.cs` | `4803fe58e360222a85f680976713eb47b7438c84c9071dc8dc4edaa2c10a1ccc` |

Native execution intervals overlap queued Java requests; they are not summed
into elapsed delivery time. Remaining task time is unmeasured.

As an input/sampling control, all **1,001** completed native observations
(segment rows 791–1791) match the committed original physics rows exactly
for P1 centre X/Y, X/Y speed, Y fraction and `Level_frame_counter`: **6,006
scalar comparisons, zero differences**. This checks the witness's alignment
over the starpost and first-hit intervals; it does not expand the engine's
parity coverage. The original movie's P2 pad is neutral throughout the first
99,000 inputs, so the witness's P1 button forwarding drops no active P2 input.

### Live gate isolation and final focused result

The final observer adds the live invitation operands, not a hypothetical
counterfactual state. Before completed count 98467, the engine has seven
emeralds, 180 rings, bonus-entry run policy allowed, `usedForSpecialStage=false`,
activation mark 3, post subtype/index 4 and `activated=false`. At 98467 the
ordinary contact succeeds: mark becomes 4, `activated=true` and one ordinary
orbiting child exists. **Zero bonus stars and zero pending hardware jobs**
remain through 98470. No prior consumed-entry flag or denied run policy explains
the missing invitation. The engine post currently occupies SST8, versus native
SST28; that allocation difference is recorded, not silently normalized or fixed.

The temporary `TestPostSs7SevenEmeraldStarpostProbe` extends the existing
ordinary-contact fixture and varies only collected emerald count through the
real `GameStateManager.markEmeraldCollected` API. It calls the existing
normal-contact assertion (20 rings, checkpoint saved, four bonus children).
Counts **0 and 6 pass; count 7 fails** with
`expected: <4> but was: <0>`. The exact parameter value is independently checked
before contact. These are ordinary component setups, not trace snapshot seeding.
No production edit/new-green result is claimed: the proven S3K-wide owner is
handed to the lead under this leaf's explicit scope boundary.

Final exact command, with the original absolute ROM and Java 25 variables:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME="$JDK25" \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestPostSs7SevenEmeraldStarpostProbe#bonusStarsDoNotDependOnChaosEmeraldCount,TestSonic3kStarPostObjectInstanceTimeAttackGate,TestSonic3kStarPostInvitationArtAdmission,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kPostSs7CausalProbe#throughHczReturn' test -B
```

Result **88T/2F/0E/0S**, queue **616 seconds**, Maven **1m18s**. Fresh XML
identifies the seven-emerald parameter and unchanged strict route as the only
failures. The route method takes **37.754s**; all nine completed level-report
canonical hashes match the first probe, including unchanged segment 11/13/15
hashes recorded above. This gives a matched unchanged-source route control
across the two different read-only observers, not a repaired campaign.

The passing cases comprise **60 S3K anchors**, **17 actual-ROM invitation-art
admission cases**, **7 existing stock/mutator/time-attack/invitation cases**,
and the two below-seven emerald controls. The 17-case selection includes
four-parent capacity/retry, non-duplication, actual physical/timed queues,
pending/submitted snapshots, destructive graph recreation and forward replay;
the seven-case selection includes consumed-entry replay and policy-denial
controls. These bound the existing consumers; they do not certify an unfixed
seven-emerald art/rewind route, all teams/viewports/donors, native pixels,
broad smoke, a full suite, or campaign completion.

The final observer SHA-256 is
`ba3cfce2a82beacec1f91eb269f9d7b7468622f609afb6aec55709133337c530`;
the emerald regression source SHA-256 is
`ec8088d61e2a42cb3a470fcd2578e29a6375e287759e95d57c6e6af019b845ea`.
Production, pinned references, ROM and fixture identities remain unchanged.
Both temporary in-repository Java probes and their consumed XML/JSON/Maven
diagnostics are removed after inspection. The small external `StarpostProbe.java`
retains the old-red reproducer for the lead, beside the native evidence; no raw
Java validation logs are archived. No task-owned process, queued request,
temporary agent or agent-lifecycle registry remains. The local evidence branch
and provisioned worktree are retained for the lead; no push, PR, integration,
release or worktree deletion is performed.


## Starpost invitation correction and bounded continuation — 2026-10-29

Implementation checkout `t3-0b5c8a6a`, local branch
`bugfix/ai-s3k-starpost-invitation-frontier`, fast-forwarded the provisioned
`c4cd6ae0e` to pushed develop `bd0fbcd72`. Evidence-only commit
`419a4676491a61ec4666da03550c13bdf96a5350` was reused as `e21023b18`;
both historical records applied without conflicts. This task owns only
`Sonic3kStarPostObjectInstance`, its related tests and documentation. The lead
owns combined validation and integration. No other checkout, fixture,
disassembly/TraceChaser state or shared implementation is changed.

### General correction and focused contract

Remove only the Chaos Emerald count early return from
`shouldSpawnBonusStars`. `sub_2D028` (sonic3k.asm:61648-61692) has no such
predicate. Keep the existing 20-ring stock threshold, authored invitation
recipe, run/mutator gates, consumed-entry flag, checkpoint save, allocation,
child entry/slot lifetime, art producer and rewind owners. No hardware job is
constructed from completion records. The preceding native evidence establishes
seven emeralds / 180 rings and the real four-child graph at completed input
98467, with the BLUE art identities recorded there.

The focused eligibility test uses ordinary contact with 0/6/7 emeralds at
20 rings, seven emeralds at 180 and 19 rings, denied run policy, and consumed
entry. It also checks checkpoint/orbit preservation, no ring injection, no
entry at contact and no repeated allocation. The existing authored zero-ring
entry/consumption/replay test now runs with both 0 and 7 emeralds. Existing
mutator denial remains a separate control.

The actual-ROM ordinary-contact test uses seven emeralds / 180 rings with
1/3/5 free slots after parent admission. The ordinary orbiting child succeeds;
0/2/4 bonus children fit. This deliberately covers bonus-allocation failure,
not failure of the preceding ordinary orbit allocation. It checks angle order,
BLUE family and exact parent fingerprint, one submission despite repeated
contact, captured slots and destructive registry restore/relink, and matching
60-frame forward graphs/queue state. Existing invitation-art capacity,
retirement, submitted/pending restore and entry-consumption checks remain in
the candidate selection. No allocation or restore implementation is changed.

### Matched baseline and immutable inputs

The original absolute ROM is
`${OPENGGF_MAIN_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen`:
CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`, SHA-256
`fba0677fde9f76df93f3e98d6310d8af68b9847bde16e253d73cd4dd8134ed23`.
The run is `src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds`;
its original BK2 SHA-256 is
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`,
and manifest SHA-256 is
`e2bcf1dfdcf3952ec07f61205e765f90fcb5405ab2ed7d8f054fde742a2f8f46`.
Fixture Git tree remains `b9c0bf21fc5b95868a6383bcf46afa5a7b7a8c96`.
Baseline production `src/main` tree is
`063c98ee577a2ccf4a294872cf4c83de158f3ed9`, exactly the prior evidence's
production tree. No fixture values enter gameplay.

Both runs use the same temporary comparison-only continuous driver, SHA-256
`677218d97bb9c343f273c390b31399ce0483a80d4ae1bf5927473e32a50577b9`.
It invokes `assertChainReplayThroughSegmentRow` from segment 0 through segment
16 row 1, so segment 15 must close and receive its ordinary strict report.
Its callback prints production emerald/child/queue observations before selected
input steps. The temporary label `completed` is misleading: its value is the
upcoming physical movie row + 1, so the observed state completed one fewer
inputs. `stateMovieLogicalRow()` runs before `loop.step()`; this is a
call-site-verified sampling correction, not a fitted timing adjustment. The
earlier record's completed-input attribution must not be copied onto this
pre-step-only print; this print does not overturn native completed98467.
The driver does not alter input or production state. Reaching
this prefix alone would not grade an active MGZ destination segment.

Exact common command prefix (`OPENGGF_MAIN_ROOT` denotes the original main
checkout; the resolved absolute ROM path is in the task handoff):

```bash
JDK25=/usr/lib/jvm/java-25-openjdk
S3K_ROM_PATH="${OPENGGF_MAIN_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen"
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME="$JDK25" \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestSonic3kStarPostObjectInstanceTimeAttackGate,TestSonic3kStarPostInvitationArtAdmission#sevenEmeraldOrdinaryContactKeepsPartialAllocationArtAndRewind,TestS3kStarpostInvitationFrontierProbe#throughHczReturn' test -B
```

Fresh baseline result on `e21023b18` plus only new tests/documentation:
**19T/7F/0E/0S**, queue **430s**, Maven **2m17s**, continuous method
**35.910s**. Fresh XML verifies Java **25.0.5.0.2**, the JDK above and
`surfaceless-egl`. Six focused failures are the two ordinary seven-emerald
positive cases (`expected 4, actual 0`), the authored seven-emerald case
(`expected 4, actual 0`), and the 1/3/5-free-slot cases (respectively no art
job / no two children / no four children). Below-seven and denial controls
pass. The seventh failure is the strict continuous route, on the same four
axes as prior evidence: MGZ admission at cursor **100896**, segment 11 **62**,
segment 13 **182**, segment 15 **27,401** differences. All nine completed
level report JSONs are canonically identical to the reused evidence, including
segment 11/13/15 hashes `b4fd8b19…bb632`, `dadcda48…dde4`,
`499e398d…50a9af`. Segment 15 is complete with **22,209 physics / 5,192
animation** differences, **145 lag rows**, zero bootstrap errors/report
warnings, first strict camera X row **940**, and first non-camera row
**1748 Y `$0777/$0772`**. Its partial art-gap journal has **15 gaps / 0
failures**; closing timing authority rejects the missing parent/direct pair
at raw1258/1259 followed by later jobs. The production observer has seven
emeralds, zero bonus children and zero pending handles at its printed
98465–98470 (states after completed98464–98469).
This establishes old-red eligibility and the matched continuous baseline;
it is not a campaign pass.


### Candidate continuation and stopping boundary

The candidate uses the common command above, replacing only the selector with:

```text
-Dtest=TestSonic3kStarPostObjectInstanceTimeAttackGate,TestSonic3kStarPostInvitationArtAdmission,TestCheckpointStarpostGraphRewind#sonic3kStarpostChildrenRestoreFreshAndRelinkNearestParent+missingS3kStarpostDropsBonusStarCleanly,TestHardwareTimingAuthorityGuard,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kStarpostInvitationFrontierProbe#throughHczReturn
```

Initial candidate result: **123T/4F/0E/0S**, queue **583s**, Maven **2m18s**,
continuous method **36.704s**, verified Java **25.0.5.0.2** / EGL. All **119**
other cases pass: 60 S3K anchors, 15 eligibility/entry cases, 17 existing art
cases, two S3K graph cases and 25 authority guards. Three new actual-ROM cases
stop at a test-author error: expected a bare parent digest while the typed
handle returns `sha256:<digest>`. The digest itself is exact. The assertion is
corrected to the actual typed representation; production source is unchanged,
and only those three cases are rerun. Their baseline failures occur earlier
(no children/art), so the representation correction does not invalidate old-red.
The fourth candidate failure remains the strict continuous route.

The corrected invitation creates four children and real parent/direct work on
native completed input **98467**: the pre-step print labelled98468 observes
that state, while print98467 still observes the preceding completed input.
Both runtime identities are exact, without a tolerance, ordinal override,
recorded-work constructor or engine-state hydration:

| Runtime work | Fingerprint SHA-256 |
| --- | --- |
| `KOS_MODULE_QUEUE#189`, source `$187B8A`, destination `$BD80` | `0fbbcb25822bda53fc0b212780f2218200ef117c753fa623fa1a05c66379f152` |
| `KOS_DECOMPRESSION_QUEUE#269`, source `$187B8C`, destination `$FFFFD000` | `107442b529cf8edafb0750d0198606ee2c4a667f4f99bb3c68065188177cf1e6` |

The direct handle is gone by the next print and the parent by print98470.
Closing authority no longer rejects this pair: unmatched recorded completions
decrease **27 → 25**. The entire remaining ordered tuple list (raw row,
boundary, kind, ordinal, fingerprint) is identical to baseline after removing
only that pair. The next missing direct job is **raw2680 PRE_MAIN_LOOP**,
`KOS_DECOMPRESSION_QUEUE#270`, fingerprint
`589a478d29f5c788ad304520acc86172ea220a4a68b5a74ac25ee62e80d5899c`;
its parent is **raw2681 POST_OBJECTS**, `KOS_MODULE_QUEUE#190`, fingerprint
`b6debc7640297e307a6987331dd3176f6bf9566d601a8c0d7febad45b7bf458d`.
Both have engine pending `<none>`. These occur after the earlier gameplay
frontier; they are not independently attributed as a producer defect.

Every completed level report and the partial art-gap journal is canonically
identical to the fresh baseline, not merely equal in totals. Segment 15 still
consumes all **3,687** rows and has **27,401** differences, zero bootstrap
errors/report warnings, first strict camera X row940 `$3941/$3940`, first
non-camera **row1748 Y `$0777/$0772`**; segment 11/13 retain **62/182**.
The journal remains **15 gaps / 0 failures**. MGZ still denies admission at
cursor **100896**, before its first comparison row. This is a strict-red route,
not a campaign or MGZ parity pass, and no permanent frontier pin is advanced.

The next bounded gameplay investigation is the boss-hit/contact boundary at
completed **98956–98957** / segment15 rows **1747–1748**, separate from the
now-restored starpost work. The reused native witness observes the ordinary
`$010134` velocity-negation branch at98956, P1 Y `$0774`, Y speed `$0300`,
boss HP **6 → 5**; native next ends at Y `$0777`, Y speed `$0338`, HP5.
The prior engine observer records the subsequent hurt-like `$FC00` vertical /
`$0200` horizontal response and Y `$0772`. The new candidate independently
retains the exact first-Y mismatch and full bounded report; it does not
remeasure those unprinted engine velocity words or prove their owning branch.
Boss phase/contact eligibility versus shared collision remains unassigned.
The known engine/native starpost parent SST8/SST28 difference is not silently
normalized and does not by itself establish a cause for that later mismatch.
No boss, collision, loading, scheduler, timing port, GameLoop or audio fix is
attempted outside this task's starpost ownership.


### Final focused qualification and handoff

The corrected three-case rerun uses the same original absolute ROM/JDK/EGL
prefix, without the trace profile/fork/run-order flags, selecting only:

```text
-Dtest=TestSonic3kStarPostInvitationArtAdmission#sevenEmeraldOrdinaryContactKeepsPartialAllocationArtAndRewind
```

Result **3T/0F/0E/0S**, queue **339s**, Maven **34.593s**, class **1.703s**
(cases **0.980/0.226/0.180s**). Fresh XML verifies the same Java **25.0.5.0.2**
and surfaceless EGL. All three cases now reach and pass destructive restore,
parent relink, exact captured child slots, one art submission and both
60-frame forward graph/queue comparisons. No production edit accompanied the
assertion repair. Across the initial candidate and this narrow rerun, **122
unique focused cases pass**; this is not a single 122-case green Maven run.
The original strict route remains a separately completed **1T/1F/0E/0S**
measurement, with the unchanged four-axis report above. No broad smoke or
release-only full suite is run; the lead retains combined validation ownership.

Tested production is `e21023b18` plus only the starpost predicate removal;
`Sonic3kStarPostObjectInstance.java` SHA-256 is
`726e9076cfbb32da45b97283f87ebf158942a984811924f89f13bc9eff9c48cc`.
The final eligibility test SHA-256 is
`f69b51261d4c58ccf20424c7385d0cc3e187bf80592352dbdd6f244ec8046cf1`;
the final actual-ROM art/rewind test SHA-256 is
`1b34459a664358b73498e75c01b76af7fe15b1cd06120861664fac7a57c43584`.
Queue waits (**430/583/339s**) are separate from Maven execution
(**2m17s / 2m18s / 34.593s**); other task time and exact total delivery wall
are unmeasured. No queue time is called failed execution, and no repeated
unchanged anchor or continuous run follows the test-only assertion repair.

Scope limits remain explicit: the partial-allocation cases start after the
ordinary orbit allocation can succeed; they do not certify its exhaustion
branch. Component tests use native320 solo Sonic, while the original route
uses native320 Sonic+Tails. The broader viewport/donor/character/team matrix,
actual HCZ checkpoint death/reload and later rewind boundaries, native pixels,
MGZ entry, full act and campaign remain uncertified. No intentional discrepancy
is introduced. The next boss/contact cause remains outside this starpost task.

The temporary in-repository route probe and consumed task-owned Maven/XML/
JSON diagnostics are removed after inspection. The external original native
witnesses and `StarpostProbe.java` remain intact. The local bugfix branch and
provisioned checkout are retained for lead integration; no push, PR, release,
worktree creation/removal or foreign process/checkout change is performed.
No task-owned live Maven request remains at handoff. No temporary agent was
created, so there is no agent-lifecycle cleanup. Lead integration/combined
validation and later branch/worktree removal are intentionally pending.


Final static checks: new relative Markdown links/anchors resolve (five added
links); root instructions and skill mirrors match; `git diff --check` passes.
The scoped citation check exits0 with **near12/ok122/stale51/unverifiable340**;
the same51 inherited stale references existed before the source edit, and no
new starpost citation is stale. Submodule pins and original ROM/fixture
identities are unchanged. Prepared commit message parsing preserves the final
`OpenAI Codex GPT-6.1 Sol <noreply@openai.com>` co-author after all seven policy
trailers.

The first commit attempt was blocked by the resource-policy hook because the
new prose included the machine-local home directory. These documentation paths
now use `OPENGGF_MAIN_ROOT`; the actual tests used the unchanged absolute original
ROM path supplied in the handoff. No hook is bypassed and no Java change follows
completed validation.

### Independent review: inherited invitation gaps

The seven-emerald predicate removal matches the native eligibility check. A
source review also identified two older, separate limits not covered by the
candidate's allocation and entry tests. If the ordinary orbit child cannot be
allocated, native `sub_2D028` (sonic3k.asm:61665) proceeds to checkpoint saving
without submitting bonus art; the engine currently continues to bonus work.
After a valid ordinary invitation, native `loc_2D47E` (sonic3k.asm:61931) uses
saved activation rings for the bonus selection without repeating the 20-ring
eligibility test; the engine currently rechecks current rings on entry. Neither
gap was introduced by removing the emerald predicate. They remain unqualified
and require their own regression evidence and fixes.
