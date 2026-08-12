# Complete-run audio frontier checkpoint

This branch is an intentionally incomplete integration checkpoint for the
cross-game complete-run audio parity effort. It contains the reproducible GPGX
observer build/install toolchain, the native BizHawk headless bridge, the
lossless raw/semantic trace schema, and the first Sonic 1 reference producer.

The reviewed canonical native patch at this checkpoint is ABI v3, SHA-256
`8eca789eaf52dd57704f34956356044f37b7a8ca312b158b349b9daa66613eaa`.
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
  preserving the physical chip, YM latches, service tokens, and ancestry. The
  game-owned recorder now ignores raw `SERVICE_PROMOTE` events for managed
  callback correlation and tracks simultaneously open M68K services by native
  token, so the real frontier advances through the nested row-877 lifetime.
  ABI-v3 action 9 now resolves the row-1219 `$72E04` crossing. It applies the
  exact action-5 direct-RAM return predicate while async kind 2 or 3 is top and
  kind 4 is its direct parent. A listed return emits the existing event-10
  KEEP proof with no stack mutation; an outside return atomically emits the
  parent snapshots and END followed by the adjacent action-8-compatible
  `SERVICE_PROMOTE`. No new semantic record type is introduced.

  The same real no-replace capture crosses row 1219 and next stops fail-closed
  at row 1548 with no published output. The preserved first fault is
  `5:2:1394:4:1:0:255`: hook/opcode proof, Z80 instruction-start PC `$1394`,
  active kind 4, depth 1, continuation `0/255`. The final native lifecycle has
  M68K token 3 kind 4 beginning at `$71B4C`, async token 4 kind 2 beneath a
  nested M68K token 5 kind 4, token 5 ending at `$71C4C`, and token 4 ending at
  `$00AC`, followed by ordinary root DPCM iterations. The managed queue at the
  failed frame ends with `normal_close,queue1`. This checkpoint records that
  diagnostic as the next frontier without proposing a new hook or action. No
  reference capture is published at this frontier.
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
  and 34 native events in published row 810. The game-owned native sink now
  writes a bounded LF JSONL staging stream with exact ROM/BK2/manifest,
  interval, `$1C00..$1FFF` state, lag, native event, latch, and cutoff-frontier
  data through a create-new sibling-staging transaction; a failed capture or
  competing final path publishes nothing. Its strict Java reader requires a
  transactional sink and rejects duplicate, missing, or extra fields, row
  gaps, oversized records/event arrays, state-width changes, noncanonical
  numerics, ABI-invalid native events, malformed service/chip/snapshot/
  ancestry frontiers, and unsigned payload loss while forwarding one row at a
  time. The row-810 arm epoch, YM latches, and empty boundary frontier are
  pinned, as is the known one-active/four-pending full cutoff shape. This is
  still capture capability
  only: the staging reader deliberately has no canonical-store authority. A
  source-exact decoder from the raw 1 KiB driver image into the existing typed
  S3K normalizer input, including an authoritative locked-on-ROM asset-range
  catalog, is the next game-owned step. After that, central integration must
  supply the pinned producer/runtime proofs and the run-local BK2 before a
  fixed Java reference producer may write a canonical store. There is no
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

Both row-810 proofs deliberately read the canonical `_movies` BK2 in place. They
did not copy, rename, or symlink that read-only input into the absent run-local
path. The raw-adapter proof used the ABI-v3 durable BizHawk home
`$AUDIO_CROSSING_LIFETIME/target/audio-parity/native/crossing-install-final2-a`.
The ROM SHA-1 was `cfbf98c36c776677290a872547ac47c53d2761d6`; the
BK2 SHA-256 was
`aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc`.

The Sonic 1 reference producer starts observation at power-on, discards
pre-publication rows while retaining native service/latch state, and publishes
a mandatory carried-in boundary frontier at row 860. That transition is unit
and real-run proven through the former row-523 crossing. No game has published
a final reference vs OpenGGF MATCH artifact at this checkpoint.

The final power-on-to-row-860 and row-1548 frontier reproductions used Sonic 1 World REV01 SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`, BK2 SHA-256
`f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`,
and durable BizHawk home
`target/audio-parity/native/action9-install-a`. Its compressed core
SHA-256 is
`bad0aa996672e3e344c3450ad846dbc15e4fb29bfb6fb247eecf2ba826ec5790`,
raw core SHA-256 is
`4715106ed3711e610b900f0dee19dcfc34de347be2717692d1c25f836d957bf5`,
Build ID is `44fb4d8c232fc98f`, and observer identity is
`f9e986419ac08b4bd51212a2169fbbf1b6d85a1552aa2364792b1b77836fb8b2`.
The row-860 proof remains a bounded prefix/capture-boundary result. The later
row-1548 first fault above is the current strict real-run frontier, not a
complete-run publication claim.

## Verified checkpoint gates

- Complete-run Java schema/store/comparator/CLI focused gate: 154 tests passing
  (47 trace, 21 store, 61 comparator, 13 cutoff, 8 CLI).
- Sonic 1 normalized state/profile: 68 tests passing.
- Sonic 1 native/managed reference session: 20 tests passing, including exact
  action-9 KEEP and direct-parent promotion correlation.
- Shared observer projection: 21 tests passing, including conditional
  direct-parent promotion and the allocation-free per-frame projection-result
  wrapper.
- Native observer selftests: six harnesses passing.
- Existing Sonic 2 and Sonic 3 & Knuckles capability/lifecycle suite: 10 tests
  passing, with their prior complete-run duplicate evidence unchanged.
- S3K Task 2 profile/resolver/normalizer: 18 tests passing.
- S3K game-owned observer profile, bounded capture runner, and raw sink: 12
  synthetic tests passing. The strict Java raw adapter adds 9 tests. Both
  opt-in real power-on-to-row-810 gates pass, including the raw envelope with
  the exact boundary values recorded above.
- S3K engine command-boundary regression guards: 7 tests passing.
- S3K AIZ release-slice, level-loading, bootstrap, and decoding guards: 52
  tests passing against locked-on ROM SHA-1
  `cfbf98c36c776677290a872547ac47c53d2761d6`.
- Paired interleaved observer performance on the final optimized managed
  collector and action-9 core passed at 9.67% median slowdown for S2 (12.09%
  worst) and 8.67% for S3K (10.41% worst). The identity-bound fixture retains
  the immediately preceding same-source measurements: S2 9.42% median/9.98%
  worst and S3K 9.16% median/12.02% worst. Earlier candidate and predecessor
  diagnostics demonstrated near-threshold host variance and are not used as
  acceptance evidence.
- Two fresh locked builds and two create-new installs are byte-identical. The
  compressed core SHA-256 is `bad0aa996672e3e344c3450ad846dbc15e4fb29bfb6fb247eecf2ba826ec5790`
  and the observer identity is
  `f9e986419ac08b4bd51212a2169fbbf1b6d85a1552aa2364792b1b77836fb8b2`.

These results establish a coherent handoff point; they are not a declaration
that complete-game audio parity is finished.

The capability fixture now binds collector source SHA-256
`516555cdef86d403fca64571bf0300711894da16b74ca46856cbdfce817a49b0`,
production harness SHA-256
`01b0a5faf1f3b08346c86c45e327ad796f3db4fc7f5af239fb21b7249186bdec`,
and full raw fixture SHA-256
`0a2be19ae646e3a06da37e05f87dff0616c81a2b531725c9b736cf5b0bda5eaf`.
To avoid a self-hash cycle while preserving production-executable authority,
the S2 runtime pins normalized template SHA-256
`cd495746b7c8bd877586aa59b570797c075712e4a2e4412d3c8f07e935ca6397`:
exactly the one canonical 64-hex executable field is zeroed for that template
hash, while runtime validation separately requires its actual value to equal
SHA-256 of `typeof(GpgxHost).Assembly.Location`. Every other raw byte remains
identity-sensitive, and Java metadata retains the full raw capability hash.

The harness executable is now built by the project contract with the pinned
Mono Roslyn 3.9 compiler, `/deterministic+`, and a canonical checkout-root path
map. A clean two-root build, including a root with spaces and hostile ambient
compiler properties, produced identical production executable SHA-256
`01b0a5faf1f3b08346c86c45e327ad796f3db4fc7f5af239fb21b7249186bdec`
and test executable SHA-256
`505ca52b78bf99d286eb8f3ec4a2b5b9a04696bd5508e29bcc532a2c1884018f`.
Their PDBs are byte-identical as well. Direct ambient `xbuild` is rejected, and
both copied production assemblies pass the strict S2 capability binding.
