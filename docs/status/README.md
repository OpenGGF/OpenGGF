# Status ledgers

Start with the [game status summary](../guide/playing/game-status.md). It gives
the current state of each game on one page and keeps "implemented", "playable"
and "verified parity" as separate claims. The files here are the evidence
behind it. Most are long, so search them rather than reading them through.

| File | What it holds | Order |
|---|---|---|
| [known-bugs.md](known-bugs.md) | Open engine-wide and S1/S2 bugs and parity gaps, each with a removal condition | Newest first |
| [s3k-known-bugs.md](s3k-known-bugs.md) | Open S3K bugs and route frontiers | Newest first |
| [known-discrepancies.md](known-discrepancies.md) | Accepted engine-wide and S1/S2 deviations from the shipped ROMs | Table of contents |
| [../S3K_KNOWN_DISCREPANCIES.md](../S3K_KNOWN_DISCREPANCIES.md) | Accepted S3K deviations only | Table of contents |
| [trace-frontier-log.md](trace-frontier-log.md) | Every trace frontier move, fix, regression and sweep, with command, commit and first error | Append-only. Entries since 2026-09-17 are at the end; the start of the file is an older history that hooks protect from rewriting |
| [audio-frontier-log.md](audio-frontier-log.md) | Audio parity frontiers and oracle results | Append-only |
| [level-test-coverage.md](level-test-coverage.md) | Per-act and character-route test coverage matrix | Dated sections |
| [trace-scope-release-7.md](trace-scope-release-7.md), [trace-scope-release-6.md](trace-scope-release-6.md) | Which traces gate each release line | Per release |
| [trace-segments-baseline.md](trace-segments-baseline.md) | A dated measurement of the segment trace set | Historical record |
| [object-occupancy-frontier.md](object-occupancy-frontier.md), [special-stage-bug-list.md](special-stage-bug-list.md), [editor-object-property-coverage.md](editor-object-property-coverage.md) | Narrower working lists | Per file |
