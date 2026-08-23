# S3K Collapse and Dash SFX validation

Status: implementation and automated verification green; listening and
integration are pending.

## Change

- Z80-family PSG note starts now publish only the post-modulation frequency,
  matching the single upload in `zUpdatePSGTrack`.
- Noise-latch/volume writes from a live PSG3/noise SFX use that logical track's
  PSG3 lock, avoiding a non-native mid-note noise silence.
- The diagnostic host gained bounded Z80 RAM writes so isolated native SFX
  lifecycles can be regenerated without Lua Z80 hooks or production changes.

No SFX ID, game name, zone, route, or measured waveform constant enters
production code. S1/S2 retain their existing modulation algorithms.

## Evidence

- Native injected lifecycle: Collapse active through frame 121; Dash active
  through frame 86; two quiet terminal frames each.
- Java ROM-backed lifecycle: 122 and 87 request-through-terminal updates.
- Effective native PSG state SHA-256:
  - Collapse: `d85bbd997725b5804d5990cb222f13a1c367ce2e76b628ab5ec61c515d81c584`
  - Dash: `0b7d78978c85bc7c021789c333594b96f905bbf2e64f1b2b3921751f2af1e093`

## Commands run

All Maven commands used Maven 3.9.16 on JDK 21.0.11.

```bash
mvn -Dmse=off -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=TestS3kCollapseDashSfxParity,TestS3kBlueSphereSfxParity,\
TestSonic3kCoordFlagParity,TestPreparedSfxAdmission,\
TestSmpsSequencerSnapshot,TestPsgChipSnapshot,TestVirtualSynthesizerSnapshot test
# 94 tests, 0 failures/errors/skips

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

The broad capability-class selector also exposes an existing combined service
manifest mismatch (`90cf...` expected, `0b96...` generated) on the integration
base. The isolated lifecycle selector is green and this change does not modify
the service manifest or native observer patch.

## Remaining gate

Package the clean feature HEAD and listen to Collapse through all five PSG
bursts and terminal silence, plus low/high-charge Dash release and a replay
after another SFX. Do not merge or push before that listening result.
