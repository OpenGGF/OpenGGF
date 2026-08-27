# Next direct-Maven convergence validation ledger

## Boundary

This ledger records the immutable validation boundary for the direct-Maven
convergence work. It is a comparison record for Tasks 5 and 6; sections for
future commands remain unrun until those tasks execute.

## Immutable refs

| Ref | SHA | Evidence |
|---|---|---|
| `HEAD` | `33a799c014906bd75e99da329abc465ecf466487` | Assigned implementation branch at task start |
| `next` | `33a799c014906bd75e99da329abc465ecf466487` | Local `next` ref at task start |
| `origin/next` | `33a799c014906bd75e99da329abc465ecf466487` | Remote-tracking ref at task start |
| `origin/develop` | `f4f3bd10cdf25a8e9a69f598be528b1276fc5fd6` | Upstream rollback reference at task start |

`33a799c014906bd75e99da329abc465ecf466487` is an ancestor of `HEAD`. The
working branch starts from the verified `next` snapshot; only the planning and
validation artifacts for this task are added before implementation.

## Snapshot evidence

The verified snapshot evidence supplied for this convergence records:

- 18,197 ordinary identities, with no new merge regression;
- Mod API: 27/27;
- guards: 520/521, with only the known Trace V5 positive-input red.

These are boundary inputs, not results of a command run in this task. Task 5
must rerun the focused, ordinary, guards, and static checks and compare its
outcomes with this record and the expected-red identities below.

## Expected-red identity sources

The expected-red files are imported as identity-only comparison inputs from
`origin/develop` commit
`f4f3bd10cdf25a8e9a69f598be528b1276fc5fd6`. Result prose, manifests, and
session paths are intentionally not imported.

| Set | Source ref | Blob SHA-1 | Identities | Local SHA-256 |
|---|---|---|---:|---|
| Ordinary | `origin/develop:docs/architecture/validation/2026-08-27-actworks-maven-ordinary-red-set.txt` | `a3b37cb8cd48991bd8e37bede912c524f00c00d8` | 70 | `522f09b010f7649607257f4fbd9ffcf4c0af7f1e4ac3b8dba603a472025cdf7c` |
| Guards | `origin/develop:docs/architecture/validation/2026-08-27-actworks-maven-guards-red-set.txt` | `3c85f33e3fdd9a0a0b7089b39f1f9da90ca7f9dd` | 19 | `3a814490a2d1fff240cadfe39753d24fbc914d791da1dc2ad9049db6fe77b467` |

The upstream rollback baseline that describes the source runs is
`origin/develop:docs/architecture/validation/2026-08-27-actworks-maven-rollback-baseline.md`
(blob `8be752763e6271fc5b38e3a9699970d219a02d69`). Its reported ordinary and
guards counts are historical upstream evidence only; the direct-Maven reruns
belong to Task 5.

## Identity inspection

The imported files were compared byte-for-byte with their `origin/develop`
source refs and each identity was checked against the current test source by
class and method name. Task 1 self-review records the exact commands and
outcomes in `task-1-report.md`.

## Focused verification

Result: not run in Task 1. Reserved for Task 5.

## Ordinary suite

Result: not run in Task 1. Reserved for Task 5; compare against the 18,197
identity snapshot and the 70 expected-red identities.

## Guards suite

Result: not run in Task 1. Reserved for Task 5; compare against the 520/521
snapshot and the 19 expected-red identities, retaining only the known Trace V5
positive-input red unless later evidence proves otherwise.

## Static proof and hygiene

Result: not run in Task 1. Reserved for Task 5.

## Independent review

Result: not run in Task 1. Reserved for Task 5.

## Integration

Result: not run in Task 1. Reserved for Task 6 after fast-forwarding local
`next`.

## Push

Result: not run in Task 1. Reserved for Task 6 after final origin refresh and
post-fast-forward verification.
