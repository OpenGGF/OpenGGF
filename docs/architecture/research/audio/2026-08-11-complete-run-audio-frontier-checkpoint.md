# Complete-run audio frontier checkpoint

This branch is an intentionally incomplete integration checkpoint for the
cross-game complete-run audio parity effort. It contains the reproducible GPGX
observer build/install toolchain, the native BizHawk headless bridge, the
lossless raw/semantic trace schema, and the first Sonic 1 reference producer.

The reviewed canonical native patch at this checkpoint is ABI v3, SHA-256
`c9c50f034e11044a6769a9d331fd1d42e529cb0302dbeb93b354c45c88039dcb`.
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
  `5:2:1394:4:1:0:255`: hook/opcode proof, source CPU M68K, instruction-start
  PC `$1394`, active kind 4, depth 1, continuation `0/255`. `$1394` is the
  REV01 `QueueSound2` write, not a Z80-driver address. Its exact caller is the
  ring-collection tail path: ROM `$A2CC` calls `CollectRing`, whose `$A32E`
  `jmp (QueueSound2).l` returns to `$A2D0`
  (`docs/s1disasm/_incObj/25, 37 Rings.asm:149-210`). The captured M68K stack
  agrees: `A7=$FFFDF0`, return `$00A2D0`.

  The fault is earlier lifecycle debt, not a missing queue allowance. In one
  physical frame M68K token 3 kind 4 begins at `$71B4C` with `A7=$FFFDB2` and
  return `$000B64`; async DPCM token 4 kind 2 then begins; the same `$71B4C`
  invocation is re-observed with the same managed stack identity while token 4
  is top. The current manifest can express action 6 retry only when kind 4 is
  itself top, so it incorrectly selects the ordinary kind-2 begin alternative
  and pushes nested token 5 kind 4. `$71C4C` closes token 5 and `$00AC` closes
  token 4, leaving token 3 falsely open until the legitimate ring request.
  The final managed queue ends with `normal_close,queue1`; the final lifecycle
  tail is token 5 END, token 4 END, then ordinary root DPCM iterations.

  The conductor-owned native contract is an exact direct-parent retry at
  `$71B4C`: while top kind 2 or 3 has direct parent kind 4, verify the same
  immutable parent service and emit the existing retry proof (`SERVICE_MARKER`,
  event kind 10, value 2) bound to that parent token, with no snapshots, END,
  promotion, token allocation, or stack mutation. Ordinary root begins for
  active kinds 0/2/3 and ordinary action-6 retry when kind 4 is top remain
  unchanged. `RequiresDirectParentRetryUnderAsyncPcm` pins the two exact
  kind-2/kind-3 alternatives. Action 10 is a configure-time paired override:
  the only permitted same-PC pair is one source-identical `PUSH_BEGIN` and one
  direct-parent retry with exact CPU, PC, opcode, active kind, and service
  kind. The retry wins only when the declared direct parent exists; otherwise
  the ordinary begin remains authoritative. At row 1548 the real REV01 run
  emits `SERVICE_MARKER` value 2 bound to the original kind-4 token at depth 0,
  then closes the async child and that parent normally. A bounded diagnostic
  capture also terminates cleanly after row 5000, so the proven S1 reference
  frontier is now **through row 5000** with no published partial output.
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
  still capture capability only: the staging reader deliberately has no
  canonical-store authority. The raw 1 KiB image now has a source-exact Java
  decoder into the existing typed S3K normalizer input. It authenticates the
  4 MiB locked-on ROM before installing the shipped bank catalog, resolves
  little-endian Z80 window pointers through their owning music/SFX bank, and
  separately identifies the two ROM-installed Z80 driver images. Unknown bank
  bytes, pointers outside `$8000..$FFFF`, the unused prefix of music bank `$1C`,
  invalid stack partitions, and live pointer unions outside their owning range
  fail closed. Inactive live tracks do not promote stale union bytes into
  semantic state. The `fix_sndbugs=0` one-up interpretation instead decodes all
  nine saved tracks: the shipped routine copies all nine, clears their playing
  bits, and later forces every copied track live during restore.

  The real row-810 boundary image from the final shared core decodes as nine
  populated music tracks plus the live-SFX overlap, then passes the existing
  normalizer. That proof also corrected an initially too-short driver-data
  hypothesis: `Size_of_Snd_driver2_guess` reserves compressed ROM space, while
  the installed Z80 tables occupy `$1300..$1BFF` under the source guard at
  `Sound/Z80 Sound Driver.asm:5305-5307`. Central integration must now supply
  the pinned producer/runtime proofs and the run-local BK2 before a fixed Java
  reference producer may write a canonical store. There is no published
  capture, and the profile's reference binding remains `UNAVAILABLE`.

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
`target/audio-parity/native/action10-final-install-a` (byte-identical to the
paired `action10-final-install-b`). Its compressed core
SHA-256 is
`ba4fdc0ce6fff92899b9640f53d13b20bebc96ed143d96f9becb4bd57c3b3b61`,
raw core SHA-256 is
`0410b3a90e355fd6a774059a0a7945d97742841cb97a05423f116fed130e483e`,
Build ID is `5c7cc70998c8b5b1`, and observer identity is
`6a9dbc44f83429f08845cb609ef14a8b595b11279bc0c12271d8579bedda6cd3`.
The row-860 proof remains a bounded prefix/capture-boundary result. Row 1548
was the action-10 contract fault; the clean bounded diagnostic through row 5000
is the current strict real-run frontier, not a complete-run publication claim.

## Verified checkpoint gates

- Complete-run Java schema/store/comparator/CLI and authority focused gate: 155
  tests passing.
- Sonic 1 normalized state/profile: 68 tests passing.
- Sonic 1 native/managed reference session: 22 synthetic tests passing plus
  the opt-in real row-1548 proof, including exact action-9 KEEP/direct-parent
  promotion correlation and action-10 parent-token retry correlation.
- Shared observer projection: 21 tests passing, including conditional
  direct-parent promotion and the allocation-free per-frame projection-result
  wrapper.
- Native observer selftests: six harnesses passing.
- Existing Sonic 2 and Sonic 3 & Knuckles capability/lifecycle suite: 10 tests
  passing, with their prior complete-run duplicate evidence unchanged.
- S3K Task 2 profile/resolver/normalizer plus the strict ROM-backed raw-state
  decoder: 26 tests passing. The decoder covers the shipped bank ranges,
  installed driver-data pointers, live pointer unions, inactive stale bytes,
  the nine-track one-up overlap, and strict width/bank/window/stack rejection.
- S3K game-owned observer profile, bounded capture runner, and raw sink: 12
  synthetic tests passing. The strict Java raw adapter adds 9 tests. Both
  opt-in real power-on-to-row-810 gates pass, including the raw envelope with
  the exact boundary values recorded above.
- S3K engine command-boundary regression guards: 7 tests passing.
- S3K AIZ release-slice, level-loading, bootstrap, and decoding guards: 52
  tests passing against locked-on ROM SHA-1
  `cfbf98c36c776677290a872547ac47c53d2761d6`.
- Paired interleaved observer performance on the final optimized managed
  collector and action-10 core passed at 9.76% median slowdown for S2 (11.26%
  worst) and 8.10% for S3K (10.38% worst). The identity-bound fixture retains
  the immediately preceding same-source measurements: S2 9.42% median/9.98%
  worst and S3K 9.16% median/12.02% worst. Earlier candidate and predecessor
  diagnostics demonstrated near-threshold host variance and are not used as
  acceptance evidence.
- Two fresh locked builds and two create-new installs are byte-identical. The
  compressed core SHA-256 is `ba4fdc0ce6fff92899b9640f53d13b20bebc96ed143d96f9becb4bd57c3b3b61`
  and the observer identity is
  `6a9dbc44f83429f08845cb609ef14a8b595b11279bc0c12271d8579bedda6cd3`.

These results establish a coherent handoff point; they are not a declaration
that complete-game audio parity is finished.

The capability fixture now binds collector source SHA-256
`92fb4c4541931c30240ec0b62d00fba2d7e26dbaf12230dc2ab0d15b42465560`,
production harness SHA-256
`935b8ac05d86ecd2c469d4f14e35f3b9e2a3e8c041dd5847d086b05e27d259e1`,
and full raw fixture SHA-256
`08c4bc15c0c7c29a3d2993a21fdb4dbb8a47b9ff8cfdd7953d3342a0cb6a55db`.
To avoid a self-hash cycle while preserving production-executable authority,
the S2 runtime pins normalized template SHA-256
`715305b0ee6e8bfcd1e3d7656b29f3801f94721ca4c14cd3e23a5f40a2f66442`:
exactly the one canonical 64-hex executable field is zeroed for that template
hash, while runtime validation separately requires its actual value to equal
SHA-256 of `typeof(GpgxHost).Assembly.Location`. Every other raw byte remains
identity-sensitive, and Java metadata retains the full raw capability hash.

The harness executable is now built by the project contract with the pinned
Mono Roslyn 3.9 compiler, `/deterministic+`, and a canonical checkout-root path
map. A clean two-root build, including a root with spaces and hostile ambient
compiler properties, produced identical production executable SHA-256
`935b8ac05d86ecd2c469d4f14e35f3b9e2a3e8c041dd5847d086b05e27d259e1`
and test executable SHA-256
`952ca51d188571c4168ba90d20a149e0f3c58df5d68841869d0953f1a4026b3e`.
Their PDBs are byte-identical as well. Direct ambient `xbuild` is rejected, and
both copied production assemblies pass the strict S2 capability binding.
