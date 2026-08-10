# Develop Into Next Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a `next` tree that contains committed `develop` and preserves every reviewed remediation feature currently at `next` commit `f53942a33`.

**Architecture:** Build the result on top of `develop`, then replay the ten `next` commits in their original order. Resolve conflicts within the feature commit that owns the behavior, using the disassembly and existing focused tests as authority; never resolve the integration with a blanket `ours` or `theirs` choice. After all replayed commits pass, record the original `next` tip as an ancestor without changing the already-reviewed tree, fast-forward `next`, and compare the merged suite against clean baselines.

**Tech Stack:** Git worktrees and cherry-pick, Java 21, Maven Surefire/JUnit 5, canonical S1/S2/S3K ROM fixtures, repository policy hooks.

> **Execution note (2026-08-10):** The isolated implementation kept `next` as the
> first parent and merged `develop` directly. This retains the original feature
> commits without rewriting them and made conflict decisions inspectable in one
> integration commit. The first reviewed merge used `develop` `9f46d1b58`; because
> `develop` advanced concurrently, the final integration also merges the later
> committed tip and repeats the complete regression comparison before updating
> the real `next` worktree.

## Global Constraints

- Preserve the main-workspace `develop` dirt owned by the concurrent trace session.
- Use committed `develop` `9f46d1b58` only; do not absorb unstaged or staged workspace changes.
- Preserve `next`'s AIZ napalm, AIZ2 splash, load-profile boundary, S2 capability contracts, S3K SMPS boundary, LBZ Big Arm, closeout evidence, and no-disassembly tests.
- Preserve `develop`'s bug fixes, incorrect-test-culling corrections, trace-v5/run-chain fixes, and all committed history.
- No executable test may read `docs/*disasm`; ROM-backed tests use the configured canonical ROMs.
- Do not push either branch unless separately requested.

---

### Task 1: Establish Exact Baselines and Replay Order

**Files:**
- Create: `docs/architecture/plans/2026-08-10-develop-into-next-integration.md`
- Generated evidence only: `target/surefire-reports/TEST-*.xml`

**Interfaces:**
- Consumes: committed `develop` `9f46d1b58`, original `next` `f53942a33`
- Produces: exact baseline manifests and the ordered replay list

- [ ] Record `git rev-parse develop next`, `git merge-base develop next`, and clean status of the integration worktree.
- [ ] Remove generated `docs/*disasm` worktree links before portability verification.
- [ ] Run the full JDK 21 three-ROM suite on committed `develop` and normalize every testcase to `class`, `method`, and `PASS|FAILURE|ERROR|SKIPPED`.
- [ ] Retain the previously recorded clean `next` manifest for `f53942a33`, or rerun it if any tree identity check differs.
- [ ] Fix the replay order to `49fd2de6b`, `f7bc8ce4b`, `5178cf3b3`, `9a650ce3b`, `99ad8f6f2`, `c4b438bcf`, `2dad85fc6`, `b7a44d203`, `83310bbf4`, `f53942a33`.

### Task 2: Replay AIZ Runtime Remediations

**Files:**
- Modify only conflict files owned by commits `49fd2de6b` and `f7bc8ce4b`.

**Interfaces:**
- Consumes: current `develop` rewind/lifetime APIs
- Produces: exact-ID napalm rewind and native waterfall deferred-delete behavior

- [ ] Cherry-pick `49fd2de6b`; resolve API conflicts by retaining current `develop` service/lifetime owners and the feature's exact `ObjectRefId` graph.
- [ ] Run `TestAizMinibossNapalmProductionRoute` plus rewind policy/coverage guards.
- [ ] Cherry-pick `f7bc8ce4b`; retain current `ObjectLifetimeOps` semantics and the feature's one-dispatch native delete marker.
- [ ] Run `TestS3kAizEndBossGraphRewind`, `TestSonic3kAIZEvents`, and the AIZ route tests.

### Task 3: Replay Capability and Timing Contracts

**Files:**
- Modify only conflict files owned by commits `5178cf3b3`, `9a650ce3b`, `99ad8f6f2`, and `c4b438bcf`.

**Interfaces:**
- Consumes: current configuration, timing, S2 module, and SMPS APIs
- Produces: reserved load-profile diagnostics and evidence-only capability boundaries without dormant runtime scaffolds

- [ ] Cherry-pick `5178cf3b3`; preserve FAST-to-IMMEDIATE and REALISTIC-to-supplied-profile identity.
- [ ] Run `TestLoadTimeProfileContract` and the S1/S2/S3K load-owner suites.
- [ ] Cherry-pick `9a650ce3b`; keep `hasLevelDebug()` false and preserve the ROM-contract-only boundary.
- [ ] Run `TestSonic2DebugPlacementRomContract` and adjacent module/rewind guards.
- [ ] Cherry-pick `99ad8f6f2`; preserve ordinary CPU-sidekick behavior and no native competition activation.
- [ ] Run `TestSonic2CompetitionBoundary` and adjacent S2 session tests.
- [ ] Cherry-pick `c4b438bcf`; preserve syntax-width-only FF01/02/03 behavior and current legacy audio ownership.
- [ ] Run `TestSonic3kSmpsMetaCommandOperands`, audio architecture guards, and the exact architectural baseline comparison.

### Task 4: Replay LBZ Big Arm and Integration Reconciliation

**Files:**
- Modify only conflict files owned by commits `2dad85fc6`, `b7a44d203`, and `83310bbf4`.

**Interfaces:**
- Consumes: current object lifecycle, touch, rewind, PLC, event, camera, and audio APIs
- Produces: the reviewed FixBugs=0 Big Arm route and reconciled AIZ/LBZ graphs

- [ ] Cherry-pick `2dad85fc6`; resolve each conflict against its reviewed design/plan and the current shared owner API.
- [ ] Compile immediately, then run Big Arm behavior, production-route, graph, rewind, PLC, and legacy fade tests.
- [ ] Cherry-pick `b7a44d203`; reconcile ledger wording to the actual replayed commit hashes and evidence without weakening open trace blockers.
- [ ] Cherry-pick `83310bbf4`; retain only reconciliation hunks still required on the new `develop` base, dropping hunks already superseded by current APIs.
- [ ] Run AIZ route/graph tests together with the complete Big Arm focused selector.

### Task 5: Replay Test Portability and Close the Tree

**Files:**
- Modify only conflict files owned by commit `f53942a33`.

**Interfaces:**
- Consumes: `develop`'s seven-family no-disassembly commit `9f46d1b58`
- Produces: the broader `next` portability set, including FBZ source-data tests and integration lifetime fixes

- [ ] Cherry-pick `f53942a33`; keep already-landed `develop` test rewrites, add `next`-only FBZ ROM readers and integration-specific lifetime/rewind fixes, and reconcile the closeout evidence.
- [ ] Remove all generated `docs/*disasm` links.
- [ ] Run the complete no-disassembly focused selector and `TestBuildToolingGuard#executableTestsMustNotReadLocalDisassemblyTrees` with zero skips.
- [ ] Run `git diff --check`, source scans, rewind guards, architecture guards, compression guards, and commit-policy checks.

### Task 6: Full Regression Comparison and Next Integration

**Files:**
- Update only evidence rows whose exact counts or commit identities changed during replay.

**Interfaces:**
- Consumes: completed integration tree and baseline manifests
- Produces: local `next` containing both histories, with exact verification evidence

- [ ] Run the full JDK 21 suite with all three ROMs and normalize testcase outcomes.
- [ ] Compare against both baselines: no test passing on committed `develop` may become red, and no `next` feature test may regress; classify order-sensitive substitutions with isolated reruns.
- [ ] Rerun every conflict-family focused selector after the full suite to exclude singleton-order contamination.
- [ ] Run `.githooks/run-policy ci-push 9f46d1b58 HEAD feature/ai-next-develop-integration` and `git diff --check`.
- [ ] Record original `next` as integrated only after tree equivalence review, then fast-forward local `next` to the reviewed integration tip.
- [ ] Run the exact final range policy and focused smoke suite on `next`.
- [ ] Remove the clean integration worktree and delete its fully merged local branch; do not push.

## Self-Review

- Spec coverage: `develop` is the ancestry base; all ten `next` commits are replayed and tested; both histories are retained.
- Placeholder scan: no deferred implementation or unspecified test step remains.
- Type consistency: conflict resolution always targets the current `develop` API while retaining the feature behavior and named test owner.
