# Unfinished-Code Remediation — Human Review Report

Date: 2026-08-08

## Review branch

- Branch: `feature/ai-unfinished-remediation`
- Worktree: `.worktrees/unfinished-remediation`
- Updated base: `develop` at `228e2effa`
- Delivery state: local and unpushed; not merged into `develop`

The branch is intentionally retained for human review. It does not delete
coherent unfinished features merely because they are incomplete. Where an
incorrect temporary implementation was removed, its behavior was replaced by
the disassembly-owned topology and covered by tests.

## Integrated outcomes

### Route and object behavior

- LRZ1 restores the non-Knuckles falling-introduction state owned by
  `SpawnLevelMainSprites loc_68A6`. Saved returns are gated without suppressing
  LBZ1's buried-start launch. The earlier SSZ attribution was disproved and the
  current docs now say so.
- The AIZ Knuckles miniboss napalm attack now uses the three native barrel
  children, harmful FallingShots, ROM-backed art/mappings, native explosion
  timing, and graph rewind. The artificial central controller was removed only
  after the disassembly proved that the barrel children own this behavior.
- AIZ2 end-boss emerge/re-submerge now creates the subtype 0/2 waterfall splash
  children with native allocation, init/draw boundaries, palette, animation,
  and rewind behavior.

### Honest capability and ownership boundaries

- `SpecialStageDebugCapabilities` makes provider support explicit. S1 keeps
  direct movement, S2 keeps its full debug tools, and S3K keeps X/Z navigation.
  Unsupported provider actions no longer silently mutate stage state or create
  rewind boundaries. Shared global overlay/screenshot key meanings remain
  documented and active.
- S1 background Y is confirmed to be owned by the eight zone scroll handlers,
  including same-instance rewind recomputation. The legacy runtime query and
  ignored renderer hint were removed; compatibility overloads are explicitly
  equivalence-tested.
- S3K SMPS fixed-point traversal proves `FF 01/02/03` unreachable in all
  loader-supported S&K/S3 music and S&K-loader SFX streams. Operand alignment
  remains supported for custom streams. The separate S3-native SFX table,
  differing IDs `9B`/`AD`, and `DC`–`DF` aliases remain open and are not called
  resolved.

## Independently rejected work

The candidate Big Arm implementation on
`bugfix/ai-remediate-lbz-big-arm` (`98d968d7f`) was rejected and not
cherry-picked. Independent review found invented phases, the wrong mapping
ownership, no native articulated arm/grab graph, and an invented defeat/capsule
flow. The current inert placeholder therefore remains a P0 blocker, but the
repository did not trade an honest placeholder for inaccurate gameplay.

## Validation

All commands ran under Maven's JDK 21 with the canonical project-root Sonic 1
REV01, Sonic 2 REV01, and locked-on S3K ROM properties.

Combined focused gate:

```text
Tests run: 93, Failures: 0, Errors: 0, Skipped: 2
```

This includes LRZ/LBZ bootstrap and saved-return behavior, AIZ napalm route and
rewind, AIZ2 splash graph rewind and art, SMPS reachability/operand alignment,
special-stage capability routing, all eight S1 scroll handlers, and renderer
source guards. The two skips are the existing environment-dependent renderer
sampling cases.

The corrected rewind inventory gate also passed 32 focused tests after adding
`AizEndBossWaterfallChild` to the intentional graph-covered totals.

Deterministic full-suite command for both current `develop` and the feature:

```bash
mvn -Dmse=off -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dsonic1.rom.path=<Sonic 1 REV01> \
  -Dsonic2.rom.path=<Sonic 2 REV01> \
  -Ds3k.rom.path=<locked-on S3K> clean test
```

| Run | Total | Pass | Failure | Error | Skipped |
|---|---:|---:|---:|---:|---:|
| `develop` `228e2effa` | 14,342 | 14,263 | 34 | 14 | 31 |
| remediation branch | 14,388 | 14,309 | 34 | 14 | 31 |

Normalized Surefire comparison by class, invocation name, outcome, and
exception type found identical failure/error sets and no baseline
PASS-to-FAIL/ERROR transition. The feature adds 48 passing invocations. Two
obsolete scalar-only napalm probe invocations disappear because the projectile
is now parent/sibling graph-linked; their replacement graph classification and
real `ObjectManager` rewind tests pass. Raw-message-only differences are one
volatile launcher identity and six Java helpful-NPE messages on already-erroring
Tornado tests.

## Still open

Highest-priority remaining work is deliberately visible rather than deleted:

1. Port Big Arm from the native object graph and defeat flow, then validate the
   Knuckles LBZ route.
2. Capture route/trace evidence for the AIZ napalm and AIZ2 splash changes.
3. Inventory the separate S3-native SFX table and native alias targets before
   closing the SMPS meta-command finding completely.
4. Resolve `AbstractLevel.markAllDirty()` through its real dirty-region owner.
5. Continue the roadmap's title-menu audio, load-profile semantics, and Mecha
   Sonic movement-order work.

The original audit remains the complete disposition ledger; this report records
only what this remediation branch changed or deliberately rejected.
