# `next` red-suite remediation plan

> Execute with subagent-driven development. Each production task starts from a focused red test, receives independent spec and quality review, and is integrated only after its focused batch is green.

**Goal:** Make every in-scope test green after merging green `develop` into `next`, while maintaining an exact operational ledger for unfinished MHZ/FBZ/SOZ-and-later work.

**Baseline:** Merge `593237ef2`; 128 red methods, of which 73 are deferred unfinished-zone coverage and 55 are in scope. See `docs/testing/next-post-develop-red-suite-catalogue.md`.

## Wave 0: make the merge safe to land

- [x] Restore HCZ vortex fixed-point motion and rewind state from reviewed commit `482d347a4`; run nested-hurtbox, vortex-motion, and HCZ graph rewind tests.
- [x] Port develop’s reviewed HCZ Hand Launcher d3 height, native position, solid-contact, and bits-0–6 control semantics into next’s per-player model; run the full launcher class.
- [x] Correct the four terrain reflection calls to next’s six-argument compatibility overload; run all nine `TestObjectTerrainUtils` methods before considering any production terrain change.
- [x] Prevent develop internals from becoming accidental frozen 2.4 Mod API. Preserve already-published compatibility, narrow package-internal controller helpers, and defer intentional additive publication to the final API wave.
- [x] Repeat merge-focused spec and quality reviews until both are green.

## Wave 1: unblock concealed assertions

- [x] Replace null-key `CollisionSystem`, mutation-pipeline, and tilemap mocks with real or explicitly key-bearing adapters in gameplay-input, mod-zone runtime-profile, debug-emerald, and gameplay-context fixtures. Do not weaken `RewindRegistry`.
- [x] Give the HCZ end-boss graph harness an explicit empty `ObjectPlayerQuery`, then rerun both graph methods.
- [x] Drive Dragonfly’s post-camera render-state phase before offscreen dispatch, then rerun both graph methods. Change production spawning only if later graph assertions still fail.
- [x] Recount the suite and preserve the catalogue as the immutable post-merge baseline; track the live remainder in the operational exclusion ledger.

## Wave 2: shared correctness foundations

- [x] Remove the stale `TensionBridgeObjectInstance#playerAtCollapse` captured policy and redundant MHZ cutscene-door annotation; retain central exact policy ownership.
- [x] Fix child-slot wait calculation to compare global slot and global execution cursor; preserve lower-slot deferral and same-pass higher-slot execution.
- [x] Enforce non-overlapping ownership by placing dynamic mod pattern windows above the static MGZ zoom range; test boundaries, overlap, clear, and re-registration.
- [x] Inject configuration ownership into FBZ visual tooling and narrowly handle JDK crypto factories in the singleton guard.
- [x] Reunify special-stage trace launch with canonical native-aspect bootstrap; run launcher/bootstrap tests and do not regenerate or hydrate traces.

## Wave 3: implemented-zone behavior

- [x] Repair MGZ twisting-loop ownership cleanup so its own captured riders release while replacement control remains untouched; run loop, ownership, native-P2, and rewind coverage.
- [x] Diagnose MGZ2 state-8 background-plane coordinates and collision ordering, preserving generic ROM `sub_F846`; fix the underlying state without a zone/frame/route carve-out.

## Wave 4: mod/runtime contracts

- [x] Define an explicit regular-pattern-count contract, including empty mod providers, and one consistent preflight/cache order; target the 14 object-art/sample integration reds.
- [x] Audit all S3K custom-zone factories by S3KL/SKL and real ROM dependency; declare stock-bound inventory rather than copying either observed set.
- [x] Restore packaged sample launch dispatch from master title into gameplay.
- [x] Restore the legacy `PlayerRewindExtra` constructor as a delegating compatibility overload.
- [x] Re-run Mod API discovery. Annotate only deliberately supported recursive types, preserve the frozen 2.4 inventory, and publish additive supported surface under a new minor version.

## Wave 5: architecture extraction and final proof

- [x] Move title-card oscillator suppression into `OscillationManager`.
- [x] Move persistent bonus-return respawn handoff and failed-load cleanup into `LevelCheckpointCoordinator` or a focused reset collaborator.
- [x] Verify `LevelManager` is at or below 2,819 effective lines without raising the budget.
- [x] Run `mvn clean test` exclusively.
- [x] Assert every remaining red identity is in the exact current operational exclusion ledger; fail on missing, stale, or falsely excluded entries.
- [ ] Run final spec and quality review loops until green, then merge the reviewed integration branch into local `next`.

## Final reconciliation

- Authoritative exclusive Maven run: 15,145 tests, 66 failures, 6 errors, and 24 skipped.
- The 72 red method identities match `docs/testing/unfinished-sk-zone-red-exclusions.txt` exactly; there are zero in-scope extras and zero stale current exclusions.
- All 55 original in-scope reds and all subsequently exposed in-scope reds are green.
- The sole former deferred entry now green is `com.openggf.game.sonic3k.objects.TestFbzObjectRewind#pendingActTransitionRoundTripsAcrossLevelEventAndZoneRuntimeOwners`.
- `docs/testing/next-post-develop-red-suite-catalogue.md` remains the immutable 128-red post-merge baseline, including its original 73 deferred identities.

## Completion criteria

- All 55 baseline in-scope red identities are green, plus every newly exposed in-scope failure.
- No test, production branch, or trace comparator contains zone/route/frame carve-outs.
- The current unfinished-zone operational exclusion ledger exactly matches the remaining reds and contains no cross-cutting guard.
- The frozen Mod API remains backward compatible; any additive publication is explicitly versioned.
- The immutable baseline catalogue, authoritative final full-suite report, operational exclusion ledger, and branch documentation agree while preserving their distinct baseline/current roles.
