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
game-local implementation, the same command passed 12 tests after review added
two applicability vectors:

```bash
mvn -Dmse=off \
  -Dtest='com.openggf.tools.audio.completerun.s2.TestS2CompleteRunAudioFixture,com.openggf.tools.audio.completerun.s2.TestS2NativeSoundResolver,com.openggf.tools.audio.completerun.s2.TestS2CompleteRunStateNormalizer' \
  test
```

The review-fix final tree reran that focused set together with the shared trace
and authority guards and the ROM-backed Sonic 2 unified-audio integration: 63
tests passed with no failures or errors. This is the same selection previously
reported as the 61-test gate, plus the two new union-byte applicability tests.

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

Fresh CLI JVM bootstrap has a separate shared dependency: static registration
in `S2CompleteRunAudioProfile` does nothing until that class is loaded. The
closed `CompleteRunAudioProducerRegistry` must reserve
`s2_rev01_complete_emeralds.v1` and name the fixed profile class so
`CompleteRunAudioProfiles.require(...)` can load it without an ambient
caller-selected class. Integration checkpoint `ef83b7e6b` already contains
that S2 dispatcher entry; this game-local commit depends on it remaining in the
integration baseline. If a target baseline lacks the entry, adding it is a
central shared-registry change, not an S2 profile workaround.

## Round 2: native observer profile

The first game-owned part of Task 2 is now implemented. The headless
`S2AudioObserverProfile` selects the reviewed S2 portion of the shared native
service manifest, rejects any change to the pinned manifest, movie, observer,
complete-run event, terminal-Z80, or cutoff-frontier identities, and verifies
the exact final observer installation before it can be used. Its configured
graph retains the source-derived `$0038` VInt, `$0110` music, and `$017A` DPCM
iteration boundaries while continuing to exclude the persistent `$0178`
busy-wait.

The installation verified in this round is
`bizhawk-2.11-gpgx-audio-observer-v2` / `gpgx-audio-observer-v2`, ABI 2,
BuildID `b49036a848890682`, observer identity
`1f0147ecc101d4d726ed09536db87c125f305eccdca986c620d735714543c5cc`.
The already-proven complete S2 evidence remains unchanged: 259,590 frames,
169,986,419 events, maximum occupancy 1,825, no open or pending service at
cutoff, and event digest
`c2b2f82374aaa16144b6bf121df051dcd5b4ba095431c16cf6224adc633de41d`.

Focused RED failed at compilation because `S2AudioObserverProfile` did not
exist. Focused GREEN used the final observer installation and passed all three
new profile tests. No new native capture was performed and no ROM, BK2, core,
or captured payload was copied or published.

The next exact boundary remains the S2-owned
`S2CompleteAudioCaptureRunner` and `S2CompleteRunReferenceProducer`. They must
serialize the observer's canonical services into the shared raw staging
contract before the profile may change its reference binding from typed
unavailable. Headless `Program.cs` and `TestMain`/project registration are
shared integration seams; this round changed only the minimal test/project
registration needed to exercise the S2-owned profile and leaves CLI routing to
the conductor.

## Round 3: bounded native capture runner

The next game-owned slice is now implemented as
`S2CompleteAudioCaptureRunner`. It authenticates Sonic 2 World REV01, the
read-only 259,590-row all-emeralds BK2, the reviewed service manifest and
capability, and the final ABI 3 observer installation before opening the host.
It observes and drains from power-on, starts publication at the fixed row-769
comparison boundary, and streams every row in `[769,259590)` to a bounded sink.
The exact-end path rejects both short and overlong movie streams; the bounded
proof path stops without consuming the preserved movie tail.

The shared manifest remains the legacy ABI 1 source description. The S2-owned
profile validates its exact `$EC000`/`$EC036` `SoundDriverLoad` begin/completion
chain, then enables only those two hooks for ABI 2 prepublication. This follows
`docs/s2disasm/s2.asm:91301-91323`: the unique routine owns the full driver
upload and reaches one common release after all 8 KiB are final. The transition
changes publication coordinates only; physical chip latches and native service
lifetime state remain continuous.

A real read-only power-on proof against
`crossing-install-final2-a` reached row 769 with publication armed at epoch 1,
one carried kind-4 DPCM service, no pending services, YM address latches
`$2A/$A1`, and 1,491 raw events in the first published row. The exact ROM and
BK2 identities remained
`8bca5dcef1af3e00098666fd892dc1c2a76333f9` and
`e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5`.
The already-reviewed complete evidence remains 259,590 frames, 169,986,419
events, maximum occupancy 1,825, and an empty cutoff frontier.

This is capture capability only. It does not add CLI routing, a shared raw
staging writer, or the fixed Java reference adapter, and the S2 profile's
reference binding remains typed unavailable. Adding the production C# class
necessarily changed the deterministic harness executable; the provisional
game-branch identity is recorded in the capability fixture while central
integration will perform the single final cross-game deterministic repin.
The clean two-root verifier reproduced production SHA-256
`9d3d9eb25c29fec4436149c802b3851657c74ea20b65df9bf22d60241d574d31`
and test SHA-256
`9c7c898f585942b6bd8c2864a1e2cd8ec7e50816cf400a557244ae2883dbd386`.
