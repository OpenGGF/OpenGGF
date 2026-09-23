# Changelog

OpenGGF keeps one changelog file per release so that release notes remain
readable and historical versions can be referenced directly.

## Unreleased (`next` / 0.8)

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
  contain malformed or excessive traffic.

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
