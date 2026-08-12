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

  The next full no-replace run with the same inputs and action-10 core crosses
  that bound and fails closed at row 8775, again publishing no raw file. The
  exact native fault is `5:2:71b4c:6:1:0:4`: M68K hook/opcode proof at the
  `UpdateMusic` entry, with kind 6 active at depth 1. Kind 6 is the Z80
  `zCheckForSamples` wait service entered at `$003A`
  (`docs/s1disasm/sound/z80.asm:71-82`). The M68K driver has already entered
  its kind-4 `UpdateMusic` service, stopped the Z80, observed that DAC input is
  unavailable, restarted the Z80, and branched back to `UpdateMusic`
  (`docs/s1disasm/s1.sounddriver.asm:147-165`). The re-entry is therefore the
  same managed invocation while the wait service remains its direct child,
  not a nested kind-4 call.

  The S1 manifest now pins an exact retry-only action-10 selector for top kind
  6/direct parent kind 4. It deliberately does **not** add kind 6 to
  `begin_expected_kinds` or grant kind 6 `ALLOW_CHILDREN`: the Z80 wait service
  has no source-valid M68K child. The current ABI-v3 core rejects this manifest
  at configure time because action 10 still requires a same-PC `PUSH_BEGIN`
  pair. The conductor contract is to permit the retry-only selector exactly
  when the selected top kind disallows children; at runtime it must require
  the declared immutable direct parent and fail closed when that parent is
  absent, never fall back to a push. The opt-in row-8775 gate is intentionally
  RED at observer configuration until that native contract lands.

  **2026-08-12 conductor correction.** A real run against the proposed
  retry-only core disproved the direct-parent interpretation above. The global
  native order at row 8775 is: kind-4 token 1 ends at ordinal 12 and `$71C4C`;
  kind-6 token 2 then begins as a root at ordinal 13 and Z80 `$003A`; the
  managed bridge observes three genuine `$71B4C` executions while kind 6 is
  active; and kind 6 ends at ordinal 20 and `$0077`. All three `$71B4C`
  observations have `A7=$FFFDB2` and return `$000B64`, but the immutable
  native stack correctly contains no kind-4 direct parent. The callbacks are
  real loop re-entries, not duplicate bridge notifications. Consequently the
  unpaired action-10 experiment was rejected and its production/identity
  cascade was discarded.

  The corrected contract is one bounded deferred/coalesced begin: the
  three source-identical `$71B4C` observations while the root wait service
  blocks must yield exactly one new kind-4 semantic begin, ordered after the
  kind-6 completion, without retroactively parenting kind 6 and without a
  push, retry, or recently-closed-ancestry fiction. ABI-v3 action 11 reserves
  that future begin against the exact non-child-bearing blocker token. Every
  physical callback emits marker value 4 and retains the identical managed
  identity; the blocker's atomic END then emits one adjacent ordinary root
  BEGIN and consumes the reservation.

  **2026-08-12 action-11 real proof.** A diagnostic core built from the locked
  source/toolchain at commit `827853924` crosses row 8775 with the required
  physical order: the prior kind-4 service ends at ordinal 12, kind 6 begins
  as a root at ordinal 13, three marker-value-4 correlations retain
  `A7=$FFFDB2` and return `$000B64`, kind 6 ends at ordinal 20, and exactly one
  new root kind-4 service begins at ordinal 21. The restored opt-in gate checks
  all four ordinals, all three raw correlations and their common managed
  identity, and the single release. A diagnostic terminal probe over every
  remaining manifest row, selected by
  `OPENGGF_S1_AUDIO_TERMINAL_PROBE=1`, is clean through row 225100 and
  `Complete(225101)`. The strict S1 reference-observer frontier is therefore
  the clean exclusive bound **225101**. This is a complete reference
  observation interval, not a published reference-vs-OpenGGF MATCH claim.
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
  4 MiB locked-on ROM before installing the shipped asset catalog, resolves
  little-endian Z80 window pointers to one exact per-song/per-SFX occupied
  source range, and separately identifies the two ROM-installed Z80 driver
  images. The catalog comes from `sonic3k.lst`, `s3.lst`, and `Lockon S3/LockOn
  Data.asm:1289-1334`: bank `$59` ends at locked-on CNZ1 `$2CEBF1`, bank `$5A`
  ends at LBZ1 `$2D17A7`, and bank `$5B` owns only `$2D8AE8..$2DFEAC`. Thus the
  `$5B` `org` prefix, every bank tail/alignment byte, and the SFX prefix before
  `Sound_33` `$FDE30` and tail after `Sound_DB` `$FFDA9` fail closed. Unknown
  banks, pointers outside `$8000..$FFFF`, invalid stack partitions, and live
  pointer unions in any unoccupied hole also fail closed. Inactive live tracks
  do not promote stale union bytes into semantic state. The `fix_sndbugs=0`
  one-up interpretation decodes all nine saved tracks: the shipped routine
  copies all nine, clears their playing bits, and later forces every copied
  track live during restore. `zFadeToPrevFlag` values `$29` and `$FF` both
  identify that saved overlap before restoration (`Z80 Sound Driver.asm:662-688,
  3067-3079`); `zFadeInToPrevious` clears it before copying tracks back
  (`:2725-2747`), so zero correctly selects the post-restore live-SFX overlap.

  The real row-810 boundary image from the final shared core decodes as nine
  populated music tracks plus the live-SFX overlap, then passes the existing
  normalizer and the S3K profile. This is now a committed opt-in Java gate: it
  asks the native headless test to transactionally capture the four-record raw
  row-810 prefix to a fresh temporary path, reads it through the strict raw
  adapter, and invokes decoder, normalizer, and profile for both boundary and
  frame state. It reads the canonical ROM and `_movies` BK2 in place and does
  not create a fixture copy. That proof also corrected an initially too-short driver-data
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
The decoder/normalizer/profile gate used the current action-9 install at
`.worktrees/audio-conditional-promotion/target/audio-parity/native/action9-install-a`.
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
was the predecessor strict real-run frontier. The action-11 diagnostic used the
same ROM and BK2 with patch SHA-256
`3d7d3c411f27b8490aa1675557d2f8ef6ae2ae09495cfebb7e9c8e03760e5388`,
raw core SHA-256
`b1f33a0e030c1998fd56d9ed28142c2c198c0d46bdfaea9462bf5e26a6f7bc08`,
compressed core SHA-256
`76fe444068231ddcebed11947f1d6cd5c77aa70417e54892088e9cd637a419e1`,
and Build ID `db8cab4f70430a16`. It was built and installed only beneath ignored
`target/audio-parity/native/action11-diagnostic`; no committed native,
managed, capability, or Java identity literal was repinned. Task 5 owns the
deterministic paired build and final identity cascade.

## Verified checkpoint gates

- Complete-run Java schema/store/comparator/CLI and authority focused gate: 155
  tests passing.
- Sonic 1 normalized state/profile: 68 tests passing.
- Sonic 1 native/managed reference session: 28 synthetic tests passing.
  The opt-in action-11 real gate proves row 8775's exact 12/13/20/21 physical
  order, three identical callback identities, and one release. A separate
  diagnostic terminal probe is clean through row 225100 and
  `Complete(225101)`.
- Shared observer projection: 21 tests passing, including conditional
  direct-parent promotion and the allocation-free per-frame projection-result
  wrapper.
- Native observer selftests: six harnesses passing.
- Existing Sonic 2 and Sonic 3 & Knuckles capability/lifecycle suite: 10 tests
  passing, with their prior complete-run duplicate evidence unchanged.
- S3K Task 2 profile/resolver/normalizer plus the strict ROM-backed raw-state
  decoder: focused gates passing. The decoder covers exact occupied assets,
  installed driver-data pointers, live pointer unions, inactive stale bytes,
  the `$29`/`$FF` nine-track one-up overlap, and strict
  width/bank/window/hole/stack rejection.
- S3K game-owned observer profile, bounded capture runner, and raw sink: 12
  synthetic tests passing. The strict Java raw adapter adds 9 tests. Both
  opt-in real power-on-to-row-810 gates pass, including the raw envelope with
  the exact boundary values recorded above and its Java decoder/normalizer/
  profile consumer gate.
- S3K engine command-boundary regression guards: 7 tests passing.
- S3K AIZ release-slice, level-loading, bootstrap, and decoding guards: 52
  tests passing against locked-on ROM SHA-1
  `cfbf98c36c776677290a872547ac47c53d2761d6`.
- Fresh paired interleaved observer performance after the R4 managed-harness
  identity cascade passed at 9.41% median slowdown for S2 (9.53% worst) and
  9.72% for S3K (10.75% worst). These measurements are bound into the current
  capability fixture; earlier candidate and predecessor diagnostics are not
  used as acceptance evidence.
- Two fresh locked builds and two create-new installs are byte-identical. The
  compressed core SHA-256 is `ba4fdc0ce6fff92899b9640f53d13b20bebc96ed143d96f9becb4bd57c3b3b61`
  and the observer identity is
  `6a9dbc44f83429f08845cb609ef14a8b595b11279bc0c12271d8579bedda6cd3`.

These results establish a coherent handoff point; they are not a declaration
that complete-game audio parity is finished.

The capability fixture now binds collector source SHA-256
`92fb4c4541931c30240ec0b62d00fba2d7e26dbaf12230dc2ab0d15b42465560`,
production harness SHA-256
`a4e2b74cb05db8152e18e6d5c6d8c6c12bb12b470f33a4e83b3b5d9bb7e36965`,
and full raw fixture SHA-256
`8a06a63e4a5c8b1d4c9445e4333537caed3c8e67df7df946135e273d911ab0fb`.
To avoid a self-hash cycle while preserving production-executable authority,
the S2 runtime pins normalized template SHA-256
`a5b5a07529f3e7c908601e7dc1ce552c8fd70390a9619e6847ccb00e16f984d3`:
exactly the one canonical 64-hex executable field is zeroed for that template
hash, while runtime validation separately requires its actual value to equal
SHA-256 of `typeof(GpgxHost).Assembly.Location`. Every other raw byte remains
identity-sensitive, and Java metadata retains the full raw capability hash.

The harness executable is now built by the project contract with the pinned
Mono Roslyn 3.9 compiler, `/deterministic+`, and a canonical checkout-root path
map. A clean two-root build, including a root with spaces and hostile ambient
compiler properties, produced identical production executable SHA-256
`a4e2b74cb05db8152e18e6d5c6d8c6c12bb12b470f33a4e83b3b5d9bb7e36965`
and test executable SHA-256
`1c110cf9011af53100a48e9a414d0d06da5510710840db944af8336d1a428bfd`.
Their PDBs are byte-identical as well. Direct ambient `xbuild` is rejected, and
both copied production assemblies pass the strict S2 capability binding.
