# S3K direct Kosinski queue Task 8 publication candidates

Date: 2026-07-28

## Status

Task 8 pre-approval is complete. Three schema-2 candidates were captured with
the reviewed native recorder into harness scratch and were not installed:

- `s3k/aiz1_to_hcz_fullrun`;
- `s3k/aiz_completerun`; and
- `s3k/icz_completerun`.

This report is the only committed Task 8 deliverable. Nothing under
`src/test/resources/traces/` was modified. Publication, post-installation
guards, after-publication frontier measurement, integration, merge, and push
remain blocked on explicit approval of the exact bytes below.

There is no prior approval for these schema-2 candidate bytes. The requested
approval is a new, narrowly scoped authorization to replace the three named
schema-1 fixture directories. It does not supersede the prior recorder code
review or the historical approval of the currently committed schema-1
fixtures.

## Candidate selection

`TestCommittedHardwareTimingFixtures` and the Task 6 checked inventory identify
exactly three schema-1 routes which reach a gameplay consumer of
`Kos_decomp_queue_count`:

| Candidate | Consumer | Native identity |
|---|---|---|
| `aiz1_to_hcz_fullrun` | AIZ intro | STANDARD `aiz_end_to_end`, offset 511, 20,798 rows |
| `aiz_completerun` | AIZ intro | COMPLETE-RUN segment 0, offset 941, 26,228 rows |
| `icz_completerun` | ICZ1-to-ICZ2 transition | COMPLETE-RUN segment 4, offset 138,117, 25,393 rows |

The multi-bonus AIZ segment begins at camera X `$1300`, after the intro
consumer, and is excluded. The complete-run capture also produced HCZ, MGZ,
and CNZ scratch segments needed to preserve the run-wide timing ledgers through
ICZ; they are not publication candidates.

## Capture provenance

Candidates were captured at:

```text
branch: bugfix/ai-s3k-kos-decompression-queue
commit: fbfee310cd3f12596cf1f0abcd8bdb0032a5ff0c
worktree: /home/farrell/code/projects/OpenGGF/.worktrees/ai-s3k-kos-decompression-queue
BizHawk: 2.11.0.0
Mono: 6.12.0
Maven: 3.9.16
Java: 21.0.11
```

The worktree had no tracked changes before capture. Its only pre-existing
untracked entries were local disassembly directories. Scratch capacity was
1.1 TiB. The native harness built successfully with `xbuild`.

The discovered ROM identities were:

| Game | File | CRC32 | SHA-1 |
|---|---|---|---|
| S1 REV01 | `Sonic The Hedgehog (W) (REV01) [!].gen` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| S2 REV01 | `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| S3K locked-on | `Sonic and Knuckles & Sonic 3 (W) [!].gen` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

Only the verified S3K ROM was consumed by these captures. Movie provenance is:

| Movie | Bytes | SHA-256 |
|---|---:|---|
| `aiz1_to_hcz_fullrun/s3-aiz1-2-sonictails.bk2` | 9,133 | `6837de0f67db7eb68f20b6f6df6a2872713a613d8b4dbc804847209c16b56e97` |
| `_movies/s3k-complete-sonic-tails.bk2` | 192,715 | `82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf` |

All output-affecting unmodeled `OGGF_*` variables were unset. The capture
commands were:

```bash
BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  env \
  -u OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS \
  -u OGGF_S3K_CNZ_EVENT_RAM_RANGE \
  -u OGGF_S3K_RNG_CALL_RANGE \
  -u OGGF_S3K_AIZ_FIRE_RANGE \
  -u OGGF_S3K_AIZ_WALL_SENSOR_RANGE \
  -u OGGF_S3K_CRL_RANGE \
  -u OGGF_S3K_CNZ_CYLINDER_RANGE \
  -u OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START \
  -u OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END \
  -u OGGF_TRACE_STOP_FRAME \
  -u OGGF_BK2_FRAME_COUNT \
  tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "/home/farrell/code/projects/OpenGGF/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  --movie "/home/farrell/code/projects/OpenGGF/.worktrees/ai-s3k-kos-decompression-queue/src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/s3-aiz1-2-sonictails.bk2" \
  --output "/home/farrell/code/projects/OpenGGF/.worktrees/ai-s3k-kos-decompression-queue/tools/bizhawk-headless/.scratch/task8-candidates/aiz-standard" \
  --trace-profile aiz_end_to_end

BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  env \
  -u OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS \
  -u OGGF_S3K_CNZ_EVENT_RAM_RANGE \
  -u OGGF_S3K_RNG_CALL_RANGE \
  -u OGGF_S3K_AIZ_FIRE_RANGE \
  -u OGGF_S3K_AIZ_WALL_SENSOR_RANGE \
  -u OGGF_S3K_CRL_RANGE \
  -u OGGF_S3K_CNZ_CYLINDER_RANGE \
  -u OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START \
  -u OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END \
  -u OGGF_TRACE_STOP_FRAME \
  -u OGGF_BK2_FRAME_COUNT \
  tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "/home/farrell/code/projects/OpenGGF/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  --movie "/home/farrell/code/projects/OpenGGF/.worktrees/ai-s3k-kos-decompression-queue/src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2" \
  --output "/home/farrell/code/projects/OpenGGF/.worktrees/ai-s3k-kos-decompression-queue/tools/bizhawk-headless/.scratch/task8-candidates/complete-through-icz" \
  --trace-profile complete_run \
  --effective-movie-length 163511
```

The first command reported offset 511 and 20,798 rows. The second reported
five segments and zero transitions:

| Scratch token | Offset | Rows |
|---|---:|---:|
| `aiz` | 941 | 26,228 |
| `hcz` | 27,170 | 31,482 |
| `mgz` | 58,653 | 39,398 |
| `cnz` | 98,052 | 40,064 |
| `icz` | 138,117 | 25,393 |

The STANDARD output contains exactly the four fixture files. The COMPLETE-RUN
root contains no files and exactly the five directories above; each segment
contains exactly the four fixture files. Neither capture contains a
`run_manifest.json`, so all three candidates are standalone fixture
directories, not run-manifest-owned segments.

## Frozen candidate bytes

For compressed payloads, the first length and digest identify the exact
prospective installed gzip container. The plaintext length and digest identify
its verified decompression.

| Candidate | File | Candidate bytes | Candidate SHA-256 | Plaintext bytes | Plaintext SHA-256 |
|---|---|---:|---|---:|---|
| `aiz1_to_hcz_fullrun` | `physics.csv.gz` | 605,006 | `b5591fe7f274b78f72dfbae83eb8d975c172cd393fe5707582f11efa4ea20c77` | 3,369,910 | `3c219725d85d64762b514f973263edced337a37cd16fb8bf50f2b0ac3b5a2a39` |
|  | `aux_state.jsonl.gz` | 5,846,635 | `0c7ac4514b9e6c1750a340aeb034660b0a954ba666977aab9dea9da18e19e137` | 127,656,342 | `9d90d669de5b9fc0c00666ad2023a164d1d110d441b9bcc8403280d1a5d74b47` |
|  | `hardware_timing.jsonl` | 17,478 | `d9dfb065ba364b7e8e1843e4debdcfd50e37205017478b12391408d0f72421ba` | — | — |
|  | `metadata.json` | 1,273 | `64fd828e0fe16debdb22766d65b7230463fe84e49a1e483c3f93690b55be2571` | — | — |
| `aiz_completerun` | `physics.csv.gz` | 818,157 | `1b60c8120b60aaf815e42123a58307e1b088201da6d6767c9703e40d16a51ce8` | 4,249,570 | `2f8d3d0c2f5a4b3f30b7784ed28fa37071951f6d8d538f08573b4631fa33f872` |
|  | `aux_state.jsonl.gz` | 7,986,812 | `b98e40eb2847ca24a83ee4be6e19a39e304a38e207f206069577ed7502aa4549` | 172,380,688 | `d55efb44c7fadc022591c56054964e002c8ade868867a8965a0efbe820f2d210` |
|  | `hardware_timing.jsonl` | 19,729 | `22ca5e34396a20f4c8b45b0c2afbf72f8f282f26da4542f3d35a75d8289e996d` | — | — |
|  | `metadata.json` | 1,608 | `26889c0529b31975200d0d8f9e33152fe047906238dcf2eca3dc7af560c02570` | — | — |
| `icz_completerun` | `physics.csv.gz` | 848,470 | `49a9d4c90dfa5e392d25fc694ebf489e372b64b8f0b30bd8c63e5cf2d1cbdbf4` | 4,114,300 | `386cf6e8e62b61c8cd03c252668db47d3511fc1fd6c43399830e6655086d0c99` |
|  | `aux_state.jsonl.gz` | 8,232,022 | `aef33fbefc2164807cb8ea66fcafa416105a154a084dd2d52dd4e9887910ec7b` | 173,735,703 | `0e21af4b895ab47ceca79ea74301208bd8ab1a44899cab921c566d75608efaa9` |
|  | `hardware_timing.jsonl` | 10,226 | `a1db1f2825b4672a2f5789e981cce17f2575548847f5b82aa47f7ecc6faf6405` | — | — |
|  | `metadata.json` | 1,611 | `81a7e5ca76d6284b8ac356772bcffd1a83caccf96b8a439acdadb396122df4a1` | — | — |

A second STANDARD capture to
`.scratch/task8-candidates/aiz-standard-repro` reproduced all four candidate
files byte-for-byte, including both gzip containers.

## Metadata, row, and event inventory

All three candidates retain trace schema 7 and CSV version 7. STANDARD is
`6.38-s3k`; COMPLETE-RUN is `6.38-s3k-completerun`. All three declare hardware
timing schema 2.

| Candidate | Physics lines | Declared rows | Aux events | Aux frame range | Hardware events |
|---|---:|---:|---:|---|---:|
| `aiz1_to_hcz_fullrun` | 20,799 | 20,798 | 598,934 | `-1..20797` | 79 |
| `aiz_completerun` | 26,229 | 26,228 | 835,313 | `-1..26227` | 89 |
| `icz_completerun` | 25,394 | 25,393 | 851,401 | `-1..25392` | 46 |

The complete aux event inventories are:

| Event | AIZ STANDARD | AIZ complete | ICZ complete |
|---|---:|---:|---:|
| `air_countdown_state` | 41,596 | 52,456 | 50,786 |
| `aiz_fire_transition` | 401 | 0 | 0 |
| `aiz_handoff_terrain_state` | 9 | 9 | 0 |
| `checkpoint` | 6 | 0 | 0 |
| `control_lock_state` | 2,160 | 3,273 | 3,196 |
| `cpu_state` | 20,798 | 26,228 | 25,393 |
| `cpu_state_snapshot` | 1 | 1 | 1 |
| `game_paused_state` | 0 | 26,228 | 25,393 |
| `interact_state` | 41,496 | 52,336 | 50,670 |
| `mode_change` | 502 | 747 | 876 |
| `object_appeared` | 3,638 | 4,551 | 7,329 |
| `object_near` | 199,830 | 277,783 | 289,285 |
| `object_removed` | 2,390 | 3,051 | 4,506 |
| `object_state` | 242,402 | 333,312 | 338,502 |
| `oscillation_state` | 20,798 | 26,228 | 25,393 |
| `player_mode_set` | 1 | 1 | 1 |
| `routine_change` | 6 | 14 | 16 |
| `sidekick_interact_object` | 20,698 | 26,108 | 25,277 |
| `slot_dump` | 1,593 | 2,174 | 3,954 |
| `state_snapshot` | 591 | 797 | 819 |
| `terrain_wall_sensor` | 12 | 12 | 0 |
| `zone_act_state` | 6 | 4 | 4 |

The plaintext payload digests are identical to the committed fixtures, so
this inventory is unchanged in full rather than sampled.

## Hardware timing ledger

The candidate event streams pass a mechanical global ordering check using
`VINT_SERVICE < PRE_MAIN_LOOP < POST_OBJECTS` within equal raw frames. Every
direct event is at `PRE_MAIN_LOOP`, and ordinals are contiguous independently
within each kind. No selected segment happens to contain a direct and module
event on the same raw frame.

| Candidate | Kind/boundary | Count | Ordinals | Raw frames | Unique fingerprints |
|---|---|---:|---|---|---:|
| `aiz1_to_hcz_fullrun` | direct/PRE | 41 | `8..48` | `358..20794` | 32 |
|  | module/POST | 38 | `2..39` | `361..20797` | 24 |
| `aiz_completerun` | direct/PRE | 48 | `8..55` | `68..26207` | 39 |
|  | module/POST | 38 | subset of `2..42` | `71..26116` | 25 |
|  | module/VINT | 3 | subset of `2..42` | `6351..26208` | 3 |
| `icz_completerun` | direct/PRE | 20 | `161..180` | `12304..25367` | 18 |
|  | module/POST | 24 | `158..181` | `35..25287` | 18 |
|  | module/VINT | 2 | `182..183` | `25352..25370` | 2 |

The first and last ordinal fingerprints are:

| Candidate/kind | First | Last |
|---|---|---|
| AIZ STANDARD direct | `8:c381a8f75b41d3e2d1e52fb90ae8a5c269b1daeb88dd198bd8fb3d07d3703a7b` | `48:fbfc78d499717cfec6df27fdd04fa4b5293a7147ec7ff7a7a18004e9db801e78` |
| AIZ STANDARD module | `2:4423f6be47e039925c8575c68ed5eb22e9cba75f2aadd05f1d288d6c9579e723` | `39:05324378670c6afa8c6d99f6e5313d625d2d926e6bc16f25cd9d8d1a5a195bf8` |
| AIZ complete direct | `8:c381a8f75b41d3e2d1e52fb90ae8a5c269b1daeb88dd198bd8fb3d07d3703a7b` | `55:e518e12a98e5cf2e81aaa09873e0c6c15e20f2813825dd2c88ce7ce3cc62d1cd` |
| AIZ complete module | `2:4423f6be47e039925c8575c68ed5eb22e9cba75f2aadd05f1d288d6c9579e723` | `42:6f27fe001c4a21687a98eb0dc8178340e7434967941c7a2fb7df442285b4c6f0` |
| ICZ complete direct | `161:29c7cf89ce102c704c78944dacf02b2a08fd370784d778dd08586c9b5842c9f6` | `180:ed4cd374a892ed4874e233fdc171cf685049e7ad8738d1874f4b76479ccf8360` |
| ICZ complete module | `158:9d76abb5369bb27fca1574b28bf37d429af1904dbd45edabef04fd0ab1e0f594` | `183:0cb0ec2d16ae44f272700f6e4825526242417afa145a7ff8405f92db8f341be2` |

The full hardware file digests above freeze every intermediate ordinal and
fingerprint.

## Mechanical byte-delta categorization

Every whole-file delta from the committed fixture has a named cause:

1. **Physics and aux plaintext: no delta.** All six decompressed byte streams
   have identical lengths and SHA-256 digests.
2. **Complete-run gzip containers: no delta.** Both AIZ and ICZ physics and
   aux gzip files are byte-identical to the committed containers.
3. **STANDARD gzip containers: native publisher recompression only.** The
   committed physics container is 570,548 bytes,
   SHA-256 `ad226dd6db810b6e12980e0211fe735782bb2888f5df2bdc865698ae8ac60571`;
   the committed aux container is 5,736,502 bytes,
   SHA-256 `212ad7191fd5f00bf333886a179a1ba471d7d7c242599da0e9fd8e230c4f5617`.
   The candidate containers differ, but complete streaming decompression is
   byte-identical and a second current-native capture reproduced the candidate
   containers exactly. The difference is therefore confined to deterministic
   current-publisher deflate layout, with no payload delta.
4. **Metadata: exactly three fields per candidate.** Mechanical JSON
   comparison finds only `recording_date` (`2026-07-27` to `2026-07-28`),
   `lua_script_version` (`6.37` to `6.38` for the relevant recorder), and
   `hardware_timing_schema` (`1` to `2`). All remaining keys, order, and
   formatting are exact.
5. **Hardware timing: direct-ledger insertion only.** Filtering each candidate
   to `kos_module_queue` produces a byte-exact copy of the complete committed
   hardware file. The candidate-minus-fixture byte increase equals the
   serialized direct lines exactly:

   | Candidate | File increase | Direct-line bytes |
   |---|---:|---:|
   | `aiz1_to_hcz_fullrun` | 9,232 | 9,232 |
   | `aiz_completerun` | 10,820 | 10,820 |
   | `icz_completerun` | 4,540 | 4,540 |

This projection and size identity accounts mechanically for every hardware
stream byte; no module event moved or changed.

## Independent recorder review

Independent review found no blocker at the capture commit.

Recorder production source is unchanged after the reviewed Task 5 sequence
`282018672`, `39584f77b`, and `cddbff68d`;
`git diff cddbff68d..fbfee310c -- tools/bizhawk-headless/src` is empty. The
review verified:

- `$FF0E` big-endian busy/count semantics and the four-entry `$FF40` FIFO;
- busy-to-idle retirement, longest suffix/prefix overlap, identical
  replacement, stable identical head, stale slot-zero, and mutation failure
  paths;
- exact 32-bit source/destination identity, standard-Kosinski scan lengths,
  and the shared language-neutral Java/C# vectors;
- direct PRE emission, canonical VINT/PRE/POST ordering, segment-gap
  continuity, and atomic reset of both ledgers; and
- schema-2 metadata defaults plus refusal of every unmodeled output-affecting
  S3K environment variable.

The ROM lifecycle remains anchored to `Queue_Kos`, `Process_Kos_Queue`, and
`Process_Kos_Module_Queue` in `docs/skdisasm/sonic3k.asm:2668-2967`, with the
ordinary level-loop boundary order at `sonic3k.asm:7884-7922`.

## Pre-installation trace frontiers

Each class ran in a separate JDK-21 Maven invocation with the verified
`-Ds3k.rom.path`. `target/trace-reports` was cleared between invocations and
each report, context, Surefire text report, and XML report was archived under
`.scratch/task8-candidates/frontiers/`.

| Class | Maven outcome | Timing publication stop | Comparator report |
|---|---|---|---|
| `TestS3kAizTraceReplay` | 16 tests: 4 failures, 1 error | module `#27`, fingerprint `65c8c371e1ca1f70acf3a74cc1fa689867dcffbe93617a8c968e3de9242f89b3`, engine pending none | 36 errors; first `x` at frame 5496, `0x00CD` vs `0x2FCD` |
| `TestS3kAizCompleteRunTraceReplay` | 1 test: 1 error | module `#26`, same fingerprint, engine pending none | 46 errors; first `x` at frame 6300, `0x00D2` vs `0x2FD2` |
| `TestS3kIczCompleteRunTraceReplay` | 1 test: 1 error | module `#160`, fingerprint `0fbbcb25822bda53fc0b212780f2218200ef117c753fa623fa1a05c66379f152`, engine pending none | 81 errors; first `x` at frame 1232, `0x386E` vs `0x386F` |

These are the frozen pre-installation frontiers. The schema-1 module-only
fixtures cannot close the new direct-count authority contract.

## Verification

Fresh focused recorder lifecycle tests:

```text
BIZHAWK_HOME=<bizhawk> ./test.sh --filter HardwareTimingEventEngine --jobs 1
15 passed; unrelated GpgxHost case skipped because S1_ROM_PATH was unset
```

Fresh schema-selection tests:

```text
BIZHAWK_HOME=<bizhawk> ./test.sh --filter "schema two" --jobs 1
3 passed; unrelated GpgxHost case skipped because S1_ROM_PATH was unset
```

Fresh Java scanner, queue, stream-loader, and replay-port tests:

```text
mvn -Dmse=off \
  "-Dtest=TestHardwareSubmissionFingerprint,TestS3kKosDecompressionQueue,TestHardwareTimingStreamLoader,TestHardwareTimingReplayPort" test
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
```

Task 7's current-recorder all-ROM no-gates result remains:

```text
410 total: 410 passed, 0 failed, 0 skipped
```

The expected native differentials remain non-successful at the explicit
schema-1 publication boundary; they were not weakened into passes.

## Exact approval payload

Approval is requested for byte-for-byte installation, with no hand edits, of
these exact mappings:

| Scratch candidate | Approved destination |
|---|---|
| `tools/bizhawk-headless/.scratch/task8-candidates/aiz-standard/` | `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/` |
| `tools/bizhawk-headless/.scratch/task8-candidates/complete-through-icz/aiz/` | `src/test/resources/traces/s3k/aiz_completerun/` |
| `tools/bizhawk-headless/.scratch/task8-candidates/complete-through-icz/icz/` | `src/test/resources/traces/s3k/icz_completerun/` |

Approval covers only the twelve exact files identified by the candidate
SHA-256 and byte-length table. It explicitly acknowledges:

- unchanged physics and aux plaintext;
- deterministic STANDARD gzip container recompression;
- the three exact metadata field changes; and
- byte-exact preservation of every module event plus the reported direct
  schema-2 events.

Without that explicit approval, none of these bytes will be installed.
