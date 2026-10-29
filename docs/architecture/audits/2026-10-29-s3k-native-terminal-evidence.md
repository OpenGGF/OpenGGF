# S3K native terminal evidence — 2026-10-29

Implementation lane: T3 checkout `t3-e2ed2f88`, branch
`s3k-terminal-evidence`, parent base
`c4cd6ae0e8cdaf00bdf013c3c740586039c10ef8`.
The certification audit at
`docs/architecture/audits/2026-10-29-s3k-campaign-terminal-certification.md`
was read from Git commit `d562cd3569347e69a3556836ef00a478476265fe`;
that consumer change is not part of this checkout's base.

## Producer change and ownership

Only this checkout's isolated `tools/tracechaser` submodule was changed.
It started clean at `703bd92d32634ce5ebac60625b48c60ef5500427` and now has
local branch `bugfix/ai-s3k-native-terminal-sampling`, commit
`2f55ea3f0115c4189b99a3bb286af24b868ede36`. Its origin remains
`https://github.com/OpenGGF/TraceChaser.git`. This commit is **local only**;
the parent gitlink is deliberately not committed. No main-workspace source,
other submodule, canonical fixture, API, release, remote branch or PR was changed.
No agents were spawned and no foreign dirty source was copied.

The S3K driver now samples raw `Game_mode` after the last original BK2 row
has advanced the native core, before segment finalization. It passes the
supported mode to the existing shared manifest writer. The exact input count
and completed-frame count must both equal the movie's real row count.
Any effective-length override, including an equal-length one, omits the field;
an extended bound exhausts the real stream and fails rather than synthesizing
input. Unknown modes remain unspecified. No segment boundary, physics, aux,
hardware-timing rule, RAM write, callback or engine owner was added or changed.

ROM basis: `GameLoop` (`sonic3k.asm:433-438`) reads `Game_mode` and masks
with `$7C` before dispatch; `GameModes` (`sonic3k.asm:440-467`) assigns
`$04` to Title Screen, `$0C` to Level, and `$4C` to Save Screen. The
PreLevel form `$8C` is explicitly checked by `VInt_0_Main`
(`sonic3k.asm:595-601`). Thus supported endpoint observations are
`title_screen` for `$04` and `level` for `$0C` / `$8C`. The segmenter's
low-nibble family would incorrectly include Save Screen and is not used.
The verified locked-on ROM contains the dispatcher bytes
`1038f6000240007c207b00064e9060f0` at `$0004B6` (and the separate
Sonic-3-half dispatcher at `$200734`); the pin for the source reference is
`docs/skdisasm` commit `8b2cf3c4178f2382f844da67798de4befc50c5e0`.

An observed `level` endpoint is not proof of campaign completion. No campaign
is certified by this producer change or by the endpoint-only corroboration.
Independent producer review remains required before authoritative fixture use.

## Original inputs and runtime

The supplied `docs/BizHawk-2.11-linux-x64.tar.gz` backup is 134194258 bytes,
SHA-256 `9a018fd85b3824f121d962de82a954bee045acf4adddecd903e72174f4effe8c`.
It contains saved movies and does not match the official runtime archive lock.
It was preserved and not extracted as an authenticated runtime.

The locked official 2.11 Linux archive was downloaded to task-owned external
storage, verified as SHA-256
`cdaf9650d880bae660d63a388430f630b8d8a96b1ba59ebf0e0195a645c3bab8`,
then extracted outside both repositories and selected through `BIZHAWK_HOME`.
The pinned preflight passed: managed version `2.11.0.0` and all 30 Lua API
capabilities. No display was used. Runtime identities:

| Member | Bytes | SHA-256 |
| --- | ---: | --- |
| `dll/gpgx.wbx.zst` | 400161 | `c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12` |
| `dll/libwaterboxhost.so` | 688512 | `d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78` |
| `dll/BizHawk.Emulation.Cores.dll` | 8774144 | `0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7` |
| `EmuHawk.exe` | 5320192 | `b2d4be5e2a766a5161cc26f3af2a90753c39d64c91c54a9884171aed09e21df3` |

The original absolute locked-on ROM was read from the main repository root,
without copying or linking it. Identity: 4194304 bytes, CRC32 `63522553`,
SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`, SHA-256
`fba0677fde9f76df93f3e98d6310d8af68b9847bde16e253d73cd4dd8134ed23`.
The S1/S2 REV01 ROMs supplied to native tests matched the root guidance's
CRC32/SHA-1 identities as well.

Original movie filenames were preserved; rows count actual `|` input rows:

| Source under `src/test/resources/traces/s3k/` | Rows | SHA-256 |
| --- | ---: | --- |
| `_movies/s3k-complete-sonic-tails.bk2` | 466334 | `82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf` |
| `runs/s3k-knuckles-complete-superemeralds/s3k-knuckles-complete-superemeralds.bk2` | 434417 | `aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc` |
| `runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2` | 539492 | `ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0` |
| `runs/s3k-tails-full-chain-all-emeralds/s3k-full-chain-tails-all-emeralds.bk2` | 499820 | `cb62a83a41de3273f12e5c1f9dbe7f60fe9eabb2c8505b76470e295315eee1f8` |

All headers name BizHawk 2.11 / Genplus-gx and the locked-on game. Their legacy
header SHA1 token was not substituted for the ROM identity. The normal source
movie was found directly, not reconstructed: Git history records it at
`31386b4c42212505f8dd9ab891f386b563c0dc33`; the existing
[trace scope](../../status/trace-scope-release-6.md) identifies its Sonic+Tails
team and no-emerald-collection route. Fifteen committed metadata files name
that exact source movie. Its name or route description supplies no endpoint.

## Native tests

Seven new native harness behavioral cases were registered explicitly and
included in the test project. Their synthetic host scripts are independent
of captured observations. They distinguish the last exported row, final input
advance and cleanup (which deliberately changes the RAM byte), and reject
the wrong frame, Save Screen, unsupported modes, shortened/equal overrides
and extended input exhaustion. The positive cases first failed against the
unchanged producer, then all seven passed with the implementation, zero skips:

```bash
BIZHAWK_HOME="$RUNTIME" bizhawk-headless/test.sh --filter S3KMovieEnd --jobs 1
```

The final producer executable is 837632 bytes, SHA-256
`0f954aeb5fc05e06bb5465b7453aeabac39205b610503fa19b666c98509f9a42`.
The test executable is 1314816 bytes, SHA-256
`56a6aaa034242f2d63ea50f6b9b431897709df43c6270baafb3d808980364f84`.
Exact source and tool identities are retained in external `producer-identity.json`.

A single full native-harness run (not the Java release suite) was bounded to
600 seconds and used two workers, the verified stock runtime, absolute S1/S2/S3K
ROM paths and `TRACECHASER_TEST_FIXTURE_ROOT="$FIXTURES"`:

```bash
timeout --signal=TERM --kill-after=15s 600s bizhawk-headless/test.sh --jobs 2
```

It completed in 35.0 seconds: **819 passed, 60 failed, 15 skipped**, exit 1.
All seven endpoint cases, 18 `S3KCompleteRun` cases and 13 `GpgxHost` cases
passed. The result is red, not full qualification. Only the bounded subset
below was matched on a clean baseline; the 60 failures must not be described
collectively as proven inherited failures. They comprise:

- 4 S1/S2 dynamic-art cases using stale `tools/src/test/resources` paths;
- 37 S1 complete-audio reference cases rejecting emitted Z80 events while
  native proofs were disarmed;
- 3 audio binding/profile cases, including the rebuilt producer executable
  differing from the audio capability's frozen executable identity;
- 9 missing or incomplete observer source-lock/build-recipe cases;
- 3 credits cases missing an explicit external fixture root;
- 1 `EndToEnd` launcher case missing required explicit roots;
- 2 S2 special-stage/aux assertions;
- 1 child-death publication case expecting exit 9 but observing 6, with a Mono
  crash artifact retained in the isolated submodule.

The 15 skips were eight KiS2 cases (ROM not supplied), three opt-in reset-prefix
live cases, two opt-in long S1 audio-prefix cases and two observer reproduction
gates requiring additional source/toolchain inputs. No endpoint case skipped.
These failures/skips remain coverage limits, not a reason to relax a gate or
copy unrelated source. The audio capability binding is not a gameplay trace
capture prerequisite and was not repinned from this invocation.

A bounded baseline snapshot of exact Git pin `703bd92` was built inside this
isolated submodule's ignored `.scratch/` storage, without changing any active
producer source or build output. Exact-name, one-worker checks reproduced:

- `S2 standalone special-stage runner includes first mode frame and stops on exit`:
  `Expected <2> but was <1>`;
- `S2RunCaptureRunner emits the special-stage aux event stream`:
  missing `"input_sample_frame":1`;
- `S1CompleteRunAudioReferenceCaptureTests keep kind six queue observations bounded`:
  the same row-860 disarmed-native-proofs error;
- `GpgxZ80AudioCapabilityTests lock reviewed S2 and S3K service manifests`:
  the same frozen capability executable-binding assertion. The baseline binary
  is SHA-256 `dad8d30f427978166fcc048dafc8a8d41e6dfce9615a664f33b159cd2f6c44a3`,
  the candidate is `0f954aeb5fc05e06bb5465b7453aeabac39205b610503fa19b666c98509f9a42`,
  and both differ from the frozen pin
  `0ba71e5c4f3dee9360b13c0c312a1dc21a61a5b4760ad3d15e02a9472179c8d3`.

Each selector ran one test, failed once, and skipped none. The first binding
attempt used an archive layout incompatible with that test's hardcoded
consumer-relative helper; its missing-file failure was discarded and the
proper `tools/tracechaser` layout rerun reached the binding assertion above.
These four failures have bounded baseline attribution; the rest do not.

The 42 repository/history/documentation policy tests passed, zero skips.
Index policy, all-history policy, documentation policy and whitespace checks
passed before the producer commit. No engine Java code changed in this lane.

## Completed capture evidence and limits

Durable work is outside both repositories under task directory
`s3k-terminal-evidence-20261029`; exact absolute locations are in the worker
handoff. Capture inputs, ROMs, source branches and outputs are retained.
The normal full trace had a ten-minute bound; the Knuckles full trace had a
twenty-minute bound. A separate read-only endpoint witness bypasses the trace
runner, segmenter, callback and manifest mapper. It reads raw RAM after each
original input advance, applies no post-movie input and writes create-new JSON.
Its four movie passes were each bounded to 300 seconds. The initial
witness launcher failed before playback because `BIZHAWK_HOME` was missing;
the corrected launcher supplies the same verified runtime. That failure
produced no endpoint document. The witness source is SHA-256
`6501283971cb762cd6944c23a0b8498dbd649b7a3544498337c0a9d86e66d989`;
its compiled executable is
`4c83203b07d717d2ad2cc4aab358d7345fee2d15a233c854101c613f47c4331c`.
It shares the verified native host/BK2 parser, but no production endpoint
classifier, trace runner or callback. It corroborates the frame boundary and
raw mode independently of the manifest output, not the complete recorder.

| Original source | Endpoint-only native result | Extent / execution |
| --- | --- | --- |
| Normal Sonic+Tails | raw `$0C` | all 466334 inputs, 124.731 s |
| Knuckles super emeralds | raw `$04` | all 434417 inputs, 115.591 s |
| Sonic+Tails emeralds | no endpoint document | timeout 300 s, exit 124 |
| Tails all emeralds | no endpoint document | timeout 300 s, exit 124 |

Both successful passes used zero post-movie advances. Normal Sonic+Tails last
entered `$0C` at completed frame 465614 and remains there at input exhaustion;
it does **not** supply an observed title-screen endpoint. Knuckles entered
`$04` at completed frame 433943 and remains there through input 434417.
These are native observations, not conclusions drawn from names or zones.

The full normal capture timed out at 600 seconds, exit 124. It left staging
directories for ten segments (`aiz`, `hcz`, `mgz`, `cnz`, `icz`, `lbz`, `mhz`,
`fbz`, `soz`, `lrz`) and **no published manifest**. Its final applied-input
extent is unknown; no complete candidate is claimed or reconstructed from it.

The full Knuckles native producer capture completed, exit 0, within its
1200-second bound. Exact elapsed execution was not separately measured.
It preserves the original run id and source movie filename and includes:

- 67 segments: 43 level, 14 special stage, 10 bonus stage;
- 48 transitions and 409630 physics rows; final segment end 433942,
  followed by the original 475-input tail;
- 6786533 auxiliary events across 21 event kinds, with 53 pre-trace CPU
  snapshots; exact per-segment counts/ranges are in `knuckles.audit.json`;
- 1537 per-segment timing events, plus 481 run-level interstitial events
  (310 direct / `pre_main_loop`, 171 module / `post_objects`), movie-frame
  range 682–337989;
- 270 stored files, 97838782 bytes, v5 / native recorder 3.0 / BizHawk 2.11;
- recorder-written `expected_movie_end_mode: "title_screen"`, agreeing with
  the separate endpoint-only native pass.

Strict v5 validation, including `--require-frame-keyed-auxiliary`, passed.
The frozen inventory was generated and independently reverified read-only:

```bash
python3 traces/validate_trace_v5.py --require-frame-keyed-auxiliary "$CANDIDATE"
python3 traces/trace_fixture_inventory.py generate "$CANDIDATE" --output "$INVENTORY"
python3 traces/trace_fixture_inventory.py verify "$CANDIDATE" "$INVENTORY"
```

| External evidence member | Bytes | SHA-256 |
| --- | ---: | --- |
| `knuckles/run_manifest.json` | 20849 | `039ef95bdb9bd8b0a0723800b7ed8c2a4d18cfec9b003a96844729dd359b6d1a` |
| `knuckles.inventory.json` | 52822 | `c5df2e472c4849e8f2aff4853766714c2e6bbb3448e3b43ad998c06f7fe88ea5` |
| `knuckles.endpoint.json` | 9946 | `c0cdf5c76ffc9c1133122155a4ef0bc040fad658c3ef7e3cffd82eb4e9fd50bf` |
| `sonic-normal.endpoint.json` | 2802 | `443f99c32f89c052db822f1b7a25a63ea9bf004d2876f11eb27fc89f5662c609` |

### Complete comparison with the committed Knuckles run

The read-only comparison covered every payload, not sampled frames. All 67
physics streams and all 67 per-segment timing streams match the committed
logical bytes exactly. Sixteen physics files also match stored bytes; the
other 51 differ only in gzip containers from native compression, with identical
decompressed content. Every per-segment timing file matches stored bytes.

The candidate is **not an equivalent fixture replacement**. I omitted the
reference run's `--load-queue-state` option from this capture. All 67 auxiliary
streams differ. A second whole-stream comparison proved that removing only
the reference's 819260 `load_queue_state` event lines leaves byte-identical
candidate auxiliary streams in every segment, with no unexplained residual.
The 14 special-stage auxiliary streams are therefore empty in the candidate.
All 67 metadata changes are exactly the omitted `load_queue_state_per_frame`
capability and recording date (`2026-08-04` to `2026-10-29`). No capture files
were changed during this classification; the omission is retained as a scope
limit, not repaired by copying old observations into the candidate.

The only manifest difference is the 45-byte terminal-mode line. Removing it
for an in-memory comparison reproduces the committed manifest exactly.
The candidate also contains `hardware_timing_interstitial.jsonl` (142701
bytes, SHA-256
`c9fe30248dab96c7e06d96fdba2f213cd63ed2e7eb57102a3f410ed080ac2dc2`),
absent from the committed run. The already-pinned driver's
`ObserveUnexportedHardwareBoundary` /
`OpenInterstitialHardwareTimingStream` path exports those run-gap completions;
the terminal patch did not introduce that path. This additional export needs
independent publication review. The input BK2 is an explicitly preserved
external curation input, not a file emitted by native capture.

The complete per-file hashes, byte counts, metadata field deltas and event
inventories are preserved in `knuckles.audit.json`; the exact filtering
classification is in `knuckles.aux-deltas.json`. These classify comparison
deltas; they do not replace independent recorder review or certify a campaign.

## Handoff and retained work

Verified deliverable: the local producer patch and a complete original-input
native Knuckles endpoint, corroborated independently; additionally, an original
normal-route raw endpoint. No fixture observations or terminal values were
hand-authored into a manifest or installed in the consumer repository.

The source patch is retained externally as `producer-2f55ea3.patch`, 16874
bytes, SHA-256
`94d604d208d2362dee0e35e688d0d8b518065c3646a0dd806580a2fc8c4aea01`.
It includes the policy and Sol co-author trailers. The local producer commit
must be independently reviewed and made available through the producer's
authorized delivery before a parent gitlink can safely reference it.

Further equivalent Knuckles fixture acquisition must preserve the original
load-queue capability with `--load-queue-state` and review the extra run-level
timing export. The two other campaign endpoints require a larger measured
capture bound or another independently authenticated original-input capture;
no values are inferred from their timed-out passes. Original normal-route
fixture acquisition likewise needs an adequate bound, and its observed Level
endpoint does not establish completion. Any eventual canonical installation
also needs the consumer's candidate-load/schema/compression/reference and
applicable replay/frontier checks. No Java fixture installation or campaign
replay was attempted in this lane, and no complete campaign is certified.

No owned jobs remain. Native capture, witness, baseline and validation command
sessions all completed; no Maven queue job or persistent agent was created.
Native test execution was 35.0 s; successful witness executions were measured
above. Queue wait does not apply to these direct native commands, and total
delivery wall time is unmeasured. Overlapping intervals must not be summed
as elapsed time.

No cleanup was performed. Retain this T3 worktree, the local producer branch,
external captures/runtime/source patch/reports, normal incomplete staging,
and both owned baseline source/build snapshots under native `.scratch/`.
The isolated submodule also retains generated
`mono_crash.1095ce9d0b.0.json`. The parent gitlink remains modified but unstaged;
these are explicit retention/cleanup obligations for the coordinator. Unknown
and user-authored source was preserved throughout.

## Full-capability follow-up — isolated implementation lane

The follow-up uses T3 checkout `t3-44295a8c`, parent base
`c4cd6ae0e8cdaf00bdf013c3c740586039c10ef8`, and the pinned producer base
`703bd92d32634ce5ebac60625b48c60ef5500427`. It fetched exact local producer
commit `2f55ea3f0115c4189b99a3bb286af24b868ede36` from the preserved prior
isolated submodule into its own isolated submodule. The new local test commit is
`3842d2cda1ee518914927550b2d07f057a0f51c2`. Neither producer commit was
pushed; the parent gitlink must remain at the available pinned base. This lane
leaves canonical fixtures, main-workspace source, prior artifacts, release/API
scope and unrelated changes untouched. No delegates were created.

Four new registered endpoint fault cases exercise the real staged segment sink,
no-replace session, and the caller's manifest/publication ordering. They inject
endpoint-read, input-advance, callback-disposal and sink-finalization exceptions.
Each requires propagation, exactly one callback disposal attempt, an absent
callback registration, no returned capture result, no manifest, and no final
files after transaction cleanup. The disposal fault models an exception after
the owning registration has removed its callback; it does not simulate arbitrary
native unregister corruption. The finalization fault occurs after segment streams
complete, proving that staged files are revoked as well. All eleven endpoint
cases passed with zero skips. No directly demonstrated producer fix was needed.
The production executable remains SHA-256
`0f954aeb5fc05e06bb5465b7453aeabac39205b610503fa19b666c98509f9a42`,
byte-identical to the prior producer. The new test executable is SHA-256
`b36b34c6c34a8d292b219fc2146635f64fc6844859d64dcd7845bf4049605dab`.

The complete native test invocation used the separately extracted, verified
stock 2.11 runtime, absolute original S1/S2/S3K ROMs, the explicit external
consumer fixture root, two workers and a 600-second bound. It completed exit 1:
**824 passed, 59 failed, 15 skipped**, 32.4 seconds reported test execution,
33.217 seconds including build/preflight; shared queue wait does not apply.
All 11 endpoint, 18 `S3KCompleteRun` and 13 `GpgxHost` cases passed.
There are no newly failing identities versus the prior candidate invocation;
the child-death publication case that previously failed now passed. This is
not a green native qualification and does not establish baseline inheritance:
only the four failures named above have matched-baseline evidence. The remaining
55 failures remain unattributed. Exact failure/assertion and skip inventories
are retained in external `native-suite.summary.json`; skip categories remain the
same as the preceding audit. The 42 repository/history/documentation policy
unit tests and all three policy scanners passed. No Java engine source changed.

Durable follow-up storage is the explicit external task directory
`s3k-terminal-fixtures-20261029`. Its `input-identity.json` and
`producer-identity.json` identify original movie/ROM paths and digests, verified
runtime members, source files, both executables and the independent witness.
Its `producer-local.patch` contains both local commits with policy and verified
Sol co-author trailers. The runtime was extracted afresh from the official
archive with SHA-256
`cdaf9650d880bae660d63a388430f630b8d8a96b1ba59ebf0e0195a645c3bab8`;
the exact 2.11 managed version and all 30 Lua capabilities passed preflight.
Original input identities and row counts are unchanged from the table above.

### Independent remaining-endpoint witnesses

The read-only witness source remains SHA-256
`6501283971cb762cd6944c23a0b8498dbd649b7a3544498337c0a9d86e66d989`.
This lane rebuilt it against its own producer/runtime; its executable is
SHA-256 `ae044dbd0c45352c32145a81151eae1b413d3aa1f63dec96a6457bfe66fa9363`.
It reads raw RAM after each original input advance without the trace driver,
segmenter, callback, mapper or manifest. The larger bounds are 900 seconds per
movie, each in its own native process. The first build attempt lacked the
`netstandard` facade and failed compilation; an accidentally started launcher
then exited 2 before playback because no executable existed. Adding the facade
fixed that prerequisite. Neither failed attempt emitted an endpoint document.
The original source was not changed and no observations were copied into a
capture or hand-authored into a manifest.

The Tails all-emerald witness completed exit 0 with all 499820 original rows
applied and zero post-movie advances: raw mode `$04` before and after the last
advance, with the last transition to `$04` at completed frame 499679.
Measured witness execution was 410.960 seconds; process wall was 411.317 seconds.
This is endpoint corroboration only, not a full trace or campaign certification.

The Sonic+Tails emerald witness also completed exit 0: all 539492 original rows,
zero post-movie advances, raw `$04` before and after the last input, final
transition to `$04` at completed frame 539167. Witness execution was 447.251
seconds; process wall was 447.618 seconds. The two witnesses overlapped; their
execution times must not be summed as elapsed delivery time. Endpoint documents:

| Follow-up witness | Bytes | SHA-256 |
| --- | ---: | --- |
| `tails-emeralds.endpoint.json` | 10347 | `76a82b2db8cbd8746510afd9d4213319897c35e04550d652a1d9cb5cf89cf9bf` |
| `sonic-emeralds.endpoint.json` | 9403 | `34713f59311c449cd7c1f903c613e9c23f674f995459a7219baadbe692fd3b94` |

These close the prior endpoint-only timeouts with fresh native observations.
They do not authenticate a full recorder manifest for either movie: full native
capture remains necessary before either run can receive terminal fixture data.

### Knuckles full-capability recapture and complete comparison

The fresh `knuckles-load-queue` capture used the original verified ROM/movie,
original run id and `--load-queue-state`, no effective-length override, no
post-movie input, and a new external output path. The exact command is retained
in `knuckles.execution.json`. Its 1800-second bound completed exit 0 in
638.611 seconds including build and preflight; no shared queue wait applies.
Capture-only execution excluding that wrapper overhead was not separately
measured. The native manifest again emits `expected_movie_end_mode:
"title_screen"` at original input exhaustion. No old observations were spliced
into this output. The independent earlier Knuckles witness remains separate
corroboration, not part of the new capture.

The new run has 67 segments (43 level, 14 special stage, 10 bonus), 48
transitions, 409630 physics rows, 7605793 auxiliary events across 22 event kinds,
and 53 pre-trace CPU snapshots. Its last segment ends at 433942, retaining the
original 475-input tail through movie length 434417. The raw capture contains
270 files / 101111943 bytes. Every segment retains exactly the committed
`aux_schema_extras`, including `load_queue_state_per_frame`. All 819260
`load_queue_state` records are present: direct and module observations for
every recorded row, including the special stages. S3K's existing metadata does
not advertise a dynamic-art-transfer capability; none was silently added or
removed.

Strict v5 validation with `--require-frame-keyed-auxiliary` passed on the raw
capture. Its complete fixture inventory was generated and independently
verified read-only. `knuckles.complete-audit.json` covers every candidate and
reference file, stored and logical sizes/hashes, metadata fields, capabilities,
row counts, auxiliary inventories and all deltas. Whole-file results:

- All 67 physics streams match canonical logical bytes; 16 also match stored
  bytes, and 51 differ solely by the plain/gzip container.
- All 67 auxiliary streams match canonical logical bytes; 66 also match stored
  bytes. The remaining small plain stream differs solely by compression.
- All 67 per-segment timing streams match canonical stored bytes exactly.
- All 67 metadata documents differ literally only by recording date
  (`2026-08-04` to `2026-10-29`). Every capability and source filename is retained.
- Removing only the recorder-emitted terminal-mode line in memory reproduces
  the canonical manifest bytes exactly. The capture file was never edited.
- The native output adds the unchanged existing 481-event run-level timing
  stream. Native capture does not emit the curation BK2; packaging includes the
  verified original file, without modifying it.

There are no unexplained payload, metadata, manifest or file-inventory deltas.
This supersedes the prior candidate's capability omission; it does not install
or certify this candidate.

### Review of the 481-event interstitial stream

The new stream is byte-identical to the prior candidate: 142701 bytes, SHA-256
`c9fe30248dab96c7e06d96fdba2f213cd63ed2e7eb57102a3f410ed080ac2dc2`.
All 481 events were checked, not sampled: 310 direct / `pre_main_loop` and 171
module / `post_objects`, movie frames 682–337989. They form 30 boundary/kind
spans. Seventeen pre-run events (11 direct, 6 module) have boundary index -1;
the Java loader validates and discards those because the initial schedule base
already accounts for them. The other 464 events form 28 spans across 14
post-special-stage handoffs. Every span is contiguous by kind, its first
ordinal follows the preceding represented stream, and its last meets the next
segment's first ordinal exactly. Names/indices and movie-frame provenance fit
the actual manifest gaps. No interstitial identity duplicates any represented
edge or another interstitial edge. Exact checks are retained in
`knuckles.interstitial.review.json` and its read-only review script.

Consumer owners were reviewed at the parent base: the strict
`HardwareTimingInterstitialStreamLoader` parses only the documented field set,
rejects duplicate identities and within-span holes, then discards provenance
fields. `TraceRunReplayWalker.HardwareTimingCoordinator.handoffToSegment` offers
the previous boundary's spans to `HardwareTimingReplayPort.handoffTo`.
The port requires the source edges to be consumed, checks next-schedule identity
and adjacency, and invokes
`HardwareTimingService.advanceOrdinalCursorAcrossRecordedSpan`. That owner
rejects pending production work; it either advances an untouched cursor or
verifies that the whole span has already been claimed with exact fingerprints.
It creates, prepares, releases or claims no work. The sidecar therefore changes
run-chain ordinal bookkeeping and is not a cosmetic addition. It must stay with
the complete candidate and requires candidate replay before publication.

An initially over-strong audit assumption required globally dense completion
ordinals. It failed and was corrected to the documented completion-only
contract, not used to synthesize events: the combined streams retain six
existing two-ordinal module gaps (457–458, 498–499, 525–526, 551–552, 599–600,
649–650); direct completion ordinals have no gaps. These are already present in
the unchanged canonical per-segment timings. The producer's mode-reset ledger
fencing may consume ordinals without publishing completion edges, as the
[hardware-timing contract](../designs/2026-07-27-cross-game-hardware-timing-trace-contract.md)
permits. This review does not newly prove each reset's native cause or certify
its production replay. The gaps must not be repaired by cursor seeding or
invented completions.

### Deterministic payload packaging

`knuckles-compressed-candidate` is a separate derivative of the complete fresh
native output, with the unchanged original BK2 added as an explicit curation
input. No event, endpoint, metadata or timestamp field was edited. All remaining
51 plain physics files and one plain auxiliary file were compressed with
`gzip -9 -n -c`; each round-tripped to its original size/SHA-256 and matched a
second gzip emission's stored hash before the derived plain file was removed.
The 15 other formerly plain auxiliary streams now exceed the native compression
threshold because the fresh run correctly retains queue observations; they were
already emitted compressed. Both raw and packaged candidates are preserved.
The packaged tree contains no plain `physics.csv` or `aux_state.jsonl`.
`knuckles.compression.json` records all 52 transformations and original movie
identity. Packaging changes no logical observation bytes.

Strict v5 (including frame-keyed aux), frozen inventory generation/verification,
and complete raw-to-packaged logical-identity comparison passed on the packaged
candidate. The only added logical member is the exact original BK2. It contains
271 files / 83254278 stored bytes. Frozen follow-up members:

| Member | Bytes | SHA-256 |
| --- | ---: | --- |
| Native `run_manifest.json` | 20849 | `039ef95bdb9bd8b0a0723800b7ed8c2a4d18cfec9b003a96844729dd359b6d1a` |
| `knuckles.raw.inventory.json` | 54247 | `f986c09e7af1e8bed8fe3ec036a201b6285f82415f63dac97bdb807cf176a6b4` |
| `knuckles.compressed.inventory.json` | 59370 | `becfa4c94c3dac87e69108cf4c69abf2c004a1cbdac545602db68665b267116d` |
| `knuckles.complete-audit.json` | 341921 | `2737a7abe45f66d135f84e5f6657c0ffff7238dad73d87e685e5206635197600` |
| `knuckles.interstitial.review.json` | 10421 | `f8fb1ef7c8b5ce36becd33667546b8c07ebe37b0cac6dfdb834e68226767743f` |
| `knuckles.compression.json` | 23636 | `0eeea2fe2bee8f71a100f7a40dd0f28d70c7d7f6abeb0dfdee39e4b8875a5ea0` |
| `producer-local.patch` | 26557 | `66068450ddb4e534ecbe4b841953d89b0e8014bdac44d4021983af2340ca21fa` |

The exact ordinary checks were:

```bash
BIZHAWK_HOME="$RUNTIME" bizhawk-headless/test.sh --filter S3KMovieEnd --jobs 1
BIZHAWK_HOME="$RUNTIME" S1_ROM_PATH="$S1_ROM" S2_ROM_PATH="$S2_ROM" \
  S3K_ROM_PATH="$S3K_ROM" TRACECHASER_TEST_FIXTURE_ROOT="$FIXTURES" \
  timeout --signal=TERM --kill-after=15s 600s bizhawk-headless/test.sh --jobs 2
python3 -m unittest discover -s testing -p 'test_*policy.py'
python3 testing/repository_policy.py
python3 testing/history_audit.py
python3 testing/documentation_policy.py
python3 traces/validate_trace_v5.py --require-frame-keyed-auxiliary "$CANDIDATE"
python3 traces/trace_fixture_inventory.py generate "$CANDIDATE" --output "$INVENTORY"
python3 traces/trace_fixture_inventory.py verify "$CANDIDATE" "$INVENTORY"
```

The last three ran separately on raw native and compressed candidate trees.
Named external scripts retain reproducible full-file/capability/timing and
compression checks. These are candidate-evidence diagnostics, not fixture
replacement or an engine green-suite claim.

### Consumer timing checks, limits and final ownership

At unchanged parent source `c4cd6ae0e8cdaf00bdf013c3c740586039c10ef8`,

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py \
  -Dmse=off -Dtest=TestHardwareTimingInterstitialStream test -B
```

passed **20 tests, zero failures/errors/skips** under Java 25. Maven reported
621 seconds of admission wait, then 1 minute 35 seconds of execution including
clean compilation (the test class itself reported 0.323 seconds). Overall
wrapper wall was not separately measured. This exercises the existing loader,
span indexing, no-work cursor handoff, claimed-span verification and adversarial
identity checks; it is not a candidate campaign replay. The initial tool
preflight rejected the ambient Lua version before running tests. The same
preflight passed after explicitly selecting `/usr/bin/lua5.4`; PowerShell and
Java 25 were also present. No broad engine smoke or public-release full suite
was run for these producer tests, external candidates and audit prose.

Remaining delivery dependencies are explicit:

- Independent review of this local test-only follow-up and authorization to
  make the producer commits remotely available. Never commit their unavailable
  gitlink in the parent.
- Candidate-root Java fixture-load/compression/reference-closure and applicable
  replay/frontier checks, including actual Knuckles run-chain handoffs with the
  interstitial sidecar, before any canonical fixture replacement. Structural
  review and generic consumer tests do not establish gameplay replay success.
- Full recorder acquisitions for the Sonic+Tails emerald and Tails emerald
  movies before publishing their freshly witnessed endpoint values. Their raw
  witnesses cannot be copied into an old manifest. The normal-route native
  endpoint remains Level and does not establish campaign completion.
- Resolution or bounded baseline attribution of native test failures wherever
  authoritative producer qualification requires them. Only four of the 59
  current failures have matched-baseline evidence; 55 remain unattributed.

No canonical fixture, parent gitlink, remote branch, API, release version or
main-workspace source was changed or pushed. The parent has only this audit
change; its index retains producer pin `703bd92`. Its working submodule
intentionally remains at local test commit `3842d2c`; retain this isolated T3
checkout, that branch and the external task directory. The prior isolated
checkout and its artifacts remain intact at local commit `2f55ea3`.
All owned capture, witness, native-test, compression, validation and Maven
command sessions have completed; no task-owned job or delegate remains active.
Total delivery wall time is unmeasured and overlapping intervals are not added.
Known throwaway Python bytecode and the consumed Java success log were removed;
useful candidate/raw evidence, diagnostics, sources and unknown/user work were
preserved. No task worktree or local branch was deleted: the unpublished local
producer commits and candidates still need the coordinator's review/delivery.
