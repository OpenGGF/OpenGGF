# S3K Collapse and Dash SFX validation

Status: implementation and automated verification green; listening and
integration are pending.

## Change

- Z80-family PSG note starts now publish only the post-modulation frequency,
  matching the single upload in `zUpdatePSGTrack`.
- Noise-latch/volume writes from a live PSG3/noise SFX use that logical track's
  PSG3 lock, avoiding a non-native mid-note noise silence.
- Each retiring FM track now releases its hardware lock at its own
  `cfStopTrack` boundary. FM5's terminal key-off and interrupted music-voice
  restore remain one audited transaction even while a longer PSG sibling is
  active; whole-SFX cleanup does not repeat them.
- The diagnostic host gained bounded Z80 RAM writes so isolated native SFX
  lifecycles can be regenerated without Lua Z80 hooks or production changes.

No SFX ID, game name, zone, route, or measured waveform constant enters
production code. S1/S2 retain their existing modulation algorithms.

## Evidence

- Native injected lifecycle: Collapse active through frame 121; Dash active
  through frame 86; two quiet terminal frames each.
- Native `cfStopTrack` key-offs FM5 on Collapse frame 18 and Dash frame 16,
  while their PSG3 siblings remain active. Before the correction the engine
  emitted neither terminal FM5 key-off and kept the music channel overridden
  until whole-SFX cleanup (Collapse frame 121 / Dash frame 86).
- Native Collapse rendered stereo RMS is 2566.75 left / 2398.23 right
  (ratio 1.070). The engine moved from 5734.37 / 3118.86 (ratio 1.839) to
  3116.01 / 3139.11 (ratio 1.007) after restoring the FM5 terminal key-off.
- Java ROM-backed lifecycle: 122 and 87 request-through-terminal updates.
- Effective native PSG state SHA-256:
  - Collapse: `d85bbd997725b5804d5990cb222f13a1c367ce2e76b628ab5ec61c515d81c584`
  - Dash: `0b7d78978c85bc7c021789c333594b96f905bbf2e64f1b2b3921751f2af1e093`

### Final-speaker correction after the listening gate

The first listening package still ended Collapse too abruptly. A fresh native
A/B capture added final presented PCM after GPGX's ordinary audio drain. The
two 125-frame captures were byte-identical at SHA-256
`5c6bfe3382749fa31137128a3bfd87d191da17d6ad33ece8c39d360e428ce7a3`
with zero overflow/fault. Raw native PSG has RMS `44.695811` on frame 121
and is exactly silent from frame 122. Final native PCM nevertheless decays with
RMS `88.917`, `21.669`, `5.142`, `1.273` on frames 121-124. GPGX owns that
tail through its globally selected `hq_psg=1` band-limited delta path; the
global post-filter is `None`.

OpenGGF already contained the matching HQ `PsgChip` path but no SMPS runtime
selected it. The correction enables it in `SmpsDriver`, so S1, S2, and S3K all
follow the same console-level GPGX setting. Sound_59 still executes five
24-tick bursts and completes after the same 122 request-through-terminal
updates. The aligned OpenGGF post-mute RMS is `76.086`, `18.124`, `4.291`,
`1.064`; its decay ratios match the native response. Disabling HQ mode makes
the final-PCM comparison fail before the mute, while the semantic lifetime
test remains unchanged. The compact comparison-only evidence is
`docs/architecture/research/audio/s3k-collapse-final-pcm-hq-psg-v1.json`.

Fresh JDK 21 verification for this correction:

```bash
mvn -Dmse=off -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=TestS3kCollapseDashSfxParity,TestPsgChipGpgxParity test
# 27 tests, 0 failures/errors/skips

mvn -Dmse=off -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=TestS3kCollapseDashSfxParity,TestPsgChipGpgxParity,\
TestPsgChipSnapshot,TestVirtualSynthesizerSnapshot,TestSmpsDriverSnapshot,\
TestSmpsDriverYmWriteTimeline,TestAudioPresentationSnapshotParity,\
TestSonic1UnifiedAudioPresentationRomIntegration,\
TestSonic2UnifiedAudioPresentationRomIntegration,\
TestSonic3kUnifiedAudioPresentationRomIntegration,\
TestAudioPresentationAllocationBudget,TestSmpsSfxAdmissionAllocation test
# 114 tests, 0 failures/errors/skips
```

All three ROM SHA-1s matched the project pins. As mutation evidence, changing
the three `SmpsDriver` constructors back to fast mode made
`collapseFinalPcmRetainsTheReferencePostMuteRingDown` fail on the first aligned
native frame (`122.835` expected, `127.764` observed); restoring HQ returned the
focused suite to green.

## Commands run

All Maven commands used Maven 3.9.16 on JDK 21.0.11.

```bash
mvn -Dmse=off -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=TestS3kCollapseDashSfxParity,TestS3kBlueSphereSfxParity,\
TestSonic3kCoordFlagParity,TestPreparedSfxAdmission,\
TestSmpsSequencerSnapshot,TestPsgChipSnapshot,TestVirtualSynthesizerSnapshot test
# 97 tests, 0 failures/errors/skips

mvn -Dmse=off -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=TestSonic1UnifiedAudioPresentationRomIntegration,\
TestSonic2UnifiedAudioPresentationRomIntegration,\
TestSonic3kUnifiedAudioPresentationRomIntegration,TestS1SfxTakeoverOrder,\
TestSfxContentionObserver,TestSmpsGlobalSfxPriority,\
TestSmpsSfxConstructionPurity,TestSmpsSfxAdmissionAllocation test
# 28 tests, 0 failures/errors/skips

tools/bizhawk-headless/test.sh --filter GpgxHost --jobs 1
# bounded Z80 write test PASS

OPENGGF_GPGX_Z80_CAPABILITY=1 \
OPENGGF_GPGX_S3K_SFX_LIFECYCLE=1 \
tools/bizhawk-headless/test.sh \
  --filter 'capture injected S3K SFX lifecycles' --jobs 1
# PASS
```

The identical full JDK 21/all-three-ROM suite was run against exact parent
`e0c94ea181a03a87cc42d4317cd6eb4452106ef3` and implementation commit
`9a5e242a1`. The parent ran 15,500 tests with 56 failures, 56 errors, and 19
skips; the implementation ran 15,506 tests with 53 failures, 56 errors, and 19
skips. Its six additional tests were the initial Collapse/Dash parity cases;
the per-track terminal correction adds three more.

The sorted failure/error identity comparison contained no candidate-only red
test. The parent ledger has 112 identities (SHA-256
`86177f94f9f56c23a1d2da447b21de0c67800c6a4fd0e5825446f7a3eef8aa2b`);
the candidate ledger has 109 (SHA-256
`f1284d5aa7ed356866a508271609c6e36c8e64176efe489af4596038b55d81a0`).
The complete Maven log hashes are
`9289b3fe08e141021a8f3bff8839e8db36d35ada22dfc1577ad7037136192706`
for the parent and
`20841cbbc91aa65f9b01a090e8190e3860f140d664a80b9c54944b586f4ca2d1`
for the candidate. The three baseline-only failures are the AIZ initial-player
state, test-mode trace-picker loading screen, and Corkey registry cases; no
baseline-passing test regressed.

The broad capability-class selector also exposes an existing combined service
manifest mismatch (`90cf...` expected, `0b96...` generated) on the integration
base. The isolated lifecycle selector is green and this change does not modify
the service manifest or native observer patch.

The focused terminal/timeline regression after this correction used:

```bash
mvn -Dmse=off -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=TestS3kCollapseDashSfxParity,TestS3kBlueSphereSfxParity,\
TestSmpsDriverYmWriteTimeline,TestSmpsFmVoiceWriteProfiles test
# 77 tests, 0 failures/errors/skips
```

The combined S3K snapshot/admission and cross-game control selector ran
125 tests with zero failures, errors, or skips.

After the per-track terminal correction and structural call-site guard, the
fresh all-three-ROM suite ran 15,510 tests with 53 failures, 56 errors, and 19
skips. Its 109-entry sorted failure/error ledger is byte-identical to the
implementation ledger above (SHA-256
`f1284d5aa7ed356866a508271609c6e36c8e64176efe489af4596038b55d81a0`),
so the four additional terminal/guard tests introduced no new red identity.
The full log SHA-256 is
`f1c637ff6258dd097528d08bfc3f565631ef3297d3e5faa3200b1bb96f33201c`.

## Remaining gate

Listen to Collapse through all five PSG bursts and terminal silence, plus
low/high-charge Dash release and a replay after another SFX. The exact package
identity is reported in the handoff after building clean HEAD so the report
does not create a self-referential rebuild. Do not merge or push before the
listening result.
