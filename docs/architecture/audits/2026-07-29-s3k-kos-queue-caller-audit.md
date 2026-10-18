# S3K Kosinski queue caller audit

Date: 2026-07-29

## Scope

This audit covers every `Queue_Kos` and `Queue_Kos_Module` call in
`docs/skdisasm/sonic3k.asm`, the queue processors and reset sites, every gameplay poll
identified by the hardware-timing inventory, and the corresponding production owners in
the Java engine. The disassembly is evidence only; runtime bytes continue to come from
the user-supplied ROM.

The scan found 187 queue call instructions, including tail-call forms and the internal
module-to-direct submission in `Process_Kos_Module_Queue`. Coalescing only immediately
adjacent calls under their nearest local label gives 124 submission clusters. Rolling
those clusters up to the nearest stable routine/object owner gives the 90 owner rows
below. The 187 instructions remain distinct FIFO submissions; neither rollup is a runtime
deduplication.

The inventory is reproducible from the disassembly with:

```bash
rg -n -B20 '(^|\\()Queue_Kos(_Module)?\\)?\\.(l|w)|bsr\\.(s|w)[[:space:]]+Queue_Kos|jmp[[:space:]]+\\(Queue_Kos' \
  docs/skdisasm/sonic3k.asm
```

The complete 124-row cluster ledger is checked in beside this audit as
[`2026-07-29-s3k-kos-queue-submission-clusters.tsv`](2026-07-29-s3k-kos-queue-submission-clusters.tsv).
Its kind column describes the cluster's first call; the instruction-level extraction is
authoritative where a cluster contains both kinds. Repeated calls on one line do not
occur. The earlier hardware-timing audit independently found 24 gameplay polls of
`Kos_modules_left` and two polls of `Kos_decomp_queue_count`.

## Queue contract

`Queue_Kos` submits RAM decompression. `Queue_Kos_Module` submits VRAM art modules and
uses the same direct FIFO for each child module. Gameplay consumers that test
`Kos_modules_left` wait for the global module queue to empty; retained module handles own
payload and identity only. Direct consumers in AIZ and ICZ wait on the global direct
queue. FIFO order matters when direct and module work overlap.

The engine's session-owned `S3kRuntimeArtCoordinator` models both queues and exposes both
global queue predicates and per-submission handles. A ROM poll of `Kos_modules_left`
maps to `modulesLeft()`, and a poll of `Kos_decomp_queue_count` maps to the direct global
pending predicate. Handles remain necessary for payload ownership, claiming, rewind, and
stable replay identity; handle readiness alone is equivalent to a global-empty poll only
where the lifecycle proves that no later work can overlap. A fixed frame delay is not an
equivalent completion predicate: cost varies with compressed input, contention, replay
timing authority, and module child scheduling.

## Submission inventory

The following table is exhaustive at the stable-owner level. `D` and `M` summarize the
kinds owned; the adjacent TSV preserves every distinct cluster edge and first call line.

| Area | Owners (kind, first line) |
|---|---|
| Queue infrastructure and synchronous modes | `Process_Kos_Module_Queue` (D 2740), `Wait_SegaS3K` (D 5528), `SK_Alone_Title_Screen` (D 6427, M 6484), `Obj_SKTitle_SonicFallMain` (M 6747), `Obj_SKTitle_SonicFallFinish` (M 6815), `LoadLevelLoadBlock` (M 9727) |
| Special-stage and form support | `Draw_SSSprite_FlyAway` (M 12610), `Obj_HyperSonic_Stars` (M 34458), `Obj_SuperTailsBirds` (M 35008), `SSEntryRing_Display` (M 128487), `Obj_HPZSSEntryControl` (M 197745) |
| Resize, common UI, and enemy PLC | `AIZ1_Resize_Index` (D 38921, M 38995), `AIZ2_SonicResize4` (D 39112), `AIZ2_KnuxResize4` (D 39223), `LBZ2_Resize_Index` (D 39509), `Map_StarpostStars` (M 61886), `Obj_TitleCardInit` (M 62124), `Obj_LevelResultsInit` (M 62522), `SpecialStage_Results` (M 63063), `LoadEnemyArt` (M 64308) |
| HCZ runtime objects | `HCZWaterWall_Horizontal_QueueArt` (M 64857), `HCZWaterWall_Vertical_QueueArt` (M 65120), `HCZLargeFan_QueueArt` (M 65607) |
| Zone transitions and refreshes | `AIZ1BGE_FireTransition` (D 104672), `HCZ1BGE_Normal` (D/M 105723), `MGZ1BGE_Normal` (D/M 106290), `Obj_CNZTeleporter` (M 108054), `FBZ2_ScreenInit` (M 109318), `ICZ1BGE_Normal` (D/M 110265), `LBZ1BGE_Normal` (D/M 111210), `MHZ2_ScreenEvent_Index` (M 112596), `MHZ2_BackgroundEvent_Index` (D/M 112953), `SOZ1_BackgroundEvent_Index` (D/M 113652), `SOZ2_BackgroundEvent_Index` (D/M 114604), `LRZ1_BackgroundEvent_Index` (D/M 115281), `LRZ2_BackgroundEvent_Index` (M 115833), `SSZ1_ScreenEvent_Index` (D/M 115983), `DEZ1_BackgroundEvent_Index` (D/M 118667), `Ending_ScreenEvent_Index` (D/M 120994) |
| Credits, continue, and ending objects | `PLC_SKCredits` (M 121798), `Obj_Continue_TailsWSonic` (M 123372), `Obj_5D86A` (M 123703), `Obj_5DFEE` (M 124263), `Obj_Ending` (M 124417), `VInt_TableFC` (M 124814), `Obj_5EF68` (M 126043) |
| Knuckles cutscenes | `MHZ1CutsceneButton_LoadKnucklesPeer` (M 130081), `CutsceneKnux_MHZ2_Index` (M 130459), `Obj_SkipIntro` (M 130852), `Obj_LRZ2CutsceneKnuckles` (M 131149), `CutsceneKnux_HPZ` (M 131825), `PLC_KnuxHPZCutsceneShip` (M 132863), `CutsceneKnux_SSZ_Index` (M 133588) |
| AIZ and HCZ bosses/cutscenes | `AIZPlaneIntro_PlaneChildInit` (M 135738), `Obj_RobotnikHeadInit` (M 136067), `Obj_MechaSonicHead` (M 136261), `AIZEndBoss_StartArenaLock` (M 138037), `HCZEndBossGeyser_LoadArt` (M 141553) |
| MGZ through LBZ bosses | `Obj_MGZ2DrillingRobotnik` (M 142399), `MGZ2DrillingRobotnik_Index` (M 142644), `MGZEndBoss_Index` (M 142763), `Obj_MGZEndBossKnux` (M 143007), `Obj_CNZEndBoss` (M 146062), `FBZMiniboss_Index` (M 146802), `FBZ2Subboss_Index` (M 148169), `FBZEndBoss_Index` (M 148949), `LBZFinalBoss1_Index` (M 152145), `LBZEndBoss_Index` (M 153380), `LBZFinalBoss2_Index` (M 154282) |
| MHZ through LRZ bosses | `MHZMiniboss_Index` (M 155659), `Obj_MHZEndBoss` (M 156907), `Obj_SOZMiniboss` (M 157825), `SOZEndBoss_Index` (M 158803), `Obj_LRZMiniboss` (M 160066), `Obj_LRZ3Autoscroll` (M 160911), `RawAni_7917E` (M 161314), `Obj_LRZEndBoss` (M 161573) |
| SSZ through DDZ bosses | `Obj_SSZGHZBoss` (M 162600), `Obj_SSZMTZBoss` (M 163041), `SSZEndBoss_Index` (M 164233), `PLC_KnuxFinalBossCrane` (M 166202), `Obj_DEZMiniboss` (M 167686), `Obj_DEZEndBoss` (M 169561), `DEZ3_Boss_Index` (M 170946), `ChildObjDat_81330` (M 173282), `DDZEndBoss_Index` (M 173587) |
| Late object-file owners | `LBZ1Robotnik_Index` (M 192194), `Obj_LBZMinibossBoxKnux` (M 192665), `Obj_LRZRockCrusher` (M 197032), `Obj_EggRobo` (M 198480) |

The owner table is the lifecycle map. The extraction command above is the instruction
level check: it includes multiple queue calls inside a single cluster (for example the
two direct plus one module submissions in seamless transitions), tail calls, and the
module processor's child submission. Review of all 187 matches confirmed no call lies
outside one of the owners listed here.

## Engine disposition

### Queue-correct production owners

The engine already submits and retains queue identities for AIZ direct/module event work,
the ICZ act transition, title cards, results, StarPost bonus art, the CNZ teleporter, AIZ
plane/boss work, HCZ water walls/fan/geyser, and the implemented AIZ/HCZ/MGZ/CNZ/ICZ enemy
art profiles. These owners use production ROM descriptors and retain their submission
handles. Predicate parity is a separate question: owners that currently use handle
readiness where the ROM polls global empty require an overlap proof or conversion to the
global predicate. They are not declared queue-predicate-correct merely because submission
identity is correct.

### Confirmed mismatches

| Engine owner | ROM behavior | Current behavior | Required correction |
|---|---|---|---|
| HCZ1-to-HCZ2 | Two direct jobs followed by one module job; transition polls global module-queue empty (`sonic3k.asm:105723-105754`). | Only the module job is submitted and the engine polls its handle. | Submit the two direct jobs first, retain all handles for rewind/lifetime, and gate on global module-queue empty. |
| MGZ1-to-MGZ2 | Two direct jobs followed by one module job; transition polls global module-queue empty (`sonic3k.asm:106290-106314`). | No queue submission; a 26-frame timer approximates decompression. | Replace the timer with exact direct/module descriptors and gate on global module-queue empty. |
| LBZ1-to-LBZ2 | Two direct jobs followed by one module job; transition polls global module-queue empty (`sonic3k.asm:111210-111235`). | No queue submission; a 55-frame timer approximates decompression. | Replace the timer with exact direct/module descriptors and gate on global module-queue empty. |

These are synchronization-affecting gaps: completion changes the frame on which act
state, layouts, managers, camera/event state, and subsequent object processing advance.

### Deferred by ownership, not assumed correct

The remaining disassembly owners fall into three classes:

1. An engine owner is not implemented yet (many later bosses, cutscenes, ending, and
   credits). Queue work belongs in that owner when it is ported.
2. The engine currently preloads an asset through level/bootstrap infrastructure and has
   no ROM lifecycle consumer. This may render correctly but is not queue-timing parity;
   it must be revisited when the owning lifecycle is implemented or traced.
3. The owner exists but has not yet been compared instruction-for-instruction. Later-zone
   transitions, post-boss refreshes, and implemented bosses are the highest-risk members
   because the ROM has an explicit completion gate.

No deferred entry is treated as evidence that eager loading is semantically equivalent.
The implementation pass prioritizes confirmed active owners with explicit transition
gates; future object/zone work should consult this inventory rather than adding a global
queue call or a game/zone carve-out.

## Reset and rewind findings

Queue state is session-owned and cleared only by the runtime-art coordinator's session
reset lifecycle. Seamless level loads do not indiscriminately clear it; ICZ deliberately
transfers prepared jobs across the act handoff. Event owners capture scalar submission
ordinals and submitted state in rewind snapshots. Queue facades and handles are transient
and rebound through `HardwareTimingService.pendingHandle(kind, ordinal)` after the timing
ledger restores. Restoration must not resubmit a duplicate job. No static queue state or
singleton access is required.


## Bonus-title archive omission independently corrected — 2026-10-17

The earlier broad title-owner inventory did not prove every specialized title
initializer submitted the complete native batch. At skdisasm044
Obj_TitleCardInit62143..62200, the locked-on Gumball portal61952..61959 writes
current/apparent$1300 and still queues Num1 at$53D between Zone and Bonus.
Visible BONUS/STAGE child count does not remove that physical archive. R23's
actual successful-submit caller witness exposed three engine archives; the
separate actual-ROM typed Knuckles/Gumball R25 component reached the final
four-descriptor assertion and failed only for Num1(source880004/compressed185/
dest42912/output512/module1). Existing apparent-act selection now submits it
through loadBonusArt's unchanged queue/lease owner, leaving ordinary title loading
and all timing admission/refusal contracts intact.

R28's exact affected selection passes120 unique cases in15 actual classes,
including the original independent oracle, complete four-archive service/drain/
claim control and two registered five-key resource/title restore/forward cycles.
R29's fresh two-class guard request is97/1F0E0S: the sole LevelManager3153vs3144
size-ratchet failure blocks the conditional maintained original route. This is
local component preservation, not integration, a full guard pass, repaired
recorded readiness or complete two-module/four-direct producer attribution.
Canonical completion descriptors have no native enqueue timestamp/caller. Keep
R23's measured pre-fix births distinct from a statically inserted Num1 job;
there is no post-fix route execution in this slice. [Exact epochs, commands and
STOP](../plans/2026-10-13-s3k-playable-completion.md#r29-conditional-guard-gate-red-original-maintained-route-not-launched--2026-10-17).


## Bonus terrain handoff independently retained — 2026-10-18 local component

The plain bonus load does not own the ordinary fresh-load context/SST graph, but
native Level/loc_6310 -> LoadLevelLoadBlock still submits its ROM terrain after
Obj_TitleCardWait and Nemesis readiness. R34 actual production Knuckles/Gumball
entry reached the final independent descriptor oracle: four correct title jobs,
missing terrain3FEE2E/compressed3407/dest0/output9792/modules3. R32's earlier extra
AIZ title batch was an inlet boundary, not clean old-red credit; observe the real
origin TITLE_CARD phase and consumed pending title request before declaring boot
quiescence. Do not hide those jobs with filters or reset the FIFO.

The narrow LevelManager eligibility now retains the existing captured title
resource index for an actual active bonus as well as the old fresh-load cause.
GameLoop onEnter sets active type before loading and onExit clears it before
return. Omitted(false)/initializeBonus preserve the index until the native gate;
no caller mode switch, ordinary four-child graph, queue/admission/ordinal/schema
change or public API. R36 preserves all original three oracles and adds real
Pachinko/Slots controls:145/0F0E0S,19 actual classes,10 selected entry/return pilots.
WaterRestore's immediate reflective enter/exit logs FIFO-full on actual Main803
too (Root69090), so its water result is not art readiness. Existing child size
ratchet and fresh combined guards remain separate. No post-fix original route,
full remaining two-module/four-direct attribution, native births or recorded
SS/fdf readiness is established. [Exact evidence and epochs](../plans/2026-10-13-s3k-playable-completion.md#r37-qualified-component-preservation-and-inherited-warning--2026-10-18).



## Saved-origin return cause and next physical queue boundary — 2026-10-18

An independent real public bonus-entry/exit test reaches its final receipt oracle
with ORDINARY instead of INTERIOR_RETURN, after normal title release/new-generation/
saved-origin/history checks. The synchronous saved-origin GameLoop load now marks
its existing semantic cause immediately after prepareReturnLoad and before the
load, following the special-return helper precedent. Null-state fallback and
ordinary loads are unchanged; a separate later-ordinary-load control proves the
mark is consumed once. Component128 and fresh99 guards pass on529012/74f65.

The original unmodified Mega R44 subsequently attaches returned segment2 and fails
AIZ2 event submission with real module FIFO-full. Its29 unmatched recorded identities
and partial first34 busy mismatch do not identify actual queue occupants, native
submission births or the unique original R38 denial cause. Keep successful component
semantics separate from unchanged strict timing admission/physical capacity and the
unvisited SS/fdf target. No queue expansion/reset/drop/ordinal renumber or readiness
waiver follows. [Full original reports, source epochs and limits](../plans/2026-10-13-s3k-playable-completion.md#r44-actual-fresh-guards-and-original-returned-aiz-frontier--2026-10-18).


## Post-extraction source guards and original route boundary — 2026-10-18

R38's source-equivalent geometry relocation passes99 actual fresh guards
(Architectural72/HardwareAuthority25/exact API2). Its following unmodified Mega
route fails coordinator segment2 admission in TRANSITION_GAP after the original
stage_exit and return assertions passed. This is neither proof of actual LEVEL
release nor post-return SS/fdf readiness. The sole suppressed close reports12
unmatched exact completion identities; no actual production head/preparation was
sampled here, so never-submitted versus already-admitted/claimed remains ambiguous.
Partial AIZ1/Gumball12 physics reports are incomplete, recent5 is bounded and actual
compared/reference counts unavailable. Root read all8 original artifacts before
accepted hash-checked cleanup; no additional execution, producer change, arithmetic
renumber, forced alignment or admission waiver follows from these facts.
[Actual commands, source pins and precise reached boundary](../plans/2026-10-13-s3k-playable-completion.md#r38-phase2-original-route-actual-stop--2026-10-18).


## Actual later physical occupants, outside-step evidence — 2026-10-18

R47's original-driver call-through witness at c4e71e8 observes the new returned
INTERIOR_RETURN receipt/generation4 and all four return-title parents15..18 claimed.
At the later AIZ2 FIFO-full, four different unprepared module parents19..22 occupy
capacity: sources36800C/367DCA/3681FE/3AF5D0. Parent19's actual active child26 has
a complete decoder/payload736 but is not ready; it remains physical direct head
ahead of the three newly submitted event direct jobs27..29. Descriptor identity
does not identify which enemy-loading caller submitted parents19..21.

All177 rows/25 definitions/175 state and clock projections, original error/closure
and comparator owner arrays were directly inspected and accepted by Root. No
inside-service, successful-submit or native-birth context was recorded. Pending
ledger/title-history handles must not be mistaken for physical capacity occupants.
Original1E/partial21196 returned errors and SS/FDF-unvisited limits remain; no
readiness waiver, renumber, queue expansion/reset/drop or producer fix follows.
[Exact observations, acceptance and cleanup](../plans/2026-10-13-s3k-playable-completion.md#r48--accepted-outside-capacity-witness-accounting--2026-10-18).


## Successful-submit callers and absent returned terrain — 2026-10-18

R50's reviewed temporary private submit logger and original full-driver subclass
provide actual successful-submit evidence on923da24+81096/40be, not another
source-only caller inference. All36 successful submits/144 job-handle-submission-
preparation refs are represented, with fresh raw owner/load/title/resource lease
context on the same service. Enemy parents19..21 are submitted by the ordinary
processRuntimeArtQueue -> processEnemyKosArt path; fire22 is submitted from
updateAct1 -> ensureFireOverlayTilesLoaded. Neither is assigned to WaitFire by
descriptor identity. Earlier lease-consume callback/clock remains unobserved.

Gumball terrain14 is a production title-handoff job3FEE2E/9792bytes/3modules.
Returned title15..18 initializes on generation4 but its handoff index is-1, and
no3A566A/3A944E successful submit appears in this invocation. R48's raw native
archive association is distinct evidence with no native enqueue PC/clock/final
VRAM/explicit parent link. Do not substitute ordinals, force readiness or expand
capacity from these facts. Original AIZ2 FIFO-full1E, incomplete comparator
1/12/21196 errors and unvisited SS/FDF remain. Root directly accepted the entire
63-row journal/XML/TXT/comparator/owner evidence before exact accounted cleanup.
[Exact R50 inspection and R51 accounting](../plans/2026-10-13-s3k-playable-completion.md#r51-accepted-submit-owner-evidence-and-accounting--2026-10-18).


## Saved-origin terrain producer component, unresolved route readiness — 2026-10-18

R53 independently exercises public Gumball/Pachinko/Slot return to Knuckles/AIZ1:
actual pending title/gates/new receipt/history/current identity precede the native
six-parent oracle. All three fail only that oracle on b0dcc (four title parents,
missing3A566A/3A944E); ordinary non-fresh control passes. LM b36ebe adds only the
existing saved-bonus-return signal to title terrain-handoff eligibility, retaining
actual resolved level sources and original queues/gates. The unchanged component
passes in144 affected cases;99 fresh structural/authority/API guards pass.

R56 original Mega still errors at AIZ2 primary module enqueue with FIFO full.
The current route has20 unmatched completion identities and incomplete comparator
1/16/21196 errors; actual returned first34 module.busy true/false. Gumball's
recent1380 active direct source3A566C is retained evidence, not the exact caller/
submit/service chronology. R50's actual enemy19..21 ordinary runtime-pass caller
and R47's four unprepared occupants belong to OLD LM epochs; neither proves the
current variant's birth sites/physical holders/lease consume callback. Canonical
completion rows never fill those missing native birth fields. No renumber,
readiness waiver, FIFO expansion/reset/drop or next producer implementation
follows automatically. Root accepted full original reports before accounted
cleanup; SS/FDF is still unvisited.
[Source/test epochs and full limits](../plans/2026-10-13-s3k-playable-completion.md#r56-actual-fresh-guards-then-original-mega--conditional-two-phases-terminal).


### Saved-return title owner qualified; current service chronology remains open

FDF00ff5efed preserves installer53f199/testee1c after168 affected tests and99
fresh guards passed. Native loc_62B6/loc_64DC admits the saved-return fixed graph
without making the whole host load fresh. Original R62 Mega still errors at
AIZ2 FIFO2932<-2926<-205; POST35's expected module21 is explicitly unprepared,
while later expected direct34/module22 sees pending33/21. No renumbering, missing
producer or physical retirement cause follows. Current consume+submit+queue
chronology is a separate temporary diagnostic; its retained-only footer requires
correction before execution. No diagnostic hook is included in this composition.

[Composition and limits](../plans/2026-10-13-s3k-playable-completion.md#root-composition-saved-return-fixed-title-ownership-and-current-strict-limits-2026-10-18).
