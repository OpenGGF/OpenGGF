# Sonic 1 complete-run audio parity implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a deterministic, naturally replayed S1 all-emeralds audio capture that matches REV01 byte-for-byte across requests, priority decisions, driver state, and YM2612/PSG transactions, including exact 1-up takeover and restore.

**Architecture:** Extend the verified S1 68K observer to the complete gameplay epoch and adapt it to the shared chunk schema. Correct OpenGGF at the owning boundaries: a source-accurate request mailbox/service cadence and a single-chip temporary-music save/restore path.

**Tech Stack:** Java 21, JUnit Jupiter, Lua 5.1/BizHawk 2.11, GPGX, Bash, shared complete-run capture infrastructure.

## Global Constraints

- Complete the shared plan before this plan.
- Use S1 World REV01 only: SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`, CRC32 `AFE05EEE`.
- Pin the committed all-emeralds BK2 SHA-256 `f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b` and 225,101 rows.
- Compare `[860,225101)` and retain all transition and terminal-tail rows.
- Model `FixBugs=0`; do not add chip resets or reorder ports to hide differences.
- The real GHZ1 frames 3698–3910 are mandatory 1-up acceptance evidence.
- Preserve the focused GHZ sound-test and GHZ1 semantic commands as regressions.
- Do not merge or push; humans must listen first.

---

### Task 1: S1 complete-run fixture and state profile

**Files:**
- Create: `src/main/java/com/openggf/tools/audio/completerun/s1/S1CompleteRunAudioProfile.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/s1/S1CompleteRunStateNormalizer.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/s1/TestS1CompleteRunAudioFixture.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/s1/TestS1CompleteRunStateNormalizer.java`

**Interfaces:**
- Produces: profile id `s1_rev01_complete_emeralds.v1` and strict S1 live/saved-state inventory.
- Consumes: shared profile/model and existing `S1AudioStateNormalizer` field knowledge without importing GHZ recurrence constants.

- [ ] **Step 1: Write failing fixture identity tests**

Assert exact ROM hashes, BK2 hash/row count, manifest hash, 34 monotonic
segments, six special stages, epoch `[860,225101)`, 208,586 segment rows, and
15,655 gap/tail rows. Assert the last segment end is 214,158 and the terminal
tail is 10,943 rows.

- [ ] **Step 2: Write failing state vectors**

Build one active-music/SFX state and one 1-up saved-state vector. Require roles
`DAC,FM1,FM2,FM3,FM4,FM5,FM6,PSG1,PSG2,PSG3` in fixed order, pointer fields as
asset key plus relative cursor, three queue slots, global priority, tempo/fade,
`f_1up_playing`, and the complete saved music state. Mutate each field once and
assert canonical bytes change.

- [ ] **Step 3: Run RED**

```bash
mvn -Dmse=off -Dtest='com.openggf.tools.audio.completerun.s1.TestS1CompleteRunAudioFixture,com.openggf.tools.audio.completerun.s1.TestS1CompleteRunStateNormalizer' test
```

- [ ] **Step 4: Implement profile and normalizer**

Register native S1 sound IDs without remapping. Normalize ROM pointers relative
to their validated ROM-backed song/SFX asset and normalize engine positions
from the same loader coordinates. Inactive tracks emit only role/hardware and
`active=false`; saved inactive capacity never leaks stale bytes.

- [ ] **Step 5: Run GREEN and shared schema tests**

Run Step 3 plus `TestCompleteRunAudioTrace`. Expected: all pass.

- [ ] **Step 6: Commit the S1 profile**

```bash
git add src/main/java/com/openggf/tools/audio/completerun/s1 \
        src/test/java/com/openggf/tools/audio/completerun/s1
git commit -m "feat(tools): define S1 complete-run audio profile"
```

### Task 2: Complete-run S1 reference observer

**Files:**
- Create: `tools/bizhawk/audio/s1_complete_run_audio_contract.lua`
- Create: `tools/bizhawk/audio/s1_complete_run_audio_contract_test.lua`
- Create: `tools/bizhawk/probes/s1_complete_run_audio_probe.lua`
- Create: `src/test/java/com/openggf/tools/audio/completerun/s1/TestS1CompleteRunLuaContract.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/s1/TestS1CompleteRunProbeContract.java`

**Interfaces:**
- Produces: raw canonical staging records for frames 860–225100.
- Consumes: verified `probe_runtime.lua` callbacks and the shared Java publisher.

- [ ] **Step 1: Write RED Lua lifecycle and priority cases**

The pure Lua harness must cover queue writes consumed on a later service,
duplicate IDs, deferred queue0, lower/equal priority, normal/special SFX,
stop-all, death/restart, act transitions, multiple services in one frame, zero
services, and 1-up save/block/restore. Encode the real 3698/3699/3702/3910
oracle as a contract fixture.

```lua
local oneup = contract.newPriorityModel()
oneup:request(115, 0x88, "music")
local admitted = oneup:service()
assert(#admitted.decisions == 1 and admitted.decisions[1].accepted)
assert(oneup:request(116, 0xB5, "sfx").blocked_by == "one_up")
```

- [ ] **Step 2: Run RED**

```bash
lua tools/bizhawk/audio/s1_complete_run_audio_contract_test.lua
mvn -Dmse=off -Dtest='com.openggf.tools.audio.completerun.s1.TestS1CompleteRunLuaContract,com.openggf.tools.audio.completerun.s1.TestS1CompleteRunProbeContract' test
```

- [ ] **Step 3: Implement the source-derived contract**

Port the proven request correlation and YM decoder, but remove GHZ `$81`
arming, cycle convergence, one-tick-per-frame, and `[860,4975)` assumptions.
Explicitly model the abnormal-return sites already verified by the GHZ1 probe.
Every hook has exact REV01 opcode bytes and a source label.

- [ ] **Step 4: Implement the read-only full-run probe**

Arm only when row 860 is about to be consumed, sample the baseline first, and
close at row 225100. Record requests at queue writes, decisions at actual
dispatch, complete service state/writes at lifecycle close, and transition-gap
frames even when they contain no service. Reject unbracketed post-arm closes,
callback contamination, speed-up/fade lifecycle contradictions, missing rows,
and missing terminal.

- [ ] **Step 5: Run Lua and Java GREEN**

Run Step 2. Expected: pure Lua and all probe shape/opcode tests pass.

- [ ] **Step 6: Run two reference captures and byte gate**

Invoke the generic runner's reference producer twice with the pinned ROM/BK2.
Require identical capture manifests and chunks. Check the real 1-up frames with
the strict Java reader, not `jq` alone.

- [ ] **Step 7: Commit the S1 observer**

```bash
git add tools/bizhawk/audio/s1_complete_run_audio_contract.lua \
        tools/bizhawk/audio/s1_complete_run_audio_contract_test.lua \
        tools/bizhawk/probes/s1_complete_run_audio_probe.lua \
        src/test/java/com/openggf/tools/audio/completerun/s1
git commit -m "feat(tools): observe complete S1 gameplay audio"
```

### Task 3: Source-accurate S1 request mailbox and service cadence

**Files:**
- Create: `src/main/java/com/openggf/game/sonic1/audio/Sonic1SoundMailbox.java`
- Modify: `src/main/java/com/openggf/game/sonic1/audio/Sonic1AudioProfile.java`
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`
- Modify: `src/main/java/com/openggf/audio/runtime/AudioFrameClock.java`
- Create: `src/test/java/com/openggf/game/sonic1/audio/TestSonic1SoundMailbox.java`
- Modify: `src/test/java/com/openggf/tools/audio/timeline/TestS1Ghz1OpenGgfAudioTimelineReduction.java`

**Interfaces:**
- Produces: a game-profile-owned three-slot mailbox whose requests are consumed at the S1 driver service boundary.
- Consumes: native IDs and existing high-level request observation.

- [ ] **Step 1: Write failing mailbox tests**

Cover source-order slots, deferred queue0, duplicate IDs retaining request
ordinal, StopAllSound queue clear, later service admission, and a frame-958
request admitted at 959. Assert the request observer still fires at submission
while the admission observer fires only at consumption.

- [ ] **Step 2: Run RED**

```bash
mvn -Dmse=off -Dtest='com.openggf.game.sonic1.audio.TestSonic1SoundMailbox,com.openggf.tools.audio.timeline.TestS1Ghz1OpenGgfAudioTimelineReduction' test
```

- [ ] **Step 3: Implement the mailbox at the game-owned boundary**

`Sonic1SoundMailbox` stores immutable request tokens in three slots and exposes
only `submit` and `consumeService`. `GameAudioProfile` gains a typed optional
mailbox/service policy; shared `AudioManager` contains no S1 check. Do not use
BK2 frame numbers or zones.

- [ ] **Step 4: Run GREEN and the real GHZ1 timeline**

Run Step 2, then the pinned GHZ1 four-capture command. The first mismatch must
move beyond the old same-frame frame-958 admission frontier without changing
the raw request frame.

- [ ] **Step 5: Commit mailbox timing**

```bash
git add src/main/java/com/openggf/game/sonic1/audio/Sonic1SoundMailbox.java \
        src/main/java/com/openggf/game/sonic1/audio/Sonic1AudioProfile.java \
        src/main/java/com/openggf/audio/AudioManager.java \
        src/main/java/com/openggf/audio/runtime/AudioFrameClock.java \
        src/test/java/com/openggf/game/sonic1/audio/TestSonic1SoundMailbox.java \
        src/test/java/com/openggf/tools/audio/timeline/TestS1Ghz1OpenGgfAudioTimelineReduction.java
git commit -m "fix(audio): consume S1 requests at driver service boundaries"
```

### Task 4: Single-chip S1 extra-life save and restore

**Files:**
- Create: `src/main/java/com/openggf/audio/driver/TemporaryMusicState.java`
- Modify: `src/main/java/com/openggf/audio/driver/SmpsDriver.java`
- Modify: `src/main/java/com/openggf/audio/AbstractSmpsAudioBackend.java`
- Modify: `src/main/java/com/openggf/game/sonic1/audio/Sonic1AudioProfile.java`
- Create: `src/test/java/com/openggf/audio/driver/TestS1TemporaryMusicState.java`
- Create: `src/test/java/com/openggf/game/sonic1/audio/TestSonic1ExtraLifePriority.java`

**Interfaces:**
- Produces: `SmpsDriver.saveTemporaryMusic(...)`, `startTemporaryMusic(...)`, and `restoreTemporaryMusic(...)` on the same synthesizer/chip instance.
- Replaces: S1's whole-driver/chip `MusicState` swap for `$88`; other profiles remain unchanged until their plans migrate them.

- [ ] **Step 1: Write failing one-chip identity test**

Start `$87` plus FM/PSG SFX, retain `driver.synthesizerForTesting()` identity,
start `$88`, request SFX while blocked, finish the jingle, and restore. Assert
the exact same driver and synth objects throughout, all SFX removed, blocked
requests rejected, and saved music tracks restored.

- [ ] **Step 2: Write failing ordered-write regression**

Use `ChipWriteObserver` to assert the ROM sequence: SFX stop/key-off, jingle
voice/note writes on the same chip, restore active FM voices, PSG note-offs,
then 40-step fade. Assert there is no constructor silence/reset burst and no
`refreshAllVoices()` approximation. Include the `FixBugs=0` FM6/DAC behavior.

- [ ] **Step 3: Run RED**

```bash
mvn -Dmse=off -Dtest='com.openggf.audio.driver.TestS1TemporaryMusicState,com.openggf.game.sonic1.audio.TestSonic1ExtraLifePriority' test
```

- [ ] **Step 4: Implement temporary music inside the live driver**

Snapshot only the source-equivalent global/music-track state and ROM-backed
dependencies. Remove active SFX, retain one `VirtualSynthesizer`, install the
jingle music sequencer into that driver, and restore the saved music sequencer
state on the coord-flag boundary. Keep rewind snapshots deterministic and
capture the saved temporary state explicitly.

- [ ] **Step 5: Run GREEN and snapshot/observer regressions**

```bash
mvn -Dmse=off -Dtest='com.openggf.audio.driver.TestS1TemporaryMusicState,com.openggf.game.sonic1.audio.TestSonic1ExtraLifePriority,com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.audio.synth.TestChipWriteObserver,com.openggf.tools.audio.timeline.TestS1Ghz1OpenGgfAudioTimelineReduction' test
```

- [ ] **Step 6: Commit the S1 temporary-music path**

```bash
git add src/main/java/com/openggf/audio/driver/TemporaryMusicState.java \
        src/main/java/com/openggf/audio/driver/SmpsDriver.java \
        src/main/java/com/openggf/audio/AbstractSmpsAudioBackend.java \
        src/main/java/com/openggf/game/sonic1/audio/Sonic1AudioProfile.java \
        src/test/java/com/openggf/audio/driver/TestS1TemporaryMusicState.java \
        src/test/java/com/openggf/game/sonic1/audio/TestSonic1ExtraLifePriority.java
git commit -m "fix(audio): preserve one chip across S1 extra-life music"
```

### Task 5: Natural S1 complete-run OpenGGF producer

**Files:**
- Create: `src/test/java/com/openggf/tools/audio/completerun/s1/S1CompleteRunOpenGgfCapture.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/s1/TestS1CompleteRunOpenGgfCapture.java`
- Create: `tools/audio/run_s1_complete_audio_parity.sh`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioProfiles.java`

**Interfaces:**
- Produces: validated S1 OpenGGF capture directory from only run path, ROM property, and output path.
- Consumes: natural complete-run replay, Task 1 normalizer, and pre-construction observers.

- [ ] **Step 1: Write failing producer isolation tests**

Assert constructor/method signatures cannot accept a reference path, reader,
expected event callback, or request sidecar. Use a compact synthetic run to
assert baseline, gap rows, zero/multi-service frames, terminal counts, and
observer removal after capture.

- [ ] **Step 2: Run RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.tools.audio.completerun.s1.TestS1CompleteRunOpenGgfCapture test
```

- [ ] **Step 3: Implement the capture reducer**

Correlate raw requests by stable ordinal, consume actual backend decisions,
open/close driver-service records from the service observer, append chip writes
to the currently open service, and normalize state at service end. Reject
orphan writes, decisions without requests, self-displacement, duplicate roles,
and writes outside service unless the S1 profile declares a lifecycle site.

- [ ] **Step 4: Run the focused real GHZ1 interval**

Capture through frame 4974 and assert the 3698–3910 oracle exactly before
attempting the whole run. Expected: no false SFX admission while `$88` is active
and all six roles restore to `$87`.

- [ ] **Step 5: Commit the S1 producer and runner**

```bash
git add src/test/java/com/openggf/tools/audio/completerun/s1 \
        src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioProfiles.java \
        tools/audio/run_s1_complete_audio_parity.sh
git commit -m "feat(tools): capture natural S1 complete-run audio"
```

### Task 6: Drive the complete S1 frontier to byte parity

**Files:**
- Modify only source owners identified by each first mismatch.
- Modify: `CHANGELOG.md`
- Create: `docs/architecture/research/audio/2026-08-10-s1-complete-run-audio-parity-result.md`
- Create: `docs/architecture/validation/audio/2026-08-10-s1-complete-run-audio-parity-validation.md`

**Interfaces:**
- Produces: two deterministic reference captures, two deterministic OpenGGF captures, and `MATCH` for `[860,225101)`.

- [ ] **Step 1: Run the complete four-capture command**

```bash
tools/audio/run_s1_complete_audio_parity.sh --rom "$S1_ROM" \
    --bizhawk-home docs/BizHawk-2.11-linux-x64
```

Expected initially: exit 3 with a typed first mismatch, or exit 4 at the first
natural run frontier. Preserve the fresh ignored run directory.

- [ ] **Step 2: Resolve each mismatch source-first**

For every frontier, cite the exact S1 disassembly routine and `FixBugs=0` path,
write a minimal failing unit/fixture regression, reproduce RED, implement at
the owning game/profile/driver boundary, run GREEN, then rerun from the start.
Never realign, skip a row/service, weaken state inventory, or exclude a write.

- [ ] **Step 3: Require explicit real-run evidence**

The final capture must contain all 224,241 rows, all 34 segments, six special
stages, every gap and the 10,943-row terminal tail. Assert at least one lower
priority rejection, equal replacement, music/SFX contention, SFX/SFX
contention, the frame-3698 1-up, blocked SFX, exact frame-3910 restore, speed
change, drowning/act-clear/emerald cues, DAC writes, ending, and credits.

- [ ] **Step 4: Run focused and full verification on JDK 21**

```bash
mvn -Dmse=off -Dsonic1.rom.path="$S1_ROM" \
  -Dtest='com.openggf.tools.audio.completerun.s1.TestS1CompleteRunAudioFixture,com.openggf.tools.audio.completerun.s1.TestS1CompleteRunStateNormalizer,com.openggf.tools.audio.completerun.s1.TestS1CompleteRunLuaContract,com.openggf.tools.audio.completerun.s1.TestS1CompleteRunProbeContract,com.openggf.tools.audio.completerun.s1.TestS1CompleteRunOpenGgfCapture,com.openggf.tools.audio.completerun.TestCompleteRunAudioTrace,com.openggf.tools.audio.completerun.TestCompleteRunAudioCaptureStore,com.openggf.tools.audio.completerun.TestCompleteRunAudioComparator,com.openggf.tools.audio.parity.TestAudioParityComparator,com.openggf.tools.audio.timeline.TestS1GameplayAudioTimelineComparator,com.openggf.game.sonic1.audio.TestSonic1SoundMailbox,com.openggf.game.sonic1.audio.TestSonic1ExtraLifePriority,com.openggf.audio.driver.TestS1TemporaryMusicState,com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.audio.synth.TestChipWriteObserver,com.openggf.tests.trace.runs.TestS1CompleteEmeraldVisualRun' test
mvn -Pci -Dsonic1.rom.path="$S1_ROM" -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" test
```

Compare the full-suite exact failure/error ID set with the recorded baseline;
no formerly green test may regress.

- [ ] **Step 5: Publish compact evidence and commit**

Record hashes, sizes/counts, interval, 1-up evidence, comparison result, test
commands, baseline comparison, and listening checklist without reconstructive
event payloads.

```bash
git add CHANGELOG.md docs/architecture/research/audio/2026-08-10-s1-complete-run-audio-parity-result.md \
        docs/architecture/validation/audio/2026-08-10-s1-complete-run-audio-parity-validation.md
git commit -m "fix(audio): match the complete S1 emerald audio run"
```
