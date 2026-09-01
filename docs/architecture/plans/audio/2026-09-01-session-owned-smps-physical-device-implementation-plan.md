# Session-owned SMPS physical-device implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking. Every task receives a requirements review and a
> code-quality review before its commit is accepted.

**Goal:** Replace presentation-owned SMPS chip pairs and logical-driver islands with one
session-owned YM2612/PSG device and one persistent logical driver, then land source-owned S3K
boot/global-command parity without regressing the proven S1/S2 lanes.

**Architecture:** Pre-cutover tasks extract composition, logical-host, activation, and snapshot
boundaries while the existing presentation graph remains authoritative. One atomic cutover then
makes `SmpsDriverSession` the sole presentation device, logical-driver, render, transaction, and
physical-snapshot owner; compatibility tools retain isolated one-driver/one-session adapters.

**Tech stack:** Java 21, Maven, JUnit 5, Nuked-OPN2 `Ym2612Chip`, clean-room `PsgChip`, SMPS
sequencers, unified audio presentation, ROM-backed S1/S2/S3K parity tools, Lua 5.4 guards.

**Spec:**
`docs/architecture/designs/audio/2026-09-01-session-owned-smps-physical-device-design.md`

**Parent roadmap:**
`docs/architecture/plans/audio/2026-08-31-sound-driver-roadmap-completion-plan.md`

## Global constraints

- Work in a fresh isolated worktree branched from
  `feature/ai-sound-driver-roadmap-completion`; never switch the main workspace branch.
- One task is one test-first, independently reviewed commit. Never combine the atomic cutover
  with exact S3K policy work.
- Until Task 5, the old presentation graph remains authoritative and retains its private
  composed devices. The new session is inert and emits no writes.
- At and after Task 5, presentation owns exactly one `SmpsPhysicalDevice` and one persistent
  `SmpsDriver`; no compatibility lease or second arbitration owner is permitted.
- Construction, activation preparation, snapshot preparation, restore materialization, discard,
  and logical teardown emit zero chip writes.
- Physical writes require an owner-thread, current-session, current-owner, open-epoch capability.
- The base session owns physical boot/global-stop policy. The incoming ROM-backed music program
  owns typed logical transition policy. Shared code never branches on game name.
- Retained FE/E2 stop wins before pending activation. A stop cancels activation and commits
  atomically with registry sample/raw clearing through the producer-owned transaction.
- Physical observers are diagnostic-only: buffer until commit, discard on pre-commit failure,
  quarantine callback exceptions, and never replay a published prefix.
- Snapshot restore mutates a resolved logical memento into the existing driver object; it never
  replaces the driver or swaps a chip pair.
- `FixBugs = 0`, `fixBugs = 0`, and `fix_sndbugs = 0` are the shipped paths. Source comments cite
  the owning disassembly routine.
- S1 GHZ music `MATCH (14,690 ticks)` and S1 sound-test SFX `MATCH (1,967 ticks, 8 dispatches)`
  are inviolable task gates. The S2 known first divergence may not move earlier.
- The S3K Z80 boot policy excludes the separate 68k `PSGInitValues` tick-3 burst.
- JDK 21, `LUA_BIN=lua5.4`, absolute verified ROM paths, ordinary tests, and fresh-JVM guards are
  required. Maven output remains below the worktree's `target/`.
- Update `docs/status/audio-frontier-log.md` only when authenticated evidence moves or regresses a
  frontier. Agents prepare but never self-certify the human listening queue.

### Mandatory S1 parity gate after every behavior-changing task

Tasks 1, 2, 5, 6, 7, and 8 must run both commands below after their focused Maven suite and must
record the terminal `MATCH` summaries. Fixture-contract tests are necessary but do not substitute
for these producer/consumer comparisons. Before any command in this plan, set
`OGGF_REPO_ROOT` to the absolute current worktree path and `OGGF_AUDIO_OUTPUT_ROOT` to an absolute
durable artifact directory outside every repository/worktree; neither may be relative.

```bash
tools/audio/run_s1_audio_parity.sh --mode music \
  --rom ${OGGF_REPO_ROOT}/s1.gen \
  --output-root ${OGGF_AUDIO_OUTPUT_ROOT}/s1-audio-music-gate

tools/audio/run_s1_audio_parity.sh --mode sfx \
  --rom ${OGGF_REPO_ROOT}/s1.gen \
  --output-root ${OGGF_AUDIO_OUTPUT_ROOT}/s1-audio-sfx-gate
```

---

## File responsibility map

### Existing files whose ownership changes

- `audio/driver/SmpsDriver.java`: logical sequencers, arbitration, cadence, logical mementos;
  compatibility forwarding only before final retirement.
- `audio/smps/SmpsSequencer.java`: sequencing against a logical host and ephemeral physical port;
  no constructor writes or reverse cast to `SmpsDriver`.
- `audio/synth/VirtualSynthesizer.java`: composed chip implementation; after cutover it is owned
  only by `SmpsPhysicalDevice` and standalone adapters.
- `audio/rewind/SmpsDriverSnapshot.java`: logical state only.
- `audio/presentation/AudioPresentationProducer.java`: outer service/render cadence and composite
  transaction owner.
- `audio/presentation/AudioVoiceRegistry.java`: sample/raw registry participant; no SMPS device or
  global emitter election.
- `audio/presentation/AudioPresentationMixer.java`: seed from the session device once, then mix
  PCM-only voices.
- `audio/presentation/SmpsCompositeVoice.java`: retired as a physical renderer; compatibility
  logical node only until Task 6.
- `audio/presentation/AudioPresentationSourceFactory.java`: prepare ROM-backed program mementos;
  never construct a presentation driver/device after cutover.
- `audio/AudioManager.java`: create, initialize, replace, and close one presentation session.

### New focused production files

- `audio/smps/SmpsSequencerHost.java`: logical callbacks formerly recovered by reverse cast.
- `audio/smps/SmpsLogicalWriteTarget.java`: descriptor-aware sequencer write target; standalone
  devices may ignore DAC provenance, session access may not.
- `audio/session/SmpsPhysicalPort.java`: ephemeral write/admission capability.
- `audio/session/SmpsPhysicalDevice.java`: sole composed `VirtualSynthesizer`, render, controls,
  physical snapshots, and owner-thread enforcement.
- `audio/session/SmpsChipWrite.java`, `SmpsWriteProgram.java`, `SmpsPhysicalPolicy.java`: immutable
  physical programs and their game-owned policy.
- `audio/session/SmpsDacSelection.java`, `SmpsMusicActivation.java`: stable DAC identity and pending
  activation input.
- `audio/session/PreparedSmpsMusicActivation.java`: pure incoming program memento, logical policy,
  physical activation metadata, and resolved DAC selection.
- `audio/session/PreparedSmpsSfxProgram.java`, `SmpsSessionCommand.java`: pure SFX input and the
  complete typed logical-command boundary used after presentation loses direct driver access.
- `audio/session/SmpsLogicalTransitionPolicy.java`: incoming-program song-load/override behavior.
- `audio/session/SmpsSessionProfileFingerprint.java`: base profile/policy/settings/dependency
  identity.
- `audio/session/SmpsDriverSessionSnapshot.java`: one physical snapshot and session state.
- `audio/session/SmpsServiceOutcome.java`: `ORDINARY` or `GLOBAL_STOP_CONSUMED`.
- `audio/session/SmpsDriverSession.java`: sole driver/device, epochs, activation, service,
  transaction tokens, snapshot/restore, diagnostics, and render.
- `audio/session/OwnedSmpsAudioStream.java`: isolated legacy/tool adapter.
- `audio/driver/SmpsDriverSessionAccess.java`: restricted synthesizer-write marker accepted only
  by the session-backed driver factory.

---

## Acceptance traceability

| Design contract | Executable test | First owning task |
|---|---|---:|
| 1. Stable physical/logical identity | `TestSmpsDriverSession.onePhysicalAndLogicalIdentitySurviveAllTransitions` | 5 |
| 2. Exact S3K 85-write boot | `TestSmpsPhysicalPolicy.s3kBootIsExact85OrderedWrites` and `TestSmpsDriverSession.initializeIsIdempotent` | 7 |
| 3. Exact 84/202 stop programs | `TestSmpsPhysicalPolicy.s3kStopAllIsExact84Writes` and `.s1AndS2CompatibilityStopRemain202Writes` | 7 |
| 4. Pure logical construction | `TestSmpsDriverSession.logicalConstructionRecreationAndDiscardAreWriteFree` | 2 |
| 5. One render/packet | `TestSmpsDriverSession.multipleLogicalOperationsRenderOnceAndPublishOnePacket` | 5 |
| 6. Integral/fractional fast-forward | `TestSmpsDriverSession.integralFastForwardServicesOnceAndRendersExactSourceFrames` and `.fractionalFastForwardUsesSamePacketInterval` | 5 |
| 7. Empty-state physical tail | `TestSmpsDriverSession.emptyLogicalStateRendersAndRewindsPhysicalTail` | 5 |
| 8. Silent/reverse isolation | `TestSmpsDriverSession.silentAndReverseDoNotServiceOrRender` | 5 |
| 9. One fingerprinted physical snapshot | `TestSmpsDriverSnapshot.logicalSnapshotContainsNoPhysicalState` and `TestSmpsSessionSnapshot.presentationSnapshotHasOnePhysicalSnapshotAndFingerprint` | 3/5 |
| 10. Pure atomic restore | `TestSmpsSessionSnapshot.prepareCommitDiscardAndFailedRestoreAreWriteFreeAndAtomic` and `.staleProfileRestoreFailsBeforeMutation` | 3/5 |
| 11. Pending stop rewind | `TestSmpsDriverSession.pendingGlobalStopRestoresWithoutWritesThenStopsOnce` | 5/7 |
| 12. Stop outranks activation | `TestSmpsDriverSession.retainedStopOutranksAndCancelsEveryPendingActivation` | 5/7 |
| 13. FE immediate/deferred split | `TestSegaPcmCommandRouting.feStopsRawImmediatelyAndDefers84Writes` | 7 |
| 14. One E2 command/stop | `TestSmpsDriverSession.e2RecordsOneCommandAndConsumesOneGlobalStop` | 7 |
| 15. Global clear/no residue | `TestSmpsDriverSession.globalStopClearsMusicOverrideSfxSampleAndRawState` | 7 |
| 16. E4 SFX-only | `TestSmpsDriverSession.e4RemovesSfxButPreservesMusicAndContinuousState` | 7 |
| 17. S3K speed 8/1 | `TestSonic3kSpeedShoesCommandSemantics.speedShoesPickupAndExpirySetMultipliersWithoutE2E3` | 7 |
| 18. Host physical policy matrix | `TestSmpsSessionTransitionMatrix.hostPolicyWinsForEveryBaseDonorPairing` | 5/7 |
| 19. Incoming logical-policy matrix | `TestSmpsSessionTransitionMatrix.baseMusicAfterDonorSfxAndDonorMusicAfterBaseSfx` | 5/8 |
| 20. Exact override resume | `TestSmpsSessionTransitionMatrix.overridePopEmitsSourceOwnedFirstServiceAndExactNextPcm` | 8 |
| 21. Transaction rollback | `TestSmpsDriverSession.commandDependencyActivationAndDriverFailuresRollbackOnce` | 5 |
| 22. Diagnostic quarantine | `TestSmpsSessionDiagnostics.observerEventsPublishOnlyAfterCommit` and `.observerExceptionIsQuarantinedWithoutReplay` | 5 |
| 23. Owner-thread enforcement | `TestSmpsPhysicalDevice.offOwnerThreadEntryPointsFailBeforeMutation` | 4 |
| 24. Epoch/token authority | `TestSmpsPhysicalPort.hiddenOutgoingStaleEpochCrossOwnerAndCrossSessionCallsFailBeforeMutation` and `.admissionTokenIsSingleUseAndBoundToDeviceEpochOwner` | 4/5 |
| 25. Stable write identity/context | `TestSmpsSessionDiagnostics.everyPhysicalWriteHasStableSessionIdentityAndLogicalContext` | 5 |
| 26. Base-profile hard boundary | `TestSmpsDriverSession.baseProfileReplacementClosesAndReinitializesSession` and `.donorChangePreservesSessionFingerprint` | 5 |
| 27. Bounded SFX rollback | `TestSmpsPhysicalPort.admissionRollbackIsBoundedAndByteExact` | 5 |
| 28. Controls once per session | `TestSmpsPhysicalDevice.controlsAndOutputSettingsApplyOncePerSession` | 5 |
| 29. Structural ownership | `TestSmpsSessionArchitectureGuard` and `TestAudioPresentationArchitectureGuard` | 1-7 |

---

### Task 1: Replace `SmpsDriver` inheritance with behavior-preserving composition

**Files:**

- Modify: `src/main/java/com/openggf/audio/driver/SmpsDriver.java`
- Create: `src/main/java/com/openggf/audio/smps/SmpsLogicalWriteTarget.java`
- Modify only if delegation requires visibility:
  `src/main/java/com/openggf/audio/synth/VirtualSynthesizer.java`
- Modify: `src/test/java/com/openggf/audio/TestAudioPresentationArchitectureGuard.java`
- Create: `src/test/java/com/openggf/audio/driver/TestSmpsDriverSynthComposition.java`

**Interfaces:**

- Consumes: existing `Synthesizer`, `AudioStream`, and `VirtualSynthesizer` behavior.
- Produces: `SmpsDriver implements SmpsLogicalWriteTarget, AudioStream` with one private
  `SmpsLogicalWriteTarget synthesizer` for sequencer writes and a nullable private
  `VirtualSynthesizer standalonePhysical` for transitional standalone render/snapshot/control
  compatibility. Normal constructors set both references to the same private device; Task 5's
  session constructor sets `standalonePhysical` to null and cannot call compatibility methods.
- Preserves: current public constructors, `read`, `serviceOuterFrame`, `renderFramePcm`, snapshot,
  mutation, observer, mute, DAC, and protected test override seams.

- [ ] **Step 1: Write the inheritance/composition RED and delegation characterization tests.**

```java
@Test void driverDoesNotExtendVirtualSynthesizer() {
    assertFalse(VirtualSynthesizer.class.isAssignableFrom(SmpsDriver.class));
    assertTrue(Synthesizer.class.isAssignableFrom(SmpsDriver.class));
}

@Test void composedDriverMatchesLegacyWriteRenderAndSnapshotBehavior() {
    ChipWriteObserver observer = new ChipWriteObserver() {
        @Override public void onYm2612Write(int port, int register, int value) {
            writes.add(new YmWrite(port, register, value));
        }
        @Override public void onPsgWrite(int value) {
            writes.add(new PsgWrite(value));
        }
    };
    SmpsDriver driver = new SmpsDriver(48_000.0, observer);
    Object source = new Object();
    driver.writeFm(source, 0, 0x2b, 0x80);
    driver.writePsg(source, 0x9f);
    short[] pcm = new short[1_600];
    assertEquals(pcm.length, driver.renderFramePcm(pcm, pcm.length));
    assertFalse(writes.isEmpty());
    assertNotNull(driver.captureSnapshot());
}
```

- [ ] **Step 2: Run the focused RED and record that inheritance is the failure.**

```bash
mvn -Dmse=off \
  '-Dtest=com.openggf.audio.driver.TestSmpsDriverSynthComposition,com.openggf.tests.TestSmpsDriver,com.openggf.audio.TestAudioPresentationArchitectureGuard' \
  test -B
```

- [ ] **Step 3: Introduce explicit composition and forward the complete compatibility surface.**

```java
public class SmpsDriver implements SmpsLogicalWriteTarget, AudioStream {
    private final SmpsLogicalWriteTarget synthesizer;
    private final VirtualSynthesizer standalonePhysical;

    public SmpsDriver(double outputSampleRate, ChipWriteObserver observer) {
        standalonePhysical = new VirtualSynthesizer(outputSampleRate, observer);
        synthesizer = standalonePhysical;
    }

    @Override
    public void writeFm(Object source, int port, int register, int value) {
        // Existing admission/lock filtering remains here.
        synthesizer.writeFm(source, port, register, value);
    }
}
```

```java
public interface SmpsLogicalWriteTarget extends Synthesizer {
    void selectDac(SmpsSourceDescriptor source, DacData data);
}
```

`VirtualSynthesizer` implements this interface for standalone compatibility and delegates
`selectDac(source, data)` to its existing DAC setter while deliberately ignoring provenance.
`SmpsDriver.setDacData(DacData)` remains a guarded standalone compatibility method;
`SmpsDriver.selectDac(source, data)` is the only logical/session route and delegates downstream
only after driver validation. Every driver-owned sequencer receives the persistent driver itself
as its `SmpsLogicalWriteTarget`, never the driver's private downstream target, so admission/lock
filtering cannot be bypassed.

Keep every currently overridden driver method non-final. Convert `super.writeFm`,
`super.writePsg`, `super.render*`, `super.forceSilenceChannel`, synth snapshot, DAC-reference,
mute, interpolation, output-rate, and PSG configuration calls to explicit delegation. Do not
share the composed synth or change constructor silence/render cadence.

Write paths always delegate through `synthesizer`. Transitional render/snapshot/control methods
delegate through `requireStandalonePhysical()` and fail before mutation on a session-backed driver;
no cast from `Synthesizer` to `VirtualSynthesizer` is allowed. Task 3 therefore calls
`standalonePhysical.captureSynthSnapshot()`, `selectedDacDataForSnapshot()`, and
`restoreSelectedDacData(...)` through those guarded driver helpers.

Add visibility-safe synth delegation for the transitional combined snapshot without exposing the
composed object itself: `VirtualSynthesizer.selectedDacDataForSnapshot()` and
`restoreSelectedDacData(DacData)`. Preserve the existing constructor overloads exactly.

- [ ] **Step 4: Run driver, synth, presentation, allocation, and snapshot regressions.**

```bash
mvn -Dmse=off \
  '-Dtest=com.openggf.audio.driver.**,com.openggf.audio.synth.**,com.openggf.audio.TestSmpsCompositeVoice,com.openggf.audio.TestAudioDiagnosticObservers,com.openggf.audio.TestAudioPresentationAllocationBudget' \
  test -B
```

Then run the mandatory S1 music and SFX parity gate block above.

- [ ] **Step 5: Independently review exact delegation coverage, then commit.**

```bash
git add src/main/java/com/openggf/audio/driver/SmpsDriver.java \
  src/main/java/com/openggf/audio/smps/SmpsLogicalWriteTarget.java \
  src/main/java/com/openggf/audio/synth/VirtualSynthesizer.java \
  src/test/java/com/openggf/audio/driver/TestSmpsDriverSynthComposition.java \
  src/test/java/com/openggf/audio/TestAudioPresentationArchitectureGuard.java
git commit -m 'refactor(audio): compose SMPS physical synthesizer'
```

### Task 2: Remove sequencer reverse casts and make construction physically pure

**Files:**

- Create: `src/main/java/com/openggf/audio/smps/SmpsSequencerHost.java`
- Modify: `src/main/java/com/openggf/audio/smps/SmpsSequencer.java`
- Modify: `src/main/java/com/openggf/audio/driver/SmpsDriver.java`
- Modify: `src/test/java/com/openggf/audio/smps/TestSmpsSfxConstructionPurity.java`
- Create: `src/test/java/com/openggf/audio/smps/TestSmpsSequencerHost.java`
- Modify: `src/test/java/com/openggf/audio/TestAudioPresentationArchitectureGuard.java`

**Interfaces:**

- Produces:

```java
public interface SmpsSequencerHost {
    SmpsSequencerHost NONE = NoOp.INSTANCE;
    SmpsDriverServiceObserver.ServiceEvent beginSequencerService(
            SmpsSequencer sequencer,
            SmpsDriverServiceObserver.ServiceKind kind);
    void endSequencerService(SmpsDriverServiceObserver.ServiceEvent event);
    void reconcileInactiveSfxTracks(SmpsSequencer sequencer);
    byte[] s1SpecialSfxVoiceForBug(int voiceId);
    boolean isContinuousSfxFlagSet();
    void clearContinuousSfxId();
    void clearContinuousSfxFlag();
    boolean decrementContSfxLoopCnt();

    final class NoOp implements SmpsSequencerHost {
        private static final NoOp INSTANCE = new NoOp();
        // Implement every callback with the existing neutral event/value contract.
    }
}
```

Preserve every existing `SmpsSequencer` constructor. Compatibility overloads delegate to the new
full constructor and may infer a host only with `synth instanceof SmpsSequencerHost`; they never
test for or cast to concrete `SmpsDriver`. Callers without a host receive
`SmpsSequencerHost.NONE`.

- `SmpsSequencer` consumes `SmpsLogicalWriteTarget` for physical writes and DAC provenance and
  `SmpsSequencerHost` for logical callbacks. It never casts one back to the other.
- Music construction prepares state only. The legacy driver explicitly performs current DAC
  selection and YM `$2B=$80` activation when the prepared music sequencer is admitted.

- [ ] **Step 1: Write REDs for no reverse cast and zero construction writes.**

```java
@Test void musicConstructionDoesNotSelectDacOrWriteYm() {
    RecordingSynth synth = new RecordingSynth();
    new SmpsSequencer(music, dac, synth, host, restoreSink, config, source, trust);
    assertTrue(synth.events().isEmpty());
}

@Test void serviceCallbacksUseLogicalHostAndPreserveExactEvent() {
    sequencer.serviceOuterFrame();
    assertSame(host.beginEvent(), host.endEvent());
}
```

- [ ] **Step 2: Run RED tests and prove constructor writes and reverse casts are the causes.**

```bash
mvn -Dmse=off \
  '-Dtest=com.openggf.audio.smps.TestSmpsSfxConstructionPurity,com.openggf.audio.smps.TestSmpsSequencerHost,com.openggf.tests.TestSmpsDriver' \
  test -B
```

- [ ] **Step 3: Inject the host separately and move compatibility activation to admission.**

```java
public SmpsSequencer(
        AbstractSmpsData data,
        DacData dac,
        SmpsLogicalWriteTarget synth,
        SmpsSequencerHost host,
        MusicRestoreSink restoreSink,
        SmpsSequencerConfig config,
        SmpsSourceDescriptor source,
        SourceDescriptorTrust trust) {
    this.synth = Objects.requireNonNull(synth);
    this.host = Objects.requireNonNull(host);
    // Parse and seed logical state only; no setDacData/writeFm here.
}
```

In `SmpsDriver.addSequencer`, after validation and immediately before publishing a music
sequencer to the legacy private device, perform the existing `setDacData(dac)` and
`writeFm(sequencer, 0, 0x2B, 0x80)` inside the existing command transaction. Snapshot restore
materialization does not call this compatibility activation.

Migrate every production sequencer/driver DAC-selection site, including music restore and SFX
admission initialization, to `SmpsLogicalWriteTarget.selectDac(source, data)`. Full constructors
require the descriptor-aware target. Legacy constructor overloads accepting plain `Synthesizer`
wrap it in a standalone-only adapter whose `selectDac` delegates to `setDacData`; the session path
never uses that adapter and anonymous DAC selection is rejected before physical mutation.

- [ ] **Step 4: Run construction, cadence, driver, S1 music, and S1 SFX gates.**

```bash
mvn -Dmse=off \
  "-Dsonic1.rom.path=${OGGF_REPO_ROOT}/s1.gen" \
  '-Dtest=com.openggf.audio.smps.TestSmpsSfxConstructionPurity,com.openggf.audio.smps.TestSmpsSequencerHost,com.openggf.audio.smps.TestSmpsSequencerCadence,com.openggf.tests.TestSmpsDriver,com.openggf.tools.audio.parity.TestS1OpenGgfAudioCapture,com.openggf.tools.audio.parity.TestS1AudioParityFixtureContract' \
  test -B
```

Then run the mandatory S1 music and SFX parity gate block above; require both `MATCH` results.

- [ ] **Step 5: Review constructor purity and callback equivalence, then commit.**

```bash
git add src/main/java/com/openggf/audio/smps/SmpsSequencerHost.java \
  src/main/java/com/openggf/audio/smps/SmpsSequencer.java \
  src/main/java/com/openggf/audio/driver/SmpsDriver.java \
  src/test/java/com/openggf/audio/smps/TestSmpsSfxConstructionPurity.java \
  src/test/java/com/openggf/audio/smps/TestSmpsSequencerHost.java \
  src/test/java/com/openggf/audio/TestAudioPresentationArchitectureGuard.java
git commit -m 'refactor(audio): separate SMPS sequencing from activation'
```

### Task 3: Split logical mementos from legacy physical snapshots

**Files:**

- Modify: `src/main/java/com/openggf/audio/rewind/SmpsDriverSnapshot.java`
- Create: `src/main/java/com/openggf/audio/rewind/LegacySmpsDriverSnapshot.java`
- Modify: `src/main/java/com/openggf/audio/driver/SmpsDriver.java`
- Modify: `src/main/java/com/openggf/audio/presentation/PresentationVoiceSnapshot.java`
- Modify: `src/main/java/com/openggf/audio/presentation/SmpsCompositeVoice.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationSourceFactory.java`
- Modify: `src/test/java/com/openggf/audio/driver/TestSmpsDriverSnapshot.java`
- Modify: `src/test/java/com/openggf/audio/TestAudioPresentationSnapshotParity.java`

**Interfaces:**

- `SmpsDriverSnapshot` becomes a logical memento and contains no `VirtualSynthesizer.Snapshot`.
- Pre-cutover presentation and standalone callers temporarily use:

```java
public record LegacySmpsDriverSnapshot(
        SmpsDriverSnapshot logical,
        VirtualSynthesizer.Snapshot physical,
        DacData liveDacReference) {
    public LegacySmpsDriverSnapshot {
        Objects.requireNonNull(logical);
        Objects.requireNonNull(physical);
    }
}
```

- `SmpsDriver.captureSnapshot()` and `restoreSnapshot(...)` are logical-only.
- `captureLegacySnapshot()` and `restoreLegacySnapshot(...)` preserve current pre-cutover PCM.
- `PresentationVoiceSnapshot.Smps.driver()` temporarily returns `LegacySmpsDriverSnapshot`;
  factory dependency reads use `snapshot.driver().logical().sequencers()`.

- [ ] **Step 1: Write REDs proving the logical record has no physical type and restore preparation
  is write-free.**

```java
@Test void logicalSnapshotContainsNoPhysicalSynthState() {
    assertTrue(Arrays.stream(SmpsDriverSnapshot.class.getRecordComponents())
            .noneMatch(component -> component.getType() == VirtualSynthesizer.Snapshot.class));
}

@Test void resolvingLogicalMementoEmitsNoChipWrites() {
    AtomicInteger writes = new AtomicInteger();
    ChipWriteObserver observer = new ChipWriteObserver() {
        @Override public void onYm2612Write(int port, int register, int value) {
            writes.incrementAndGet();
        }
        @Override public void onPsgWrite(int value) {
            writes.incrementAndGet();
        }
    };
    SmpsDriver source = new SmpsDriver(48_000.0, observer);
    source.addSequencer(newSequencer("music", 0x81, source), false);
    SmpsDriverSnapshot memento = source.captureSnapshot();
    SmpsDriver target = new SmpsDriver(48_000.0, observer);
    writes.set(0);

    target.restoreSnapshot(memento, SmpsDriverSnapshot.liveReferences());

    assertEquals(0, writes.get());
}
```

- [ ] **Step 2: Run RED snapshot and rewind classes.**

```bash
mvn -Dmse=off \
  '-Dtest=com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.audio.TestAudioPresentationSnapshotParity,com.openggf.audio.presentation.TestAudioPresentationProducerRewind' \
  test -B
```

- [ ] **Step 3: Split capture/restore without changing authoritative presentation behavior.**

```java
public SmpsDriverSnapshot captureSnapshot() {
    return captureLogicalState();
}

public LegacySmpsDriverSnapshot captureLegacySnapshot() {
    return new LegacySmpsDriverSnapshot(
            captureLogicalState(), requireStandalonePhysical().captureSynthSnapshot(),
            selectedDacDataForSnapshot());
}
```

Resolve every dependency before mutating the live driver. Restore the live DAC reference before
the legacy physical snapshot. Direct physical restore emits no observer events. Prepared discard
only drops the memento.

- [ ] **Step 4: Run driver snapshot, registry restore, rewind, diagnostics, and next-PCM parity.**

```bash
mvn -Dmse=off \
  '-Dtest=com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.audio.presentation.TestAudioVoiceRegistry,com.openggf.audio.TestAudioPresentationSnapshotParity,com.openggf.audio.presentation.TestAudioPresentationProducerRewind,com.openggf.audio.TestAudioDiagnosticObservers,com.openggf.audio.TestSmpsCompositeVoice' \
  test -B
```

- [ ] **Step 5: Review every snapshot carrier, then commit the split.**

```bash
git add src/main/java/com/openggf/audio/rewind/SmpsDriverSnapshot.java \
  src/main/java/com/openggf/audio/rewind/LegacySmpsDriverSnapshot.java \
  src/main/java/com/openggf/audio/driver/SmpsDriver.java \
  src/main/java/com/openggf/audio/presentation/PresentationVoiceSnapshot.java \
  src/main/java/com/openggf/audio/presentation/SmpsCompositeVoice.java \
  src/main/java/com/openggf/audio/presentation/AudioPresentationSourceFactory.java \
  src/test/java/com/openggf/audio/driver/TestSmpsDriverSnapshot.java \
  src/test/java/com/openggf/audio/TestAudioPresentationSnapshotParity.java
git commit -m 'refactor(audio): split logical and physical SMPS snapshots'
```

### Task 4: Introduce an inert session composition root and scoped physical capabilities

**Files:**

- Create: `src/main/java/com/openggf/audio/session/SmpsChipWrite.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsWriteProgram.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsPhysicalPolicy.java`
- Create: `src/main/java/com/openggf/audio/session/LegacyCompatibilitySmpsPhysicalPolicy.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsPhysicalPort.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsPhysicalDevice.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsDacSelection.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsMusicActivation.java`
- Create: `src/main/java/com/openggf/audio/session/PreparedSmpsMusicActivation.java`
- Create: `src/main/java/com/openggf/audio/session/PreparedSmpsSfxProgram.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsSessionCommand.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsLogicalTransitionPolicy.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsSessionProfileFingerprint.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsPendingGlobalCommand.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsDriverSessionSnapshot.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsServiceOutcome.java`
- Create: `src/main/java/com/openggf/audio/session/SmpsDriverSession.java`
- Modify: `src/main/java/com/openggf/audio/synth/VirtualSynthesizer.java`
- Modify: `src/main/java/com/openggf/audio/GameAudioProfile.java`
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationSourceFactory.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioVoiceRegistry.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationProducer.java`
- Create: `src/test/java/com/openggf/audio/session/TestSmpsPhysicalDevice.java`
- Create: `src/test/java/com/openggf/audio/session/TestSmpsPhysicalPort.java`
- Create: `src/test/java/com/openggf/audio/session/TestSmpsDriverSession.java`
- Create: `src/test/java/com/openggf/audio/session/TestSmpsSessionSnapshot.java`
- Create: `src/test/java/com/openggf/audio/session/TestSmpsSessionThreadOwnership.java`

**Interfaces:**

```java
public sealed interface SmpsChipWrite {
    record Ym2612(int port, int register, int value) implements SmpsChipWrite { }
    record Psg(int value) implements SmpsChipWrite { }
}

public record SmpsWriteProgram(List<SmpsChipWrite> writes) {
    public SmpsWriteProgram { writes = List.copyOf(writes); }
}

public interface SmpsPhysicalPolicy {
    record Identity(String value) {
        public Identity { value = Objects.requireNonNull(value); }
    }
    Identity identity();
    SmpsWriteProgram boot();
    SmpsWriteProgram stopAll();
    SmpsWriteProgram activateMusic(SmpsMusicActivation activation);
}

public interface SmpsPhysicalPort {
    SmpsDriverServiceObserver.DriverIdentity owner();
    long epoch();
    void writeFm(int port, int register, int value);
    void writePsg(int value);
    void setInstrument(int channelId, byte[] voice);
    void playDac(int note);
    void stopDac();
    void selectDac(SmpsDacSelection selection);
    void forceSilenceFmChannel(int channelId);
    AdmissionToken captureAdmissionState(int fmMask, int psgMask);
    void restoreAdmissionState(AdmissionToken token);
    interface AdmissionToken { }
}

public record SmpsDacSelection(
        SmpsSourceDescriptor source,
        DacData data) { }

public record SmpsMusicActivation(
        SmpsSourceDescriptor source,
        int fmDacTrackCount) { }

public record PreparedSmpsMusicActivation(
        SmpsMusicActivation activation,
        SmpsDriverSnapshot.SequencerEntry incomingMusic,
        SmpsLogicalTransitionPolicy logicalPolicy,
        SmpsDacSelection selectedDac) { }

public record PreparedSmpsSfxProgram(
        SmpsDriverSnapshot.SequencerEntry incomingSfx,
        int continuousSfxId,
        int continuousTrackCount) { }

public sealed interface SmpsSessionCommand {
    record AdmitSfx(PreparedSmpsSfxProgram program) implements SmpsSessionCommand { }
    record StopMusic() implements SmpsSessionCommand { }
    record StopAllSfx() implements SmpsSessionCommand { }
    record PushOverride(PreparedSmpsMusicActivation activation) implements SmpsSessionCommand { }
    record RestoreOverride() implements SmpsSessionCommand { }
    record EndOverride(int musicId) implements SmpsSessionCommand { }
    record FadeMusic(int steps, int delay) implements SmpsSessionCommand { }
    record SetSpeedMultiplier(int multiplier) implements SmpsSessionCommand { }
    record SetSpeedShoes(boolean enabled) implements SmpsSessionCommand { }
    record ChangeMusicTempo(int dividingTiming) implements SmpsSessionCommand { }
    record ResetRingAlternation(boolean ringLeft) implements SmpsSessionCommand { }
    record HardReset() implements SmpsSessionCommand { }
}

public interface SmpsLogicalTransitionPolicy {
    Result prepareMusicStart(
            SmpsDriverSnapshot current,
            SmpsDriverSnapshot.SequencerEntry incomingMusic);
    Result prepareOverrideRestore(
            SmpsDriverSnapshot current,
            SmpsDriverSnapshot saved);

    record Result(
            SmpsDriverSnapshot logical,
            SmpsWriteProgram firstServiceWrites) { }
}

public enum SmpsPendingGlobalCommand { NONE, STOP_ALL }

public record SmpsSessionProfileFingerprint(
        String baseGameId,
        long sourceGeneration,
        SmpsPhysicalPolicy.Identity physicalPolicyId,
        SmpsPhysicalDevice.Settings settings) { }
```

`LegacyCompatibilitySmpsPhysicalPolicy` is the default returned by
`GameAudioProfile.smpsPhysicalPolicy()` and reproduces current behavior exactly. Game-specific
policies replace it in Task 7.

Add an explicit no-write construction path while preserving all old constructors. The enum is
nested as `VirtualSynthesizer.Initialization`, not a separate top-level file:

```java
public enum Initialization { LEGACY_SILENCE, DEFERRED }

public VirtualSynthesizer(
        double outputSampleRate,
        ChipWriteObserver observer,
        Initialization initialization) {
    // construct chips/configuration
    if (initialization == Initialization.LEGACY_SILENCE) {
        silenceAll();
    }
}
```

Existing constructors delegate to `LEGACY_SILENCE`; only `SmpsPhysicalDevice` uses `DEFERRED`.
Its compile-time API is:

```java
public final class SmpsPhysicalDevice {
    public record Settings(double outputSampleRate, boolean dacInterpolate,
            boolean psgNoiseShiftEveryToggle) { }
    public record Snapshot(VirtualSynthesizer.Snapshot synth, Settings settings) { }
    interface LiveMutationToken { }

    // Constructor and all mutating operations are package-private; only the session calls them.
    SmpsPhysicalDevice(Settings settings, ChipWriteObserver observer);
    void apply(SmpsWriteProgram program);
    int renderFrames(short[] target, int offsetSamples, int stereoFrames);
    Snapshot captureSnapshot();
    void restoreSnapshot(Snapshot snapshot, DacData resolvedDac);
    LiveMutationToken captureLiveMutation();
    void rollbackLiveMutation(LiveMutationToken token);
    void applyChannelMasks(int fmMask, int psgMask);
    void close();
}
```

`SmpsDriverSession` is deliberately device-only and inert in this task: it constructs no driver
and exposes no install/service/render API until Task 5. Define its stable supporting types now:

```java
public final class SmpsDriverSession implements AutoCloseable {
    public interface DacDependencyResolver {
        DacData resolve(SmpsSourceDescriptor source);
    }
    public record PreparedRestore(SmpsDriverSessionSnapshot session,
            SmpsDriverSnapshot logical, DacData resolvedDac) { }
    public interface LiveMutationToken { }

    public SmpsDriverSession(SmpsPhysicalDevice.Settings settings,
            SmpsPhysicalPolicy policy, ChipWriteObserver observer,
            SmpsSessionProfileFingerprint profile);
    public boolean installed();
    public SmpsDriverSessionSnapshot captureSnapshot();
    public LiveMutationToken captureLiveMutation();
    public void commitLiveMutation(LiveMutationToken token);
    public void rollbackLiveMutation(LiveMutationToken token);
    public void applyChannelMasks(int fmMask, int psgMask);
    // Package-private test support only; production uses withPort after Task 5.
    SmpsPhysicalPort openTestEpoch(SmpsDriverServiceObserver.DriverIdentity owner);
    void closeTestEpoch(long epoch);
    @Override public void close();
}
```

`SmpsDriverSessionSnapshot` is physical/session state only: initialized flag,
`SmpsPendingGlobalCommand`, profile fingerprint, selected-DAC source, and exactly one
`SmpsPhysicalDevice.Snapshot`. The presentation snapshot will carry the separate logical memento
after cutover. Creating, capturing, preparing, and discarding this inert composition emits no
writes.

- [ ] **Step 1: Write REDs for owner thread, epoch authority, token identity, pure construction,
  and inert composition.**

```java
@Test void staleEpochAndCrossSessionTokensFailBeforeMutation() {
    SmpsPhysicalPort first = session.openTestEpoch(ownerA);
    SmpsPhysicalPort.AdmissionToken token = first.captureAdmissionState(1, 1);
    session.closeTestEpoch(first.epoch());
    assertThrows(IllegalStateException.class, () -> first.writePsg(0x9f));
    assertThrows(IllegalArgumentException.class,
            () -> otherSession.openTestEpoch(ownerA).restoreAdmissionState(token));
    assertEquals(before, device.captureSnapshot());
}

@Test void physicalPortRoutesDacInstrumentAndSilenceOnlyDuringCurrentEpoch() {
    SmpsPhysicalPort port = session.openTestEpoch(ownerA);
    port.setInstrument(0, voice);
    port.selectDac(new SmpsDacSelection(source, dac));
    port.playDac(0x81);
    port.stopDac();
    port.forceSilenceFmChannel(0);
    session.closeTestEpoch(port.epoch());
    assertThrows(IllegalStateException.class, () -> port.playDac(0x82));
    assertEquals(expectedCommittedSnapshot, device.captureSnapshot());
}

@Test void composingInertSessionEmitsNoWrites() {
    SmpsDriverSession session = fixture.newInertSession();
    assertTrue(writes.isEmpty());
    assertFalse(session.installed());
}
```

- [ ] **Step 2: Run the new session RED suite.**

```bash
mvn -Dmse=off '-Dtest=com.openggf.audio.session.**' test -B
```

- [ ] **Step 3: Implement the device, capability checks, immutable policy types, and inert
  composition.**

```java
private void requireOpen(PortCapability capability) {
    requireOwnerThread();
    if (capability.sessionId() != sessionId
            || capability.epoch() != openEpoch
            || !capability.owner().equals(openOwner)) {
        throw new IllegalStateException("SMPS physical capability is not active");
    }
}
```

Capture the session owner thread at construction. Every write, render, control, snapshot,
restore, rollback, initialize, and close entry checks it before mutation. Admission tokens carry
session/device/owner/epoch identity and a consumed flag. Direct rollback bypasses diagnostic
observers.

- [ ] **Step 4: Wire one inert instance at `AudioManager.ensureShadowPresentation`.**

Resolve the base profile's compatibility physical policy and stable fingerprint; inject the same
session reference into factory, registry, and producer. Do not call `install`, redirect driver
creation, change render, or remove any old presentation device in this task.

- [ ] **Step 5: Run session, current presentation, rewind, donor, thread, and allocation tests.**

```bash
mvn -Dmse=off \
  '-Dtest=com.openggf.audio.session.**,com.openggf.audio.presentation.TestAudioPresentationProducer,com.openggf.audio.presentation.TestAudioVoiceRegistry,com.openggf.audio.presentation.TestAudioPresentationProducerRewind,com.openggf.audio.TestDonorAudioRouting,com.openggf.audio.TestAudioPresentationAllocationBudget' \
  test -B
```

- [ ] **Step 6: Review that the new session is unique and completely inert, then commit.**

```bash
git add src/main/java/com/openggf/audio/session \
  src/main/java/com/openggf/audio/GameAudioProfile.java \
  src/main/java/com/openggf/audio/AudioManager.java \
  src/main/java/com/openggf/audio/presentation/AudioPresentationSourceFactory.java \
  src/main/java/com/openggf/audio/presentation/AudioVoiceRegistry.java \
  src/main/java/com/openggf/audio/presentation/AudioPresentationProducer.java \
  src/test/java/com/openggf/audio/session
git commit -m 'feat(audio): compose inert session SMPS device'
```

### Task 5: Cut presentation atomically to one persistent driver and device

**Files:**

- Modify: `src/main/java/com/openggf/audio/session/SmpsDriverSession.java`
- Modify: `src/main/java/com/openggf/audio/session/SmpsPhysicalDevice.java`
- Modify: `src/main/java/com/openggf/audio/driver/SmpsDriver.java`
- Create: `src/main/java/com/openggf/audio/driver/SmpsDriverSessionAccess.java`
- Modify: `src/main/java/com/openggf/audio/presentation/PresentationVoice.java`
- Create: `src/main/java/com/openggf/audio/presentation/PcmPresentationVoice.java`
- Modify: `src/main/java/com/openggf/audio/presentation/SmpsCompositeVoice.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationMixer.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationProducer.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioVoiceRegistry.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationSourceFactory.java`
- Modify: `src/main/java/com/openggf/audio/presentation/PresentationVoiceSnapshot.java`
- Modify: `src/main/java/com/openggf/audio/presentation/SampleBackedVoice.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationSnapshot.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationDependencyResolver.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationCommandResolver.java`
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`
- Modify: `src/test/java/com/openggf/audio/session/TestSmpsDriverSession.java`
- Create: `src/test/java/com/openggf/audio/session/TestSmpsSessionTransitionMatrix.java`
- Modify: `src/test/java/com/openggf/audio/session/TestSmpsSessionSnapshot.java`
- Create: `src/test/java/com/openggf/audio/session/TestSmpsSessionDiagnostics.java`
- Modify: `src/test/java/com/openggf/audio/presentation/TestAudioPresentationProducer.java`
- Modify: `src/test/java/com/openggf/audio/presentation/TestAudioPresentationProducerRewind.java`
- Modify: `src/test/java/com/openggf/audio/TestAudioPresentationSnapshotParity.java`
- Modify: `src/test/java/com/openggf/audio/TestMusicOverrideRestore.java`
- Modify: `src/test/java/com/openggf/audio/TestDonorAudioRouting.java`

**Interfaces:**

```java
public enum SmpsServiceOutcome { ORDINARY, GLOBAL_STOP_CONSUMED }

public interface PcmPresentationVoice extends PresentationVoice {
    void mixInto(long[] accumulation, int stereoFrames);
}

public record SmpsDriverSessionSnapshot(
        boolean initialized,
        SmpsPendingGlobalCommand pendingGlobalCommand,
        SmpsSessionProfileFingerprint profile,
        SmpsSourceDescriptor selectedDacSource,
        SmpsPhysicalDevice.Snapshot physical) { }
```

- `AudioPresentationSnapshot` carries exactly one `SmpsDriverSessionSnapshot` and, separately,
  exactly one logical `SmpsDriverSnapshot`. `PresentationVoiceSnapshot` no longer carries
  `LegacySmpsDriverSnapshot` after this atomic cutover.
- `SampleBackedVoice` implements `PcmPresentationVoice`; SMPS logical nodes never do.
- The session constructs one driver exactly once at `install()`. Factory commands prepare
  `PreparedSmpsMusicActivation` values containing the immutable logical memento, transition
  metadata, and resolved DAC selection.
- `serviceForward()` consumes retained stop first, otherwise activation then logical service.
- Producer owns a composite transaction token over session and registry. On
  `GLOBAL_STOP_CONSUMED`, registry clears sample/raw state without writes before commit.
- Mixer renders the session device once for the complete `sourceFramesNeeded` span, then mixes
  only `PcmPresentationVoice` instances.

The compile-time cutover API is:

```java
public final class SmpsDriverSession implements AutoCloseable {
    public void install();                         // exactly once
    public SmpsServiceOutcome serviceForward();
    public int renderFrames(short[] target, int offsetSamples, int stereoFrames);
    public void queueActivation(PreparedSmpsMusicActivation activation);
    public void applyCommand(SmpsSessionCommand command);
    public void retainGlobalStop();
    public SmpsDriverSessionSnapshot captureSnapshot();
    public PreparedRestore prepareRestore(SmpsDriverSessionSnapshot sessionSnapshot,
            SmpsDriverSnapshot logicalSnapshot, DacDependencyResolver dependencies);
    public void commitRestore(PreparedRestore restore);
    public LiveMutationToken captureLiveMutation();
    public void commitLiveMutation(LiveMutationToken token);
    public void rollbackLiveMutation(LiveMutationToken token);
    public void publishCommittedDiagnostics();
    <T> T withPort(SmpsDriverServiceObserver.DriverIdentity owner,
            Function<SmpsPhysicalPort, T> action); // package-private
    @Override public void close();
}
```

`PreparedSmpsMusicActivation` validates that `incomingMusic.sfx()` is false, and that the physical
activation source, incoming-program source, selected-DAC source, source generation, and dependency
generation agree before publication. `PreparedSmpsSfxProgram` requires `incomingSfx.sfx()` and
valid unsigned continuous metadata. There is exactly one logical-policy field: the one on
`PreparedSmpsMusicActivation`.

The producer maps every existing SMPS presentation command to `SmpsSessionCommand`; there is no
fallback driver lookup. `AdmitSfx` covers both new admission and same-id continuous extension and
opens one scoped physical epoch while the persistent driver prepares/commits channel takeover.
`StopAllSfx` opens the same bounded epoch for source-owned channel restoration. Stop/fade,
override pop/end, speed, tempo, and ring-alternation mutate the persistent logical driver within
the producer transaction; any exact writes they require use that command's scoped epoch.
Replace/push music publish a validated prepared activation and defer activation writes to the next
forward `serviceForward`. Retained global stop remains a separate higher-priority deferred command.
`SetSpeedShoes` retains the source-owned S1/S2 tempo-swap semantics and is distinct from semantic
`SetSpeedMultiplier`. `HardReset` transactionally clears persistent logical/session pending state,
invalidates saved overrides and continuous SFX state, and applies the base policy initialization
program through one scoped epoch; rollback restores the pre-reset device and driver identity.

`install()` creates the persistent `SmpsDriver` with a session-owned restricted
`SmpsSessionSynthesizerAccess implements SmpsDriverSessionAccess`, where the public marker
interface extends `SmpsLogicalWriteTarget`. Its descriptor-aware `selectDac` creates the required
`SmpsDacSelection`; it never accepts anonymous DAC data. The access object stores no
`SmpsPhysicalPort`; every synthesizer call requires the current scoped `withPort` epoch and routes
through that ephemeral capability. It exposes no render, snapshot, boot, stop, or control methods.
`SmpsDriver.createSessionDriver(SmpsDriverSessionAccess sessionAccess)` is the public cross-package factory and
the only constructor path that does not allocate a private synth. It is guarded as a named
composition-root seam, and the architecture guard permits calls only from `SmpsDriverSession`.
This preserves one
driver identity without creating a second chip pair or making a physical port durable.

At cutover, remove `PresentationVoiceSnapshot.Smps` entirely. `AudioPresentationSnapshot` owns the
single logical/session pair; `PresentationVoiceSnapshot` contains PCM/sample/raw variants only.
`SmpsCompositeVoice` becomes a deprecated standalone compatibility wrapper that is neither a
`PresentationVoice` nor accepted/recreated by `AudioPresentationSourceFactory`; the factory's
SMPS methods return `PreparedSmpsMusicActivation` or `SmpsSessionCommand`, and
`AudioVoiceRegistry` stores only presentation command/handle metadata, never channel claims,
arbitration state, a driver, or a physical snapshot. All channel ownership stays in the persistent
driver. Task 6 removes this wrapper after all direct callers migrate.

- [ ] **Step 1: Write REDs for identity, one render, fast-forward, stop ordering, transitions,
  snapshot purity, observer buffering, controls, and profile replacement.**

```java
@Test void retainedStopCancelsActivationBeforeEitherCanInterleave() {
    session.queueActivation(replacement);
    session.retainGlobalStop();
    assertEquals(SmpsServiceOutcome.GLOBAL_STOP_CONSUMED, producer.presentForward());
    assertEquals(stopProgram.writes(), writes);
    assertFalse(session.hasPendingActivation());
}

@Test void restoreMutatesThePersistentDriverAndReproducesNextPcm() {
    SmpsDriver identity = session.logicalDriverForTesting();
    SmpsDriverSession.PreparedRestore restore = session.prepareRestore(
            snapshot.smpsSession(), snapshot.smpsLogical(), dependencies);
    session.commitRestore(restore);
    assertSame(identity, session.logicalDriverForTesting());
    assertArrayEquals(expectedNextPcm, producer.presentForward().pcm());
}
```

Add the exact test names listed in design acceptance items 1, 4-12, 18-29 to the new session
test files. Parameterize the base/donor transition matrix in both directions; incoming music
policy decides SFX preservation/clearing while host policy identity stays fixed.

- [ ] **Step 2: Run the broad RED set and record every expected ownership failure.**

```bash
mvn -Dmse=off \
  '-Dtest=com.openggf.audio.session.**,com.openggf.audio.presentation.TestAudioPresentationProducer,com.openggf.audio.presentation.TestAudioPresentationProducerRewind,com.openggf.audio.TestAudioPresentationSnapshotParity,com.openggf.audio.TestMusicOverrideRestore,com.openggf.audio.TestDonorAudioRouting' \
  test -B
```

- [ ] **Step 3: Implement persistent logical ownership and source-owned transitions.**

No-music SFX enter the persistent driver's existing sequencer/lock tables. Music activation
consumes `PreparedSmpsMusicActivation` and applies its captured `SmpsLogicalTransitionPolicy`:
S1-compatible policy preserves source-owned
SFX state; S2/S3K-compatible policy clears it. Overrides save and restore ROM-defined logical RAM
regions inside that driver. Override pop emits no writes; the next service performs only the
source-owned reassertion program. Preparation and discard are write-free; retained stop cancels
the complete prepared activation without losing or re-resolving its dependencies.

- [ ] **Step 4: Implement the producer-owned transaction and diagnostic buffering.**

```java
PresentationMutationToken token = capturePresentationMutation();
try {
    for (SmpsSessionCommand command : drainResolvedSmpsCommands()) {
        smpsSession.applyCommand(command);
    }
    SmpsServiceOutcome outcome = smpsSession.serviceForward();
    if (outcome == SmpsServiceOutcome.GLOBAL_STOP_CONSUMED) {
        registry.clearForGlobalStopWithoutWrites();
    }
    token.commit();
    smpsSession.publishCommittedDiagnostics();
} catch (RuntimeException failure) {
    token.rollbackWithoutWrites();
    throw failure;
}
```

`capturePresentationMutation()` composes
`SmpsDriverSession.captureLiveMutation()` with
`AudioVoiceRegistry.captureLiveMutation()`. Both participants expose matching
`commitLiveMutation(token)` and `rollbackLiveMutation(token)` methods; rollback is observer-silent
and reverse-order. Only after both commits does the producer call
`smpsSession.publishCommittedDiagnostics()`.
Command draining therefore occurs strictly after both rollback tokens exist and before logical
service. A command/admission/dependency failure rolls session and registry back together; no
physical command write can precede the composite capture boundary.

Observer exceptions are caught by the diagnostic publisher, recorded in the error sink, and do
not escape or roll back committed state.

Producer close calls `SmpsDriverSession.close()` exactly once on the owner thread. Close invalidates
the current epoch, releases the device without emitting a global stop, and makes all later public
entry points fail before mutation.

- [ ] **Step 5: Centralize service/render and snapshot restore.**

Render exactly `sourceFramesNeeded` stereo frames once per outer forward presentation; buffer
offsets are interleaved-short sample offsets and return values are stereo-frame counts. Internal
chunking never services logical state again. `SILENT` applies commands but does not consume
retained stop, service, or render. Reverse uses history only. Restore preparation resolves DAC and
profile fingerprint, materializes a logical memento, and writes nothing. Commit restores that
memento into the existing driver, selects the resolved live DAC, restores the physical snapshot,
then reapplies session controls.

- [ ] **Step 6: Run session, full presentation, rewind, donor, allocation, and S1 hard gates.**

```bash
LUA_BIN=lua5.4 mvn -Dmse=off \
  "-Dsonic1.rom.path=${OGGF_REPO_ROOT}/s1.gen" \
  '-Dtest=com.openggf.audio.session.**,com.openggf.audio.presentation.TestAudioPresentationProducer,com.openggf.audio.presentation.TestAudioPresentationProducerRewind,com.openggf.audio.TestAudioPresentationSnapshotParity,com.openggf.audio.presentation.TestAudioVoiceRegistry,com.openggf.audio.TestMusicOverrideRestore,com.openggf.audio.TestDonorAudioRouting,com.openggf.audio.TestAudioDiagnosticObservers,com.openggf.audio.TestAudioPresentationAllocationBudget,com.openggf.tools.audio.parity.TestS1OpenGgfAudioCapture,com.openggf.tools.audio.parity.TestS1AudioParityFixtureContract' \
  test -B
```

Then run the mandatory S1 music and SFX parity gate block above; require both `MATCH` results.

- [ ] **Step 7: Run an independent cutover review; fix every Critical/Important finding before
  committing.**

```bash
git add src/main/java/com/openggf/audio/session \
  src/main/java/com/openggf/audio/driver/SmpsDriver.java \
  src/main/java/com/openggf/audio/driver/SmpsDriverSessionAccess.java \
  src/main/java/com/openggf/audio/presentation \
  src/main/java/com/openggf/audio/AudioManager.java \
  src/test/java/com/openggf/audio/session \
  src/test/java/com/openggf/audio
git commit -m 'refactor(audio): own SMPS presentation per session'
```

### Task 6: Enforce final ownership and migrate compatibility callers

**Files:**

- Create: `src/main/java/com/openggf/audio/session/OwnedSmpsAudioStream.java`
- Modify: `src/main/java/com/openggf/audio/AbstractSmpsAudioBackend.java`
- Modify: `src/main/java/com/openggf/audio/debug/SoundTestApp.java`
- Modify: `src/main/java/com/openggf/tools/audio/PsgSfxRenderTool.java`
- Modify: `src/main/java/com/openggf/tools/audio/FmSfxRenderTool.java`
- Modify: `src/main/java/com/openggf/tools/audio/parity/S1OpenGgfAudioCapture.java`
- Modify: `src/main/java/com/openggf/tools/audio/parity/S1OpenGgfSfxAudioCapture.java`
- Modify: `src/main/java/com/openggf/tools/audio/parity/s2/S2OracleEngineCapture.java`
- Modify: `src/main/java/com/openggf/tools/audio/parity/s3k/S3kOpenGgfAudioCapture.java`
- Modify: `src/main/java/com/openggf/audio/driver/SmpsDriver.java`
- Modify: `src/main/java/com/openggf/audio/presentation/SmpsCompositeVoice.java`
- Modify: `src/main/java/com/openggf/audio/presentation/PresentationVoiceSnapshot.java`
- Delete: `src/main/java/com/openggf/audio/rewind/LegacySmpsDriverSnapshot.java`
- Modify: `src/test/java/com/openggf/audio/TestAudioPresentationArchitectureGuard.java`
- Create: `src/test/java/com/openggf/audio/session/TestSmpsSessionArchitectureGuard.java`
- Modify: direct tool/capture tests corresponding to each migrated caller.

**Interfaces:**

```java
public final class OwnedSmpsAudioStream implements AudioStream, AutoCloseable {
    private final SmpsDriverSession session;

    public SmpsDriver logicalDriver();
    @Override public int read(short[] buffer);
    @Override public int read(short[] buffer, int length);
    @Override public void close();
}
```

Every adapter instance owns exactly one private session/device/driver. It is never accepted by
presentation factories. Authoritative presentation code has no direct `new SmpsDriver`,
`new VirtualSynthesizer`, or `SmpsCompositeVoice.render` path.

- [ ] **Step 1: Write staged architecture REDs.**

```java
@Test void presentationCannotConstructPrivateChipPairsOrStorePhysicalPorts() {
    assertNoProductionReference("audio/presentation", "new VirtualSynthesizer");
    assertNoProductionReference("audio/presentation", "new SmpsDriver");
    assertNoFieldOfType(SmpsDriver.class, SmpsPhysicalPort.class);
}

@Test void logicalSnapshotsAndVoicesCannotRenderOrOwnPhysicalState() {
    assertNoRecordComponent(SmpsDriverSnapshot.class, VirtualSynthesizer.Snapshot.class);
    assertFalse(PcmPresentationVoice.class.isAssignableFrom(SmpsCompositeVoice.class));
}
```

Extend the existing whole-production dependency guard, not a presentation-only text scan. Inspect
the compiled production graph and declared fields/record components so that no authoritative
presentation, registry, factory, manager, logical-driver, sequencer, or snapshot type can own or
construct `VirtualSynthesizer`, `SmpsPhysicalDevice`, or `SmpsPhysicalPort` except the named
session composition root. The standalone `OwnedSmpsAudioStream` adapter is allowlisted only as an
isolated one-session owner; source-string helpers remain supplemental diagnostics.

- [ ] **Step 2: Run guards and compile-sensitive direct callers as RED.**

```bash
mvn -Dmse=off \
  '-Dtest=com.openggf.audio.session.TestSmpsSessionArchitectureGuard,com.openggf.audio.TestAudioPresentationArchitectureGuard,com.openggf.tools.audio.parity.**,com.openggf.tools.audio.parity.s2.**,com.openggf.tools.audio.parity.s3k.**' \
  test -B
```

- [ ] **Step 3: Move every direct caller to `OwnedSmpsAudioStream` and remove transitional
  wrappers.**

The adapter selects its physical and logical policies explicitly from the caller's profile. It
initializes once, preserves current direct-read service cadence, and closes without issuing a
global stop. Remove `SmpsDriver` physical render/control/snapshot forwarding that no supported
caller needs. Remove legacy combined snapshots from presentation and tools.

- [ ] **Step 4: Run all direct tools, parity captures, architecture guards, allocation tests, and
  S1 hard gates.**

```bash
LUA_BIN=lua5.4 mvn -Dmse=off \
  "-Dsonic1.rom.path=${OGGF_REPO_ROOT}/s1.gen" \
  '-Dtest=com.openggf.audio.session.**,com.openggf.audio.TestAudioPresentationArchitectureGuard,com.openggf.audio.TestAudioPresentationAllocationBudget,com.openggf.tools.audio.parity.**,com.openggf.tools.audio.parity.s2.**,com.openggf.tools.audio.parity.s3k.**' \
  test -B
```

Then run the mandatory S1 music and SFX parity gate block above; require both `MATCH` results.

- [ ] **Step 5: Independently review for any second device/driver path, then commit.**

```bash
git add src/main/java/com/openggf/audio/session/OwnedSmpsAudioStream.java \
  src/main/java/com/openggf/audio/AbstractSmpsAudioBackend.java \
  src/main/java/com/openggf/audio/debug/SoundTestApp.java \
  src/main/java/com/openggf/tools/audio \
  src/main/java/com/openggf/audio/driver/SmpsDriver.java \
  src/main/java/com/openggf/audio/presentation \
  src/test/java/com/openggf/audio/session/TestSmpsSessionArchitectureGuard.java \
  src/test/java/com/openggf/audio/TestAudioPresentationArchitectureGuard.java
git commit -m 'refactor(audio): enforce session SMPS ownership'
```

### Task 7: Add exact S3K physical policy and global-command semantics

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/audio/Sonic3kSmpsPhysicalPolicy.java`
- Create: `src/main/java/com/openggf/game/sonic1/audio/Sonic1SmpsCompatibilityPolicy.java`
- Create: `src/main/java/com/openggf/game/sonic2/audio/Sonic2SmpsCompatibilityPolicy.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/audio/Sonic3kAudioProfile.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/audio/Sonic3kSmpsConstants.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kMonitorObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/shared/objects/SpeedShoesTimer.java`
- Modify: `src/main/java/com/openggf/audio/AbstractAudioProfile.java`
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`
- Modify: `src/main/java/com/openggf/audio/rewind/AudioCommand.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationCommand.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationCommandResolver.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioVoiceRegistry.java`
- Modify: `src/main/java/com/openggf/audio/session/SmpsDriverSession.java`
- Modify: `src/main/java/com/openggf/tools/audio/parity/s3k/S3kOpenGgfAudioCapture.java`
- Create: `src/test/java/com/openggf/audio/session/TestSmpsPhysicalPolicy.java`
- Create: `src/test/java/com/openggf/audio/session/ExactWriteProgramFixture.java`
- Create: `src/test/resources/audio/parity/s3k/s3k-stop-all-write-program.v1.json`
- Modify: `src/test/java/com/openggf/audio/TestSegaPcmCommandRouting.java`
- Modify: `src/test/java/com/openggf/audio/session/TestSmpsDriverSession.java`
- Create:
  `src/test/java/com/openggf/game/sonic3k/audio/TestSonic3kSpeedShoesCommandSemantics.java`
- Modify:
  `src/test/java/com/openggf/tools/audio/parity/s3k/TestS3kAudioOracleFixtureContract.java`

**Interfaces:**

- S3K command constants become source-correct: E0 stop, E1/E5 fade, E2 global stop, E3 PSG
  silence, E4 state-derived SFX-only stop, and FE stop SEGA PCM plus retained global stop.
  Commands resolve identically from the music and SFX mailboxes.
- Speed shoes model the source's direct `zTempoSpeedup` writes of 8/0 as engine-semantic
  multipliers 8/1 and never route through E2/E3.
- `Sonic3kSmpsPhysicalPolicy.boot()` returns 85 exact writes.
- `stopAll()` returns 84 exact writes.
- S1/S2 compatibility policies preserve the current exact 202-write stop until independently
  source-closed replacements land.
- E4 is not an immutable physical-policy program: it walks seven live SFX slots and emits
  state-derived teardown/restoration writes. Its logical session operation preserves music,
  overrides, tempo/speed state, continuous-SFX globals, ring state, pending activation, selected
  DAC and raw SEGA PCM. Exact E4 write parity remains a named frontier unless that slot walk and
  its FM/PSG restoration are ported in full.
- E3 must never alias speed-off. If its transient four-write PSG-silence behavior is not completed
  here, expose it as an explicit unsupported/reference-limitation result without false state
  mutation.

- [ ] **Step 1: Write literal exact-program and command-routing REDs.**

```java
@Test void s3kBootAndStopProgramsMatchShippedOrder() {
    List<SmpsChipWrite> expectedStop84 =
            ExactWriteProgramFixture.load("audio/parity/s3k/s3k-stop-all-write-program.v1.json");
    assertEquals(85, policy.boot().writes().size());
    assertEquals(new Ym2612(1, 0x82, 0xff), policy.boot().writes().getFirst());
    assertEquals(new Ym2612(0, 0x2b, 0x00), policy.boot().writes().getLast());
    assertEquals(expectedStop84, policy.stopAll().writes());
}

@Test void feStopsRawNowAndConsumesOneStopOnNextForward() {
    audio.playSegaPcm();
    audio.stopSegaPcm();
    assertFalse(registry.hasRawSegaPcm());
    assertTrue(writes.isEmpty());
    producer.presentForward();
    assertEquals(expectedStop84, writes);
    producer.presentForward();
    assertEquals(84, writes.size());
}
```

Add REDs proving that E4 is routed from both music and SFX mailboxes, releases only active SFX
ownership, and preserves continuous-SFX globals, music/override state, speed/tempo, ring state,
pending activation and raw SEGA PCM. Do not assert one fixed E4 write list. Add source-backed speed
pickup/expiry REDs for semantic 8/1 with no E2/E3 timeline command, and an E3 RED proving it cannot
mutate speed or masquerade as a global/SFX stop.

The immutable expected tuple fixture cites S3K `zStopAllSound` in
`docs/skdisasm/Sound/Z80 Sound Driver.asm` (design anchors D:2460-2521, shipped
`fix_sndbugs=0`). Assert channel order 6,0,1,2,4,5; PSG `$9F,$BF,$DF,$FF`; YM-I
`$2B=$00`; YM-I `$27=$00`. Assert the separate 68k tick-3 PSG burst is absent.
Before transcribing or reviewing this fixture, run
`git submodule update --init docs/skdisasm` and verify the pinned submodule commit; never substitute
an online or locally edited source tree.
`ExactWriteProgramFixture` validates the fixture schema/version, rejects unknown write kinds or
out-of-range YM/PSG values, preserves tuple order, and returns the complete immutable list; tests
also assert the literal tuple count and boundary writes so a truncated fixture cannot self-certify.

- [ ] **Step 2: Run exact policy, FE/E2/E4, speed, donor-host, and S3K oracle REDs.**

```bash
mvn -Dmse=off \
  "-Ds3k.rom.path=${OGGF_REPO_ROOT}/s3k.gen" \
  '-Dtest=com.openggf.audio.session.TestSmpsPhysicalPolicy,com.openggf.audio.session.TestSmpsDriverSession,com.openggf.audio.TestSegaPcmCommandRouting,com.openggf.audio.TestDonorAudioRouting,com.openggf.game.sonic3k.audio.TestSonic3kSpeedShoesCommandSemantics,com.openggf.tools.audio.parity.s3k.TestS3kAudioOracleFixtureContract' \
  test -B
```

- [ ] **Step 3: Implement immutable policy programs and source-correct command identities.**

Route FE and E2 to the same retained session command, submitted exactly once from either mailbox.
FE removes raw PCM immediately. Global stop service emits once even with no logical program,
returns `GLOBAL_STOP_CONSUMED`, clears every logical save area, and lets the producer transaction
clear sample/raw state. Implement E4 as a distinct logical driver/session operation over current
SFX ownership; do not reuse broad registry/raw cleanup and do not add it to the immutable physical
policy. Preserve the source globals and non-SFX state listed above. If the exact seven-slot
teardown/restoration walk is not ported, record exact E4 writes as the next frontier rather than
claiming parity. Keep E3 a named unresolved PSG-silence frontier if the source-owned mutation is
not completed in this task; do not map it to speed control. Route speed pickup/expiry through
semantic multiplier operations (8/1), not sound-command IDs.

- [ ] **Step 4: Make the oracle use the production policy.**

Delete the duplicate 84/85 emitter from `S3kOpenGgfAudioCapture`; inject or call the immutable
production program. The oracle remains a consumer/checker and cannot become an alternate policy
owner.

- [ ] **Step 5: Run exact S3K gates, S1/S2 compatibility stops, S1 hard gates, and focused audio.**

```bash
LUA_BIN=lua5.4 mvn -Dmse=off \
  "-Dsonic1.rom.path=${OGGF_REPO_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${OGGF_REPO_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${OGGF_REPO_ROOT}/s3k.gen" \
  '-Dtest=com.openggf.audio.session.**,com.openggf.audio.TestSegaPcmCommandRouting,com.openggf.audio.TestDonorAudioRouting,com.openggf.game.sonic3k.audio.TestSonic3kSpeedShoesCommandSemantics,com.openggf.tools.audio.parity.**,com.openggf.tools.audio.parity.s2.**,com.openggf.tools.audio.parity.s3k.**' \
  test -B
```

Then run the mandatory S1 music and SFX parity gate block above; require both `MATCH` results.

- [ ] **Step 6: Independently review source attribution and write order, then commit.**

```bash
git add src/main/java/com/openggf/game/sonic3k/audio \
  src/main/java/com/openggf/game/sonic1/audio/Sonic1SmpsCompatibilityPolicy.java \
  src/main/java/com/openggf/game/sonic2/audio/Sonic2SmpsCompatibilityPolicy.java \
  src/main/java/com/openggf/game/sonic3k/objects/Sonic3kMonitorObjectInstance.java \
  src/main/java/com/openggf/game/shared/objects/SpeedShoesTimer.java \
  src/main/java/com/openggf/audio \
  src/main/java/com/openggf/tools/audio/parity/s3k/S3kOpenGgfAudioCapture.java \
  src/test/java/com/openggf/audio/session/ExactWriteProgramFixture.java \
  src/test/java/com/openggf/audio/session/TestSmpsPhysicalPolicy.java \
  src/test/resources/audio/parity/s3k/s3k-stop-all-write-program.v1.json \
  src/test/java/com/openggf/audio/TestSegaPcmCommandRouting.java \
  src/test/java/com/openggf/game/sonic3k/audio/TestSonic3kSpeedShoesCommandSemantics.java \
  src/test/java/com/openggf/tools/audio/parity/s3k/TestS3kAudioOracleFixtureContract.java
git commit -m 'fix(audio): apply shipped S3K device commands'
```

### Task 8: Prove override-resume and cross-game transition boundaries

**Files:**

- Modify source-owned logical transition policies from Task 5.
- Create: `tools/audio/run_smps_override_resume_parity.sh`
- Create: `tools/audio/probes/s1_audio_override_resume_probe.lua`
- Create: `tools/audio/probes/s2_audio_override_resume_probe.lua`
- Modify: `src/test/java/com/openggf/audio/session/TestSmpsSessionTransitionMatrix.java`
- Modify: `src/test/java/com/openggf/audio/TestMusicOverrideRestore.java`
- Create: `src/test/java/com/openggf/tools/audio/parity/TestS1OverrideResumeAudioOracle.java`
- Create: `src/test/java/com/openggf/tools/audio/parity/s2/TestS2OverrideResumeAudioOracle.java`
- Create authenticated movies and compressed references under
  `src/test/resources/audio/parity/s1/` and `src/test/resources/audio/parity/s2/` only through the
  regenerable producer below.
- Modify: `docs/status/audio-frontier-log.md` only if an authenticated frontier moves.
- Add a validation record below `docs/architecture/validation/audio/` when both boundaries are
  source-closed.

**Interfaces:**

- Produces exact first-service writes and next PCM after S1/S2 override restoration.
- Produces a complete 3×3 host/donor matrix in both base-music-after-donor-SFX and
  donor-music-after-base-SFX directions.

- [ ] **Step 1: Capture source-backed S1/S2 override-resume windows without deriving engine
  constants from the fixture.**

Use the pinned TraceChaser/BizHawk producer path and external output root. Record ROM/BK2 hashes,
source routine, capture command, service boundary, and expected write/PCM digest. If current
fixtures do not exercise the boundary, add a bounded authenticated movie/window rather than a
synthetic fitted constant.

The bounded committed inputs/outputs are:

- `src/test/resources/audio/parity/s1/s1-override-resume.bk2`
- `src/test/resources/audio/parity/s1/s1-override-resume-reference.v1.jsonl.gz`
- `src/test/resources/audio/parity/s2/s2-override-resume.bk2`
- `src/test/resources/audio/parity/s2/s2-override-resume-reference.v1.jsonl.gz`

The wrapper validates official BizHawk 2.11, TraceChaser gitlink, ROM and BK2 hashes, Lua 5.4,
external output roots, two deterministic producer captures, and compressed-fixture metadata. Run:

```bash
tools/audio/run_smps_override_resume_parity.sh --game s1 \
  --rom ${OGGF_REPO_ROOT}/s1.gen \
  --movie src/test/resources/audio/parity/s1/s1-override-resume.bk2 \
  --output-root ${OGGF_AUDIO_OUTPUT_ROOT}/s1-override-resume

tools/audio/run_smps_override_resume_parity.sh --game s2 \
  --rom ${OGGF_REPO_ROOT}/s2.gen \
  --movie src/test/resources/audio/parity/s2/s2-override-resume.bk2 \
  --output-root ${OGGF_AUDIO_OUTPUT_ROOT}/s2-override-resume
```

Implement the wrapper and both probes first, record each movie rather than synthesizing inputs,
run each producer twice, compare its two raw outputs byte-for-byte, and only then write the
compressed reference plus manifest metadata. The wrapper refuses to overwrite a committed
fixture unless `--publish-fixture` is explicitly passed and all identity checks succeed.

- [ ] **Step 2: Write RED transition and exact-next-PCM tests.**

```java
@ParameterizedTest
@MethodSource("allIncomingMusicPoliciesAndExistingSfxSources")
void incomingMusicOwnsTransitionWithoutReplacingSession(Case testCase) {
    Identity before = fixture.identities();
    fixture.startExistingSfx(testCase.sfxSource());
    fixture.startMusic(testCase.musicSource());
    assertEquals(testCase.expectedSfxDisposition(), fixture.sfxDisposition());
    assertEquals(before, fixture.identities());
}
```

The oracle methods are exactly
`TestS1OverrideResumeAudioOracle.exactFirstServiceAndNextPcmMatch` and
`TestS2OverrideResumeAudioOracle.exactFirstServiceAndNextPcmMatch`. Each verifies the committed
reference metadata/hash before comparing the first resumed service writes and next PCM digest.

- [ ] **Step 3: Implement only source-owned transition/resume behavior exposed by the first
  divergence.**

No generic refresh write is permitted. Update the game-owned transition policy with the exact
saved logical fields and first-service operations from the cited routine.

- [ ] **Step 4: Run transition matrix, override, S1/S2 oracle, and S1 hard gates.**

```bash
LUA_BIN=lua5.4 mvn -Dmse=off \
  "-Dsonic1.rom.path=${OGGF_REPO_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${OGGF_REPO_ROOT}/s2.gen" \
  '-Dtest=com.openggf.audio.session.TestSmpsSessionTransitionMatrix,com.openggf.audio.TestMusicOverrideRestore,com.openggf.tools.audio.parity.**,com.openggf.tools.audio.parity.s2.**' \
  test -B
```

Then run both override-resume producer commands and the mandatory S1 music/SFX parity gate block;
require all comparisons to report `MATCH`.

- [ ] **Step 5: Review source closure and fixture provenance, update durable evidence, and commit.**

```bash
git add src/main/java/com/openggf/audio/session \
  src/main/java/com/openggf/game/sonic1/audio \
  src/main/java/com/openggf/game/sonic2/audio \
  src/test/java/com/openggf/audio/session/TestSmpsSessionTransitionMatrix.java \
  src/test/java/com/openggf/audio/TestMusicOverrideRestore.java \
  src/test/resources/audio/parity \
  docs/architecture/validation/audio \
  docs/status/audio-frontier-log.md
git commit -m 'fix(audio): restore source-owned SMPS transitions'
```

### Task 9: Resume authenticated S2/S3K first-divergence work

**Files:**

- Modify only the ROM-owned production source and focused tests identified by each comparator.
- Modify: `docs/status/audio-frontier-log.md`
- Modify affected behavior specs/gap analysis when evidence changes.
- Prepare proposed external human-listening queue rows without modifying the queue or marking any
  row heard; report the exact proposed rows to the human owner.

**Interfaces:**

- Consumes the existing production-owned validation framework and the session architecture.
- Produces an authenticated S2/S3K first divergence, `MATCH`, or honest
  `REFERENCE_LIMITATION`; never fixture-derived runtime behavior.

- [ ] **Step 1: Revalidate producer identities, TraceChaser gitlink, ROM/BK2 hashes, and external
  output-root policy.**

```bash
git submodule status tools/tracechaser
LUA_BIN=lua5.4 mvn -Dmse=off \
  '-Dtest=com.openggf.tools.audio.completerun.**,com.openggf.tools.audio.parity.s2.**,com.openggf.tools.audio.parity.s3k.**' \
  test -B
```

- [ ] **Step 2: Run the authenticated S2 comparison from its profile boundary and preserve only
  first-divergence evidence.**

Use profile `s2_rev01_complete_emeralds.v1`, interval `[769,259590)`, the verified REV01 ROM,
the pinned complete-emeralds BK2, and the fixed producer command from the parent roadmap. If a
layer remains unavailable, report `REFERENCE_LIMITATION` rather than inferring requests.

- [ ] **Step 3: Run the authenticated S3K Knuckles comparison from its profile boundary.**

Use profile `s3k_locked_on_knuckles_superemeralds.v1`, interval `[810,434417)`, and never run
the Sonic/Tails diagnostic under that identity. The tick-3 68k PSG burst remains outside the
Z80 policy.

- [ ] **Step 4: For each first divergence, execute the fixed source-closed loop.**

```text
comparator first divergence
  -> identify owning shipped routine/state
  -> write focused RED
  -> implement exact ROM behavior
  -> run focused GREEN
  -> run S1 hard gates
  -> rerun comparison from profile boundary
  -> update frontier ledger
```

- [ ] **Step 5: Stop only at equality, a proved limitation, or a concrete product gap with a
  recorded next RED.**

Never realign, skip a divergence, infer a request from output, hydrate runtime state from a trace,
or claim frontier movement from an unauthenticated producer.

### Task 10: Whole-roadmap verification, integration, and cleanup

**Files:**

- Modify: `docs/architecture/audits/audio/2026-08-31-sound-driver-re-current-state-audit.md`
- Modify: `docs/architecture/plans/audio/2026-08-31-sound-driver-roadmap-completion-plan.md`
- Create: `docs/architecture/validation/audio/2026-09-01-session-smps-integration-report.md`
- Create: `docs/architecture/audits/audio/2026-09-01-session-smps-end-to-end-review.md`
- Modify release/discrepancy/listening documents only when their mapped facts change.
- Modify: `README.md` during final merge only when the branch checked out in the main workspace is
  `develop`, as required by policy.

- [ ] **Step 1: Run an independent end-to-end requirements and architecture review.**

Trace all 29 design acceptance contracts to tests and current results. Confirm one physical
device, one logical driver, one render, one physical snapshot, host policy, incoming transition
policy, epoch authority, atomic global stop, observer quarantine, and profile fingerprint. Fix
every Critical/Important finding.

- [ ] **Step 2: Run focused session/audio/parity verification on JDK 21.**

```bash
LUA_BIN=lua5.4 mvn -Dmse=off \
  "-Dsonic1.rom.path=${OGGF_REPO_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${OGGF_REPO_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${OGGF_REPO_ROOT}/s3k.gen" \
  '-Dtest=com.openggf.audio.session.**,com.openggf.audio.**,com.openggf.tools.audio.parity.**,com.openggf.tools.audio.parity.s2.**,com.openggf.tools.audio.parity.s3k.**,com.openggf.tools.audio.completerun.**' \
  test -B
```

- [ ] **Step 3: Run ordinary and fresh-JVM guard suites with absolute verified ROM paths.**

```bash
LUA_BIN=lua5.4 mvn -Dmse=off \
  "-Dsonic1.rom.path=${OGGF_REPO_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${OGGF_REPO_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${OGGF_REPO_ROOT}/s3k.gen" test -B

LUA_BIN=lua5.4 mvn -Dmse=off -Pguards \
  "-Dsonic1.rom.path=${OGGF_REPO_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${OGGF_REPO_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${OGGF_REPO_ROOT}/s3k.gen" test -B
```

Record exact test/failure/error/skip counts and compare with the pre-migration baseline of
16,083 ordinary tests (0 failures/errors, 34 skips) and 587 guards (0 failures/errors/skips).
Any new failure blocks delivery; a changed total requires attribution.

- [ ] **Step 4: Refresh the six-stage roadmap evidence and human listening queue.**

Classify each stage as source map/spec, compared evidence, catalogue, implementation, and
validation/publication. Distinguish `MATCH`, first divergence, `REFERENCE_LIMITATION`, synthetic
contract, and fixture-assisted evidence. Do not mark listening rows heard.

- [ ] **Step 5: Follow the repository integration policy exactly.**

Resolve the branch currently checked out in the main workspace and treat it as the integration
branch. Fetch and fast-forward that branch without discarding user changes; record the updated
full-suite baseline; rerun the same full/focused suites in the development worktree; merge directly
into that main-workspace branch without switching it; update `README.md` only when that branch is
`develop`; rerun ordinary/guard comparison; push only that checked-out branch; then remove only
clean, fully merged worktrees/branches and prune metadata.

## Plan self-review checklist

- [ ] Every design section and all 29 acceptance contracts map to Tasks 1-10.
- [ ] Every new type is defined before a later task consumes it.
- [ ] No task shares the new physical device before the atomic Task 5 cutover.
- [ ] No code step contains a placeholder, inferred ROM value, or fixture-fitted behavior.
- [ ] Every task has RED, GREEN, focused regression, review, and commit boundaries.
- [ ] S1 music/SFX matches are explicit gates at every behavior-changing task.
- [ ] S2/S3K frontier work begins only after architecture and exact S3K policy land.
- [ ] Final delivery includes ordinary/guard comparison, docs, listening queue, integration,
  push, and verified cleanup.
