# Task 7 report — native buffered GPGX observer

## Outcome

Task 7 freezes and reproducibly builds the generic observation-only native
observer for the exact BizHawk 2.11 GPGX Waterbox core. The artifact remains a
separate create-new installation; stock and its managed DLLs are unchanged.
The selected managed lane is the Task 6 exact-hash `REFLECTION` adapter. No
replacement managed DLL or provisional S2/S3K profile is shipped. Game-specific
real Z80 capability remains Task 8.

Task 8's first real S2 bootstrap probe reopened the candidate artifact before
publication: row 3 executed future watched Z80 PCs while ZRAM was still zero
and before the source-cited `$0EC000` upload routine. The eager-proof candidate
correctly returned `-3`, proving that proof readiness had to be an explicit
native state rather than a movie-row or managed-RAM heuristic.

## RED/GREEN

The registered initial C# test failed because the native patch and artifact
lock were absent. A later exact-Clang build failed because the public header
omitted the departure prototypes; the same `-Werror` build passed after the
exact prototypes were added. Dirty/ignored source and provisional bundle locks
were independently rejected without publishing output.

The actual M68K boundary harness was RED when a zero-initialized prefetch cache
made the intended multiword `MOVE.B` fixture decode as another instruction and
emit no FM write. Invalidating the synthetic prefetch cache made the real
interpreter fetch the intended opcode and its literal start-PC assertion green.
The managed proof later reproduced zero-capacity empty-array drain as
`TRACE_INVALID_PHASE`: BizInvoker marshalled the empty array as a non-null
pointer. The adapter now normalizes that exact case to null, and a fresh empty
READY frame proves the corrected contract through Waterbox.

Fix round 1 reproduced an installer destination-following flaw with an internal
stock-core symlink: the actual installer replaced an external scratch canary's
locked stock bytes with the patched core before its cleanup check failed. The
same installer accepted and published a top-level notices symlink and accepted
an external hardlink inside the notices tree. Adversarial registered tests now
require refusal before external mutation or output publication. Caller stock
and its private staged copy must be closed regular trees; staged core replacement
removes the validated destination before copying. Build evidence must be
single-link regular files. The producer now emits LLVM/Debian notice files and
directories only (omitting package convenience symlinks, including one broken
link), and the installer validates that regular self-contained form before and
after copying. The locked notice-tree SHA-256 is
`e8df18fb120730c10d8a30b5665132bed57f074eedfafdfae5a01e7de06ff2b0`.

Independent review then found that caller-owned regular files could still be
changed between validation and copying. A deterministic actual-installer RED
test waited for the private stage, changed the final managed-source input, and
observed the changed bytes publish successfully. The installer now validates
the complete private stage immediately before create-new publication: all six
unchanged stock artifacts, patched core, build evidence, complete notice-tree
digest, whole identity, Task 7 recipe and every versioned input, plus the full
Task 6 recipe graph. The same mutation test is green by refusing publication;
caller stock remains exact and no partial target exists.

## Feasibility and ABI proof

Five packed candidates and the 65,536-entry event array were compiled/linked
with the exact Task 6 Clang 16, LLD, musl sysroot, link script, historical build
path, and environment. Static assertions freeze every published offset and
sizes 64/16/32/16/32, `CHAR_BIT == 8`, fixed integer widths, and little-endian
`LSB_FIRST`. The linked ELF has no dynamic section. Every observer object is
LOCAL in writable/alloc `.invis`, aligned to 32 bytes, and excluded from
savestate serialization. Exactly ten `gpgx_audio_trace_*` departures are
GLOBAL; no host/interior pointer is retained after configure.

```text
.invis size       0x20a8f0 (2,140,400 bytes)
stock .invis      0x000d10 (3,344 bytes)
observer addition 0x209be0 (2,137,056 bytes)
event array       2,097,152 bytes
```

## Native implementation and proof

- Z80 visits after interrupt admission and before `R++`/`ROP()`.
- M68K visits at the ordinary prefetch boundary and IRQ-delay direct fetch.
- scoped issue-source save/restore wraps both runners; chip events use the
  latched instruction-start PC, not the advanced dispatch-time PC.
- one common FM wrapper precedes YM2612/YM3438 mutation for all five managed
  Genesis selectors; the common PSG entry precedes sync/latch mutation for the
  M68K, Z80, and banked-Z80 paths.
- copied manifests drive a depth-eight stack, deterministic same-PC selection,
  atomic snapshots/tails, typed iterations, continuation, saturation, and the
  disabled/configured/recording/ready state machine.
- configured hooks are sorted into exact CPU/PC ranges; Z80 and low-16 M68K
  masks reject unwatched instructions before binary lookup while every
  instruction still updates the exact start-PC latch used by chip writes.
- reset pre-reserves cancellation groups and its protected tail, cancels inner
  to outer, attributes reset writes to the reset root, and fails closed without
  discarding retained in-frame events.
- the sole optional M68K root upload completion retains its ordinary full-ZRAM
  snapshot/end, scans the complete Z80 proof set, and arms atomically; watched
  Z80 PCs are inert before it. Reset/Power disarm before post-reset execution
  and the next complete upload rearms in the same or a later native frame.
- API 2 still accepts byte-identical ABI-1 configs under the legacy action and
  zero-reserved-field rules. ABI-2 configs may use the bounded M68K direct-RAM
  return-PC predicate, an owned active-service retry marker, and an exact
  top-of-stack observation marker. Predicate keep/pop, retry, and observation
  are values 0/1/2/3 of event kind 10 in the same native order as chip writes;
  malformed stacks, indirect/custom memory pages, odd PCs, missing same-PC
  alternatives, and insufficient atomic capacity fail closed.
- illegal reset in CONFIGURED or READY is sticky `-3` for status departures;
  the three unsigned capability queries remain fixed and `disable` alone
  clears the condition.

The exact-Clang native suite produced:

```text
native-observer-selftest: 12 ordered nested events; scoped CPU PCs; READY reset sticky fail-closed
```

Six linked harnesses prove copied inputs, ordered nested ownership, distinct
snapshots, exact FM-port/PSG vectors, phase/drain/overflow/reset behavior, token
wrap, and ordinal continuity. Two harnesses execute the actual patched Z80 and
M68K interpreters: ordinary and post-IRQ Z80 admission, plus a multiword M68K
write at PC 4 and the IRQ-delay extra-fetch seam at PC 8. These are synthetic
CPU-boundary proofs, not real-game capability claims.
The sixth harness proves zero-filled pre-upload visits are ignored, one
complete 8 KiB upload service arms atomically, proof mismatch never partially
arms, and an in-frame reset/reupload preserves ordered cancellation/reset
events before capturing the first rearmed Z80 service.

## Frozen identities

```text
patch SHA-256                 dd1e860795ac4e3055081b83ccb77368ae470280911787da849845c9570e8fa1
raw core size                 41731544
raw core SHA-256              642cf1ce651e95dab1143c0a718e9ab95cf617c07d66590831368fdeb0b4202a
compressed core size          412363
compressed core SHA-256       3b632c65bca7372b8f70c7526b51e7ca14f53a43224790534a200274c0351ebf
Waterbox BuildID              d2011feb908faa14
source bundle raw SHA-256     a1e07b266ebd1c5d43e757513edd0fbcabcd6361558742789e8b7fd89ca7ce2b
source bundle zst SHA-256     7f1a3558f3b74aa3f8f03ee0e66c8cfb9806ac00c1496e48614a3da2f058b91a
path manifest SHA-256         ca94c5213d326ab3affac073dff5b67cf6b9c275db6d2c6291688376703709c1
mode manifest SHA-256         03b81d212882a71329d5d45377bdebe1aa6194a0012bd9a4749ec35cd5683440
Task 7 recipe SHA-256         e71f5b83616556577aabc01057fc3bc2a5b4e6c74792adfbc83e5e1f97e8b3e0
whole identity SHA-256        b72175c3c7e15db66e37b64e15bc3e8f27ba1ac71a4896cd5a8363f4205d51b8
adapter source SHA-256        9689ba255d2b14e7e31b533437855faddf9f068dfdf18b8a13f4662dfd2dbbba
host bridge source SHA-256    af9da7ed2f08d27c663176f4f1c852504c4a515e437655abb0fd5d20a3364bf1
callgraph proof SHA-256       536711d3eada4bc9898c256c7479ee5a651381025f0476499bac76c9e82dd5c6
ELF proof SHA-256             d6c2a1093ccc3484e4f353e8f01dac3bc1aa26487080d6addd5bff815bcfd630
Task 6 input identity         36dde84c81429343b2f4425ff66c04f8fbdf54bcaf42a2459e68c52f95e9a0d4
```

Fresh locked builds use independent toolchain copies and compare raw,
compressed, source-bundle, manifest, and identity bytes. The source archive
normalizes LF, modes, uid/gid, names, mtime, and path order, excluding
Git/generated/sysroot/cache/binary material. Installation copies rather than
links stock and includes corresponding source, literal patch, build evidence,
BizHawk/GPGX/musl/zstd/LLVM/Debian notices, and complete GPGX terms.

## Semantic and regression evidence

Stock and observer-disabled patched cores advance exact S1 REV01 for ten frames
to the same full 64 KiB 68K RAM digest:

```text
de2f256064a0af797747c2b97505dc0b9f3df0de4f489eac731c23ae9ca9cc31
```

The Waterbox proof invokes all ten departures, validates every managed offset
and little-endian vector, destroys caller arrays after configure, drains Reset
snapshots from copied data, and proves `.invis` savestate exclusion. It boots
all five Genesis FM selectors. No runtime performance percentage is claimed
from this short prefix; disabled execution takes one enable split and then an
observer-free loop.

Task 8's complete S2 route exposed that wall-clock continuation aging was too
strict for a parent suspended under a bounded child. The reviewed execution-
exposure amendment increments only the service that is top-of-stack across a
frame boundary. A direct native regression suspends a parent for three frames,
then proves the parent retains its own budget while the child ages; it also
proves a continuously exposed top service still fails at its configured limit
and Reset cancels both without aging either. Two fresh builds and installations
reproduced the amended identities above byte-for-byte. The complete 259,590-
frame S2 movie then replayed twice with 169,986,419 events, maximum occupancy
1,825, no overflow, and identical digest
`c2b2f82374aaa16144b6bf121df051dcd5b4ba095431c16cf6224adc633de41d`.

Task 8's canonical 1,000-frame S2 and S3K capability runs preserve their exact
event digests after indexing. Streaming capture overhead is 6.33% median / 9.29%
worst for S2 and 9.41% median / 9.54% worst for S3K, below the 10%/15% gates; maximum
occupancies are 1,611 and 1,446 of 65,536. The complete 434,417-frame S3K
Knuckles movie also replayed twice with 254,921,281 events, maximum occupancy
1,446, no overflow, and identical digest
`08c2f6249dc379b18b0362c04ed757f4f053a3c1cf7c28c110b09b82af6cee7e`.

## Final verification

```text
selftest/run.sh                                      PASS (6 native harnesses)
fresh locked build A / fresh locked build B          PASS (byte-identical)
independent install A / independent install B         PASS (create-new)
GpgxHost departure/reset/savestate proof              PASS
GpgxHost five Genesis FM selector boots               PASS
GpgxHost 10-frame 64 KiB RAM digest                   PASS (de2f2560...)
```

The registered slow gate passed and exercises two fresh builds/installs,
outside-root/existing-root refusal, a failure after stock verification,
unchanged caller stock, no symlinks/hard links, and full notice/source
publication. The final focused Java Task 1–3 regression is 115/115. The final
C# evidence is 10/10 GPGX-audio tests, 9/9 Waterbox host tests, 15/15 collector
tests, and 10/10 real S2/S3K capability/lifecycle tests; the separately enabled
two-build/two-install slow gate and both complete duplicate gates pass.
