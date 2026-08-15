# Trace scope for release 6

Release 6 targets the **Sonic path through S1, S2 and S3**. Sonic 3 & Knuckles is only
partially implemented past Launch Base, and the Knuckles routes are not a release-6
deliverable at all, so a large part of the S3K trace suite is **expected red** and always
has been. Ranking trace work by error count hides that: the biggest numbers in the suite
sit almost entirely outside the release.

This document records the split and how it is enforced. Numbers are measured on a full
`-Ptrace-replay` sweep at commit `9900b3114` (843 tests, the expected total — a smaller
total means the run truncated, a larger one means stale reports were counted).

## The two axes that put a trace out of scope

1. **Zone.** S3K zone ids 0-6 (AIZ, HCZ, MGZ, CNZ, ICZ, LBZ) are the Sonic 3 half and are
   in scope. Ids 7-13 (MHZ, FBZ, SSZ, SOZ, LRZ, HPZ, DDZ, DEZ) are the S&K half. Anything
   past LBZ is expected red.
2. **Character.** Knuckles routes are out of scope regardless of zone.

Bonus and special stages inherit the scope of the run that recorded them, **not** the name
of the stage: the Sonic+Tails Slots/Gumball/Pachinko replays are in scope because they come
from a Sonic+Tails movie, while the standalone `bonus_slots` / `bonus_gumball` /
`bonus_pachinko` fixtures are Knuckles recordings and are not.

## Identifying which run a fixture came from

Every fixture states it. `metadata.json` carries `characters`, `main_character` and
`source_bk2`, and segment fixtures live under `runs/<bk2-stem>/<zone>`. Four movies are
committed:

| `source_bk2` | characters |
|---|---|
| `s3k-sonic-tails-complete-emeralds.bk2` | sonic + tails |
| `s3k-complete-sonic-tails.bk2` | sonic + tails |
| `s3k-knuckles-complete-superemeralds.bk2` | knuckles |
| `s3-knux-multibonus-ss.bk2` | knuckles |

**Do not infer the character set from a class or directory name.** Two traps have already
cost rounds: the standalone `bonus_*` classes look like siblings of the Sonic+Tails bonus
replays but are Knuckles recordings from a different movie, and the `dez23*` directories
are not Death Egg — the level-size table names that zone `Special Stage Arena (HPZ)`
(`docs/skdisasm/sonic3k.asm:38144`), so those eight classes are S&K-half and out of scope.

## In scope for release 6 — S3K

13 red classes, 30,975 errors, plus the Sonic+Tails bonus and special stages reachable from
those zones.

| class | errors | first error |
|---|---:|---|
| `TestS3kSonicTailsLbzSegmentTraceReplay` | 7174 | 958, `player_animation_id` |
| `TestS3kSonicTailsCnzSegmentTraceReplay` | 6300 | 5754, `player_animation_id` |
| `TestS3kSonicTailsMgzSegmentTraceReplay` | 3446 | 10709, `x` |
| `TestS3kSonicTailsHcz2SegmentTraceReplay` | 3437 | 0, `y_speed` |
| `TestS3kSonicTailsIczSegmentTraceReplay` | 2862 | 1983, `tails_animation_id` |
| `TestS3kSonicTailsAiz5SegmentTraceReplay` | 2138 | 0, `x_speed` |
| `TestS3kSonicTailsAiz3SegmentTraceReplay` | 1567 | 0, `tails_x` (0x7F00 respawn sentinel) |
| `TestS3kSonicTailsAiz2SegmentTraceReplay` | 1034 | 0, `tails_y` |
| `TestS3kSonicTailsIcz2SegmentTraceReplay` | 965 | 0, `rings` (inventory boundary) |
| `TestS3kSonicTailsAiz4SegmentTraceReplay` | 963 | 0, `tails_x` (0x7F00 respawn sentinel) |
| `TestS3kSonicTailsHcz3SegmentTraceReplay` | 640 | 0, `rings` (inventory boundary) |
| `TestS3kSonicTailsHcz4SegmentTraceReplay` | 447 | 0, `rings` (inventory boundary) |
| `TestS3kHczCompleteRunTraceReplay` | 2 | 29095, `rings` — **deliberate**, see its javadoc |

Green and protected: `TestS3kSonicTailsAizSegmentTraceReplay`,
`TestS3kSonicTailsHczSegmentTraceReplay`.

Also in scope: `SlotsBonus`, `Pachinko`/`Pachinko2`/`Pachinko3`, `Gumball`/`Gumball2`, and
the `Ss*SpecialStage` classes — all from the Sonic+Tails run. `Ss4` (2 errors) and `Ss5`
(7 errors) are the closest classes in the suite to green.

The S1 and S2 suites are in scope in full.

## Out of scope — the `trace-replay-r7` profile

Two groups, moved out of `-Ptrace-replay` and into `-Ptrace-replay-r7`:

- **S&K-half zones, Sonic+Tails** (30 classes, 49,261 errors): `Mhz*`, `Fbz*`, `Ssz*`,
  `Soz*`, `Lrz*`, `Hpz*`, `Ddz*`, `Dez*`, `Zone0c*`, and `TestS3kMhzCompleteRunTraceReplay`.
- **Knuckles routes**: `TestS3kSlotsBonusTraceReplay` (now the `Knuckles` nested class of
  the merged stage file, gated by tag rather than by file name — see below),
  `TestS3kGumballBonusTraceReplay`,
  `TestS3kPachinkoBonusTraceReplay`, `TestS3kSpecialStageTraceReplay`,
  `TestS3kKnucklesSuperEmeraldRunChain`, `TestS3kMegaRunChain`.

### How the split is expressed

Two mechanisms, both live:

1. **File name.** The include/exclude lists in the two profiles in `pom.xml`, mirrored
   one-for-one. This gates every trace class that still has one flat class per route.
2. **JUnit tag.** A trace class restructured to one file per zone holds several
   character sets, and the release-6 split cuts *between characters inside one file* —
   a file name cannot express that. Each character-set class carries exactly one of
   `trace-scope-r6` / `trace-scope-r7`; `-Ptrace-replay` excludes the r7 tag and
   `-Ptrace-replay-r7` excludes the r6 tag. The Slots bonus stage is converted;
   `docs/architecture/plans/2026-08-15-s3k-trace-class-naming-restructure.md` has the
   pattern and the remaining stages.

Adding an out-of-scope trace class means adding it to BOTH file-name lists, or tagging
it `trace-scope-r7` if its zone has been converted.

Run them with:

```bash
mvn -Ptrace-replay-r7 test        # out-of-scope traces only
```

**Nothing is deleted, weakened or made advisory.** These classes still run, still assert
exactly what they asserted before, and still fail if they regress — they are gated out of
the release-blocking sweep, not silenced. A fix that is correct for a release-6 zone but
breaks an S&K one is still a bug, and the frontier log still records their measured state.

### When you must run the r7 profile anyway

The gate removes cross-checks that have caught real problems, so run `-Ptrace-replay-r7`
on both trees whenever a change touches **shared** runtime rather than a release-6 zone:

- the sidekick CPU/despawn path (`SidekickCpuController`) — a ROM-cited fix there moved
  both FBZ and SSZ, which are both r7;
- the bonus-stage runtime and coordinator — the three **green** Knuckles bonus classes
  (1024 / 1277 / 2900 frames compared) are the best available cross-check for slot-runtime
  changes, and they caught nothing only because the change was right;
- the hardware-timing port, PLC/Kosinski queues, camera, or anything in `AbstractTraceReplayTest`.

A green r7 class going red is exactly as informative as a release-6 one going red. The
scope split is about **what blocks a release**, not about what is worth knowing.

## Measured effect of the gate, and one thing it exposed

At `9900b3114`, before the gate: **843 tests, 64 red**. After: **806 tests, 33 red** in
`-Ptrace-replay`, and **37 tests, 32 red** in `-Ptrace-replay-r7`. The 32 gated reds are
accounted for one-for-one by name.

The arithmetic leaves one class over, and it is a real finding rather than a rounding
error. `TestTraceStructuralRowComparator` was green in the ungated sweep and is red in the
gated one, failing with:

> `GameServices.hardwareTiming() requires an active gameplay mode.`

It fails **in isolation on both an ungated and a gated tree**, so the gate did not break
it: the class has a latent dependency on some earlier class in the run leaving a gameplay
session open, and its previous green was an ordering accident. Removing 37 classes changed
the ordering and took the accident away. The fix is for that class to open its own session
(`TestEnvironment.activeGameplayMode()`, as `TestS3kSlotBonusStageCoordinator` does), not
to restore the ordering it happened to rely on. Until then it is red in the release-6
sweep and is **not** a trace-parity failure.
