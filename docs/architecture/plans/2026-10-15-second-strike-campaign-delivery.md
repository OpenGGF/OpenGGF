# Second Strike — two-team campaign delivery

Status: implementation authorized on 2026-10-15; both Sol team leads running. No new
gameplay is claimed by this checkpoint. The binding content scope is the
[complete AIZ prototype and S3&K campaign proposal](../designs/2026-10-15-second-strike-campaign-scope.md).

Pinned pre-task destination: `develop`
`76022570e8233823e1e36419666a0f29c768f4b6`, fetched and fast-forward checked.
Both implementation teams start there. Main's existing three dirty disassemblies
and five untracked user files remain outside this task.

## Teams and ownership

| Owner | Models and remit | Private branch / worktree |
| --- | --- | --- |
| Integration lead | Root Sol: shared-contract sequencing, composition, delivery-wide validation, normal develop integration/push and accounted cleanup. | `feature/ai-second-strike-campaign-delivery`; `.worktrees/ai-second-strike-campaign-delivery` |
| AIZ team | Sol lead and Sol implementers; Astra reviews coherent change batches. Complete M0–M3, both apparent acts, native branches and HCZ handoff. | `feature/ai-second-strike-aiz-completion`; `.worktrees/ai-second-strike-aiz-completion` |
| Remaining campaign team | Sol lead with Sol and Sonnet implementers; Opus reviews coherent campaign waves. Implement M4–M6 across every remaining zone, stage-family variants and ending/resource closure. | `feature/ai-second-strike-campaign-expansion`; `.worktrees/ai-second-strike-campaign-expansion` |

Live catalog selections: Sol `codex/gpt-6.1-sol`, Astra
`codex/gpt-6-astra`, Sonnet `claudeAgent/claude-sonnet-5-5`, Opus
`claudeAgent/claude-opus-5-5`. Discover availability before child dispatch; never
silently substitute another model for a requested reviewer. Use saved app-owned
delegated tasks, with bounded child outcomes and results retained in task history.

A team lead may own two concurrent implementation workers, each in an isolated
worktree with disjoint files. Add reviewers at coherent batch boundaries rather
than dispatching one review per small edit or idle watchers. T3 child-task IDs
and batch state are recorded below as they become available.

## File boundaries and shared contracts

- AIZ owns its authored route, badnik refinements, AIZ native owners and tests,
  and the example's existing shared registration/resource/combat files. It owns
  shared Mod API/candidate-pin edits only in explicitly sequenced contract
  batches coordinated by root. Campaign consumers must not fork these contracts.
- Campaign owns new `secondstrike/campaign/` zone recipes, their focused tests,
  and the corresponding non-AIZ native zone-owner extensions. Its public entry
  point is handed to AIZ/root for registration; it does not concurrently edit
  `SecondStrikeMod`, existing `SecondStrikeRoutes`, common enemy files, API pins,
  API version descriptors, shared placement/compiler code or the root README.
- Campaign requests a missing semantic extension with exact owner, bounded
  recipe, stock-default behaviour, callers and focused checks. Root sequences
  the contract before its consumers, using accepted commits in private trees
  only after live builds/tests drain. No speculative generic difficulty engine.
- Each team owns a separate dated implementation plan under
  `docs/architecture/plans/`; root owns this coordination plan. Shared README,
  changelog, coverage index and final API export are reconciled by root from
  stable team patches. Do not rewrite the delivered evidence as future claims.
- Never mutate another team's tree, main, unrelated worktrees, queues or locks.
  Create/remove worktrees under the normal exclusive main-worktree lease.

## AIZ implementation batches

1. **A0 — combat/resource/native contracts:** reproduce or dismiss the P2
   hit-separation risk with actual attacks; implement the bounded recovery if
   needed. Lock scarce ring/monitor rules, bonus-invitation lifecycle and narrow
   brittle-log support. Preserve native physics and absent-policy defaults.
2. **A1 — complete apparent Act 1:** author A1–A7, not just the first four
   encounters; preserve forest/fire identity and semantic release boundaries.
   Finish Fire Breath warning/recovery and real cold/checkpoint routes.
3. **A2 — true Act 2 and completion:** A8–A11, early Bouncer feasibility spike
   and admit/reject decision for A12, Knuckles' distinct finale, waterfall-boss
   branches, reward/bonus/rewind/save lifecycle and playable HCZ handoff.

Astra reviews these as meaningful source batches, with precise findings and
evidence. Fix blockers in the owning team; subsequent review covers fixes and
unresolved objections, not repeated unchanged code. Final handoff must distinguish
implemented, runtime-observed and rejected/deferred stretch content.

## Remaining campaign implementation waves

1. **C1 — Sonic 3 half:** HCZ → MGZ → CNZ → ICZ → LBZ. A zone receives its own
   level, enemy, event and boss treatment, with both acts and character branches.
2. **C2 — S&K body:** MHZ → FBZ → SOZ → LRZ. Wind, footing, darkness and industrial
   pressure retain native movement, control, lifecycle and readable recovery.
3. **C3 — finale and product closure:** HPZ → SSZ → DEZ → eligible Doomsday;
   fourteen Chaos/Super boards, all three bonus families and ending/save/resource
   integration. Knuckles ends through his own SSZ branch. Doomsday is exempt
   from the ordinary five-world-ring rule because rings are flight fuel.

Use the proposal as an implemented-content blueprint, not merely another roadmap
or one global difficulty multiplier. Start source-grounded zone workers in
parallel with AIZ; contract dependencies are coordinated, not guessed. Existing
native route blockers are explicit prerequisites, not license to advertise an
unexecuted hard route. Do not silently absorb unrelated parity repair.

Opus reviews stable wave-sized changes, including route intent, counterplay,
native/mod boundary, character differences, lifecycle and evidence limits. The
team includes actual Sol and Sonnet implementation work; reviewers do not own
the implementation or repeat full-suite validation.

## Verification and delivery

Production viewport captures use the actual `WidescreenAspect` and
`GameplayCaptureSession` presets: 320, 352, 400, 528 and 800 pixels. Root checked
both owners. The general testing standard's 512/640 widths are inherited research
overrides, not production capture presets; record those obligations separately
rather than claim unsupported presets or silently certify coverage.

Workers run focused checks appropriate to owned behaviour. Use original absolute
ROM paths/hashes; new game code keeps required S3K startup checks applicable.
Native state and ROM owners remain authoritative; trace rows are comparison only.
Headless tests and offscreen capture are the default. Never automate the user's
desktop focus/keyboard. Native window diagnostics require an owned isolated
display and fail closed before launch when isolation is unavailable.

All test-running Maven commands use the intended-tree queue; build-only commands
hold that tree's build/test lease. Keep current queued requests, source freezes,
required graphics flags and admission reservations intact. No worker submits a
broad run. Root inspects the final change-selected batched **smoke** plan against
the pinned integration base, adds semantic dependencies where necessary, states
count/cost/stopping rule, preflights the actual environment and owns combined
coverage plus fresh guards. Release-only full-suite is not routine validation.
Disputed failures get bounded matched baseline/current checks by full identity,
assertion and skip reason. Consume/acknowledge category diagnostics normally.

Before a batch handoff, provide stable commits, exact source/tested state, changed
files/contracts, reviewer task IDs/findings/resolutions, commands/results/skips,
real route/rewind/capture observations, remaining gaps and cleanup ownership.
Keep strict creator packaging at zero unsupported references, matching compiled
candidate pins and actual engine/SDK/test-kit consumers. Do not weaken guards or
copy native implementation into creator content to bypass missing APIs.

Only root integrates into and pushes the current main-workspace `develop`
branch after applicable checks. Teams keep feature branches local and do not
create PRs or mutate main. Remove only fully accounted merged task worktrees and
branches, preserving user config/ROM/save targets, useful captures and histories.

## Compact execution checkpoint

Root private branch remains `feature/ai-second-strike-campaign-delivery`, HEAD
`bf8a85ae7908bfb11054f3c14d859d7968c738ca`, with the exact sources below staged,
plus root-only host integration tests. This plan stays unstaged until a coherent
source/pin commit. No main integration, push or completed campaign is claimed.
The pinned pre-task destination remains `76022570e8233823e1e36419666a0f29c768f4b6`.
Preserve main's unrelated three dirty submodules and five untracked user files.

### Stable private composition

| Packet | Paths | SHA-256 or commit |
| --- | ---: | --- |
| Initial native AIZ | 20 | `cc6fc5fd24714fb9697e211fecccbf4c28d12ae63aafd1283ad239dbe4473abe` |
| Native A0 review fixes | 10 | `b6e1d0e01afbee5ac8c2cc41d903a49d0a45ea8d745ac6f373a7dd19e83f6f53` |
| Native null-main fixture | 2 | `917d243f35c6e012a76bb07044c4c2fbf5d83755dc8fc8218e283de03c9f03d4` |
| Invitation, inventory merged by intent | 7 | `4c0d2452f5d229f8cc9e608ed0711e47213f8880a2b6ef8c3e8fc6058f9412e5` |
| Coupled invitation producer test | 1 | `28a36ee68685bf85aa262ab01101e90c1a90e9955655ec8a58dc5d754772b90e` |
| Initial content, inventory merged by intent | 11 | `04d044a12ea3f02fdbfbee5ff2ee19771e9f260f364e184c9b8cf1c4eeb09adc` |
| Lookout and route-driver repair | 7 | `2dc99cedc58523d62ec10742433263bd1e58af8e4adbcf58941b3d63ba54a8b0` |
| Combat/source and focused tests | 8 | `0f8416615a02109e1e7e58b6da53e3cb244f1c66609d17bf3733bb3911c3c849` |
| Combat four-player fixture only | 1 | `9ded496bbf108f77dd97f7527d79e9061de0e753e122288a00ff685fed7e6c5b` |
| SPLIT registration/static-map repair | 5 | `f36a0224454538ecbbd50ff54f4710c93200eb9dccbb535ed5eb9a6a13b3748b` |
| Fresh owned enum/AIZ policy lifetime | 2 | commit `0b32030b74fdb7074af3eb668b32f73370d7d875` |

| Native explicit fixture isolation | 2 | `6d607e977ccc5d45b5c7cfd9dc0031f28e4707ba3675cba4867023f2051a4ce9` |
| Native isolation outcome prose | 1 | `637e7b7ecbac272627c018315363dfca1d2d70a2d75816dafb39105f62e27595` |
| Lookout fixed-point fixture | 1 | `bfb00a0348ac254fb871c3b71faf987056ea6c2b0e2d8d965de8a838c59b7c6d` |
| C1 HCZ/MGZ native/facade packet | 23 | `bd891b1162afc15f0bce021c8b62f2143b2723cab49b0b059f1c6af2b77b00e9` |
| C1 inventory union, by intent | 1 | `1be2dc021c36fe5372b54f95b9a44ce29d3788175b4944d8d805c12eaaddf7e9` |
| Fourteen-board policy and real provider | 4 | commit `e0efcb372f9131ce959c5f2487d2acca0cdd977e` |
| Atomic provider registration | 1 | `af2a4f5108353e7297cfc5f168076ecd1d1c3c2ea51528169e07a182ebd3d25f` |
| C1 east native/facades/creator helpers | 18 | `8e5eb676c4f207399366641ddcbccfdc3a5f8d55e397d8c3245ca3784ed145c0` |
| Five-width native witness | 1 | `432463d5599d56f82b8fd6773fb6a213d8fdd89c3815351098bd1b41974f0f61` |

All non-inventory imported disk/index blobs match their source snapshots. The C1
packet used abbreviated Git blob names; root expanded them through the shared
object database before exact checking. Inventory unions retain every prior AIZ
root; C1 adds only three factories and the explicit event-born MGZ boss recipe
root, with the other four recipes reached by genuine signatures. No speculative
C2/C3 roots, copied targets, fake pin or hook bypass. Stable team/helper trees
remain for final ancestry and accounted cleanup.

The real campaign provider is now present and called directly from SecondStrikeMod.
Only its old BlueSphere service registration is removed; the same native-strength-3
Gumball provider remains. Fourteen-board source/admission evidence is separate from
root's new actual ModContext -> owned provider -> native admission test, pending.
Campaign owns provider source; AIZ/root owns this atomic shared call.

### Root verification

Current root source is compiled by33094, terminal0:4,022 production and3,953 test
sources, Java25,67seconds, no tests. The C1 candidate pin/SDK export is deliberately
HELD for the first Opus review's bounded contract repairs; the old20,942-line AIZ
pin is not claimed to describe C1. West's20,994-line observation remains attributed
to36397 only. No broad/API/SDK acceptance has occurred.

OpusC1R1 is terminal read-only, NOT acceptance. Root checked the relevant owners:
MGZ2 Knux actually spawns by event at3D20/68 while the layoutA2 registry is ungated;
remove the upcoming knucklesBoss facade/binding and use the immutable event service
for both MGZ2 branches. Preserve the inherited registry gate gap as a real route
prerequisite, without absorbing an unrelated parity repair. Spiker's preset must
change only initial detection, restoring native64px open/launcher gates. LBZ must
check existing escort before siren/timer and retire on native LBZ script flags,
not every object-controlled gadget. One attempt is per allocated alarm-instance
lifetime (including failed slots), captured through rewind; ordinary native reload
makes a new instance, max one live escort remains. No global saved ledger.
Authentic ICZ carried rings1/7 plus trueAct2{84,164,341} fit five apparentAct1/three
trueAct2 with three physicalAct1 rings; this is a content repair, not zero-ring
checkpoint or route proof. MGZ wait/punish readability remains open. Existing
workers own repairs after relevant freezes; no new implementer or unchanged review.
A fresh R2 will include original scope, all findings/responses and unresolved gates.
Root's two affected host methods (waterfall fault/recreate and actual published
board admission) may run independently of this held C1 public export; their source
will stay frozen while queued/executing. No native DFS/bootstrap repetition.


Latest build-only36397 completed0:4016main/3950test Java25,74s, no tests.
Its genuine west+AIZ+provider export20994lines SHA256
`5f4e0d5c0b0b0bb95e15162924e39f8ecfef88b3db609d76d6be98e112ca4515`
adds94/removes0 versus760,52new versus priorAIZ pin. East arrived afterward;
next compiled export covers both halves before the shared API/SDK check.

East initial1d2666 packet was overwritten before import; root checksum guard
refused it without changing source. Root recovered that EXACT hash from advertised
staged tree3bba5e7c5b2cb4acdddc17370abab443da31140c. Corrected8e5e differs ONLY
two JavaDoc ROM pointers; no executable/API/test delta. Both source snapshots are
preserved under immutable versioned handoff filenames. Root imports corrected8e5e
with explicit attribution, three actual facade inventory roots, no placeholders.
Accidental unchanged build5673 started after the refusal and finished0 in23.244s
before cancellation; it ran no tests and is no new-source or extra coverage claim.

East creator helpers currently contain static object arrays/sets, conflicting
with unchanged ModValidator.validateStatics378-389. Strict packaging has NOT yet
been run on that source; this is a source-grounded blocker assigned to existing
Sonnet for literal/local content repair, preserving every binding and rule.
Native/public contracts may compile; creator publication stays blocked.
East worker reports613affected cases and22final cases0skip, with actual native
replay/corrected trueICZ2 ring inventory, not playable routes. New OpusC1R1 audits
stable code and original scope under campaign ownership, task
`node:delegated-task:command%3Amcp%3Ae2c49b22-ef64-43d3-b46f-ac1f66a333e7%3Adelegate-task%3Asecond-strike-c1-opus-review-20261015-r1`;
this is not M4/wave completion. Native width76265 passes5parameterized cases0skip
at320/352/400/528/800, configured camera/native floor/tell/hurt/first dash; exact
one-test delta is imported. No cold/pixel certification.


- Build-only1565 compiled4008 production/3945 test sources, no tests. The actual
 20942-line export equalled the candidate pin (42 additions/0 removals versus base),
 SHA256 `f00280288a89465ff73f8a9afd31362eb2ee6a455468f1faf6c1e69ee9f9195a`.
 It predates later imports/C1. The current composition must be compiled/exported.
 Descriptor and runtime remain unpublished0.7.0; no published version is changed.
- Helper12666 passed40 focused cases (owned6/context-fault23/policy11),0skips,
 after actual negative enum/policy checks. Fresh enum and exact
 AizMinibossAttackPolicy lookup falls back before unavailable owner providers.
 Captured values, cached callbacks, direct calls and VM-fatal behavior retain their
 historical semantics; no blanket ModFaultBoundary.call precheck.
- Root32986 is terminal exit1 after1055s admission wait/~77s Maven:3 cases,
 2pass/0failures/1error/0skips,4008main/3946test compiled. Real registration/fault
 composed with allocated native FireBreath flames and ship bombs passes. Waterfall
 fixture errored before any flame because arm initialization lacked the parent's
 current V_int clock; shouldUpdate correctly declined it. Root now sets that
 isolated parent clock exactly like the native harness and asserts two propellers.
 Only the affected waterfall method needs rerun; no production change. Mock art
 and isolated commit callbacks do not certify route, timing or visible warning.
- No current API/Javadoc/SDK/strict package/combined smoke pass on final composition.
 The next root check compiles this coherent source, regenerates its genuine pin,
 then verifies the affected lifecycle, actual provider and public-contract consumers.

### AIZ evidence and remaining gates

- Combat27230 reproduced positioned native P2 contact/recovery failure at update24,
 expected0x23/actual0; not a cold-route permanent-stall claim. Current exact player
 reference-array latches cover all engine participants, preserving native P1/P2
 attack targets. 22161's Enemies27/Barrage16/Cater2 pass plus11684's10Native pass
 cover55 identities across two runs, not one55pass batch. P3/P4 mixed/duplicate
 contact, reference recreation8+16, held rejection and native rebound pass.
 Clean local combatc69f2040/treeb16e2425 equals imported0f+9ded; no new patch.
 Supported-width entry/tell76265 remains worker-owned, no unchanged barrage repeat.
- Native42820 had62 cases/3fixture failures; actual warnings must exist and only
 proved construction x/y seeds normalize during replay. 69051 had28/0F1E due to
 null-main camera fixture;13167 had3/0F1E due to missing isolated gameplay timers.
 Exact isolation repair75203 now passes3/0F0E0skip at tree12796cff. Native slot
 retirement, whole managed child/rider graph and actual warning producer are
 retained. These are focused outcomes, not native320px readability/cold routes.
- AstraA0r1's threeP2 findings (bridge IDs/precontact cue/parent-only rewind)
 resolved at source/test-design in new A0r2. A1 and A2 coherent source reviews
 found no new actionable issues. A2 retains the conditional invitation-post risk
 below; reviews executed no tests or captures and do not certify completion.
- Invitation71942 passes22; coupled74220 passes2, all0skip. Actual factory post at
 zero rings creates stars; denied child preserves unused opportunity/pending SLOT,
 restored permit publishes GUMBALL, retires siblings, consumes once. Native void
 semantics remain; real bonus play/reward/save/death-return/all families are open.
- Lookout89847 had18/1F0E: four native ride/real landing/birth/world-restore positives,
 admission negatives and registration checks passed. Only low-speed fall fixture
 read fixed-point low16; corrected high16 method21110 passes1, production unchanged.
 Current48x64 birth guard is retained: native touch halfwidth8 + player8 + shifted
 native origin32 bounds harmful span48. No unsafe birth proved from offset alone.
 Real manager/P2/art-ready delayed-right47/48/49 witness78869 is pending after a
 test-compile IOException-signature repair. No AI-wait or invented80px safety claim.
- Strict builder39711 passed0findings after literal switches replaced unsupported
 static maps. Actual jarSHA `1f6bfedd83b5d711c74fc73f01889838680d631c54b93bc308dd9d4ccbfcfef9`.
 Cold25134 is INCOMPLETE:12281 rows native intro/forest/fire reload at11293, death
 at12280,x2601/y920 near A7, apparentAct0; no boss/results/HCZ. This is failed
 controller authorship, not route infeasibility. Retry68453 preserves source/jar,
 uses round-tripped12000-row safe input prefix then revised controls; pending.
- Supported production widths320/352/400/528/800; inherited512/640 research overrides
 remain separate. Character/team/donor/checkpoint/rewind/pixel/audio breadth remains.
- A2 original checkpoint326/sub7 may precede selected476/sub5 on lower Knuckles
 route, suppressing invitation by native mark. Actual route->eligible original
 post mapping remains open; never relax native marks or create checkpoint currency.
 A12 chaser is explicitly rejected; finite native bombing is the selected direction.

### Campaign dependencies and honest open scope

- C1 west approved HCZ/MGZ source is imported. Its67 valid focused identities across
 57789/60584/46541 pass0skip, not a whole route. Bridge manager/rider/90slot/replay uses
 synthetic art; boss counters are not punish/defeat proof. Creator consumers remain
 inactive until root's actual compiled export. East initialb40 had10/1F1E; corrected
 solebe1cpa3w8 stays frozen. Exact facades/genuine annotations, explicit Clamer
 staggering and remaining ICZ2/LBZ2/CNZ rhythm/boss/event/branch/cold scope remain.
- Boards60790/37811 were stopped130 after CPU-bound native DFS; plugin timeout did
 not reap the latter. Exact owned descendants were reconciled. Stage2 red22,26
 joined a10-vertex/1-cycle stock component to372vertices/183cycles. Only that gate
 becomes native bumper3. Outer owned-process execution deadline, not queue/JUnit
 timeout, protects82627: it now passes3/0skip, all14 admission plus ALL stock-null/
 non-null rectangular comparisons and232 unchanged native rewards. Converter is
 unchanged. Full-board/perfect clears, emerald/save/return remain unproved.
- SOZ65149's98pass includes controlled movement,600-tick light fade and ghosts;
 calling positiveX pitward was wrong and rejected (pit is left). Ghost-only41886
 passes15. Corrected negative-hop42902 has17native pass plus1 real positioned
 zero-ring arena death1196: no safe-answer/pit completion. Sole85278 records bounded
 trajectory without changing controller/recipe/physics/HP/assertions; pending.
- LRZ16 affected native cases pass0skip; retained five-path packetSHA
 `335f23e7cb85a5b7c990b63e1bed758617f18ac5230ad818768ea181647e3146` is NOT imported.
 Exact29 finite nozzle cue/echo preserves native lifecycle, but actual pixels,
 safe pocket, authentic binding, zero-ring/cold/fault proof remain. No C1 delay.
- HPZ81851's other5 new+5stock cases passed; corrected isolated clock replay85643
 adds1pass, native unchanged. Authentic1601/index31 is11F0042C8228 atROM1FCD34,
 excludes MasterEmerald37/B0 and sanctuary1701. Actual story52190 stock passes,
 FEINT fails without observed guard;70841 adds binding/routine diagnostics only.
 Creator admission/final-blow/theft/teleport/pixels/cold remain open.
- MHZ66309 passes34 (8new/26stock),0skip. PULSED gates native pressure/emissions20on/
 12off with separate captured phase and original windActive/SFX latch. Tightened P2
 force method37962 is pending; no route/season/chase or source packet acceptance.
- FBZ is the next EXISTING Sol after west stable packet, not a third implementer.
 Native EXTRA_BEAT is committed once only after accepted registry-created boss
 allocation, absent/unavailable=>STOCK; failed allocation/alternate SK AC samples
 nothing. Captured pure-native choice persists after birth and restores without
 creator execution. Actual waves/gated64-32 timing/512-256 rotation/punish/defeat/
 slots/rewind need proof; seed10 is not measured count. No safety supports removed.
- Later ICZ STOCK/SUSTAINED BC is separate from priority east facades: at most two
 native arcs/orb-prep, actual96/64 dispatches, HP6/recovery/graph/recipe replay. It is
 ICZ1's carried boss in physicalAct2; trueAct2 native deletion remains. C2/C3 and
 full campaign/ending/resource coverage remain active scope, not silently deferred.

No broad smoke, final matching SDK/artifact or complete hard-route acceptance is
claimed. Each existing job retains its original owner/observer. Preserve normal
queue/source freezes and foreign work; root will record its next live handle here.


### Root affected host/provider gate completed

Root84359 completed0 at2026-10-15T18:59:12Z after2110s admission wait and
26.271s Maven. Fresh XML contains exactly the selected waterfall
`restoredWaterfallAttackCannotReviveAQuarantinedRecipe` and campaign
`publishedCampaignBoardProviderAdmitsAllFourteenOriginalBoards`:2pass,0failures,
0errors,0skips. HEADbf8a85ae/indexd1ce08d2/working-diffa2b54b88 remained unchanged;
no owned Maven/Java execution survives. The fixture now supplies the native parent's
current V_int before child initialization; production source was unchanged. Actual
ModContext publication admits all14 original boards. This is neither a stage-clear
nor reward/cold-route certificate. Root source hold is released.

The exact two-path obsoleteStages cleanup
`2a6dd6bb5f995ab5d74cde77317bd10f6173e7149e3753983f11980cfdb1279e`
is composed after the gate: Gumball-only original registration instance and fresh
`{-32,0,32}` result remain; its fixture uses the real campaign board policy and only
unused slots7/15 for stock fallback. The root campaign publication method is untouched.
The AIZ lead owns the affected TestSecondStrikeStages focus; no duplicate is queued.

AIZ68453 is incomplete despite surviving A7 and observing native lookout birth10483
and physical fire reload11293:36000 rows stall near firex3893, no boss/results/HCZ.
A578869 has13cases/4floor-prerequisite failures/0errors/skips,9pass; terrain-grounded
fixture repair plus one progressed-born graph witness are pending. Presentation53277
failed all3 setups before pixels; replacement66303 remains worker-owned. Bonus19283
passes its single positioned invitation/Gumball/return case and observed a real1UP,
but its positive-award predicate is being strengthened in an affected-only followup.
The immutable old jar1f6 qualifies unchanged invitation/Gumball scope, not the new
14-board provider or durable saves.

Initial C1 export still waits for coherent OpusR1 repairs, especially removing the
questionable MGZ Knuckles placement facade in favor of service-only event birth.
Later C2 MHZ packet5d354c65 is checksum/signature-inspected and retained, NOT imported;
its34unique focused identities plus strengthened same P2 method do not prove a route.
SOZ birth-proximity and HPZ early-kill failures require control-only diagnostics;
FBZ43pass measures10/11waves and352/384updates, with first-abort/Knuckles parity open.
No final C1 pin/SDK/strict combined artifact, broad smoke or campaign completion claim.


Root has prepared `TestOwnedNativeBossRecipeBirth` with three character cases:
real ModContext dynamic recipe publication/quarantine plus accepted native MGZ
arena allocation must finish with exactly one stock boss after the first provider
fault. It isolates the existing camera commit, reads the preset only for fallback
assertion, and supplies no gameplay phase/timer/HP writes. Build-only92363 compiled
all3954 test sources with Java25 and completed0 in22.566s; production classes were
up to date. No tests executed. Runtime checks wait for coherent C1 neutral-abort/native
R1 fixes. No additional root Maven request is live.

AIZ lead84207 remains source-frozen with distinct TestStages then cold Sonicr3 goals;
root will inspect those outcomes separately. SSZ66561's four actual arena cases pass
(three/four pairs in one unchanged dive, destructive graph/replay); six controlled
birth cases failed the pre-existing lock-word expectation. Source confirms lock byte3
FF means word2=00FF before fighting publication7F00. Fixture-only correction is in
progress; no production fix or complete SSZ acceptance follows. The later ICZ BC enum
must remain constants-only; its unapproved public extraArcPasses method is excluded
from initial C1 and must be removed in a separate versioned followup after its freeze.


### C1 east R1 and earned Gumball source composition

Exact east incremental59195ed01a9c5609545a96e8aa123c87ffeac6ecf69d50cf257d4f6d06364915
(8paths against accepted8e5e/ff31ca) is composed. All old/new index and working blobs
match. It narrows LBZ scripted retirement, checks existing escort before the alarm
cue, preserves the allocated-instance summon latch including slot failure/recreation,
retains authentic carried ICZ rings1/7 with trueAct2 roots84/164/341 and replaces
creator static collections with invocation-local literals. The enum adds prose only,
no public members. Lead D4093783 reports55pass/0failures/errors/skips on this PURE R1
source; later carried-ICZ BC is excluded. Actual root strict/Javadoc/SDK remain pending.

The final one-file Gumball witness96c93efba4feb771fc093b287db71e9ffb2833249b5482970845ea6da3cfff8a
is composed exactly from hooked child29b4804793145afc4ed0907004fa46cc4a7c5e1c; newblob
c91a1f69416624cb49d6fee7b4d0e087a215eb3d. Positive award is now required before exit.
Lead62391 reports1pass/0failures/errors/skips with actual native1UP485/lives3->4,
LEVEL674 and stable return through824. It is opt-in with explicit strict jar1f6 and
isolated NoSave. No copied target, substitute mod, direct stage/reward write or durable
save/new-board/cold certificate follows. Glowing is a separate new-method-only task.

Main was normally fast-forwarded under its worktree lease76022570->published
1259b0016cb37f41194647eb7da9cd19921de9a5; all8unrelated paths were byte/state preserved.
Incoming33paths include Engine/input/editor runtime, not just documentation. No task
feature is yet merged into main. Root alone will intent-compose this destination into
the coherent private candidate; all worker source/jobs stay pinned760. Pre-task
change-selection base760 remains recorded; disputed negatives use the actual
destination/source identity. No extra whole baseline or cancellation was requested.

West37920 ended at testCompile with0tests; explicit-branch fixture repair51843 compiled
Java25, and sole89642 now freezes the same15affected cases on its9Java overlayd4485602.
Original35098 was a normal completed pre-R1 four-case source/binding check, not a
replaced/cancelled request. Root final C1 pin waits for the actual source-ready R1 west
packet with the A2 facade removed; pure BC followup remains separate.


### Coherent C1 R1 source and real five-zone provider

Source-ready westR1eef13c650a39cd992460576237ec539b199e78dd1d4727952f0245e6ad2e006d
(9Java paths, immutable98717->110659ba overlayd4485602) is imported exactly. All
old/new index and working blobs match. A2 facade/test is removed; both event-born
MGZ2 branches sample the enum and catch ONLY neutral optional-provider abort toSTOCK
without changing native flags/music/id/allocation. Spiker initial detection96 stays,
open/side-launcher returns64. The two real HCZ/MGZ creator classes and tests are
present. Owner89642 remains the sole frozen15case runtime gate; source readiness is
not its test result. No tested child source/index/target was modified.

Atomic provider023a7ec1218f01f5fa887fb6276065e4e14ed0e8d0d767677713bd45704ea122
is composed with those actual classes and pure eastR1: real HCZ/MGZ/CNZ/ICZ/LBZ
register calls precede the unchanged ONE campaign-boards service. Provider blob is
3e1e28ad5ce2d935a3b6baa4cd077ec521033522. No absent-class stub, BC2' inclusion,
duplicate BlueSphere or Gumball/shared entry change. Genuine compiled export, actual
ModContext whole-provider checks, strict artifact/Javadoc/SDK remain required here.


### First-fault contract correction and next composed checks

Root canceled build-only30206 at exit130 after a writer precondition failed before
any edits; the shell had incorrectly launched Maven anyway. No test/result claim,
and its partial target is not a compiled-export source. Exact private-CWD Maven/Java
inventory was empty before the corrected writer. Source imports remained intact.
The corrected writer is separate from the subsequent build command.

West89642 now completed15 affected cases without failure/error/skip on eef13c/110659.
OpusC1R2 accepted the earlier repairs, found a bounded alarm overlap-pass defect and
requested source-grounded Spiker pump documentation/cycle proof; both stay lead-owned.
Its MGZ first-fault objection is accepted: quarantine withdraws fresh services but
leaves existing callback graphs referenced. Engine.runFrameWithModAbort uniquely
aborts/discards the frame and returns through world teardown. Root's earlier
neutral-abort-to-STOCK decision was unsafe for dynamic providers and is withdrawn.
Missing/already-withdrawn recipes still select STOCK; new typed provider aborts must
propagate. Root is writing the actual ModContext/native-arena/Engine-boundary
negative before removing the catch. Later FBZ/SSZ owners received the same correction;
no blanket callback precheck or historical direct/cached semantics change.

The current real provider has all five C1 entries. Root's inventory integration
check traverses both physical tables for native zones1/2/3/5/6 and preserves every
original record field while validating actual bound factory keys, original-only
ring retention and idempotence. Unaffected-zone identity uses MHZ7 now that HCZ is
authored. A5 test-only1c211 is imported with exact old/new blobs; its14 valid unique
outcomes remain attributed across the lead's separate runs, not a fresh14-pass batch.

Native82580's3 actual ROM/SAT presentation cases passed in isolated positioned
scope. Original736/746 decoded boss records coexist with a guarded event fallback;
root's earlier exclusive event-origin inference from dynamic spawn descriptors was
too broad. This is not a post-bombing/cold/event-scene certificate. Stable test-only
ae73ee packet is pending exact import; no production presentation change is implied.

Later-only FBZ99ee348f and SOZc52dd1cf annotated native packets are retained outside
initialC1, alongside MHZ5d354c65/LRZ335f23e7. Their native focused evidence is not an
actual host/SDK/cold/whole-C2 qualification. DEZ83191 eight lifecycle cases pass;
new stale-touch list27855 has two positive controls passing and two retirement
failures, justifying only the mace's current-destroyed flag exclusion in its owner.


### Reproduced first-fault recovery defect and exact R2 composition

Root7491 completed exit1 after519s queue and78s Maven:6 cases,3 expected first-fault
failures and3 already-withdrawn STOCK controls passing,0errors/skips. Fresh XML full
identities and assertions were inspected. All three native character branches wrongly
returned completed=true from Engine.runFrameWithModAbort after the dynamic creator
fault; cached AIZ callbacks could continue. Working/index inputs stayed unchanged
(SHA256d62d249a8b9ebbaafb25b3ea25e2ce63ff8b681ac50796ac6827390ccc4adcc5),
and exact private-CWD Java/Maven inventory was empty before source release. Actual
4022main/3958test Java25 compile passed; this is a deliberate red regression, no SDK
or artifact verification claim.

Exact west R2 native260c373b (110659->16446,4paths) removes the catch and changes
controlled first-abort expectations; root verified all old/new index and working
blobs. Worker74882 native6+Spiker three-cycle1 pass, while its separate positioned
Sonic arena passes and Knuckles approach fails before boss admission; the failed M4
witness is NOT imported or labeled green. This is no Knuckles one-encounter/cold proof.

Exact test-only presentationae73ee(17dd235), capacity470654(95372->6ff950) and
Glowing9564b7(29b480->1ea145) are imported with all blob checks. Capacity63337 has
2valid focused cases with real zero/one free SST slot, no retry after space returns,
whole graph restore/retirement and unchanged rewards. This adds2unique outcomes to
A5, not a single16-pass run. Glowing50320 has1valid positioned positive award and
stable LEVEL return: native pending5 after an orb,7 rings retained after return,
no duplicate. Earlier failed no-prize R1/R2 remain failures. Both bonus gameplay
methods use immutable pre-C1jar1f6 and NoSave; no new14-board/giant/cold/durable-save
claim. The native return reload restores activationmark but not usedForSpecialStage;
keep single476 and actual Knuckles branch eligibility open. A source-only dual-post
act-wide-once argument was rejected; no checkpoint schema or high-water relaxation.

All four measured cold axes remain incomplete (47826/20502); controller authoring
now uses bounded ordinary-pad research from a naturally reached prefix with complete
world/clock restore and a separate fresh no-search replay gate. No product physics,
content or native state is changed to make that authoring succeed.


### Genuine C1 compiled surface

Build-only63627 completed0 in70seconds, Java25 production/test compilation after
exact R2 and test imports; no tests or artifacts were run. Maintained
ModApiSignatureSurface --snapshot over these own target/classes generated the
actual 21031-line candidate, SHA256211ff880561f388c214a4cad060457dd62ea48d8f4726897b044a34e2c0635b5.
Compared with pinned pre-task760: 131 additions,0removals; compared
with the prior AIZ pin: 89 additions,0 removals.
All six C1 factories are actually compiled/admitted, with no Knuckles A2 boss facade.
The mutable0.7 pin now contains exact generated output; descriptor/currentVersion
remain unpublished0.7.0 and no published baseline changes. This is a compiled
surface observation, not a completed SDK/Javadoc/strict artifact or runtime gate.


### Composed check8934 and narrow repair

8934 ended1:65tests/1failure/2errors/0skips,35.614s Maven; queue time unavailable
from the retained bounded output. Fresh8XML suites inspected: actual host3PASS,
API surface10PASS, Javadoc7PASS, SDK-unit10PASS, CLI7PASS, Campaign10PASS+1new
fixture failure, PinPolicy3PASS+1error, ReleasePolicy12PASS+1error. Source digest
remaineda04763b95868c3256844d4798f712c2fb47a282725a524029509c064e40aade3;
no own private-CWD execution survived. Packaging/SDK artifact phases did NOT run.

The two policy errors are root-introduced descriptor comments: the parser requires
exact key=value lines, unlike generic Java Properties. Remove the comments, preserving
the exact existing unpublished0.7.0 descriptor; do not weaken the parser. The new
provider test incorrectly required actors in every table. Actual IceCapCampaign
explicitly authors only original-ring retention in physicalICZ2 (same open enemy/boss
scope forLBZ2), so accept either real bindings OR a strict original-ring subset while
preserving all record/key/idempotence checks. This corrects a test overstatement, not
an ICZ2 encounter implementation or waived route gate.

Exact isolated alarmP68f9f645a (4paths vs pure9982, noBC2/BD) is composed after
8934. Fresh touch-order5 valid outcomes and corrected native expectation16 remain
attributed to the owner's G/H union runs; root will exercise the new real touch-order
class on this actual pureC1 composition with the3repaired checks. No unchanged62-pass
repeat. Artifacts still require fresh strict bundled packaging and verification.

README and creator index now distinguish AIZ/C1 source ingredients, ring-only rows,
native invites and14board authoring from incomplete routes. Partial AIZ obligations
are linked from the coverage backlog. The matrix's stronger source-only non-farming
claim is removed in favor of actual played visits plus unresolved native revisit/death
lifetime; no production/checkpoint/selector changes. These prose corrections are not
new runtime certification.


### Native checkpoint and matrix source reconciliation

Exact test-only4a74412a is imported as new TestSecondStrikeCheckpointGameplay,
blob27abe5e9/commit9ff7be78. Owner56582 has1valid positioned native post697
activation/spike death/new-manager stopped zero-ring respawn/ordinary spindash escape
case, life3->2/mark6. This is one of six posts, not all checkpoint/death/save breadth;
no unchanged test rerun. Actual immutable1f6 producer scope remains explicit.

Latest AIZ matrix docs-only22841e86 is reconciled by intent (exact committed text),
retaining original A1-A12 inventory and adding observed rewards/checkpoint/presentation
and four incomplete cold outcomes. Its native return lifetime correction agrees with
root's README/backlog qualification; no gameplay/schema or selector changes.


### Narrow composed verification75627 and actual artifact audit

75627 completed0: eight tests, zero failures/errors/skips across four fresh XML
suites. Selection was only the repaired real five-zone provider method, exact pin
policy method, exact repository descriptor method, and TestSecondStrikeEastAlarmTouchOrder
(five native touch-order cases). Queue331seconds; Maven80seconds. All source/index
inputs remained unchanged (SHA256f445182b0fef60ffb81d13fd5a67945a5f1e40dc8c5f79266aecc69f0c0162a7).
Fresh XML identities/assertions were read; no task-owned private-CWD Maven/Java survived.
The unchanged62 successful8934 outcomes remain individually attributed, not a repeated
whole65-case pass.

The same normal verify completed fresh strict bundled preparation, SDK/Javadoc/testkit
packaging and the actual artifact verifier. Actual fatjar SHA256bc897aac76648bcb6110406003d5ad25ce781ba5070718de45e0ce7605eee7dd;
SDK SHA2566adc9ac62a9d8a4fba06b43426e5daeffd00f522ab16bf42f61430044d6de0c6.
Actual bundled second-strike-0.1.0.jar has33 entries,62356bytes and SHA256
9ddd7740c0f9996648302867ad723d191e41179f555a4e2ec6596bc80df6974b,
including the five real campaign classes and board provider, with no ROM payload.
A separate bounded intended-tree audit exported the actual fatjar surface:21031
lines, byte-equal to the current candidate pin/SHA211ff880. Running GgfModCli
from the actual SDK+fatjar pair validated that actual bundle with warnings=error,
exit0 and zero findings. No smoke/full-suite, cold-route, whole-C1 or delivery claim.

### Newly attributed native MGZ Knuckles ownership blocker

West5458 completed two positioned cases: Sonic native side/back punish succeeded
(HP8->7,rolling/not hurt,4800-frame survival), while Knuckles stopped before arena
commit because its player was above the synthetic camera viewport. The original
checkpoint333 floor was independently qualified, so this is no longer an arbitrary
positioned-seed assumption. Root's bounded read-only native source check identifies
an inherited owner mismatch, not merely an A0->28 camera correction.

ROM A2 at original MGZ2 layout offset7FE=(3E78,0000,A2,00) owns admission through
Check_CameraInRange: inclusive Y0000..0128/X3C78..3E78; destination Y0028/X3D78.
Its sub_85D6A/loc_85CA4 stages own saved bounds, music/fade,120-tick wait and camera
commit before children. MGZ2_Resize instead owns the lower Y0600..0700 A1 event at
3D20,0668 with no Knuckles branch. Current Java's upper event A2 branch competes
with an ungated original placed A2 owner. An offset-only patch would preserve both
incorrect encounter ownership and spawning.

The existing west Sol is sequenced to restore the lower A1 resize path and implement
the placed A2's native admission/staging, attaching the service recipe at that owner.
No character gate absent from ROM, new public surface or shared camera framework.
Root will migrate the Knuckles ModContext/frame-recovery regression off its synthetic
event path. Earlier host outcomes remain valid for the exercised path and fault
boundary, but do not qualify the native Knuckles encounter. Native admission, camera,
exact-one-root, lifecycle/rewind and affected host checks remain pending. Current
compiled/API/artifact evidence above remains attributed to its pre-correction source.
