# KiS2 presentation and Super Knuckles completion

Scope: complete the remaining title, special-stage, results, ending and Super
Knuckles paths from the user-supplied lock-on dump. Continue-player presentation
is included with ending. The existing no-chip fallback remains available.

Base: `1505a4c7a` on develop. Reference: s2disasm `c336fed`, fixBugs=0,
[original design](../designs/2026-06-12-game-patch-kis2-design.md) and
[branch catalogue](../../kis2/BRANCH_DIFFS.md). The disassembly is research only;
production reads the logical ROM through PatchContext/LockOnAddressSpace.

## Ownership and sequence

- Title workstream: dedicated KiS2 title provider, verified art/mappings/palettes,
  original sequence and input exit, preserving stock S2 provider.
- Special-stage workstream: injected ROM profile in the S2 owner for Knuckles
  frames/DPLCs/mappings, palettes, HUD and ring requirements; preserve capture/restore.
- Ending/continue workstream: ROM-backed Knuckles ending and continue player,
  outcome selection and existing screen lifecycle.
- Integration owner: Super Knuckles controller (second-press entry, speeds,
  palette timer, drain and revert, rewind), results presentation, patch module
  wiring, catalogue/discrepancies/changelog and aggregate validation.

Workers use separate local feature worktrees; the integration owner merges them
before validating the combined delivery. Module wiring and common documentation
remain owned by integration to avoid competing changes.

## Validation

One shared task receipt `kis2-presentation-super-completion`, pinned to the base
above; 40-minute aggregate test budget. Workers perform bounded focused checks
and record elapsed time. Inspect the combined category plan and preflight before
the one permitted broad attempt; timing/public-contract changes require normal
change-based validation. Attribute failures with bounded matched checks, never
by broad retries. Use real ROM paths and inspect skips. Exercise screen lifecycle,
chip and fallback paths, transform/revert, and capture/restore where state changes.

## Delivery

Merge into the existing main-workspace develop branch, preserving unrelated
changes. Push only develop after required verification. Clean fully merged task
worktrees and local branches. Record implementation decisions, rejected options
and verified limits here as the work completes.

## Coverage matrix

| Path | Executable obligation | Limits |
| --- | --- | --- |
| Special stage 1 | `TestKis2HeadlessBoot`: chip provider, entry, 12-pass capture/restore forward replay | No full emerald route |
| Special stage 2 | same independent stage loop | No full emerald route |
| Special stage 3 | same independent stage loop | No full emerald route |
| Special stage 4 | same independent stage loop | No full emerald route |
| Special stage 5 | same independent stage loop | No full emerald route |
| Special stage 6 | same independent stage loop | No full emerald route |
| Special stage 7 | same independent stage loop | No full emerald route |
| Title | `TestKis2Title`: native sequence and skip; ROM art bounds; active module provider | Attract/cheats/VDP masks remain open; CPU composition inspected, no GL capture |
| Results | `TestKis2ResultsArt`, live registered sheet assertion; SS loader mappings/text tests | Inherits S2 results timing approximations |
| Ending, emeralds incomplete / complete | `TestKis2EndingContinue`: both full cutscenes reach credits | Existing walk cadence; no ending rewind owner |
| Continue | same test class plus stock lifecycle tests | Native character sequence, no Tails |
| Super Knuckles | `TestKis2SuperStateController`, headless module activation/revert | No dedicated trace; alternate-button-held input gap remains |

The special-stage integration loop runs at 320 and 426 logical pixels with
Knuckles alone and donation disabled. Configured sidekick and chip-absent
fallback are covered separately by existing KiS2 tests. Other aspect presets,
donor combinations, complete stage routes and normal-act powered-form rewind
scenarios remain inherited coverage gaps, not inferred passes.

## Decisions and evidence

- Special-stage worker `49115a84d` integrated as `2ea9f22d8`; title `df8505cb7`
  as `e3b318bb6`; ending/continue `635ce495e` as `e31bdc7a8`.
- Rejected stock S2 mapping parsing for chip assets: new boundary tests found
  eight-byte stride versus the chip's six-byte pieces. Use the shared KiS2
  decoder and correct the mirrored disassembly guide.
- Dedicated title provider avoids stock Sonic/Tails ownership assumptions.
  Engine resolves the patch before entering configured title startup, so no
  Engine routing change is needed. Data-select/time-attack remain direct loads.
- Super controller owns only its timing state; palettes and sparkle objects
  use their existing rewind owners. The shared air-ability input gate remains
  a documented inherited limitation rather than changing all games' input here.

- Ending review separated `Ending_Routine` from the Super flag: KiS2 forces
  routine zero even with seven emeralds, so initial Float2 and the fixed plane
  hold apply to both outcomes. Super still owns departure and eagles. Follow-up
  `afd9c45f0` corrects the conflated branches. Rejected animating the long wait:
  `sub_A524` / `loc_A53A` resets `prev_anim` every frame, intentionally keeping
  the first Wait frame $56. The lifecycle regression preserves that shipped bug.

## Verification record

On the integration tree after all provider wiring:

- `mvn -Dmse=off '-Dtest=TestKis2*,TestLaunchProfileKis2Roster,Sonic2SpecialStageDataLoaderTest' test`
  with absolute S2 REV01 and S3K ROM properties: 70 tests, 69 passed, no skips;
  the wildcard also selected the existing EHZ1 trace. Its one failure was
  matched on base `1505a4c7a` with `-Dtest=TestKis2Ehz1TraceReplay`: both
  report 300 errors, zero warnings, first bootstrap error frame 0,
  `player_history.pos expected=0x0068 (slot 0x19), actual=0x003F`.
- After the reviewed ending and debug-owner corrections:
  `mvn -Dmse=off '-Dtest=TestKis2EndingContinue,TestKis2HeadlessBoot' test`
  with the same ROM paths: 7 passed, zero skips. This includes chip-palette
  publication, both logical widths, all seven stage replay windows and both
  ending outcomes. Earlier worker tests and root timer/results tests also passed;
  these are focused results, not a full-suite claim.
- Actual launcher preflight passes with Java21 and `LUA_BIN=/usr/bin/lua5.4`;
  the system default Lua is unsuitable. The combined category selection is
  full ordinary plus guards; its result is recorded after execution below.

- Combined candidate `74006f23b`, run `20260914T082758Z-7ce522f0`:
  `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 1505a4c7a --run`
  selected all 2,536 ordinary candidate classes and structural guards. Ordinary:
  19,926 tests, 19,912 passed, 14 skipped, zero failures/errors (447.43 seconds).
  Guards: 665 tests, 662 passed, three failures, zero skips/errors (189 seconds).
  All four required S3K loading/bootstrap/decoding/AIZ1 classes passed without skips.
  Ordinary skips were opt-in diagnostics/soaks, unavailable EGL/reference capture,
  and `TestCPZObjectBugs#testSpinTubeForcesRolling`'s unmet capture assumption;
  no KiS2 test skipped. This ordinary pass does not certify trace/native profiles.
- Matched guard baseline on unchanged `1505a4c7a`:
  `mvn -Dmse=off -Pguards '-Dtest=TestBuildToolingGuard#traceChaserStaysExactOptionalAndOutsideOrdinaryBuilds,TestTraceChaserBoundaryGuard#exactGitlinkAndNonFloatingConfigurationAreTracked,TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree' test -B`.
  All three failed with identical identities/messages and no skips. The first
  two expect TraceChaser `4fb6d080` but the base pins `9fd957bb`; the third flags
  `FbzRouteEvidenceProbe#printEvidence` and
  `LevelSolidityMapProbe#writeSolidityMap` as assertion-free. These unchanged
  failures remain open; no new failure was observed. No broad retry was used.
- Aggregate validation consumed 20.32 minutes, including focused tests and
  matched baseline checks. Diagnostics were inspected before acknowledgment.
  Upstream develop remained at the pinned base, allowing a merge with the same
  validated code tree; the final follow-up changes only this verification record.
