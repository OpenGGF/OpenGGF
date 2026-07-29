# Native Load-Time Measurement Stream Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans and TDD.

**Goal:** Capture native FIFO service opportunities, generate deterministic measured
manifests, and publish a validated optional estimator without changing replay authority.

## Tasks

- [ ] Resolve and cite exact S&K-half PCs for every callback label enumerated in the
  design; add locked-ROM instruction-byte golden tests before registering them.
- [ ] Extend `HardwareTimingEventEngine` with one run-wide callback ledger and optional
  diagnostic writer. Execute callbacks—not frame-end samples—own immutable submission
  descriptors/features, activation, eligible service, module coordination, retirement,
  clear/reset, raw frame, and monotonic sequence. Leave `hardware_timing.jsonl`
  byte-identical.
- [ ] Add C# unit tests for direct and module lifecycles, waiting exclusion, callbacks,
  same-frame ordering, reset, hashes, and diagnostic writer absence.
- [ ] Thread the optional writer through S3K headless capture runners. Keep the ledger
  run-wide across segment writers and publish `load_time_measurements.jsonl` to
  `target/load-time-measurements/` under the design's input-set no-replace hash.
- [ ] Add Java `HardwareWorkFeatures` to production submissions and rewind snapshots;
  extract matching standard-Kos features from ROM-backed bytes in S3K queue owners.
- [ ] Implement a strict Java diagnostic parser and deterministic manifest generator with
  lower-median aggregation, fixture dictionary, validation report, and byte stability.
- [ ] Implement the finite feature/intercept/divisor estimator family, exact candidate
  ordering, checked arithmetic, whole-fingerprint/family folds, and validation gates;
  publish coefficients only when all gates pass.
- [ ] Add shared synthetic C#/Java scanner vectors for terminators, descriptor refill,
  short/long/overlap copies, padding/alignment, final modules, malformed input, and
  overflow.
- [ ] Add architecture guards excluding `com.openggf.tools.timing` and diagnostic filenames
  from trace replay/gameplay authority; trace fixture loaders explicitly reject the file.
- [ ] Replay the five enumerated S3K BK2 files with the verified ROM, publish the nonempty
  S3K manifest, validation Markdown, and publication TSV at the exact design paths, then
  regenerate into a fresh target directory and compare bytes plus unique coverage with
  the 125 S3K and 436 cross-game provisional lower bounds.
- [ ] Run headless C# tests, focused Java timing/S3K tests, full Java tests, and the
  repository integration workflow.
