# Audio frontier log

The audio counterpart of [trace-frontier-log.md](trace-frontier-log.md): the
running record of driver-oracle comparisons between the engine's SMPS driver
and reference captures recorded from the real driver running in the pinned
emulator (BizHawk 2.11 / Genesis Plus GX through TraceChaser).

Each entry records, newest first:

- **Date / commit / worktree** the comparison ran at.
- **Fixture** — the committed reference capture (and its BK2 movie) compared
  against, by file name under `src/test/resources/audio/parity/`.
- **Command** — the exact re-runnable invocation.
- **Result** — `MATCH` or the comparator's first divergence: tick ordinal,
  role/field (or event index), reference vs engine value. The comparator is
  validation-first and no-realignment, so one entry has exactly one first
  divergence; there is no error count beyond it. A capture failure is recorded
  as such, never as a parity result.
- **Notes** — what moved, or what the divergence is suspected to be. Fixing
  driver behaviour belongs to implementation lanes; this log only measures.

Comparisons at this tier are per driver invocation: driver-RAM-shaped track
state plus the ordered YM/PSG write stream of that invocation ("ticks"), as
defined by `com.openggf.tools.audio.parity`.

---

<!-- entries are prepended below, newest first -->

## 2026-09-03 — S2 request-transfer window MATCH; driver tick frontier unchanged

- **Worktree/branch:** `.worktrees/s2-c0a-replan3`,
  `bugfix/ai-s2-c0a-replan3`; measured at `7b1442846` plus the reviewed
  spike-cause diff now committed as `89eab0649`.
- **Fixture:** two independently extracted, comparison-only raw-v2 candidates
  for source rows `[10150,10900)`, each SHA-256
  `a7d56fe71674d9f4a9307e6fb6078f7832409bb310916e808faf28b1e9426c2c`;
  both remain explicitly unbound (`production_bound:false`).
- **Command:** `mvn -Dmse=off -Dsonic2.rom.path=<absolute REV01 ROM>
  -Ds2.request.candidate.path=<candidate-g-or-h raw-v2 JSONL>
  -Ds2.request.bk2.path=<pinned complete-emeralds BK2>
  -Dtest=com.openggf.tools.audio.parity.s2.TestS2RequestAwareOracleRawStream#realCandidateComparesAgainstIndependentProductionBk2Run
  test -B`.
- **Result:** candidate g and candidate h independently reported
  **`MATCH: 25 production transfers agree`**, exit 0 (one test, no
  failures/errors/skips). Before the two source-owned fixes, the first
  divergences were transfer 3 (ring B5 in SFX0 rather than ROM SFX1) and then
  transfer 20 (CPU Tails spike damage A3 rather than ROM A6). The reviewed
  observer-only same-BK2 comparison at `89bdb6eb9` then reported
  **DIVERGENCE at tick 0 (movie row 10202)**, `global.tempoTimeout`, expected
  `0x3c`, actual `0x58`; 698 of 698 ticks diverged.
- **Notes:** the raw-v2 candidates remain comparison-only and supply no driver
  input; requests arise from the BK2-driven engine. This does not authenticate
  the candidates or bind replay authority. It supersedes the old tick-210
  music-only-host result: that host supplied no SFX requests, whereas the
  same-BK2 observer measures the production request path.

  At `3045e716d`, the S2 compressed-load readiness model cost the actual EHZ
  bank-2 Saxman path at 363,255 Z80 T-states (363,283 on the enabled PAL
  path). It predicted exactly six fully masked presentation rows and moved the
  same-BK2 tick-0 value from `0x58` to **`0xa4`**, no further. The remaining
  `0xa4` versus `0x3c` mismatch is the separately measured early Music0
  request: OpenGGF submits EHZ at row 10184, while native initialization is
  bounded to the row 10194→10195 boundary.

## 2026-09-02 — S3K E4 seven-slot stop/restore source correction under review; no oracle move

- **Worktree/branch:** `.worktrees/sound-driver-roadmap-completion`,
  `feature/ai-sound-driver-roadmap-completion`, candidate over accepted retained
  S3K E4-state commit `8e0babd09`.
- **Fixture:** none changed. The authenticated S3K AIZ1 reference has no E4
  request, so it cannot establish an E4 comparison result.
- **Command:** `mvn -Dmse=off
  -Dtest=TestSmpsDriverSession,TestS3kE4StopSfxPlan,TestSmpsStatefulCommandPolicy,TestSmpsPhysicalPolicy
  test -B`.
- **Result:** the earlier candidate omitted `cfStopTrack`'s E4-local
  `zGetSFXChannelPointers` PSG sequence. The corrected plan now covers, in native
  order, the raw YM `$28` hazard, `1Fh + current SFX VoiceControl`, the stopped-SFX
  bit-0 conditional `$FF`, the FixBugs=0 unconditional `$FF`, and then an eligible
  signed music-noise re-latch; it also retains AMS/FMS for the music `$B4` restore.
  Exact direct and composite rollback tests cover physical PSG failures and the
  post-logical-mutation/pre-publication boundary.
- **Notes:** this entry records a source correction, not a product-frontier closure.
  No comparator was run and no `MATCH` is claimed. Service 128 remains the same
  authenticated `REFERENCE_LIMITATION` (`producer_input`); standalone E3 and
  PSG-SFX-admission stale-`ix`/`$FF` behaviour are separate frontiers.

## 2026-09-02 — S3K E3 PSG-silence product gap closes without moving service 128

- **Worktree/branch:** `.worktrees/sound-driver-roadmap-completion`,
  `feature/ai-sound-driver-roadmap-completion` from `8952620bf` (Task 9A
  implementation; commit containing this entry).
- **Fixture:** no capture or fixture changed. The existing authenticated
  `s3k-aiz1-intro-reference-v1.jsonl.gz` remains unchanged and does not supply
  an E3 request.
- **Command:** focused profile/resolver/queue/session/oracle-host tests:
  `LUA_BIN=lua5.4 mvn -Dmse=off
  -Ds3k.rom.path=<absolute-S3K-ROM>
  '-Dtest=com.openggf.game.sonic3k.audio.TestSonic3kSpeedShoesCommandSemantics,com.openggf.audio.presentation.TestAudioPresentationCommandResolver,com.openggf.audio.presentation.TestAudioPresentationCommandQueue,com.openggf.audio.session.TestSmpsDriverSession#psgSilenceWritesExactRomProgramWithoutMutatingSessionState+psgSilenceObserverFailureCannotPartiallyApplyOrReplay,com.openggf.tools.audio.parity.s3k.TestS3kAudioOracleFixtureContract'
  test -B`.
- **Result:** **56 tests pass** with 0 failures, 0 errors, and 0 skips. The
  production typed route and engine oracle host both execute the immutable
  `9F BF DF FF` program sourced from `zPlaySoundByIndex` /
  `zPSGSilenceAll`. The session test retains the physical identity and all
  logical music/SFX/override/tempo/pending-service state, observes no YM
  writes, and proves there is no next-service duplicate. Observer failure is
  quarantined after commit and cannot partially apply or replay E3.
- **Notes:** this closes the source-backed E3 **product gap only**. It does not
  move or reinterpret the authenticated comparison. Service **128** remains
  `REFERENCE_LIMITATION`, `field=producer_input`, because the request consumed
  while the reference producer suspended interrupt services is unavailable.

## 2026-09-01 — Override-resume producer stops at `REFERENCE_LIMITATION`

- **Worktree/branch:** `.worktrees/sound-driver-roadmap-completion`,
  `feature/ai-sound-driver-roadmap-completion`; TraceChaser producer commits
  `912fef0a`, `e3fdf73`, and mechanics hardening through `a61450ee`.
- **Fixture:** none. The dedicated
  `src/test/resources/audio/parity/override-resume-first-divergence-v1/`
  commit bundle remains absent; its proposed nested S1/S2 members have no
  independent authority, and no capture or fixture gained authority.
- **Command:** focused TraceChaser S1, S2, extractor/publisher, CLI, and Lua
  contract tests plus `verify-deterministic-build.sh`; the locked observer
  recipe verifier and the reviewed-capability guard were then run as hard
  authority checks. Exact commands and hashes are recorded in
  `docs/architecture/validation/audio/2026-09-01-override-resume-reference-limitation.md`.
- **Result:** `REFERENCE_LIMITATION`, code
  `FRESH_AUTHENTICATED_NATIVE_GPGX_AUTHORITY_UNAVAILABLE`. The current host's
  `/usr/bin/ar` differs from the locked recipe, the current collector source
  differs from the pinned capability field that Task 8 is forbidden to
  refresh, and no current-session two-build observer inputs are configured.
  A static older install is not fresh capture authority.
- **Notes:** `a61450ee` hardens fd-relative staging and identity-uncertain
  quarantine, but it cannot prove nested-path containment when a retained
  target dirfd is renamed after revalidation, and four sequential `linkat`
  calls cannot provide one visibility point. The canonical plan now requires
  private construction of the complete dedicated bundle and one
  `renameat2(RENAME_NOREPLACE)` commit under an explicit cooperative-lock and
  namespace-stability precondition. That publisher redesign is required before
  publication. The mandatory provenance inventory also remains incomplete, so
  Task 8 made no Java production change, froze no literal expectation,
  performed no live capture, and left both S1 hard gates untouched.
  The subsequent atomic-bundle implementation replaces those four links with
  private exact-inventory construction and one directory
  `renameat2(RENAME_NOREPLACE)` under the documented cooperative lock and
  namespace-stability precondition. Bundle-aware Java consumers now reject an
  absent or invalid commit object without consulting legacy leaves. This moves
  no audio frontier: the fresh authenticated native-GPGX and complete
  provenance gates remain unavailable, no fixture was published, and the same
  `REFERENCE_LIMITATION` code remains authoritative.

## 2026-09-01 — S3K service 128 is an authenticated producer-input limitation

- **Worktree/branch:** `.worktrees/sound-driver-roadmap-completion`,
  `feature/ai-sound-driver-roadmap-completion` at `e4f083172`
  (documentation fix round 3; production comparator/reference evidence is
  unchanged).
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged authenticated 5,400-frame reference; projected to 5,286
  services).
- **Command:**
  `LUA_BIN=lua5.4 mvn -Dmse=off
  -Ds3k.rom.path=${OGGF_REPO_ROOT}/s3k.gen
  '-Dtest=com.openggf.tools.audio.parity.s3k.TestS3kAudioOracleFixtureContract,com.openggf.tools.audio.parity.s3k.TestS3kAudioParityComparator'
  test -B`, plus
  `java -cp target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar
  com.openggf.tools.audio.parity.s3k.S3kAudioParityTool compare
  --reference src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz
  --rom ${OGGF_REPO_ROOT}/s3k.gen --ticks 260 --format json`.
- **Result:** the 260-service run compares services 0-127, then stops at
  service/tick **128**, underlying reference event **0** (`YM port 1,
  register 82h, value FFh`). The typed report is
  `REFERENCE_LIMITATION`, `field=producer_input`, with
  `ProducerInputEvidence.Availability.UNAVAILABLE_DURING_PRODUCER_SUSPENSION`
  and reason `mailbox input was unavailable for the first observable service
  after reference producer interrupt services suspended`; the CLI exits **5**.
  This is not an engine divergence and there is no realignment. Ordinary
  missing-write evidence remains `EVENT_MISSING` and exits **3**; malformed
  reference/tool failures remain exit **4**.
- **Notes:** the limitation is selected from the source-owned
  `zPlaySEGAPCM` interrupt-suspension boundary and the first resumed service,
  not from a tick number, zone, request guess, or write shape. The exact
  84-write stop proof remains at service/tick **49** for `FFh`; service/tick
  **138** (next music activation) still begins with the exact reference
  84-write stop prefix. S1 hard gates remain `MATCH (14,690 ticks)` for GHZ
  music and `MATCH (1,967 ticks, 8 dispatches)` for sound-test SFX. Named
  remaining frontiers are the unsupported `E3h` PSG-mute product gap (not a
  structured producer-input `REFERENCE_LIMITATION`), the `E4h` seven-slot
  conditional physical write/restoration walk, and full `FFh` control-flow
  parity beyond the implemented 84-write stop/PCM transport (including the
  producer-side pre-consumption mailbox at service 128).

## 2026-08-31 — S1 sound-test SFX oracle reaches full MATCH

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` at `4c1efea6c` plus the pending S1
  implementation tranche.
- **Fixture:** `src/test/resources/audio/parity/s1/s1-soundtest-sfx-reference.v1.jsonl.gz`
  (unchanged committed reference; S1 World REV01 ROM SHA-1
  `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`).
- **Command:** `S1AudioParityTool capture --capture sfx --reference
  <run-root>/reference.jsonl.gz --rom <absolute verified ROM> --output
  <run-root>/openggf.jsonl`, followed by `S1AudioParityTool compare
  --reference <run-root>/reference.jsonl.gz --openggf
  <run-root>/openggf.jsonl --human-report <run-root>/report.txt
  --json-report <run-root>/report.json`.
- **Result:** **`S1 audio parity: MATCH (1967 ticks)`**, exit 0. A fresh
  capture/comparison against the committed GHZ music fixture remains
  **`MATCH (14690 ticks)`**, exit 0.
- **Notes:** first-divergence work from tick 377 through the end of the
  fixture modelled source-owned S1 behavior: one terminal note-off; the
  explicit PSG3 `$DF/$FF` admission pair and shared tone-3/noise ownership;
  per-track release with FM voice/pan and PSG rest/noise restoration; fixed
  SFX-RAM walk order; tied PSG volume service; raw `$B5` ring-speaker
  alternation; and shipped `FixBugs=0` `SendVoiceTL` reads through the global
  special-SFX pointer, including ROM vector bytes when it is zero. No fixture
  or comparator was changed. Human listening remains pending in the SMPS
  playback checklist.

## 2026-08-31 — S3K service projection crosses SEGA PCM to the hidden stop request

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the PCM-tier correction lands with this
  entry).
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged authenticated 5,400-frame reference).
- **Command:** `S3kAudioParityTool compare --reference <committed fixture>
  --rom <absolute SHA-1-verified locked-on ROM>` after compiling the worktree.
- **Result:** services 0-127 match. First divergence service **128** (source
  frame **242**), `EVENT_MISSING`, event 0: reference Z80 YM part II
  `82h = FFh`, engine missing.
- **Notes:** `FFh` enters `zPlaySEGAPCM`, which clears its request flag and
  disables interrupts for the whole chant (`D:4372-4424`). The projection now
  keeps the command-owned 84-write stop-all service, excludes register `2Ah`
  sample transport / `2Bh=80` DAC entry, and emits no fictitious driver
  services for the 100 transport-only frame rows. This yields **5,286**
  complete services. At source frame 242 the reference emits another exact
  stop-all burst, consistent with `FEh` (`cmd_StopSEGA`), but its pre-frame
  mailbox is empty: the 68k request is written and consumed within
  `host.Advance`, before the post-frame RAM snapshot. The v1 producer therefore
  cannot authorize the engine request at service 128. Moving this frontier
  requires a true pre-consumption 68k-to-Z80 mailbox probe, not inference from
  the burst.

## 2026-08-31 — S3K service projection reaches the SEGA command at service 49

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the service projection lands with this
  entry).
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged authenticated 5,400-frame reference).
- **Command:** `S3kAudioParityTool compare --reference <committed fixture>
  --rom <absolute SHA-1-verified locked-on ROM>` after compiling the worktree.
- **Result:** the complete boot service and the first ordinary `E1h`
  fade-init service match. First divergence service **49**, `EVENT_MISSING`,
  event 0: reference Z80 YM part II `82h = FFh`, engine missing.
- **Notes:** the reader now projects 5,400 frame rows into 5,386 complete Z80
  services. It groups the cross-frame boot burst by the source-owned
  `zPalDblUpdCounter` transition `0 -> 5` at `zInitAudioDriver` completion
  (`D:523-551`), producing one 85-write boot service without a frame-number
  trigger. The engine host emits the exact shipped `zStopAllSound` sequence;
  the next service consumes the `E1h` request that remained pending during
  boot and matches its unconditional `zPSGSilenceAll`. Service 49 consumes
  `FFh` (`cmd_SEGA`), whose first action is another `zStopAllSound`; SEGA PCM
  dispatch is still explicitly unsupported by the host and is the new
  frontier.

## 2026-08-31 — S3K driver projection advances from the 68k bootstrap to Z80 boot at tick 13

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the projection fix lands with this entry).
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged committed reference; its CPU-tagged full-bus rows and terminal
  digest remain intact).
- **Command:** the entry-of-record `S3kAudioParityTool compare` invocation
  below with the absolute SHA-1-verified locked-on ROM; `--ticks 13` was run
  separately as the green-prefix gate.
- **Result:** ticks **0-12 MATCH**. First divergence tick **13**,
  `EVENT_MISSING`, event 0: reference Z80 YM part II `82h = FFh`, engine
  missing (first-divergence-only comparator).
- **Notes:** the reader now validates each captured write's observer
  `source_cpu` and projects only CPU 1 (Z80) into this driver oracle. CPU 2
  (68k) writes, including tick 3's `PSGInitValues` `9F BF DF FF`, remain in
  the digest-authenticated fixture but are outside comparison. Tick 13 is the
  genuine `zInitAudioDriver -> zStopAllSound` burst (S3K spec §1 boot and §5,
  `D:523-551,2460-2521`); it spans movie frames 13-14 before ordinary `zVInt`
  service. The current frame-shaped engine host has no source-owned driver
  installation/boot-service boundary. Emitting it at a fixture frame or
  triggering it from comparison writes would violate the no-trace-hydration
  rule, so this frontier requires a service-shaped oracle/host boundary rather
  than a production sequencer patch.

## 2026-08-31 — S2 EHZ music prefix reaches the first SFX override at tick 210

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the fixes land with this entry).
- **Fixture:** `src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz`
  (unchanged committed reference).
- **Command:** `S2AudioOracleTool --fixture <committed fixture> --rom
  <absolute SHA-1-verified S2 REV01 ROM>` on the compiled worktree classes.
- **Result:** DIVERGENCE at tick **210** (movie row **10412**), `writes[0]`:
  reference PSG `0x9A`, engine YM part II `A4h = 33h`; **303 of 698 ticks
  divergent**. Ticks 0-209 match.
- **Notes:** the comparator now pairs each tick with the kind-9 service
  **completion** frame, not its begin frame; update 0 begins at row 10201 and
  completes at row 10202, so the old FM2 `1424h/1428h` frontier was a
  mid-track-walk snapshot. Tick writes are likewise kind-9-owned only rather
  than mixing the parent V-int's multi-frame load burst into its child update.
  The engine fixes exposed along the prefix are source-owned S2 semantics:
  resting PSG envelopes advance without writing, FM note preparation does not
  repeat pan, `zSetChanVol` rewrites all four TLs, E7 persists on DAC while
  FM/PSG clear it at expiry, FM no-attack still keys on, and note-start
  modulation follows key-on without forcing a write. At tick 210 the
  reference FM4 has override bit 2 set by an SFX and suppresses its modulation
  write (`sd:1088-1092`); this music-only engine capture deliberately injects
  no SFX. The next S2 frontier is therefore the declared SFX/admission tier.

## 2026-08-31 — S3K tick-3 attribution retracted: this is the 68k PSG bootstrap, not `zStopAllSound`

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` at `a390f1649` plus this documentation
  correction.
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged committed reference).
- **Command:** the entry-of-record `S3kAudioParityTool compare` invocation
  below, plus direct inspection of the fixture's first non-empty write rows
  against `skdisasm/sonic3k.asm:175-184,260` and
  `Sound/Z80 Sound Driver.asm` `zInitAudioDriver` / `zStopAllSound`.
- **Result:** the comparator remains red at tick 3, event 0, PSG `0x9F`
  missing. **No production fix is valid at that frontier.** The reference row
  contains exactly `0x9F,0xBF,0xDF,0xFF`, matching the 68k power-on
  `PSGInitValues` loop before the SMPS driver is installed. The actual Z80
  initialization burst first appears at tick 13 and continues at tick 14 with
  the source-specified FM silence/SSG-EG/PSG/DAC/FM3 sequence.
- **Notes:** the 2026-08-30 entry's claim that tick 3 was
  `zInitAudioDriver -> zStopAllSound` is retracted. `S3kOpenGgfAudioCapture`
  is a driver/request host and has no 68k power-on execution boundary; adding
  the four writes on oracle tick 3 would key engine behavior to a fixture
  frame, while emitting them from `SmpsDriver` startup would assign 68k-owned
  work to the wrong subsystem. This is a host-capture scope gap. The committed
  fixture and comparator remain unchanged, so a later oracle revision must
  establish a source-owned 68k bootstrap boundary before it can expose the
  first driver-owned divergence.

## 2026-08-31 — S1 SFX frontier advances from admission tick 351 to release tick 377

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the fix lands in the same commit; base
  `fcc190d5f`).
- **Fixture:** `src/test/resources/audio/parity/s1/s1-soundtest-sfx-reference.v1.jsonl.gz`
  (unchanged committed reference).
- **Command:** `S1AudioParityTool capture --capture sfx` followed by `compare`
  against the committed fixture in `<external run root>/sdre2-s1-sfx`, with
  the absolute SHA-1-verified S1 REV01 ROM path.
- **Result:** **MISMATCH**, first divergence tick **377**, event 3: the engine
  emits an extra PSG `0x9F` after the reference's final event. Tick 351's prior
  `event_extra` is green. The S1 GHZ music gate remains **MATCH (14,690
  ticks)** in a fresh committed-reference engine capture.
- **Notes:** `Sound_PlaySFX` (`SD:977-1087`) has no PSG1/2 takeover write; the
  typed S1 PSG takeover profile now leaves the first visible write to the SFX
  track while legacy profiles retain the existing synthetic silence. The new
  tick-377 frontier is release-shaped: after both streams write PSG `0xB3`,
  `0xF7`, `0x9F`, the engine writes a second `0x9F` and begins immediate music
  restoration, while the reference stops. That belongs to the profile-shaped
  stop/restore gap (§6.2), not admission.

## 2026-08-31 — Cadence 2–4 land without moving the three live oracle frontiers

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the fix lands in the same commit; base
  `f07e45c44`).
- **Fixtures:** the unchanged committed S1 GHZ, S2 EHZ reload-window, and S3K
  AIZ1 intro references named in their entries below.
- **Commands:** S1 engine capture and comparison against the committed fixture
  through `S1AudioParityTool capture` / `compare` in
  `<external run root>/sdre2-s1-committed`; the entry-of-record
  S2 and S3K Java invocations below with absolute, SHA-1-verified ROM paths.
- **Results:** S1 music **MATCH (14,690 ticks)**. S2 remains at tick 0,
  `track.FM2.dataPointer`, expected `0x1424`, actual `0x1428`, **669 of 698
  ticks divergent**. S3K remains at tick 3, `EVENT_MISSING`, event 0, reference
  PSG `0x9F`, engine missing.
- **Notes:** live presentation is now outer-frame locked; S2 PAL uses the
  driver-global 6-per-5 music cadence while SFX stays single-service; S3K PAL
  repeats the complete driver pass 7-per-6 and the shared speed tail produces
  the cited 5-per-4 vector. These branches are absent from the three current
  oracle windows, so unchanged frontiers are expected; the cadence vectors are
  pinned by `TestSmpsSequencerCadence`. A fresh BizHawk S1 reference recapture
  was attempted through `run_s1_audio_parity.sh` but the local host had no X
  display; that capture failure is not reported as a parity result. The
  committed-reference engine comparison above is the recorded gate.

## 2026-08-31 — S2 frontier: tick-0 `tempoTimeout` green after the delay-frame cadence fix

- **Worktree/branch:** `.worktrees/sdre2-cadence`, `feature/ai-sdre2-cadence`
  (the fix lands in the same commit as this entry; base
  `feature/ai-sound-driver-re` `fc3e70c95`).
- **Fixture:** `src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz`
  (unchanged committed reference).
- **Command:** the entry-of-record S2 invocation (`S2AudioOracleTool --fixture
  <committed fixture> --rom <s2.gen>` on the compiled worktree classes, as in
  the 2026-08-30 first-measurement entry).
- **Result:** DIVERGENCE — first divergence still tick 0 (movie row 10201),
  now `track.FM2.dataPointer` expected `0x1424` actual `0x1428`;
  **698 → 669 of 698 ticks divergent**. The previous frontier field,
  tick-0 `global.tempoTimeout` (`0x3c` vs `0x0`), is green: the sequencer now
  seeds its accumulator at song load (`sd:1820-1822`) and runs `TempoWait` on
  the first update (`sd:545-551`), and a no-carry frame pre-increments every
  music slot's `DurationTimeout` while the track walk still runs
  (`sd:596-619`, gap analysis §1.2 #2).
- **Notes:** the exposed `dataPointer` divergence is a load/track-walk stream
  position gap (engine 4 bytes ahead on FM2 at the first update), not a
  cadence field — it belongs to a music-load/note-parse lane. Cross-checks at
  the same commit: S1 GHZ music oracle **MATCH (14,690 ticks)** held; S1
  sound-test SFX frontier unchanged (tick 351 `event_extra`); S3K frontier
  unchanged (tick 3 boot-silence `EVENT_MISSING`, unreachable by cadence).

## 2026-08-30 — S1 sound-test SFX oracle first light: red at the first SFX admission

- **Worktree/branch:** `.worktrees/sdre-oracle-s1`, `feature/ai-sdre-oracle-s1` (commit recorded with the fixture landing).
- **Fixture:** `src/test/resources/audio/parity/s1/s1-soundtest-sfx-reference.v1.jsonl.gz`
  (movie `s1-soundtest-sfx.bk2`: the pinned GHZ sound-test prefix, then eight normal SFX
  `$A0 $A4 $A6 $AA $B5 $C6 $CC $CF`, dispatched at tick ordinals 351, 525, 689,
  863, 1072, 1311, 1495 and 1664; 1,967 ticks, epoch at GHZ music acceptance).
  Reference recorded twice on BizHawk 2.11/GPGX (debug and production probe modes)
  with byte-identical emitted captures.
- **Command:**
  `tools/audio/run_s1_audio_parity.sh --mode sfx --output-root <external dir>`
  (or the recorded `S1AudioParityTool capture --capture sfx` + `compare` pair against
  the committed fixture).
- **Result:** **MISMATCH** (exit 3). First divergence: tick **351** — precisely the
  invocation whose recorded `dispatches` is `[0xA0]`, the first SFX (jump) — event
  index 2, kind `event_extra`: the engine emits `psg 0x9F` (PSG1 silence) that the ROM
  does not. Reference events at that tick run `psg 0xB3, psg 0xF6, psg 0x80, psg 0x14, …`;
  the engine inserts a PSG1 attenuation-off silence between index 1 and the ROM's
  frequency latch. There is no error count beyond the first divergence by design.
- **Notes:** the extra write comes from the engine's SFX channel-steal path
  (`SmpsDriver.writePsg` lock acquisition calls `silencePsgChannel` when an SFX takes a
  channel from music), while S1 `Sound_PlaySFX` only marks the music track overridden and
  writes nothing at admission (S1 routine map §6). Matches gap analysis §1.2 #6
  (override/restore burst shape is profile work). State and all 350 earlier ticks
  (music-only, including tick 0's music-load burst) match. Fix belongs to an
  implementation lane, not this oracle lane.

## 2026-08-30 — S1 GHZ music oracle re-established from a committed fixture: MATCH

- **Worktree/branch:** `.worktrees/sdre-oracle-s1`, `feature/ai-sdre-oracle-s1`.
- **Fixture:** `src/test/resources/audio/parity/s1/s1-soundtest-ghz-reference.v1.jsonl.gz`
  (movie `s1-soundtest-ghz.bk2`; 14,690 ticks to proven recurrence, cycle start 5,473,
  period 4,608). Uncompressed SHA-256 `5941958c…` — byte-identical to the 2026-08-09 and
  2026-08-30 audit captures, and to a fresh capture recorded this session with the
  consumer-side domain-fixed probe (`tools/audio/probes/s1_audio_driver_parity_probe.lua`).
- **Command:** `S1AudioParityTool capture` + `compare` against the committed `.gz` fixture
  (external run root; also reachable via `tools/audio/run_s1_audio_parity.sh --mode music
  --output-root <external dir>`).
- **Result:** **`S1 audio parity: MATCH (14690 ticks)`**, exit 0.
- **Break-it-on-purpose (comparator proof it actually compares):** two independent
  corruption experiments were run (this lane's, and the concurrent writer's — see the
  validation record's provenance note); all four outcomes were first divergences with
  exit 3:
  - fixture byte, run A: tick 5000 `tempoTimeout` 3→4 → `global_state_mismatch,
    tick 5000, field tempo_timeout, reference 4, openggf 3`;
  - engine write, run A: tick 3001 event 0 `ym2612 p0 reg 0xA4` 34→35 →
    `event_value_different, tick 3001, event 0`;
  - fixture byte, run B (concurrent writer's, per commit 0c1d0580e; not re-run by
    this lane): tick 5000 DAC `duration` 11→12 → `track_state_mismatch, tick 5000,
    role DAC, field duration`;
  - engine write, run B (same provenance): tick 7000 event 0 `ym2612 p0 reg 0x28`
    1→0 → `event_value_different, tick 7000, event 0`.

## 2026-08-30 — S2 driver oracle: first measurement (expected red)

- **Worktree:** `.worktrees/sdre-oracle-s2`, branch `feature/ai-sdre-oracle-s2`
  (commit recorded in the entry's own commit).
- **Oracle:** `S2AudioOracleComparator` against
  `src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz`
  (movie rows 10150-10899 of the pinned S2 complete-emeralds movie; EHZ music
  reload anchor at row 10195; recorded by the TraceChaser headless harness
  with the patch-0001 GPGX audio observer — see the fixture's metadata JSON
  and `docs/architecture/research/audio/2026-08-30-s2-driver-oracle.md`).
- **Command:**

  ```bash
  mvn -q -Dmse=off compile dependency:build-classpath -Dmdep.outputFile=target/oracle-classpath.txt
  java -cp "target/classes:$(cat target/oracle-classpath.txt)" \
    com.openggf.tools.audio.parity.s2.S2AudioOracleTool \
    --fixture "$PWD/src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz" \
    --rom "$PWD/s2.gen"
  ```

- **Result:** DIVERGENCE — 698 of 698 recovered driver-update ticks divergent.
- **First divergence:** tick 0 (movie row 10201), `global.tempoTimeout`,
  expected `0x3c`, actual `0x0`.
- **Reading:** the ROM seeds `TempoTimeout = CurrentTempo = 9Eh` at song load
  (`s2.sounddriver.asm:1820-1822`) and runs `TempoWait` at the top of the
  first `zUpdateMusic` (`sd:545-551`): `9Eh + 9Eh = 13Ch` → carry → `3Ch`.
  The engine's first update leaves its tempo accumulator at 0 — neither the
  load-time seed nor the first-update accumulation is modelled
  (gap analysis §1.2 #1/#2; behaviour spec §3.1). Every subsequent tick also
  diverges (cadence differences cascade through durations, envelope cursors
  and the write stream), so 698/698 is the honest count, and the tick-0 field
  is the frontier to move first. Two measurement facts recovered from the
  reference along the way, both now encoded in the comparator's tick
  recovery: the Saxman EHZ load masks interrupts across movie rows
  10195-10200 (those frames hold a half-initialised driver image and no
  `zUpdateMusic` service), and the caught-up Z80 misses row 10202's V-int
  entirely — one oracle tick is therefore one completed `zUpdateMusic`
  service from the observer's service stream, not one video frame.
- **Break-it evidence** (`TestS2AudioOracleComparator`, outputs from the
  evidence run at this commit):
  - untampered self-comparison: `S2 driver oracle: MATCH (698 ticks)`;
  - reference byte corrupted (tick 40, `FM1.DurationTimeout ^ 0x55`):
    `S2 driver oracle: DIVERGENCE at tick 40 (movie row 10242), field
    track.FM1.durationTimeout: … expected=0x41 actual=0x14 [1 of 698 ticks
    divergent]`;
  - engine write corrupted (tick 20, `writes[0] value ^ 0x40`):
    `S2 driver oracle: DIVERGENCE at tick 20 (movie row 10222), field
    writes[0]: … expected=ym0[0x28]=0x0 actual=ym0[0x28]=0x40 [1 of 698
    ticks divergent]`.
## 2026-08-30 - S3K oracle first frontier: boot silence burst (tick 3) — attribution retracted

- Worktree `.worktrees/sdre-oracle-s3k`, branch `feature/ai-sdre-oracle-s3k`
  (fixture, capture tooling and comparator land in the same commit as this
  entry; engine base `f087b8947` + sdre-gaps/spec-s3k docs merges).
- Fixture: `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (identity in `s3k-aiz1-intro-metadata-v1.json`): 5,400 driver invocations
  (movie frames 0-5399 of the committed `s3k-complete-sonic-tails.bk2` from
  power-on), 725,898 decoded YM/PSG writes; covers boot, SEGA chant, title
  music (`25h`), Knuckles intro theme (`1Fh`), AIZ1 music (`01h`, ~54 s) and
  ten-plus distinct gameplay SFX. Captured deterministically (two runs,
  byte-identical) by `tools/audio/run_s3k_audio_oracle_reference.sh` with the
  lock-verified patch-0001 observer core
  (`e65315743a6a1228…`, `artifact-lock.json` identity).
- Command:
  `java -cp "target/classes:$(cat target/s3k-oracle.classpath)"
  com.openggf.tools.audio.parity.s3k.S3kAudioParityTool compare
  --reference src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz
  --rom <locked-on s3k.gen>`
- Result: **red**, as expected for the first run. **Superseded attribution:**
  the 2026-08-31 correction above proves this row is the 68k power-on PSG
  initialization loop, not the Z80 driver's initialization burst.
  First divergence: **tick 3, `EVENT_MISSING`, event 0** — the reference
  emits PSG `9Fh` as the first of the 68k bootstrap's four
  `PSGInitValues`; the driver-only engine host emits nothing. Error count:
  first divergence only (comparator stops); ticks 0-2 of the same run are
  green (`MATCH (3 ticks)` with `--ticks 3`).
- Broken on purpose before trusting the comparison (project rule): a
  corrupted `zCurrentTempo` byte in a temp copy (terminal digest recomputed)
  reports `GLOBAL_STATE_MISMATCH` at its exact tick with expected/actual
  (`64` vs `0`, exit 3); the same corruption without the digest fix is
  refused as `terminal body digest mismatch` (exit 4); a corrupted engine
  write is reported at its tick/event index by
  `TestS3kAudioParityComparator.corruptedWriteIsReportedAtItsEventIndex`.
- Unmodelled requests this run (logged by the capture host, not silently
  skipped): `E1h` fade-out (7 ticks), `FFh` SEGA chant (1 tick).
