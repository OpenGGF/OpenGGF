# Sonic 2 complete-run audio frontier

## Result

Task 1 of the Sonic 2 complete-run audio parity plan is implemented on
`bugfix/ai-s2-complete-audio-frontier`. The closed profile dispatcher can now
resolve `s2_rev01_complete_emeralds.v1`, but both producer bindings deliberately
remain typed as unavailable.

This advances the S2 engine-side frontier from no game profile to a pinned
fixture, native-content identity table, and strict source-state normalizer. It
does not yet produce or compare an OpenGGF trace, so the reference-vs-engine
event frontier remains unmeasured.

## Pinned reference contract

- ROM SHA-1: `8bca5dcef1af3e00098666fd892dc1c2a76333f9`
- ROM CRC32: `7b905383`
- BK2 SHA-256: `e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5`
- manifest SHA-256: `dfb220822eab3c524472aa02d6d78463a9489233b97fdd9ccd9340c9f3a10411`
- comparison interval: `[769,259590)` over 35 segments and seven special
  stages
- proven duplicate native result: 259,590 frames, 169,986,419 events, maximum
  frame occupancy 1,825, digest prefix `c2b2f823`, and an empty cutoff frontier

## Source-derived state and identity model

The model follows the shipped `fixBugs=0` driver:

- `docs/s2disasm/s2.sounddriver.asm:31-227` defines `zComRange`, the 42-byte
  `zTrack`, `zVar`, the ten music tracks, six SFX tracks, and the ten-track
  saved-music payload.
- `docs/s2disasm/s2.sounddriver.asm:1496-1549` defines three-slot request
  priority and the transient jump-SFX priority.
- `docs/s2disasm/s2.sounddriver.asm:1667-1724` defines ordinary-music SFX stop
  behavior and the shipped one-up save order: globals and ten music tracks are
  copied before live priority is cleared.
- `docs/s2disasm/s2.sounddriver.asm:3823-3855` and
  `docs/s2disasm/s2.constants.asm:826-970` define the native playlist, SFX IDs,
  and driver commands.
- `Sonic2SmpsLoader` ROM addresses provide stable music content identities, so
  native driver IDs and the engine's diagnostic API IDs compare by ROM content
  rather than by numeric coincidence.

The normalizer rejects observations made mid-service, validates the exact
source-slot order, expresses live pointers as asset-relative cursors, suppresses
inactive stale bytes, retains the source layers as well as effective hardware
owners, and includes the complete saved one-up payload while it is active.

## RED/GREEN evidence

The focused command was run before production classes existed and failed at
test compilation on the missing `S2CompleteRunAudioProfile`,
`S2NativeSoundResolver`, and `S2CompleteRunStateNormalizer` types. After the
game-local implementation, the same command passed 10 tests:

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.tools.audio.completerun.s2.TestS2CompleteRunAudioFixture,com.openggf.tools.audio.completerun.s2.TestS2NativeSoundResolver,com.openggf.tools.audio.completerun.s2.TestS2CompleteRunStateNormalizer' \
  test
```

A broader 162-test gate covered the shared authority/schema/store/CLI/
comparator surface, the three S2 classes, and the ROM-backed Sonic 2 unified
audio presentation integration. It produced 161 passes and one pre-existing
checkout-mode failure: `TestCompleteRunAudioCli` requires
`tools/audio/run_complete_audio_parity.sh` to be executable, while the pinned
baseline records that file as mode `100644`. The S2 integration test and every
test affected by this task passed; this task does not own the shared script or
its file mode.

## Next boundary

The next ordered plan item is Task 2. Completing it requires the absent native
`S2AudioObserverProfile`, native `S2CompleteAudioCaptureRunner`, their native
tests, and headless `Program.cs` registration. Those files are outside this
worktree's S2 Java-only ownership boundary. The smallest central contract is to
install those native owners and expose their fixed Java reference adapter
binding without changing the shared trace schema. Until that central work is
rolled into the integration baseline, the S2 reference producer must remain
typed unavailable and Task 3 must not leapfrog Task 2.
