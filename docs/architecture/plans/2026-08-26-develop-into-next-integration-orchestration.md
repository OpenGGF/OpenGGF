# Develop Into Next Integration Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a locally reviewed `next` integration commit whose first-parent
line is rooted at frozen `next` `84d9a3761`, whose exact second parent is frozen
`develop` `e3f390e9f`, and whose measured behavior introduces no regression
relative to either parent.

**Architecture:** Keep one coordinator-owned merge index in the isolated
integration worktree. Use separate worktrees and test sessions for parent
baselines and subagent work; use parallel subagents first for read-only conflict
analysis, then resolve the merge centrally in dependency order. Build exact
Surefire inventories, treat missing execution as `ABSENT`, and retain a
deterministic partitioned fallback when a monolithic suite is incomplete.

**Tech Stack:** Git worktrees/cowtree, Bash, PowerShell 7, Java 21, Maven 3.9,
Surefire/JUnit 5, OpenGGF test-session coordinator, canonical S1/S2/S3K ROMs.

**Spec:**
`docs/architecture/designs/2026-08-26-develop-into-next-integration-orchestration.md`

## Global Constraints

- Frozen `next` is `84d9a3761f618035dd1caa40a3d5fc72a1019693`.
- Frozen `develop` is `e3f390e9fe381b89cc54a2399b5a49843ba044e4`.
- The merge base is `59e59c8feb5fb5a247ff0ab43da63aeccc742cb0`.
- The main workspace remains on `develop`; no branch switch is permitted there.
- The coordinator alone owns the integration worktree's index, staging, merge,
  commits, and branch operations.
- Parallel agents use separate worktrees and separate wrapper sessions; they do
  not edit the coordinator's merge worktree.
- No blanket `ours`, `theirs`, game-name/zone carve-out, trace hydration,
  fixture-fitted constant, disassembly runtime fallback, or `--no-verify` is
  permitted.
- Conflict decisions preserve `develop`'s current shared owners and re-express
  `next`'s observable 0.7 contracts on them.
- Certifying Maven commands run on JDK 21 through the quiet test-session
  coordinator and retain both start/end markers, manifest path, and log path.
- Raw Maven lifecycle output is diagnostic only and never release evidence.
- Ordinary and structural-guard sessions are separate.
- Before quoting any suite or trace number, read
  `docs/agent-workflow/briefing-trace-rounds.md` and apply its measurement-hazard
  rules.
- Discover ROM files rather than assuming example aliases. Verify:
  - S1 CRC32 `AFE05EEE`, SHA-1
    `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`;
  - S2 CRC32 `7B905383`, SHA-1
    `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`;
  - S3K CRC32 `63522553`, SHA-1
    `CFBF98C36C776677290A872547AC47C53D2761D6`.
- Stop before updating or pushing `next`; final promotion requires explicit
  human confirmation after green independent review.

---

### Task 1: Implement the Frozen-Next Test-Session Compatibility Adapter

**Files:**

- Create: `tools/testing/frozen-next-session.exclude`
- Create: `tools/testing/frozen-next-session-launch.sh`
- Create: `tools/testing/frozen-next-session-adapter.sh`
- Create: `tools/testing/test-frozen-next-session-adapter.sh`
- Modify: `tools/testing/README.md`

**Interfaces:**

- Consumes: frozen-next worktree path, expected commit, detached frozen-develop
  harness worktree, expected harness commit, pinned wrapper/coordinator paths,
  Maven command, and inherited `OPENGGF_*` coordinator identity.
- Produces: a coordinator-launched Maven run whose frozen POM output is routed
  into the session roots, plus recovery evidence and a cleanup command.

- [ ] **Step 1: Write the external exclude and failing adapter self-test**

  Put exactly `/target` and a trailing newline in
  `tools/testing/frozen-next-session.exclude`. In the self-test, create a new
  detached cowtree at `84d9a3761`, select two existing frozen-next guard classes,
  and assert that the not-yet-implemented launcher provides all of these cases:

  1. success with two distinct Surefire fork identities;
  2. ordinary Maven failure with cleanup;
  3. forced child termination with outer recovery;
  4. refusal to unlink an ordinary `target` directory;
  5. refusal to unlink a mismatched `target` symlink;
  6. runtime-input mutation ending `INVALID_IDENTITY_CHANGED`;
  7. in-run clean `git status` and `git check-ignore -v target` attribution to
     the pinned external exclude file; and
  8. post-run exact detached HEAD, clean source inventory, and absent `target`;
     and
  9. rejection of a wrong harness commit, wrapper outside the harness
     worktree, and coordinator bytes that differ from the expected Git blob.

- [ ] **Step 2: Run the self-test and confirm RED**

  Run:

  ```bash
  tools/testing/test-frozen-next-session-adapter.sh
  ```

  Expected: nonzero with `frozen-next-session-launch.sh` missing.

- [ ] **Step 3: Implement the pre-coordinator launcher**

  Make `frozen-next-session-launch.sh`:

  - accept `--worktree`, `--expected-head`, `--harness-worktree`,
    `--expected-harness-head`, `--wrapper`, `--coordinator`, `--adapter`, then
    `--` and the Maven arguments;
  - canonicalize every path and reject a non-detached or wrong HEAD;
  - require wrapper and coordinator paths beneath the canonical detached clean
    harness worktree at the expected harness HEAD, and byte-compare both files
    against `git show $EXPECTED_HARNESS_HEAD:tools/testing/test-session.sh` and
    `git show $EXPECTED_HARNESS_HEAD:tools/testing/TestSessionCoordinator.java`;
  - append one temporary Git config entry without overwriting any inherited
    `GIT_CONFIG_COUNT` entries: key `core.excludesFile`, value the canonical
    tracked exclude file;
  - export `OPENGGF_RUNTIME_INPUTS` containing the canonical launcher, exclude,
    adapter, wrapper, and `TestSessionCoordinator.java` paths while preserving
    existing entries;
  - invoke the pinned wrapper from the frozen worktree; and
  - execute an outer `finally` recovery that reads the session marker and uses
    `lstat`/`readlink` to unlink only the exact adapter-created `target` symlink.

  Never use `rm -rf`, glob deletion, `clean`, or persistent `git config`.

- [ ] **Step 4: Implement the coordinator child adapter**

  Make `frozen-next-session-adapter.sh`:

  - reject `clean` anywhere in the Maven arguments;
  - require exact detached HEAD, empty tracked/non-ignored status, and absent
    `target` before mutation;
  - validate the inherited external exclude and its SHA-256;
  - install its trap before creating links;
  - create `target -> $OPENGGF_BUILD_DIRECTORY` and session-build links for
    `surefire-reports`, `test-tmp`, `trace-reports`, `diagnostics`, `artifacts`,
    and `distribution`;
  - verify status remains empty after link creation;
  - resolve the active-platform `surefire.argLine`, preserving CDS, Mockito,
    heap, and macOS first-thread options;
  - append
    `-Dorg.lwjgl.system.SharedLibraryExtractPath=$OPENGGF_TEST_TMP_ROOT/lwjgl-${surefire.forkNumber}`;
  - run Maven with the supplied lifecycle/selector arguments;
  - before cleanup, parse report JVM properties and record lexical/canonical
    temp and LWJGL evidence in the session diagnostics; and
  - unlink only after no-follow type, exact link target, marker run ID, and
    canonical worktree checks pass.

- [ ] **Step 5: Run adapter self-tests to GREEN**

  Run the self-test through the test-session coordinator where applicable.
  Expected: every case prints `PASS`; the successful manifest inventories the
  selected `TEST-*.xml`; two fork reports have distinct resolved
  fork-specific `lwjgl-1` and `lwjgl-2` paths beneath the same session tmp root;
  forced cleanup leaves
  no `target`; the negative cases preserve their targets.

- [ ] **Step 6: Run existing coordinator harnesses**

  From the detached harness worktree at exact
  `e3f390e9fe381b89cc54a2399b5a49843ba044e4`, byte-authenticate both frozen
  Java sources against their Git blobs before compilation, then run the
  process harness and compiled coordinator self-test from managed scratch:

  ```bash
  cmp -s tools/testing/TestSessionCoordinator.java \
    <(git show e3f390e9fe381b89cc54a2399b5a49843ba044e4:tools/testing/TestSessionCoordinator.java)
  cmp -s tools/testing/TestSessionCoordinatorSelfTest.java \
    <(git show e3f390e9fe381b89cc54a2399b5a49843ba044e4:tools/testing/TestSessionCoordinatorSelfTest.java)
  OPENGGF_PROCESS_HARNESS_ROOT="$(agent-scratch new frozen-develop-process-harness)"
  OPENGGF_COORDINATOR_CLASSES="$(agent-scratch new frozen-develop-coordinator-classes)"
  OPENGGF_COORDINATOR_SELF_TEST_ROOT="$(agent-scratch new frozen-develop-coordinator-self-test)"
  OPENGGF_HARNESS_ROOT="$OPENGGF_PROCESS_HARNESS_ROOT" \
    tools/testing/run-session-process-harness.sh
  javac --release 21 -d "$OPENGGF_COORDINATOR_CLASSES" \
    tools/testing/TestSessionCoordinator.java \
    tools/testing/TestSessionCoordinatorSelfTest.java
  java -cp "$OPENGGF_COORDINATOR_CLASSES" TestSessionCoordinatorSelfTest \
    "$OPENGGF_COORDINATOR_SELF_TEST_ROOT"
  ```

  Expected: both harnesses pass with no source/runtime identity regression.

- [ ] **Step 7: Document the historical adapter contract**

  Add a bounded section to `tools/testing/README.md` naming frozen commit
  `84d9a3761`, explaining why the adapter exists, prohibiting `clean`, and
  documenting that it is baseline-only rather than a production launcher.

- [ ] **Step 8: Commit the adapter**

  Stage only the five Task 1 files. Commit as:

  ```text
  test(integration): adapt frozen next to session isolation
  ```

  Use the repository trailer block with accurate mappings.

---

### Task 1A: Repair Historical-Next Certification After the First Full Run

**Files:**

- Modify: `tools/testing/frozen-next-session-adapter.sh`
- Modify: `tools/testing/frozen-next-session-launch.sh` for mandatory outer
  generated-report recovery after forced termination.
- Modify: `tools/testing/test-frozen-next-session-adapter.sh`
- Modify: `tools/testing/README.md`

**Interfaces:**

- Consumes: the invalid first frozen-next run
  `20260826T044550Z-p1686525-b0d842`, exact historical report blob, and the
  existing authenticated adapter contract.
- Produces: representative coordinator temp semantics and a fail-closed,
  evidenced normalization of one intentional historical generated report.

- [ ] **Step 1: Preserve the two independent RED reproductions**

  Record fixture cases that fail against the current adapter:

  1. a child that rewrites only
     `docs/status/rewind-round-trip-gaps.md` makes the coordinator publish
     `INVALID_IDENTITY_CHANGED`; and
  2. the symlink adapter makes `java.io.tmpdir` lexically
     `<worktree>/target/test-tmp` but canonically session-external, causing real
     frozen containment tests to fail even though the storage root is isolated.

  Keep the original invalid run and dirty cowtree as evidence until the repair
  is reviewed. Do not restore it in place or reinterpret its failure counts.

- [ ] **Step 2: Replace symlink routing with private bind-mount and PID namespaces**

  Preflight
  `unshare --user --map-root-user --mount --pid --fork --kill-child=KILL`,
  `mount --bind`, `mountpoint`, and `rmdir` in managed scratch. Do not replace
  the host procfs mount. Fail closed if unprivileged namespaces or bind mounts
  are unavailable. Before root mapping, resolve the
  current numeric UID through the passwd database, canonicalize that home and
  `HOME`, and require equality without modifying `HOME`. Reject `user.home` in
  caller Maven arguments, existing `MAVEN_OPTS`, `JAVA_TOOL_OPTIONS`, or the
  resolved Surefire base line. The coordinator-child adapter
  creates an ignored empty real `target` directory and empty real mountpoint
  directories beneath the coordinator build root, then starts an exact
  `unshare` supervisor whose child is PID 1 in the private PID namespace. The
  child must run `mount --make-rprivate /` before establishing these bindings:

  ```text
  $OPENGGF_BUILD_DIRECTORY       -> <worktree>/target
  $OPENGGF_TEST_TMP_ROOT         -> <worktree>/target/test-tmp
  <session>/surefire-reports     -> <worktree>/target/surefire-reports
  <session>/trace-reports        -> <worktree>/target/trace-reports
  $OPENGGF_TEST_DIAGNOSTICS      -> <worktree>/target/diagnostics
  $OPENGGF_ARTIFACT_ROOT         -> <worktree>/target/artifacts
  $OPENGGF_DISTRIBUTION_ROOT     -> <worktree>/target/distribution
  ```

  Authenticate each binding inside the namespace with `mountpoint` and matching
  no-follow device/inode identity between source and mounted target. Record a
  bounded mount table, the exact outer supervisor PID/start identity, the outer
  PID/start identity and PID-namespace inode of private PID 1, and their common
  mount-namespace inode, then stop at a ready/go barrier. Before releasing
  Maven, the adapter in the still-parent namespace revalidates those identities
  and simultaneously proves its `target` view is the original empty ordinary
  directory and is not a mountpoint. Do not infer private PID 1 from `/proc/1`
  or `/proc/$$`: without `--mount-proc`, both resolve through the host procfs
  mount. Have PID 1 use a shell-native read of an already-open
  `/proc/self/status` to publish its `NSpid` chain; do not delegate that read to
  a subprocess. The outer adapter must authenticate the published host PID
  against the supervisor's single direct
  `/proc/<supervisor>/task/<supervisor>/children` edge, exact start time, an
  `NSpid` chain ending in 1, PID-namespace inode, and the common mount-namespace
  inode. Preserve frozen `next`'s own historical
  `java.io.tmpdir=<worktree>/target/test-tmp`; lexical and canonical paths must
  both remain worktree-local while the mount identity proves exact backing by
  the coordinator tmp root. Root mapping otherwise makes Java choose `/root`,
  so append `-Duser.home=<authenticated outer passwd home>` to preserved
  adapter-owned `MAVEN_OPTS` for both help/evaluate and actual Maven, and append
  the same property through `${surefire.argLine}` for every fork. Also append:

  ```text
  -Dorg.lwjgl.system.SharedLibraryExtractPath=$OPENGGF_TEST_TMP_ROOT/lwjgl-${surefire.forkNumber}
  ```

  Reject caller `argLine`, `surefire.argLine`, `java.io.tmpdir`, LWJGL, mount,
  namespace, or `user.home` overrides. Require Surefire XML to report the exact
  authenticated outer home. Normal cleanup must reap the exact supervisor and
  then verify both the recorded supervisor and private-PID-1 identities are
  gone; forced cleanup must perform the same bounded verification. After
  termination, an absent PID or the same PID with a different start time means
  the recorded identity is gone, while the same PID with the same start time
  means it survives. Private PID-namespace
  lifecycle is the containment proof: descendants cannot escape to an ancestor
  PID namespace, PID-1 exit causes the kernel to kill remaining members, and
  supervisor death invokes `--kill-child=KILL`. Then require the parent view of
  `target` to be the exact still-empty, non-mount ordinary directory recorded
  in the recovery marker and remove only it with `rmdir`. Never recursively
  delete it. Do not scan every host `/proc/*/ns/mnt` entry or maintain an
  unreadable-process census; concurrent hostile privileged host interference is
  outside the unprivileged harness threat model. Mandatory outer recovery uses
  the same bounded identity checks after forced termination.

- [ ] **Step 3: Implement the pinned generated-report normalization**

  Before Maven, require exact detached HEAD
  `84d9a3761f618035dd1caa40a3d5fc72a1019693`, a clean tree, a regular
  non-symlink `docs/status/rewind-round-trip-gaps.md`, and committed blob
  `d83614ec3a32abd1d6636d2be247ade01331bf3c`. Archive its exact bytes in session
  diagnostics. After the child exits:

  - allow no normalized path except that exact report;
  - if it changed, require a regular non-symlink file with the expected title
    and summary fields and require authenticated current-session Surefire XML
    to record exact class
    `com.openggf.game.rewind.coverage.TestRewindRoundTripProbe`, method
    `probeReportIsWrittenToDisk`, and its outcome; archive that outcome with the
    generated bytes, SHA-256, and byte length;
  - atomically restore the exact committed preimage before adapter return; and
  - record run ID, path, original/generated hashes and lengths, restoration
    status, and any unexpected source inventory.

  Before Maven, extend the authenticated recovery marker with frozen HEAD, run
  ID, canonical worktree/report paths, exact preimage archive path/hash/length,
  ordinary target mountpoint path, pre-mount no-follow type/device/inode,
  expected parent-empty/non-mount state, exact supervisor and private-PID-1
  PID/start identities, PID-namespace inode, and their common mount-namespace
  inode. Mandatory outer recovery uses no-follow/type/hash
  checks to restore only that report if forced termination bypasses the child
  trap. Exact-coordinator evidence run
  `20260826T053627Z-p1892866-17619a` proves the coordinator forcibly terminates
  the adapter before its trap completes, so launcher INT/TERM must remain
  `INVALID_IDENTITY_CHANGED` / `valid=false`; outer restoration happens only
  after finalization and never upgrades that manifest. TERM evidence run
  `20260826T064242Z-p2230037-e42daf` also records the exact frozen coordinator's
  competing-finalizer race: the on-disk manifest is invalid/false and cleanup
  succeeds, but `OPENGGF_TEST_RUN_END` can be absent. Permit that shape only in
  the interruption safety fixture, record it explicitly, and reject it as
  parent/candidate evidence exactly like every other missing-marker run.

  Do not pin the generated hash: the report contains the date and has
  non-contractual enumeration/locale ordering. Never ignore, delete, restore,
  or normalize a second mutation. Any bad preimage, replacement type,
  archive/restore failure, or second tracked/untracked mutation must end
  nonzero and remain coordinator-invalid. Preserve an already-failing Maven
  status when normalization succeeds.

  Add a pinned identity tripwire for adapter safety or cleanup failures. Frozen
  `next` ignores `target`, so an unsafe target replacement can otherwise leave
  a `FAILED` / `valid=true` coordinator manifest even though namespace teardown
  or outer cleanup failed. When such a failure occurs before adapter return:

  - if the authenticated report is already dirty, leave it untouched until the
    coordinator final digest;
  - if it is still the exact authenticated preimage, write a deterministic
    invalidation marker containing the current run identity and record the
    safety reason, hash, and byte length in session diagnostics;
  - never normalize or restore this tripwire before coordinator finalization;
  - preserve an existing nonzero Maven status, or return nonzero when the child
    succeeded; and
  - treat inability to arm the tripwire as a hard non-certifying launcher
    failure, regardless of any superficially valid coordinator manifest.

  After coordinator finalization, outer recovery restores only the exact
  authenticated report preimage. It must preserve any unsafe `target` object
  for inspection and continue to refuse unlink/readlink recovery. Ordinary
  Maven failure with successful adapter safety and cleanup remains
  `FAILED` / `valid=true`. Every parent-baseline consumer must require the
  authenticated launcher outcome and cleanup diagnostics in addition to the
  manifest; never admit an adapter-produced manifest by itself.

- [ ] **Step 4: Extend the adapter matrix with fail-closed cases**

  Add cases for exact-report mutation on success and child failure, no report
  mutation, report plus second mutation, wrong initial blob, missing report,
  symlink/directory replacement, archive failure, restore failure, and
  interruption after report generation. Add namespace-unavailable,
  bind-failure, wrong mount source/device/inode, propagation leakage,
  ready/go parent-view mismatch, surviving supervisor/private PID 1,
  passwd/HOME mismatch, preexisting `user.home` override, non-empty target, and
  changed target-identity cases. Include a real frozen-POM fixture proving
  both worktree-local lexical/canonical temp paths and exact coordinator-tmp
  mount identity while `${surefire.argLine}` yields distinct fork-specific
  LWJGL roots; do not accept a synthetic XML-only assertion. Require exact outcomes:

  | Case | Process status | Manifest state | valid |
  |---|---:|---|---|
  | child 0 + authorized normalization | 0 | `PASSED` | `true` |
  | child N + authorized normalization | N | `FAILED` | `true` |
  | normalization failure after child 0 | nonzero | `INVALID_IDENTITY_CHANGED` | `false` |
  | normalization failure after child N | N | `INVALID_IDENTITY_CHANGED` | `false` |
  | adapter safety/cleanup failure after child 0 | nonzero | `INVALID_IDENTITY_CHANGED` | `false` |
  | adapter safety/cleanup failure after child N | N | `INVALID_IDENTITY_CHANGED` | `false` |
  | tripwire-arm failure | hard launcher failure; never accepted | non-certifying regardless of manifest | never accepted |
  | launcher INT/TERM, outer recovery after digest | 130/143 | on-disk `INVALID_IDENTITY_CHANGED`; terminal marker present or explicitly diagnosed frozen race | `false` |

  Require clean exact final bytes and a valid manifest only for authorized
  successful normalization; every unsafe shape remains invalid and emits an
  explicit diagnostic. Verify the archived metadata and generated report are
  session-owned. The forced-kill case must bypass the child trap, exercise
  mandatory outer recovery after coordinator finalization, preserve the invalid
  manifest, explicitly classify any missing terminal line as non-certifying,
  and prove the session is never certifying. Do not add a new
  cross-process graceful-signal handshake merely to manufacture `ABORTED`.
  Add explicit unsafe-target fixtures for a symlink replacement after child N,
  a cleanup failure after child 0, and tripwire-arm failure. The first must
  preserve child N as the launcher/coordinator process status, separately
  record adapter namespace-teardown status 75 as the pre-finalization failure
  that armed the tripwire, publish invalid/false, restore the report
  externally, retain the symlink, and separately diagnose authenticated outer
  cleanup status 73. The fixture must reject a tripwire armed for an unrelated
  reason. Also retain a control case proving an ordinary child N with no adapter
  safety failure stays `FAILED` / `valid=true`.

  Add functional PID-containment fixtures, not a help-text assertion: launch a
  `setsid` plus double-fork descendant and a nested mount-namespace descendant,
  then prove their recorded outer PID/start identities are gone after normal
  PID-1 exit and after forced supervisor termination. At ready/go and when
  authenticating the recovery marker, prove PID/start, PID-namespace, and
  supervisor/PID-1 mount-namespace mismatches fail closed. After termination,
  prove same PID plus same recorded start time is treated as a survivor and
  preserves the target, while an absent PID or the same PID with a different
  start time counts as the recorded identity gone. Exercise both normal and
  forced cleanup. No host-wide unreadable-holder fixture is required.

- [ ] **Step 5: Run focused real frozen-next proof**

  In a fresh clean detached cowtree, use the authenticated frozen-develop
  wrapper to run `TestRewindRoundTripProbe` plus representative containment
  tests that failed only because of lexical/canonical temp mismatch. Require:

  - explicit start/end markers and `valid=true`;
  - historical probe methods and containment tests actually reported;
  - archived generated-report evidence;
  - historical worktree-local lexical/canonical temp properties in the XML,
    plus recorded device/inode mount identity to the coordinator tmp root;
  - fork `user.home` equal to the authenticated outer passwd home, with Maven
    local-repository resolution proven before project execution;
  - exact original report bytes restored; and
  - clean detached source inventory with no `target` after cleanup.

- [ ] **Step 6: Review and commit the repair**

  Run the complete adapter matrix and both authenticated coordinator harnesses.
  A fresh reviewer checks the normalization boundary, signal/status precedence,
  temp semantics, and evidence. Fix and repeat until green, then commit as:

  ```text
  test(integration): normalize frozen next generated output
  ```

---

### Task 1B: Re-pin the Frozen Harness After Develop Drift

**Files:**

- Modify: `tools/testing/test-frozen-next-session-adapter.sh`

**Interfaces:**

- Consumes: old harness pin `f1b82774d`, new frozen develop
  `97bc177ee`, and the independently green Task 1A adapter.
- Produces: the same authenticated adapter contract pinned to the new exact
  parent commit.

- [ ] **Step 1: Prove harness bytes and prerequisites are unchanged**

  Require an empty diff from `f1b82774d..97bc177ee` for
  `tools/testing/test-session.sh`, `TestSessionCoordinator.java`,
  `TestSessionCoordinatorSelfTest.java`, the Maven/test configuration, and hook
  paths consumed by the adapter. Record the two S2 special-stage commits and
  their merge ancestry separately; do not infer harness equivalence from the
  fast-forward alone.

- [ ] **Step 2: Update only the exact harness pin**

  Change the adapter self-test's frozen harness constant from
  `f1b82774d4aeb9585e75bd74e90856e7b67256d7` to
  `97bc177ee1f3a8924d4354933dad32fb1c038a9b`. Do not weaken any byte,
  detached-HEAD, admission, cleanup, or source-identity check.

- [ ] **Step 3: Repeat certification and review**

  Run the complete adapter matrix, real combined frozen-next proof, pinned
  process harness, and compiled coordinator self-test against the new detached
  harness head. Require production `PASSED` / `valid=true` / authenticated /
  admissible evidence and a clean target-free cowtree. Independently review the
  one-line pin change and evidence, then commit it before Task 3 restarts.

---

### Task 1C: Re-pin the Reconciled and Pushed Develop Snapshot

**Files:**

- Modify: `tools/testing/test-frozen-next-session-adapter.sh`

**Interfaces:**

- Consumes: historical pin `97bc177ee`, reconciled and pushed snapshot
  `e3f390e9f`, and the independently green Task 1A/1B adapter.
- Produces: the unchanged authenticated adapter contract pinned to the exact
  merge second parent.

- [x] **Step 1: Prove harness bytes and merge shape are unchanged**

  Require an empty `97bc177ee..e3f390e9f` diff for the wrapper, coordinator,
  coordinator self-test, process harness, hook paths, `.mvn`, and `pom.xml`.
  Record the seven-commit, 28-path source delta separately. A fresh recursive
  merge simulation must retain the exact 77-path conflict set.

- [x] **Step 2: Update only the exact harness pin**

  Change the adapter self-test's frozen harness constant from
  `97bc177ee1f3a8924d4354933dad32fb1c038a9b` to
  `e3f390e9fe381b89cc54a2399b5a49843ba044e4`. Do not weaken any authentication,
  admission, cleanup, or source-identity check.

- [x] **Step 3: Repeat the complete adapter matrix**

  Run `tools/testing/test-frozen-next-session-adapter.sh` against the new pin.
  Require exit 0 across its success and deliberate failure-path fixtures.

- [ ] **Step 4: Repeat real parent evidence and independent review**

  Repeat the real frozen-next proof, pinned process/coordinator harnesses, both
  parent ordinary/guard evidence sets, and independent review. Regenerate the
  union partition map; the new CNZ ordering class makes the prior 2,374-class
  map stale (expected 2,375 if classification is otherwise unchanged).

- [ ] **Step 5: Re-review the affected conflict-ledger rows**

  When Task 4 creates the ledger, independently re-review the rows for
  `README.md`, `docs/status/known-discrepancies.md`,
  `Sonic3kCoordFlagHandler.java`, and `TestSonic3kAIZEvents.java` against the
  `97bc177ee..e3f390e9f` source delta.

---

### Task 2: Implement Complete Surefire Inventory and Comparison Tooling

**Files:**

- Create: `tools/testing/Export-SurefireOutcomeInventory.ps1`
- Create: `tools/testing/Compare-SurefireOutcomeInventory.ps1`
- Create: `tools/testing/New-SurefirePartitionMap.ps1`
- Create: `tools/testing/Test-SurefireOutcomeInventory.ps1`
- Modify: `tools/testing/README.md`

**Interfaces:**

- Consumes: clean source-class inventories, one or more coordinator-owned
  Surefire report roots, and normalized baseline TSV files.
- Produces: ordinal-sorted outcome TSVs, absence-aware comparison reports, and a
  deterministic union partition map filtered per tree.

- [ ] **Step 1: Write failing fixture tests for the inventory contract**

  In `Test-SurefireOutcomeInventory.ps1`, generate temporary XML fixtures for
  pass, failure, error, skipped, disabled, parameterized identities, malformed
  XML, duplicate identity, missing selected class, parent-only class,
  candidate-only class, and an approved-removal record. Assert exact ordinal
  ordering and exact `PASS|FAILURE|ERROR|SKIPPED|ABSENT` values. Add separate
  fixtures for FAILURE-to-different-FAILURE and
  ERROR-to-different-ERROR signatures.

- [ ] **Step 2: Run the fixture tests and confirm RED**

  Run:

  ```bash
  pwsh -NoProfile -File tools/testing/Test-SurefireOutcomeInventory.ps1
  ```

  Expected: nonzero because the three implementation scripts are absent.

- [ ] **Step 3: Implement outcome export**

  `Export-SurefireOutcomeInventory.ps1` must parse XML with DTDs and external
  resolvers disabled, normalize `classname#name` identities, reject malformed
  or duplicate identities, require each selected executable class to emit at
  least one report/testcase, and write TSV columns:

  ```text
  identity	class	method	outcome	red_kind	exception_type	normalized_message	red_body_bytes	red_body_sha256	report
  ```

  For red outcomes, convert line endings to LF; replace canonical worktree,
  session-root, generated run-ID, and ISO-8601 timestamp tokens; encode UTF-8;
  and record the complete normalized byte length plus a streaming SHA-256 of
  the complete normalized body. Reject a red element lacking a deterministic
  signature.

  Empty helper classes are accepted only through a separate reviewed allowlist
  whose entries name a reason.

- [ ] **Step 4: Implement baseline comparison**

  `Compare-SurefireOutcomeInventory.ps1` must compare ordinal identities from
  either parent with the candidate, synthesize `ABSENT` for missing candidate
  identities, reject PASS→red/SKIPPED/ABSENT, flag changed failure/error kinds,
  and flag every same-kind red signature change for paired isolated reruns of
  the exact frozen parent and candidate under the same selector/environment,
  followed by owner/disposition classification. It writes a TSV with baseline,
  candidate, red signatures, classification, owner, and disposition fields. A
  reviewed-removal input may exempt only an explicitly named identity with a
  reason.

- [ ] **Step 5: Implement deterministic partitions**

  `New-SurefirePartitionMap.ps1` must take the three sorted source-class lists,
  construct their ordinal union, assign stable numbered slots of at most 75
  classes, and emit each slot with per-tree filtered selectors. Reject duplicate
  classes, an empty union, or a class assigned to zero/multiple union slots.

- [ ] **Step 6: Run fixture tests to GREEN**

  Run the test script. Expected: all fixture cases pass, including the false
  `ABSENT`, duplicate, malformed, and per-tree partition-filter cases.

- [ ] **Step 7: Document commands and schemas**

  Add exact export, compare, and partition examples to
  `tools/testing/README.md`, including the rule that a partial monolithic run is
  not a suite result.

- [ ] **Step 8: Commit the inventory tooling**

  Stage only Task 2 files and commit as:

  ```text
  test(integration): compare complete surefire inventories
  ```

---

### Task 2A: Add Nested-Aware Explicit-Source Inventory Fallback

**Files:**

- Modify: `tools/testing/Export-SurefireOutcomeInventory.ps1`
- Modify: `tools/testing/Test-SurefireOutcomeInventory.ps1`
- Modify: `tools/testing/README.md`

**Interfaces:**

- Consumes: a sorted inventory of exact top-level ordinary source classes and
  Surefire XML from an authenticated `surefire.includesFile` run.
- Produces: complete nested-aware testcase inventories without admitting
  duplicate discovery or unrelated classnames.

- [ ] **Step 1: Preserve the duplicate-discovery RED**

  Record both frozen-parent ordinary monoliths as inventory-invalid because the
  same nested testcase identity appears twice. Do not deduplicate their XML or
  reinterpret their counts. Record the non-certifying partition collision with
  unrelated Maven sessions; it is environmental evidence, not a child result.

- [ ] **Step 2: Add failing nested ownership fixtures**

  Require a selected top-level `Outer` plus nested-only XML to be accepted and
  cover `Outer`; accept combined outer/nested XML while preserving
  `Outer$Nested#method`; reject selector roots containing `$`, lookalike
  `Outerish$Nested`, duplicate nested identities, and a root with neither exact
  nor nested testcase coverage. Include a nested red-signature fixture.

- [ ] **Step 3: Implement exact nested ownership**

  Treat a testcase classname as selected only when it equals a selector root or
  begins with the exact `root + '$'` boundary. Preserve the actual classname in
  exported identity rows. Keep malformed XML, duplicate identity, extra class,
  missing root, helper allowlist, and red-signature rules unchanged.

- [ ] **Step 4: Document and review the fallback**

  Document construction of sorted exact generated-test-classes-relative
  selector patterns by mapping `com.openggf.Foo` to
  `com/openggf/Foo.java` after each tree's ordinary source includes/POM
  excludes. Require no `$` or wildcard patterns, ordinal bijection with selector
  roots, canonical absolute owner-only managed-scratch storage,
  SHA-256/count evidence, and inclusion in
  `OPENGGF_RUNTIME_INPUTS` before passing
  exactly one `-Dsurefire.includesFile=<absolute-path>`. Explicitly prohibit
  `-Dtest`, `surefire.includes`, another `surefire.includesFile`, or any other
  selector override. `-Dtest` is forbidden because it overrides POM
  includes/excludes.

- [ ] **Step 5: Prove effective-POM and real selector semantics**

  For frozen next, frozen develop, and later the merged candidate, parse the
  selector invocation's exact effective ordinary Surefire execution. Require
  no POM-configured `<includes>` or `<includesFile>` and no other competing
  selector channel. Record effective excludes/groups/fork settings, the
  project `surefire.argLine` property, and the separately composed execution
  `<argLine>`. Maven's effective-POM model does not materialize the CLI-bound
  `surefire.includesFile` mojo property, so authenticate that canonical path
  through the exact argv and `OPENGGF_RUNTIME_INPUTS`; reject inert
  `surefire.reuseForks` as an unapproved property. In each frozen parent, run a
  focused authenticated selector containing one exact top-level root with
  `@Nested` and one class named by an effective POM exclude. Require the nested
  testcase exactly once, no report for the excluded class, exact selector
  runtime-input hashing, and no unrelated report. Add a static/plugin fixture
  for FQCN-to-slash-path mapping and an invocation fixture rejecting duplicate
  or competing selector properties. Run all Task 2 fixtures and obtain
  independent review before Task 3 resumes.

---

### Task 3: Freeze Refs, Create Parent Worktrees, and Record Parent Baselines

**Files:**

- Create: managed scratch baseline directories and normalized TSV/partition
  evidence; do not commit raw session output.
- Modify in Task 11:
  `docs/architecture/validation/2026-08-26-develop-into-next-integration.md`

**Interfaces:**

- Consumes: Task 1 adapter, Task 2 inventory tools, frozen hashes, canonical ROMs.
- Produces: immutable parent baseline manifests, normalized outcome inventories,
  guard inventories, red sets, and an aggregation map.

- [ ] **Step 1: Fetch and enforce the four-ref drift gate**

  Run `git fetch origin`. At the initial snapshot gate, record exact equality:

  ```text
  next == origin/next == 84d9a3761f618035dd1caa40a3d5fc72a1019693
  develop == origin/develop == e3f390e9fe381b89cc54a2399b5a49843ba044e4
  ```

  Freeze detached baseline worktrees at these exact hashes. At every later
  gate, require `next == origin/next == 84d9a3761` and require `e3f390e9f` to
  remain an ancestor of both current develop refs. Also require clean main and
  integration worktrees. Target drift or loss of snapshot ancestry requires an
  amendment. Record every later develop descendant as a follow-up source-line
  delta without refreezing or rebaselining this merge; keep the detached parent
  baseline at `e3f390e9f`. Non-linear post-snapshot descendants do not block
  detached baseline work, but must be reconciled into one pushed develop tip
  and verified separately before final delivery.
  Evidence captured against `f1b82774d` is historical and must not be reused as
  the `e3f390e9f` develop parent baseline. All evidence captured at
  `97bc177ee` is historical. Frozen-next evidence must also be
  repeated through the Task 1C harness pin even though the authenticated
  wrapper/coordinator bytes are unchanged.

- [ ] **Step 2: Create managed scratch and detached baseline worktrees**

  Use `agent-scratch new next-develop-integration-20260826` for evidence. Create
  separate cowtrees detached at each frozen parent. The frozen-develop cowtree
  is also the authenticated harness worktree. Verify detached clean status,
  install the tracked develop hook path without changing source, and ensure
  frozen-next starts with no `target`.

- [ ] **Step 3: Verify JDK and ROM identities**

  Record `mvn -v` showing Java 21. Use `cksum -a crc32b` and `sha1sum` on the
  discovered S1 REV01, S2 REV01, and locked-on S3K files. Pass their canonical
  paths through `sonic1.rom.path`, `sonic2.rom.path`, and `s3k.rom.path`.
  Write the verified canonical paths and both hashes to a task-owned managed-
  scratch TSV with mode `0600`; never copy ROM bytes and never shell-source the
  file. Parse its `game`, `canonical_path`, `crc32`, and `sha1` columns into
  `OPENGGF_S1_ROM`, `OPENGGF_S2_ROM`, and `OPENGGF_S3K_ROM`. Every later
  independent command or agent reloads the TSV, then immediately checks that
  each value is non-empty, names a regular file, and still matches both hashes.

- [ ] **Step 4: Read the measurement-hazard briefing**

  Read `docs/agent-workflow/briefing-trace-rounds.md` completely before
  interpreting or reporting any test count. Record that this gate was completed
  in the integration validation report.

- [ ] **Step 5: Self-test the pinned coordinator and adapter**

  In the detached frozen-develop harness worktree, byte-compare the wrapper and
  coordinator source with
  `git show e3f390e9fe381b89cc54a2399b5a49843ba044e4:tools/testing/test-session.sh`
  and
  `git show e3f390e9fe381b89cc54a2399b5a49843ba044e4:tools/testing/TestSessionCoordinator.java`,
  then hash them together with the launcher, exclude, and adapter. Run the coordinator harness
  and the repaired Task 1/1A self-test, including
  wrong-wrapper/wrong-coordinator rejection, private bind-mount isolation,
  exact historical temp semantics, and the generated-report normalization
  matrix.
  Expected: green; otherwise no parent baseline may start.

- [ ] **Step 6: Run frozen-develop ordinary and guard baselines**

  In the detached develop worktree, run separate quiet sessions:

  ```bash
  tools/testing/test-session.sh -- mvn \
    -Dsonic1.rom.path="$OPENGGF_S1_ROM" \
    -Dsonic2.rom.path="$OPENGGF_S2_ROM" \
    -Ds3k.rom.path="$OPENGGF_S3K_ROM" test
  tools/testing/test-session.sh -- mvn -Dmse=off -Pguards test -B
  ```

  Record both run IDs, manifests, logs, terminal states, and inventories. A
  terminal red parent baseline is valid evidence: classify every failure/error
  and continue inventory construction. A missing terminal marker, invalid
  source identity, missing or failed launcher/cleanup authentication, or
  incomplete inventory is not valid evidence and triggers the partition
  fallback in Step 8. Never accept the coordinator manifest without the
  launcher's authenticated terminal and cleanup records.

- [ ] **Step 7: Run frozen-next ordinary and explicit guard baselines**

  Generate the frozen-next guard selector from exact source patterns
  `Test*Guard*`, `TestNo*`, `TestArchUnit*`, and
  `TestAudioPresentationBoundary`; expected current inventory is 67 classes.
  Run ordinary and explicit-guard sessions through the Task 1 launcher using the
  pinned develop wrapper. Use the same ROM paths and no `clean` lifecycle.
  Require the adapter to archive and restore any authorized rewind-probe report
  rewrite before coordinator finalization. Reject any mismatch between the 67
  selected classes and produced reports, any other source mutation, or temp XML
  paths/mount identities that do not prove historical path semantics backed by
  the exact coordinator session root.

- [ ] **Step 8: Export and validate both parent inventories**

  Use Task 2 tooling to export normalized TSVs. Reject malformed/duplicate
  identities and selected classes without reports. If a monolithic session is
  duplicate-invalid, retain it and run one Task 2A authenticated explicit-source
  session using that tree's complete top-level ordinary selector. Require exact
  root/descendant coverage, no duplicate identity, and no unselected classname.
  If that session is incomplete or OOMs, retain it, create the deterministic
  union partition map, run all filtered partitions in separate sessions, and
  require exact aggregate class coverage before calling the baseline complete.
  Any partition overlapped by unrelated Maven is environmental-invalid and must
  be rerun; it contributes no inventory row or suite count.

- [ ] **Step 9: Preserve evidence paths**

  Store hashes, commands, run IDs, manifest/log paths, normalized TSVs,
  partitions, and environment limitations in managed scratch. Do not copy raw
  reports or ROMs into the repository.

---

### Task 4: Dispatch Parallel Conflict Analysis and Publish the Ledger

**Files:**

- Create: `docs/architecture/audits/2026-08-26-develop-next-conflict-ledger.tsv`
- Create: `docs/architecture/audits/2026-08-26-develop-next-conflict-synthesis.md`

**Interfaces:**

- Consumes: exact merge-tree conflict list, both parent trees/history, prior
  2026-08-10 integration plan/validation, frozen baselines.
- Produces: one row per conflict with owner, authority, resolution, tests,
  dependency order, and reviewer status.

- [ ] **Step 1: Regenerate the exact conflict set**

  Run an isolated recursive `git merge-tree --write-tree --name-only` for the
  final pre-merge integration head versus frozen develop. Expected: 77 paths:
  the original 76 plus the intentional Task 1 documentation conflict at
  `tools/testing/README.md`. Record any further change and update the ledger
  count before proceeding.

- [ ] **Step 2: Dispatch three read-only analysis agents in parallel**

  Assign separate agents:

  1. audio plus shared/core runtime;
  2. S1/S2/S3K plus level/object/ring/movement;
  3. build/hooks/CI, tests/guards, release docs, and baseline comparison.

  Require each to cite parent commits, symbols, current owners, preservation
  risks, recommended per-path resolution, and focused tests. Agents do not edit
  files or stage the merge index.

- [ ] **Step 3: Build the file-level ledger**

  Write TSV columns:

  ```text
  path	family	owner	develop_authority	next_contract	resolution	tests	depends_on	reviewer	status
  ```

  Include every conflict exactly once. Reject duplicate/missing paths and any
  resolution containing a blanket side choice without a path-specific reason.

- [ ] **Step 4: Resolve analyst disagreements locally**

  Inspect both parent versions, merge base, relevant commits, disassembly/ROM
  source, and focused tests. Record the decided owner and reasoning in the
  synthesis document rather than voting between agents.

- [ ] **Step 5: Independently review the ledger**

  A fresh subagent checks path completeness, authority evidence, dependency
  ordering, overlapping ownership, and test adequacy. Fix every valid issue and
  repeat until the reviewer reports no blocker.

- [ ] **Step 6: Commit the green ledger**

  Stage the two audit artifacts and commit as:

  ```text
  docs(integration): map develop next conflict ownership
  ```

---

### Task 5: Start the Direct Merge and Resolve Build, Policy, and Release Contracts

**Files:**

- Resolve: `.githooks/validate-policy.sh`
- Resolve: `.github/workflows/ci.yml`
- Resolve: `.github/workflows/release.yml`
- Resolve: `pom.xml`
- Resolve: `CHANGELOG.md`
- Resolve: `README.md`
- Resolve: `docs/S3K_KNOWN_DISCREPANCIES.md`
- Resolve: `docs/status/known-discrepancies.md`
- Resolve: `docs/status/rewind-round-trip-gaps.md`
- Resolve: `docs/status/s3k-known-bugs.md`

**Interfaces:**

- Consumes: green conflict ledger, frozen parent baselines.
- Produces: an unresolved merge index with the build/policy/documentation family
  resolved and current test-session/0.7 contracts intact.

- [ ] **Step 1: Recheck refs and create the merge index**

  Verify the four frozen refs again. Run:

  ```bash
  git merge --no-ff --no-commit e3f390e9fe381b89cc54a2399b5a49843ba044e4
  ```

  Expected: conflicts; do not commit or abort.

- [ ] **Step 2: Resolve hook, workflow, and Maven contracts**

  Adopt develop's explicit hook installer, quiet session coordinator, JDK 21
  enforcement, session-owned output properties, separate guards profile, and
  current release packaging. Preserve next-only Mod API/native/universal,
  editor, Time Attack/multiplayer, and 0.7 workflow gates. Ensure Maven no longer
  installs hooks during lifecycle execution.

- [ ] **Step 3: Resolve changelog and roadmap prose**

  Preserve develop's split immutable historical release files and 0.6 current
  release index while retaining next's 0.7 roadmap/status links. Treat the
  modify/delete `rewind-round-trip-gaps.md` conflict by moving any still-current
  0.7 facts to the owning status/validation artifact, not by resurrecting stale
  inventory blindly.

- [ ] **Step 4: Validate resolved paths against the ledger**

  Stage only these ten paths. For each, mark the ledger row `resolved` and record
  the exact chosen contract. `git diff --check` must pass for staged content.

---

### Task 6: Resolve Audio Conflicts

**Files:**

- Resolve: `src/main/java/com/openggf/audio/AbstractSmpsAudioBackend.java`
- Resolve: `src/main/java/com/openggf/audio/AudioBackend.java`
- Resolve: `src/main/java/com/openggf/audio/AudioManager.java`
- Resolve:
  `src/main/java/com/openggf/audio/presentation/AudioPresentationCommand.java`
- Resolve:
  `src/main/java/com/openggf/audio/presentation/AudioPresentationSourceFactory.java`
- Resolve:
  `src/main/java/com/openggf/audio/presentation/PresentationVoiceSnapshot.java`
- Resolve: `src/main/java/com/openggf/audio/rewind/AudioCommand.java`
- Resolve: `src/main/java/com/openggf/audio/smps/AbstractSmpsData.java`
- Resolve: `src/main/java/com/openggf/audio/smps/DacData.java`

**Interfaces:**

- Consumes: current develop audio policy/presentation/rewind owners and next's
  Mod API/1-up/powered-form/audio contracts.
- Produces: one coherent audio API used by all auto-merged callers.

- [ ] **Step 1: Resolve interfaces before implementations**

  Reconcile `AudioBackend`, presentation command/value records, source factory,
  rewind command, and SMPS/DAC data shapes first. Preserve source/bus identity,
  repeated S3K SFX service order, cross-game override fade policy, standalone
  sound-test output, and next's public Mod API surface.

- [ ] **Step 2: Resolve manager/backend implementations**

  Reconcile `AbstractSmpsAudioBackend` and `AudioManager` against the decided
  interfaces. Do not restore superseded direct singleton ownership or weaken
  rewind/presentation boundaries.

- [ ] **Step 3: Compile the resolved audio family**

  Finish resolving any compile errors in auto-merged audio callers attributable
  to these interface decisions. Stage only ledger-owned audio paths and directly
  necessary caller adaptations, recording each added path in the ledger.

- [ ] **Step 4: Run focused audio verification after the whole index resolves**

  Queue this selector for Task 10:

  ```text
  TestAudioPresentationArchitectureGuard,TestAudioPresentationBoundary,
  TestLiveRewindManagerAudioCleanup,TestS3kVoiceResolution,
  TestSonic3kSmpsMetaCommandOperands
  ```

  Mark rows `resolved-pending-test`.

---

### Task 7: Resolve Shared Runtime, Level, Object, Ring, and Movement Conflicts

**Files:**

- Resolve: `src/main/java/com/openggf/GameLoop.java`
- Resolve: `src/main/java/com/openggf/camera/Camera.java`
- Resolve: `src/main/java/com/openggf/game/AbstractLevelEventManager.java`
- Resolve: `src/main/java/com/openggf/game/rules/PowerUpRules.java`
- Resolve: `src/main/java/com/openggf/level/BigRingReturnState.java`
- Resolve: `src/main/java/com/openggf/level/LevelData.java`
- Resolve: `src/main/java/com/openggf/level/LevelManager.java`
- Resolve: `src/main/java/com/openggf/level/LevelTransitionCoordinator.java`
- Resolve: `src/main/java/com/openggf/level/objects/ObjectManager.java`
- Resolve: `src/main/java/com/openggf/level/objects/ObjectSolidContactController.java`
- Resolve: `src/main/java/com/openggf/level/objects/ObjectTouchResponseController.java`
- Resolve: `src/main/java/com/openggf/level/objects/PersistentRespawnState.java`
- Resolve: `src/main/java/com/openggf/level/rings/LostRingObjectInstance.java`
- Resolve: `src/main/java/com/openggf/level/rings/RingManager.java`
- Resolve: `src/main/java/com/openggf/sprites/animation/SpriteAnimationScript.java`
- Resolve: `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`
- Resolve: `src/main/java/com/openggf/sprites/managers/SpriteManager.java`
- Resolve: `src/main/java/com/openggf/trace/replay/runs/TraceRunPresentationClosure.java`

**Interfaces:**

- Consumes: resolved audio interfaces, develop lifecycle/timing/rewind owners,
  next mod/team/viewport contracts.
- Produces: coherent frame, transition, object-contact, respawn, ring, movement,
  animation, and trace-presentation lifecycles.

- [ ] **Step 1: Resolve frame and transition ownership**

  Reconcile `GameLoop`, camera, abstract events, level data/manager/transition,
  Big Ring return, and trace presentation. Preserve render-copy publication,
  production-raised transitions, seamless carry rules, checkpoint/save state,
  hardware scheduling authority, and continuous run-chain ownership.

- [ ] **Step 2: Resolve object/contact/respawn/ring ownership**

  Retain develop's current `ObjectManager` execution clocks, ENEMY per-frame
  touch polling, solid/touch controller separation, persistent respawn semantics,
  and ring/lost-ring ROM cadence. Preserve next's arbitrary-sidekick, mod-zone,
  widescreen, super-emerald, and rewind identities through semantic providers.

- [ ] **Step 3: Resolve movement and animation ownership**

  Reconcile sprite animation/movement/manager code using ROM centre coordinates,
  native clock names, current game rules, exact solid/balance contracts, and
  sidekick/main-player participation. Do not reintroduce `getInstance()` in
  objects or direct layout mutation.

- [ ] **Step 4: Stage and ledger-check the family**

  Stage the 18 named paths plus only evidence-backed caller adaptations. Scan
  for conflict markers, game/zone carve-outs, direct map mutation, and object
  singleton calls. Mark rows `resolved-pending-test`.

---

### Task 8: Resolve S1, S2, and S3K Runtime Conflicts

**Files:**

- Resolve:
  `src/main/java/com/openggf/game/sonic1/events/Sonic1FixedTitleCardManager.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic1/events/Sonic1LevelEventManager.java`
- Resolve: `src/main/java/com/openggf/game/sonic2/Sonic2Level.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic2/objects/BridgeObjectInstance.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic2/titlecard/TitleCardManager.java`
- Resolve: `src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/Sonic3kZoneFeatureProvider.java`
- Resolve: `src/main/java/com/openggf/game/sonic3k/Sonic3kZoneRegistry.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/audio/smps/Sonic3kCoordFlagHandler.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kConstants.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kZoneIds.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/dataselect/S3kDataSelectProfile.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/events/Sonic3kLBZEvents.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/objects/AizEndBossInstance.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/objects/AutoSpinObjectInstance.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/objects/HCZBreakableBarObjectInstance.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/objects/HczMinibossInstance.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/objects/S3kHiddenMonitorInstance.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kInvisibleBlockObjectInstance.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kSSEntryFlashObjectInstance.java`
- Resolve:
  `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kSSEntryRingObjectInstance.java`

**Interfaces:**

- Consumes: Tasks 6-7 shared owners.
- Produces: game-specific implementations preserving both parent route and 0.7
  feature contracts.

- [ ] **Step 1: Resolve S1 title-card/event conflicts**

  Reconcile `Sonic1FixedTitleCardManager` and `Sonic1LevelEventManager` against
  develop's current title-card/run-chain timing while retaining next viewport
  and mod-donation contracts.

- [ ] **Step 2: Resolve S2 level, bridge, and title-card conflicts**

  Preserve develop's current WFZ Tornado lifecycle, complete-run boundary fixes,
  object cadence, and title-card ownership while retaining next competition,
  mod-zone, viewport, and team capabilities.

- [ ] **Step 3: Resolve S3K module/registry/constants/data-select conflicts**

  Reconcile `Sonic3kGameModule`, level events, zone feature provider/registry,
  coordinate flags, constants, zone IDs, and data-select profile. Respect S3KL
  versus SKL ownership, FBZ's split predicates, locked-on ROM addresses, current
  policy placement, launch-transition ratchet, and Mod API 0.7 reachability.

- [ ] **Step 4: Resolve S3K event/object conflicts**

  Reconcile LBZ events; AIZ end boss; auto-spin; HCZ bar/miniboss; hidden and
  invisible monitors/blocks; and special-stage entry flash/ring. Preserve
  develop's AIZ timing/rewind/fire/waterfall fixes and next's super-emerald,
  Big Arm/late-route, arbitrary-sidekick, viewport, and rewind graph contracts.
  Use shipped `FixBugs = 0` behavior with comments at any encountered conditional.

- [ ] **Step 5: Stage and ledger-check the family**

  Stage only the 22 named game-specific paths plus recorded direct adaptations.
  Verify ROM assets still load through ROM owners and no disassembly file is
  opened by production or executable tests. Mark rows `resolved-pending-test`.

---

### Task 9: Resolve Test and Guard Conflicts

**Files:**

- Resolve: `src/test/java/com/openggf/TestGameLoop.java`
- Resolve:
  `src/test/java/com/openggf/game/rewind/TestRewindArchitectureGuard.java`
- Resolve:
  `src/test/java/com/openggf/game/rewind/TestRewindBenchmarkSizeEstimator.java`
- Resolve:
  `src/test/java/com/openggf/game/sonic2/dataselect/TestS2DataSelectProfile.java`
- Resolve:
  `src/test/java/com/openggf/game/sonic2/objects/TestTornadoObjectInstance.java`
- Resolve:
  `src/test/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageBootstrapCadenceTest.java`
- Resolve:
  `src/test/java/com/openggf/game/sonic3k/TestS3kRuntimeStateReadGuard.java`
- Resolve:
  `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kAIZEvents.java`
- Resolve:
  `src/test/java/com/openggf/game/sonic3k/objects/TestAizVineHandleLogic.java`
- Resolve:
  `src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kSSEntryRingFormation.java`
- Resolve:
  `src/test/java/com/openggf/level/TestLevelSeamlessTransitionExecutor.java`
- Resolve:
  `src/test/java/com/openggf/level/objects/TestObjectPhysicsStandardizationGuard.java`
- Resolve:
  `src/test/java/com/openggf/level/objects/TestSidekickTouchHurtAnimationOwnership.java`
- Resolve: `src/test/java/com/openggf/tests/TestArchitecturalSourceGuard.java`
- Resolve: `src/test/java/com/openggf/tests/TestBuildToolingGuard.java`
- Resolve: `src/test/java/com/openggf/tests/TestTitleCardObjectExecution.java`
- Resolve: `src/test/java/com/openggf/tests/trace/runs/AbstractRunChainTest.java`

**Interfaces:**

- Consumes: final production API shapes from Tasks 6-8 and both parent test
  contracts.
- Produces: tests that exercise merged production owners without weakening,
  deleting, skipping, or reading local disassembly trees.

- [ ] **Step 1: Resolve behavior tests against production contracts**

  Reconcile game-loop, data-select, Tornado, S2 special-stage bootstrap, S3K AIZ,
  vine, special-stage ring, seamless transition, and title-card tests. Preserve
  both parent assertions when they cover independent behavior; update harness
  setup only when the merged production boundary changed legitimately.

- [ ] **Step 2: Resolve architecture and coverage guards**

  Reconcile rewind architecture/size, S3K runtime state, object physics,
  architectural source, build tooling, and run-chain guards. Do not expand a
  baseline or remove an assertion merely to accept merged production.

- [ ] **Step 3: Decide modify/delete test conflicts explicitly**

  For `TestSidekickTouchHurtAnimationOwnership.java`, preserve its contract in
  the current owning test/guard if develop intentionally consolidated it; retain
  the file only if the identity still owns unique coverage. Apply the same rule
  to any test whose parent path was deleted or renamed, and record the mapping so
  Task 2 comparison does not treat legitimate identity migration as silent loss.

- [ ] **Step 4: Finish the index**

  Require `git diff --name-only --diff-filter=U` to be empty and scan the entire
  staged tree for conflict markers. Confirm all 77 ledger rows are resolved and
  every extra edited path is attributed to a direct compile/API dependency.

---

### Task 10: Compile, Run Focused Conflict Tests, Review, and Commit the Merge

**Files:**

- Modify only merge-attributable source/test paths exposed by compile or focused
  verification.
- Update conflict ledger statuses and synthesis findings.

**Interfaces:**

- Consumes: fully resolved merge index.
- Produces: exact two-parent merge commit plus green focused conflict-family
  evidence.

- [ ] **Step 1: Run a certifying compile/package check**

  Run through the merged in-tree wrapper with all three verified ROM properties
  from `OPENGGF_S1_ROM`, `OPENGGF_S2_ROM`, and `OPENGGF_S3K_ROM`.
  Diagnose only bounded log excerpts. Fix compile errors by returning ownership
  to the applicable Task 6-9 family and update the ledger.

- [ ] **Step 2: Run focused selectors by family**

  Use separate wrapper sessions for audio; core/transition/object/movement;
  S1/S2; S3K; and tests/guards. Include every conflict test named in the ledger,
  `TestBuildToolingGuard`, `TestArchitecturalSourceGuard`, rewind coverage
  guards, Mod API signature/policy tests, and the no-disassembly source guard.
  Require zero missing selected class and record each run ID/manifest/log.

- [ ] **Step 3: Dispatch independent resolution review**

  Give a fresh subagent the frozen parents, green ledger, staged merge diff,
  focused evidence, and design/plan. Require blockers first: lost parent
  behavior, wrong runtime owner, weakened guard, undocumented path, asset-policy
  violation, and missing focused test. Fix every valid finding and repeat review
  until green.

- [ ] **Step 4: Verify exact merge topology and commit**

  Immediately before commit, require `MERGE_HEAD` equals frozen develop and the
  current first parent descends by uninterrupted first-parent history from
  frozen next. Commit the merge without bypassing hooks. Record the resulting
  parents and ensure the second parent is exactly `e3f390e9f`.

- [ ] **Step 5: Rerun focused selectors on the committed merge**

  Repeat the complete conflict-family selector matrix on the commit rather than
  relying only on pre-commit index evidence. Any new failure returns to a normal
  follow-up fix commit with focused red/green proof.

---

### Task 11: Refresh the Mod API Candidate and 0.7 Integration Evidence

**Files:**

- Modify: `src/test/resources/mods/mod-api-signatures-0.7.txt`
- Modify as measured: `README.md`
- Modify: `docs/project/v0.7-roadmap.md`
- Create: `docs/architecture/validation/2026-08-26-develop-into-next-integration.md`
- Modify when obligated: `docs/status/trace-frontier-log.md`
- Modify when facts change: current known-discrepancy/status files.

**Interfaces:**

- Consumes: committed merged production surface, parent baselines, focused
  evidence.
- Produces: current Mod API candidate pin and honest Milestone 0 evidence.

- [ ] **Step 1: Regenerate the Mod API 0.7 snapshot**

  Follow `docs/architecture/mod-api-compatibility.md` exactly using the merged
  session-owned build/classpath. Review every signature delta against the
  conflict ledger and public API annotations. Do not publish a patch-version
  baseline; this remains the 0.7 candidate.

- [ ] **Step 2: Run Mod API and sample gates**

  Run the signature surface, hook policy, SDK/Javadoc, mod validator, maintained
  sample integration, editor, Time Attack, and multiplayer focused selectors
  through separate wrapper sessions where environment needs differ.

- [ ] **Step 3: Write the integration validation report**

  Record exact parent/merge hashes, commands, JDK/ROM hashes, run IDs, manifest
  and log paths, normalized outcome counts, adapter/coordinator hashes,
  conflict-family results, Mod API delta, known environment limitations, and an
  owner plus 0.7 release disposition for every remaining failure/error.

- [ ] **Step 4: Reconcile README, roadmap, and trace/status evidence**

  Replace stale claims only with measured results. Keep 0.6 release records and
  0.7 candidate status distinct. Update the frontier log if a full trace sweep
  moved/regressed a frontier or selected a new target; include command, commit,
  pass/fail, error count, and first-error frame/field.

- [ ] **Step 5: Review and commit Milestone 0 artifacts**

  Independently review public claims, failure ownership, Mod API snapshot, and
  documentation obligations. Fix until green, then commit with accurate
  changelog/guide/discrepancy/configuration trailers.

---

### Task 12: Run Full Merged Certification and Exact Parent Comparison

**Files:**

- Create in managed scratch: merged outcome inventory, comparison TSVs,
  partition/aggregation manifest when required.
- Update: integration validation report with final evidence.

**Interfaces:**

- Consumes: committed merge plus Task 11 evidence and both parent inventories.
- Produces: complete ordinary/guard certification and absence-aware parent
  regression comparison.

- [ ] **Step 1: Run the monolithic ordinary suite**

  Use `tools/testing/test-session.sh` with all three verified ROM properties.
  Require both markers and a terminal manifest. Inspect bounded logs for compile
  failure, Surefire startup, OOM, or premature termination before interpreting
  reports.

- [ ] **Step 2: Export inventory or execute the partition fallback**

  If the full run is complete and duplicate-free, export it. If Maven completes
  but the inventory is duplicate-invalid, retain that run, generate and
  authenticate the merged tree's exact top-level ordinary slash-path selector,
  add it to `OPENGGF_RUNTIME_INPUTS`, and run one session with exactly one
  `-Dsurefire.includesFile=<selector>` property. First repeat Task 2A's
  effective-POM and real nested/excluded selector gate against the merged tree.
  Require exact root-or-`root + '$'` coverage, no extra classname, and no
  duplicate identity. If the original or explicit-source run OOMs or is
  incomplete, retain the failed manifests, generate the deterministic union
  partition map from the three trees, run every merged filtered slot in its own
  wrapper session, and require exactly-once class coverage before aggregation.

- [ ] **Step 3: Run the fresh structural guards**

  Run:

  ```bash
  tools/testing/test-session.sh -- mvn -Dmse=off -Pguards test -B
  ```

  Require the guard profile inventory test, architecture guards, no-object-
  singleton guard, layout mutation guard, rewind coverage, and trace authority
  guards to execute with no missing class.

- [ ] **Step 4: Compare against both parents**

  Run Task 2 comparison twice: frozen develop→merged and frozen next→merged.
  Reject any parent PASS becoming failure/error/skipped/`ABSENT`, any unexplained
  failure-kind change, any duplicate identity, and any selected class without a
  report. Rerun environment/order-sensitive differences in isolated matching
  sessions before classification.

- [ ] **Step 5: Resolve every merge-attributable regression**

  Route each regression to its ledger owner, use systematic debugging and TDD,
  amend design and plan if an assumption changes, independently re-review both
  artifacts, then rerun the affected focused and complete comparisons.

- [ ] **Step 6: Update and commit final validation evidence**

  Record only complete suite/partition aggregates, never partial totals. Commit
  the final validation update with accurate trailers after independent review.

---

### Task 12A: Reconcile Post-Snapshot Develop Descendants

**Files:**

- Main workspace `develop`; no integration-parent or baseline mutation.
- Modify: `README.md` with the merge-policy-required concise release/change-log
  summary for this post-snapshot reconciliation.
- Update the final validation artifact with the follow-up evidence.

**Interfaces:**

- Consumes: frozen snapshot `e3f390e9f`, local descendant
  `75f64e53c379e4ac65fdde1d3c37fc2f52701711`, fetched remote descendant
  `ed9f55941343093e226b5ad55d80ece7a9417267`, and the completed integration
  evidence.
- Produces: one tested and pushed `develop` follow-up tip that still contains
  `e3f390e9f`; it is not substituted as this integration merge's second parent.

- [ ] **Step 1: Fetch, classify, and protect the main workspace**

  In the clean main workspace on `develop`, fetch origin and resolve the exact
  local and remote tips. Require both to contain `e3f390e9f`; never switch the
  main workspace, rewrite history, discard user changes, or force-push.

- [ ] **Step 2: Record the updated-origin baseline**

  In a detached worktree at the exact fetched `origin/develop`, run the ordinary
  full suite, fresh `-Pguards` suite, and focused tests covering the descendant
  changes through quiet `tools/testing/test-session.sh` sessions. Record exact
  failures, errors, markers, manifests, and logs as the comparison baseline.

- [ ] **Step 3: Merge the exact fetched source tip**

  Merge the fetched `origin/develop` commit into clean main-workspace `develop`
  without switching branches or bypassing hooks. Reconcile conflicts narrowly;
  before committing, stage a concise `README.md` release/change-log summary as
  required by the non-master-into-develop merge policy. Keep this commit outside
  the frozen integration branch's second-parent and parent-baseline identities.

- [ ] **Step 4: Verify no follow-up regression**

  Run the same ordinary full suite and fresh guards on the reconciled main tip,
  plus the relevant focused tests for both descendant lines. Compare against
  the updated-origin baseline: no baseline pass may become red, and no baseline
  failure may worsen or change due to the reconciliation. Classify the staged
  README delta as intentional reconciliation-only documentation and record it
  in the final validation evidence.

- [ ] **Step 5: Push and verify the follow-up source tip**

  Push only main-workspace `develop`, fetch again, and require local develop and
  `origin/develop` to equal the exact reconciliation commit with `e3f390e9f` as
  an ancestor. Record the pushed commit and all verification evidence. Do not
  change the frozen integration parent or rerun its parent baselines solely
  because this follow-up tip exists.

---

### Task 13: End-to-End Review and Human Handoff

**Files:**

- Create: `docs/architecture/audits/2026-08-26-develop-next-end-to-end-review.md`
- Update if findings require: design, plan, source, tests, or validation.

**Interfaces:**

- Consumes: all requirements, green ledger, merge history, parent/merged
  inventories, documentation, and policy evidence.
- Produces: blocker-first end-to-end review and a human promotion checklist.

- [ ] **Step 1: Run static and policy checks**

  Run `git diff --check`, conflict-marker scans, executable-disassembly scans,
  uncompressed-trace checks, status/untracked classification, and the exact
  range policy:

  ```bash
  .githooks/run-policy ci-push \
    84d9a3761f618035dd1caa40a3d5fc72a1019693 \
    HEAD feature/ai-next-develop-integration-20260826
  ```

- [ ] **Step 2: Dispatch independent end-to-end reviewers**

  Assign one reviewer to runtime/ROM accuracy and one to tests/build/docs/API.
  Require blockers, non-blocking risks, missing parent contracts, inventory
  gaps, policy failures, and promotion readiness. Reviewers do not edit.

- [ ] **Step 3: Fix findings and repeat until green**

  Route findings to owners. If assumptions or sequencing change, amend and
  independently re-green both design and plan before continuing. Rerun all
  affected focused and full evidence.

- [ ] **Step 4: Verify final drift and topology**

  Fetch origin and require `next`/`origin/next` still equal the frozen target.
  Verify the merge second parent is exact frozen snapshot `e3f390e9f`, both
  current develop refs retain that commit as an ancestor, first-parent ancestry
  reaches frozen next, the branch is clean, and `next` itself is unchanged.
  Record newer develop descendants as later follow-up source-line deltas; do
  not absorb them into or rebaseline this merge, and do not imply that `next`
  itself can fast-forward to them. Before final delivery, reconcile any
  divergent post-snapshot source lines into one pushed develop tip and verify
  that follow-up separately.

- [ ] **Step 5: Write the end-to-end review artifact**

  Record findings/fixes, residual risks, exact test evidence, documentation
  obligations, branch/commit topology, and the human checklist for updating and
  pushing `next`.

- [ ] **Step 6: Stop for explicit human review**

  Present the local integration branch and commits, conflict/design summary,
  test commands/results, upstream reconciliation, residual risks, and exact
  proposed `next` update/push operation. Do not update, push, remove the
  integration worktree, or delete its branch before human confirmation.

## Self-Review

- Spec coverage: every design requirement maps to Tasks 1-13, including the
  frozen-parent adapter, parent inventories, merge topology, conflict ledger,
  Milestone 0 artifacts, OOM fallback, drift gates, independent reviews, and
  stop-before-promotion boundary.
- Placeholder scan: the plan contains no deferred implementation marker or
  angle-bracket command token; verified ROM paths flow through the three named
  shell variables established in Task 3.
- Interface consistency: all baseline and merged runs feed the same normalized
  TSV schema; partition maps use one union with per-tree filters; ledger owners
  persist through conflict resolution, regression repair, and final review.
