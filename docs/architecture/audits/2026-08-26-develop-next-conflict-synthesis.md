# Develop into next conflict synthesis

Date: 2026-08-26

## Frozen inputs

- Merge base: `59e59c8feb5fb5a247ff0ab43da63aeccc742cb0`
- Frozen `next`: `84d9a3761f618035dd1caa40a3d5fc72a1019693`
- Official 0.6 scope freeze: `a17adaba5b57298ffd88c6d7b6ab3a4d6aff87bb`
- Planning head: `c02816c6b1f0de1cb055cd318f5581ae21e037e9`
- Synthetic recursive merge tree: `bf953a08572551f984962afb078dc04dc8b64553`
- Final restoration review: 2026-08-27, through
  `8d8902bafaca00e7d5ce7ca16d22a7b6618541ec`

The recursive simulation produces exactly 77 conflicts: 74 content conflicts,
two modify/delete conflicts, and one add/add conflict. There are no rename,
binary, or mode conflicts. The accompanying TSV contains one path-specific row
for every conflict and no blanket parent-side choices.

## Parent evidence

The official-freeze ordinary baseline recorded 15,136 outcomes: 15,063 pass,
46 failure, 8 error, and 19 skipped. Its guard baseline recorded 557 outcomes:
538 pass, 18 failure, and 1 error.

Frozen `next` required a disjoint ordinary fallback because a reused fork
exposed a pre-existing global-engine test contamination. The two authenticated
partitions cover exactly 2,266 ordinary roots without overlap and aggregate
17,568 outcomes: 17,515 pass, 31 failure, 8 error, and 14 skipped. The explicit
guard baseline covers 67 authoritative roots, including 66 executable suites
and one reviewed compatibility facade, with 483 pass, 3 failure, and no error.

The exact parent-union selector contains 2,376 unique classes in 32
deterministic slots. Its SHA-256 is
`bc2a8ec2d600a8b3234a71c0194cf7d79d6bcef654c8d855db67d224a78eedec`.

Parallel wrapper sessions in other worktrees do not invalidate evidence merely
by existing. Each accepted run must instead prove its own source identity,
coordinator identity, private namespace and mounts, session-owned output roots,
runtime inputs, clean source pre/post state, and cleanup. The accepted frozen
`next` guard run satisfies that contract.

## Resolution order

1. Resolve the 11 build, policy, tooling, and documentation contracts.
2. Resolve the nine audio value types and interfaces before factories,
   backends, and `AudioManager`.
3. Resolve the 18 shared runtime conflicts from value/state types through
   transition, ring, object-contact, camera, movement, sprite, level, and loop
   owners.
4. Resolve the 22 game-specific conflicts, with identifiers and registries
   before their event/object consumers.
5. Resolve the 17 tests and guards against the final production shapes,
   tightening exact inventories rather than retaining the larger parent
   allowance.

Compilation happens after interface families are coherent. Focused tests run
only after the full merge index resolves; the merged ordinary suite and fresh
guards then compare against both recorded parent inventories.

## Authority rules

Develop is authoritative for ROM-derived timing, dispatch order, counters,
fixed-slot behavior, immutable audio data, current test-session infrastructure,
and closed discrepancy facts. `next` retains its public Mod API, streamed
audio, mod-zone descriptors, arbitrary-team behavior, time attack,
multiplayer, Super Emerald, nonlinear route, native/universal packaging, and
editor contracts. Synthesis occurs at the smallest existing owner boundary; it
must not introduce game-name, zone-name, trace-route, or frame-number carve-outs.

Two modify/delete conflicts remain deleted:

- `docs/status/rewind-round-trip-gaps.md` is a stale generated snapshot.
  Any still-live next-only fact is migrated to a current validation owner.
- `TestSidekickTouchHurtAnimationOwnership.java` pinned behavior to the wrong
  owner. Its unique coverage remains with the generic solid-contact and
  scripted-animation tests.

The add/add `tools/testing/README.md` conflict is a semantic synthesis:
preserve the official coordinator usage and the integration branch's
frozen-next adapter, authenticated inventory, partition, and isolated-parallel
session contracts.

## Highest-risk review points

- `pom.xml`, hooks, and workflows must preserve next packaging and
  destination-aware Mod API gates while adopting JDK 21, explicit hook
  installation, session-owned output, and separate guards.
- Audio must retain next streamed/mod surfaces without bypassing develop's
  policy-bearing command, catalog, admission, rewind, or observer owners.
- Special-stage and title-card timing follows live ROM scheduling, never fitted
  trace timing or obsolete per-frame expectations.
- S3K zone 22 and zone 23 identities must retain the official 24-row table,
  nonlinear HPZ resource ownership, and the true `$1701` arena path.
- Fixed-slot, object-loop, transition, shield, timer, and respawn state must have
  one authority each; parallel parent implementations must not both execute.
- Rewind and architecture guard baselines are recomputed from the merged graph
  and never widened to the larger parent count.

## Review status

Three independent read-only analysts reproduced their assigned conflict sets
and supplied commit-, symbol-, dependency-, and test-backed resolutions.
A fresh reviewer reproduced the exact 77-path merge tree and checked the
conflict ledger against the parent blobs and history. That conflict-only review
was useful planning evidence, but its unconditional GREEN/high-risk-checked
conclusion is retracted: a later broad audit against the official freeze found
retained helpers and policies whose final call sites had become unreachable or
had been replaced during synthesis. The affected ledger rows are corrected to
describe the repaired final contracts rather than the initially intended
resolution.

### Final broad-audit findings: GameLoop

The final pre-restoration candidate had lost six official-freeze lifecycle
connections even though most of their lower-level owners survived:

- results exit ran the returning-level load inside the fade callback instead of
  preserving the final whiteout row and pre-level delay;
- the special-stage terminal mode boundary committed before the run-chain
  closure bridge;
- shared transition gaps suppressed every row instead of allowing the first
  source-level row and then servicing only the gap's transition work;
- a suppressed level row could still advance or start an in-level title
  overlay;
- a seamless request raised after admission could apply in the same iteration;
  and
- Time Attack cross-act seamless routing could apply the destination before
  returning to the menu.

The reviewed repair was authored as
`52b861fe517ce571878caf68c4e5f824da70cedc` and integrated without semantic
change as `9c2698321a8f33262f7dffc1381af4f21c088d1c`. Its six caller-level wiring
regressions failed together in red run
`20260827T051655Z-p3716148-3c21c9` and passed 6/6 in final wiring run
`20260827T053529Z-p2-aaf19e`; the eight-class core selection passed 132/132 in
`20260827T052504Z-p2-6337b1`, and the focused guard selection passed 144/144 in
`20260827T053151Z-p2-56d7d8`. The ROM-backed candidate run
`20260827T052720Z-p2-7ca48b` retained exactly the four failures and one error of
the exact-`e13114ad` baseline `20260827T053012Z-p2-eb23f0`; it did not introduce
a new signature. The trace-infrastructure run
`20260827T052612Z-p2-406708` ran 47 tests and retained two exact baseline
closure-order failures, so it is comparison evidence rather than a green claim.

### Final broad-audit findings: movement and stage rings

The same broad audit found six more lost official-freeze call sites across
`PlayableSpriteMovement` and `RingManager`:

- Knuckles' slide get-up no longer ran the `Knux_TouchFloor` grounding tail;
- the shipped `FixBugs = 0` no-input wall-climb path no longer let the floor
  distance clobber the mapping-frame delta, including byte wrap and the
  empty-tile return distance;
- Sonic 2 Tails no longer used effective deceleration divided by four for the
  controlled-roll path, most visibly underwater;
- `Sonic_HurtStop` no longer performed its own per-game word comparison and
  returned on a bottom kill before terrain collision;
- lifting a control lock could manufacture a logical jump edge after the raw
  hardware edge had already been consumed; and
- stage-ring touch used semantic crouching rather than the shipped S1/S2 Sonic
  mapping frames, incorrectly affecting Tails and S3K.

The reviewed repair was authored as
`338e47723892bac976951692b96399aa6bb71dfc` and integrated without semantic
change as `8d8902bafaca00e7d5ce7ca16d22a7b6618541ec`. Red evidence is recorded by
`20260827T051747Z-p2-35e8f7` and the corrected HurtStop red run
`20260827T051918Z-p2-02872f`. The final four-class selection passed 220/220 in
`20260827T055511Z-p2-7dad96`, the bootstrap guard passed 7/7 in
`20260827T055605Z-p2-bdfdfa`, and the S3K canaries passed 57/57 in
`20260827T055145Z-p2-346c32`. Requested trace evidence retained two passes and
two existing frontiers in `20260827T054949Z-p2-4950f3`; cross-game run
`20260827T055259Z-p2-076df9` kept S1 green and the existing S2/S3K red
signatures.

### Conditional completion status

On the final integration commit `8d8902baf`, combined focused run
`20260827T060203Z-p3795260-ea65a0` passed all 226 selected GameLoop, movement,
roll, and stage-ring tests. This establishes reviewed focused restoration, not
whole-candidate certification. A final full ordinary suite and a separate fresh
`-Pguards` suite still have to be compared with the recorded parent baselines.
The synthesis may be called GREEN only if those final runs introduce no new or
worsened failure/error signature.
