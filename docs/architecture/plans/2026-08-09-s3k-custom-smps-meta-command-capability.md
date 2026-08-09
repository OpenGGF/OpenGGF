# S3K Custom SMPS Meta-Command Capability Boundary Plan

> **Execution scope:** publish and test the reviewed unavailable boundary. Do
> not implement `FF 01/02/03`, add a custom-loader API, or change shipped-ROM
> playback. The architecture is defined in
> `docs/architecture/designs/2026-08-09-s3k-custom-smps-meta-command-capability.md`.

**Goal:** Replace any remaining implication that operand consumption is custom
SMPS support with an explicit syntax-only contract, executable behavioral
characterization, current documentation, and exact future activation gates.

**Delivery rule:** This plan changes tests, documentation, and production
comments only. It adds no production behavior, custom-driver interface,
mutable Z80 memory, command callback, configuration flag, or public ingestion
route.

## Canonical inputs and baseline

- Base commit: the current `origin/develop` from which the isolated worktree
  was created.
- JDK: Maven must report Java 21.
- ROM: Sonic 3&K locked-on, SHA-1
  `CFBF98C36C776677290A872547AC47C53D2761D6`, CRC32 `63522553`.
- Property: `-Ds3k.rom.path=/absolute/discovered/path/to/locked-on.gen`.
- Native source: `docs/skdisasm/Sound/Z80 Sound Driver.asm`, especially
  `zExtraCoordFlagSwitchTable` and `cfMetaCF`/`cfPlaySoundByIndex`/
  `cfHaltSound`/`cfCopyData`.
- Product source: authored audio uses the streamed-audio design and new
  SMPS/VGM authoring is out of scope at
  `docs/architecture/designs/2026-07-09-mod-support-design.md:199-224`.

Before editing the test, record the existing focused result:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -v
sha1sum "/absolute/discovered/path/to/Sonic and Knuckles & Sonic 3 (W) [!].gen"
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  mvn -Dmse=off \
  -Ds3k.rom.path="/absolute/discovered/path/to/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  -Dtest=com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandOperands,com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandReachability \
  test
```

Expected baseline on this branch: seven tests, zero failures, errors, or
skips: three syntax-width tests plus four ROM reachability tests.

Also record the pre-edit architecture-guard baseline rather than assuming a
green repository-wide ratchet:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  mvn -Dmse=off \
  -Dtest=com.openggf.tests.TestArchitecturalSourceGuard \
  test
```

Exact base result at `e2aa50cd5980efc720f70c1c2a6209b2637b3042`:
69 tests, two failures, zero errors/skips. The failures are the existing
`ObjectManager.java` effective-source budget (`3036 > 2914`) and
`AbstractPlayableSprite.java` release-critical size budget (`3180 > 3161`).
The final run must reproduce these same two failures with the same measured
line counts; no new failure, changed failure, or ratchet increase is accepted.

## Task 1: Strengthen the syntax-only characterization

**Modify:**

- `src/test/java/com/openggf/game/sonic3k/audio/smps/TestSonic3kSmpsMetaCommandOperands.java`

This is characterization of the selected non-goal, not a synthetic RED for
new behavior. The current production code should remain green throughout.

1. Replace the class description with an explicit statement that these cases
   validate defensive operand-width consumption only and do not advertise
   custom execution.
2. Rename the existing tests to:
   - `ff01IsSyntaxOnlyAndConsumesOneOperand`
   - `ff02IsSyntaxOnlyAndConsumesOneOperand`
   - `ff03IsSyntaxOnlyAndConsumesThreeOperands`
3. Retain their literal byte streams and exact final track-position/F2
   assertions. Do not replace the expected positions with production width
   metadata.
4. Add `ff01DoesNotDispatchSoundCommand` and annotate the class with
   `@ExtendWith(SingletonResetExtension.class)` so the real singleton audio
   owner starts from the normal per-test reset:
   - capture `AudioManager.commandTimeline().entryCount()` and the real
     presentation registry count from
     `AudioManagerTestDiagnostics.producerFingerprint(audio).voiceIdentities()`;
   - execute the literal FM stream `FF 01 A4 F2` through a real
     `SmpsSequencer`;
   - present a silent frame so any incorrectly submitted presentation command
     would be applied; and
   - assert both counts are unchanged, while the command track is inactive at
     exact position `0x44`. This proves syntax consumption did not become a
     hidden sound-dispatch path.
5. Add `ff02DoesNotHaltSiblingSongTrack` with exact, non-tautological state:
   - set the S3K header FM count byte to `3` (DAC plus two FM entries), with
     FM1's little-endian pointer at header offset `0x0A` targeting `0x40` and
     FM2's at `0x0E` targeting `0x60`;
   - put the exact FM1 stream `FF 02 01 F2` at `0x40` and the exact FM2 stream
     `81 7F` at `0x60`;
   - call `sequencer.read(new short[0])`, which performs only the S3K priming
     tick needed to consume both tracks' first events;
   - assert FM1 is inactive at exact position `0x44`; and
   - assert FM2 is active at exact position `0x62`, with note `0x81` and
     duration `0x7F`. This pins the current syntax-only boundary and prevents
     documentation from describing a native nine-song-record halt that the
     engine does not perform.
6. Add `ff03DoesNotMutateSequenceMemory`:
   - place `FF 03 70 00 01 F2` at `0x40`;
   - put a different literal byte at source offset `0x70`;
   - advance through the command;
   - assert `sequencer.getData()[0x45] == F2`, distinct from the source byte;
     and
   - assert the command track is inactive at exact position `0x46`. This
     demonstrates that the three-byte pointer/count is consumed but no
     private-array approximation of native `LDIR` occurs.
7. Use only existing `Sonic3kSmpsData`, `SmpsSequencer`,
   `VirtualSynthesizer`, `AudioManager`, and read-only audio diagnostics. Do
   not introduce a test-only production callback or mutable memory API.

Run:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  mvn -Dmse=off \
  -Dtest=com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandOperands \
  test
```

Expected: six tests, zero failures/errors/skips.

## Task 2: Make production comments unambiguous

**Modify:**

- `src/main/java/com/openggf/game/sonic3k/audio/smps/Sonic3kCoordFlagHandler.java`

Change comments only:

1. State in the class documentation that custom/imported streams executing
   these commands are unsupported. The decoder advances over the documented
   widths solely to retain a byte boundary in synthetic/diagnostic data.
2. In each `FF 01/02/03` case, replace “preserve custom stream alignment” or
   “no ROM path to model” language with the exact syntax-only/non-capability
   disposition.
3. Preserve every conditional, increment, return, and log statement byte for
   byte. No runtime behavior changes.

Run Task 1 again after the comment edit to prove the source-only clarification
did not alter the decoder.

## Task 3: Reconcile current documentation

**Modify:**

- `README.md`
- `CHANGELOG.md`
- `docs/guide/cross-referencing/architecture-overview.md`
- `docs/guide/contributing/audio-system.md`
- `docs/guide/contributing/architecture.md`
- `docs/architecture/research/audio/2026-08-08-s3k-smps-meta-command-reachability.md`
- `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md`
- `docs/architecture/designs/2026-08-08-dead-and-unfinished-code-sweep.md`
- `docs/architecture/plans/2026-08-08-unfinished-code-remediation-roadmap.md`
- `docs/architecture/validation/2026-08-08-unfinished-code-remediation.md`

For every current claim:

1. Keep the exact shipped reachability result: all loader streams and both
   173-entry native SFX banks close without reaching `FF 01/02/03`.
2. Replace “custom handling,” “custom support,” or “custom stream remains
   supported” with “the decoder recognizes/consumes the source-defined width;
   custom execution is unsupported.”
3. Link the reviewed 2026-08-09 capability design from the research note,
   roadmap, audit, and contributor audio guide.
4. Record why it is a product non-goal today: no public custom SMPS format or
   ingestion route exists, and the mod-audio design uses streamed audio while
   leaving authored SMPS/VGM out of scope.
5. Correct native `FF 02` wording to its exact `zTracksStart..zTracksEnd`
   ownership: the nine song track records. Do not imply that it rewrites the
   separately stored SFX track records.
6. Preserve the historical 2026-08-08 reachability evidence and test outcomes.
   Append or qualify current disposition rather than rewriting commands/counts
   as if they were rerun then.
7. Update the current README/changelog release summary so it does not list the
   shipped-unreachable commands as unfinished gameplay or call syntax
   consumption custom support.

Do not add this to the known-discrepancy ledgers: supported shipped-ROM audio
does not diverge at these commands because no supported stream reaches them.
The explicit capability/non-goal belongs in architecture and contributor
documentation unless a real custom product is later introduced.

## Task 4: Publish the validation record

**Create:**

- `docs/architecture/validation/2026-08-09-s3k-custom-smps-meta-command-capability.md`

Record:

- branch, worktree, base commit, and no-merge/no-push boundary;
- JDK version and locked-on ROM path/hash;
- design and plan review outcomes;
- Z80 routines, product route, sequencer/driver/context, immutable-source, and
  rewind/presentation owners audited;
- the seven-test baseline and final focused/adjacent test counts;
- the three added syntax-only behavioral assertions (no sound dispatch, no
  sibling halt, and no sequence-memory mutation);
- the exact two-failure `TestArchitecturalSourceGuard` baseline and unchanged
  final comparison;
- the fact that no custom SMPS fixture format or supported ingestion route
  exists;
- why `FF 01`, `FF 02`, and `FF 03` cannot be owned independently in the
  current handler;
- every future activation prerequisite from the reviewed design; and
- that shipped behavior and trace frontiers did not change.

Do not claim meta-command implementation, SMPSPlay parity, trace advancement,
or custom-stream support.

## Task 5: Final verification

Run the focused ROM and adjacent audio suites on JDK 21:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  mvn -Dmse=off \
  -Ds3k.rom.path="/absolute/discovered/path/to/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  -Dtest=com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandOperands,com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandReachability,com.openggf.tests.TestSonic3kCoordFlagParity,com.openggf.audio.smps.TestSmpsSequencerSnapshot,com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.audio.TestAudioPresentationSnapshotParity \
  test
```

Run proportionate source/documentation guards. `TestBuildToolingGuard` must be
green. `TestArchitecturalSourceGuard` is compared against the exact two-failure
baseline recorded above and must show no worsening:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  mvn -Dmse=off \
  -Dtest=com.openggf.tests.TestArchitecturalSourceGuard,com.openggf.tests.TestBuildToolingGuard \
  test
```

Then:

1. Run `git diff --check`.
2. Inspect `git diff --stat`, the complete diff, and every untracked file.
3. Confirm the only `src/main` diff is comment text in
   `Sonic3kCoordFlagHandler`; use `git diff --word-diff=porcelain` or an
   equivalent review to ensure its executable tokens did not change.
4. If a Maven test regenerates `docs/status/rewind-round-trip-gaps.md`, inspect
   it and restore it to `HEAD` only when it is solely test-generated output.
   Do not stage it.
5. Stage all intended design, plan, test, current-doc, and validation files.
6. Run `.githooks/run-policy pre-commit`; never bypass hooks.

## Task 6: Commit the isolated evidence package

Commit on `feature/ai-s3k-smps-custom-meta-capability` with a documentation-led
subject such as:

```text
docs: define S3K custom SMPS meta-command boundary
```

Use the policy trailers appended by the hook. Expected classifications include:

- `Changelog: updated`
- `Guide: updated`
- `Known-Discrepancies: n/a`
- `S3K-Known-Discrepancies: n/a`
- `Agent-Docs: n/a`
- `Configuration-Docs: n/a`
- `Skills: n/a`

Fill any required inline reasons rather than deleting or bypassing a trailer.
Do not merge or push the worktree branch.

## Completion criteria

- Independent design and plan reviews are green.
- Six syntax-only tests and four ROM reachability tests pass with the verified
  locked-on ROM.
- The no-dispatch, sibling-track, and immutable-sequence assertions prove what
  the decoder deliberately does not do.
- Current documentation uniformly says custom execution is unsupported and
  names the existing streamed-audio product decision.
- No executable production token changes and no dormant custom-driver
  scaffolding are present.
- Focused/adjacent tests, the unchanged two-failure architecture-guard
  comparison, the green tooling guard, diff inspection, and commit policy pass.
- The clean isolated branch contains the committed evidence package and
  remains unmerged and unpushed.
