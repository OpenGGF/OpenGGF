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
