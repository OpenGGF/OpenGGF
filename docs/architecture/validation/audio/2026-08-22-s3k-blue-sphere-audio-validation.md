# S3K Blue Sphere Source-Timed Audio Validation

Date: 2026-08-22
Disposition: automated gates complete; merge and push blocked on listening

## Scope and identity

This report validates the local branch
`feature/ai-smps-playback-verification` through the Task 7 implementation HEAD
`dc8abae501297d6563d2885419f0e891c9a5444d` plus the Task 8 verification fixes
and documentation in the final handoff commit. The exact final commit and JAR
SHA-256 are deliberately recorded in the listening handoff because a commit
cannot embed its own object id.

The source documents verified here are:

- design SHA-256:
  `c4736b7708e729c3d2006979fc018f48535c0bff8e3e47fa3139d2fa28e4f9ed`;
- plan SHA-256:
  `ffab40680ff718eaeae8c680d6f98871d336fa09db1d70bf03c7703acc43ec69`;
- feature base: `914ac9a87badbad5c574cd8edaadc81c743e390a`;
- pre-Task-8 implementation HEAD:
  `dc8abae501297d6563d2885419f0e891c9a5444d`.

The runs used Maven 3.9.16 on OpenJDK 21.0.11 from
`/usr/lib/jvm/java-21-openjdk`. The authenticated absolute ROM paths were
`$OPENGGF_MAIN_WORKSPACE/s1.gen`, `s2.gen`, and `s3k.gen`, with the
project-specified SHA-1 values `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`,
`8bca5dcef1af3e00098666fd892dc1c2a76333f9`, and
`cfbf98c36c776677290a872547ac47c53d2761d6` respectively.

## Native evidence

The retained lab was captured twice for S3K and twice for Sonic 2. Each pair
was byte-identical. No superseded diagnostic spike remained in `src`, `tools`,
or `docs`; the approved GPGX lab and its regeneration scripts remain tracked.

S3K evidence:

- observer patch SHA-256:
  `9c204d55e1c7524bf94180aa930d6be6a88e332d5227f187a2ed3d048b6bd375`;
- patched native core SHA-256:
  `3e2cddbb22c93676046f980926fd14d0689bb5bfd36ee75d0d630c2289b940a3`;
- compact oracle SHA-256:
  `5115c7e2bb5443ae7ccf1fa32d3d41dc1f77d17f086405e29bd3c258e96ee7e2`;
- raw write SHA-256:
  `8b55ae5833651fc3cdbe6caddee54dd604cbea2b7e906615e6edd55ddd9614d0`;
- six-column projection SHA-256:
  `33cef3472ad2c9c0d0d50e27f6ae574b51e02755420cd9c542b0443996013f99`;
- FM5 PCM SHA-256:
  `4277bc5f29fa086013b49f006fd887b9795ebfbb17e8288de4c50005bb97e6d8`;
- 12 groups, 34 writes per group, zero DMA stalls, zero observer fault, and
  zero overflow.

Every group carries this exact source-relative master-cycle vector:

```text
[0,3150,6300,9450,15885,19110,22875,26445,30015,33585,37155,
 40725,44295,47865,51435,55005,58575,62145,65715,69285,72855,
 76425,79995,83565,87135,90705,95850,99675,103500,107325,
 115380,146010,148710,151590]
```

The capture wrapper was corrected during final verification to publish the CPU
instruction ledger only for the S1/S2 instruction-audit games. S3K never emits
that ledger. A failing test demonstrated the unconditional publication bug
before the wrapper fix; its final SHA-256 is
`b518761c57e7123ad086e6560616929be5cf6a7d91280af4f61ce0d14f618b1e`.
Fresh S2 capture pairs remained byte-identical with compact-oracle SHA-256
`e3ce0fa19db864cfdbc79f7f53568c1cdb323ec8b7a1d536444f6e6f8ee9e56b`
and instruction-ledger SHA-256
`d03eed2d2679b2287c626c5098b96140c22e3746e425a23901ef023998826c3c`.
This refresh changed
provenance only, not the S2 ruling.

## Automated verification

The native/oracle Java gate was run twice with all three ROM properties:

```text
-Dtest=TestYm2612ChipGpgxParity,TestS1S2YmWriteTimingAudit,
TestSonic3kYmServiceTimingProfile,TestS3kBlueSphereSfxParity test
```

Both runs passed 51 tests with zero failures, errors, or skips. The explicit
rewind/observer/cadence/presentation group passed 263 tests with zero failures,
errors, or skips. It covered S3K service order and contention, transactional
timeline publication, modulation, pause, fade, one-up and speed state, PAL
cadence, ring panning, special-stage speed, OpenAL packetization, speaker FIFO,
deterministic synth/driver/sequencer/presentation snapshots, and observer
rollback. The Task 7 three-ROM audio selector passed 102 tests with zero
failures, errors, or skips.

Final-verification tests also corrected two stale assumptions exposed by the
new delayed timeline. The bounded playback trace admits already-committed
predecessor writes, but now inspects the pending timeline's source descriptors
and segment kinds, rejects any FM5 music-voice programming before the Blue
Sphere source, and requires the exact reviewed 34-write sequence. The
architecture guard no longer exempts a helper globally by name: it admits only
the timing-dominated callsites and the one-capture implementation shape. Tests
were observed failing against both broader checks before the constraints were
implemented. The pre-review focused run actually passed 53 tests, not the 52
reported in the first handoff. The corrected focused group passes 57 tests with
zero failures, errors, or skips.

The exact focused commands in the correction round used this common prefix:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -Dmse=off \
  -Dsonic1.rom.path="$OPENGGF_MAIN_WORKSPACE/s1.gen" \
  -Dsonic2.rom.path="$OPENGGF_MAIN_WORKSPACE/s2.gen" \
  -Ds3k.rom.path="$OPENGGF_MAIN_WORKSPACE/s3k.gen"
```

The twice-run native/oracle selector appended:

```text
-Dtest=TestYm2612ChipGpgxParity,TestS1S2YmWriteTimingAudit,
TestSonic3kYmServiceTimingProfile,TestS3kBlueSphereSfxParity test
```

Each run passed 51 tests. The review selector appended
`-Dtest=TestAudioPresentationArchitectureGuard,TestS3kBlueSpherePlaybackTrace,TestS3kBlueSphereSfxParity test`
and passed 57 tests. The rewind/observer/cadence/presentation selector appended:

```text
-Dtest=TestSmpsSequencerSnapshot,TestS3kPalDriverCadence,
TestSmpsSequencerDriverCadence,TestS3kSpecialStageAudioPlaybackTrace,
TestAudioVoiceRegistry,TestVirtualSynthesizerSnapshot,TestSmpsFadeHybridParity,
TestSmpsPauseProtocol,TestSmpsDriverServiceOrder,TestSfxContentionObserver,
TestSmpsDriverYmWriteTimeline,TestS3kBlueSphereSfxParity,
TestSmpsDriverSnapshot,TestSpeakerPacketFifo,TestOpenAlPcmSink,
TestAudioPresentationSnapshotParity,TestSmpsSequencerFadeTiming,
TestAudioDiagnosticObservers,TestSonic3kUnifiedAudioPresentationRomIntegration test
```

It passed 263 tests. Log SHA-256 values are
`6f240ee0a0a8b7bc5d53cea3fea4ae43ecf5eec28ac2347549560b888715fbf1`
and `64bec44152d2da0a5437d82d3fa1f5b6344d8feb65700bbc2b4382fdd91eaa04`
for the oracle repetitions,
`aeb892b921c4e475e4bd474b8eee6ca93b657eaa8e06ce0f60c6bf346f12ebf7`
for the review selector, and
`66d9c857f095dc9b7e1dc7c6973918ff034b37f8681ede2568e846fa8024e5ca`
for the 263-test selector. All commands used Maven 3.9.16 and OpenJDK 21.0.11.

## Full-suite comparison

The earlier `d473365ed72facfffcd36d9e07af09666b094d37` result is not a
valid regression baseline: it is not an ancestor of this feature branch and
its merge base is `683b1a3984958b4b6ae53baf383a81bee6727078`.

The comparable baseline was therefore run in an isolated detached worktree at
the documented feature base `914ac9a87badbad5c574cd8edaadc81c743e390a`.
Its log SHA-256 is
`3c6266ba425ae8b20ddbf3c456c8b0d870c782a0511b4cc85fa821b4fc3e09bc`.
It ran 15,298 tests with 53 failures, 64 errors, and 18 skips.

The final three-ROM test command was:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -Dmse=off \
  -Dsonic1.rom.path="$OPENGGF_MAIN_WORKSPACE/s1.gen" \
  -Dsonic2.rom.path="$OPENGGF_MAIN_WORKSPACE/s2.gen" \
  -Ds3k.rom.path="$OPENGGF_MAIN_WORKSPACE/s3k.gen" test
```

The candidate ran 15,404 tests with 53 failures, 64 errors, and 18 skips. Its
log SHA-256 is
`a5612865fe4ba8f85ca4df0081f2762eddfebe73ec420419d4a9c6496b8e939b`.
The higher test total is the feature's added coverage, not a changed selector.

XML parsing compared the fully qualified class and test method plus failure
versus error status. Both baseline and candidate contain exactly 117 unique red
identities. The two sorted identity ledgers are byte-identical, each with
SHA-256
`584fe83dcf7311fbee0dce80c3308439608318b814a9c243e2f098786fcedaa5`.
No baseline-passing test fails, no baseline-red test changes failure class, and
no red identity is added, removed, or waived. This is the applicable regression
comparison for the feature branch.

## Cross-game ruling and limits

Sonic 1 and Sonic 2 keep `YmServiceTimingProfile.none()`. Their native
instruction audits establish different service owners and do not justify
copying S3K's source-cycle vector by symmetry. This is an explicit ruling, not
missing implementation.

The retained S3K evidence proves source-relative write spacing and ordering.
It does not prove absolute VInt phase, and the captured groups contain no DMA
contention. Accordingly this report makes no absolute-phase or DMA-contended
parity claim. Runtime timing comes from the profile and ROM-owned service work;
no trace data is consulted by gameplay.

## Listening handoff

Automated verification is complete, but this change is not approved for merge
or push. Listen to the exact packaged handoff commit for:

1. the first Blue Sphere pickup;
2. rapid consecutive pickups;
3. the first pickup after completion or a turn;
4. a pickup after another FM5 SFX;
5. ring pickups; and
6. special-stage entry while speed shoes were active.

Only a positive result lifts the integration gate.
