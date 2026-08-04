# Trace v5 replacement freeze — pre-capture boundary

## Frozen identity

This freeze supersedes the pre-capture artifact bound to the earlier
development baseline. It authorizes scratch candidate work only; it does not
authorize installation or deletion of committed fixtures.

| Identity | Value |
| --- | --- |
| Reviewed source commit | `cd89d6ab4f623c99afc76629eb423cd03f246809` |
| Development baseline | `3573af57be947284a1f8398c7b4b4e05a8b12f14` |
| Source diff SHA-256 | `b45bfc7e521cddc5caa18fc4363ec9240a09d7a678e2a8fb36b431abf152335b` |
| Diff definition | `git diff --full-index --binary origin/develop..cd89d6ab4 \| sha256sum` |
| Native harness | `tools/bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe` |
| Native harness SHA-256 | `81b072f37a1b3a1202d6ac02b5e230365adbe3e9a6e2be9bb2fbee274738f459` |
| Native harness size | `359424` bytes |
| Native test SHA-256 | `3f90d1dc4df4fb80b9e3b3b4445b949934a209c7da2b964f3cbbb078f0730f4b` |
| Native test size | `619520` bytes |

The native harness was rebuilt once from this reviewed source boundary after
the upstream visual-run merge and reconciliation fixes. The build returned
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
Ran 37 tests in 32.821s — OK
```

The native no-gate suite passed with exit 0 after the rebuild. With ROM
variables absent it reported only expected ROM-dependent skips. The real-ROM
credits gate was run against the verified S1 REV01 ROM and passed:

```text
PASS S1 credits captures twice with deterministic logical evidence
```

This exercises two independent all-eight captures, deterministic candidate
comparison, raw-host sidecar equality, and first-divergence binding.

The installed fixture inventory passed both filesystem and Git-index checks.
`git diff --name-only -- src/test/resources/traces` remained empty throughout
the re-entry and merge. No production fleet capture, candidate assembly,
fixture regeneration, fixture deletion, or installation has occurred.

## Next boundary

Task 9 may now implement and review the literal 36-row scratch capture matrix,
preflight, no-replace assembler, and postprocessor. Every capture must verify
this source/diff/native identity and the unchanged predecessor inventory.
Capture output remains outside the installed fixture tree until an explicit
comparison and installation decision is made.
