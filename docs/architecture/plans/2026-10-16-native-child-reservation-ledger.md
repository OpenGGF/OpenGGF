# Native child reservation ledger extraction

## Source attribution and boundary

Root combined smoke found `TestArchitecturalSourceGuard#objectManagerFacadeStaysWithinExtractedCollaboratorBudget`
at 3132 effective ObjectManager lines against its unchanged 3086 limit. Starting
point: `baaaed16b25ebcbd6745eaa3679aa8bccafc5294`, containing the accepted native
reservation seam and capture resolver prerequisite.

Move fixed-prefix initialization and placed-owner folded-tail reservation mutation
to package-private `NativeChildReservationLedger`. The manager retains its exact
public and package-private entry signatures, allocator authority, own-execution
retirement mechanism, and snapshot ledger. The helper owns mutation responsibility;
it is not a population view. No API, snapshot schema, guard budget, native consumer,
or generic raw allocator contract changes.

The helper holds existing final collection references. Reset and destructive restore
clear/refill those collections rather than replacing them; rewind identity remains
a predicate over the current manager map. Exact placement identity, live-owner and
alias rejection, fixed-prefix protection, partial-allocation ordering, tail reuse,
and higher-slot deferred retirement remain unchanged. The extracted facade is 3084
effective lines using the guard's comment/blank-line accounting. An initial seam-only
extraction remained over budget at 3093; the existing fixed-prefix initializer moved
to the same mutation collaborator to keep that responsibility together.

## Focused evidence

One queued invocation, session 21679, tested the two source files with SHA256
`c3edb119e0cebdd1111229fcbd5f3e78a0c1984df1befa8fd2bbd841aab2748f`
(ObjectManager) and `de9b4d951db972ce72e48e025aaa8b3aca3841c74f93321016b4d5870beb70e8`
(NativeChildReservationLedger). The source stayed frozen through terminal.

```sh
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestPlacedChildSlotReservations,TestArchitecturalSourceGuard#objectManagerFacadeStaysWithinExtractedCollaboratorBudget,TestSecondStrikeEastIczEndBossFrostRestore#committedStockFrostPuffsRestoreExactlyAndReplayIdentically' -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$S3K_ROM_PATH" test
```

`S3K_ROM_PATH` denotes the existing locked-on ROM absolute path. Fresh XML reports
7 reservation cases, the exact facade guard, and the actual Stock ICZ frost restore:
9 passed, zero failures/errors/skips. Queue wait was 176 seconds; Maven execution
was 1 minute 13 seconds. Reservation cases cover fixed-prefix partial allocation,
interleaved tail reuse, destructive restore and exact next slot, aliases/unmanaged
owners, matched release, teardown isolation, and actual ascending-walk retirement
for higher and already-visited slots.

The unchanged Stock ICZ fixture captured QUIET and COMMITTED states, including six
committed puffs. Immediate structural poses matched; puff/player tuples matched at
+1/+2/+5/+65/+130, and final snapshot-key differences were zero. This is the declared
owner-local fixture, not a whole-route certificate. Root owns other combined-smoke
failures and integration; no broad, API/SDK, native controller, or schema checks were
repeated. Consumed diagnostics are temporary rather than retained evidence archives.
