# Session Storage Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every OpenGGF test session allocate predictably, refuse unsafe capacity, expose its storage contract, and compact reproducible terminal data without losing evidence.

**Architecture:** `agent-scratch` owns the managed Codex test-session lane, structured reservation, retention, and quarantine. `TestSessionCoordinator` owns storage-tier selection, independent reservation verification, capacity gating, manifest/marker state, and two-path terminal compaction within the unique session directory it created. Project-local and explicit roots use the same compaction contract but not managed retention.

**Tech Stack:** Python 3 standard library, Java 21 standard library, Bash, PowerShell, Maven/JUnit 5 structural guards.

**Spec:** `docs/architecture/designs/2026-08-26-session-storage-and-worktree-lifecycle-safety.md`

## Global Constraints

- Certifying Maven commands must run through `tools/testing/test-session.sh` or `test-session.ps1`.
- Managed sessions live below `<AGENT_SCRATCH_ROOT>/codex/test-sessions`; `<AGENT_SCRATCH_ROOT>/codex` remains the Codex writable root.
- Configured managed allocation failures fail closed and never fall back to `.openggf/test-runs`.
- Default capacity floor is `max(20 GiB, 5% of filesystem capacity)`; `OPENGGF_TEST_MIN_FREE_BYTES` may raise but never lower it.
- Automatic compaction may remove only `tmp` and `build/test-classes/traces` below the verified session directory.
- `.git`, `.agents`, and `.codex` protections remain intact.
- `AGENTS.md` and `CLAUDE.md` must remain byte-identical.
- No third-party runtime dependency may be added.
- Every commit command below supplies all seven required policy trailers; do not
  rely on the hook's non-executable `TODO` placeholders.

---

### Task 1: Managed test-session lane and structured reservation

**Files:**
- Modify: `tools/agent-scratch:33-239,1253-1410`
- Modify: `tools/test_agent_scratch.py:58-420`

**Interfaces:**
- Produces: `TEST_SESSION_LANE = ("codex", "test-sessions")`.
- Produces: `agent-scratch reserve-test-session --json` with one versioned JSON object.
- Produces: `_reservation_record(root: Path, allocation: Path) -> dict[str, object]`.
- Consumes: existing descriptor-relative root creation and locking helpers.

- [ ] **Step 1: Write failing layout and reservation tests**

Add tests with these assertions:

```python
def test_reserve_test_session_returns_structured_private_allocation(self):
    root = self.make_root()
    with environment(AGENT_SCRATCH_ROOT=str(root)):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.helper.cmd_reserve_test_session(argparse.Namespace(json=True))
    record = json.loads(output.getvalue())
    allocation = pathlib.Path(record["allocation_path"])
    self.assertEqual(1, record["schema_version"])
    self.assertEqual("MANAGED_CODEX_TEST_SESSIONS", record["storage_tier"])
    self.assertEqual(str(root.resolve()), record["managed_root"])
    self.assertEqual(str(allocation.resolve()), record["allocation_path"])
    self.assertEqual(root / "codex" / "test-sessions", allocation.parent)
    self.assertEqual(0o700, stat.S_IMODE(allocation.stat().st_mode))
    self.assertTrue(record["filesystem_device"])
    self.assertGreater(record["usable_bytes"], 0)
    self.assertIn(record["inode_count_status"], ("MEASURED", "UNAVAILABLE_DYNAMIC"))
    if record["inode_count_status"] == "MEASURED":
        self.assertGreaterEqual(record["usable_inodes"], 0)
    else:
        self.assertIsNone(record["usable_inodes"])
    self.assertRegex(record["retention_deadline"], r"^\d{4}-\d{2}-\d{2}T")
    self.assertTrue(record["helper_version"])
```

Extend the root-layout test to require `codex/test-sessions`, and add rejection tests for a symlinked lane and a replaced allocation parent.

Inject `statvfs` results rather than relying on the host: a Btrfs-shaped
`f_files=0, f_favail=0` fixture must emit `UNAVAILABLE_DYNAMIC` plus JSON
`null`, while a nonzero-total fixture with `f_favail=0` must emit `MEASURED`
plus numeric zero.

- [ ] **Step 2: Run the helper tests and prove the new contract is absent**

Run:

```bash
python3 -m py_compile tools/agent-scratch tools/test_agent_scratch.py
python3 tools/test_agent_scratch.py
```

Expected: failures because the lane, command, and JSON fields do not exist.

- [ ] **Step 3: Implement lane creation and secure reservation**

Add constants and focused helpers:

```python
TEST_SESSION_LANE = ("codex", "test-sessions")
TEST_SESSION_RETENTION_DAYS = 7
RESERVATION_SCHEMA_VERSION = 1
HELPER_VERSION = "openggf-agent-scratch-v2"

def _statvfs_record(path: pathlib.Path) -> dict[str, object]:
    info = os.statvfs(path)
    return {
        "usable_bytes": info.f_bavail * info.f_frsize,
        "total_bytes": info.f_blocks * info.f_frsize,
        "inode_count_status": (
            "UNAVAILABLE_DYNAMIC"
            if info.f_files == 0 and info.f_favail == 0 else "MEASURED"),
        "usable_inodes": (
            None if info.f_files == 0 and info.f_favail == 0 else info.f_favail),
    }

def cmd_reserve_test_session(args: argparse.Namespace) -> int:
    root = ensure_root()
    with open_root(root) as root_fd, root_lock(root_fd):
        with child_fd(root_fd, TEST_SESSION_LANE, create=True) as lane_fd:
            name = f"session-{dt.datetime.now(dt.UTC):%Y%m%dT%H%M%SZ}-{random.randrange(1 << 32):08x}"
            os.mkdir(name, mode=MODE, dir_fd=lane_fd)
            allocation_fd = os.open(
                name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                dir_fd=lane_fd)
            try:
                _probe_directory(allocation_fd)
            finally:
                os.close(allocation_fd)
    allocation = root.joinpath(*TEST_SESSION_LANE, name)
    print(json.dumps(_reservation_record(root, allocation), sort_keys=True))
    return 0
```

The probe must create, write, `fsync`, atomically rename, read, and unlink a private file within the allocation. Register `reserve-test-session` in `build_parser()` and make `ensure_root()` create the lane idempotently.

- [ ] **Step 4: Run helper tests**

Run the Step 2 commands. Expected: all helper tests pass.

- [ ] **Step 5: Commit the reservation unit**

```bash
git add tools/agent-scratch tools/test_agent_scratch.py
git commit -m "feat: reserve managed test session storage" \
  -m "Changelog: n/a: agent workflow tooling only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: n/a" -m "Configuration-Docs: n/a" -m "Skills: n/a"
```

### Task 2: Installation verification and manifest-aware retention

**Files:**
- Modify: `tools/agent-scratch:505-557,571-609,831-872,1253-1392`
- Modify: `tools/test_agent_scratch.py:240-560`

**Interfaces:**
- Consumes: Task 1 lane and reservation schema.
- Produces: installed-helper freshness verification.
- Produces: seven-day terminal retention and stale-`RUNNING` quarantine.

- [ ] **Step 1: Add failing install, verify, and prune tests**

Cover `test_verify_rejects_missing_test_session_lane`,
`test_verify_rejects_stale_installed_helper`,
`test_prune_preserves_live_running_session`,
`test_prune_moves_expired_stale_running_session_to_quarantine`, and
`test_prune_removes_expired_terminal_session_unless_kept` explicitly.

Use minimal manifests containing `state: RUNNING|PASSED`, owner PID/start identity fixtures, and bounded keep markers. Assert stale `RUNNING` data is renamed into `quarantine`, not deleted.

Add verifier fixtures for both known sandbox user-bus diagnostics: “failed to
connect to bus” and “failed to connect to user scope bus”. Static helper,
configuration, lane, writable-root, and unit-file checks must still pass and
runtime service state must report `UNAVAILABLE_IN_SANDBOX`; an unknown
systemctl error remains a hard verification failure.

- [ ] **Step 2: Run helper tests and observe the new failures**

Run `python3 tools/test_agent_scratch.py`. Expected: the new verify and lifecycle cases fail.

- [ ] **Step 3: Implement install/verify freshness and session pruning**

Extend `cmd_verify()` to compare the installed helper fingerprint/content with the source helper and to require the canonical `codex/test-sessions` lane while continuing to require `<root>/codex` in `writable_roots`.

Add manifest-aware helpers named `_session_manifest_state(session_fd)`,
`_session_owner_live(session_fd)`, and
`_quarantine_session(root_fd, name, expected)`. The first returns a validated
manifest state or `None`, the second returns a PID/start-identity liveness
result, and the third returns the collision-safe quarantine entry name.

Prune rules are exact: live `RUNNING` is preserved; expired stale `RUNNING` is atomically moved to quarantine; expired terminal state is removed through existing staged descriptor-relative deletion; keep markers win.

- [ ] **Step 4: Run helper tests**

Run the Task 1 Step 2 commands. Expected: all pass.

- [ ] **Step 5: Install and verify the managed-allocation migration**

Install the just-tested source helper before any coordinator or wrapped Maven
command can depend on `reserve-test-session`:

```bash
tools/agent-scratch install
agent-scratch verify
agent-scratch reserve-test-session --json
```

Expected: installation reports the canonical managed root, verification proves
the installed helper matches this worktree's source and that
`<AGENT_SCRATCH_ROOT>/codex/test-sessions` exists, and the reservation command
returns schema version `1`, tier `MANAGED_CODEX_TEST_SESSIONS`, the installed
helper version, canonical managed/allocation paths, filesystem device,
capacity fields, seven-day retention deadline, and an allocation contained by
that lane. Capacity fields include inode-count status and conditional numeric
nullability.
This is the migration checkpoint: a stale installed helper is a hard failure,
not permission to continue to the coordinator tasks.

- [ ] **Step 6: Commit managed retention**

```bash
git add tools/agent-scratch tools/test_agent_scratch.py
git commit -m "fix: enforce managed session retention safety" \
  -m "Changelog: n/a: agent workflow tooling only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: n/a" -m "Configuration-Docs: n/a" -m "Skills: n/a"
```

### Task 3: Coordinator storage-allocation model and fail-closed resolution

**Files:**
- Modify: `tools/testing/TestSessionCoordinator.java:73-109,734-910,1289-1375`
- Modify: `tools/testing/TestSessionCoordinatorSelfTest.java:22-245,582-620`

**Interfaces:**
- Consumes: Task 1 JSON reservation command.
- Produces: `StorageTier`, `CapacitySnapshot`, and `StorageAllocation`.
- Produces: `resolveStorageAllocation(Path, boolean)`.

- [ ] **Step 1: Add failing root-policy tests**

Add a fake `agent-scratch` executable to the self-test PATH and verify:

```java
verifyManagedReservationIsValidated(root);
verifyManagedHelperFailureDoesNotFallback(root);
verifyManagedMalformedJsonDoesNotFallback(root);
verifyUnmanagedProjectFallbackIsVisible(root);
verifyExplicitRootRemainsFailClosed(root);
```

The managed-failure tests assert that no `.openggf/test-runs` directory and no child-start marker are created.

- [ ] **Step 2: Compile and run the coordinator self-test**

Run:

```bash
session_tmp="$(mktemp -d "$(agent-scratch path codex-tmp)/coordinator-plan-red.XXXXXX")"
javac --release 21 -d "$session_tmp/classes" \
  tools/testing/TestSessionCoordinator.java \
  tools/testing/TestSessionCoordinatorSelfTest.java
java -ea -cp "$session_tmp/classes" TestSessionCoordinatorSelfTest "$session_tmp/run"
```

Expected: new tests fail before the allocation model exists.

- [ ] **Step 3: Implement typed allocation and strict JSON parsing**

Add:

```java
private enum StorageTier {
    EXPLICIT_OVERRIDE,
    MANAGED_CODEX_TEST_SESSIONS,
    PROJECT_LOCAL_FALLBACK,
    SYSTEM_TMP_EXPLICIT
}

private record InodeSnapshot(
        InodeCountStatus status, Long usableInodes, String unavailableReason) {}

private record CapacitySnapshot(
        long usableBytes, long totalBytes, InodeSnapshot inodeSnapshot) {}

private record StorageAllocation(
        Path outputRoot, StorageTier tier, Path managedRoot,
        int allocationSchema, String helperVersion, String filesystemDevice,
        CapacitySnapshot allocationCapacity, InodeCountStatus inodeCountStatus,
        Instant retentionDeadline,
        String notApplicableReason, String warning) {}
```

Replace `resolveOutputRoot()`/`agentScratchRoot()` with `resolveStorageAllocation()`. Parse only the flat fields emitted by Task 1, reject duplicate/unknown required-field types, independently canonicalise and verify containment, and fail with `StartupFailure` whenever managed scratch is configured but reservation fails.

Require every design-mandated reservation field: schema version, canonical
managed root, canonical allocation path, storage tier, filesystem device,
usable bytes, inode-count status plus conditionally nullable inode value,
retention deadline, and helper version. Reject missing or
malformed fields, including a retention deadline that is not a future ISO-8601
instant within the helper's bounded seven-day policy.

Accept exactly `MEASURED` with a nonnegative numeric inode value or
`UNAVAILABLE_DYNAMIC` with JSON `null`. Reject every other pairing. The
allocation-time numeric zero gate applies only to `MEASURED`; the all-tier live
file probe remains authoritative when the count is unavailable.

- [ ] **Step 4: Run the coordinator self-test**

Run Step 2. Expected: all allocation-policy tests pass.

- [ ] **Step 5: Commit allocation resolution**

```bash
git add tools/testing/TestSessionCoordinator.java tools/testing/TestSessionCoordinatorSelfTest.java
git commit -m "fix: fail closed on managed session allocation" \
  -m "Changelog: n/a: agent workflow tooling only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: n/a" -m "Configuration-Docs: n/a" -m "Skills: n/a"
```

### Task 4: Capacity gate and storage manifest context

**Files:**
- Modify: `tools/testing/TestSessionCoordinator.java:73-180,574-812,1289-1375`
- Modify: `tools/testing/TestSessionCoordinatorSelfTest.java:22-191,620-788`
- Modify: `tools/testing/TestSessionProcessHarness.java:49-388`

**Interfaces:**
- Consumes: Task 3 `StorageAllocation`.
- Produces: `requiredFreeBytes(CapacitySnapshot)` and `ManifestContext`.
- Produces: visible start/end storage fields and `command.txt`.

- [ ] **Step 1: Add failing capacity and manifest tests**

Add deterministic tests for:

```java
InodeSnapshot measured = new InodeSnapshot(InodeCountStatus.MEASURED, 1L, null);
check(requiredFreeBytes(new CapacitySnapshot(0, 1_000L << 30, measured)) == 50L << 30,
        "five percent should exceed 20 GiB");
check(requiredFreeBytes(new CapacitySnapshot(0, 100L << 30, measured)) == 20L << 30,
        "20 GiB should be the minimum floor");
```

Add subprocess tests setting `OPENGGF_TEST_MIN_FREE_BYTES` above actual free space, asserting `STARTUP_FAILED`, no fake Maven launch, and manifest fields for tier, floor, launch capacity, schema/helper nullability, and warning. Add an invalid/too-low override test.

Add an injectable capacity probe returning sufficient bytes but zero usable
inodes with status `MEASURED`. Assert the coordinator writes `STARTUP_FAILED`, records the zero-inode
measurement, and never starts the fake Maven child.

Add a Btrfs-shaped allocation fixture with inode totals/free both zero,
`inode_count_status=UNAVAILABLE_DYNAMIC`, and `usable_inodes=null`. Assert a
successful live file probe permits launch and the manifest never labels the
unknown count as measured zero.

Add all-tier live inode-availability fixtures. The real production probe must
create, write, file-flush, read, and unlink a private file inside the unique
session directory, then flush the directory where the platform supports it.
Inject portable-file-probe failure before launch and assert every tier fails
closed with terminal evidence and no child; inject completion failure and
assert the primary child state remains terminal rather than `RUNNING`. Add a
platform-capability fixture proving unsupported directory flush records
`DIRECTORY_FLUSH_UNSUPPORTED` without refusing an otherwise successful probe.
Managed numeric inode zero still refuses immediately; unmanaged numeric counts
remain explicitly unavailable while a successful live probe records
`AVAILABLE`.

Add capacity-probe `IOException` tests before launch and at completion, blank
and whitespace-only override tests, and a `--lock-root` containing an encoded
newline/marker payload. Assert terminal evidence survives probe failures and
no output line can be forged as `OPENGGF_TEST_RUN_START` or `_END`.

- [ ] **Step 2: Run self-test and process harness for red evidence**

Run Task 3 Step 2 and `tools/testing/run-session-process-harness.sh`. Expected: new capacity/manifest tests fail.

- [ ] **Step 3: Implement capacity and manifest context**

Add:

```java
private static final long GIB = 1024L * 1024L * 1024L;
private static final long DEFAULT_MIN_FREE_BYTES = 20L * GIB;

private record ManifestContext(
        StorageAllocation allocation,
        CapacitySnapshot launchCapacity,
        CapacitySnapshot completionCapacity,
        CompactionResult compaction,
        boolean retainEphemeral,
        String storageFinalizationError) {}
```

Calculate `max(DEFAULT_MIN_FREE_BYTES, totalBytes / 20)`, allow the environment
to raise it, and reject explicitly blank as well as malformed/lower values.
Refuse launch when usable bytes are below the threshold, a managed numeric
inode snapshot with status `MEASURED` is zero, or the all-tier live
inode-availability probe fails. `UNAVAILABLE_DYNAMIC` is not exhaustion and
requires the successful live probe.
The portable file operations are authoritative; directory flush is required
only where supported and otherwise produces explicit observability rather than
a false inode failure.
Write `STARTUP_FAILED` after a session directory exists and ensure no child
process starts, even when the byte/inode probe itself throws. Add `command.txt`
before launch. Completion probe failures must be recorded without leaving a
`RUNNING` manifest. Extend manifest generation and both markers with the exact
spec fields, numeric inode nullability/reason, and live inode-probe status;
unmanaged helper fields serialize as JSON `null` plus a reason. Encode or
reject every string marker field, including lease paths, to keep markers
single-line and unforgeable.

- [ ] **Step 4: Run focused Java tests**

Run Step 2. Expected: all capacity and schema tests pass.

- [ ] **Step 5: Commit capacity and observability**

```bash
git add tools/testing/TestSessionCoordinator.java \
  tools/testing/TestSessionCoordinatorSelfTest.java \
  tools/testing/TestSessionProcessHarness.java
git commit -m "fix: gate test sessions on storage capacity" \
  -m "Changelog: n/a: agent workflow tooling only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: n/a" -m "Configuration-Docs: n/a" -m "Skills: n/a"
```

### Task 5: Terminal compaction and retention opt-out

**Files:**
- Modify: `tools/testing/TestSessionCoordinator.java:73-180,734-820,1167-1375`
- Modify: `tools/testing/TestSessionCoordinatorSelfTest.java:73-380,620-800`
- Modify: `tools/testing/TestSessionProcessHarness.java:49-454`
- Modify: `tools/testing/test-session.ps1:1-34`

**Interfaces:**
- Consumes: Task 4 `ManifestContext` and inventories.
- Produces: `CompactionResult compactTerminalSession(Paths paths, String state, boolean retainEphemeral, List<String> reports, List<String> artifacts, SessionDirectoryIdentity identity)`.
- Produces: coordinator flag `--retain-ephemeral`; PowerShell alias `-RetainEphemeral`.

- [ ] **Step 1: Add failing compaction safety tests**

Build fixture sessions containing both removable paths and preserved evidence. Cover every terminal state, all storage tiers, retain opt-out, a symlinked candidate, a replaced session root, a mount/file-store mismatch via injectable verifier, and a protected inventory path. Assertions must prove only these relative paths disappear:

```java
Set<String> expectedRemoved = Set.of("tmp", "build/test-classes/traces");
```

Also assert a compaction error changes an otherwise green run to `STORAGE_FINALIZATION_FAILED` while a pre-existing child/identity failure remains primary.

Exercise both deletion strategies through an injectable provider capability.
For secure streams, deterministically swap a bound candidate and prove an
outside sentinel survives. For the generic stable-key/no-secure-stream
strategy, require
same-store atomic tombstoning into an identity-bound private staging lane,
moved-file-key equality, no-follow/reparse rejection, per-ancestor identity
revalidation, and exact partial accounting. Prove the fallback succeeds for a
generic forced-provider fixture, while candidate/ancestor swaps move or refuse
the replacement itself and never touch the outside sentinel. Do not label the
injected stable-key fixture as native Windows evidence. Use an injectable
reparse or identity mismatch fixture when privileges do not permit symlinks.

Add an injectable provider fixture with neither `SecureDirectoryStream` nor a
non-null stable `fileKey()`. Assert the test fails before the branch exists,
then prove the implementation performs no mutation, preserves both compactable
paths, returns certifying `RETAINED_PLATFORM_UNSUPPORTED`, and records the
exact provider/file-store reason.
Name this as the JDK 21 native-Windows contract in tests/docs while keeping it
provider-injected and portable; do not claim native-host coverage unless a
Windows JDK 21 run actually occurred.

- [ ] **Step 2: Run focused Java tests and observe failures**

Run Task 4 Step 2. Expected: compaction cases fail.

- [ ] **Step 3: Implement verified allowlist compaction**

Add:

```java
private enum CompactionStatus {
    COMPACTED, NOTHING_TO_REMOVE, RETAINED_BY_REQUEST,
    RETAINED_PLATFORM_UNSUPPORTED, FAILED, REFUSED
}

private record CompactionResult(
        CompactionStatus status,
        List<String> removedRelativePaths,
        List<String> partiallyModifiedRelativePaths,
        long reclaimedBytes,
        String error) {}

private record SessionDirectoryIdentity(
        Path realPath, Object fileKey, FileStore fileStore) {}
```

Capture identity immediately after session creation. Before each removal,
revalidate the session and descendant with `NOFOLLOW_LINKS`, same file store,
exact canonical containment, and report/artifact protection. Use
`SecureDirectoryStream` descriptor-relative deletion when supported. Otherwise
atomically move each fully bound candidate into a private same-store staging
lane, verify its moved identity, and delete the tombstone with no-follow,
reparse, and ancestor-identity checks. Never fall back to an ordinary unbound
pathname walk, and do not fail a healthy native Windows session merely because
secure streams are unavailable: under JDK 21 it remains certifying as
`RETAINED_PLATFORM_UNSUPPORTED`. Use a non-null provider
`BasicFileAttributes.fileKey()` plus file-store identity as the stable native
token. If both secure streams and a stable token are unavailable, perform no
mutation and return certifying `RETAINED_PLATFORM_UNSUPPORTED` with an exact
reason. Record full and partial candidate progress separately through the two
result lists. Write a terminal pre-compaction manifest, compact, measure exact
reclaimed bytes after each successful deletion, then atomically write the final
manifest. Apply the same function from normal and shutdown finalisation.

Task 6 documentation must state that automatic terminal compaction is active
on secure-stream or stable-key providers, while native Windows JDK 21 visibly
retains with `RETAINED_PLATFORM_UNSUPPORTED` pending a future native file-ID
bridge. Capacity refusal and managed retention still apply there.

Task 6 guidance and guards must also document measured-versus-dynamic inode
nullability and sandbox-static verification with
`UNAVAILABLE_IN_SANDBOX` service runtime state.

- [ ] **Step 4: Add and translate the opt-out flag**

Parse `--retain-ephemeral` in `Options`. Add this PowerShell translation:

```powershell
'^(?i)-RetainEphemeral$' { $translated.Add('--retain-ephemeral'); continue }
```

The POSIX wrapper already passes arguments through unchanged; add a test proving that behavior rather than changing it.

- [ ] **Step 5: Run focused Java and harness tests**

Run Task 4 Step 2. Expected: all tests pass and terminal fixture sizes shrink only by the allowlisted paths.

- [ ] **Step 6: Commit compaction**

```bash
git add tools/testing/TestSessionCoordinator.java \
  tools/testing/TestSessionCoordinatorSelfTest.java \
  tools/testing/TestSessionProcessHarness.java \
  tools/testing/test-session.ps1
git commit -m "fix: compact terminal test sessions safely" \
  -m "Changelog: n/a: agent workflow tooling only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: n/a" -m "Configuration-Docs: n/a" -m "Skills: n/a"
```

### Task 6: Structural guards and user documentation

**Files:**
- Modify: `src/test/java/com/openggf/tests/TestBuildToolingGuard.java:516-763`
- Modify: `docs/architecture/designs/2026-08-23-test-session-isolation-design.md:60-90,519-560`
- Modify: `tools/testing/README.md:1-40`
- Modify: `docs/agent-workflow/README.md:65-140`
- Modify together: `AGENTS.md`, `CLAUDE.md`

**Interfaces:**
- Consumes: Tasks 1-5 final command and manifest contracts.
- Produces: structural enforcement and operator guidance.

- [ ] **Step 1: Add failing structural assertions**

Extend `TestBuildToolingGuard` to require:

```java
List<String> requiredStorageGuidance = List.of(
    "MANAGED_CODEX_TEST_SESSIONS",
    "OPENGGF_TEST_MIN_FREE_BYTES",
    "--retain-ephemeral",
    "STORAGE_FINALIZATION_FAILED",
    "UNAVAILABLE_DYNAMIC",
    "UNAVAILABLE_IN_SANDBOX"
);
```

Require both wrappers to route the retention flag, require AGENTS/CLAUDE byte
identity, and require guidance to explain that `UNAVAILABLE_DYNAMIC` carries a
JSON-null numeric count whose live probe is authoritative.

- [ ] **Step 2: Run the focused guard and verify it fails**

Run:

```bash
tools/testing/test-session.sh -- mvn -Dmse=off \
  -Dtest=TestBuildToolingGuard test -B
```

Expected: failure identifying missing guidance.

- [ ] **Step 3: Update the documents**

Document the storage tiers, managed install/verify requirement, capacity formula, terminal compaction allowlist, evidence retained, opt-out, stale-running quarantine, and exact marker fields. Add a reference from the 2026-08-23 design to the 2026-08-26 terminal-storage addendum. Keep `AGENTS.md` and `CLAUDE.md` identical.

- [ ] **Step 4: Run helper, harness, and focused guard tests**

Run:

```bash
python3 tools/test_agent_scratch.py
tools/testing/run-session-process-harness.sh
tools/testing/test-session.sh -- mvn -Dmse=off \
  -Dtest=TestBuildToolingGuard test -B
```

Expected: all pass with wrapper start/end markers present.

- [ ] **Step 5: Commit guards and documentation**

```bash
git add src/test/java/com/openggf/tests/TestBuildToolingGuard.java \
  docs/architecture/designs/2026-08-23-test-session-isolation-design.md \
  tools/testing/README.md docs/agent-workflow/README.md AGENTS.md CLAUDE.md
git commit -m "docs: define terminal session storage lifecycle" \
  -m "Changelog: n/a: agent workflow documentation only" -m "Guide: n/a" \
  -m "Known-Discrepancies: n/a" -m "S3K-Known-Discrepancies: n/a" \
  -m "Agent-Docs: updated" -m "Configuration-Docs: n/a" -m "Skills: n/a"
```

### Task 7: Session-storage verification checkpoint

**Files:**
- Verify only; fix failures in the owning task's files before proceeding.

**Interfaces:**
- Produces: independently testable session-storage delivery before worktree lifecycle starts.

- [ ] **Step 1: Run all focused non-Maven checks**

```bash
python3 -m py_compile tools/agent-scratch tools/test_agent_scratch.py
python3 tools/test_agent_scratch.py
tools/testing/run-session-process-harness.sh
git diff --check
```

Expected: all exit zero.

- [ ] **Step 2: Run the ordinary full suite**

```bash
tools/testing/test-session.sh -- mvn test
```

Expected: certifying start/end markers; record run ID, manifest, log, and exact failures.

- [ ] **Step 3: Run the fresh-JVM structural guards**

```bash
tools/testing/test-session.sh -- mvn -Dmse=off -Pguards test -B
```

Expected: certifying markers and no new guard failures.

- [ ] **Step 4: Commit any verified corrections**

Stage only files actually corrected, rerun the affected focused checks, and
commit with all seven accurate policy trailers. Do not create a “verification”
commit when no files changed.
