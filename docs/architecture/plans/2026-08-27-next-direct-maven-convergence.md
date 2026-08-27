# Next Direct-Maven Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconcile the completed direct-Maven rollback onto verified `next` while preserving newer `next` safeguards and excluding superseded lifecycle, FBZ, gameplay, and host-cleanup work.

**Architecture:** Use develop commits `b99078954`, `3484910d6`, `3746093d9`, `572a5cc36`, and `0d57f460e` as reference, not raw replacements. Test-drive a target-local output contract, remove the retired protocol, then reconcile workflows and active documentation around the preserved `next` build graph.

**Tech Stack:** Java 21, Maven 3.9, JUnit 5, Surefire, GitHub Actions YAML, POSIX/PowerShell launchers.

**Spec:** `docs/architecture/designs/2026-08-27-next-direct-maven-convergence.md`

## Global Constraints

- Base all work on `33a799c014906bd75e99da329abc465ecf466487` in `.worktrees/next-direct-maven-convergence`.
- Do not invoke `agent-scratch`, run FBZ traces, mutate host configuration, or delete external storage.
- Do not replay `88951aeec..92775b14b`, merge commits, or parked FBZ commit `2659e703c4`.
- Preserve unrelated and `next`-only behavior; use `apply_patch` for edits.
- Use red-green-refactor and direct Maven after the cutover.
- Keep `AGENTS.md`/`CLAUDE.md` and changed skill mirrors identical.
- Use hooks and policy trailers; never use `--no-verify`.

---

### Task 1: Establish the immutable baseline and planning record

**Files:**
- Create: `docs/architecture/designs/2026-08-27-next-direct-maven-convergence.md`
- Create: `docs/architecture/plans/2026-08-27-next-direct-maven-convergence.md`
- Create: `docs/architecture/validation/2026-08-27-next-direct-maven-convergence.md`
- Import/adapt: `docs/architecture/validation/2026-08-27-actworks-maven-ordinary-red-set.txt`
- Import/adapt: `docs/architecture/validation/2026-08-27-actworks-maven-guards-red-set.txt`

**Interfaces:**
- Consumes: verified snapshot evidence and upstream rollback expected-red sets.
- Produces: the comparison ledger used by Tasks 5 and 6.

- [ ] **Step 1: Verify refs and status**

```bash
git rev-parse HEAD next origin/next origin/develop
git status --short --branch
git merge-base --is-ancestor 33a799c014906bd75e99da329abc465ecf466487 HEAD
```

Expected: the new branch starts at `33a799c01`; only planning artifacts differ.

- [ ] **Step 2: Create the validation ledger**

Record immutable SHAs and snapshot evidence: 18,197 ordinary identities with no
new merge regression, Mod API 27/27, and guards 520/521 with only the known
Trace V5 positive-input red. Add empty result headings for focused, ordinary,
guards, static proof, review, integration, and push; do not claim future runs.

- [ ] **Step 3: Import and inspect expected-red identities**

Read the two files from `origin/develop` with `git show`, recreate them using
`apply_patch`, and verify every listed identity exists in the corresponding
snapshot/upstream evidence. Do not import result prose.

- [ ] **Step 4: Self-review and commit**

```bash
rg -n 'T[B]D|T[O]DO|implement lat[e]r|fill i[n]' docs/architecture/designs/2026-08-27-next-direct-maven-convergence.md docs/architecture/plans/2026-08-27-next-direct-maven-convergence.md
git diff --check
git add docs/architecture/designs/2026-08-27-next-direct-maven-convergence.md docs/architecture/plans/2026-08-27-next-direct-maven-convergence.md docs/architecture/validation/2026-08-27-next-direct-maven-convergence.md docs/architecture/validation/2026-08-27-actworks-maven-ordinary-red-set.txt docs/architecture/validation/2026-08-27-actworks-maven-guards-red-set.txt
git commit -m "docs: plan next direct Maven convergence"
```

---

### Task 2: Define target-local output test-first

**Files:**
- Modify: `src/test/java/com/openggf/tests/TestBuildToolingGuard.java`
- Modify: `src/test/java/com/openggf/tests/TestSessionOutputPaths.java`
- Modify: `src/test/java/com/openggf/tests/TestSessionOutputPathsTest.java`

**Interfaces:**
- Consumes: current POM/workflows and Maven build-directory properties.
- Produces: guards requiring target-local output and rejecting retired tooling.

- [ ] **Step 1: Replace session-root assertions**

Require these exact POM values and per-fork LWJGL isolation:

```java
assertEquals("${project.build.directory}/test-tmp", property(pom, "openggf.test.tmpdir"));
assertEquals("${project.build.directory}/surefire-reports", property(pom, "openggf.surefire.reports"));
assertEquals("${project.build.directory}/trace-reports", property(pom, "openggf.trace.reports"));
assertFalse(pom.contains("<openggf.build.directory>"));
assertTrue(pom.contains("lwjgl-${surefire.forkNumber}"));
```

Make output-path tests resolve beneath a supplied build directory and reject
an escaping path. Keep the existing class names to avoid selector churn.

- [ ] **Step 2: Add active-source rejection assertions**

Reject `tools/testing/test-session`, `TestSessionCoordinator`,
`OPENGGF_TEST_RUN_`, `agent-scratch`, and `frozen-next-session` in active code,
workflows, and guidance. Exclude historical changelog/frontier records.

- [ ] **Step 3: Verify RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestSessionOutputPathsTest test -B
```

Expected: failures identify wrapper/coordinator/session output. If `validate`
rejects raw Maven before JUnit, record that policy failure and use the smallest
test-only bootstrap from upstream solely to reach the intended JUnit RED.

- [ ] **Step 4: Commit RED tests**

```bash
git add src/test/java/com/openggf/tests/TestBuildToolingGuard.java src/test/java/com/openggf/tests/TestSessionOutputPaths.java src/test/java/com/openggf/tests/TestSessionOutputPathsTest.java
git commit -m "test: require worktree-local Maven output"
```

---

### Task 3: Remove the retired protocol and make the contract green

**Files:**
- Modify: `pom.xml`, `.mvn/jvm.config`, `dev.sh`, `dev.cmd`, `run.sh`, `run.cmd`
- Modify: `src/main/java/com/openggf/tools/audio/parity/S1AudioParityTool.java`, `src/main/java/com/openggf/tools/audio/timeline/S1GameplayAudioTimelineTool.java`
- Modify: `src/main/java/com/openggf/tools/TraceCaptureTool.java`, `src/main/java/com/openggf/tools/TraceTriageTool.java`, `src/main/java/com/openggf/configuration/SonicConfigurationService.java`
- Modify: `tools/audio/run_complete_audio_parity.sh`, `tools/audio/run_s1_audio_parity.sh`
- Delete: `tools/agent-scratch`, `tools/test_agent_scratch.py`
- Delete: coordinator/self-test/process-harness/session-guard sources and fixtures under `tools/testing/`
- Delete: POSIX/PowerShell `test-session` and process-harness launchers
- Delete: `frozen-next-session-*` adapters and their test
- Preserve: hook installers, Surefire inventory/comparison utilities, and trace fixture validators

**Interfaces:**
- Consumes: Task 2 RED contract.
- Produces: direct Maven with every generated path derived from `${project.build.directory}`.
- Removes obsolete session-output environment reads from active `src/main` and
  tooling code while preserving target-derived diagnostic/report inputs.

- [ ] **Step 1: Remove coordinator-only Maven executions**

Delete executions that compile/run the coordinator, reject raw Maven, or
validate wrapper leases/manifests. Preserve JDK enforcement, Surefire,
packaging, guards, ROM inputs, Mod API, Net isolation, and trace profiles.
Update the listed audio parity/timeline tools and trace/report tooling so they
do not consume `openggf.session.*`, `openggf.build.directory`, managed-scratch
environment variables, or wrapper-exported output roots.

- [ ] **Step 2: Collapse output properties**

```xml
<openggf.test.tmpdir>${project.build.directory}/test-tmp</openggf.test.tmpdir>
<openggf.surefire.reports>${project.build.directory}/surefire-reports</openggf.surefire.reports>
<openggf.trace.reports>${project.build.directory}/trace-reports</openggf.trace.reports>
<openggf.test.diagnostics>${project.build.directory}/diagnostics</openggf.test.diagnostics>
<openggf.artifact.root>${project.build.directory}</openggf.artifact.root>
<openggf.distribution.root>${project.build.directory}</openggf.distribution.root>
```

Use Maven's default `target/`; retain `java.io.tmpdir` below `test-tmp` and
`lwjgl-${surefire.forkNumber}`.

- [ ] **Step 3: Delete only classified retired files**

Use `apply_patch`. Inspect any file differing from upstream before deletion;
preserve and report content that is not solely session-protocol code.

- [ ] **Step 4: Update launchers and verify GREEN**

```bash
mvn -Dmse=off -Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestSessionOutputPathsTest test -B
git grep -n -E 'TestSessionCoordinator|test-session|agent-scratch|frozen-next-session' -- tools/testing pom.xml dev.sh dev.cmd run.sh run.cmd
git grep -n -E 'openggf\.session\.|openggf\.build\.directory|AGENT_SCRATCH_ROOT|OGGF_SCRATCH_ROOT|OPENGGF_TEST_RUN_|TEST_SESSION_' -- src/main tools/audio tools/testing
```

Expected: focused tests pass and both searches are empty. The second search is
the explicit prohibition against obsolete session-output environment use in
active source/tooling; target-derived `openggf.test.diagnostics` and
`openggf.trace.reports` remain valid only when Maven binds them below `target/`.

- [ ] **Step 5: Commit the build cutover**

```bash
git add pom.xml .mvn dev.sh dev.cmd run.sh run.cmd src/test/java/com/openggf/tests/TestBuildToolingGuard.java src/test/java/com/openggf/tests/TestSessionOutputPaths.java src/test/java/com/openggf/tests/TestSessionOutputPathsTest.java
git add -u tools
git diff --cached --check
git commit -m "build: restore worktree-local direct Maven"
```

Use `Changelog: n/a: release note follows in documentation commit`.

---

### Task 4: Reconcile workflows and active guidance

**Files:**
- Modify: `.github/workflows/ci.yml`, `.github/workflows/release.yml`
- Modify together: `AGENTS.md`, `CLAUDE.md`
- Modify: `README.md`, `ROADMAP.md`, `CHANGELOG.0.6.md`
- Modify: active agent-workflow and guide documents
- Modify mirrored S1 trace-replay and trace-replay-bug-fixing skills
- Delete dedicated lifecycle records only after proving no `next` merge evidence is lost
- Modify: convergence validation ledger

**Interfaces:**
- Consumes: Task 3 target paths.
- Produces: static workflow consumers and one active contributor contract.

- [ ] **Step 1: Test workflow expectations RED**

Require raw `mvn -Dmse=off test -B`, separate guards, native/universal package
profiles, and static `target/` consumers. Run `TestBuildToolingGuard`; expected
failures name remaining wrapper invocations or dynamic outputs.

- [ ] **Step 2: Reconcile workflows**

Replace wrapper commands/outputs with `target/surefire-reports`,
`target/trace-reports`, `target/diagnostics`, and target artifacts. Preserve
all current matrices, ROM arguments, Mod API/Net checks, counts, warnings,
smoke tests, and uploads.

- [ ] **Step 3: Rewrite active guidance**

Use this exact root contract in both agent files:

```markdown
OpenGGF uses Maven directly. Build and test output belongs below the current
worktree's `target/` directory. Do not redirect Maven build/report roots to a
shared or durable session directory. Parallel agents use separate worktrees;
repeated runs in one worktree reuse its target tree.
```

Retain JDK 21, ROM properties, `-Dmse=off`, separate guards, bounded log
inspection, hooks, worktree safety, and gameplay rules. Update active guides,
runbooks, and mirrored skills; preserve historical evidence.

- [ ] **Step 4: Record release/roadmap boundary and verify mirrors**

Add the direct-Maven rollback release note and v0.8 informational Actworks
item. Then run:

```bash
cmp AGENTS.md CLAUDE.md
cmp .agents/skills/s1-trace-replay/SKILL.md .claude/skills/s1-trace-replay/SKILL.md
cmp .agents/skills/trace-replay-bug-fixing/SKILL.md .claude/skills/trace-replay-bug-fixing/SKILL.md
git grep -n -E 'agent-scratch|tools/testing/test-session|TestSessionCoordinator|OPENGGF_TEST_RUN_|frozen-next-session' -- AGENTS.md CLAUDE.md README.md ROADMAP.md .github pom.xml tools src/test docs/guide docs/agent-workflow .agents/skills .claude/skills
```

Expected: only explicitly classified historical references outside active
paths. Make the focused guard green.

- [ ] **Step 5: Commit workflows and documentation**

Stage only the listed workflow, agent, guide, skill, architecture, guard, and
release files. Set `Guide: updated`, `Agent-Docs: updated`, `Skills: updated`,
and the correct Changelog trailer; commit as `docs: adopt direct Maven workflow`.

---

### Task 5: Verify and independently review the branch

**Files:**
- Modify: `docs/architecture/validation/2026-08-27-next-direct-maven-convergence.md`

**Interfaces:**
- Consumes: completed branch and expected-red identities.
- Produces: regression decision and two independent review verdicts.

- [ ] **Step 1: Run JDK/focused verification**

```bash
mvn -v
mvn -Dmse=off -Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestSessionOutputPathsTest test -B
```

Expected: Java 21 and focused green.

- [ ] **Step 2: Run ordinary and guards directly**

```bash
mvn -Dmse=off test -B
mvn -Dmse=off -Pguards test -B
```

Export `target/surefire-reports` with retained inventory utilities. Compare
identities/outcomes with the snapshot ledger and expected-red files. Record
counts, new/resolved reds, errors, and first differing messages. No new or
worsened red is acceptable.

- [ ] **Step 3: Run static proof and hygiene**

```bash
git diff --check 33a799c014906bd75e99da329abc465ecf466487..HEAD
cmp AGENTS.md CLAUDE.md
git status --short --branch
```

- [ ] **Step 4: Dispatch independent reviews**

One reviewer covers POM/output/deletions and preserved `next` behavior. A
second covers workflows/docs/skills/trailers/evidence. Resolve every Critical
or Important finding with a red-green cycle and repeat affected verification.

- [ ] **Step 5: Commit final evidence**

Stage only the validation ledger and commit `test: validate next direct Maven convergence`.

---

### Task 6: Fast-forward, verify, push, and clean

**Files/State:**
- Integration worktree: `.worktrees/next-0.7-roadmap`
- Implementation worktree: `.worktrees/next-direct-maven-convergence`
- Branches: `next`, `feature/ai-next-direct-maven-convergence`

**Interfaces:**
- Consumes: reviewed Task 5 tip.
- Produces: pushed fast-forward `next` and cleaned scaffolding.

- [ ] **Step 1: Refresh and prove fast-forward safety**

```bash
git fetch origin --prune
git -C .worktrees/next-0.7-roadmap status --short --branch
git merge-base --is-ancestor origin/next feature/ai-next-direct-maven-convergence
```

- [ ] **Step 2: Fast-forward local next and repeat focused/ordinary/guards**

```bash
git -C .worktrees/next-0.7-roadmap merge --ff-only feature/ai-next-direct-maven-convergence
```

Run the direct Maven commands from Task 5 on exact local `next`; any changed
outcome blocks push.

- [ ] **Step 3: Push and verify exact remote SHA**

```bash
git -C .worktrees/next-0.7-roadmap push origin next
git fetch origin --prune
git -C .worktrees/next-0.7-roadmap rev-parse HEAD origin/next
```

Expected: identical hashes.

- [ ] **Step 4: Clean completed scaffolding**

Prove the implementation worktree clean and merged, remove it with
`git worktree remove`, delete its local branch, and prune. Preserve parked FBZ
and session-lifecycle refs plus unrelated worktrees.
