# Trace v5 replacement freeze — replacement capture boundary

## Frozen identity

This freeze supersedes the pre-capture artifact bound to the earlier
development baseline. It authorizes scratch candidate work only; it does not
authorize installation or deletion of committed fixtures.

| Identity | Value |
| --- | --- |
| Reviewed source commit | `369c3a89975ba54f471cf9560e7008f0aa58761c` |
| Development baseline | `3573af57be947284a1f8398c7b4b4e05a8b12f14` |
| Source diff SHA-256 | `8ae54221dd82fb4aa43b347c09d1ebe69aa4bc019950b84f1d320e3d2405ee79` |
| Diff definition | `git diff --full-index --binary origin/develop..369c3a899 \| sha256sum` |
| Native harness | `tools/bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe` |
| Native harness SHA-256 | `48e6deca61ac2a9ad6332e7170aa89f7468d82bfced33f66dd88475b501af3dc` |
| Native harness size | `359936` bytes |
| Native test SHA-256 | `6c5aad4c1b0a331ee61b096495550f9bf7ba68ebfa65119700c7f07569931e35` |
| Native test size | `619520` bytes |

The native harness was rebuilt from this replacement source boundary after the
first 35 matrix rows captured successfully but the 67-segment Knuckles row
exposed a valid same-frame Kos module retirement plus append. The recorder now
validates the shifted prefix, retires one completion, and reconciles the new
tail; its focused contract covers that exact ROM ordering. The build returned
exit 0; Mono reported only the known .NET Framework 4.8 toolset warning.

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
the re-entry, merge, and first capture batch. Rows 1–35 of the first batch
passed; row 36 was preserved as a failed diagnostic batch after 314 seconds,
with no published output. No installed fixture has been regenerated, deleted,
or modified.

## Next boundary

Task 9 may now recapture row 36 using a fresh absent batch, then assemble and
validate the candidate. Every capture must verify this source/diff/native
identity and the unchanged predecessor inventory. Capture output remains
outside the installed fixture tree until an explicit comparison and
installation decision is made.
