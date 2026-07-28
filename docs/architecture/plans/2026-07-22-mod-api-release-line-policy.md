# Mod API Release-Line Policy Implementation Plan

> **For agentic workers:** REQUIRED WORKFLOW: execute tasks test-first and stop
> after integration review for explicit human approval before merging.

**Goal:** Make the repository enforce one mutable Mod API candidate per product
release line, begin compatibility obligations only after publication from
`master`, and preserve every genuinely published contract in runtime loading,
SDK validation, signature checks, hooks, CI, and maintained documentation.

**Architecture:** A root descriptor is the machine-readable release-policy
authority. A strict test-side parser validates it and derives the expected pin
layout. Production keeps an immutable supported-contract set next to
`ModApiVersion.CURRENT`; final-tree tests prove that it equals the descriptor's
current API plus published baselines. Runtime and SDK consumers share one
range-intersection predicate. Candidate pins require exact surface equality;
published pins use forward signature compatibility; maintenance patches on the
same release line require exact equality.

**Tech stack:** Java 21, JUnit 5, Maven, GitHub Actions YAML, Bash, PowerShell,
checked-in Java signature snapshots.

---

## Requirements

### Goals

- Represent the current topology as `master=0.5`, `develop=0.6`, `next=0.7`,
  with `next` owning the unpublished mutable `0.7.0` candidate.
- Record that no Mod API baseline has yet been published.
- Keep ordinary internal 0.7 changes at `0.7.0` and replace
  `mod-api-signatures-0.7.txt` in place.
- Preserve and validate every future baseline published from `master`.
- Accept a mod when its `engineApiRange` contains the current contract or any
  retained published contract supported by the engine.
- Enforce descriptor, version, pin, API-surface, branch destination, hook, and
  documentation consistency.

### Non-goals

- Publishing 0.7 or changing the present `0.7.0` candidate surface.
- Inferring policy from `pom.xml` or the checked-out Git branch at runtime.
- Adding union syntax to `VersionRange`.
- Claiming compatibility beyond the checked signature-surface model.
- Automating promotion, release, commit, push, PR creation, or merge.
- Rewriting dated historical plans/specifications except where a current link
  falsely presents them as normative authority.

### Constraints and assumptions

- `mod-api-release-policy.properties` is a repository policy file, not a
  packaged runtime resource.
- Production constants remain explicit and immutable; tests couple them to the
  descriptor so packaging cannot silently omit policy state.
- `VersionRange` stays conjunctive. Compatibility is
  `supportedContracts.stream().anyMatch(range::contains)`.
- Candidate exactness, published forward compatibility, and same-line
  maintenance equality are distinct operations.
- Forward-compatible additions are allowed on a later configured release line,
  including 0.9 to 1.0; removals/changes always fail.
- Hook checks provide fast changed-path coupling. JUnit final-tree validation is
  authoritative for semantic correctness.
- Preserve unrelated user changes and stage exact paths only.

### Acceptance criteria

1. The initial descriptor parses strictly and says `next`, `0.7.0`, candidate,
   with an empty published list.
2. The only accepted initial pin is `mod-api-signatures-0.7.txt`, and it exactly
   equals the recursive current surface.
3. Runtime and SDK validation share one supported-contract predicate.
4. A synthetic later engine supporting current 0.8 plus published 0.7 accepts a
   `>=0.7.0 <0.8.0` mod; a disjoint range is rejected.
5. Published pin removals fail; later-line additions pass; candidate drift,
   maintenance additions, and published removals fail.
6. Descriptor parsing rejects missing, duplicate, unknown, malformed, or
   contradictory keys and invalid normalized pin sets.
7. CI explicitly validates destinations for pushes and PRs affecting `next`,
   `develop`, and `master`.
8. Bash and PowerShell policy paths couple version, descriptor, signature pins,
   and detectable `@ModApi` surface changes, including deletes and renames.
9. Maintained code and docs no longer call 0.7 published or treat the prose guide
   as version-state authority.
10. Focused tests and the full Maven suite pass without introducing failures.

### Risks

- Text-based hook detection can miss an indirect recursive surface change when
  an unannotated dependency type enters the API. The final-tree signature guard
  remains the backstop.
- Production supported contracts could be updated without the descriptor, or
  vice versa. The release-policy guard must compare the two sets exactly.
- Candidate and published pin naming can collide during promotion. The parser
  derives one normalized expected filename map and rejects extras.
- GitHub manual/scheduled runs have no natural destination. They must not claim
  destination validation unless an explicit input is supplied.

### Requirements self-review

Green: every goal maps to a testable acceptance criterion; the confirmed
unpublished status of 0.7 is explicit; implementation guesses are isolated as
named constraints.

---

## Exploration Synthesis

Two independent read-only explorations agreed on the principal seams:

- `ModApiVersion` currently exposes only `CURRENT=0.7.0` and incorrectly calls
  it published.
- `EffectiveCatalogBuilder#ownBlock` and
  `ModJarValidator#validateManifest` independently check only whether a range
  contains `CURRENT`.
- `VersionRange` already supplies the needed `contains` operation; changing its
  grammar would add needless complexity.
- `TestModApiSignatureSurface` hard-codes the sole 0.7 pin as published, while
  `ModApiSignatureSurface#baselineViolations` permits additions only for a
  same-major minor increase.
- `.githooks/validate-policy.sh` and `.ps1` are paired implementations; existing
  diff filters do not fully account for deletion/publication renames.
- `.github/workflows/ci.yml` covers PRs into `develop` but not pushes or `next`;
  `.github/workflows/release.yml` covers `master`. Maven receives no explicit
  destination property today.
- The maintained compatibility guide, `ModApiVersion`, content-mod guide, and
  backlog currently misdescribe 0.7 as published.

The explorations differed only on placement of the runtime policy helper. Keep
the small immutable contract set and predicate in `ModApiVersion`: it is already
the production version owner, avoids a second production abstraction, and can
be compared to the test-side descriptor model. Put descriptor parsing under
`src/test` because the root policy file is repository enforcement data, not
runtime configuration.

### Evidence

- `src/main/java/com/openggf/mods/ModApiVersion.java`
- `src/main/java/com/openggf/mods/VersionRange.java`
- `src/main/java/com/openggf/mods/EffectiveCatalogBuilder.java`
- `src/main/java/com/openggf/tools/modsdk/ModJarValidator.java`
- `src/main/java/com/openggf/mods/code/ModApiSignatureSurface.java`
- `src/test/java/com/openggf/mods/TestModApiSignatureSurface.java`
- `.githooks/validate-policy.sh`, `.githooks/validate-policy.ps1`
- `.github/workflows/ci.yml`, `.github/workflows/release.yml`
- `docs/architecture/mod-api-compatibility.md`

### Exploration self-review

Green: both reviews identified the same runtime, signature, hook, CI, and docs
boundaries; the only conflict has a documented ownership decision.

---

## Architecture Decision

### Ownership and boundaries

- Root `mod-api-release-policy.properties`: source of truth for branch topology,
  current API state, publication state, and published version list.
- `ModApiVersion`: packaged version constants, immutable supported-contract set,
  compatibility predicate, and diagnostic formatting.
- Test-side `ModApiReleasePolicy`: strict parser/value model and normalized pin
  derivation; it does not enter the production artifact.
- `ModApiSignatureSurface`: generic signature comparison primitives. It must not
  read branch policy or files.
- `TestModApiReleasePolicy`: integration guard joining descriptor, production
  constants, snapshots, destination property, and signature rules.
- Hook scripts: changed-path coupling only; both platform implementations must
  apply equivalent rules.
- Workflows: event-to-destination wiring; they pass
  `-DmodApi.destinationBranch=<branch>` rather than relying on Git heuristics.

### Data flow

```text
descriptor ──test guard──> CURRENT + SUPPORTED_CONTRACTS
     │                         │
     ├──normalized pin map     ├──EffectiveCatalogBuilder
     │                         └──ModJarValidator
     ├──candidate exact surface
     └──published compatibility surface

Git event ──workflow──> modApi.destinationBranch ──test guard──> targetBranch
changed paths ──hooks──> required paired files ──JUnit──> semantic final state
```

### Failure and migration behavior

- Malformed descriptors fail with a key-specific action message.
- Unsupported manifests fail with the declared range and deterministic list of
  supported contracts.
- Existing behavior is unchanged initially because the supported set is only
  `0.7.0`.
- No compatibility shim is added for superseded internal 0.7 shapes.
- Rollback is a normal revert of descriptor/constants/guard changes together;
  published pins, once introduced, may never be removed by rollback.

### Architecture self-review

Green: ownership is singular, runtime does not depend on repository files,
candidate and published validation paths are separated, and promotion failures
are fail-closed.

---

## Feature Design

### Descriptor grammar

Required keys, exactly once: `schemaVersion`, `targetBranch`, `masterLine`,
`developLine`, `nextLine`, `currentApi`, `currentStatus`,
`publishedBaselines`. Reject unknown keys, duplicate keys, whitespace-damaged
keys, unsupported schema versions, unknown branches/statuses, noncanonical
SemVer, duplicate published versions, and contradictory state.

Release lines parse as major/minor pairs and compare lexicographically. Require
`masterLine < developLine < nextLine`; do not infer an immediate numeric
successor. `currentApi` major/minor must equal the line selected by
`targetBranch`. Normal `develop`/`next` candidates have patch zero.

Published baselines may be older than every line in the current topology and
must not be rejected for that reason. Reject a published version later than
`currentApi`. Compare its major/minor line to the current line: an older line
uses forward compatibility; the same line requires exact signature equality.
Multiple published maintenance patches on the current line must consequently
equal the current surface and each other.

### Supported-contract behavior

`SUPPORTED_CONTRACTS` is an immutable, sorted, duplicate-free list containing
`CURRENT` and every published baseline supported by the build. `supports(range)`
returns true if any member is contained by the range. Diagnostics list all
members deterministically.

### Snapshot behavior

- Candidate: one `mod-api-signatures-MAJOR.MINOR.txt` file; exact equality with
  current recursive surface.
- Published: one immutable `mod-api-signatures-MAJOR.MINOR.PATCH.txt` per entry.
- Published forward check: removals/changes fail; additions pass only when the
  current version is on a later configured release line.
- Maintenance patch on the same line: signature set equals the prior published
  same-line baseline.
- If current is published, it contributes only its full-version file, never a
  duplicate candidate file.

### Branch behavior

`TestModApiReleasePolicy` reads optional system property
`modApi.destinationBranch`. If absent, it skips only destination agreement. If
present, it must be `next`, `develop`, or `master` and equal `targetBranch`.
Workflows provide it for PR/push runs. Manual/scheduled runs either receive an
explicit validated input or run without a destination claim.

### Acceptance-test map

- AC1/AC2/AC5/AC6: `TestModApiReleasePolicy` and descriptor fixtures.
- AC3/AC4: `TestSemanticVersionAndRange`, `TestEffectiveCatalogBuilder`, and
  `TestModJarValidator`.
- AC7: workflow source guard plus destination-positive/negative test commands.
- AC8: hook fixture tests for both policy implementations.
- AC9: documentation/source guard searches.
- AC10: focused and full Maven verification.

### Feature-design self-review

Green: parsing, compatibility, filenames, diagnostics, event handling, and edge
cases have concrete behavior and mapped tests.

---

## Implementation Plan

### Task 1: Add the strict release-policy model and descriptor

**Dependencies:** none. Complete before Tasks 2–5; Task 6 follows Tasks 1–5.

**Files:**

- Create: `mod-api-release-policy.properties`
- Create: `src/test/java/com/openggf/mods/ModApiReleasePolicy.java`
- Create: `src/test/java/com/openggf/mods/TestModApiReleasePolicy.java`

**Steps:**

- [ ] Write fixture-driven tests first for all required keys, duplicates,
  unknown keys, schema/status/branch validation, canonical versions, ordered
  topology, patch-zero candidates, publication consistency, and normalized pin
  names. Include a published baseline older than the configured topology, a
  forbidden baseline later than current, multiple same-line maintenance pins,
  and 0.9 to 1.0 ordering. Parse raw lines; do not use `java.util.Properties`,
  which loses duplicate-key evidence.
- [ ] Add destination-property tests: absent succeeds without branch assertion,
  `next` succeeds for the initial descriptor, and `develop` fails with the
  prescribed corrective message.
- [ ] Implement the immutable parser/model and normalized expected-pin mapping.
- [ ] Add the initial descriptor exactly as approved, with no published entries.
- [ ] Self-review error messages for key name, observed value, and required
  action; ensure collections are immutable and deterministically ordered.

**Verification:**

```bash
mvn "-Dtest=com.openggf.mods.TestModApiReleasePolicy" test
mvn "-DmodApi.destinationBranch=next" "-Dtest=com.openggf.mods.TestModApiReleasePolicy" test
```

Negative proof (expected failure):

```bash
mvn "-DmodApi.destinationBranch=develop" "-Dtest=com.openggf.mods.TestModApiReleasePolicy" test
```

**Reviewer checklist:** strict duplicate detection; no Git-branch inference;
initial empty publication set; line comparison handles 0.9 to 1.0.

### Task 2: Centralize runtime and SDK supported-contract compatibility

**Dependencies:** Task 1 model available for final coupling assertion.

**Files:**

- Modify: `src/main/java/com/openggf/mods/ModApiVersion.java`
- Modify: `src/main/java/com/openggf/mods/EffectiveCatalogBuilder.java`
- Modify: `src/main/java/com/openggf/tools/modsdk/ModJarValidator.java`
- Modify: `src/test/java/com/openggf/mods/TestSemanticVersionAndRange.java`
- Modify: `src/test/java/com/openggf/mods/TestEffectiveCatalogBuilder.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestModJarValidator.java`
- Create: `src/test/java/com/openggf/mods/TestModApiRuntimePolicy.java`

**Steps:**

- [ ] Add failing tests for an explicit supported set `{0.7.0, 0.8.0}`:
  accept a 0.7-only range, accept `*`, reject a disjoint range, and return a
  deterministic supported-version diagnostic.
- [ ] Rename tests and messages that say pinned-current/legacy acceptance to
  candidate/supported-contract terminology.
- [ ] Add immutable `SUPPORTED_CONTRACTS`, shared `supports(VersionRange)`, and
  a package-visible or pure helper overload that accepts an explicit set for
  future-state tests without mutating globals.
- [ ] Replace both production `contains(CURRENT)` checks with the shared API.
- [ ] Give each consumer a package-private predicate injection seam used only by
  its same-package tests; its public/default construction must bind the shared
  production predicate. Do not expose the explicit-set helper as Mod API.
- [ ] Assert at consumer level that both builders accept a range matching only a
  retained published contract and that both rejection diagnostics report the
  same complete, sorted supported set.
- [ ] In `TestModApiRuntimePolicy`, assert that production `CURRENT` and the
  supported set equal descriptor `currentApi ∪ publishedBaselines`.
- [ ] Confirm dependency ranges between mods still use their existing logic.

**Verification:**

```bash
mvn "-Dtest=com.openggf.mods.TestSemanticVersionAndRange,com.openggf.mods.TestEffectiveCatalogBuilder,com.openggf.tools.modsdk.TestModJarValidator,com.openggf.mods.TestModApiRuntimePolicy" test
```

**Reviewer checklist:** no `VersionRange` grammar change; both consumers
delegate; no mutable global test seam; deterministic diagnostics.

### Task 3: Generalize candidate and published signature enforcement

**Dependencies:** Task 1.

**Files:**

- Modify: `src/main/java/com/openggf/mods/code/ModApiSignatureSurface.java`
- Modify: `src/test/java/com/openggf/mods/TestModApiSignatureSurface.java`
- Create: `src/test/java/com/openggf/mods/TestModApiPinPolicy.java`
- Verify unchanged candidate content:
  `src/test/resources/mods/mod-api-signatures-0.7.txt`

**Steps:**

- [ ] Replace published-0.7 constants/test names with candidate terminology and
  retain exact equality against the current surface.
- [ ] Write comparison tests first for: candidate addition/removal failure;
  published removal failure; later-line addition success within a major and
  across 0.9 to 1.0; same-line maintenance addition failure; and exact
  maintenance equality success.
- [ ] Refactor comparison primitives so forward compatibility accepts additions
  for any later configured release line, while exact comparisons remain exact.
  Pass ordering/policy as an argument; do not make the generic surface utility
  read the descriptor.
- [ ] Enumerate actual pin files and compare them to the descriptor-derived map.
- [ ] For the candidate pin, assert exact current surface. For each published
  pin, parse its full version and run the appropriate compatibility check.
- [ ] Assert pin content and traversal order are deterministic.

**Verification:**

```bash
mvn "-Dtest=com.openggf.mods.TestModApiSignatureSurface,com.openggf.mods.TestModApiPinPolicy,com.openggf.mods.TestNoProvisionalModApiShims" test
```

If the candidate surface is intentionally changed by concurrent work, regenerate
the existing 0.7 file in place; do not rename it or bump the API version:

```bash
mvn "-DskipTests" compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/mod-api-snapshot-classpath.txt"
java -cp "target/classes:$(tr -d '\r\n' < target/mod-api-snapshot-classpath.txt)" \
  com.openggf.mods.code.ModApiSignatureSurface --snapshot \
  > src/test/resources/mods/mod-api-signatures-0.7.txt
```

PowerShell uses the equivalent command documented in
`docs/architecture/mod-api-compatibility.md`.

**Reviewer checklist:** no 0.7 published language; normalized filenames; major
transition additions tested; maintenance equality is not weakened.

### Task 4: Add symmetric changed-path enforcement to Git hooks

**Dependencies:** Tasks 1 and 3 define artifact coupling.

**Files:**

- Modify: `.githooks/validate-policy.sh`
- Modify: `.githooks/validate-policy.ps1`
- Create: `src/test/java/com/openggf/tests/TestModApiHookPolicy.java`
  (or extend an existing hook-policy test if discovered during implementation)

**Steps:**

- [ ] Encode this exact matrix in fixture names and both implementations:

  | Change | Required companion |
  |---|---|
  | CURRENT | descriptor plus normalized pin add/delete/rename implied by the new state |
  | detectable `@ModApi` delta | current candidate pin content update |
  | candidate pin content-only update | detectable API delta; no descriptor edit |
  | any pin add/delete/rename | descriptor publication/promotion edit |
  | descriptor topology/destination/status edit | only the pin operation implied by the resulting normalized map; no unconditional pin rewrite |

- [ ] Build temp-Git fixture tests first for staged and CI-range cases covering
  every matrix row, valid pairs, deletion, rename, and paths with spaces. Invoke
  `validate-policy.sh commit-msg <message-file>` for staged cases and
  `validate-policy.sh ci-pr <base> <head> <base-ref> <head-ref>` plus `ci-push`
  for range cases. Create valid trailer blocks and README merge evidence in
  fixtures so unrelated policy gates do not mask Mod API assertions.
- [ ] Change diff collection to include deletions and both sides of renames.
- [ ] Add one conceptual coupling routine to each platform implementation with
  equivalent messages and exit behavior.
- [ ] Inspect before and after blobs for `@ModApi` so deleting an annotated
  declaration is detectable. Document that indirect recursive changes remain a
  JUnit responsibility.
- [ ] Invoke coupling from staged commit validation and per-commit CI range
  validation without bypassing existing trailer/merge rules.
- [ ] Run the same semantic fixtures directly against
  `validate-policy.ps1` when `pwsh` is available, not syntax validation alone.
  If unavailable, record the semantic-parity limitation after syntax parsing.

**Verification:**

```bash
mvn "-Dtest=com.openggf.tests.TestModApiHookPolicy" test
bash -n .githooks/validate-policy.sh
pwsh -NoProfile -Command "[void][scriptblock]::Create((Get-Content -Raw '.githooks/validate-policy.ps1'))"
```

**Reviewer checklist:** shell/PowerShell parity; D/R statuses; existing policy
behavior preserved; no broad false positive on every Java edit.

### Task 5: Wire explicit destination validation through CI

**Dependencies:** Task 1 destination guard. Can proceed in parallel with Task 4.

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Create or modify: workflow source assertions in
  `src/test/java/com/openggf/tests/TestBuildToolingGuard.java`

**Steps:**

- [ ] Add source tests first asserting PR/push coverage and Maven destination
  properties for all three long-lived branches.
- [ ] Set `ci.yml` PR and push branches to `[next, develop]`; retain
  `release.yml` PR/push ownership for `master` to avoid duplicate expensive
  release jobs.
- [ ] On PR events pass `${{ github.base_ref }}` and on push events pass
  `${{ github.ref_name }}` as `-DmodApi.destinationBranch=...`. Materialize the
  selected nonempty value through an event-gated environment/step rather than a
  detached-checkout heuristic. Apply the same rule to the master release test.
- [ ] Make the source guard prove that every broad Maven test path used for a
  PR or push receives the destination property; merely finding the property
  somewhere in each YAML file is insufficient.
- [ ] For manual/scheduled CI, omit destination validation unless an explicit
  constrained workflow input is supplied. Do not infer from detached checkout.
- [ ] Keep existing ROM-backed release/trace behavior unchanged.

**Verification:**

```bash
mvn "-Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.mods.TestModApiReleasePolicy" test
mvn "-DmodApi.destinationBranch=next" "-Dtest=com.openggf.mods.TestModApiReleasePolicy" test
```

**Reviewer checklist:** no duplicate master release workload; push and PR paths
both set destination; manual/schedule semantics explicit; YAML expressions safe.

### Task 6: Correct active documentation, guidance, samples, and changelog

**Dependencies:** Tasks 1–5 behavior settled.

**Files:**

- Modify: `docs/architecture/mod-api-compatibility.md`
- Modify: `docs/modding/content-mods.md`
- Modify: `docs/modding/formats/manifest.md`
- Modify if confirmed by search: `docs/modding/BACKLOG.md`,
  `docs/modding/guides/rom-art-remix.md`,
  `docs/architecture/per-game-rule-placement.md`, `docs/modding/index.md`
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`
- Modify: `CHANGELOG.md`
- Verify: SDK template and maintained sample manifests remain
  `>=0.7.0 <0.8.0`

**Steps:**

- [ ] Add/update doc guard assertions first where maintained terminology is
  already scanned. Task 6 owns documentation assertions in
  `TestExternalContentPolicy`; any needed additions to
  `TestModApiReleasePolicy` or `TestBuildToolingGuard` are reserved for the Task
  7 integration owner to avoid overlapping earlier task ownership.
- [ ] Make the descriptor the sole version/publication-state authority and the
  compatibility guide its explanatory contract.
- [ ] Describe 0.7 as the unpublished mutable `next` candidate, explain the
  empty published set, supported-range intersection, pin forms, maintenance
  equality, and promotion checklist.
- [ ] Add the mandatory descriptor/promotion directive identically to
  `AGENTS.md` and `CLAUDE.md`.
- [ ] Update the changelog for the new policy enforcement.
- [ ] Preserve dated historical design provenance; remove only claims that
  present those documents as current authority.
- [ ] Search for remaining misleading terms and manually classify every hit.

**Verification:**

```bash
rg -n "first published|only published|published.*0\.7|PUBLISHED_(BASELINE|VERSION)" src/main docs/architecture docs/modding AGENTS.md CLAUDE.md CHANGELOG.md --glob '!docs/superpowers/**'
mvn "-Dtest=com.openggf.mods.TestModApiReleasePolicy,com.openggf.mods.TestModApiSignatureSurface,com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.TestExternalContentPolicy" test
```

**Reviewer checklist:** active vs historical docs distinguished; paired agent
guidance consistent; sample ranges unchanged; changelog and trailer obligations
recognized.

### Task 7: Integrate, verify, and review end to end

**Dependencies:** Tasks 1–6.

**Files:** all files above; no new behavior scope.

**Steps:**

- [ ] Reconcile concurrent edits in this order: descriptor/model, runtime,
  signature guard, hooks, workflows, docs.
- [ ] Run the combined focused suite and repair only policy-related failures.
- [ ] Run the full suite with full logs only if focused failures need diagnosis.
- [ ] Run hook syntax/platform checks and inspect the normalized pin inventory.
- [ ] Review the diff for unintended signature changes or rewritten historical
  documents.
- [ ] Produce an Integration Report and independent End-to-End Review.
- [ ] Stop for explicit human confirmation. Do not commit, push, open a PR, or
  merge unless separately requested.

**Verification:**

```bash
mvn "-Dtest=com.openggf.mods.TestModApiReleasePolicy,com.openggf.mods.TestModApiRuntimePolicy,com.openggf.mods.TestModApiPinPolicy,com.openggf.mods.TestModApiSignatureSurface,com.openggf.mods.TestSemanticVersionAndRange,com.openggf.mods.TestEffectiveCatalogBuilder,com.openggf.tools.modsdk.TestModJarValidator,com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.tests.TestModApiHookPolicy,com.openggf.tests.TestBuildToolingGuard,com.openggf.TestExternalContentPolicy" test
mvn test
bash -n .githooks/validate-policy.sh
find src/test/resources/mods -maxdepth 1 -name 'mod-api-signatures-*.txt' -printf '%f\n' | sort
```

**Reviewer checklist:** all acceptance criteria traced; no new public API bump;
no published baseline invented; unrelated failures clearly distinguished; human
review gate honored.

### Parallel execution map

After Task 1:

- Lane A: Task 2 (runtime/SDK).
- Lane B: Task 3 (signature enforcement).

After Task 3 defines artifact coupling:

- Lane C: Task 4 (hooks).
- Lane D: Task 5 (CI), which needs only Task 1 and may start earlier.

Task 6 follows behavioral convergence to avoid documentation churn. Task 7 is a
single integration owner. Lanes must not edit files outside their ownership;
Task 1 exclusively owns `TestModApiReleasePolicy`, Lane A owns
`TestModApiRuntimePolicy`, and Lane B owns `TestModApiPinPolicy`. Any final
cross-cutting assertions are added by the Task 7 integration owner after lane
completion.

### Implementation-plan self-review

Green: every task has tests first, explicit files, dependencies, verification
commands, and reviewer checks. Parallel lanes have disjoint production
ownership; the one shared test integration point and its order are explicit.

---

## Integration Report

- Changed files: root policy descriptor; Mod API version/runtime/SDK validation;
  signature comparison and pin guards; Bash/PowerShell policy hooks; CI/release
  workflows; focused policy/runtime/signature/hook/build tests; active Mod API
  documentation, agent guidance, and changelog.
- Focused test evidence: 146 tests passed with zero failures from a disposable
  local-Linux copy of the exact source tree. The copy avoids NTFS/Surefire report
  instability in the shared Windows-mounted worktree.
- Full-suite evidence: 15,043 tests executed. Five known/unrelated MHZ assertions
  failed; six fixture errors were caused by intentionally excluded ROM,
  disassembly `.bin`, and validation-tool files in the disposable copy. The one
  sample integration failure was isolated to the excluded `s2.gen` ROM. No Mod
  API policy test failed.
- Hook/platform evidence: `bash -n .githooks/validate-policy.sh` passes; 12
  temp-Git semantic fixtures pass for Bash. The same fixtures are parameterized
  for PowerShell when `pwsh` exists, but PowerShell is unavailable on this host.
- Unresolved risks: hook `@ModApi` detection is intentionally text-based and is
  backstopped by final-tree JUnit signature validation.
- Deferrals: native PowerShell execution and ROM-backed/full-fixture suite
  verification remain CI/Windows-host responsibilities.

## End-to-End Review

- Blocking findings and fixes: initial review found mutable published-pin hook
  handling, an impossible API-changing line transition, stale published-0.7
  prose, downstream published descriptors, and two stale/brittle tests. All were
  fixed and regression-covered.
- Non-blocking residual risks: PowerShell semantics are source-symmetric but not
  executable on this Linux host; text-based hook detection cannot replace the
  authoritative recursive signature guard.
- Requirements traceability result: all ten acceptance criteria are implemented
  and covered by focused tests or explicit workflow/hook source guards.
- Documentation/policy result: descriptor is the sole state authority; 0.7 is
  consistently the unpublished mutable `next` candidate; maintained samples
  remain `>=0.7.0 <0.8.0`.
- Human-review checklist: inspect descriptor/topology, hook coupling matrix,
  workflow destination wiring, supported-contract intersection, and recorded
  full-suite fixture limitations.
- Merge readiness: **independent final review reports merge-ready with no
  blockers**.
