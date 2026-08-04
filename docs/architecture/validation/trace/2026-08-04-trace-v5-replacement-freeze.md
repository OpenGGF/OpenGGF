# Trace v5 replacement freeze — replacement capture boundary

## Frozen identity

This freeze supersedes the pre-capture artifact bound to the earlier
development baseline. It authorizes scratch candidate work only; it does not
authorize installation or deletion of committed fixtures.

| Identity | Value |
| --- | --- |
| Reviewed source commit | `93f0abb81dc216f5ab6fee998db75e9eb4410379` |
| Development baseline | `3573af57be947284a1f8398c7b4b4e05a8b12f14` |
| Source diff SHA-256 | `f9e7818f3cef38e7eae3fa623ed4f5956cbe39805006f59ecd668f93d9b8f0d7` |
| Diff definition | `git diff --full-index --binary origin/develop..93f0abb81 \| sha256sum` |
| Native harness | `tools/bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe` |
| Native harness SHA-256 | `2237b64955d268a57531b3019abf3c4cf9baece02f50c6558f33473ef51f1a0c` |
| Native harness size | `359936` bytes |
| Native test SHA-256 | `8c44925e9a99428c0fef20f490ded3e87084dfe1e0a8131e1cf0e22b5cf94f8b` |
| Native test size | `620032` bytes |

The native harness was rebuilt from this replacement source boundary after the
first 35 matrix rows captured successfully but the 67-segment Knuckles row
exposed a valid same-frame Kos module retirement plus append whose shifted head
remained busy. The recorder now validates the shifted prefix, permits that
ROM-proven busy state, retires one completion, and reconciles the new tail; its
focused contract covers that exact ROM ordering. The build returned exit 0;
Mono reported only the known .NET Framework 4.8 toolset warning.

## Reconciled upstream work

The isolated branch incorporates the latest `origin/develop` visual-run wins
before freezing. The merge conflicts in `CHANGELOG.md` and the launcher test
were reconciled by retaining both trace-v5 and visual-run records, migrating
positive visual tests to generated strict-v5 fixtures, and keeping the
production visual admission implementation unchanged.

Strict catalog discovery now skips rejected predecessor traces rather than
introducing a legacy compatibility path. The catalog contract itself runs on
a generated v5 run. The retained predecessor runs remain available for the
Task 9 regeneration matrix.

## Verification

Focused Java selection (v5 parsing, recorder contracts, divergence reports,
catalogs, dynamic art, timing, special-stage row publication, replay clocks,
and the merged visual launcher) passed:

```text
Tests run: 265, Failures: 0, Errors: 0, Skipped: 7
```

The seven skips are the two intentional recorder-contract skips, three
intentional parser fixture skips, and two ROM-dependent timing skips.

The complete Python testing directory passed:

```text
python3 -m unittest discover -s tools/testing -p 'test_*.py'
Ran 42 tests — OK
```

The native no-gate suite passed with exit 0 after the replacement rebuild. With ROM
variables absent it reported only expected ROM-dependent skips. The real-ROM
credits gate was run against the verified S1 REV01 ROM and passed:

```text
PASS S1 credits captures twice with deterministic logical evidence
```

This exercises two independent all-eight captures, deterministic candidate
comparison, raw-host sidecar equality, and first-divergence binding.

The installed fixture inventory passed both filesystem and Git-index checks.
`git diff --name-only -- src/test/resources/traces` remained empty throughout
the re-entry, merge, and diagnostic captures. Rows 1–35 of the first batch
passed; row 36 was preserved as a diagnostic batch after the recorder correction
and passed strict v5 validation with all 67 segment directories present. No
installed fixture has been regenerated, deleted, or modified.

## Next boundary

Task 9 must now recapture all 36 rows in one fresh absent batch because the
recorder source changed after the preceding diagnostic. Every capture must
verify this source/diff/native identity and the unchanged predecessor inventory.
Capture output remains outside the installed fixture tree until an explicit
comparison and installation decision is made.
