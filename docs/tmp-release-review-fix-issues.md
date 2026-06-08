# Temporary Release Review Fix Tracker

Status key: `open`, `in-progress`, `fixed`, `deferred`.

This file tracks the release-prep architecture/code review findings being fixed before committing to `develop`. It is intentionally temporary and should be removed or folded into permanent release docs before final release if no longer useful.

| ID | Severity | Status | Area | Finding | Primary Files |
| --- | --- | --- | --- | --- | --- |
| RRF-001 | High | fixed | Release policy | Direct `master` pushes can bypass branch-policy validation because release policy only runs on pull requests. | `.github/workflows/release.yml`, `.githooks/` |
| RRF-002 | High | fixed | Release trace gate | Release trace coverage requires only four reports despite the trace profile selecting many trace tests. | `.github/workflows/release.yml`, `pom.xml`, `src/test/java/com/openggf/tests/TestBuildToolingGuard.java` |
| RRF-003 | High | deferred | Trace policy | Legacy S3K AIZ full-run trace remains diagnostic-only; release replay now blocks it unless the explicit diagnostic heuristic property is set. Full regeneration is still required before re-enabling it as release parity. | `TraceReplayBootstrap.java`, `TestS3kAizTraceReplay.java`, `docs/TRACE_FRONTIER_LOG.md` |
| RRF-004 | High | fixed | Trace bootstrap | S2 Tornado replay no longer applies metadata start position or primes player/Tornado ride state from route-shaped object state; remaining prelude work is native timing/object advancement only. | `TraceReplayBootstrap.java`, `TraceReplaySessionBootstrap.java` |
| RRF-005 | High | fixed | Runtime ownership | AIZ intro preload can use `BootstrapObjectServices` instead of active runtime-owned services. | `Sonic3k.java`, `AizIntroTerrainSwap.java`, `Sonic3kAIZEvents.java`, `AizPlaneIntroInstance.java` |
| RRF-006 | High | fixed | S3K progression | S3K special-stage entry rings now route Hidden Palace subtypes and all-chaos/all-super emerald state to HPZ instead of silently falling through. | `Sonic3kSSEntryRingObjectInstance.java`, `TestSonic3kSSEntryRingFormation.java` |
| RRF-007 | Medium | fixed | Object services | Production object constructors are guarded against `services()` calls while safe child construction continues through `ObjectConstructionContext`; the existing constructor/context guards now cover the release concern. | `TestNoServicesInObjectConstructors.java`, `TestConstructionContextGuard.java`, `TestObjectServicesMigrationGuard.java` |
| RRF-008 | Medium | fixed | Object services | Deprecated `DefaultObjectServices` fallback can create detached registries/pipeline when no runtime exists. | `DefaultObjectServices.java`, tests |
| RRF-009 | Medium | fixed | Trace reporting | Trace replay and credits-demo harnesses now fail warning-only reports by default, with an explicit diagnostic-only override for non-release fixtures. | `AbstractTraceReplayTest.java`, `AbstractCreditsDemoTraceReplayTest.java` |

## Trace-scope notes

- RRF-003 residual debt: `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`
  still needs regeneration or a native intro replay model before it can be a
  release parity gate. The legacy heuristic is now guarded by
  `openggf.trace.allowLegacyS3kAizDiagnosticHeuristic` and throws in normal
  release replay.
- RRF-004 removed the committed trace-to-engine ride-state bootstrap. S2 SCZ/WFZ
  may expose earlier native frontiers once the wider workspace compiles and
  those ROM-backed traces can run.
| RRF-010 | Medium | fixed | Coordinate semantics | S3K Pachinko flipper launch distance now uses player centre X against ROM object X, with a regression test for same-centre/different-bounds sprites. | `PachinkoFlipperObjectInstance.java`, `TestPachinkoFlipperObjectInstance.java` |
| RRF-011 | Medium | fixed | Game completion | Advancing past the final configured zone now requests credits and preserves the terminal progression sentinel instead of wrapping to zone 0. | `LevelManager.java`, `TestLevelManagerEndProgression.java` |
| RRF-012 | Medium | fixed | Release guard tests | Build/release guard tests rely heavily on raw string checks and disabled-test guard only blocks one wording. | `TestBuildToolingGuard.java` |
| RRF-013 | Medium | fixed | Release automation | Manual release uses static `v0.6.prerelease` tag without an explicit tag-exists preflight. | `.github/workflows/release.yml`, `pom.xml` |
| RRF-014 | Medium | deferred | Runtime assets | S1 palette cycles, conveyor waypoint and child spawn tables, GHZ bridge bend tables, a small SLZ/support-object mapping slice, LZ `Map_Jaws` / `Map_Burro` / `Map_Flap`, GHZ/SLZ `Map_Smash`, LZ `Map_Harp`, shared `Map_But`, SBZ2 `Map_FFloor`, and all S1 boss mappings now load from ROM data; the legacy boss mapping helper file was removed and the ratchet budgets were tightened again. Remaining S1 handwritten object-provider mapping pieces still require broader ROM-backed migration. | `Sonic1PaletteCycler.java`, `Sonic1BridgeObjectInstance.java`, `Sonic1ObjectPlacement.java`, `Sonic1ObjectArtProvider.java`, `Sonic1LZConveyorObjectInstance.java`, `Sonic1SpinConveyorObjectInstance.java`, `TestArchitecturalSourceGuard.java`, related S1 assets |
| RRF-015 | Low | fixed | Presentation | S3K special-stage emerald handling now loads Super Emerald art/palette state and marks Super Emerald collection when the save state is in Super Emerald mode. | `Sonic3kSpecialStageManager.java`, `TestArchitecturalSourceGuard.java` |
| RRF-016 | Low | fixed | Presentation | S2 bridge stake subtypes 7/8 now render with the bridge renderer ground-edge frame instead of returning invisible. | `BridgeStakeObjectInstance.java`, `TestArchitecturalSourceGuard.java` |
| RRF-017 | Low | fixed | Diagnostics | S3K special-stage results audio helper failures now log warnings instead of being swallowed. | `S3kSpecialStageResultsScreen.java`, `TestArchitecturalSourceGuard.java` |
