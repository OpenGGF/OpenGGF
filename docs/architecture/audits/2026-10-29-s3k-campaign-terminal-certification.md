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
