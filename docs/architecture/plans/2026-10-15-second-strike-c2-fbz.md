# Second Strike C2: finite Flying Battery boss packet

## Ownership and contract

Private tree/branch `ai-second-strike-c2-fbz` /
`feature/ai-second-strike-c2-fbz`, pinned base
`76022570e8233823e1e36419666a0f29c768f4b6`. Cow helper ran under MAIN's
build/inventory lease, released immediately; normal checkout fallback because
copy-on-write was unavailable. Hooks installed. Original C1 tree/index/packet
remain intact. Own only enum, boss/event control, three focused tests and this
plan. Root owns compiled API admission/pins, campaign activation and shared docs.
No registry, facade, outdoor support, Blaster or physics edit.

New public annotated enum:
`com.openggf.game.sonic3k.difficulty.FlyingBatteryBossRecipe { STOCK, EXTRA_BEAT }`.
The event retains its original registry `$AC` creation lambda and one-attempt
allocation/event-tail order. Only a returned accepted `FbzEndBossInstance` gets a
fresh optional service lookup, then package-private
`void commitEncounterRecipe(FlyingBatteryBossRecipe)` before first update.
The factory executes before slot rejection: null/denied allocation and SK's
actual `$AC` capsule never sample. Accepted-slot, first-update and no-second-
commit guards apply. Absent/already-withdrawn lookup or successful null/STOCK
selects STOCK. A newly thrown `ObjectCallbackAbortException` propagates to host
frame discard/teardown and SessionManager.clear; accepted allocation does not
authorize continuing a quarantined callback graph. Original shake/rebase/
publication follows successful intake only. No native exception catch,
creator-visible setter or creator callback in the boss.

Constructor and `recreateForRewind` default STOCK. Captured enum/commitment/counter
and recreated native graph resume without another lookup. Service withdrawal
cannot alter an already-committed native lifetime. Only `beginAttack` uses seed10
for EXTRA_BEAT; public static `attackRounds()` stays9. Decrement-before-test plus
the immediate wave produces10 stock versus11 extra actual child waves,352 versus
384 eligible attack updates, first64 then32 dispatches. HP8, invulnerability32,
weapon/proximity gates and full512 Sonic/Tails /256 Knuckles rotation remain.
References: existing disassembly loc_53372/53388 (allocation/tail), loc_70700,
loc_7071A/70736/7075A (commitment/gates), loc_7078A/707BC/707CA (rotation).
Runtime assets still come solely through the ROM pipeline.

## Confirmed inherited branch defect and rejected oracle

A bounded stockmatched negative confirmed the pinned stock `updateRotation`
omitted loc_707BC's Knuckles bit1 write. Both default-constructor STOCK and
EXTRA_BEAT failed; Sonic controls passed. Root approved the narrow owner repair:
set weaponTrigger during Knuckles rotation, leaving loc_707CA Sonic/Tails branch
alone. Actual weapon child/firing/raw5F/nine flames are now tested. This is an
explicit inherited Knuckles-only weapon behavior correction, not merely a recipe
change or a claim that all stock behavior was unchanged.

The followup's assumed global peak25 failed for both Knuckles presets: actual30.
Source explains it without another gameplay edit. Weapon raw5F dispatches the
next nine-child volley97 updates later in this owner; waits plus raw animations
leave flame indices0..4 alive108/105/102/102/99 updates. Thus five old nodes overlap
nine new ones:16 steady +14 flames =30. The repaired test asserts that exact
index multiplicity and97-update intervals, instead of fitting a production
constant. This is the measured controlled graph, not an absolute route-wide cap.
Inherited public `nativePeakObjectCount()`25 remains unchanged; it has no runtime
consumer, only `TestFbzEndBossChildren`'s literal25 assertion, and expresses the
single-volley footprint rather than a global maximum. No docs advertise it as a
measured global bound. Native references: loc_70B18/70B74, ChildObjDat_70F04,
byte_70F3E/70F61/70F70. No broader child timing parity repair attempted.

## Focused verification

Java25.0.4.1 / Maven3.9.16 observed. All test-running commands used this tree's
queue and target, one observer, source frozen until terminal. Measurement hazards,
fresh XML, full failures and skips inspected. Build-only test-compile passed
(Maven1m19s,19:11:18+01:00), which is compilation evidence only.

Command prefix is `python3 tools/testing/maven_queue.py --lean -Dmse=off`;
all selections below use `-Dopenggf.headless.gl=surfaceless-egl test -B`.
Original ROM argument when specified is
`'-Ds3k.rom.path=${REPO_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen'`,
resolved to the existing main checkout's actual absolute original path, never a
copy/link. CRC32 `63522553`, SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6`.

1. 55281 selector
   `TestFbzCampaignBossRecipe,TestFbzCampaignBossBirth,TestFbzEndBoss,TestFbzEndBossFormalCorrections#capsuleAndExitReadyPublishLiveCameraControlHelpersAndDoorHallQueueInOrder,TestFbzPlaneTransition#bossAllocationRebasesTheSolidWithoutCarryingThePlayerTwice`
   with original-ROM argument:43 pass/0 failures/errors/skips; wait2588s,
   Maven28.223s,terminal2026-10-15T19:55:41+01:00. Includes31 recipe,4 birth,
   6 stock root,1 stock exit/capsule and1 actual-ROM allocation/rebase check.
   This preceded the inherited rotation fix and first-abort witness additions.
2. 75991 selector `TestFbzKnucklesRotationWeapon`:4 cases,2 assertion failures
   (both Knuckles missing bit1),2 Sonic pass,0 errors/skips; wait590s,
   Maven1m21s,terminal20:07:56+01:00. Verified the failing owner block exactly
   matched pinned stock before fixing it; not a new recipe-only regression.
3. 59823 selector
   `TestFbzKnucklesRotationWeapon,TestFbzCampaignBossBirth,TestFbzCampaignBossRecipe#actualArmStartsKeepTheImmediateWaveAndNativeDispatchAndRotationBudgets+destructiveGraphRestorePreservesCommittedEnumCounterAndForwardReplayWithoutLookup`:
   20 cases,18 pass/2 peak25-vs30 assertion failures,0 errors/skips;
   wait455s,Maven1m16s,terminal20:18:43+01:00. Rotation4, birth6 and destructive
   replay2 passed; six of eight cycles passed. First-abort accepted root and
   captured STOCK restore are controlled native witnesses, not real host proof.
4. 64971 selector
   `TestFbzCampaignBossRecipe#actualArmStartsKeepTheImmediateWaveAndNativeDispatchAndRotationBudgets`:
   8 pass/0 failures/errors/skips; wait605s,Maven25.207s,
   terminal2026-10-15T20:34:51+01:00. Actual arm-wave starts/cadence, full rotation,
   root attackability16 and at least15 both-arm-idle attackable updates per
   inter-wave interval passed; measured flame shape/cadence also passed.

No live request remains. Final sorted-path/NUL/content/NUL six-Java fingerprint:
`37fc868fb6978833be36638c76a426f37358610ccb026d5695daaba10fd350fa`.
Across scoped runs49 unique cases pass, with affected failures repaired narrowly;
this is not a single49-case current-tree run or full-suite claim. Actual manager
lifecycle evidence includes all0..15 initial-child admission prefixes, no healing
after capacity returns, actual destructive graph recreation with identities,
slots/links/enum/counter, and exact one-update forward replay without lookup.
Existing unaffected direct HP/defeat/exit checks were not repeated ceremonially.

## Remaining evidence and handoff

No complete campaign/character/team route, zero-ring checkpoint retry, empirical
player landing-hit/punish opportunity, visual warning/readability, capture,
viewport breadth or cold strict SDK/packed-host admission was executed here.
Runtime character branches include Sonic solo/Tails/Knuckles and Sonic+Tails
configuration, but no CPU teammate behavior is certified. Root attackability and
idle arms are potential windows, not demonstrated player punish routes. Existing
stock capsule/exit publication is bounded component evidence, not a full ending.
Root owns real ModContext/OwnedModServices first-fault composition and final API
export. This is a stable native boss packet, not whole-FBZ completion.

Normal commit policy first rejected an incorrect Agent-Docs classification (the
dated plan is not AGENTS/CLAUDE policy); its trailer was corrected to n/a. The
remaining genuine @ModApi surface requires root-owned compiled candidate pins, so
the annotated seven-file staged packet is handed off intact without bypass,
annotation stripping or unrelated signature edits. Prepared exact message has
all seven documentation trailers and the GPT-6.1-Sol coauthor; the latter was
verified with `git interpret-trailers --parse`. No push/merge/tree removal.


## R2 neutral-abort contract reversal (2026-10-15)

Root's composed-host assessment rejected the earlier approved first-abort STOCK
fallback: quarantine alone does not revoke assembled/cached controllers/object
callbacks. Removed ONLY that neutral catch and reversed the native birth witness
to assert propagation after allocation, before commitment or shake/rebase tail.
The partial accepted root remains STOCK/uncommitted/not-started in the aborted
frame; only host discard/teardown owns its removal. No retry, native rollback or
successful-encounter restoration is attempted after abort. Absent/withdrawn STOCK
and pure captured postbirth EXTRA_BEAT lifetimes remain unchanged. Root owns
actual ModContext/OwnedModServices frame teardown composition.

The seven-file99ee348f packet/tree41119ea3 remains immutable evidence of the
superseded fallback. New two-Java frozen overlay on that tree has SHA-256
`e1e97f41aab88e3de666a00972975b87a556db4d2dec2048de2323092706ec02`.
75637 command: `python3 tools/testing/maven_queue.py --lean -Dmse=off
'-Dtest=TestFbzCampaignBossBirth,TestFbzCampaignBossRecipe#destructiveGraphRestorePreservesCommittedEnumCounterAndForwardReplayWithoutLookup'
-Dopenggf.headless.gl=surfaceless-egl test -B`.
Fresh XML8 pass (birth6+committed EXTRA destructive replay2),0 failures/errors/
skips; wait437s,Maven1m17s,terminal2026-10-15T20:55:54+01:00. No Java/source change
followed that tested overlay. Earlier first-abort STOCK expectations in59823
are retained as history and superseded, not current acceptance evidence.
No live request remains. Hand off a separate full-index incremental packet
against41119ea3; preserve real index and99ee patch. All route/punish/readability/
cold host gaps above remain open.


## Final R1 wording clarification

The leading contract now states final first-abort propagation, matching the R2
owner/test correction; the rejected earlier fallback stays in historical evidence.
`nativePeakObjectCount()` retains its name/value25 and now explicitly documents
16 steady plus9 single-volley nodes, not a global maximum; the measured Knuckles
continuous-volley graph reaches30. Only this plan and JavaDoc changed against
447113cde5083db37935f9d5048190b1116c6e11. No timing, behavior, tests, API shape or
new validation run changed; comment-stripped source matches that accepted tree.
99ee/fd1 packets and the real41119 index remain intact.
