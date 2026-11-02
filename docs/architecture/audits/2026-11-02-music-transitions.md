# Music-transition source audit — 2026-11-02

## Scope and evidence

Starting engine source: `11ef2b812648ee57d14ad722975ee0ac5967d258`.
This audit compares the music request owners in Sonic 1, Sonic 2 and locked-on
Sonic 3 & Knuckles with shipped-ROM disassembly routines. It distinguishes
request identity, mailbox, fade delay, restoration selection and ownership gates.
It is source review and bounded regression testing, not certification of every
route's frame timing or audible equivalence.

The main workspace's existing reference checkouts were preserved. Reviewed refs:

| Reference | Local revision |
| --- | --- |
| S1 | `f6ece657c1cf253404312137dfcb8ec15fa42318` |
| S2 | `380f37a731bfc720bb0371a35a593184a7ec5e43` |
| S3K | `1a454a0e335137a1a016d1090a9f4528d36944cf` |

Routine labels below identify those reviewed sources; these are not new line
citations against the repository's different pinned submodule revisions.

## Inventory reviewed

| Game | Transition families and engine owners examined |
| --- | --- |
| S1 | Five DLE boss starts and ascent/escape level restores; SBZ2 results to FZ; monitor shoes/invincibility/extra-life; Sonic display expiry; water exit, bubbles and drowning; rings/score/special-stage awards; emerald and results; signpost/prison; special stage; continue; title/SEGA and sound test; ending/credits. Owners include Sonic1 level events and objects, shared playable/drowning controllers, Sonic1AudioProfile and frontend managers. |
| S2 | Pause/unpause; SEGA/title/demo/level; special stage/results; continue; competition results; options/level-select/cheats; ending/credits; ordinary zone bosses; ring/score awards; monitors; Super, invincibility/shoes expiry; game over; drowning/ResumeMusic; emerald and Mecha/WFZ/Death Egg. Owners include Sonic2 events/bosses, shared playable/drowning controllers, Sonic2AudioProfile and frontend managers. |
| S3K | All 28 direct allocations of the song fade helpers and nine shared Knuckles-theme fade callers, defeat-wait helpers and direct Restore_LevelMusic callers; monitor/air/expiry/Super arbitration; title/SEGA/data select/level select/continue/competition; level title-card publication, special-stage emerald/results and ending owners. Explicit fixed LRZ2 and DEZ2 requests remain distinct from level restores. |

The inventory covers transition families, not all configuration products. Existing
frontend timelines and actual allocation-pass ordering still need native capture
qualification. A matching track on the default driver is not sufficient evidence
that the request ID matches another supported driver.

## Confirmed corrections

| ROM owner | Previous behavior | Correction |
| --- | --- | --- |
| S1 Pow_ChkInvinc | Invincibility monitor replaced boss/drowning music. | Preserve boss ownership; REV01 suppresses at air <=12, with REV00's native air bypass retained. Power grant remains independent. |
| Shared drowning countdowns | P2 could publish drowning music and later restore shared music. | Gate countdown audio and restoration on primary-player identity while retaining P2 air/countdown effects. |
| S2 invincible_monitor; S3K Monitor_Give_Invincibility | Invincibility monitor could replace boss/countdown music, and recipient-only Super checks missed a powered primary character. | Gate music on boss inactive and air >12; use the primary player's powered form for the global Super gate. |
| S2 loc_397BA, ObjC5_CaseStart, loc_3D5A8 | Boss entry faded directly, bypassing native SFX-mailbox admission. | Submit native fade request $F9 through the SFX mailbox, preserving entry timers. |
| S2 MenuScreen_LevelSelect | Sound test remapped engine IDs, routed SFX separately, and dropped system requests. | Submit raw native sound-test + $80 through the native music mailbox. |
| S3K loc_61FC2 / sub_65DD6 | AIZ2 Knuckles theme played immediately. | Spawn the native 90-word fade transition. MHZ2's missing transition was fixed in the preceding commit. |
| S3K Obj_Song_Fade_ToLevelMusic / Restore_LevelMusic | Numerous bosses/cutscenes froze a level ID, used actual loaded act, or ignored invincibility. FBZ miniboss used ID zero. | Resolve apparent act and P1 secondary invincibility/powered status at expiry, preserving native signed wait words and existing dispatch delays. Direct restore owners use the same selection. |
| S3K mus_Miniboss versus mus_MinibossK | Object/event starts used $18 where the ROM requests $2E. | Use native $2E at miniboss starts; retain $18 for Player_ResetAirTimer's explicit boss override. The default S&K table aliases the songs but the S3 table does not. |
| S2 CollectRing_Sonic / CollectRing_Tails | Ring-threshold jingles used ordinary music submission; native competition rings omitted the jingle. | Submit native $98 in SFX1 for 100/200-ring awards, replacing the ring request. S1/S3K retain their music path through an internal profile hook. |
| S3K helper allocations | Several cutscenes/minibosses allocated after the current slot, and AIZ1 created its rock before music. | Use native lowest-free allocation and music-before-rock order; exercise lower/higher-slot dispatch. |
| S3K loc_62D04 | MHZ1 started Knuckles’ theme from the later generic actor. | Submit the helper at the controller’s P1 clamp/lock trigger; do not restart it from the actor. |
| S3K loc_6242C / loc_625E2 / loc_62528 | CNZ wrappers added a helper entry and one forced next-frame dispatch. | Use ordinary 120/90-word helpers: 121/91 total own entries, retaining allocator-owned first dispatch. |
| S3K CNZMiniboss_BossDefeated / Wait_FadeToLevelMusic | Restore helper was submitted immediately on defeat. | Submit only when the inherited signed wait word underflows. |

## Remaining qualification and open findings

- S1 REV01 Japanese-region AddPoints awards score-threshold extra lives/jingles;
  the shared score service has no corresponding region-aware award contract.
  Overseas default behavior does not require that award.
- S2 TwoPlayerResults conditionally requests the 2P results track when Level_Music
  differs. The engine's competition scoring object has no corresponding results
  presentation music owner; frontend parity remains incomplete.
- CNZ miniboss still collapses the later native End → 119-word wait → END_GO
  gameplay chain. Boss/control release is 120 entries early after helper allocation;
  this can affect later admission state. The music helper’s allocation/countdown
  is corrected, but the route’s full phase parity remains open.
- S3K Current_music is a native gameplay identity as well as a playback request.
  Restoring the apparent playlist is now explicit, but expiry/air restore cannot
  be certified for every cutscene overlap without a complete Current_music owner
  model. Do not infer this from a saved audio-driver song slot.
- Frontend stop/fade timings, all allocation-failure/pass-order cases, region and
  cartridge/driver products, and audible route comparisons remain unqualified.
  Source matches and focused passes do not close these obligations.

## Validation

`ROM_DIRECTORY` denotes the local directory containing the original ROMs.

Broad command on the initial correction tree (baseline `11ef2b812`, uncommitted
working-tree fingerprint `0298bd8566cbd28d21c274868584e1987ccb3dd3b02ce6a638e37e2f32e00acb`):

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 11ef2b812 --workers 2 --headless-gl surfaceless-egl --rom-directory "$ROM_DIRECTORY" --run
```

Completed 89/89 ordinary batches: 36,566 tests, 12 failures, 10 errors, 50 skips.
Fresh guards: 820 tests, zero failures/errors/skips. Ordinary execution was
1,654.73 seconds, guards 150.29 seconds and preparation 32.53 seconds. The run
started at 10:40:48 UTC and was inspected complete at 13:42:39 UTC (3h01m51s).
Queue wait was not separately aggregated; that wall interval includes shared
admission and unmeasured observer gaps, not just execution. Individual recorded
queue intervals reached 501 seconds.

Four failures match bounded runs on clean baseline `11ef2b812` by identity and
assertion: `TestMasterTitleScreenAudio.cancellingRomLocationEmitsCancel`
(missing UI_CANCEL), `TestMasterTitleScreenLayout.previewAnimationFrame_doesNotResetWhenSelectionStaysTheSame`
(expected1/actual0), `TestPrivateLobbyScreenRendering.actualLobbyAndFullTextPixelsSupportMultipleReadablePages`
(new full-text pixels assertion), and `TestS3kMhzAct2EntryHeadless.coldEntryPressesLiftsAndReleasesNativeTeamWithReplay[12]`
(camera minimum expected872/actual896). Baseline focused runs covered22,2 and15
cases respectively, with2,1 and1 matching failures and no errors/skips. The first
baseline attempt using `--lean` exhausted the heap during test compilation;
`--normal` completed the matched checks.

Eight failures are explicit EGL qualification assertions against inherited
`DISPLAY=:0`. Ten errors are unstubbed P1 queries in MHZ boss fixtures, repaired
by explicitly supplying the fixture’s actual P1 or its intentional absence.
Follow-up source corrections and these repairs receive focused checks below;
the broad run is not described as green.

All50 skip reasons were inspected: optional captures, measurements, external
fixtures/mod packages and service isolation dominate. One CPZ spin-tube test
aborted because its capture/release assumption was not reached; that is ungraded
behavior, not a pass. Three reel cases require an isolated display, and one
OpenAL case requires the explicit isolated null driver. No skip was attributed
to a missing original ROM. Diagnostics were acknowledged and deleted.

The task tree was then advanced to updated develop `92dd2135f` before final
focused validation. Upstream AIZ2 animation, MHZ2 boss/rendering and post-results
music changes were retained. Only additive changelog/matrix/backlog conflicts
needed manual reconciliation; overlapping runtime/test edits merged by intent.
The stale queued focused request was cancelled before execution. Final coverage
includes the overlapping animation, encounter, post-results and recreation owners.

The composed base then retained original AIZ inventory commit `41c948655`,
Sanctuary changes, and develop's input-owner repairs through `a8f3f8647`. The
upstream MHZ graph declaration was reconciled to one entry, identical to the
previously tested mapping. No input repairs were authored by this audit.

A 1,408-case focused run on the composed tree passed 1,407 cases with no errors
or skips (698 seconds queue, 44.201 seconds Maven). It covered song-helper slot
timing, CNZ animation, actual ring pickups, global inventories, S2 competition
rendering with both display variables unset, and Sanctuary backdrop integration.
Its sole failure was the new graph fixture's pre-capture geometry: MHZ exposes
placement X before camera admission. Correcting that fixture then exposed a real
missing capture policy: structural `parent` is excluded by default. The repair
explicitly captures this controller's exact parent reference, recreates pure
shells, and uses `ObjectLifetimeOps` for pending retirement. No guard debt
baseline was increased and no nearest-parent heuristic remains.

Final focused command on composed base `af41c025a` plus the correction tree:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dtest=TestMhzEndBossExplosionController,TestGraphCoveredIsolatedProbeClassification,TestEveryObjectRewindRoundTrip,TestRemainingRewindTailInventory,TestRewindHarnessCoverageRatchet,TestRewindPolicyRegistry test
```

Passed 1,400 tests, zero failures/errors/skips (442 seconds queue, 35.170 seconds
Maven). This includes the four controller witnesses, 28 graph-classification
cases, 24 policy cases and all three inventory suites. The global sweep reports
1,090 isolated executed identities, 250 graph declarations and no unavailable
prerequisites. The exact-parent witness forces physical recreation, keeps a
nearer decoy, compares complete snapshots/RNG, and replays twice; a separate
witness restores pending stationary retirement without another explosion/RNG read.
A lean attempt before the final policy fix exhausted javac's heap while
recompiling all test sources; it ran no tests, and normal admission completed.
Earlier focused owner/API checks also passed, including 128 MHZ boss cases,
S2 solo/competition ring routing and the unchanged creator API surface.

The fresh guard command is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards test -B
```

Final guards after the exact captured-field policy passed all 820 tests with
zero failures/errors/skips (373 seconds queue, 3m15s Maven). This includes the
three reported guards and all 14 exact-policy guard cases. The preceding fresh
guard run also passed 820 cases (672 seconds queue, 3m13s Maven).

Focused checks replace another broad smoke repetition: the final changes are
one explicit field policy, one object's recreation/lifetime and affected
fixtures/declarations. Shared algorithms and unrelated input paths were retained.
The original broad smoke result remains red with the exact baseline limits above.

## Final composition and delivery validation

Develop's LBZ art-admission/camera-exit and glide repairs through `4311f21c4`
were merged into the audit at `cec1112ed`; runtime changes merged automatically.
Additive LBZ matrix/backlog conflicts retained both evidence records. The commit
hook rejected adding an `@EngineOnly` method to the creator-facing audio profile
under its source-surface rule. The final implementation leaves `GameAudioProfile`
unchanged and routes ring awards through `AudioManagerInternalAccess` plus the
internal `RingExtraLifeRequestOwner` profile contract. No signature pin or API
version was changed.

The final focused command (`SONIC2_ROM` and `S3K_ROM` denote the absolute original
ROM paths used locally) was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=$SONIC2_ROM" "-Ds3k.rom.path=$S3K_ROM" -Dtest=TestRingExtraLifeRequestRouting,TestSonic2CompetitionRingExtraLifeMusic,TestModApiSignatureSurface,TestOfficialModsSupportedSurface,TestLbzEndBossKosOwnerRewind,TestSongFadeRestoreMusic test
```

All26 cases passed, zero failures/errors/skips (345 seconds queue, 1m49s Maven).
This grades the internal audio relocation and overlapping LBZ owner on the
composed source. The MHZ implementation, capture policy and inventory declarations
are unchanged from the 1,400-case and 820-guard passing runs above. No unchanged
broad smoke or guards were repeated solely for the merge. Only this validation
record changed after the final focused run.
