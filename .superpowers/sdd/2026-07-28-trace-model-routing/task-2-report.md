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

## Review fix round 1 — 2026-07-28

`RomTestUtils` reads S1 and S2 ROM locations from `sonic1.rom.path` and
`sonic2.rom.path`, not the obsolete shorthand names in the initial manifest.
The manifest, schema, and per-game correlation constraints now use the actual
property names. The following commands were rerun from their pinned historical
parent worktrees and intentionally exited non-zero only because they reproduced
the benchmark failure:

```bash
mvn -q -Dmse=off -Dsurefire.forkCount=1 -DreuseForks=true \
  "-Dsonic1.rom.path=/home/farrell/code/projects/OpenGGF/s1.gen" \
  "-Dtest=com.openggf.tests.trace.s1.TestS1Lz2CompleteRunTraceReplay#replayMatchesTrace" test
# 1,290 errors; first f6418 obj_s44_slot

mvn -q -Dmse=off -Dsurefire.forkCount=1 -DreuseForks=true \
  "-Dsonic2.rom.path=/home/farrell/code/projects/OpenGGF/s2.gen" \
  "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace" test
# 594 errors; first f202 tails_x
```

The result schema now requires the complete Task 1 route object in every
stage, rejects extra keys, distinguishes per-stage `attemptCount` from
top-level `totalAttemptCount`, and uses nullable observed telemetry. The
`terra-sol` template keeps triage Terra-only; its shared classification routes
the subsequent fix and verification directly to Sol without claiming a triage
escalation. The runbook now supplies safe executable worktree, input hash,
owned-file/patch (including untracked additions), external retention, schema,
restoration, clean-status, and ordinary-removal commands.

Post-fix validation reran `jq empty` for all four JSON artifacts,
`check-jsonschema` for manifest, metaschemas, and result template, the
unique-case/all-game/historical-confirmation query, all pinned commit checks,
and all pinned fixture-object SHA-256 checks. Every validation command exited
zero; each `check-jsonschema` invocation reported `ok -- validation done`.

## Review fix round 2 — 2026-07-28

The lifecycle now creates `/tmp` retention before any redirected snapshot,
uses the compliant `feature/ai-trace-model-benchmark-<policy>-<case>` branch
form, and snapshots tracked plus untracked baseline state. Only the worker's
explicit `owned-files` list can contribute to the patch, temporary-index result
tree, or restoration; a listed unchanged path or baseline untracked path fails
capture. Hook-created disassembly symlinks are baseline resources, not owned
files, and are unlinked only after baseline equality is proven so ordinary
worktree removal can proceed without touching their targets. Other foreign
baseline untracked state prevents removal and retains the worktree.

The capture computes `resultTree` with `GIT_INDEX_FILE=<external temporary
index> git read-tree HEAD`, stages only enumerated changed/new paths in that
temporary index, and calls `git write-tree`; it does not read `HEAD^{tree}` or
mutate the user's index. It computes the patch SHA-256, writes both fields into
the target result before schema validation, and retains copies outside both the
worktree and `target/` before cleanup.

Dry-run command sequence used a disposable worktree at parent
`5e3ba3ea3d6a609f98cd7662a0767bce21fef06c`, one explicit new file
`benchmark-owned.txt`, and baseline hook links. It passed result-schema
validation, produced working-tree hash
`aad04a2c24c317fa1a37522f2c57473b8def7e46` and patch SHA-256
`ebaf7f7006f4390a096da5a9459a8989f411d5c17fca821837c3cc1323d31b67`,
matched both final baseline snapshots, retained artifacts under
`/tmp/trace-model-routing-lifecycle-retain2`, and removed the worktree with
ordinary `git worktree remove`.

The manifest schema now requires exactly three ordered policy identities with
their exact route tables: enabled `sol-only`, enabled `terra-sol`, and disabled
`luna-terra-sol` with its reason. Its game-specific conditions also bind ROM
property and SHA-1, command property, and trace package.

## Review fix round 3 — 2026-07-28

Result initialization no longer copies the illustrative template. The lifecycle
derives `policy`, `caseId`, `baseCommit`, `beforeFrontier`, target-relative
patch path, and the initial per-stage routes from the selected manifest pair.
Workers subsequently replace the pending route fields with observations. The
retention directory, branch, and worktree include a UTC/PID run identifier (and
the retention directory additionally uses `mktemp`), so reruns cannot reuse
another run's artifacts. Empty no-commit branches are safely deleted after an
ordinary worktree removal; branches with commits remain for audit.

The capture no longer recopies files that already reside in external retention:
it writes the result from the worktree there once, while `owned-files`,
`owned-tracked`, `owned-new`, and `worker.patch` are already external. A
separate semantic `jq` gate binds the output to the selected manifest policy,
case, base commit, before frontier, and patch destination. It also rejects a
target test class, verifier package, ROM property, ROM SHA-1, or command ROM
property that does not match the case's game binding.

Two clean lifecycle dry-runs initialized, captured, schema-validated, passed
that semantic gate, compared their final tracked/untracked state to their
pre-worker baselines, retained artifacts externally, and used ordinary
`git worktree remove`:

| Policy / case | Derived base and frontier | Initial routes (Discovery, Triage, Fix, Verify) | Result patch path |
| --- | --- | --- | --- |
| `sol-only` / `s1-lz2-monitor-break-narrow` | `5e3ba3ea3d6a609f98cd7662a0767bce21fef06c`; `f6418 obj_s44_slot` | Sol/high, Sol/high, Sol/high, Sol/high | `target/trace-model-routing/sol-only/s1-lz2-monitor-break-narrow.patch` |
| `terra-sol` / `s2-cnz-shared-sidekick-control` | `7fe4b63fa7f411515fe20c8dd5d1b5f9321d2b7f`; `f202 tails_x` | Terra/low, Terra/medium, Sol/high, Sol/high | `target/trace-model-routing/terra-sol/s2-cnz-shared-sidekick-control.patch` |

The S1 dry run produced result tree
`2c4ff98b096178d8f5716163cedb9b4d5e2529dc` and patch SHA-256
`00752ae8ebeb731f2a8ab25263c5a903ace66eb3811fdf975e389c8e7102fd32`.
The S2 run produced result tree
`daa255069b0289f99098cf1113a9dfc45995538f` and patch SHA-256
`9ce7df9e4ee055692e85f9be0cfc71e6fb201e2f22f635c0073d9f351ffdb72c`.
Each patch contained only the explicit `benchmark-owned.txt` fixture. Both
`check-jsonschema` invocations reported `ok -- validation done`.
