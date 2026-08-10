# S2/S3K FM Voice Write Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproduce the shipped S2 and S3K FM voice-loading register streams without changing the corrected YM2612 hardware mapping.

**Architecture:** A typed `FmVoiceWriteProfile` in `SmpsSequencerConfig` selects the exact driver-owned register traversal. `SmpsSequencer` emits raw writes for ROM-driven voices, while each data loader preserves its game's raw voice bytes and the YM2612 remains format-agnostic.

**Tech Stack:** Java 21, JUnit Jupiter, Maven, Sonic 2/Sonic 3&K disassemblies, existing chip-write observer.

## Global Constraints

- Treat the shipped `FixBugs = 0` / `fix_sndbugs = 0` driver paths as authoritative.
- Do not change YM2612 raw-register slot mapping, key-bit mapping, algorithm routing, or chip port ordering.
- Cover music plus local, bank-shared, and global SFX voice sources.
- Keep runtime assets ROM-backed; disassemblies supply research evidence only.
- Work only on `bugfix/ai-s1-audio-parity-frontier`; do not merge or push before human testing.

---

### Task 1: Lock the driver write contracts with failing tests

**Files:**
- Create: `src/test/java/com/openggf/audio/smps/TestSmpsFmVoiceWriteProfiles.java`
- Modify: `src/test/java/com/openggf/tests/TestSonic3kVoiceData.java`

**Interfaces:**
- Consumes: existing `ChipWriteObserver`, `SmpsSequencer`, S2/S3K data sources.
- Produces: literal expected YM write streams for S2 and S3K.

- [ ] Add an S2 music fixture whose five operator groups and TL group contain four distinct bytes. Assert B0, parameter, B4, and TL order, with no synthetic `0x28` or `0x90..0x9C` writes.
- [ ] Add an S2 local-SFX fixture using the same distinct vector and assert the identical profile.
- [ ] Add S3K music, local-SFX, and global-voice fixtures. Assert B4 precedes B0 and each source byte follows the literal `30,38,34,3C` family.
- [ ] Assert S3K `getVoice()` returns the raw 25 ROM bytes, so a loader-side middle swap fails visibly.
- [ ] Run `mvn -Dmse=off -Dtest=com.openggf.audio.smps.TestSmpsFmVoiceWriteProfiles,com.openggf.tests.TestSonic3kVoiceData test` and verify failures identify the old middle-slot permutation/key-off behavior.

### Task 2: Add typed profiles and exact runtime writers

**Files:**
- Modify: `src/main/java/com/openggf/audio/smps/SmpsSequencerConfig.java`
- Modify: `src/main/java/com/openggf/game/sonic1/audio/Sonic1SmpsSequencerConfig.java`
- Modify: `src/main/java/com/openggf/game/sonic2/audio/Sonic2SmpsSequencerConfig.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/audio/Sonic3kSmpsSequencerConfig.java`
- Modify: `src/main/java/com/openggf/audio/smps/SmpsSequencer.java`

**Interfaces:**
- Produces: `SmpsSequencerConfig.FmVoiceWriteProfile` and `getFmVoiceWriteProfile()`.
- Consumes: raw 25-byte voice blobs and existing `Synthesizer.writeFm()`.

- [ ] Add enum values `S1_68K`, `S2_Z80`, and `S3K_Z80`; require each game config to select one explicitly.
- [ ] Extract profile dispatch in `refreshInstrument()` without altering note, contention, or snapshot state.
- [ ] Preserve the existing S1 writer unchanged behind `S1_68K`.
- [ ] Implement S2's literal `B0 -> grouped 30/34/38/3C -> B4 -> 40/44/48/4C` sequence.
- [ ] Implement S3K's literal `B4 -> B0 -> grouped 30/38/34/3C -> 40/48/44/4C` sequence and retain explicit nonzero SSG-EG restoration afterward.
- [ ] Make initial and later TL refreshes use the same profile mapping.
- [ ] Run the Task 1 tests and verify they pass.

### Task 3: Preserve raw S3K voice data across every source

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/audio/smps/Sonic3kSmpsData.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/audio/smps/Sonic3kSfxData.java`
- Modify: relevant S3K presentation/source tests if their expectations describe the obsolete normalized representation.

**Interfaces:**
- Produces: raw 25-byte ROM voices from local, bank-shared, and global lookup paths.
- Consumes: `S3K_Z80` profile in `SmpsSequencer`.

- [ ] Remove the per-group middle-byte swap from both S3K copy helpers and correct their format documentation.
- [ ] Run Task 1 plus existing S3K voice-resolution and presentation snapshot tests.
- [ ] Confirm no production code reads voice bytes from `docs/` or embeds fixture-derived assets.

### Task 4: Regression verification and delivery commit

**Files:**
- Modify: `CHANGELOG.md`
- Modify: this plan only to mark completed checkboxes if useful during execution.

**Interfaces:**
- Consumes: all preceding production and test changes.
- Produces: one reviewable branch commit with exact verification evidence.

- [ ] Add a changelog entry describing corrected S2/S3K FM operator routing and exact driver write ordering.
- [ ] Run focused JDK21 tests for the new profiles, YM2612 GPGX parity, chip observer, S2 sequencer/instrument parsing, S3K voice/coordination, and audio snapshots.
- [ ] Discover and hash the S2 REV01 and locked-on S3K ROMs; run relevant ROM-backed audio smoke tests when available.
- [ ] Run `git diff --check` and inspect the complete diff for accidental S1 or chip-core changes.
- [ ] Commit with required project trailers. Do not merge, push, or remove the worktree.
