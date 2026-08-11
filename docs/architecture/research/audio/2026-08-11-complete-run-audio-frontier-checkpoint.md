# Complete-run audio frontier checkpoint

This branch is an intentionally incomplete integration checkpoint for the
cross-game complete-run audio parity effort. It contains the reproducible GPGX
observer build/install toolchain, the native BizHawk headless bridge, the
lossless raw/semantic trace schema, and the first Sonic 1 reference producer.

The reviewed canonical native patch at this checkpoint is ABI v3, SHA-256
`eba32c88f0b1465de0a307a2cdd53e53e655e56e70a70ffc3a1e3b0cf1198e46`.
Build and create-new installation instructions are in
`tools/bizhawk-headless/native/gpgx-audio-observer/README.md`; trust-boundary
details are in the adjacent `TRUST.md`. No generated core or ROM is committed.

## Current frontier

- Sonic 1: the ABI v3 direct-parent-close action resolves the row-523 crossing
  lifetime without reordering physical events. At `$00AC` it snapshots and ends
  the kind-2 DPCM parent, compacts the still-open kind-4 M68K child to the
  parent's effective ancestry, and emits one adjacent raw `SERVICE_PROMOTE`
  proof. The child's immutable begin parent/depth remain canonical, while its
  bounded effective-ancestry transition is producer-neutral semantic state.
  The same-PC ordinary POP remains selected when kind 2 is top. Synthetic tests
  cover both crossing directions, atomic capacity, reset/cutoff/continuation,
  malformed promotion, forged ancestry, and interposed parent snapshots.
  A final real power-on run with Sonic 1 REV01 and the all-emeralds movie now
  crosses row 523 and reaches the row-860 publication boundary with its carried
  native service still live. Prepublication rows are drained and validated but
  are not published; row 860 resets only publication coordinates/inventories,
  preserving the physical chip, YM latches, service tokens, and ancestry.
- Sonic 2: the complete reference movie has two identical observer runs:
  259,590 frames, 169,986,419 events, maximum frame occupancy 1,825, event
  digest prefix `c2b2f823`, and an empty cutoff frontier. Engine comparison is
  not implemented yet.
- Sonic 3 & Knuckles: the complete reference movie has two identical observer
  runs: 434,417 frames, 254,921,281 events, maximum frame occupancy 1,446,
  event digest prefix `08c2f624`, and the source-correct nonempty cutoff
  frontier. The game-local Task 2 profile, native identity resolver, and strict
  `$1C00` state normalizer are now implemented. They retain the shipped
  `fix_sndbugs=0` one-up save-loop behavior and treat the overlapping RAM as
  either seven live SFX tracks or nine saved music tracks. Track unions are
  canonicalized by physical role and live driver mode: DAC/FM frequency,
  PSG noise, FM volume-envelope/SSG-EG/TL state, normal versus envelope
  modulation, SFX voice pointers, and the two-entry return stack compare only
  while their owning branch can affect future output. The complete `$28-$2F`
  shared loop/voice/return region remains canonical: unchecked F7 loop indices
  retain every raw byte below the live stack pointer, while live voice and
  return pointer cells compare by the unique asset/cursor address encoded by
  their authoritative little-endian raw word; disagreeing adapter hints fail
  closed. Both producer
  bindings remain explicitly unavailable, so engine comparison is still not
  implemented or publication-capable. The game-owned reference-capture seam
  now authenticates the locked-on ROM, the reviewed service manifest, and the
  read-only canonical movie; observes and drains from power-on; performs the
  ABI v2 prepublication transition at row 810 without resetting the chip,
  latches, or native stack; and streams every row in `[810,434417)` to a bounded
  sink. A real prefix against the final shared core reached row 810 with an
  armed empty boundary frontier, YM address latches `$28/$A1`, arm epoch 1,
  and 34 native events in published row 810. This is capture capability only:
  there is no raw-to-canonical store writer, fixed Java reference producer, or
  published capture, and the profile's reference binding remains
  `UNAVAILABLE`.

This role/union applicability is derived from
`docs/skdisasm/Sound/Z80 Sound Driver.asm:25-98`. In particular, the source
declares only two loop bytes but warns that they may overflow into the voice
pointer and stack region (`:92-95`); the F7/F8 handlers use an unchecked index
from `$28` (`:3249`, `:3615`), and F8/F9 partition the live return stack through
`StackPointer` (`:3649-3677`).

The S3K profile cannot become publication-capable until central integration
provides the read-only run-local movie at
`src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/s3k-knuckles-complete-superemeralds.bk2`.
It must be the existing pinned movie byte-for-byte, SHA-256
`aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc`;
capture and publication must never rewrite it. Central Tasks 3 and 6 must also
install the actual reference and OpenGGF producer runtime/observer identities,
proofs, and capability vectors before either unavailable binding is replaced.
Central integration must additionally prove that a fresh CLI JVM reaches this
profile through the closed dispatcher without a caller first loading
`S3kCompleteRunAudioProfile`; the game-local unit test does not authorize
publishing on its own.

The row-810 proof deliberately read the canonical `_movies` BK2 in place. It
did not copy, rename, or symlink that read-only input into the absent run-local
path. The exact command used the durable BizHawk home
`$AUDIO_SHARED_FRONTIER/target/audio-parity/native/shared-frontier-hotpath-install`,
whose compressed core SHA-256 is
`f9c6a1cbaa3c70428ffc1774473ff4f9ba7d1ce1503fa00ab657e497dd584625`.
The ROM SHA-1 was `cfbf98c36c776677290a872547ac47c53d2761d6`; the
BK2 SHA-256 was
`aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc`.

The Sonic 1 reference producer starts observation at power-on, discards
pre-publication rows while retaining native service/latch state, and publishes
a mandatory carried-in boundary frontier at row 860. That transition is unit
and real-run proven through the former row-523 crossing. No game has published
a final reference vs OpenGGF MATCH artifact at this checkpoint.

The final row-523-to-row-860 reproduction used Sonic 1 World REV01 SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`, BK2 SHA-256
`f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`,
and durable BizHawk home
`target/audio-parity/native/crossing-install-final2-a`. Its compressed core
SHA-256 is
`93be2835112aeb73bd38cd467cfa0a55f38e3b6ceb7bed642033eb73656cc453`,
raw core SHA-256 is
`c29a3631c5aa6b4566dd80f2dcca5138426adaa624dbb7c450cdaead09cd4bd6`,
Build ID is `822895adb39463ad`, and observer identity is
`b8023a7a80cb961d97c80bcb3835480aca9a78f3eb1ede5490c9295e2ca9bd60`.
The row-860 proof remains a bounded prefix/capture-boundary result, not a
complete-run publication claim.

## Verified checkpoint gates

- Complete-run Java schema/store/comparator/CLI focused gate: 154 tests passing
  (47 trace, 21 store, 61 comparator, 13 cutoff, 8 CLI).
- Sonic 1 normalized state/profile: 68 tests passing.
- Sonic 1 native/managed reference session: 19 tests passing.
- Shared observer projection: 19 tests passing, including direct-parent
  promotion and the no-action8 allocation-stable legacy fast path.
- Native observer selftests: six harnesses passing.
- Existing Sonic 2 and Sonic 3 & Knuckles capability/lifecycle suite: 10 tests
  passing, with their prior complete-run duplicate evidence unchanged.
- S3K Task 2 profile/resolver/normalizer: 18 tests passing.
- S3K game-owned observer profile and bounded capture runner: 7 synthetic
  tests passing. The opt-in real power-on-to-row-810 gate also passes with the
  exact boundary values recorded above.
- S3K engine command-boundary regression guards: 7 tests passing.
- S3K AIZ release-slice, level-loading, bootstrap, and decoding guards: 52
  tests passing against locked-on ROM SHA-1
  `cfbf98c36c776677290a872547ac47c53d2761d6`.
- Paired interleaved observer performance on the final managed collector: S2
  passed two consecutive frozen repetitions at 9.98% and 9.91% median slowdown
  (worst 10.04% and 12.09%); S3K passed at 9.88% and 8.94% (worst 10.95% and
  10.74%). The capability fixture binds the second-run samples.
- Two fresh locked builds and two create-new installs are byte-identical. The
  compressed core SHA-256 is `93be2835112aeb73bd38cd467cfa0a55f38e3b6ceb7bed642033eb73656cc453`
  and the observer identity is
  `b8023a7a80cb961d97c80bcb3835480aca9a78f3eb1ede5490c9295e2ca9bd60`.

These results establish a coherent handoff point; they are not a declaration
that complete-game audio parity is finished.

The capability fixture now binds collector source SHA-256
`d9b525bf7c5b4620833d4eeeda5acf75bef82ab3ee7d1e5a74aa715b641cb69c`,
production harness SHA-256
`e044d963b53b44003e13a4bef7d5360cf100aea421cb40ebc5ed44e08db8d5dd`,
and full raw fixture SHA-256
`d7b2e8f3a78cf34dae7cb882ad8a12aeeb883542499cf8b7d023ccd68deeb795`.
To avoid a self-hash cycle while preserving production-executable authority,
the S2 runtime pins normalized template SHA-256
`97b800c1421a5a15d4dc53acd99fa853399a57a9c46c7b79a3eff1032eb7f098`:
exactly the one canonical 64-hex executable field is zeroed for that template
hash, while runtime validation separately requires its actual value to equal
SHA-256 of `typeof(GpgxHost).Assembly.Location`. Every other raw byte remains
identity-sensitive, and Java metadata retains the full raw capability hash.

The harness executable is now built by the project contract with the pinned
Mono Roslyn 3.9 compiler, `/deterministic+`, and a canonical checkout-root path
map. A clean two-root build, including a root with spaces and hostile ambient
compiler properties, produced identical production executable SHA-256
`e044d963b53b44003e13a4bef7d5360cf100aea421cb40ebc5ed44e08db8d5dd`
and test executable SHA-256
`7afbd2b0e9f80a717b4438d46ef91dc8dfc44c74107789afa982877f5700b2d2`.
Their PDBs are byte-identical as well. Direct ambient `xbuild` is rejected, and
both copied production assemblies pass the strict S2 capability binding.
