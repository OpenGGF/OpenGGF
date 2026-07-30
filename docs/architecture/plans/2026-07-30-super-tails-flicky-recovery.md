# Super Tails Flicky Recovery

## Requirements

- Super Tails visibly owns four attacking Flickies whenever the form is active.
- The debug powered-form command selects Super Tails for main-player Tails without
  mutating emerald progression.
- Normal transformation eligibility remains driven by ROM progression state.
- Bird art remains ROM-backed, and rewind/object lifecycle rules remain intact.
- Sidekick Tails must not acquire a flock.

Non-goals are changing the existing ROM-derived movement, targeting, collision, or
palette behavior. The main risks are duplicate flocks, late art publication, and a
missing dynamic object after runtime manager replacement.

Acceptance criteria: production and debug activation each create one flock; active
Super Tails recreates a missing flock without duplicating a live one; the flock requests
its ROM art before its first render pass; focused controller, flock, art-registry, and
rewind guards pass on JDK 21.

## Exploration Synthesis

`Sonic3kSuperStateController.onTransformationStarted()` currently registers
`SuperTailsFlickyFlockObjectInstance` once through an optional `PowerUpSpawner`.
Unlike `ensureHyperSonicStars`, it neither scans the live `ObjectManager` nor recovers
after a missed allocation or manager rebuild. `SuperStateController.debugActivate()`
bypasses emerald gates, but the S3K controller still derives its tier from progression,
so debug-powered Tails can retain `NORMAL` as its presentation tier. Finally,
`SuperTailsFlickyFlockObjectInstance` first requests standalone art from
`appendRenderCommands`; Hyper stars already request comparable Kosinski-module art from
`update()`.

Both independent explorations recommended a semantic controller-owned ensure path,
explicit S3K debug-tier selection, and update-owned art preparation. The local
disassembly tree is unavailable in this checkout, so existing verified ROM constants and
the implementation's cited `Obj_SuperTailsBirds` labels remain the behavioral oracle.

## Architecture Decision

The S3K super-state controller owns form selection and flock presence. A private debug
activation flag changes only tier selection during the existing transformation callback.
A private ensure operation scans the current object manager for a live flock bound to
the same player, then uses the existing spawner only when one is absent. Active-form
updates retry reconciliation, covering transient allocation and manager replacement.

The flock continues to own its four ROM bird states, target identities, attacks, render
commands, and rewind recreation. Art preparation moves into its update path while render
retains its defensive ensure call. Rollback is limited to these controller/object changes.

## Feature Design

Normal activation selects the ROM-progression tier. Debug activation selects `HYPER` for
Sonic/Knuckles and `SUPER_TAILS` for main-player Tails, without granting emeralds.
Transformation ensures one flock. Each active update scans for a matching live flock and
recreates it if absent. Existing flocks fly away when the semantic form becomes inactive.

Tests cover normal registration, debug registration/tier selection, no duplicate
registration, and pre-render ROM-art requests.

## Implementation Plan

1. Add failing controller activation and flock art-timing tests.
2. Add bound-player identity and update-owned art preparation to the flock.
3. Add S3K debug-tier selection plus deduplicating active flock reconciliation.
4. Run focused tests with the discovered S3K ROM under JDK 21, then rewind guards.

## Integration Report

Changed:

- `Sonic3kSuperStateController` now selects the strongest character form for debug
  activation, restricted to main-player Tails for `SUPER_TAILS`, and reconciles one
  live bound flock while that form remains active.
- `SuperTailsFlickyFlockObjectInstance` exposes owner identity for deduplication and
  requests its standalone ROM art during update as well as rendering.
- Focused controller and flock tests cover progression activation, debug activation,
  secondary-Tails exclusion, live-manager deduplication, and pre-render art loading.
- `CHANGELOG.md` records the player-visible fix.

Verification used JDK 21 and the discovered locked-on S3K ROM:

`mvn -Dmse=off '-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen' -Dtest=TestSonic3kSuperStateRewind,TestSuperTailsFlickyFlockRuntime,TestSonic3kPlcArtRegistry,TestPatternSpriteRendererCorruptionGuard,TestRewindCoverageGuard test`

Result: 109 tests passed with no failures or errors. `git diff --check` also passes.
No movement/targeting/collision code or ROM addresses changed.

## End-to-End Review

Independent review found one blocker: the first debug implementation allowed any Tails
instance to select `SUPER_TAILS`. The final implementation gates both debug tier selection
and flock reconciliation on main-player identity, with a regression test for secondary
Tails. The review also requested explicit manager deduplication coverage, changelog
handling, JDK 21 evidence, and completion of this artifact; all are addressed.

Residual risk is limited to visual validation in a live GLFW session, which is not
automated here. The ROM-backed art registry, renderer corruption guard, controller/flock
behavior, and rewind coverage are green. Human review should confirm four visible birds
after normal all-Super-Emerald activation and the `Shift+U` debug activation.
