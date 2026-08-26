# Develop into next conflict synthesis

Date: 2026-08-26

## Frozen inputs

- Merge base: `59e59c8feb5fb5a247ff0ab43da63aeccc742cb0`
- Frozen `next`: `84d9a3761f618035dd1caa40a3d5fc72a1019693`
- Official 0.6 scope freeze: `a17adaba5b57298ffd88c6d7b6ab3a4d6aff87bb`
- Planning head: `c02816c6b1f0de1cb055cd318f5581ae21e037e9`
- Synthetic recursive merge tree: `bf953a08572551f984962afb078dc04dc8b64553`

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
A fresh reviewer reproduced the exact 77-path merge tree, checked every
high-risk family against the parent blobs and history, verified all cited test
classes, and reported GREEN after two citation corrections. Every ledger row is
now marked `reviewed`.
