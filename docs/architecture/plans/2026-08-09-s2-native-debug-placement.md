# Sonic 2 Native Debug Placement Contract Ratchet Implementation Plan

> **Execution scope:** strengthen the exact REV01 contract and current-status
> evidence without adding an inaccessible partial runtime capability. The full
> production implementation remains the coherent activation slice defined by
> `docs/architecture/designs/2026-08-09-s2-native-debug-placement.md`.

**Goal:** Replace the broad “engine-wide capability required” deferral with a
reproducible ROM catalog/lifecycle inventory, an explicit activation contract,
and current documentation that names the remaining owners and evidence.

**Delivery rule:** This plan changes tests and documentation only. It does not
add `src/main` code. `DebugModeProvider.hasLevelDebug()` remains false. A
passing evidence test is not a native-placement implementation.

## Task 1: Add the REV01 catalog contract test

**File:**
`src/test/java/com/openggf/game/sonic2/debug/TestSonic2DebugPlacementRomContract.java`

1. Create a Jupiter test annotated with `@RequiresRom(SonicGame.SONIC_2)` and
   read the user-supplied ROM through `GameServices.rom()` and
   `RomByteReader`. Keep the decoder private to the test; do not introduce a
   production catalog.
2. Pin the source input by asserting the documented Sonic 2 World REV01 SHA-1
   (`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`).
3. Decode the 17 offsets at `DebugObjectLists` ROM address `$41D0C`, then
   decode each list's count word and eight-byte rows. Bounds-check every list
   and 24-bit mappings address.
4. Assert the exact zone table contract:

   | Slots | Relative list offset | Row count |
   |---|---:|---:|
   | EHZ | `$0034` | 19 |
   | Zone 1, WZ, Zone 3, Zone 9, DEZ | `$0022` | 2 |
   | MTZ1/2, MTZ3 | `$00CE` | 34 |
   | WFZ | `$01E0` | 32 |
   | HTZ | `$02E2` | 31 |
   | HPZ, OOZ | `$03DC` | 33 |
   | MCZ | `$04E6` | 24 |
   | CNZ | `$05A8` | 24 |
   | CPZ | `$066A` | 24 |
   | ARZ | `$072C` | 29 |
   | SCZ | `$0816` | 13 |

5. Assert 340 zone-expanded rows, 265 rows across distinct list definitions,
   and 117 unique object IDs. Assert HPZ/OOZ table aliasing rather than merely
   equal decoded content.
6. Pin the shipped `fixBugs = 0` variants: EHZ Obj `$49`, subtype `$00`, uses
   preview frame `$00`; CNZ contains the parent-dependent Obj `$D3` row. These
   assertions make a future accidental `fixBugs = 1` port fail visibly.
7. Compare the 117 unique IDs with
   `Sonic2ObjectRegistry.hasRegisteredFactory(...)`. Assert that exactly four
   lack factories: `$25` Ring, `$46` OOZ Ball, `$73` Rotating Rings, and `$D3`
   Bomb Prize. The assertion is an inventory, not permission to register beta
   or parent-dependent objects approximately.
8. Assert `new Sonic2GameModule().getDebugModeProvider().hasLevelDebug()` is
   false, with a message naming preview, dynamic-ring/lifecycle, global-gate,
   rewind, and dedicated-trace activation dependencies.
9. Run the test on JDK 21 with the verified ROM:

   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
     mvn -Dmse=off \
     -Dsonic2.rom.path="/absolute/path/to/Sonic 2 REV01.gen" \
     -Dtest=com.openggf.game.sonic2.debug.TestSonic2DebugPlacementRomContract \
     test
   ```

This task is an evidence test, not a behavior implementation; there is no
production change to drive with a synthetic red test. If an assertion fails,
re-audit the ROM, registry, or stated contract rather than changing expected
values to match a mistaken document.

## Task 2: Refresh the authoritative current-status documents

**Files:**

- `docs/status/known-discrepancies.md`
- `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md`
- `docs/architecture/plans/2026-08-08-unfinished-code-remediation-roadmap.md`
- `docs/architecture/validation/2026-08-08-unfinished-code-remediation.md`

1. Replace the CPZ-centric native-placement wording with an engine-wide
   disposition linked to the design. Preserve the already-correct statement
   that CPZ rejects engine free-fly at entry and that free-fly is not native
   placement.
2. Record the exact REV01 table address, 17/340/265/117 counts, 113 registered
   factories, and the four exceptional IDs.
3. Name the two architectural blockers independently:
   preview `art_tile` addresses do not map directly to the virtual object
   atlas, and current construction lacks dynamic stage-ring plus three object
   lifecycle paths. Do not call factory presence placement readiness.
4. Name the remaining cross-cutting work: a distinct module-owned controller,
   shipped global gate audit, rewind/session state, and dedicated native-debug
   BK2 evidence.
5. Mark the remediation item “materially narrowed / capability unavailable,”
   not resolved or implemented. Point the roadmap's next action to the
   test/research readiness probes and the one coherent production activation
   slice.
6. Leave player-facing controls/configuration text unchanged unless it is
   stale: it currently says native placement is unavailable. Do not advertise
   selection or spawn controls from the future design.

## Task 3: Write the validation record

**File:**
`docs/architecture/validation/2026-08-09-s2-native-debug-placement.md`

1. Record the base commit/branch, verified ROM hash, design/plan review
   outcomes, and the exact source routines/data audited.
2. Record the test decoder's table/list/factory results and the distinction
   between evidence closure and runtime capability.
3. Record every verification command and exact test counts/outcomes.
4. List the unresolved activation blockers and the next exact evidence action:
   use existing PLC definitions/art providers to produce a row-by-row preview
   readiness matrix, then existing object/ring/rewind harnesses for a
   row-by-row lifecycle matrix. These probes remain test/research-only.
5. State explicitly that no dedicated native-debug BK2 exists yet and that
   ordinary traces with `$FE08 == 0` do not provide placement evidence.

## Task 4: Verify the honest boundary and repository policy

1. Confirm Maven itself uses JDK 21:

   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -v
   ```

2. Run the focused evidence and adjacent capability/lifecycle tests:

   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
     mvn -Dmse=off \
     -Dsonic2.rom.path="/absolute/path/to/Sonic 2 REV01.gen" \
     -Dtest=com.openggf.game.sonic2.debug.TestSonic2DebugPlacementRomContract,com.openggf.game.sonic2.TestSonic2SpecialStageModuleGraph,com.openggf.game.sonic2.objects.TestOOZPlacedObjectGaps \
     test
   ```

3. Run source/architecture guards proportionate to the test/docs-only diff:

   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
     mvn -Dmse=off \
     -Dtest=com.openggf.game.TestProductionSingletonClosureGuard,com.openggf.game.rewind.coverage.TestRewindCoverageGuard \
     test
   ```

4. Review `git diff --check`, the complete diff, and untracked files. Stage
   every intended design, plan, validation, test, and current-status edit.
5. Run `.githooks/run-policy pre-commit` on the staged change. Do not bypass
   hooks. This docs/test-only change uses an explicit `Changelog: n/a` reason;
   `Known-Discrepancies` must be `updated`.
6. Commit on `feature/ai-s2-native-debug-placement`. Do not merge or push.

## Completion criteria

- The ROM-backed test reproduces the exact catalog and current registry gap on
  JDK 21.
- Current docs agree that native placement is unavailable, distinguish it from
  free-fly, and name the exact production owners/evidence still required.
- No production file or runtime behavior changes.
- The focused tests, adjacent boundary tests, selected guards, diff check, and
  commit policy pass.
- The isolated branch contains one reviewable commit and remains unpushed for
  the parent integration/review workflow.
