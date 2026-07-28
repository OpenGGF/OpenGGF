# Task 2 report — trace model routing benchmark

## Outcome

Completed the operator documentation and frozen benchmark protocol. The
manifest contains one historically replayed, ROM-backed case for each game and
one each of the `narrow`, `shared`, and `deep` classifications. No candidate was
accepted from log text alone.

## Historical-parent evidence

| Case | Fix commit / pinned parent | Parent replay result | Fixture verification |
| --- | --- | --- | --- |
| `s1-lz2-monitor-break-narrow` | `c74683818de3b62f36be6e36ff9cfa0161d7e3cc` / `5e3ba3ea3d6a609f98cd7662a0767bce21fef06c` | 1,290 errors, first `f6418 obj_s44_slot` | all three fixture blobs SHA-256 verified |
| `s2-cnz-shared-sidekick-control` | `12c3a4d46e9537127ddc5ad342d755c0c3fa6ab5` / `7fe4b63fa7f411515fe20c8dd5d1b5f9321d2b7f` | 594 errors, first `f202 tails_x` | all four fixture blobs SHA-256 verified |
| `s3k-cnz-miniboss-arena-deep` | `63031dc34dd16bb93e76cd0bce70639b2e85a0c0` / `cb74114a6c218d01454812666ef996529133608d` | 3,675 errors, first `f14157 tails_x` | all four fixture blobs SHA-256 verified |

All ROM SHA-1 values matched the manifest: S1
`69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`, S2
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, and S3K
`CFBF98C36C776677290A872547AC47C53D2761D6`.

The S3K parent initially exhausted the default heap while serialising its large
divergence report. Re-running the historical command with its recorded
`-Dsurefire.argLine=-Xshare:off -Xmx6g` completed and reproduced the logged
frontier. The manifest therefore pins that memory setting in the exact target
command.

## Artifacts

- Updated the multi-agent runbook with exact Terra/Sol routes, direct Sol
  classification, deterministic escalation, verification escalation, sequential
  worktree ownership, and safe benchmark retention/removal.
- Recorded the 2026-07-28 routing decision: Terra-first, Sol for shared/deep and
  escalation, Luna future-only, and accepted-result/token measures primary.
- Added the benchmark manifest, Draft 2020-12 manifest/result schemas, and a
  schema-valid `terra-sol` result template.

## Validation

All commands below exited zero:

```bash
jq empty docs/architecture/validation/trace/trace-model-routing-benchmark.json
jq empty docs/architecture/validation/trace/trace-model-routing-benchmark.schema.json
jq empty docs/architecture/validation/trace/trace-model-routing-result.schema.json
check-jsonschema --schemafile docs/architecture/validation/trace/trace-model-routing-benchmark.schema.json docs/architecture/validation/trace/trace-model-routing-benchmark.json
check-jsonschema --check-metaschema docs/architecture/validation/trace/trace-model-routing-benchmark.schema.json docs/architecture/validation/trace/trace-model-routing-result.schema.json
check-jsonschema --schemafile docs/architecture/validation/trace/trace-model-routing-result.schema.json docs/architecture/validation/trace/trace-model-routing-result.template.json
jq -e '([.cases[].id] | length == (unique | length)) and ([.cases[].game] | unique | sort == ["s1","s2","s3k"]) and (all(.cases[]; .historicalFailureConfirmed == true))' docs/architecture/validation/trace/trace-model-routing-benchmark.json
```

The validator was installed only in temporary `/tmp/trace-model-routing-validator`
because it was not available on PATH. The pinned commit-existence and historical
fixture-object SHA-256 loops also exited zero.
