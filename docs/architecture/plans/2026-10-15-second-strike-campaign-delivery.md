# Second Strike — two-team campaign delivery

Status: implementation authorized on 2026-10-15; native foundations are verified
for integration, while the full campaign remains in progress. Current team state
and bounded evidence are in the compact checkpoint below. The binding content scope is the
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

The foundation and bounded AIZ historical-latch repair are integrated and pushed
as `f813672d0599f9debc0cf9a32148999fb3cc61a6`.
Focus42171 passes8 cases without skips; Astra R1 has no blocker; actual89496
reached-prefix restore and64-input replay pass on the new42171 artifact pair.
Fresh input-only29449 clears the former16262 death, then dies16518 before
waterfall/HCZ. Successor75430 changes the earlier terrain-edge response and dies
16637 without requesting a roll. Isolated29024 preserves29449's exact prefatal
pads, admits native Down-only roll and survives four inputs; its conservative
ahead-floor guard stops before accepting the clearance endpoint.
Post-clear81356 admits an ordinary rolling jump, then stops at negative native
velocity on a right-wall landing; alive/nohurt is not a qualified recovery.
ICZ28579 qualifies the declared zero-ring neutral WARNED fall/landing and84-update
registered replay. Successor97772 lands on the stone, retreats unharmed to real
terrain and matches73 registered return/replay updates. Its STOCK arm follows
the same input policy, with identical pads through147, then dies149. Neither
qualifies under-body/lower-route shelter, harder gameplay or a cold route. No full
AIZ-to-HCZ axis or M4–M6 acceptance is closed. The following paragraphs retain
foundation history.

At the earlier foundation checkpoint, root private branch
`feature/ai-second-strike-campaign-delivery` held HEAD
`b3d307daffa7de2a63f373fb2913e4a30a03e30b`, including the committed bounded repairs.
Main has fast-forwarded from1259 to `b07c52dfd601ff78cd00d89a24e7fe57320acd65`
(editor-chrome retirement); the three dirty disassemblies and five untracked user
files remain preserved. That upstream change merged cleanly into this private
source, preserving both changelog and workflow-catalogue entries. The immutable
pre-task comparison base remains
`76022570e8233823e1e36419666a0f29c768f4b6`.

Combined smoke24453 on247fd is terminal and its diagnostics were inspected and
acknowledged. All owned ordinary failures are repaired; the unchanged audio CLI
assertion reproduces on base1259 with the inherited launcher environment. Focus19399
passes83 cases with one expected historical-artifact opt-in skip, and guard32731
passes62 with no skips. Their exact identities/source/timings are recorded in
[bounded repairs](#bounded-repairs-after-the-combined-run). Build-only30455
`-DskipTests verify` succeeds in42.368seconds at03:26:50BST, including actual
engine/SDK/Javadoc/test-kit packaging and the artifact verifier. This build's
configured publisher is the default Time Attack bundle. The later19448 verification,
maintained Second Strike builder and actual paired signature/consumer checks below
complete packaging on the upstream-reconciled source.

C3 native ingredients, STOCK golem, placed A2/capsule, corrected reservation seam,
ICZ fail-closed consumer, checkpoint high-water restoration, pending-art admission,
Slots ROM authority and inactive MHZ/LRZ/HPZ creator files are composed. Public
version remains unpublished0.7.0; the21107-line candidate pin is unchanged. Existing
53676 artifacts remain immutable and keep their historical witness attribution.
No current-artifact route or harder-gameplay claim is inferred from compiler support.
Campaign workers are stopped and their unmerged trees remain cleanup_pending.

The verified foundation is integrated and pushed on develop606330ef7; remote hash
was checked and main's eight unrelated changes remain intact. Its runtime/build
source is byte-identical to the tested b3d307daf tree. Original M0–M6 delivery is still
incomplete: all four full AIZ-to-HCZ cold routes and substantial later-act/character,
bonus/ending, counterplay and hardness gates remain open. The separate development
reel is delivered; it does not close those gates. Historical batch handoffs and
exact rejected/limited outcomes remain below.

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


### Private checkpoint and updated destination composition

Normal-hooked private checkpoint2c940397393371857b53d93145e8ee1a3100285a preserves
all118 accounted staged paths, seven policy trailers and the four contributing model
coauthors (parsed before commit). The imported AIZ plan's machine-local ROM path was
normalized while retaining its original filename/hash. No main integration or push.

Privately merge actual develop1259b0016cb37f41194647eb7da9cd19921de9a5 after that
clean checkpoint. The merge is conflict-free. Before this evidence paragraph, all118
paths changed only by the task exactly match the task parent; all32 upstream-only
paths exactly match1259. The only shared path, CHANGELOG.0.7.prerelease.md, retains
both Second Strike and the incoming editor HUD entry. No main tracked/index/foreign
working paths were edited by this private merge.

Incoming editor HUD/window chrome includes Engine/input executable changes, six new
test classes and associated docs/resources, not just prose. Original task validation
remains attributed to its760-based tested inputs. New destination-focused editor/input
checks will combine with the pending native MGZ correction and host-test migration;
no duplicate unchanged smoke/baseline run is requested. Genuine API surface/artifact
reverification after the coherent source is required. The task's pinned pre-task
integration base remains760 for eventual change-based smoke selection.


### Updated destination focus3880

On clean private merge5301c95ed4c2464be1d6f94e87cb9a385a8d974c, one normal lean
queue request selected exactly TestInputHandlerGamepadSuppression,
TestEditorInputHandlerFrontEnd, TestEditorWindowChromes, TestEditorHudControls and
TestEditorHudLayout, with -Dmse=off and explicit surfaceless-egl. This is the new
upstream Engine/input/editor integration, not an unchanged task-suite repeat.
3880 completed0:25tests/zero failures/errors/skips; five fresh XML suites read,
counts3+2+4+9+7. Queue13seconds; Maven74seconds, ending2026-10-15T20:44:39Z.
Actual4034main/3967test Java25 compilation completed. HEAD/source status stayed
exactly clean5301 throughout; exact own-CWD Java/queue-wrapper inventory is empty.
The opt-in HUD picture refresh was not selected or claimed.

A bounded intended-tree actual target/classes API export after that compile still
matches the current21031-line pin byte-for-byte/SHA211ff880. The incoming private
merge changes no candidate API. Existing jar/SDK verification75627 remains attributed
to its earlier source; these compile-only outputs are not freshly packaged artifacts.
MGZ placed-owner correction, root Knuckles host migration, remaining routes and
combined smoke/guards/delivery remain pending.


### Next native export batch: MHZ, LRZ and FBZ

After the actual initialC1 export/SDK gate, compose only the next source-ready native
packets: MHZ5d354c652256 (five paths), LRZ335f23e7cb85 (five paths), and FBZ99ee348fa297
(seven paths) plus first-abort propagationfd1f74ef6bbc (three paths) and comment/docs-only
ca56e744ab68 (two paths). Every full old/new index and working blob matches the exact
immutable packet. Child tested trees/indices/target remain untouched. Actual five public
roots (two factories/three finite enums) are present before inventory admission; no
placeholder or uncompiled signature is added to the pin. Descriptor/version remain
unchanged unpublished0.7.0. Genuine fresh compile/export/pin remains the next gate.

MHZ/LRZ opted-in teardown defenses are retained after a bounded source check: removed
parents are not normally scheduled, but the managed MHZ arrow benefits from parent
retirement. Detached leaves and committed independent LRZ flames keep native lifetimes.
The explicit retained-parent STOCK negative is scoped diagnostic evidence, not a new
PULSED regression or authority for an unrelated stock fix. LRZ additional lifecycle
diagnostic remains lead-owned. FBZ includes the separately attributed inherited Knuckles
rotation weapon correction and actual first-abort propagation; its final wording
identifies the25 helper as single-volley, not the observed overlapping30 peak.

SOZc52 golem gameplay is rejected after the native review (passive auto-sink/misleading
facing); do not import it or admit an absent root. Corrected passive/engagement/ghost
work5851 is still lead-owned and source-frozen. C3/BC2/BD are excluded. No later-zone
creator activation, routes, actual host lifetime, SDK artifact or whole-C2 pass is
claimed by this source composition. A preliminary A2 handoff exposure concern was
withdrawn after tracing the inherited initial-wait render/touch gate: it already
suppresses normal presentation/collision until the first fight initializer. No extra
phase/behavior repair is authorized from that incomplete analysis. The source audit of
7a488e finds no new blocker; actual85518 runtime/graph evidence remains pending. Root's
prior25 input/editor outcomes retain their exact5301 attribution.


### Native A2 source and earned giant-stage witness

Exact source-audited A2v17a488e8d (eight Java paths,7180bd71->7364497a) is imported
with all old/new index/working blobs verified. Lower A1 event behavior is retained;
placed A2 owns inclusive native admission, fresh recipe before staging effects,
raw120 camera/music gate and captured phases. Restore creates no construction
children; captured children reattach independently. Source audit found no blocking
issue after withdrawing its preliminary inherited-wait visibility concern. Runtime
85518 remains pending in the worker's unchanged frozen tree. Root must migrate the
actual host Knuckles regression and inspect focused outcomes before acceptance.

Exact new-file Giant packet6989858b (commita9e9a13e,parent1ea14545) is imported.
Owner72897 fresh single-case pass0skip on immutable AACjar: positioned NoSave Sonic320
ordinary entry124;102blue collected by3604; native Chaos emerald0 earned3939/count1;
results3999/score2300; LEVEL4650 at Saved2(7112,1216), giant25 retired, retained
emerald/stage/score/lives through4800. Prior failed setup/controller attempts remain
historical. This is played board0/reward/return, not14-board, cold, durable-save or
rewind certification. The future durable menu/save/reload method is a new separate
lead-owned gate; no unchanged giant rerun is requested here.


### Actual host regression migrated to the placed Knuckles owner

Root TestOwnedNativeBossRecipeBirth now separates real lower A1 Sonic/Tails event
cases from two new placed A2 Knuckles cases. Exact original341 at3E78,0000 is
materialized by the normal ObjectManager/registry, then ordinary manager updates
inside the actual Engine frame boundary drive admission/staging. No reflected native
routine, forced preset, timer, HP or children. First provider fault preserves the
already-existing dormant root but aborts before flags/opening children/retained creator
callbacks. Already-withdrawn control must retain the same STOCK root, signed120 staging,
hidden handoff, then native initialization/children without duplicate roots or retry.
The real ModContext/decorated service graph and Engine recovery observations remain;
actual full Engine/title teardown is still a separate scope. Six new/current outcomes
are pending; historical synthetic Knuckles event outcomes are not native qualification.


### Coherent C2 source and A2 reconstruction follow-up

Import exact native A2v28d101014 (increment7364497a->9fd28d25), preserving v1 and
its85518 record:27cases22pass/2fixture failures/3committed reconstruction errors,
zero skips. Missing dynamicId1 exposed a render-child constructor unsupported by
existing reconstruction after removing premature construction children. V2 adds only
a package-private native child probe constructor through the existing strategy,
repairs stale-session/direct-coordinate fixtures and asserts the inherited hidden
handoff. No shared probe framework or extra camera/phase change. Worker33714 remains
pending/frozen; root host migration is distinct and pending fresh compiled execution.

Import corrected SOZ58488184 (13paths/tree d964ddde), not the rejected absolute-left
c52 source. Relative actual-player commitment occurs at observe18; facing follows the
actual vector through observe/anticipation/launch. Native STOCK/hit/physics unchanged.
Ghost provenance changes only at actual native charge transitions. Owner5851 retained
21good identities plus one controller death;24833 reached real sink alive after25native
hit/collapse interactions but missed a routine22 replay prerequisite;14447 passed the
actual observe18 destructive replay/positive. This is no launched-three-hop/readable
retreat/harder/cold-route certificate and no creator activation. Admit only the actual
present SandopolisChallengeRecipe root; there are now six later C2 public roots.

Exact MHZ01ac3e13 and LRZ78f1f20c test/docs-only increments follow their original
packets. Final approved opted-in retirement and explicit STOCK/default controls pass
four/two affected identities respectively; production unchanged. Original explicit
retained-parent STOCK negatives remain scoped diagnostics, not silently repaired stock
behavior. Root will compile this actual union, generate its API pin and verify actual
SDK/source consumers. No unchanged worker native suite or whole smoke repeat here.


### C2/A2 union build and actual compiled candidate

Build-only63269 completed0 in70seconds, Java25:4040main/3975test sources compiled
on the actual1259-composed A2v2/C2/Giant union. No tests or package phase executed;
compiler deprecation/removal warnings are not strict-mod validation findings. Target
classes were exported by the maintained ModApiSignatureSurface tool under the normal
intended-tree lease. The0.7 pin is exact generated stdout, with no manual signatures.
Actual candidate: 21067 lines, SHA256a5a7871c72d77f6b1bce75ddf35086eae2274716f9bec109ac59d73f9b770635; 36 additions,0 removals
compared with the prior21031-line C1 pin. All six C2 roots are actually present; no
rejected SOZc52, C3/BC/BD or unannotated native bridge is an admitted creator surface.
Descriptor and current version remain exact unpublished0.7.0. Actual SDK/Javadoc/
strict package and migrated host checks remain pending fresh execution.


### Owned placed-child slot seam

Import exact ea6d3937 (helper3260d2dd,base0b32030b,three paths) after63269 is
terminal. Only ObjectManager package-private methods, unannotated engine-only
NativeChildSlotReservations bridge and TestPlacedChildSlotReservations are added.
It requires an actual live placed parent, existing fixed-prefix ledger, unique exact
spawn identity and managed slot. Moving dynamic parents reject because their spawn
identity would need a different snapshot authority. Successful raw FindNext slots
append/reuse only ledger tail cells; release matches the current genuine parent and
tail membership. Native callers clear their saved slot on first release; this is not
a hostile same-owner generation-token API. Prefix/teardown/restore authority remains
inside the existing reservedChildSlots/ChildSpawnEntry/owned used-bit shape.

Helper34571 passed13 focused identities (new5+existing child-slot8), zero skips,
with exact snapshot/next allocation, prefix, capacity, old/foreign/aliased owner,
release/reuse and teardown isolation checks. Test-first62492 was compile-red against
an absent seam plus a corrected accessor typo, not a behavioral-red claim. No global
raw helper, supported Mod API, allocator snapshot schema or native owner list changes.
Source is actual compiled/tested in the helper's760-based tree; root's new integrated
compile/SDK gate remains pending. Existing Sonnet may now consume this exact tested
bridge and own both causal ICZ lists, with no further ObjectManager edits or synthetic
keys. The unsafe list-only attempts and both pose/slot failure observations remain
historical; FLANKED stays inactive until real replay and gameplay gates close.

Worker33714 additionally closed the A2v2 native22-case runtime gate, zero skips:
actual ROM/registry admission15, committed graph3 and corrected lower intake4.
360-update replay retains child IDs/roles/drill state and does not resample the
captured preset. This is native-owner evidence, not the still-pending real host
Knuckles regression or positioned/cold readable route/punish evidence.


### Actual composed host/API/artifact gate50133

50133 completed0 at2026-10-15T21:08:51Z:37tests/zero failures/errors/skips across
five fresh XML suites (host6,surface10,pin4,Javadoc7,SDK10). Queue47seconds; Maven93.
Frozen index22a1fec75c47d6e161576e6623fa8f28bf37f988 unchanged, no unstaged source
mutation. Actual4041main/3976testJava25 compile completed. Knuckles first-fault and
withdrawn controls use the REAL original placed A2; Sonic/Tails use the lower A1 event.
First fault reaches actual Engine frame-abort recovery callbacks before retained
creator callbacks; this is not a full window/Engine teardown certificate.

The same verify completed actual bundled strict package, SDK/Javadoc/testkit and
artifact verification. Actual fatjar SHA256ffad06c708ffa1c37667df85e79b1adb687941b34bf9dfc041492fbb9328b6c4;
SDK SHA2566190e7220162e5006ed8ad13af466ee04916a8a83a29c34b72d9b85e2ea98c94.
Actual fatjar export is byte-equal to21067/a5a7871c candidate. Standalone actual SDK+
fatjar CLI validates bundle9ddd7740 (62356bytes) with warnings=error,zero findings.
An additional temporary creator source consuming ALL six C2 roots compiled against
that actual pair and packaged through the same strict CLI with zero findings. Its
source/classes/jar were removed after inspection; no runtime registration, route,
SOZ hardness or whole-C2 acceptance is inferred from compiler/package support.

### Post-gate evidence imports and open gameplay decisions

After50133 and the artifact audit are terminal, import exact durable-Giant a0945a66
(commitfc9e2231,parenta9e9; one method addition, existing NoSave driver unchanged).
Owner21450 has one valid case using two fresh isolated JVMs/actual cwd+saveRoot:
native menu NEW_SLOT_START, declared positioned setup after intro release, ordinary
played giant/earned Chaos0/results/LEVEL+150, native persisted slot state, then fresh
menu LOAD_SLOT/emerald1/team/lives/zoneact+120 ordinary frames. No direct save/progress
writes. This is durable positioned Sonic320/board0, not cold/Knuckles/14-board or
rewind coverage; no unchanged owner rerun is requested here.

SOZ C2R2 still rejects current golem difficulty evidence: passive outcomes can be
vacuous on death, observe-count is not launch-count, and the relative short cycle is
not shown harder. Root chooses STOCK-only golem pending a qualified successor, while
keeping accepted TOMB light/ghost behavior and the same enum surface.34903 remains
lead-owned matched research/launch-index work; no retuning/activation by root. Current
50133 artifacts are explicitly attributed to their unactivated experimental source;
a source-only STOCK-golem delta and final package are required before integration.
The inherited MHZ visual-child and ICZ folded-state gaps are recorded as bugs, not
intentional discrepancies, in docs/status/s3k-known-bugs.md.

AIZ46863's post476 deathreload FIFO overflow is a real bounded runtime error on its
older native5189 composition, not yet attributed to the final root tree or stock.
The art provider flushes pending enemy modules before the star request without an
aggregate capacity check; the nearby ring-restoration producer already has a pending
capacity path. Lead owns exact occupancy/cause and bounded matched controls after its
live71814 source freeze. No queue expansion, asset dropping, forced service timing or
checkpoint/ring/used-gate change is accepted. Native26 and697 recoverability passes
remain separately scoped.

The example manifest description now reflects experimental Angel Island/early-campaign
content without the obsolete Act1-only assertion. This metadata-only delta is later
than the above artifact gate; final bundled packaging must include it. Required
Opus C1R3 attempt failed with only generic provider error/no findings; a new full-context
retry is active. It is pending required review, not an accepted review or rate-limit
attribution. No task main integration, publication or cleanup claim.


### C1 R3 source review and Stock golem disposition

Opus native-prerequisite R3retry1 completed read-only against exact9fd28 A2,3260
slot seam and22a1 real-host source. The first provider-failed attempt produced no
findings; this fresh review now supplies findings, not an additional runtime gate.
One medium native A2 defect remains: capsule attempt must clear Boss_flag before
allocation, as loc6C8B4 does even with exhausted slots. Existing west owns only this
flag/pattern-resume correction and two focused normal/full-capacity cases after its
route30327 terminal. Root independently verified the same missing clear.
A second reservation timing issue remains: a future folded child's released SST
must remain occupied until the ascending execution walk reaches its own position.
Existing root helper owns a real in-pass allocation negative and the existing
releaseChildSlotAtItsOwnExecPosition mechanism; campaign must not edit the manager.
The current3260 source/13 passes do not close this intermediate timing risk.
No unchanged R4/API/SDK rerun is requested; route/pixels/punish remain separate.

Exact SOZ0d57 and docs304985 packets are now applied. SozMinibossInstance and
TestSozMiniboss independently byte-match76022570; experimental arena source is
removed from candidate and retained only in prior research history. Owner78847
fresh14tests/zero failures/errors/skips validates these Stock native/test blobs
(284seconds queue,76seconds Maven,22:20:41BST). TOMB lighting/ghost code and public
21067 surface are unchanged.50133 artifacts predate this source-only fallback;
they are shared as immutable matched compiler inputs, not current golem runtime
acceptance. Their actual paired six-root creator compile/package and bundle
strict validation were terminal0/zero findings in92531. The final delivery package
must include the Stock fallback plus subsequent native fixes and manifest prose.


### R3 repair source imports and focused attribution

Shared seam increment85caddd1 is imported exactly, helper commitd9e9d122 versus3260.
ObjectManager18f20f04 now uses existing deferred child retirement; the future child's
slot remains occupied until its SST position, while already-visited/outside-pass
release is immediate. Negative55227 ran7cases/one assertion failure (expected next
slot13, actual12); corrected23018 fresh7new+8existing=15pass/zero failures/errors/
skips, including the actual intermediate allocator, later reuse and completed-pass
destructive reservation restore. No API/schema/bridge change. ICZ native lists
still require their separate actual tuple/geometry/reservation/replay qualification.

Capsule flag source c252900d is imported exactly (owner8a37b074,new testae627e17).
The native clear precedes attempted allocation, including exhausted slots; stored
bounds receive only a faithful-ROM comment.23460's two cases pass flag/current-id/
allocation-count/script-guard assertions, but both fail the final tile-byte change
prerequisite because the fixture omitted legal VBlank payload publication. This is
not yet a green two-case batch: worker owns a test-only publication correction and
necessary same-case followup, with no native/PLC/phase/camera change.

AIZ matrix/README now distinguish71814's actual fresh no-search first-boss defeat/
results/apparentAct2 milestone from the still-incomplete HCZ route; durable positioned
board0 uses owner21450's two native-menu processes, not cold/whole-campaign proof.
The476 observer94744 records three pending enemy modules plus BLUE marker filling
the physical four-entry FIFO; activation requests the same BLUE again before
service. This is exact older5189/1f6 composition evidence, not root runtime
attribution. Lead owns a matched stock-post control and a pending/reuse proposal;
no capacity/clock/asset/checkpoint workaround is accepted.


### Final capsule fixture and Stock ICZ consumer composition

Final capsule packet9ccacab9 changes only the already imported native8a37b074 and
its test. Native owner is independently byte-equal to finalf0861a; testd19db695
replaces the earlier missing-publication fixture. Owner18280 fresh2pass/zero
failures/errors/skips at22:25:29BST (zero queue,28.484seconds Maven) proves actual
normal/full-slot native flag clear/current-id/capsule/no-retry and real MGZ tile222/
252 paused24/resumed24 via the existing legal ordinary-level VBlank.23460's two
pixel-prerequisite failures remain historical; no native/PLC change followed them.

Exact P12 consumer68e07ff5 applies cleanly to root's STOCK-only ICZ owner. To avoid
importing unqualified API/presets, independently derive the expected native owner
from worker5969380b by reversing ONLY P5's IczEndBossInstance delta in a temporary
index. The root result is byte-equal to that derived STOCK consumer; no FLANKED
enum/factory/callback is imported. Default rewind policy changes exactly effect
and structural collections to CAPTURED; restore-only child holders keep full
script/anchor/timer/pose/nativeSlot/ledger state. The named prefix6 is used on
both reserve/release. Owner272466 fresh624tests/zero failures/errors/skips used
our exact corrected manager18f20f04 and asserted actual quiet/committed tuples,
geometry, ledger, allocator, player timeline and destructive +1/2/5/65/130 replay.
That outcome is attributed to its union, not a root execution or hardness claim.
Root found reserveEffectSlot's rejected-placed-parent exception still falls back
to raw allocation. Same Sonnet owns a minimal fail-closed correction plus narrow
regression; non-placed/dynamic compatibility keeps the legacy path. No further
manager/schema/API change or624-case repeat is requested.

The next root build-only test-compile checks this union on HEAD5301/updated1259;
no runtime acceptance is inferred from compilation. The immutable matched-control
creator provenance is corrected:424ff845 authored1f6;5189 identifies its older
private engine dependency, not creator source. AIZ verified all22 payloads of a
normal exact-source build against1f6 before removing only the two invitation
bindings. No control runtime or PLC production fix has been claimed.


### Root Stock native union compilation64846

64846 build-only test-compile completed0 in72seconds, Java25/4041main/3977test
source files on frozen741ded60/HEAD5301. Compiler deprecation/removal and unchecked
warnings remain; this is not a zero-warning or runtime claim. Changed Markdown
relative-file links (two new links) resolve and staged diff passes whitespace checks.
The next exact focused root selection is TestMgzEndBossKnuxCapsuleFlag,
TestPlacedChildSlotReservations,TestObjectManagerChildSlotAllocation,TestSozMiniboss
with --lean/-Dmse=off/required surfaceless-EGL/original absolute S3K ROM. Expected
coverage is native R3 flag/pixel2, reservation7+8 and Stock golem14; actual XML/skips
will determine the outcome. ICZ consumer fail-closed and new harness qualification
remain separate; no unchanged SDK/Javadoc/37-case rerun or broad smoke is launched.


### Composed root31 and next C3 native export batch

Root58138 completed0 at22:54:06BST on frozenf41af61e,1095seconds admission wait
and30.256seconds Maven. Fresh full XML identities inspected: capsule2,SozMiniboss14,
child-slot-allocation8 andplaced-reservation7,all31pass/zero failures/errors/skips.
Source/index remain exact and no working mutation occurred during the invocation.
This closes the two R3 source/runtime fixes on the actual updated-base union, not
C1 route/punish, all-checkpoint or whole-campaign acceptance.

Exact HPZ6fdb+35d8,SSZ/DEZcebb,DDZb9e7 andDEZ536ec source packets are now applied.
Before root inventory/prose edits the resulting tree is independently byte-equal
to prospective08f854af,22owned paths. HPZ dust retirement/legacy Stock controller
fixes andDEZ post-bonk/DDZ teardown wording are included. Three native factories
and four finite enums enter the real inventory; no absent class or handwritten
signature is admitted. Actual compile/export/pin and matchedSDK/consumer gates
are next. Normal candidate0.7.0/descriptor remain the same unpublished contract;
published baselines are immutable. No later creator provider or native preset
is activated by this source batch. Existing owner/native evidence remains scoped
to their exact source, including explicit inherited Stock lifetime corrections,
unsupported native three-slot DEZ budget, dynamic-only DDZ teardown and open
fuel/readability/character/cold routes. No unchanged additional Opus review.

AIZ66792 exact matched Stock-post control1b8cb080 passes ordinary death/reload/
escape with0rings and no bonus stars. This isolates duplicate invitation art
admission, not native high-water preservation.16564's14of15pass closes the former
FIFO crash but retains the original zero-star assertion failure (actual4) after
reload; no lifetime assertion was weakened. Native ordinary Load_Starpost_Settings
restores the saved live index; current engine drops its separate activation mark.
Root helper confirms owning files byte-equal to760, and50411's ordinary component
case fails restoredMark5vs-1,re-save1vs2 andSFXemptyvsnonempty; its actual bonus
coordinator0index/5mark control passes.72178 is the sole authorized one-call
ordinary-owner correction focus; post/provider code stays independent.

Successful AIZ11708 now provides a real native320x224/60 FFV1+FLAC movie while
remaining incomplete after Act2 arrival/death. Root is preparing a short development
reel from that exact movie and CSV, with long ordered shots and one genuine
synchronized audio source. Required editable-source archive uses OpenGGF-Media's
existing second-strike project; no full-route, mux-ENOSPC or final reel claim yet.


### Ordinary checkpoint owner and unwired M5 source imports

Exact fb74ed8b helper increment is imported (commitada25617 versusd9e9). Only
LevelCheckpointCoordinator restores existing activation-mark state immediately
after ordinary saved checkpoint restoration. Generic restoreFromSaved, synthetic
intro/cutscene callers, stock posts, bonus/debug producers, schema and public API
are unchanged. Red50411:ordinary case fails pre-postMark5vs-1,save1vs2 andempty SFX;
its actual bonus-coordinator0index/5mark/usedfalse control passes. Positive72178
fresh same two cases pass/zero failures/errors/skips. This is a component/load
boundary, not physical death or complete route qualification. Current source
is exact helper coordinator1e8f238e/new test20ca3ee0. Native476's untouched zero-star
assertion remains useful for later actual composed death validation.

M5 creator-only f0d1eb9b and687c8e27 add authentic MHZ2 record34 PULSED and LRZ2
record0 ECHO_JET candidates plus tests/local evidence. Both are UNWIRED: the actual
five-zone provider remains byte-equal3e1e28ad. MHZ78037 corrected one equality
fixture method passes;94540's other two passing identities are reused explicitly,
not a new three-pass batch. LRZ54546 fresh three cases pass/zero skips. Actual
paired engine+SDK creator compile/strict package pass; none proves encounter
readability, counterplay, both acts or harder gameplay.37721's MHZ capture is a
wrong-band/backside observation (no Cluckoid visible), not a frontal crouch-safe
answer. MHZ1/LRZ1 remain unmodified because these actor IDs are absent natively.

AIZ11708 has a valid native movie and fresh no-search playable Act2 milestone:
all common fields match71814 through15034, tail starts15035, actual60released
control+32px movement completes15120; zero-ring native Rhinobot landing death16089
ends the run. No waterfall/HCZ claim. Original mux failure cause remains unknown;
new native scale1 recording succeeds. Root reel edit preparation uses four ordered
shots from that exact movie, with the long Act2 ending before the later death.


### C3 compiled candidate and ICZ fail-closed import

Build84932 completed0 in69seconds at23:06:23BST on frozen14788e1c, Java25,
4048production sources. This is compile only; existing deprecation/removal and
unchecked warnings remain. The actual compiled recursive surface exports21107
lines,40 additions/zero removals versus21067, SHA256
aba80f0313e248e9984431a8cdd8b0ab29dce5e2c86a943aa21391b590ef5aec.
The unpublished0.7 candidate pin is generated from those classes; no published
baseline or version changes. C3 creators remain unwired.

Exact P13 230b23aa is applied to the STOCK root owner: placed helper rejection
returns native allocation failure without raw allocation, while layoutIndex<0
keeps the legacy raw component path. The patch adds its two owner-method controls
and changes no manager/policy/schema/API. Worker318807's71passing identities
include the genuine placed restore positive; the earlier624proof is unrepeated.
Root's STOCK owner omits the later FLANKED constructor/selector/public recipe.

Next composed verification covers the new C3 native owners and selected story/
replay consumers, ordinary checkpoint component, ICZ rejection+genuine placed
replay and actual API/pin/Javadoc/SDK packaging. Original S3K ROM and surfaceless
EGL remain explicit; actual XML identities/skips determine the result. It does
not repeat unchanged R3/host37 or assert physical476 checkpoint closure.


### Root107/artifact handoff and next native composition

53676 completed0 at23:36:23BST on frozen c0a3513c/HEAD5301:1322seconds admission
wait,98seconds Maven. Fresh13XML/fulltest identities inspected:107pass,zero
failures/errors/skips. C3birth6/DDZ19/HPZnative7/DEZ31/ICZrejection2/ordinary
checkpoint2/API10/pin4/HPZstory2/SSZselected6/genuinefrost1/Javadoc7/SDK10.
Strict actual bundled publisher and artifact verification also pass.
Build-only17658 independently exports the actualfatjar21107snapshot byte-equal
to the pin, strict-validates the actual bundle0findings and compiles/packages a
temporary typed consumer of all seven C3roots using the actual pairedSDK+engine
(0strictfindings). Probe source/classes/jar are removed, no registration/gameplay
claim. Immutable readonly matched artifacts are retained outside target under
second-strike-campaign/artifacts/53676-c0a3513c373e:
engine76ab99e288605412635a7930791b3c146bb9602bfdba27f05a111e468ff5bd38,
SDKf41bc45670fdd3f5e8eabb218f30b6cee84570a4ea2a2d991ddb852c052f7a3e,
actualbundle0dd53e4d032cd07ea3db5f864f4616a08ba0f5d607158b1858c3806ccc7bbbeb.
Campaign receives that exact pair/bundle for the one actual creator MGZ encounter;
prior native stand-in collected346..349 while actual content retains349only.
Native24233 hit is not packaged-content or damage-free economic proof.

After53676/17658 terminal, exact cumulativePLC ec1daef5 is composed: provider
b6dbbd34/postd7e96157/newtest8c4b66ad. Native plan merges by intent because its
preexisting history diverges; all three Java blobs remain exact. New activated
PENDING full-FIFO/recreation, exact-family marker and spawn-backed unload controls
are included. Astra no-blocker source review plus17new identities across16564
and9253 are separate from the original476 expected0/actual4 failure.
Checkpoint test61df13ac updates earlier697file27abe5toexact55dd, no new test
dependency or changed assertion. The next actual physical476 run composes this
producer repair with ordinarycheckpointfb74, original1f6creator and current root
engine; no old26/697/control/bonus repeat or checkpoint workaround is admitted.

Slots d7f73494 is composed as four owner-only production files plus explicit
fixture migrations/newloader andcanonical-ROM controls. Runtime reads the entire
bounded1E4080..1E4483 source (two entry words+1024cells) before state mutation;
production literal layout/start/default-buffer path is removed. Canonical stock
bytes match the former oracle; this repairs runtime source authority, not a
measured stock board mismatch. Coordinator production, payouts/RNG/cage/physics/
policy/API remain unchanged. Worker74852's128pass/19XML is bounded provenance,
not a root invocation. Existing SLOT_MACHINE production rewind capability remains
false: three graph/componentcodec passes do not qualify the dedicated live runtime.
No authored Slots variant, played reward/return, wholeM6 or missing-ROM fallback
is claimed. Root will run affected source/loader/coordinator/graph and physical
post476 checks only; no repeated API/SDK37/R3/624 or broad smoke yet.


### Composed post476 and Slots authority focus

22786 completed0 at23:49:51BST on frozen20bb84853a4cd62a7a440d3aa7497eb612cb02b8,
HEAD5301:441seconds admission wait/79seconds Maven. Fresh eight XML documents and
full testcase identities inspected:40pass,zero failures/errors/skips. Exact
selection: invitation-art admission17, unchanged physical post4761, Slots graph3,
coordinator7, layout data4, buffers3, canonical ROM2 and object codecs3. Original
locked-on ROM, explicit surfaceless EGL, no ambient display and immutable1f6creator
remain explicit. This does not repeat26/697/control, played bonus or API/SDK checks.

The actual composed post476 method now preserves every original assertion: earned
mark5/zero-ring opportunity, truthful native Eggman-monitor death402, new manager557
with stopped player10880/1402,0rings/lives2/index5/mark5, zero recreated bonus stars,
saved resize8/maxY1584 and ordinary grounded forward escape703 at11042/1329. The
CSV has737 observation records spanning703 ordinary driver frames; these are not
737 gameplay frames. The immediate557 reload still is black (presentation not yet
ready);703 escape still shows the native player/HUD/scene. Thus pending art admission
plus the ordinary checkpoint-owner restoration close this bounded physical death
witness together. The former FIFO error and later expected0/actual4 histories stay
as separate rejected states. Bonus return still resets index/used differently;
act-wide non-farming, deliberate revisits and all other route/breadth cells remain
open. No new checkpoint gate, queue capacity, forced processing or timing workaround.

The22 affected Slots identities pass on the composed source. This qualifies the
bounded ROM authority/fixture migration only: production dedicated Slots rewind
remains disabled, and authored variant/reward/return/M6 completion is not claimed.
Current public surface remains the generated21107-line candidate; these native
source changes add no creator API. Actual53676 artifacts handed to workers predate
this PLC/Slots composition and must not be relabelled as containing it.


### Inactive HPZ creator import and encounter boundaries

Exact41d4903a HPZ creator v2 imports two new files plus the existing owned plan
appendix. Java/test blobs remain byte-equal v1a262eea3/168db0b1. Only authentic
playable1601 record31 (11F0042C8228), all native fields and null ownership match;
LRZ boss1600, sanctuary1701 and Master Emerald record37 remain native.29394's
three real ModContext/ROM publication identities pass with no failures/errors/skips;
actual53676 paired javac/strict package proofb2c118c has zero findings. The root
provider remains five C1 calls plus boards: no HPZ/MHZ/LRZ activation. This is
source ingredient admission, not duel readability, ordinary side/back punish,
zero-ring recovery, cold route or whole HPZ completion.

Actual packaged MGZ46052 uses engine76ab99/SDKf41bc/bundle0dd53 (all predating the
new PLC/Slots source), authentic keeper/monitor checks and a real placed341 root.
Ordinary held-RIGHT jump produces HP8→7 at796 with no earlier/current player
invulnerability or hurt, then recovery takes damage801 and correctly fails the
no-damage/full-second-pair acceptance. No native fix or blind controller rerun is
justified by that outcome. Fixed frontal MHZ44377 remains separately pending;
compiled native Cluckoid detection is128x64 (not the earlier96 wording).


Maintained builder14483 completes0 at23:57:48BST after the inactive HPZ import:
Maven build-only1.462seconds (production classes up to date), ordinary Java25
creator compile and actual strict publisher zero findings. New67252-byte creator
jar cb2c4cb4a505163739e68edcac000b62eb694ac543dbda9fb631b30d6e935eac
includes the unwired ingredients. A subsequent intended-tree-lease export from
current compiled classes remains byte-equal21107-line candidate pin/SHAaba80f03.
No API/SDK suite or unchanged HPZ behavior test is repeated. Immutable53676 paired
fatjar/SDK are not replaced or relabelled; final coherent packaging remains pending.

### Combined smoke and guard result on 247fd438

Run `20261015T230210Z-1cbe254b` is terminal, not a green delivery. Exact command:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/run_categories.py \
  --base 76022570e8233823e1e36419666a0f29c768f4b6 \
  --max-minutes 90 --rom-directory "$OPENGGF_MAIN_WORKTREE" --run
```

Tested source was clean `247fd43810bce7e6f5dc9d3372dd7893cea8ab2d` throughout.
All 67 ordinary batches completed: 27,849 tests, one failure, two errors,
74 skips, 4,784 execution seconds. Guards completed separately: 712 tests,
six failures, no errors/skips, 272.19 execution seconds. Queue waits are separate;
no delivery wall-time total is inferred by adding overlapping worker intervals.
This is the complete selected smoke/guard run, not the release-only full suite.

Ordinary failures by exact identity:

- `TestMgzEndBossHandoffHeadless#eventSpawnedKnucklesBossCompletesRealFightCapsuleResultsAndAutoWalk`:
  missing synthetic upper event boss; the fixture still expects the removed owner.
- `TestSecondStrikeEastCampaign#threeCampaignsRegisterTogetherWithTheAizModWithoutKeyCollisions`:
  stale helper registers east campaigns after the real provider already registered them.
- `TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`:
  stderr-prefix assertion; source is identical at760/1259/247fd. Environment versus
  inherited failure remains unqualified until its bounded matched check.

Guard failures by exact identity:

- `TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline`: new `difficultyOwner`
  and `exitLookout` references lack explicit captured-policy declarations.
- `TestObjectPhysicsStandardizationGuard#productionObjectLifecycleRawCallCountsDoNotGrow`:
  raw destruction calls587 versus budget582; investigate only the new calls.
- `TestObjectUpdateClockTerminologyGuard#objectUpdateClockUsesVIntRunCountTerminologyAcrossBoundaryAndFrameworkHooks`:
  six new test update-parameter names use frame/tick rather than the ROM V-int clock.
- `TestArchitecturalSourceGuard#objectManagerFacadeStaysWithinExtractedCollaboratorBudget`:
  manager3132 effective lines versus3086; extract reservation business logic without
  changing the budget, snapshot ledger or allocation/release semantics.
- `TestNoServicesInObjectConstructors#objectsWhoseConstructorsReachServices_mustUseManagedCreationContext`:
  the bridge and two Hydrocity factory constructor chains need source attribution.
- `TestSingletonLifecycleGuard#ambientGameplayModeSetupsDoNotGrowWithoutLifecycleTriage`:
  `TestOwnedNativeBossRecipeBirth#headlessConfiguration` bypasses the shared reset.

All74 skip identities/reasons were inspected. They include explicit artifact,
capture, benchmark/soak and diagnostic opt-ins; Infinite Sonic topology assumptions;
graphics capability assumptions; and the existing CPZ tube-capture prerequisite.
They are unexecuted coverage, not passes. No missing-ROM skip was reported. The
graphics skips do not establish broad GPU pixel qualification. Existing separately
executed artifact/ROM outcomes keep their own source and coverage limits.

The source freeze ends at this terminal boundary. Preserve the failing identities,
repair owned regressions and verify affected checks only; do not repeat unchanged
67-batch coverage as a ceremony or weaken guards/baselines to manufacture green.

### Bounded repairs after the combined run

The unchanged updated base1259 reproduces the exact audio stderr-prefix failure
at `TestCompleteRunAudioCli.java:182`:15920, one failure/no errors/skips,817seconds
admission wait/25.857seconds Maven,03:11:52BST. Its command used the same inherited
`JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`; both the test and complete-run
audio production package are byte-identical at760/1259/247fd. No audio production or
test assertion was changed. The candidate's identical method passes with that
environment variable unset and the explicit headless JVM property retained. The
combined-run failure is therefore pre-existing under that launcher environment,
not a new Second Strike audio regression or an audio playback certificate.

19399 completed0 on HEAD1bff1b90 plus source overlay
`fa568f50b666ad2bdb44e5cec0f862379fada84db645d2c888f13714914ab81d`:
365seconds queue/74seconds Maven,03:13:07BST. All14 fresh XML identities inspected:
84 cases,83 passing,zero failures/errors,one explicit opt-in skip. Exact ordinary
selection (normal queue, DISPLAY/WAYLAND/JAVA_TOOL_OPTIONS unset, original absolute
S3K ROM and `-Dopenggf.headless.gl=surfaceless-egl`):

```text
TestCampaignNativeFactories,
TestWestCampaignBridgeLifecycle,
TestS3kCollapsingBridgeParity,
TestHczEndBossInstance,
TestS3kHczEndBossGraphRewind,
TestSecondStrikeEastCampaign,
TestOwnedNativeBossRecipeBirth,
TestAizNativeDifficultyPresets#nativeOwnerRemovalRetiresOptionalGraphsEvenWhileTheServicesRemainAvailable+committedPortAndHarmfulFragmentsRestoreAndRetireWithTheirExactShip+waterfallWithdrawalAndDefeatRetireCommittedFlameBombAndSmoke+failedMinibossWarningDoesNotBecomeAnUnwarnedWaveWhenSlotsReturn,
TestAizExitLookout#progressedBornLookoutRelinksAndContinuesOnceAfterDestructiveWorldRestore+actualSlotExhaustionOrOneFreeSlotKeepsOneRestorableAdmissionDecision,
TestMgzEndBossHandoffHeadless#nativePlacedKnucklesBossCompletesRealFightCapsuleResultsAndAutoWalk,
TestSecondStrikeEastNativeRecipes,
TestWestMgzBossNativeArena,
TestGameplayCaptureDataSelectLaunch,
TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput
```

Counts in that order:19,3,14,5,2,6,6,4,3,1,16,1,3,1. The historical MGZ
artifact method is the sole skip: `secondstrike.mgz.packaged.arena` is absent.
Its immutable76734/74c72d8a passing body remains separately attributed to53676,
organic349=1 and the bounded hit/glide/full-pair response. Promotion adds only a
distinct opt-in header/import: wrong artifacts/CodeSources/ROM/environment still
fail when enabled; ordinary current classes do not become artifact proof.

Repairs retain strict duplicate registration rejection and the actual placed A2
owner. Bridge zone configuration and HCZ end-boss character bounds resolve on
genuine service injection before first managed geometry/INIT, rather than requiring
ambient constructor services. No native child births, slot claims or timers move.
Existing captured mode/lock targets prevent reinjection from resetting progress;
direct construction, native zone variants and destructive recreation are checked.
The guard parser also rejects control-flow keywords as method declarations, with a
regression for the false HCZ-miniboss dependency and existing real dependency checks.

Explicit reference policies declare the already-tested native owner/child graphs;
six new owned cancellation branches use existing lifetime operations. Dummy test
update parameters name the V-int clock, and the host fixture uses the shared reset.
No baseline/guard budget was enlarged.

32731 completed0 at03:21:24BST on HEAD1bff1b90 plus overlay
`ecc0ae08a98b747cbf834f0b5e713c57ed3688401041b6ff3cc773f0910c6867`
(the Java inputs match19399; only this plan changed between requests). Admission
wait91seconds, Maven90seconds. Eight fresh XML suites contain62 passing identities,
zero failures/errors/skips: coverage1, lifecycle-raw-call budget1, update-clock
terminology1, ObjectManager facade budget1, constructor-service guard11, singleton
lifecycle triage1, rewind policy21 and schema25. The command used a fresh guards
JVM, DISPLAY/WAYLAND/JAVA_TOOL_OPTIONS unset and the explicit surfaceless-EGL
property. Its exact selection was:

```text
TestRewindCoverageGuard,
TestObjectPhysicsStandardizationGuard#productionObjectLifecycleRawCallCountsDoNotGrow,
TestObjectUpdateClockTerminologyGuard#objectUpdateClockUsesVIntRunCountTerminologyAcrossBoundaryAndFrameworkHooks,
TestArchitecturalSourceGuard#objectManagerFacadeStaysWithinExtractedCollaboratorBudget,
TestNoServicesInObjectConstructors,
TestSingletonLifecycleGuard#ambientGameplayModeSetupsDoNotGrowWithoutLifecycleTriage,
TestRewindPolicyRegistry,
TestRewindSchemaRegistry
```

All six combined-run guard failures are resolved by these affected checks. The
ordinary regressions are resolved by19399; the unchanged audio failure is attributed
by15920 as above. This is bounded follow-up to the consumed combined smoke run,
not a second broad smoke pass or whole-campaign certification. Actual final
engine/SDK/bundle packaging and compiled signature comparison remain next.

### Upstream-reconciled source and actual paired artifacts

Normal-hook repair commit78a4f085f and merge b3d307daf retain upstream develop
b07c52dfd's editor-chrome retirement without source conflicts. Upstream's own191
editor checks and681 guards are historical evidence for that separate change;
root does not repeat its whole tooling category. Root19448 instead verifies the
affected startup/capture/editor boundary on clean b3d307daf:56seconds admission
wait/89seconds Maven,03:32:18BST. Nine fresh XML suites contain88 cases, all passing
with zero failures/errors/skips. Selection was:

```text
TestGameplayCaptureDataSelectLaunch,
TestDevelopmentPatchRewindResolver,
TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading,
TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils,
TestEditorInteractionSafety,
TestEditorWindowChromes
```

The two distinct packages named TestSonic3kLevelLoading both execute (36 and7
cases); other counts are capture3, resolver4, AIZ-skip8, bootstrap6, decoding3,
editor safety17 and chrome4. Command uses the normal queue, original absolute S3K
ROM, explicit surfaceless-EGL property and unset DISPLAY/WAYLAND/JAVA_TOOL_OPTIONS,
ending in `verify`. Its post-test lifecycle also packages the real engine, SDK,
Javadoc/test-kit and default Time Attack bundle and passes the artifact verifier.

The maintained `python3 examples/second-strike/build.py` then compiles the actual
creator source and publishes its separate jar with strict zero findings. An
intended-tree build lease exports the API from the packaged fatjar:21107 lines,
byte-equal to the unchanged candidate pin SHA256
`aba80f0313e248e9984431a8cdd8b0ab29dce5e2c86a943aa21391b590ef5aec`.
Actual engine+SDK javac25 compiles a temporary typed consumer of MHZ/LRZ/HPZ/DEZ/DDZ
factories and FBZ/SOZ/SSZ enum services; strict CLI packaging passes zero findings.
Its first temporary-manifest attempt omitted required author/description metadata
and failed packaging after javac; only that fixture metadata was corrected. No
production/source/pin/validator change or new runtime acceptance is inferred.

Immutable individual artifacts (no copied build tree) are retained externally in
`second-strike-campaign/artifacts/19448-b3d307daffa7`, source b3d307daf:

| Artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Engine fatjar | 26673119 | `486afc9b090d5c3344b770f97a6248aa6204ef6c4dfe76cc4488db616c9c7b7f` |
| SDK | 249944 | `61d6adc0fedd324cd6c7a55238a85d9bdd3cc284c9dc8d45083b03bbed411586` |
| SDK Javadoc | 1348915 | `43e0bd4214d5e1475cb126ae35ecd601bb10eaae8cfa083faf43473ac6d61dba` |
| Second Strike sidecar | 67252 | `cb2c4cb4a505163739e68edcac000b62eb694ac543dbda9fb631b30d6e935eac` |

These supersede older pairs for new work, without relabelling any53676/1f6/AAC
physical witness. Inactive MHZ/LRZ/HPZ ingredients remain unwired; the rejected SOZ
golem is STOCK. Neither packaging nor88 startup checks closes the remaining cold
routes, whole-zone counterplay/hardness, bonus-family or ending/resource gates.

### Integrated foundation and bounded continuation

Main develop and origin/develop both resolve to
`606330ef70f1af905639cd296048d11d9f09517c` after fast-forward integration/push.
The post-integration source comparison against tested b3d307daf is empty for
`src`, `examples`, `racing`, POM and API policy; only the verification prose was
added. No unchanged broad smoke or artifact suite is repeated for that identical
runtime source. Main's three dirty disassemblies and five untracked user files
remain byte/status-preserved by the integration operation.

The clean lifetime helper at1bff1b90 is fully merged and retired under the main
worktree lease; its local branch is deleted and stale worktree metadata pruned.
Its accepted patches, plans and external physical evidence remain accounted.
Delivery and campaign trees remain for active work; the campaign lead, west/east,
AIZ content/combat/native and later-zone trees contain useful unmerged or staged
inputs and remain cleanup_pending. No unknown source, ROM, config or save files
are deleted. Main branch never switched; no feature branch was pushed.

Original work continues with two bounded assignments after the foundation push:

- Existing root Sol helper `second_strike_enemy_boss_scope` is reused for AIZ
  input-authoring in the existing AIZ completion tree. First outcome is a
  source-grounded ordinary-input plan past the known true-Act2 spring/Rhinobot266
  death from11708; no production edit, new worker or engine request before that
  plan is handed root. It receives the actual19448 pair/creator hashes. Complete
  native entry remains required for a selected research recipe to qualify a cold
  route, and all four reference axes remain open.
- Sonnet5.5 source-only ICZ shelter plan, task
  `second-strike-c1-icz-shelter-source-plan-20261016-r1`, owns authentic ICZ1 WARNED
  stalactite geometry/ordinary counterplay planning in the existing east tree.
  No engine request or API/native/creator edit in that bounded turn. This is new
  physical-counterplay scope, not a repeat of the repaired CNZ B replay or624 ICZ
  list/ledger checks. Root sequences any implementation/actual capture and fresh
  coherent Opus review later.

Neither assignment authorizes another MHZ calibration or changes the inactive
MHZ/LRZ/HPZ provider state. No M4–M6, all-route, harder-gameplay or complete-delivery
claim follows from this foundation checkpoint.

### AIZ completed-history restore repair — 2026-10-16

Fresh19119 uses actual19448 engine/SDK and the unchanged cb2c4cb4 creator. All22
common native-entry physics/camera/mode/act fields match its15784-pad prefix.
At15783 Sonic is free, grounded and rolling at6648/1313 with zero rings. Immediate
registered restore then changes `level-event.extra[216]` from1 to0. The request
stops before its64-input replay or any candidate. Queue516seconds; admitted
compile/execution approximately5.4seconds. Source comparison establishes that
the responsible event/schema code is unchanged from760 to e278; it is not a
matched baseline runtime result.

`minibossSpawned` is captured one-shot history. Native `AIZMiniboss_SpawnEndSign`
hands the retained actor to `Obj_EndSignControl`; results changes apparent act,
and the retained owner calls `Change_Act2Sizes` then deletes itself
(`sonic3k.asm:137395–137398,62713–62724,180420–180424`). Its later absence is
valid. Both native engine spawn cases advance the resize routine immediately;
clearing this latch alone cannot recover a lost active actor. The fix removes
only that presence-based inference. Initialization, end-boss reconciliation and
the distinct active ship-camera missing-driver backstop are unchanged.

RED86205 runs the new class against unchanged e278:3 cases, one expected
`level-event.extra[216]: A=1 B=0` failure, zero errors/skips, queue211seconds and
Maven24.778seconds. GREEN42171 on event656c2e266/testa023c677 runs:

```text
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS LUA_BIN=/usr/bin/lua5.4
python3 tools/testing/maven_queue.py -Dmse=off
  -Dopenggf.headless.gl=surfaceless-egl -Ds3k.rom.path=<original-absolute-ROM>
  -Dtest=TestAizMinibossHistoryRewind,TestAiz2ShipLoopRewindRoundTrip,TestS3kAizMinibossGraphRewind test
```

Three fresh XML suites contain8 passing cases without failures/errors/skips:
history3, ship1, miniboss graph4. Admission110seconds; Maven approximately70seconds,
04:35:34BST. The completed-history unit fixture is explicitly synthetic; the
active control uses actual native event spawn dispatch and destructive recreation.
All registered keys are compared with existing `RewindSnapshotDiff` semantics,
which normalize object ordering and omit some restore bookkeeping. The correct
claim is **all-key semantic snapshot equality**, not unrestricted byte equality.
The event bytes are not excluded. The unchanged ship test proves successful
driver recreation, not the missing-driver fallback branch.

Fresh delegated Astra R1 `second-strike-aiz-miniboss-history-astra-review-20261016-r1`
reviews exactly these two blobs, read-only, and finds no blocker. Actual fresh
15784-prefix restore and64 ordinary-input replay remain mandatory. No extra broad
smoke, unchanged SDK suite or full-route qualification follows from the eight tests.

Build-only98231 (`-DskipTests verify`) succeeds in38.714seconds at04:39:32BST.
The actual fatjar exports21107 lines byte-equal to the unchanged aba80f03 candidate
pin; the new engine/SDK strictly validates the unchanged cb2 creator with zero
findings. Immutable individual artifacts are in external
`second-strike-campaign/artifacts/42171-e278-history-656c2e2662ed`:

| Artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Engine | 26673043 | `9aa469752d4c4923bbee2b8791556e135103175386512a653829acf0e414134c` |
| SDK | 249942 | `9e7a0610bbd92c18a8704c875e08609e4a3b5a1a37126c70c9db7a780363131a` |
| Unchanged Second Strike sidecar | 67252 | `cb2c4cb4a505163739e68edcac000b62eb694ac543dbda9fb631b30d6e935eac` |

Build source is e278 plus the two recorded blobs;19448 remains immutable. The
default embedded bundle is still Time Attack; Second Strike remains a sidecar.

Independent research24954 runs four fixed alternatives in separate fresh native
JVMs, without capture/restore:301seconds queue/20.56seconds admitted execution.
Selected0 uses ordinary right after the spring, passes native Rhinobot266 above,
and reaches16113 at7585/966 with16 grounded safe updates, zero rings and no hurt,
death or ability press. Its16114-pad BK2 is c67d53e5 and input script6c51e3fe.
Alternative1 stops because the spring clears jumping, so the proposed insta-shield
is unavailable; no action is substituted. Alternative2 reaches its900-input cap
alive, and alternative3 incurs organic pickup/hurt and stalls. None is a complete
cold route or a rewind qualification.

The next bounded orchestration uses the new pair: one fresh JVM for the actual
reached-prefix restore/64-input replay, then an independent fresh JVM for the
selected16114 fixed pads plus one ordinary-input author. Its cached platform
intent now retires stale/unmanaged, missed, out-of-reach and departed targets;
retired layout identities cannot reacquire in the same world. An unsuccessful
target stops before its601st selected pad. These are author research stopping
rules, never native timers. Earlier unbounded platform steering already trapped
a cold attempt. A successful authored continuation would still need a separate
full fixed-pad native-entry replay.

Session89496 is terminal, one observer and unchanged frozen source/inputs. A driver hash
bbf7e6aa and B1bce2b2f use runner072cedd2 and the new42171 pair. A captures the
natural prefix, runs64 pads forward without restore, then restores the original
prefix and compares immediate all-key semantic state before replaying those same
64 pads (neutral first, external previous-input history restored). B is a separate
fresh JVM. Queue899seconds, admitted compile/two-JVM execution18.56seconds;
overall exit1 combines A exit0 and B exit1 and is not a failed rewind result.
Compiled A19a8e37a/B68c2bbee match their frozen sources. A's15784-row prefix CSV
is byte-identical to historical19119 (SHA7107660e); all51 registered snapshot keys
match immediately after restore and after64 identical ordinary inputs. All64
scalar state rows also match literally between natural and restored passes.
This closes that reached-prefix repair gate, not general rewind qualification.

B independently reproduces the fixed16114 native-entry prefix, actual first-boss
HP0/results and the60-free-control-frame/32px Act2 milestone. Its one author adds149
pads, then native death at16262/8238,1349 with zero rings. No waterfall, second
results or HCZ is reached. No platform target was acquired. The exact damaging
owner is not instrumented, so neither a native/content defect nor a safe response
is inferred. All three child process groups and the wrapper are reaped; owned
temporary target logs were inspected/consumed. Original19119/24954/19448 evidence,
external useful inputs/state/stills and the private unmerged tree remain retained.

### ICZ positioned entry attribution — 2026-10-16

Sonnet's r2 WARNED shelter fixture stops before measurement: the first setup-only
call follows an already-created ICZ intro owner, and an observer dereferences a
null-spawn object's default coordinate getter. No committed capture/replay or
STOCK comparison ran. Preserve nonspatial objects in observations; absent spawn
does not authorize filtering their identity/state out of a world comparison.

Read-only r3 traces the entry ordering: the ordinary boot loads ICZ with no saved
checkpoint, so native zone initialization creates its intro. The capture helper
applies `Settings.starPost` only afterward, then resets event references while the
existing intro actor remains managed. Its next initialization matches the observed
800/280 velocity and rolling/control state. This is source attribution; the exact
writer was not instrumented. It is not a measured stalactite or native ICZ defect.
The earlier row210 floor probe also returns−9, so the declared standing entry has
not yet been qualified; a possible missed previous-row surface remains attribution.

Fresh r4 `second-strike-c1-icz-entry-diagnostic-20261016-r4` is terminal: two fresh
JVMs compare the original late-starpost setup with an explicitly declared native
checkpoint/full-reload fixture, keeping the original seed unchanged. It observes
one call per arm, every object's identity including null-spawn owners, exact floor
queries/radii and actual setup/completed counters. No actor retirement, field,
queue, camera, physics or production helper change is authorized by this diagnostic.
Its two measured calls are SETUP_ONLY: Level/V-int deltas0 and stable LEVEL, not
completed gameplay updates. Arm A already has800/280, rolling radii7/14 and locks
at boot, yet no intro appears in its observed live-object lists. That falsifies
r3's specific pre-call live-intro prediction; no measured writer identity replaces
it. Arm B's explicit checkpoint/full reload creates a new Level/ObjectManager,
retains camera/player identity and leaves zero speeds, standing radii9/19 and no
lock before/after its setup-only call. It does not prove ordinary object-pass
readiness. No null-spawn object appears; r2 also rendered, so that crash's exact
object remains unattributed. No new helper/native fix or retry follows.

The offline correction uses the collision-index word stride. At55D0/55D8/55DF
on both paths, query20B returns−9 and210 returns−14: first solid row203, contact
row202, standing centre1EF with radius13 (1F4 with rolling radiusE). R3's prior207
surface interpretation used the wrong query row. The unchanged1FD seed is not a
qualified standing entry. The actual shelter/tell/landing/hardness witness and
the exact entry-owner source attribution remain pending. Both child JVMs were
reaped; execution1.7seconds, queue approximately1037seconds from runner timestamps.

### ICZ committed mechanism and STOCK control — 2026-10-16

Corrected source-only r5/r6 resolves the two actual feet, not just the centre
column: fixed centre55D8/1EE gives left/right distances2/0 with standing radii9/19
on both collision paths. The native checkpoint/full reload is a declared fixture
boundary, not physical death or a cold route. One subsequent call is setup-only;
ordinary calls then reach OFFSCREEN, INITIALIZING and WAITING with no lock,
movement, camera drift or neighbour commitment. The earlier proposed live-intro
writer remains unmeasured. Source traces initial intro locking followed by distant
unload without control release; it does not establish a native regression.

Root92726 stops at the fixture's population gate on completed call1, before a
commit or STOCK run. The gate incorrectly assumes a fixed character roster means
an immutable object population. The r5b repair allows only the exact pending
native insta-shield handle to become managed; all tuples, slot multisets and
snapshot comparisons still include it. Root28579 measures that registration in
both arms at call2: class InstaShieldObjectInstance, slot100/dynamicId8, null spawn.
This explains the repaired gate, not the exact uninstrumented object in r2's crash.
The old r2 loop already retained its capture row; no indexing defect was found.

Exact external r5b source SHA7a05a382, compiled class4c413c38 and root runner11aea902
use immutable19448 engine486afc9b/SDK61d6adc0/Second Strike cb2c4cb4 and the original
ROM. Command: `env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS
-u JDK_JAVA_OPTIONS LUA_BIN=/usr/bin/lua5.4 python3 -I run-root.py --execute`, from
the retained east tree, through normal admission. Root28579 exits0 after593seconds
queue and5.8seconds reported execution; the two fresh isolated child JVMs take2.0
and1.0seconds and are reaped. East source/index/status remain unchanged. This is
a capture witness, not a JUnit count or a repeated SDK suite.

WARNED authentic record165 at5660/190, dx88hex, commits on ordinary call4
(total call5), SHAKING/active/timer47. After capture,48 shake dispatches have net
zero X displacement; the live-state/local-motion plus native ceiling query derives
35 fall updates. It lands atY530 with12 debris, then executes one more update.
Immediate registered restore compares all51 keys semantically before any update,
plus complete object tuple and class/slot multiset. The same84 ordinary inputs
repeat every tuple and all-key comparison. Root independently compares all84 CSV
rows across31 native/state columns, excluding only external call/kind labels:
zero differences. No explicit fresh-Java-identity assertion is inferred.

The separate STOCK arm executes the same89 first-pass calls. It remains WAITING
after initialization, without falling/debris; WARNED's89 player/camera rows equal
STOCK's across14 columns. Both remain Sonic solo, standing at55D8/1EE, zero rings,
three lives, no hurt, death or invulnerability. WARNED is non-solid while shaking
and falling, solid after landing; STOCK is solid while waiting. CSV is read before
native320px PNGs: the landed body and both replay landing images match; the hanging
body is partly above the viewport, so this does not prove visible warning quality.
No traversal, under-body shelter, recovered lower path, harder gameplay, wider view,
other character, checkpoint or cold route is qualified. Runtime logs report the
existing LWJGL/JNI warnings and deterministic estimates for two unmeasured Kos jobs;
they provide no measured hardware-timing parity. Inspected raw child logs are
consumed; useful source, inputs/state, notes and native stills remain external.

Source-only r7 then maps the real spike objects, the landed stone's higher solid
top and the safe ledge. It finds no lower route beneath this slab. The proposed
fixed jump/retreat is a hypothesis, not an executed safe answer. Root corrects
the model's conflated stone-X/return-X precheck before preparation, and rejects
pixel-count thresholds as cue-readability acceptance or publication attribution.
Prepared W7 sourcefbcd9ef5/class14e7f3ee/runnere6667e2c retains the same19448 pair,
declared entry and all33 original row checks through88. Normal request97772,
submitted05:48:10Z, is terminal0. It permits one fixed WARNED attempt, one
STOCK control and a changed-pose replay only after genuine supported endpoints.
Negative gameplay endpoints deliberately exit0 as observations; wrapper success
alone must not be reported as a crossing, counterplay or replay pass.

Actual97772 waits482seconds; reported execution6.0seconds, WARNED child2.3 and
STOCK1.2seconds, both fresh JVMs isolated/reaped. All88 neutral rows match the
prior r5b authority. WARNED's ordinary jump89 lands147 at5650/1DE on actual
record165/slot7, manager rider/standing/OnObject true, with no terrain-foot support.
After braking, E1 at164 is5657/1DF; foot is exactly the stone top (initial touchdown
was one pixel above it). Return jump165 lands on terrain221; E2 at237 is55EA/1ED,
zero speed, no current rider/OnObject, real feet distances1/0, freezer idle.
Every WARNED row, including the later replay, retains zero rings/three lives and
no hurt, death or invulnerability. Native PNGs inspected after CSV visibly show
the stone landing and safe-ledge recovery. This is a declared positioned encounter,
not cold or physical-death entry, and does not supply a lower route.

At E1, the registered-world capture restores immediately with complete tuples,
class/slot multiset, actual target/rider identity and every registered key equal
semantically. All73 recorded return inputs replay with per-update tuple/all-key
equality. Root independently compares56 CSV state/support/input columns across
all73 pairs: zero differences. External call/kind labels and the non-rewound
observer event-history column are excluded from that literal CSV claim; the first
replay event line reports restoration of its future memo, not a world-state mismatch.
No objects or snapshot entries are excluded, and no fresh-Java-identity claim is made.

The STOCK control uses identical actual pads through147. The first physical
difference is landing at147: WARNED is supported, STOCK remains airborne while
its later-triggered stone is FALLING. Their shared policy then selects different
pads from148 (WARNED brakes left; STOCK remains neutral), and STOCK dies149 at
5657/1ED. This is not an identical149-pad comparison or proof that optimal STOCK
play is unsafe. It cannot establish increased difficulty; WARNED supplies a
useful earlier support for this particular wait/jump/recovery response.

Both arms' sampled tip boxes contain zero light pixels on calls4/5, then185 from
6 through52; native6/30 PNGs positively show the partly clipped hanging tip. This
disproves persistent absence across those shaking samples, but does not identify
the initial publication/palette/occlusion cause or establish full cue readability.
Published SAT remains unobserved. The prior84-update neutral replay is not repeated;
the new73-update replay covers changed player pose/support. Source/index remain
unchanged; inspected raw child logs are consumed. Wider/other-character approach,
under-body/lower-route shelter, route value, hardness and cold gates stay open.

### AIZ under-log input research — 2026-10-16

Fresh native-entry29449 uses immutable42171 engine9aa46975/SDK9e7a0610 and the
unchanged cb2 creator. Queue243seconds; admitted compile plus one JVM11.705seconds.
The fixed16114 prefix is unchanged. One ordinary-input author exception suppresses
only the obstruction jump beneath a live native overhead log when real ground
support and the native24x4 half-size touch band permit it. The source's pure
background-state observation avoids a cache-writing provider getter. No native
physics, hazard, timer, actor or latch field changes.

All26 common columns through16255 literally equal the prior continuation. The one
override16256 stays on the lower terrain; former death16262 is survived. The author
later dies16518 at8710/1360 with zero rings after405 new inputs. Native log344 and
its down-phase child are source/geometry candidates; the actual touch/crush winner
was not instrumented. No platform target is acquired, and waterfall/results2/HCZ
remain unreached. A stale historical latched pointer must not suppress terrain
queries when current OnObject/riding support is absent; no native pointer clear
is proposed. A source-only successor identifies ordinary Down with no Left/Right
as the native S3K roll input at speed>=256, with positive roll/radii admission
still required. Crouch alone does not shrink the S3K touch box. That response and
its calculated clearance are unexecuted, not a safety or route certificate.

Successor75430 preserves that native source/artifact pair and all16114 fixed pads,
but also corrects the author's current-support probe. Source5018ef56/class2881c179
and runnerdccb48b4 are frozen; the full13365-file tree fingerprint8e7c5680 is
unchanged before/after. Queue300seconds; admission05:34:58.869Z to child
termination05:35:08.076Z (9.207seconds), wrapper terminal05:35:09.018Z. Its one
fresh native-entry JVM exits1 at native death16637/8702,1299, zero rings,
16638 total inputs. No waterfall, second results or HCZ is reached.

Root reads CSV before the native terminal PNG. The first26 common columns match
29449 through16138; at16139 the now-enabled current-support probe reports no
forward terrain and selects an edge jump. That earlier path change prevents a
matched test of the proposed roll response: all524 authored rows report
not-requested, zero admissions. Airborne jump rolling is not Down-only ground-roll
admission. The later death occurs while descending near log344's upper spike
phase; current geometry is consistent with contact, but its actual damaging
callback remains unobserved. Do not infer a roll failure, a safe answer, a native
defect or route progress merely from the later death. The read-only source/CSV
follow-up must isolate the response from the earlier edge-input change before
another runtime proposal. Source/index remain intact, the child group is reaped,
and its inspected raw target log is consumed. Useful input/state/stills and
earlier rejected attempts remain external.

Isolated29024 then uses the exact16518 prefatal R4 input pads, with no route
author or snapshot search. Source448de4a9/classf4313f6c/runner0ac1c381, actual42171
pair and unchanged8e7c fingerprint are retained. Queue162seconds; admitted
06:03:25.801Z, child terminates06:03:32.708Z, wrapper06:03:33.642Z. All16518 prefix
rows compare literally across25 native columns; only the input-provenance label
is excluded, while actual BK2 held/action/start masks are checked independently.

At the naturally reached8707/1362/gSpeed736, actual current rider/standing support
is absent and the native344 child is down/9C. Four ordinary Down-only pads have
logical mask2/no actions. The first, at16518, admits native rolling/radii7x14 with
no spindash and survives the prior death point. At16521, Sonic remains grounded,
free, alive, unhurt and zero-ring at8717/1363/gSpeed677; normal gravity, OnObject
false, no current rider, log/child identities unchanged. The driver exits1 at its
declared UNPROVEN_NATIVE_GROUND_OR_EDGE guard before accepting an endpoint.
The logged centre-floor query passes (distance3/angle238), so the failure is in
the next-step ahead query; which found/distance/angle subcondition failed was not
logged. Do not invent it or relax the guard to report a green driver.

The stopped position's recorded geometry is horizontally past all three bands:
37px beyond the log centre exceeds native combined solid half-width35; visible
body and touch left edges exceed log/child right edges. That observation is
separate from the rejected floor-proof endpoint. Root reads CSV before the native
prefix/admission/terminal PNGs and consumes the inspected raw log. This supplies
positive native roll admission and four-input survival, not an accepted general
clearance, slope recovery, replay or AIZ/HCZ route. No native field, collision,
timer, resource or physics change follows. Further source work must distinguish
current native support from the future, direction-specific floor probe.

Post-clear81356 uses source dde0105f/class93ab0227/runnerece97050 on the same42171
pair. Admission is immediate (zero queue),06:26:21.536Z to child termination
06:26:27.524Z; full13,365-file source/index remains unchanged and the group is
reaped. All16,522 prefix rows compare literally across25 native columns against
29024. In this matched reproduction the ahead world-DOWN query is found/-2 at
angle222, outside the former declared author band; current actual feet/angle232
still represent native ground. This observes the repeated query, not the missing
29024 log value or a native attachment defect.

One Right+A at16522 admits native rolling jump/air with9x19 radii and velocity
63/-1118, followed only by Right. The fixed response stops after60 inputs at16581,
8726/1350, zero rings/alive/nohurt, groundModeRIGHTWALL/angle222/gSpeed-432.
The last foot scan is still DOWN, from the landing pass before that new mode.
The declared negative-velocity guard rejects the endpoint before its support
checks; no safe landing/recovery or full clearance is certified. CSV is read
before native prefix/jump/terminal PNGs, and the inspected raw log is consumed.
Rolling-jump admission is positive ability evidence; the continuing route and
the momentum/mode/sensor-phase interpretation remain source questions, not grounds
to change physics or tune a test to green. No search, restore or state write is
used. The complete16,582 admitted input rows remain external.

### HCZ oxygen authority — source-only prerequisite

The current actual creator converts all22 HCZ1 and21 HCZ2 ordinary monitors to
genuine Eggman contents; there are no original speed-shoes boxes excluded by its
predicate. This includes all six placed bubble-shield grants (HCZ1 originals
113/143/324; HCZ2 98/222/427). All nine HCZ1 and the sole HCZ2 maker221 remain
native/unbound; breath cadence, inhalation overlap, native35-update breathing
lock and water ownership are unchanged. This proves resource loss, not route
infeasibility or a native defect. In particular, HCZ2 oxygen reachability and
air budget remain actual-play gates.

Original HCZ1 maker114, raw12C808385480 at12C8/0838 below native water0680, is a
bounded mechanism candidate. Neither that placement nor nearby monitor109 proves
floor0840/standing082D. No supported start/endpoint or fixed input recipe is
established source-only. A later actual-artifact terrain/streaming prerequisite
must observe both current feet/native solids, real water/control/art and maker
identity before attempting a natural refill; no injected bubble, forced breath,
camera reset or economics change is proposed. All-character/act air sufficiency,
surface exits, bubble-monitor removal balance and cold HCZ gates remain open.

Terrain-only33051 on exact42171/cb2 is terminal0: zero queue, actual child0.887s
at06:24:48.874Z. From the ordinary HCZ1 native start it takes no gameplay/render
steps or position seed, and performs37,700 remote plus4 start-foot queries.
Both explicit foreground paths show a flat083F surface across both radius9 feet
for centreX1280..12E6 (query0830 gives+15,0840 gives-1). This derives candidate
standing082C from radius19; it does not prove body clearance or current support.
Original114 is authenticated and unbound, remote/live count0 as expected; the
actual start still has water0500, BUBBLER art ready and empty native queues.
All51 registered keys remain semantically equal and scalar/clock observations
are unchanged before/after; the group is reaped and raw log consumed. Actual
positioned streaming/water0680, ordinary supported entry, mature-bubble refill,
breathing lock and escape are still unexecuted.
