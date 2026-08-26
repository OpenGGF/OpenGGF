# Worktree Lifecycle Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give OpenGGF one auditable, fail-closed stand-down path that retires proved-safe worktrees while preserving every uncertain or unique state.

**Architecture:** A new Python standard-library CLI treats Git porcelain as authoritative for all registered worktrees and optionally scans allowlisted roots for audit-only physical orphans. `audit` is always read-only; `retire --apply` revalidates a single registered target immediately before Git-mediated removal and deletes a local branch only after a fresh merge proof. Workflow documentation and mirrored trace skills make lead-owned retirement mandatory at stand-down.

**Tech Stack:** Python 3 standard library, Git CLI porcelain, Python `unittest`, Maven/JUnit 5 structural guards, Markdown skills and workflow documentation.

**Spec:** `docs/architecture/designs/2026-08-26-session-storage-and-worktree-lifecycle-safety.md`

## Global Constraints

- The main workspace branch is the default integration base; never switch the main workspace.
- `audit` always includes every registered worktree; `--root` adds only allowlisted orphan scanning.
- All mutation is dry-run unless `retire --apply` names one registered target.
- Never raw-delete a registered worktree, dirty tree, unknown directory, foreign-pointer orphan, or test-session lock namespace.
- A clean unmerged branch is retained after its worktree is removed.
- A local branch is deleted only through `git branch -d` after a fresh ancestor proof.
- Detached worktree retirement requires the exact recorded HEAD through `--confirm-detached-head`.
- `.agents/skills/trace-green-fleet/SKILL.md` and `.claude/skills/trace-green-fleet/SKILL.md` must remain byte-identical.
- `AGENTS.md` and `CLAUDE.md` must remain byte-identical.
- Run `tools/testing/install-hooks.sh` before the first commit, and supply all
  seven required policy trailers on every commit command below.

---

### Task 1: Porcelain parser and read-only registered-worktree audit

**Files:**
- Create: `tools/worktree-lifecycle`
- Create: `tools/test_worktree_lifecycle.py`

**Interfaces:**
- Produces: `WorktreeRecord`, `RepoContext`, `AuditRecord` dataclasses.
- Produces: `parse_worktree_porcelain(data: bytes) -> list[WorktreeRecord]`.
- Produces: `audit_registered(context: RepoContext, base: str | None) -> list[AuditRecord]`.

- [ ] **Step 1: Write failing parser and audit tests**

Load the extensionless tool using `SourceFileLoader`, create disposable real Git repositories, and cover main, branch, detached, locked, and prunable porcelain records. Assert the record schema:

```python
@dataclasses.dataclass(frozen=True)
class AuditRecord:
    path: str
    kind: str
    registered: bool
    state: str
    branch: str | None
    head: str | None
    base: str | None
    base_head: str | None
    dirty: bool | None
    merged_into_base: bool | None
    apparent_bytes: int | None
    proposed_action: str
    blockers: list[str]
```

Add a dry-run test asserting repository paths, branches, and worktree count are unchanged.

- [ ] **Step 2: Run tests and confirm they fail**

```bash
python3 tools/test_worktree_lifecycle.py
```

Expected: failure because the CLI and parser do not exist.

- [ ] **Step 3: Implement parser, repository context, and classification**

Use:

```python
git worktree list --porcelain -z
git -C <path> status --porcelain=v2 --untracked-files=all
git merge-base --is-ancestor <branch> <base-head>
```

Resolve the main workspace from the first non-linked/main porcelain record and use its checked-out branch/commit as the default base. If detached or ambiguous, require `--base`. Any status failure is an unreadable blocker; any status output is dirty. Human output and `--json` must share schema version `1`.

Set the tracked CLI executable and guard that contract:

```bash
chmod +x tools/worktree-lifecycle
test -x tools/worktree-lifecycle
```

Add a unit assertion that the checked-out tool has an executable mode bit.

- [ ] **Step 4: Run parser/audit tests**

Run Step 2 plus:

```bash
python3 -m py_compile tools/worktree-lifecycle tools/test_worktree_lifecycle.py
```

Expected: all pass.

- [ ] **Step 5: Commit the audit unit**

```bash
git add tools/worktree-lifecycle tools/test_worktree_lifecycle.py
git commit -m "feat: audit OpenGGF worktree lifecycle" \
  -m "Changelog: n/a: agent workflow tooling only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: n/a" -m "Configuration-Docs: n/a" -m "Skills: n/a"
```

### Task 2: Allowlisted physical-orphan discovery

**Files:**
- Modify: `tools/worktree-lifecycle`
- Modify: `tools/test_worktree_lifecycle.py`

**Interfaces:**
- Consumes: Task 1 registered path set.
- Produces: `audit_orphan_root(root: Path, registered: set[Path]) -> list[AuditRecord]`.

- [ ] **Step 1: Add failing orphan-boundary tests**

Cover `test_registered_path_is_never_an_orphan`,
`test_broken_windows_git_pointer_is_audit_only`,
`test_unknown_directory_is_blocked`, `test_symlink_root_is_rejected`, and
`test_home_project_and_filesystem_roots_are_rejected`.

Use a literal `.git` file containing
`gitdir: C:/foreign/project/.git/worktrees/old` and assert
`proposed_action == "AUDIT_ONLY_FOREIGN_POINTER"`.

- [ ] **Step 2: Run tests and observe failures**

Run `python3 tools/test_worktree_lifecycle.py`. Expected: new orphan cases fail.

- [ ] **Step 3: Implement safe orphan scanning**

Canonicalise each explicit `--root`, reject broad/symlinked roots, inspect only direct children, and compare canonical paths with the complete registered set. Parse `.git` pointer text as data only; never follow a foreign pointer. Unknown entries remain blocked. Do not add an orphan deletion command.

- [ ] **Step 4: Run lifecycle tests**

Run Task 1 Step 4. Expected: all pass.

- [ ] **Step 5: Commit orphan auditing**

```bash
git add tools/worktree-lifecycle tools/test_worktree_lifecycle.py
git commit -m "feat: inventory orphaned worktree directories" \
  -m "Changelog: n/a: agent workflow tooling only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: n/a" -m "Configuration-Docs: n/a" -m "Skills: n/a"
```

### Task 3: Fail-closed registered worktree retirement

**Files:**
- Modify: `tools/worktree-lifecycle`
- Modify: `tools/test_worktree_lifecycle.py`

**Interfaces:**
- Consumes: Task 1 context/classification and exact target path.
- Produces: `retire(context, target, base, apply, confirm_detached_head) -> RetireResult`.
- Produces: `detect_test_session_leases(worktree: WorktreeRecord) -> list[LeaseNamespaceStatus]`.

The operation schemas are distinct from audit records:

```python
@dataclasses.dataclass(frozen=True)
class LeaseNamespaceStatus:
    namespace_path: str
    metadata_path: str | None
    run_id: str | None
    state: str  # ACTIVE, RECOVERED, UNREADABLE, INITIALIZING, STALE
    blocker: str | None

@dataclasses.dataclass(frozen=True)
class RetireResult:
    target: str
    branch: str | None
    head: str
    base: str
    base_head: str
    merged_into_base: bool
    apparent_bytes: int | None
    removed_worktree: bool
    deleted_branch: bool
    branch_retained: bool
    partial_success: bool
    lease_namespaces: list[LeaseNamespaceStatus]
    git_errors: list[str]
    blockers: list[str]
```

- [ ] **Step 1: Add failing retirement matrix tests**

Use real Git worktrees to cover clean merged removal with branch deletion,
clean unmerged removal with branch retention, dirty/main/locked blockers,
exact detached-HEAD confirmation, live or unreadable session leases, recovered
lease reporting without deletion, and deterministic state change immediately
before apply.

Inject a callback immediately before final revalidation so the race test can dirty or advance the target deterministically.

Lease discovery must inspect every direct child matching
`openggf-test-session.lock*` beneath the linked worktree Git directory returned
by `git -C <worktree> rev-parse --git-dir`. It parses `owner.json` and
`initializing.json`, validates their `worktree` and `lease_path`, and applies
the coordinator's `pid` plus `process_start_epoch_ms` identity contract.
Active, initializing-live, and unreadable namespaces block. Recovered and
stale namespaces are reported but never removed. Do not search arbitrary
external lock roots in this delivery; report that only the canonical Git-dir
lock root is covered.

- [ ] **Step 2: Run lifecycle tests and prove mutation is not implemented**

Run `python3 tools/test_worktree_lifecycle.py`. Expected: retirement cases fail and all fixture worktrees remain.

- [ ] **Step 3: Implement retirement revalidation**

Apply mode performs this exact sequence:

```text
reload porcelain -> canonical target/allowlist checks -> reject main/locked
-> status check -> lease owner/liveness check -> resolve and snapshot base
-> re-read porcelain/path/branch/HEAD -> second status check
-> revalidate base -> git worktree remove <target>
-> fresh merge-base --is-ancestor <recorded-head> <requested-base-head>
-> optional git branch -d from the main workspace
-> git worktree prune
```

Absence of a test-session namespace is not proof that no arbitrary process is present; output states that limitation. An unreadable or live coordinator lease blocks. Recovered namespaces are reported and untouched. If branch deletion fails after successful worktree removal, retain the branch and return a partial-success record rather than attempting force deletion.

Branch deletion requires both the explicit ancestor proof against the
still-identical requested base HEAD and a successful `git branch -d` executed
with `git -C <main-workspace>`. If Git's own deletion proof is stricter, retain
the branch and include its exact stderr in the partial-success result.

- [ ] **Step 4: Run the complete lifecycle test suite**

Run Task 1 Step 4. Expected: all pass.

- [ ] **Step 5: Commit safe retirement**

```bash
git add tools/worktree-lifecycle tools/test_worktree_lifecycle.py
git commit -m "fix: retire proved-safe worktrees" \
  -m "Changelog: n/a: agent workflow tooling only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: n/a" -m "Configuration-Docs: n/a" -m "Skills: n/a"
```

### Task 4: Resolve trace-fleet lifecycle contradictions

**Files:**
- Modify together: `.agents/skills/trace-green-fleet/SKILL.md`, `.claude/skills/trace-green-fleet/SKILL.md`
- Modify: `docs/agent-workflow/trace-green-fleet-decisions.md`
- Modify: `docs/agent-workflow/briefing-trace-rounds.md:1189-1283`
- Modify: `docs/agent-workflow/README.md`
- Modify: `tools/testing/README.md`
- Modify together: `AGENTS.md`, `CLAUDE.md`
- Modify: `src/test/java/com/openggf/tests/TestBuildToolingGuard.java`
- Modify: `tools/testing/test_compare_trace_v5_candidates.py`

**Interfaces:**
- Consumes: Tasks 1-3 CLI contract.
- Produces: one unambiguous lead-owned stand-down workflow and mirror guards.

- [ ] **Step 1: Add failing documentation/skill guards**

Extend existing Python and Java guards to require mirrored skills and these concepts:

```python
required = (
    "tools/worktree-lifecycle audit",
    "tools/worktree-lifecycle retire",
    "branch is the durable",
    "dirty",
)
```

Reject the phrases “persistent worktree” and “worktrees remain for review” in the trace-green-fleet mirrors. Keep a targeted exception only when historical docs quote the old incident wording.

- [ ] **Step 2: Run focused guards and confirm failure**

```bash
python3 tools/testing/test_compare_trace_v5_candidates.py
tools/testing/test-session.sh -- mvn -Dmse=off \
  -Dtest=TestBuildToolingGuard test -B
```

Expected: failures identify the old lifecycle contract.

- [ ] **Step 3: Update mirrored skills and workflow docs**

Replace “persistent for review” with:

```text
At stand-down, the worker reports path, branch, HEAD, dirty state, and promoted
artifact paths. The lead audits the worktree. A clean ended lane is retired;
a dirty, unreadable, locked, or live lane is retained with its blocker. Review
continues from the retained branch, not by hoarding a clean checkout.
```

State that normal delivery worktrees remain until integration, verification, push, and cleanup complete. Document that `retire --apply` needs ordinary Codex approval because Git metadata is protected. Keep both skill files and AGENTS/CLAUDE byte-identical.

Document the audit/retirement commands and their dry-run/apply boundary in both
workflow READMEs, as required by the design.

- [ ] **Step 4: Run focused guards and mirror comparisons**

```bash
git diff --no-index .agents/skills/trace-green-fleet/SKILL.md \
  .claude/skills/trace-green-fleet/SKILL.md
cmp -s AGENTS.md CLAUDE.md
python3 tools/testing/test_compare_trace_v5_candidates.py
tools/testing/test-session.sh -- mvn -Dmse=off \
  -Dtest=TestBuildToolingGuard test -B
```

Expected: both comparisons and both test commands pass.

- [ ] **Step 5: Commit workflow enforcement**

```bash
git add .agents/skills/trace-green-fleet/SKILL.md \
  .claude/skills/trace-green-fleet/SKILL.md \
  docs/agent-workflow/trace-green-fleet-decisions.md \
  docs/agent-workflow/briefing-trace-rounds.md \
  docs/agent-workflow/README.md tools/testing/README.md AGENTS.md CLAUDE.md \
  src/test/java/com/openggf/tests/TestBuildToolingGuard.java \
  tools/testing/test_compare_trace_v5_candidates.py
git commit -m "docs: enforce worktree stand-down lifecycle" \
  -m "Changelog: n/a: agent workflow documentation only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: updated" -m "Configuration-Docs: n/a" -m "Skills: updated"
```

### Task 5: Worktree-lifecycle verification checkpoint

**Files:**
- Verify only; fix failures in the task that owns the affected surface.

**Interfaces:**
- Produces: independently verified worktree lifecycle delivery.

- [ ] **Step 1: Run all focused checks**

```bash
python3 -m py_compile tools/worktree-lifecycle tools/test_worktree_lifecycle.py
python3 tools/test_worktree_lifecycle.py
git diff --no-index .agents/skills/trace-green-fleet/SKILL.md \
  .claude/skills/trace-green-fleet/SKILL.md
cmp -s AGENTS.md CLAUDE.md
python3 tools/testing/test_compare_trace_v5_candidates.py
git diff --check
```

Expected: all exit zero.

- [ ] **Step 2: Audit the real repository without mutation**

```bash
tools/worktree-lifecycle audit \
  --root "$OPENGGF_MAIN_WORKSPACE/.worktrees" \
  --root "$OPENGGF_WORKTREE_ROOT" \
  --json
```

Set the two task-specific variables to the canonical main-workspace and external
worktree roots before running the command. Expected: valid schema, all
registered worktrees included, foreign-pointer directories audit-only, and no
filesystem or Git mutation.

- [ ] **Step 3: Run ordinary and guard suites**

```bash
tools/testing/test-session.sh -- mvn test
tools/testing/test-session.sh -- mvn -Dmse=off -Pguards test -B
```

Expected: certifying start/end markers; record run IDs, manifests, logs, and exact baseline failures.

- [ ] **Step 4: Commit any verified corrections**

Stage only corrected files, rerun their focused checks, and commit with all seven
accurate policy trailers. Do not mutate any real worktree during this
verification task.
