# Changelog

OpenGGF keeps one changelog file per release so that release notes remain
readable and historical versions can be referenced directly.

## Unreleased (`develop` and `next` / 0.7)

Current fixes include the normal explosion effect and break sound for
upside-down S3K capsules, crowded-scene sprite retention with sprite limits
disabled, native tally culling and accidental-mask prevention, and Tails
carrying Sonic into CNZ1 after the MGZ2 rescue. See the
development changelog below.

Both development branches currently share the same commit and release line.
Current changes are recorded in
[CHANGELOG.0.7.prerelease.md](CHANGELOG.0.7.prerelease.md), with earlier themes in
[CHANGELOG.0.7.md](CHANGELOG.0.7.md). The [0.8 roadmap](docs/project/v0.8-roadmap.md)
remains future scope; `next` has no separate 0.8 implementation line yet.

Recent maintenance includes protected movie and trace controls, codec-backed rewind
snapshot equality, session-owned
render-registry cleanup, S3K's fresh-entry camera-focus calculation,
declared-scope stock parity reporting, Sonic 2 object-slot and sidekick visibility
timing, S3K fresh-level ring timing and touch-suppressed lightning attraction,
S1 Marble flame retirement and visual trace handoff ownership, Carnival Night
cylinder rider ownership, Lava Reef crusher timer dispatch, the original S3K
final emerald-reveal countdown, Death Egg lift and staircase rider-release
boundaries, gravity-puzzle landings and walk-offs, turbine floor responses and hurt
control, ordered physical gameplay and native-menu short-tap admission, and live
PAL50/NTSC60 simulation/audio scheduling independent of the window rendering limit,
display-locked pacing on matching refresh rates with a drift-regulated speaker,
opt-in render-only motion interpolation on higher refresh rates with an Auto rendering
limit beside its Display setting,
with region-cadence viewport recording, redraw-independent native object state,
and row-owned trace-ghost animation whose
seeks preserve observed historical context. The level editor menu now opens on a
short front page with Exit to title. Creator tooling now includes task recipes,
local build/test/run configuration for all seven starter purposes, read-only
preflight and actionable SDK reports.

## Release files

- [0.7 prerelease / current development snapshot](CHANGELOG.0.7.prerelease.md) — current native-game accuracy, campaign, visual editor authoring and native Stageplay directing work, with the linked earlier development themes.

- [0.6.20260911](CHANGELOG.0.6.md)
- [0.5.20260411](CHANGELOG.0.5.md)
- [0.4.20260304](CHANGELOG.0.4.md)
- [0.3.20260206](CHANGELOG.0.3.md)
- [0.2.20260117](CHANGELOG.0.2.md)
- [0.1.20260110](CHANGELOG.0.1.md)
- [0.05](CHANGELOG.0.05.md)
- [0.01](CHANGELOG.0.01.md)

## 0.7 development documentation

- [Release summary](docs/changelog/v0.7-release-summary.md) — current scope and limitations.
- [Development ledger](docs/changelog/v0.7-prerelease-detailed.md) — integration and validation evidence.
- [Trace scope](docs/status/trace-scope-release-7.md) — retained no-regression contract.
- [0.7 roadmap](docs/project/v0.7-roadmap.md) — complete campaigns before feature/API publication.

## Published 0.6 documentation

- [0.6 changelog](CHANGELOG.0.6.md) and [release summary](docs/changelog/v0.6-release-summary.md).
- [Release notes](RELEASE_NOTES_v0.6.20260911.md).
- [Raiscan's thoughts on 0.6](docs/changelog/raiscan-0.6-thoughts.md).
- [Archived development ledger](docs/changelog/v0.6-development-ledger.md).
- [Detailed engineering history](docs/changelog/v0.6-prerelease-detailed.md).
- [Release-6 trace evidence](docs/status/trace-scope-release-6.md).

The published `v0.6.20260911` tag and `release/0.6.20260911` branch retain the
release snapshot. Its recorded limitations remain historical release evidence.
Current replay evidence continues in the [trace frontier log](docs/status/trace-frontier-log.md).
