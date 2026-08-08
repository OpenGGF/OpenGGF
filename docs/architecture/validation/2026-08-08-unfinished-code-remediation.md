# Unfinished-Code Remediation — Human Review Report

Date: 2026-08-08

## Review branch

- Branch: `feature/ai-unfinished-remediation-review`
- Worktree: `.worktrees/unfinished-remediation-review`
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
- The unused `AbstractLevel.markAllDirty()` no-op was removed. Geometry restore
  invalidation remains owned by `LevelRewindSnapshotAdapter` and persistent
  Plane B restoration remains owned by `LevelTilemapRewindAdapter`.
- S3K SMPS fixed-point traversal proves `FF 01/02/03` unreachable in all
  loader-supported S&K/S3 music and S&K-loader SFX streams plus both native
  SFX banks (`33-DF`, 173 entries each). Strict full-bank traversal resolves
  every native root/frontier; ROM type-check bytes prove S&K `DC` is CreditsK
  music and `DD-DF` are SFX, while S3 dispatches `DC-DF` as SFX. The differing
  `9B`/`AD` payloads and all alias targets are covered. Operand alignment
  remains supported for custom streams.

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

The native SFX extension was run separately on JDK 21 with the verified locked-on
ROM:

```text
mvn -Dmse=off \
  -Dtest=com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandReachability \
  -Ds3k.rom.path="<locked-on S3K>" test
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

That test covers both 173-entry native tables, all 346 native entries, and the
strict full-bank frontier/dispatch assertions described in the audio research
note.

The corrected rewind inventory gate also passed 32 focused tests after adding
`AizEndBossWaterfallChild` to the intentional graph-covered totals.

## Mecha Sonic parity follow-up

On 2026-08-08, branch `bugfix/ai-remediate-mecha-order` (based on reviewed
remediation HEAD `5cc94d457`) closed the audited Sonic 2 ObjAF movement-order
debt. The implementation follows shipped REV01 `loc_398C0`/`loc_39D4A` (the
audit's provisional `loc_398F4`/`loc_39D44` labels resolve to those source
labels): phase logic runs first, the LED and targeting sensor align, and one
outer-loop `ObjectMove` runs afterward. Child updates no longer overwrite those
pre-move positions. The existing Mecha Sonic and Death Egg Robot implementations
are covered by the dedicated DEZ ending replay at
`src/test/resources/traces/s2/dez_ending/`; its auxiliary stream shows ObjAF
present from frame 127 through the fight. With verified Sonic 2 REV01 ROM and
`-Ptrace-replay -Dmse=off -Dsurefire.forkCount=1
-Dsurefire.runOrder=alphabetical`, the dedicated
`TestS2DezEndingLevelSelectTraceReplay#replayMatchesTrace` passed 1/1 on both
base `5cc94d457` and candidate `4b4572cc3`. The complete-run fixture remains at
`src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/seg28_dez1/`.
No trace frontier advanced. JDK 21 focused validation also passed:

```text
mvn -Dmse=off -Dtest=com.openggf.tests.TestDEZMechaSonic test
Tests run: 30, Failures: 0, Errors: 0, Skipped: 0

mvn -Dmse=off -Dtest=com.openggf.tests.TestDEZMechaSonic,\
com.openggf.game.sonic2.objects.bosses.TestS2MechaSonicGraphRewind,\
com.openggf.game.rewind.TestBossChildNoDoubleSpawnParity,\
com.openggf.game.rewind.coverage.TestRewindCoverageGuard test
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
```

The first run was intentionally red before the production edit (27 tests, one
dash-start movement failure). The dedicated DEZ replay was green on both base
and candidate, so this change introduced no replay regression.

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
3. Continue the roadmap's load-profile semantics work.

## Follow-up: master-title audio disposition

The Wave 3 title-menu audio item is resolved on the remediation follow-up
branch. `MasterTitleScreen` now emits typed host-owned `NAVIGATE`, `CONFIRM`,
and `ERROR` cues through an injected sink. `Engine` connects that sink to the
existing `AudioManager` command timeline and initializes the normal LWJGL
presentation backend before the bootstrap title is shown. Because the title
screen runs before ROM/profile selection, the presentation source factory
resolves these names to deterministic synthesized PCM (`host/ui/*`) rather
than asking a game SMPS loader for a fabricated SFX id. The same stable asset
identity can be regenerated during presentation snapshot restore.

Focused JDK 21 validation:

```text
mvn -Dmse=off -Dsurefire.forkCount=1 \
  -Dtest=com.openggf.game.TestMasterTitleScreenAudio,com.openggf.audio.presentation.TestHostUiSfx test
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

The interaction tests cover one navigate, one confirm, one missing-ROM error,
selection-boundary silence, repeated confirm suppression, and repeated
load-error suppression. The PCM test resolves all three host cues, proves
stable identity reuse for each immutable sample, distinguishes their content,
and mixes each cue into nonzero output.

The lifecycle follow-up also passed:

```text
mvn -Dmse=off -Dsurefire.forkCount=1 \
  -Dtest=com.openggf.audio.TestAudioManagerRuntimeInstallation,com.openggf.TestEngine test
Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
```

That gate proves `AUDIO_ENABLED=false` performs no install, enabled startup
installs once, and the title-exit reset followed by gameplay initialization
asks the retained backend to recreate exactly one closed/replaced presentation
sink. Re-entry then presents a cue through the rebuilt sink without replacing
the backend.

The original audit remains the complete disposition ledger; this report records
only what this remediation branch changed or deliberately rejected.
