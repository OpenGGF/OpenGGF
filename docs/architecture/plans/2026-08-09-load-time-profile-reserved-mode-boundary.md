# Load-time Reserved-mode Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:test-driven-development` to execute this plan. Steps use checkbox
> (`- [ ]`) syntax for tracking.

**Goal:** Preserve `FAST` and `REALISTIC` as unfinished aliases while making
their diagnostics and current documentation accurately describe the optional
hardware-admission profile boundary.

**Architecture:** `LoadTimeProfileFactory` continues to select only the
`LoadTimeProfile` consumed by `HardwareTimingService`. S1/S2 PLC and dynamic-art
owners remain independent and receive no new delay. The only production change
is a scoped warning; the rest of the work is contract coverage and evidence-led
documentation.

**Tech Stack:** Java 21, JUnit Jupiter, Maven Surefire, Markdown, repository
commit-policy hooks.

## Global Constraints

- Do not add a service-frame value, eligible boundary, queue kind, profile
  record, or timing-authority path.
- Preserve `FAST -> LoadTimeProfile.IMMEDIATE` and `REALISTIC -> supplied
  profile` object identity.
- Preserve one warning for every reserved-mode factory resolution; do not add
  global or session warning suppression.
- Keep S1 and S2 Nemesis PLC cadence in their game-owned services, keep S2 DPLC
  in `DynamicArtLifecycleService`, and keep S3K direct/module Kosinski distinct.
- Preserve that S1/S2 default module resolution returns
  `LoadTimeProfile.IMMEDIATE` for all four values because their supplied profile
  is immediate; that resolution does not retime their non-submitted PLC/DPLC
  lifecycles.
- Trace comparison data remains comparison-only; recorded timing may release
  only matching prepared production work.
- Run Maven with JDK 21 and never bypass the commit hook.

---

### Task 1: Make the reserved-mode diagnostic precise through TDD

**Files:**

- Modify: `src/test/java/com/openggf/game/timing/TestLoadTimeProfileContract.java`
- Modify: `src/main/java/com/openggf/game/timing/LoadTimeProfileFactory.java`

**Interfaces:**

- Consumes: `LoadTimeProfileFactory.resolve(LoadTimeSimulationMode,
  LoadTimeProfile, Consumer<String>)`.
- Produces: unchanged profile identity and warning frequency, with warnings that
  explicitly name a missing mode-specific hardware-admission profile.

- [x] **Step 1: Replace the weak warning-count test with the behavior contract**

  Rename `reservedModesWarnAndUseTheirSpecifiedFallbacks` to
  `reservedModesWarnOnEveryResolutionAndExposeScopedFallbacks`. Call `resolve`
  twice for each reserved mode, assert both `FAST` results are the singleton
  immediate profile, assert both `REALISTIC` results are the supplied profile,
  and assert the four hand-derived diagnostics in call order:

  ```java
  assertEquals(List.of(
          "FAST load-time simulation is reserved; no independent FAST "
                  + "hardware-admission profile exists, using NONE",
          "FAST load-time simulation is reserved; no independent FAST "
                  + "hardware-admission profile exists, using NONE",
          "REALISTIC load-time simulation is reserved; no independent REALISTIC "
                  + "hardware-admission profile exists, using PROFILED",
          "REALISTIC load-time simulation is reserved; no independent REALISTIC "
                  + "hardware-admission profile exists, using PROFILED"),
          warnings);
  ```

  The production break caught by this test is a warning that hides the resolver
  scope, the wrong fallback identity, or warning suppression across repeated
  resolutions.

- [x] **Step 2: Run the new test and verify the intended red state**

  Run:

  ```bash
  mvn -Dmse=off \
    "-Dtest=com.openggf.game.timing.TestLoadTimeProfileContract#reservedModesWarnOnEveryResolutionAndExposeScopedFallbacks" \
    test
  ```

  Expected: one assertion failure showing the old `is not implemented; using`
  warning instead of the new scoped `is reserved; no independent ...
  hardware-admission profile exists` warning. A compilation error or identity
  failure is not the accepted red state.

- [x] **Step 3: Make the minimal production change**

  Add class-level Javadoc to `LoadTimeProfileFactory`:

  ```java
  /**
   * Resolves optional readiness admission for work submitted through
   * {@link HardwareTimingService}. Game-owned PLC and dynamic-art lifecycle
   * services do not consume this profile.
   */
  ```

  Replace only the two warning literals with:

  ```java
  "FAST load-time simulation is reserved; no independent FAST "
          + "hardware-admission profile exists, using NONE"
  ```

  and

  ```java
  "REALISTIC load-time simulation is reserved; no independent REALISTIC "
          + "hardware-admission profile exists, using PROFILED"
  ```

  Do not change the switch yields.

- [x] **Step 4: Run the focused test and verify green**

  Re-run the Step 2 command. Expected: one test, zero failures and zero errors.

### Task 2: Publish the narrowed applicability and exact evidence path

**Files:**

- Modify: `docs/architecture/validation/2026-08-08-load-time-profile-remediation.md`
- Modify: `docs/architecture/designs/2026-07-29-profiled-load-time-simulation.md`
- Modify: `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md`
- Modify: `docs/architecture/designs/2026-08-08-dead-and-unfinished-code-sweep.md`
- Modify: `CONFIGURATION.md`
- Modify: `src/main/resources/config.yaml`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Retain: `docs/architecture/designs/2026-08-09-load-time-profile-reserved-mode-boundary.md`
- Retain: `docs/architecture/plans/2026-08-09-load-time-profile-reserved-mode-boundary.md`

**Interfaces:**

- Consumes: the reviewed design and existing S1/S2 PLC, S2 dynamic-art, S3K
  profile-publication, rewind, and trace-authority evidence.
- Produces: one consistent current statement of scope plus a concrete future
  measurement sequence; historical point-in-time evidence remains dated.

- [x] **Step 1: Add a dated follow-up to the 2026-08-08 validation**

  Add `## 2026-08-09 follow-up: per-pipeline boundary` after the existing
  decision/evidence sections. State these literal facts:

  - `LoadTimeProfile` currently gates only S3K direct/module Kosinski jobs.
  - S1/S2 still resolve the enum through their default module factory, but every
    value returns the supplied immediate profile and none of their PLC/DPLC
    work becomes a `HardwareWorkSubmission`.
  - S1 and S2 Nemesis PLC queues already own ROM-derived 3/9 and 3/6 service
    cadence and rewind outside the factory.
  - S2 DPLC/dynamic art is a separate decision/transfer lifecycle with no
    generic hardware-readiness submission.
  - Therefore all-game submission unification is not a prerequisite when the
    future modes remain S3K-only; S1/S2 need non-interference proof instead.
  - `FAST` still needs an approved policy definition, while `REALISTIC` first
    needs exact top-level S3K direct observations and an approved confidence
    rule. Link the reviewed 2026-08-09 design for the capture sequence.

  Retain the original 2026-08-08 command and result as historical evidence; do
  not rewrite them as results from this branch.

- [x] **Step 2: Correct current architecture and audit summaries**

  In the 2026-07-29 profile design, advance `Current status` to 2026-08-09 and
  replace the blanket cross-game prerequisite with the per-pipeline matrix from
  the reviewed design. Preserve the original `FAST`/`REALISTIC` intent and
  fallback table.

  In both 2026-08-08 unfinished-code documents, label the row
  `P2 (re-narrowed 2026-08-09)` and replace “S1/S2 PLC/DPLC timing remains in
  separate production owners” as a generic blocker with:

  ```text
  LoadTimeProfile currently admits only S3K Kosinski work. S1/S2 Nemesis PLC
  cadence is already ROM-derived and game-owned; S2 DPLC is a separate
  lifecycle without a generic readiness submission. FAST still lacks an
  approved safety policy. REALISTIC still lacks exact top-level S3K direct
  measurements and an approved confidence/context rule.
  ```

  Keep the row unresolved and link
  `docs/architecture/validation/2026-08-08-load-time-profile-remediation.md` and
  the new design.

- [x] **Step 3: Make user-facing configuration scope exact**

  Replace the `CONFIGURATION.md` entry with a statement that the setting
  selects optional admission only for work submitted through
  `HardwareTimingService` (currently S3K Kosinski). Explicitly say S1/S2 PLC
  cadence and dynamic-art/DPLC lifecycles remain game-owned and are not disabled
  by `NONE` or retimed by another value. Preserve the four values, defaults,
  fallbacks, warnings, and trace bypass.

  Change the bundled YAML comment to:

  ```yaml
  loadTimeSimulation: "NONE"   # S3K Kos admission: NONE, PROFILED, or reserved FAST/REALISTIC aliases
  ```

- [x] **Step 4: Add release-facing summaries**

  Add an Unreleased changelog entry stating that no delay changed, the reserved
  warnings now name their hardware-admission scope, and the docs separate S1/S2
  PLC, S2 DPLC, and S3K Kosinski evidence.

  Add a dated README release-log bullet stating that `FAST` and `REALISTIC`
  remain reserved and that their completion path is now scoped to S3K
  admission evidence rather than incorrectly requiring the existing S1/S2
  native queues to adopt the same profile.

- [x] **Step 5: Self-review all documentation**

  Run:

  ```bash
  rg -n -i "T[B]D|T[O]DO|implement[ ]later|fill[ ]in details|appropriate[ ]error handling" \
    docs/architecture/designs/2026-08-09-load-time-profile-reserved-mode-boundary.md \
    docs/architecture/plans/2026-08-09-load-time-profile-reserved-mode-boundary.md
  git diff --check
  ```

  Expected: the placeholder scan has no matches and `git diff --check` exits
  zero. Read every changed paragraph against the reviewed design and remove any
  claim that all three pipelines share one timing mechanism.

### Task 3: Validate timing, rewind, authority, documentation, and policy

**Files:**

- Verify all files listed above; create no generated report or trace fixture.

**Interfaces:**

- Consumes: the completed source/test/docs change.
- Produces: fresh JDK 21 evidence that profile fallback, native PLC ownership,
  DPLC lifecycle, rewind, and trace isolation remain intact.

- [x] **Step 1: Verify the environment and ROM identities**

  Run `mvn -v` and require Java 21. Verify the discovered ROM files against the
  SHA-1 values in `AGENTS.md`: S1
  `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`, S2 REV01
  `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, and S3K
  `CFBF98C36C776677290A872547AC47C53D2761D6`.

- [x] **Step 2: Run the focused ownership and authority suite**

  From the worktree root:

  ```bash
  load_profile_root=$(pwd)
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Dsonic1.rom.path=${load_profile_root}/Sonic The Hedgehog (W) (REV01) [!].gen" \
    "-Dsonic2.rom.path=${load_profile_root}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
    "-Ds3k.rom.path=${load_profile_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.configuration.TestBundledConfigResource,com.openggf.configuration.TestConfigCatalog,com.openggf.configuration.TestLoadTimeSimulationConfiguration,com.openggf.game.TestGameModuleLoadTimeProfile,com.openggf.game.resources.TestDynamicArtLifecycleService,com.openggf.game.session.TestWorldSessionLoadTimeMode,com.openggf.game.sonic1.resources.TestSonic1PlcService,com.openggf.game.sonic2.resources.TestSonic2PlcService,com.openggf.game.sonic3k.TestS3kLoadTimeProfile,com.openggf.game.timing.TestHardwareTimingRewind,com.openggf.game.timing.TestHardwareTimingService,com.openggf.game.timing.TestLoadTimeProfileContract,com.openggf.game.timing.TestProfiledLoadTimeManifest,com.openggf.level.resources.TestNemesisPlcServiceQueue,com.openggf.trace.TestS1S2PlcComparisonOnlyGuard,com.openggf.trace.timing.TestHardwareTimingAuthorityGuard" \
    test
  ```

  Expected: every selected test passes with zero failures and errors. Report the
  exact test count; do not infer it in advance.

- [x] **Step 3: Inspect the final change and stage only intended files**

  Run `git diff --check`, `git diff --stat`, `git diff`, and `git status
  --short`. Confirm there is no change to mode yields, hardware kinds,
  readiness code, rewind snapshots, trace sources, profile manifests, or trace
  fixtures. Stage only the source, test, configuration, README/changelog, and
  architecture documents listed by this plan.

- [x] **Step 4: Run commit policy and commit**

  Run `.githooks/run-policy pre-commit` against the staged set, then commit with
  a subject such as `fix(timing): scope reserved load-profile diagnostics`. Use
  these trailers:

  ```text
  Changelog: updated
  Guide: n/a
  Known-Discrepancies: n/a
  S3K-Known-Discrepancies: n/a
  Agent-Docs: n/a
  Configuration-Docs: updated
  Skills: n/a
  ```

  Do not use `--no-verify`.

- [x] **Step 5: Verify the committed state**

  Re-run `git status --short --branch`, `git show --stat --oneline HEAD`, and
  `.githooks/run-policy pre-push origin < /dev/null`. The worktree must be clean
  and the branch must remain local; do not merge or push.
