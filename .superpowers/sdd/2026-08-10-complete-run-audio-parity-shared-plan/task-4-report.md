# Task 4 report: pre-construction audio observers and authority guard

Status: DONE

## Delivered

- Added disabled-by-default `AudioAdmissionObserver` and
  `SmpsDriverServiceObserver` contracts. Service callbacks carry global
  ordinals and post-service snapshots; lifecycle callbacks cover driver
  construction, reset/pause/resume, stop, save/restore, and the reserved PCM
  boundaries needed by the game-specific follow-up plans.
- Added the game-neutral `SmpsRequestAdmissionPolicy`, with the exact
  permissive default selected through `GameAudioProfile`. The presentation and
  legacy backend paths evaluate it once per resolved SFX request before
  continuous-SFX, sequencer, DAC, or lock mutation. S1 therefore retains its
  existing driver-owned per-role contention semantics.
- Propagated admission, service, existing chip-write, and existing SFX
  contention observers from `AudioManager` into every subsequently constructed
  presentation or legacy SMPS driver, including first music, overrides,
  standalone SFX, and reconstructed/restored drivers. Installation occurs
  after the `SmpsDriver` constructor and before sequencer construction, so
  constructor silence writes remain unobserved while real sequencer writes are
  retained.
- Moved SFX admission notification after sequencer attachment and lock
  arbitration notification after lock/override mutation, without moving the
  ensuing YM/PSG write. Restored SFX admissions are emitted in snapshot order
  with fresh monotonic identities.
- Marked observer exceptions so resolver, command-mirror, and mixer fallback
  paths rethrow diagnostic failures instead of treating them as rejected or
  failed voices.
- Kept the disabled path literal: ordinary drivers retain the `NONE`
  sentinels, perform no SFX diagnostic bookkeeping, and skip service-snapshot
  capture entirely.
- Added a static authority guard forbidding complete-run tooling references
  outside production `tools/`, and scanning future complete-run OpenGGF
  producer constructor parameters for reference/expected/oracle/sidecar
  authority. A fixture test proves comments do not trigger the constructor
  check while a reference path does.

## TDD evidence

RED was observed before implementation when the focused tests could not compile
because the observer and policy interfaces/setters did not exist. Additional
focused REDs then pinned the subtle boundaries:

- admission notification initially ran before sequencer attachment;
- arbitration notification initially ran before lock mutation;
- ordinary drivers initially received wrapper observers even when public state
  was `NONE`;
- constructor-time chip observer exceptions were initially converted into
  presentation rejection warnings;
- the broad audio sweep exposed hash-order restored admissions and an uncached
  request reaching policy resolution before the existing cache-miss boundary.

Each RED was corrected at the owning boundary before the corresponding suite
was rerun.

## Verification

- `mvn -Dmse=off -Dtest=com.openggf.audio.TestAudioDiagnosticObservers,com.openggf.audio.synth.TestChipWriteObserver,com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.tools.audio.completerun.TestCompleteRunAudioAuthorityGuard test`
  - PASS: 25 tests, 0 failures, 0 errors.
- `mvn -Dmse=off '-Dtest=com.openggf.audio.AudioManager*,com.openggf.audio.TestAudioManager*,com.openggf.audio.TestAudioPresentation*,com.openggf.audio.TestShadowAudioPresentationRouting,com.openggf.audio.TestUnifiedAudioPresentationIntegration,com.openggf.audio.TestMusicOverrideRestore,com.openggf.audio.TestAudioLogicalSnapshot,com.openggf.audio.TestAudioBackend*,com.openggf.audio.presentation.TestAudioPresentation*,com.openggf.audio.driver.TestSfxContentionObserver,com.openggf.audio.driver.TestSmpsDriverSnapshot*,com.openggf.audio.synth.TestChipWriteObserver,com.openggf.audio.synth.Test*Snapshot' test`
  - PASS: 255 tests, 0 failures, 0 errors.
- `mvn -Dmse=off -Dtest=com.openggf.audio.driver.TestSfxContentionObserver,com.openggf.audio.driver.TestS1SfxTakeoverOrder,com.openggf.audio.smps.TestSmpsSfxConstructionPurity test`
  - PASS: 10 tests, 0 failures, 0 errors.
- `git diff --check`
  - PASS.

Maven's hook installer logged that the shared `.git/config` was read-only in
the sandbox. The Ant step is non-fatal and all requested tests completed on
JDK 21.

## Remaining scope and concerns

- The observer contract is intentionally pre-construction. Setting an observer
  after a driver already exists affects future drivers; complete-run producers
  must install observers before bootstrap/music construction as the design
  requires.
- This task only supplies the permissive policy seam. Source-accurate S1
  mailbox timing, S2 global request priority, S3K two-slot/continuous behavior,
  and game-specific PCM lifecycle emission remain owned by their later plans.
- No complete-run tooling type or reference reader/path was introduced into
  production audio behavior. No game-name checks were added.
- No merge or push was performed.

## Review fix round 1

The initial report's statement that constructor silence remains unobserved is
superseded by this review round. An observer supplied to the new synthesizer
constructor is now installed before `silenceAll()`, so every committed first,
override, standalone-SFX, legacy, and reconstructed driver reports its exact
198 YM2612 plus four PSG initialization writes. The uncommitted music blueprint
remains deliberately unobserved, avoiding a duplicate constructor stream.

Service callbacks moved from `SmpsDriver.read()` to the actual tempo-frame
service boundary. Zero-length and pre-boundary reads report nothing; each real
tick reports one begin/end pair, including multiple ordered pairs when one read
crosses multiple ticks. Begin precedes sequencer mutation and writes, while end
follows completion removal, lock release, silence writes, and the post-service
snapshot.

Prepared snapshot restore now opens one dependency-resolver diagnostic
transaction. Reconstructed drivers bind all chip, contention, service, and
lifecycle callbacks to a single ordered collector. Preparation and cleanup are
invisible; successful publication flushes the callbacks once in their original
cross-observer order; abandoned and failed preparations discard them.

`AudioDiagnosticObserverException` now searches cause and suppressed chains,
so cache/asset translation, restore rollback, reverse release, raw-PCM,
resolver, mirror, mixer, backend lifecycle, and cleanup catches cannot demote a
tooling failure into a cache miss, warning, failed voice, or `false` result.

The presentation and legacy paths now evaluate the game policy exactly once at
the resolved request boundary before later block/cache/continuous-SFX gates.
Late engine cache failures reclassify the already-evaluated admission without
running policy again; genuine pre-command asset-resolution failure runs no
policy because no request boundary exists. The default remains the exact
permissive singleton.

Lifecycle callbacks now carry stable driver identity/admission origin, scope,
and source. Driver stop mutations emit once per affected driver; aggregate
registry stop duplicates were removed. Registry music-override and reset
commands, session controls, and raw-PCM enter/leave use distinct non-driver
scopes. Standalone SFX, base music, and override music are covered together.

Review-round TDD evidence included RED failures for the missing constructor
observer constructor, read-level service callbacks, missing lifecycle identity,
blocked-before-policy behavior, and missing diagnostic transaction API. The
following final verification passed on JDK 21:

- Focused observer/chip/snapshot/contention/authority sweep: 40 tests, zero
  failures and zero errors.
- Broad AudioManager/backend/presentation/rewind/SFX contention/chip/snapshot
  sweep: 256 tests, zero failures and zero errors.
- S1 takeover-order and SFX-construction-purity sweep: 10 tests, zero failures
  and zero errors.
- `git diff --check`: clean.

The static guard additionally pins game-neutral shared diagnostic contracts.
No tooling import, game-name runtime check, snapshot field, game policy, chip
port order, lock behavior, or default-`NONE` PCM behavior was introduced.

## Review fix round 2

Service observation now surrounds each literal `SmpsSequencer.tick()` call,
not an inferred read or tempo-frame boundary. The callback event identifies
both the stable driver and the particular sequencer/source. Adversarial tempo
snapshots prove that an OVERFLOW2 tempo frame which executes zero, one, or
three real ticks emits exactly zero, one, or three ordered service pairs.
Tick-owned chip writes occur inside the pair, the end snapshot is post-tick,
and the established music-then-SFX tick order is unchanged.

Detached reverse command staging now owns an outer diagnostic transaction.
The factory supports nested restore savepoints over one cross-observer event
stream, while driver-instance ordinals are provisionally allocated inside the
same transaction. Detached reconstruction, admission/lifecycle events, staged
commands, and cleanup are discarded together. A later live restore therefore
flushes its constructor and restore events exactly once and receives the next
visible ordinal without a speculative gap. Both successful staging followed
by logical discard and failed staging followed by a real commit are covered at
the `AudioManager` boundary.

Resolved SFX commands now retain the data-resolved sound ID and special-SFX
classification independently of the mutable asset cache. The game policy is
therefore evaluated exactly once even if that already-resolved asset is
evicted before insertion. Every later cache, construction, or presentation
gate reclassifies the evaluated admission: it preserves the policy's resolved
ID and `priorityBefore`, and defines rejection `priorityAfter` as that same
unchanged pre-request priority. Custom nontrivial payload tests cover
presentation success, policy rejection, block, cache eviction, late
instantiation failure, and legacy success/block; a genuinely unresolved
loader failure still creates no request and runs no policy.

Round-two TDD recorded RED failures for the absent sequencer-bearing service
event, speculative staging event leakage and ordinal consumption, cache-null
policy bypass, and late rejection payload reconstruction. Final JDK 21
verification:

- Focused observer/chip/snapshot/authority sweep: 38 tests, zero failures and
  zero errors.
- Broad AudioManager/backend/presentation/rewind/registry/contention/chip/
  snapshot sweep: 310 tests, zero failures and zero errors (the prior
  256-test selection plus all 54 registry tests).
- S1 takeover-order, contention, and SFX-construction-purity sweep: 10 tests,
  zero failures and zero errors.
- `git diff --check`: clean.

No tooling import, game-name branch, reference-reader authority, snapshot
field, chip-port reorder, SFX lock change, or default-policy behavior was
introduced. No merge or push was performed.

## Review fix round 3

Service events now identify their authentic semantic boundary with the
profile-neutral `SEQUENCER_TICK`, `FADE_STEP`, and `COMPLETION_CLEANUP` kinds.
Each literal sequencer tick still emits its own begin/end pair. Fade processing
emits a separate pair only while it performs a real fade-state mutation, and
completion removal brackets lock release, override restoration, terminal
`forceSilence` writes, and the final post-cleanup snapshot. The typed kind is
preserved through both presentation and legacy diagnostic adapters.

SFX expiry is performed inside the final literal tick of its tempo frame. An
OVERFLOW2 speed-up frame therefore retains the shipped update frequency: three
literal ticks produce three tick services, while `maxTicks` changes only on the
third (`2, 2, 1`). A still-running FM SFX whose budget expires keys off before
that tick ends; subsequent force-silence writes occur only in the separately
ordered cleanup service. Adversarial chip observers fail if any YM2612 or PSG
write reachable from the exercised fade/tick/cleanup read paths appears outside
exactly one service pair.

Sequencer service identities are now live-set diagnostics rather than a history
table. The identity map is allocated lazily only when observation is enabled,
entries are forgotten on completion, replacement, conflict removal, stop, and
snapshot reconstruction, and live-command rollback retains only surviving live
sequencers. The allocation ordinal remains monotonic and is deliberately not
rewound or reset, so discarded identities are never reused. Package-private
diagnostics prove the retained count remains bounded by the live sequencer set.

Round-three RED evidence included missing service-kind compilation failures and
a deliberate retained-identity mutant that failed on the first completed SFX.
The lifecycle stress exercises 1,000 completions, 1,000 same-ID replacements,
and 1,000 snapshot reconstructions, then verifies strict identity ordering,
live-set bounds, rollback survivor stability, and no ordinal reuse. Final JDK
21 verification:

- Focused observer/chip/snapshot/fade/authority sweep: 56 tests, zero failures
  and zero errors.
- Broad AudioManager/backend/presentation/rewind/contention/chip/snapshot/fade
  sweep: 289 tests, zero failures and zero errors; the 54-test voice-registry
  suite also passed independently.
- S1 takeover-order, contention, and SFX-construction-purity sweep: 13 tests,
  zero failures and zero errors.
- `git diff --check`: clean.

No game-specific policy, tooling dependency, snapshot field, chip-port change,
lock-order change, or default-`NONE` audio behavior was introduced. Maven's
non-fatal hook installer warning remains the sandbox's read-only shared
`.git/config`; no merge or push was performed.
