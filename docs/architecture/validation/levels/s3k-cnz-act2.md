# S3K Carnival Night Act 2 coverage matrix

Canonical binding: `S3K_CARNIVAL_NIGHT_2`, engine zone 3/act 1, ROM `$0301`, S3KL object set.
Edges: CNZ1 incoming structural/results handoff; ICZ1 outgoing full entry.
Character routes: Sonic/Tails reach the end boss; Knuckles uses native cutscene/teleporter progression. Do not infer one route from another character code.

Status: in progress. The bounded normal-entry and graph floor below is qualified; the act is not certified.
Supported production presets are 320/352/400/528/800 via `WidescreenAspect` and `resolveDisplayAspect`. Configured main/donor support is off×{Sonic,Tails,Knuckles}, S1×Sonic and S2×{Sonic,Tails}. `LaunchProfile.sanitizedFor` owns configured fallback; script-created NPCs have their own native registration/art authority. Configured comma rosters are uncapped by `ActiveGameplayTeamResolver.parseConfiguredSidekicks`; pair, mixed, duplicate and declared large-roster examples do not establish a maximum.

The current native/default binding covers all three solo mains at 320. Remaining preset/donor/main/team entry and lifecycle products stay open unless explicitly qualified below. Raw 512/640 injection, when used by older local tests, is custom geometry evidence rather than production-preset coverage.

## Obligations

| Obligation | Contract / existing binding | Current evidence and shortest remaining action |
| --- | --- | --- |
| ENTRY / CAMERA: normal character start and current focus | `Sonic_Start_Locations` / `Knux_Start_Locations`; [`TestS3kCanonicalEntryLifecycle#nativeMainCharacterStartsFromTheBoundRomTableAndReplaysTwice`](../../../../src/test/java/com/openggf/tests/TestS3kCanonicalEntryLifecycle.java) | 3 native 320 solo-main cases within the 70-case class; real ROM start, decoded main art, unchanged lives, current P1 focus and resolved camera width. Extend finite production configuration products and applicable intro/alternate-entry contracts. |
| REWIND: normal entry world | Complete registered-world capture/restore and 30-frame forward replay twice; all title-card pattern bytes compared by content | Qualified for the stated normal-entry configurations. This does not cover every event/interaction/child lifetime or cross-load history policy. |
| OBJECT / EVENT / REWIND: water/cork and Knuckles switch graph | `TestS3kCnzWaterLevelCorkFloorGraphRewind`, `TestS3kCnz2CutsceneButtonGraphRewind`, `TestS3kCnzMechanismRewind`, `TestS3kCnzLocalMechanicsRewind` | Declared component graphs; actual placement and team/viewport/donor interaction breadth remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| EVENT / CAMERA / LOAD: Knuckles teleporter route | `TestS3kCnzAct1EventFlow#act2KnucklesEntryStartsTeleporterRoute_notModeOnly`, `#act2KnucklesRouteEndReleasesTeleporterClamp`, `TestS3kCnzTeleporterRouteHeadless` | Local production event/transport contexts; independent whole-route and actual outgoing-entry products remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| BOSS / REWIND: end boss, children and scatter | `TestS3kCnzEndBossHeadless`, `TestS3kCnzEndBossGraphRewind` | Component/positioned boss contracts; natural arena and uninterrupted playable exit remain separate. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| LIFE / LOAD: physical post, actual death/restart and incoming/outgoing worlds | Native checkpoint/death/reload owners and the edges listed above | Finite applicable configuration products, real post activation, repeated actual reloads, current roster/control/camera and history retirement remain open. Local seeded checkpoint or late event state is not physical activation or cold-route evidence. |
| PRESENT: actual viewport, palette/scroll/plane and audio | Act-owned ROM art/AniPLC/AnPal/event and music/SFX owners | Decoded playable source is checked at entry. Scene pixels, native audiovisual timing and viewport-edge parity require their own bounded native/GPU evidence. |
| ROUTE: representative completion and compatibility axes | Authentic entry→mandatory mechanics→boss/results→released next state | Complete character-route composition, every preset/donor axis and applicable team-shape traversals remain open; short entry is not route certification. |
| ORACLE: timing-sensitive native behavior | Existing CNZ trace bindings and [frontier log](../../../status/trace-frontier-log.md); no new strict trace run in this slice. | Preserve each owning strict-ledger result/source. Comparison rows do not supply gameplay; no strict trace or conformance sweep was run here. |

## Qualification — 2026-10-14

Base/runtime lineage: `5b2ff8e78`; branch tip before this slice `390ebd765`, plus this commit’s explicit native-scripted art owner and test. Worktree `.worktrees/ai-s3k-parity-hpz`, Java 25.0.4.1, all three original ROMs by absolute path, `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null`.

`python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestS3kCanonicalEntryLifecycle,TestLevelPlayableArtInitializerModArt,TestDonorTailsTailBanks,TestSpriteManagerRewindCapture,TestS3kCnzSoloCarryInHeadless,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils"` with `-Ds3k.rom.path`, `-Dsonic1.rom.path`, `-Dsonic2.rom.path`, then `test`: request 31958 completed 10:58:41 BST (09:58:41 UTC), 169 tests/0 failures/0 errors/0 skips, Maven 37.197 s. The new class contributes 70 cases/0/0/0 (7.070s); these are bounded focused checks, not broad smoke/full-suite certification. No integration/push is claimed by this local record.

The shared selection’s first measured CNZ NPC art failure, rejected alternatives and coherent correction are recorded in the [CNZ1 matrix](s3k-cnz-act1.md). They do not imply a new native scene-parity qualification for this act.
