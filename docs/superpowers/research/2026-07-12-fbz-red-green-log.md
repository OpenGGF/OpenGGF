# FBZ RED/GREEN evidence log

## Task 1 — evidence and completeness gates

- Requirement: FBZ-INV-001 / FBZ-REG-001
- RED command: `mvn "-Dtest=TestFbzObjectInventory,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen" -Dmse=off`
- RED result (2026-07-12): 3 tests, 1 failure. The binary inventory test passed; `fbzProfileAllowlistMatchesTheCheckedConcreteFactoryInventory` failed because FBZ returned the broad S3KL set instead of the frozen 15-ID concrete set. The failure explicitly showed expected `[01,02,07,08,0F,26,28,2A,2F,33,34,6A,6B,80,85]` versus the broad S3KL allowlist.
- GREEN command: `mvn "-Dtest=TestFbzObjectInventory,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"`
- GREEN result (2026-07-12): exit 0; fresh Surefire XML reports 3 tests, 0 failures, 0 errors, 0 skipped. MSE's session aggregate printed `passed=54 failed=0 errors=0 skipped=0`; the authoritative selected-class XML is 1 + 2 tests, and the registry classification loop exercises all 860 placement records.
- Test commit: pending conductor commit.
- Implementation commit: pending conductor commit.
- Reviewer verdict: pending delegated review.

No trace capture or replay was executed. Trace baseline values come only from
persisted `docs/TRACE_FRONTIER_LOG.md` evidence; missing measurements are
recorded as `unknown/not previously run` rather than inferred.

### Task 1 specification-review correction

- Review RED: persisted S3K sibling/MHZ trace measurements had incorrectly been
  marked unknown; checkpoint entries lacked executable state recipes; the
  inventory lacked the mandatory per-family/allocation contract and two badnik
  label-provenance rows; the terminator assertion checked only two bytes.
- Correction: froze the exact persisted HCZ/MGZ/CNZ/ICZ/LBZ tuples from the
  2026-07-02 sibling check and MHZ tuple from its immediately preceding entry,
  retaining null warning counts because warnings were not persisted. Added a
  source-cited setup recipe for every immutable checkpoint, including prior
  event state, both boundary approach directions, phase/timer state, and capture
  predicate/count. Added the one-row-per-placed-family contract, dynamic
  allocation policy, missing later-task label provenance, and full six-byte
  terminator assertion.
- Correction verification command: `mvn "-Dtest=TestFbzObjectInventory,TestFbzObjectRegistryCompleteness" test "-Ds3k.rom.path=s3k.gen"`
- Correction GREEN result (2026-07-12): focused command exited 0; fresh
  selected-class Surefire XML reports 3 tests, 0 failures, 0 errors, 0 skipped
  (MSE session aggregate: `passed=59 failed=0 errors=0 skipped=0`).

### Task 1 factual-review correction

- Review RED: several address-manifest entries used noun shorthand instead of
  exact mapping labels; Spring Plunger was incorrectly described as allocating
  children despite being five placed-only `$D0` objects; the capsule checkpoint
  cited `AfterBoss_FBZ`, which is an `rts`, rather than the transition routine.
- Correction: expanded every affected row to the exact S&K mapping label and
  RomOffsetFinder/disassembly address; documented `Obj_FBZSpringPlunger`'s
  allocation-free init/rider/`Sprite_CheckDelete` path at
  `sonic3k.asm:187094-187119`; and cited `loc_7092A`/`loc_70938` at
  `sonic3k.asm:148959-148968` for the `$720` camera gate and
  `StartNewLevel #$0800`.
- Verification (2026-07-12): focused Maven command exited 0; fresh selected
  Surefire XML remains 3 tests, 0 failures, 0 errors, 0 skipped (MSE aggregate
  `passed=59 failed=0 errors=0 skipped=0`). JSON and diff validation passed.

### Task 1 quality-review correction

- Review RED: AIZ complete-run was incorrectly listed green despite repeated
  persisted expected-red evidence at f1095 / 4319; AniPLC recipe aliases meant
  checkpoint/recipe set equality was not enforced; registry construction used
  the no-level S3KL fallback rather than an explicit FBZ zone id.
- Correction: moved `TestS3kAizCompleteRunTraceReplay` into `known_red` with
  `TRACE_FRONTIER_LOG.md:37268-37272,37387-37389` provenance and left
  `green_test_classes` empty. The final plan treats that empty list as a
  documented no-op. Renamed all recipe keys to their exact checkpoint IDs and
  added a focused JSON contract test for exact set equality and deterministic
  required fields. Registry classification now uses a test registry whose
  `currentRomZoneId()` is explicitly `ZONE_FBZ`, retaining concrete `$A8/$A9`
  S3KL remap rejection through the full placement loop.
- First correction run stopped at test compilation because the new manifest
  test was missing its `java.util.Set` import; no test or production behavior
  executed. After correcting the import, the focused command exited 0.
- GREEN evidence: fresh selected-class Surefire XML reports 4 tests, 0
  failures, 0 errors, 0 skipped (MSE aggregate
  `passed=60 failed=0 errors=0 skipped=0`).
