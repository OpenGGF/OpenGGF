# Changelog

OpenGGF keeps one changelog file per release so that release notes remain
readable and historical versions can be referenced directly.

## Unreleased (`next` / 0.8)

- **Time Attack is a bundled mod:** Time Attack now ships as the first-party **Time Attack**
  mod, bundled with JVM builds and enabled by default (disable it in **Mods**). It adds a
  Time Attack entry to the master title and plays every run as a stock, non-saving session,
  so ghost times stay comparable. Ghosts and the player identity stay in `ghosts/` and
  `identity/`. The engine no longer carries racing code, its `timeAttack.*` settings or the
  Netty, Bouncy Castle and SQLite libraries; the master server, dedicated host and verifier
  build separately from `racing/server`. Native builds do not load code mods, so Time Attack
  is JVM-only. Multiplayer racing lives in the mod too: host a LAN room on the in-process
  host, join one by invite, or browse and create master-server rooms; the lobby, room browser
  and settings (port, display name, master URL, minimap) are part of the mod's menu, and its
  settings are stored by the mod instead of `config.yaml`.

- **Mod API candidate: title entries and gameplay runs:** a mod may add one master-title entry
  whose scene launches stock gameplay runs through `SceneContext.gameplay()` and is resumed when
  each ends. A run host observes level starts and executed steps, may hold steps, draws ghosts
  and an overlay, and issues retry, leave and post-completion spectator commands; a session run
  policy controls special/bonus stage entry, act-completion handoff, rewind and the editor. See
  [Title entries and gameplay runs](docs/modding/guides/gameplay-runs.md).

- **Configurable widescreen HUD anchor:** the score, time, rings, and lives HUD can
  keep its centered native-frame position or align to the left edge of the screen.
  Native 4:3 positioning is unchanged.

- **Multiplayer time-attack integrity:** Race hosts now enforce one active,
  strictly ordered attempt per player during the running phase, reject ghost
  streams that advance ahead of server-observed time, and bind each finish and
  verifier verdict to one immutable attempt with valid recording evidence.
  Control messages reject ambiguous duplicate or unknown fields, while terminal
  verification jobs expire after a brief cache window. Active bans and timeouts
  immediately revoke master access, and verification work has bounded capacity
  and lifetime with auditable non-cheating void outcomes when workers disappear.
  Direct and manual LAN joins now pin the host's TLS certificate and identity
  before sending credentials. Results and spot checks retain the admitted
  participant across slot reuse, broker strikes clean up hosted rooms, and
  bounded room fields, client event/ghost queues, and nested-message validation
  contain malformed or excessive traffic. Departed votes leave the track tally,
  active finishes retain a two-second transit grace without permitting new
  attempts, and repeated finish-evidence violations close the sender. Reused
  recordings remain available to fresh verification jobs, identity keys are
  private from creation and an interrupted identity creation retries cleanly,
  ordinary master replies and final joins stay bound to their request order and
  room context, and a rejected relay attach fails the join immediately.

- **Bundled first-party mods:** an OpenGGF build can ship first-party code mods with
  its JVM distribution. The engine artifact pins each one's exact jar hash; a bundled
  mod whose shipped bytes match is enabled and trusted by default, validated like any
  other code mod, and marked BUNDLED in the Mod Manager, where it can be disabled (the
  choice survives upgrades) but not uninstalled. Missing, damaged or modified bundled
  jars are reported and never loaded, and a `mods/` copy with a bundled id is ignored.
  Deterministic launches and native builds never load bundled mods.

- **Binary mod storage:** code mods can keep binary files of up to 4 MiB, such as
  recorded inputs or ghosts, in their private save directory beside their text
  settings. Each write replaces the whole file atomically and an oversized write
  leaves the previous file untouched.

Work promoted from `next` is recorded in [CHANGELOG.0.7.md](CHANGELOG.0.7.md).

## Release files

- [0.7 prerelease / current development snapshot](CHANGELOG.0.7.md) — including the controller-accessible title hub, engine settings, and shared UI improvements.

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
