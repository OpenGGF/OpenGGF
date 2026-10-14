# S3K Angel Island Act 2 coverage matrix

Canonical binding: `S3K_ANGEL_ISLAND_2`, engine zone 0/act 1, ROM `$0001`, S3KL object set.
Edges: AIZ1 incoming seamless handoff; HCZ1 outgoing full entry.
Character routes: Sonic/Tails share the normal ship/end-boss path; Knuckles has a separate native route and end-boss branch.

Status: in progress. The bounded normal-entry and graph floor below is qualified; the act is not certified.
Supported production presets are 320/352/400/528/800 via `WidescreenAspect` and `resolveDisplayAspect`. Configured main/donor support is off×{Sonic,Tails,Knuckles}, S1×Sonic and S2×{Sonic,Tails}. `LaunchProfile.sanitizedFor` owns configured fallback; script-created NPCs have their own native registration/art authority. Configured comma rosters are uncapped by `ActiveGameplayTeamResolver.parseConfiguredSidekicks`; pair, mixed, duplicate and declared large-roster examples do not establish a maximum.

The current native/default binding covers all three solo mains at 320. Remaining preset/donor/main/team entry and lifecycle products stay open unless explicitly qualified below. Raw 512/640 injection, when used by older local tests, is custom geometry evidence rather than production-preset coverage.

## Obligations

| Obligation | Contract / existing binding | Current evidence and shortest remaining action |
| --- | --- | --- |
| ENTRY / CAMERA: normal character start and current focus | `Sonic_Start_Locations` / `Knux_Start_Locations`; [`TestS3kCanonicalEntryLifecycle#nativeMainCharacterStartsFromTheBoundRomTableAndReplaysTwice`](../../../../src/test/java/com/openggf/tests/TestS3kCanonicalEntryLifecycle.java) | 3 native 320 solo-main cases within the 70-case class; real ROM start, decoded main art, unchanged lives, current P1 focus and resolved camera width. Extend finite production configuration products and applicable intro/alternate-entry contracts. |
| REWIND: normal entry world | Complete registered-world capture/restore and 30-frame forward replay twice; all title-card pattern bytes compared by content | Qualified for the stated normal-entry configurations. This does not cover every event/interaction/child lifetime or cross-load history policy. |
| OBJECT / EVENT / CAMERA: battleship loop and bomb child | `TestAiz2ShipLoopRewindRoundTrip#battleshipSurvivesRewindRestoreAndCameraLockIsNotOrphaned`, `TestAiz2TransientChildRewind#shipBombSurvivesRewindRestoreWithMidFlightState` | Constructed ship/child plus declared camera/autoscroll state; active graph checks do not prove natural placement admission. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| BOSS / LOAD: end-boss graph, splash and sequence | `TestS3kAizEndBossGraphRewind`, `TestAiz2BossEndSequenceObjects`, `TestAizEndBossInstance` | Component/late boss contracts; independent arena entry and all required character routes remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| OBJECT / CAMERA: forest ring and team bounds | `TestAiz2ForestRingCameraGeneration`, `TestS3kAiz2BigRingFormation`, `TestS3kAiz2BigRingCollision`, `TestS3kAiz2SidekickBoundsSync` | Local scene/bounds contracts; configuration and before/act ive/after placement breadth remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| LIFE / LOAD: physical post, actual death/restart and incoming/outgoing worlds | Native checkpoint/death/reload owners and the edges listed above | Finite applicable configuration products, real post activation, repeated actual reloads, current roster/control/camera and history retirement remain open. Local seeded checkpoint or late event state is not physical activation or cold-route evidence. |
| PRESENT: actual viewport, palette/scroll/plane and audio | Act-owned ROM art/AniPLC/AnPal/event and music/SFX owners | Decoded playable source is checked at entry. Scene pixels, native audiovisual timing and viewport-edge parity require their own bounded native/GPU evidence. |
| ROUTE: representative completion and compatibility axes | Authentic entry→mandatory mechanics→boss/results→released next state | Complete character-route composition, every preset/donor axis and applicable team-shape traversals remain open; short entry is not route certification. |
| ORACLE: timing-sensitive native behavior | `TestS3kSonicTailsAiz2SegmentTraceReplay`, `TestS3kTailsFullChainAiz2SegmentTraceReplay` | Preserve each owning strict-ledger result/source. Comparison rows do not supply gameplay; no strict trace or conformance sweep was run here. |

## Qualification — 2026-10-14

Base/runtime lineage: `5b2ff8e78`; branch tip before this slice `390ebd765`, plus this commit’s explicit native-scripted art owner and test. Worktree `.worktrees/ai-s3k-parity-hpz`, Java 25.0.4.1, all three original ROMs by absolute path, `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null`.

`python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestS3kCanonicalEntryLifecycle,TestLevelPlayableArtInitializerModArt,TestDonorTailsTailBanks,TestSpriteManagerRewindCapture,TestS3kCnzSoloCarryInHeadless,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils"` with `-Ds3k.rom.path`, `-Dsonic1.rom.path`, `-Dsonic2.rom.path`, then `test`: request 31958 completed 10:58:41 BST (09:58:41 UTC), 169 tests/0 failures/0 errors/0 skips, Maven 37.197 s. The new class contributes 70 cases/0/0/0 (7.070s); these are bounded focused checks, not broad smoke/full-suite certification. No integration/push is claimed by this local record.

The shared selection’s first measured CNZ NPC art failure, rejected alternatives and coherent correction are recorded in the [CNZ1 matrix](s3k-cnz-act1.md). They do not imply a new native scene-parity qualification for this act.
