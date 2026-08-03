# Task 1 Report: Trace v5 Baseline and Inventory

## Delivered

- Added `tools/traces/validate_trace_v5.py`, a read-only Python standard-library
  validator for v5 fixture roots.
- Added executable contract tests at `tools/testing/test_validate_trace_v5.py`.
- Added the immutable baseline and migration inventory at
  `docs/architecture/validation/trace/2026-08-03-trace-v5-baseline.md`.
- Included the already-reviewed consolidation design and implementation plan in
  the task commit.

## Validator contract

The validator recursively enumerates every plain or gzip-compressed
`metadata.json` and `run_manifest.json`, then validates plain or gzip-compressed
physics and timing payloads associated with metadata. It reports every failure
as an exact filesystem path and exits nonzero without writing to the selected
root.

It requires the v5 native envelope, rejects all removed version fields and
alternate `*_retro` sidecars, enforces 42-column ordinary rows and fixed
S1/S2/S3K special-stage widths, validates the unversioned module-plus-direct
timing grammar, and requires the current run-manifest arrays including
`dynamic_art_gap_transitions`.

## Baseline evidence

The baseline report captures commit `2c7fb8fbbb65790e596a78f938fe38a7b0a346d8`,
JDK 21.0.11, Mono 6.12.0, the repository-local BizHawk 2.11 installation, all
three verified ROM hashes, the 217 metadata / seven manifest inventory, and
the eight preserved S1 credits fixtures.

- Full Java, single fork: 13,935 tests, 25 failures, 225 errors, 35 skipped.
  The known unavailable-LWJGL/GLFW `GlfwKeyNameResolver$Holder` cascade is
  separated as an environment-wide baseline failure.
- `*TraceReplay`, single fork with verified ROM paths: 35 tests, 0 failures,
  11 errors, 0 skipped. All errors are pre-existing S3K timing/replay cases.
- Legacy fleet validator: expected red, 1,257 diagnostics across 233 paths.
  The baseline report contains the reason-count migration checklist and exact
  reproduction command.

## Verification

```text
python3 -m py_compile tools/traces/validate_trace_v5.py tools/testing/test_validate_trace_v5.py
python3 -m unittest tools.testing.test_validate_trace_v5
python3 tools/traces/validate_trace_v5.py src/test/resources/traces
```

The six validator tests passed. The last command exited 1 as required for the
legacy fixture fleet; it did not change fixture bytes.

## Self-review

- Read-only behavior is exercised by a before/after byte snapshot test.
- Every rejection identifies the actual file path, including sidecars,
  compressed input, timing-line failures, and manifests.
- Validation does not encode zone, route, trace frame, or game-specific
  compatibility exceptions; game only selects the documented special-stage
  width.
- No runtime source or canonical fixture payload is changed. All eight S1
  credits directories remain present.
- A Maven baseline generated the unrelated
  `docs/status/rewind-round-trip-gaps.md`; it is intentionally left unstaged as
  test-generated output and is not part of this task.
