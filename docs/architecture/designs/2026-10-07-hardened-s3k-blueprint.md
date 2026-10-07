# Hardened Sonic 3 & Knuckles — design blueprint

[Shared roadmap and reconciled decisions](2026-10-07-mod-challenges-blueprint.md).
Research baseline: `develop` commit `09282b17305cb5794e43a26855cd2b9543b4ff5f`, inspected read-only on 2026-10-07. This deliverable specifies future implementations. **Observed** means inspected source or an explicitly dated maintained validation record, not a test run in this research round. Proposed contract names below are design vocabulary, not callable APIs.

## Product intent and recommended boundary

Build a readable, deliberately authored hardened campaign, accompanied by optional Kaizo precision trials. Scarce rings should make route choices consequential; enemies should demand observation and movement through committed, telegraphed attacks. The reference example must teach another creator how to author, package, certify and extend one encounter without modifying stock behavior.

Recommend three presets with separate records: **Practice** supplies learning/retry tools; **Hardened** is the campaign default; **Kaizo Trials** contains separately authored precision rooms. Avoid a universal “everything is faster” slider. A single global multiplier cannot certify geometry, enemy targeting, water, character movement or stage solvability.

The polished product target is a complete hardened Sonic 3 & Knuckles campaign, including authored special and bonus stages. Reach it through certified chapter releases. The first polished chapter milestone, provisionally **Island Trials**, contains six acts: AIZ1–2, HCZ1–2 and MHZ1–2. Its chapter menu provides explicit entrances and returns; this intermediate release does not claim uninterrupted whole-game completion. Certify the connecting MGZ/CNZ/ICZ/LBZ/FBZ routes and the remaining SOZ/LRZ/SSZ/DEZ/endgame progression before the final campaign promise. HPZ and DDZ retain their character/emerald eligibility contracts. Missing connecting-zone work must never be hidden behind a menu. Prototype and MVP begin in MHZ, whose solo-Sonic route has unusually useful existing evidence.

## Observed starting point

### Stock routes and inherited gaps

`src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java#initLevel` binds the main zone handlers; `Sonic3kZoneRegistry.java` inventories their acts. Registration is not route certification. The current ledger is `docs/status/level-test-coverage.md`; each act’s matrix under `docs/architecture/validation/levels/` records its actual limits. Old bring-up designs and superseded dated “pending” entries are historical.

| Content | Current evidence and campaign consequence |
| --- | --- |
| AIZ/HCZ | Live intro, fire, water, miniboss/boss and handoff owners exist. AIZ1’s maintained reload route ends at the fire resource handoff; the six-hit miniboss follows it. AIZ2 and HCZ2 need distinct full-route composition checks. HCZ1’s maintained complete route uses Sonic with CPU Tails; it does not prove a solo hardened route. Keep this chain stable. |
| MGZ/CNZ/ICZ/LBZ | Actual event/object implementations exist. MGZ’s Knuckles background-rise branch is explicitly unported; ICZ’s Tails-alone/Knuckles intro branches are inactive. CNZ has distinct team/control/teleporter paths. LBZ1’s October 3 trace remains red. The per-act matrices retain traversal/configuration/presentation gaps. These zones are outside the first chapter milestone, with route work required for the full campaign; they are not declared unsupported. |
| MHZ | The maintained matrix records native Sonic, Tails and pair completion through both acts, and 800px Sonic into FBZ, with remaining breadth/presentation gaps. `src/test/java/com/openggf/tests/TestS3kMhzAuthoredRoute.java#freshSonicCompletesActOneAndReplaysItsLiveInteractions` drives 9,301 cold solo-Sonic inputs, six miniboss hits, MHZ2 load, released movement and six restore/replay spots. This is the best bounded MVP baseline, not full certification. |
| FBZ/SOZ/LRZ | Current ledger records substantial native character routes, exits and replay windows; broader donors, viewports and native presentation remain incomplete. Later SOZ entries supersede earlier Knuckles “Act2 open” notes. FBZ has the explicit `fbz-routes` deeper lane; do not invent equivalent profiles for other zones. |
| HPZ/SSZ | Sanctuary/character-specific progression and SSZ routes need their own owning matrices. Neither a hub load nor a boss-arena alias is a full independent act. Preserve Knuckles-specific exits and emerald conversion. |
| DEZ/DDZ | Later DEZ per-act records supersede the ledger’s old 800px Act2 frontier: `s3k-dez-final-boss.md#cold-solo-sonic800-ending-route-2026-09-28` records 60,298 inputs through the ending load, integrated `8cebbd847`, with 177 two-cycle replay windows. Other breadth remains open. DDZ requires emerald/character entry contracts; neither belongs in the first product promise. |

MHZ’s matrix preserves a historical task exclusion of further Knuckles implementation work; that is neither technical unsupported status nor a permanent prohibition on this future campaign. A Knuckles chapter requires a distinct route design and certification; the solo-Sonic MVP does not certify it.

### Creator seams that exist, and seams that do not

* `mods/code/ModContext.java` transactionally registers owner-local objects, patches, zones and launch policies. `game/patch/GamePatch.java`, `DelegatingGameModule.java` and `game/GameModule.java` expose module/provider decoration, level overrides, session services and rewind adapters. Object code uses injected `ObjectServices`; recreated objects need `RewindRecreatable`.
* `examples/infinite-sonic/src/main/java/infinite/InfiniteSonicMod.java` and `examples/sonic-survivors/src/main/java/survivors/SurvivorsMod.java`, both `Module#loadLevelOverride`, remix locally loaded ROM terrain/art without shipping ROM bytes. Their tests are `src/test/java/com/openggf/mods/code/TestInfiniteSonic.java` and `TestSonicSurvivors.java`. Survivors removes stock events/progression; copying that decision would break this campaign.
* S3K additive format-v2 zones exist through `game/sonic3k/Sonic3kModZoneAdapter.java`, but their profile is `flatEmpty`: no stock events, animated tiles, PLCs or advanced render modes. `mods/StockProgressionAnchors.java` exposes **no S3K insertion anchors**. `gameStart` selects a fresh tagged zone; it does not connect stock cutscenes or results.
* A generic `Level` wrapper is unsafe here. `Sonic3kAIZEvents#applyBattleshipTerrain`, its fire-transition path, `Sonic3kZoneEvents#applyPlc`, and `S3kSeamlessMutationExecutor` require `Sonic3kLevel`. `level/LevelManager.java#loadActTransitionLevelData` discards prepared loading when an override supplies a level. Preserve these native owners and deferred resource boundaries.
* `ModContext#registerRomObjectArt` currently accepts only S2 patch mods. S3K can reuse a resident ROM-loaded renderer, subject to readiness/palette ownership; arbitrary new S3K art intake is a prerequisite. `game/sonic3k/objects/badniks/AbstractS3kBadnikInstance.java` is package-private. Use public shared bases or a narrow public semantic helper.
* Special/bonus provider interfaces are public Mod API, but bounded authored-stage registration is absent. Ordinary level destinations cannot identify a Blue Sphere board, bonus interior and return contract. `GameModule#gameplayStepsPerFrame` affects LEVEL, not SPECIAL/BONUS.

These source paths are relative to `src/main/java/com/openggf/` unless fully qualified above. Maintained contracts are `docs/architecture/next-line-subsystems.md` and `docs/modding/{content-mods.md,concepts/trust.md,formats/level-definition.md}`. The descriptor `mod-api-release-policy.properties` says unpublished candidate `0.7.0`, with no published baselines. New public surfaces must reconcile it, `mods/ModApiVersion.java`, and `src/test/resources/mods/mod-api-signatures-0.7.txt` under the compatibility policy; this blueprint grants no release/version bump authority.

## Recommended architecture and difficulty rules

Keep authored chapter/encounter data inside a `hardened-s3k` example mod. Generic dependencies for the Mutators lead are only: owner-scoped launch/configuration selection; bounded stock-placement transforms; semantic encounter lifecycle notifications where polling cannot express ownership; and typed stage extensions. This blueprint can implement those small host contracts itself. It requires no Multigame container, generic scripting language or dynamic enemy taxonomy.

The proposed placement transform runs after ROM decode and before object/ring placement, preserving the concrete native level, source identities, native palette/art state, prepared/deferred loading and stock event owners. Freeze bounded value plans transactionally; attribute the owner from registration, never creator-reported identity. Apply exactly once on normal entry, checkpoint reload, stage return and seamless handoff. Retain checkpoints, bubbles, plane switchers, mandatory movers and boss triggers. **HCZ Blastoid defeat opens paired bridges** (`game/sonic3k/objects/badniks/BlastoidBadnikInstance.java#onPlayerAttack`); replacing it with a generic tougher enemy can block the route. Layout alterations use `ZoneLayoutMutationPipeline` / `LevelMutationSurface`, with captured authoritative state.

Launch an explicit campaign session with an immutable route/preset/seed/content hash and `SaveSessionContext.noSave(...)`. Current `GameplayLaunchRequest` carries only game/team, not a campaign intent: extend the host launch boundary rather than storing selection in mutable static state. Native save slots must remain byte-identical across challenge starts, deaths, stages and completion. Future campaign progress lives in owner-local storage with schema/version migration; credits, emeralds and assists never masquerade as stock clear flags. Unsupported mod combinations need explicit tested rejection before world mutation; do not silently force user preferences or describe missing stock coverage as unsupported.

For Hardened, remove most **ambient** rings through an explicit retention map, not a coordinate hash or random deletion ratio. An initial 80–95% reduction is an authoring hypothesis; acceptance is a verified per-room resource budget. Keep a safe recoverable ring after each checkpoint, optional shield/recovery branches, and intentional bonus-entry banks. Retain native damage, ring spill/recollection, hurt invulnerability and elemental-shield behavior. Every mandatory route must be completable without taking damage or consuming a one-shot powerup; avoid required damage boosts. Practice can add recovery aids as a separately tracked preset.

Mod-owned enemy variants should use finite tell → committed aim → volley → recovery states. Snapshot phase, timers, aim, PRNG and child identities. Use admitted simulation ticks with a named clock; stock gates still read their owning ROM clocks, including `V_int_run_count`. Never use wall time, render count, trace rows or route-frame constants. Set encounter-wide projectile caps, expiry, culling and allocation-failure behavior. No harmful offscreen spawn or harmful projectile before its art/tell is available. Keep a safe waiting/escape option and a visible shape cue as well as sound; color alone is insufficient.

Rejected alternatives have concrete kill evidence: a stock-wide rate/HP patch changes inactive gameplay; generic wrappers bypass S3K art/terrain owners; additive zones do not inherit campaign events; unrestricted random ring removal can remove checkpoint recovery and bonus eligibility; Survivors’ ring-toll health rewrites native survival rather than expressing scarcity; developer rewind alone is not a controlled, separately scored practice system.

## Stages and completion gates

### Prototype: MHZ1 “Post Two Ambush”

Deliver one surveyed short route beside the real MHZ1 post at native centre `(0x1D60, 0x01A8)`, subtype/index 2. `src/test/java/com/openggf/tests/TestMhzCheckpointRoutes.java#AUTHORED_CHECKPOINTS` and `#approachingEveryPlacedPostActivatesAndReplaysItsSave` establish the post and local approach `(0x1D30, 0x01A8)`; they do **not** establish combat-safe surrounding geometry.

Survey floor, headroom, camera edges and native objects first. Author one clearly marked spore sentry, two readable volleys, scarce safe rings, the untouched physical post and a visible exit milestone. Use MHZ-local ROM art; the stock Mushmeanie’s two-hit shell/recovery is design evidence (`game/sonic3k/objects/badniks/MushmeanieBadnikInstance.java`, `Obj_Mushmeanie`), not permission to mutate private timers. `0x8D` means Mushmeanie in SKL but Rhinobot in S3KL. A borrowed shell needs an unmistakable harmful cue because the stock detached shell is harmless.

Deliver a code-bearing jar containing its manifest and authored encounter data, one human walkthrough and one agent-authoring walkthrough. It runs on the JVM build; native-image execution of arbitrary mod code is not supported. Target native solo Sonic, donor off, width 320, no-save. Gate: real post contact, playable entry/exit, both safe and failure solutions, ring/shield behavior, owner-fault abort, projectile recreation and two restore/forward-replay cycles. Non-goals: full act, altered bosses, stage difficulty, save progression, alternate characters or Kaizo certification. If the host transform/launch seams are unavailable, the artifact remains an experimental encounter preview; a flat additive arena is not renamed an MHZ campaign prototype.

### MVP: a complete hardened MHZ Act 1

Deliver normal entrance → mandatory traversal → six-hit native miniboss → results → real MHZ2 load and released movement. Add authored encounters and ring budgets throughout the act, using a fixed Hardened preset; preserve the boss contract while making approaches harder. MHZ2 serves as a short verified exit landing, not an advertised hardened act. Keep stock special/bonus visits optional and unchanged, with real return tests.

Target solo native Sonic across all five production presets. Require cold continuous input completion, independent event/checkpoint/projectile tests, no-save isolation, repeatable config/seed records, both rehearsal and unassisted records, and the validation matrix below. Native asset, presentation and lifecycle gaps remain explicit until exercised. Non-goals: changing native physics, arbitrary sliders, campaign-wide bosses, donor support or Knuckles routes. If a supported-engine configuration has no mod rejection contract, its uncertified cells remain open; do not claim the act meets the standard by omission.

### Polished product: complete hardened S3&K campaign plus Stage Trials

Deliver the complete connected hardened campaign, including its intermediate zones, character-specific progression, bosses and eligible endings. The first six-act Island Trials chapter release is a useful intermediate milestone; it does not satisfy this final gate. Every connecting stock route must meet its prerequisite matrix before authoring its hardened counterpart. Advertise only character routes actually completed and certified, then expand to Sonic, Tails, native Sonic/Tails pairing and Knuckles with separate authored route packs and their native progression/emerald obligations. Donor compatibility is a separate certified extension rather than a substitute for native-character coverage.

Provide consistent title/HUD/controls, navigable encounter lessons, controller-friendly checkpoint retry, optional bounded practice rewind, seeded/configurable replay sharing, reproducible captures and complete campaign progress persistence isolated from stock saves. Kaizo Trials are optional short rooms with their own precision labels, inputs and instant safe retries. Authored ring scarcity and encounter difficulty extend throughout the campaign rather than stopping at the first chapter pack.

Stage Trials delivers a curated harder Blue Sphere set and harder versions of all three native bonus families, after their typed adapters and stock audit gates land. Retain ROM terrain/art/audio; themes use verified resident art, owned palette transformations and ROM music/SFX through their normal owners. New nonresident art needs bounded S3K mapping/DPLC/PLC intake and readiness tests. Do not ship extracted art/audio/layout payloads as a shortcut.

Completion requires every advertised route/stage to be finishable with recorded native inputs, stage return/reward/reset coverage, compatible route/config manifests and measured human playtests. Proposed pilot: three independent players receive only in-product instruction; each can explain the tell and clear every mandatory room after practice, and at least two complete the learned act unassisted. Log deaths, retries, cause and assistance. Mandatory blind hits, unavoidable spawn damage, powerup softlocks or unreadable tells block acceptance. These are proposed product gates, not observed playtest results. If a required act or stage fails its prerequisites, release the accurately named completed chapter pack while the full product remains incomplete; do not shrink the final goal to manufacture completion.

## Special/bonus prerequisites and cut lines

Use typed, owner-scoped host plans for ROM-backed board transformations, bounded speed curves and variant rules; the host owns clocks, collision, assets, entry/return and rewards. For Blue Sphere, validate cell types/counts and native-input solvability; geometric reachability alone does not prove timed turn/jump feasibility. For bonus stages, certify Slots, Glowing Spheres and Gumball separately, including ring/item awards, depleted pools and forced exit.

`Sonic3kStarPostObjectInstance#computeBonusStarVariant` selects `((rings - 20) / 15) % 3`: 20–34 Slots, 35–49 Glowing, 50–64 Gumball, repeating. Entry rechecks 20 rings. Current `#shouldSpawnBonusStars` additionally gates seven Chaos emeralds; the inspected ROM routine apparently lacks that gate. `specialstage/Sonic3kSpecialStageManager#loadRomData` selects board family by index, while HPZ supplies 0–6 with a Super reward; the ROM uses `SK_special_stage_flag` independently. Both are **source-backed audit hypotheses**, not measured failures; native corroboration is a prerequisite before designing stage difficulty around them.

Native special-stage snapshots are registered by `GameLoop#doEnterSpecialStage` through `game/session/GameplayModeContext.java#registerSpecialStageAdapter`. Bonus adapter discovery currently requires `AbstractBonusStageCoordinator`; proxy replacement is not a generic guarantee. Slots explicitly disables rewind and needs a dedicated runtime snapshot before Stage Trials promises it. `mods/runtime/OwnerBoundGamePatch.java#returned/queryInterface` needs an ownership audit for Optional adapters, results callbacks and bootstrap factories. These are inspected risks, not fault-injection results.

Special results request a save; `src/test/java/com/openggf/TestGameLoop.java#testDoExitBonusStageDoesNotWriteSaveForActiveSlot` asserts bonus exit does not. Contrary discrepancy-guide prose is stale. Preserve native return envelopes: origin/checkpoint, respawn/ring activation, event/camera/water, shield/path, timer and live stage rewards (`SpecialStageTransitionSupport.java`, `game/BonusStageTransitionCoordinator.java`). If prerequisites fail, ship the bounded campaign with stock stages and list Stage Trials as unfinished.

A worked later **Blue Trial 01** keeps verified ROM Chaos board 0, native turns/jumps/conversion and the native speed cap, but proposes acceleration every 1,200 admitted stage ticks instead of 1,800. The author freezes board identity, schedule and content hash; independent cold input must finish it, with replay around each rate boundary and the final reward/return. This is an untested difficulty hypothesis requiring the typed adapter, not a current CLI option. Layout trials follow only after cell-plan validation and a native-input solution; a static board solver is insufficient.

## Implementation sequence and worked authoring example

1. **Baseline and launch safety:** inspect chosen route fixtures; implement campaign intent, no-save/support rejection and immutable configuration at `game/patch`, `game/session`, data-select/title adapters and `mods/code`. Test native save isolation and inactive launches first.
2. **Placement contract:** implement bounded transforms around `level/LevelManager`, stock prepared/deferred load installation and native `Sonic3kLevel` spawn inventories. Add registration, ordering, fault and repeated-load tests before authoring encounters.
3. **Reference encounter:** create proposed `examples/hardened-s3k/{build.py,README.md,src/...}` with `CampaignSession`, `EncounterPlan`, `Sentry`, `Projectile` and minimal presentation. Use public bases/injected services and module rewind adapters. Build the prototype’s independent local tests.
4. **Act composition:** author MHZ1 retention/encounter data; drive the full route, miniboss, checkpoint resets, stage visits and real exit. Update its matrix/backlog. Do not expand unrelated stock-trace development merely to make the campaign example pass.
5. **Campaign/practice:** expand first to the six named acts, then to the remaining campaign acts after their stock prerequisites and route packs are certified, validated semantic completion/return boundaries, alternate route packs and bounded practice rollback. Keep assists outside world rollback where intended; full developer rewind must still capture owner state. Invalidate obsolete checkpoints at load/reset boundaries.
6. **Stages/polish:** close native stage audit questions, add typed adapters and all stage matrices, then finalize lessons, presentation, package reproducibility and support diagnostics. Shared host contracts use normal change-based validation; content authoring cannot waive those checks.

The following is a **proposed authoring format**, not accepted by today’s CLI. It is a worked starting design, deliberately blocked until a geometry/art survey fills the binding. Fixed values are pilot choices, not ROM constants or tuned trace measurements.

```yaml
schema: 1
id: post-two-ambush
route: mhz1-sonic-native
romSha1: CFBF98C36C776677290A872547AC47C53D2761D6
source: { zone: 7, actIndex: 0, objectZoneSet: SKL }
anchor: { role: star-post, subtype: 2, centre: [0x1D60, 0x01A8] }
binding: { status: requires-survey, protectCheckpointApproach: true }
rings: { policy: explicit-retention, checkpointRecovery: 1 }
sentry:
  key: hardened-s3k:spore-sentry
  art: { residentKey: mhz_mushmeanie, requireVerifiedFrames: true }
  phases: { tellTicks: 36, volleyCount: 2, recoveryTicks: 90 }
  projectileCap: 4
  aim: commit-at-tell-end
acceptance: [safe-wait, no-damage-clear, real-post-respawn, recreate-replay]
```

An author surveys the anchor, supplies explicit retained ring placements and sentry/escape coordinates, records approved art frames/collision bounds, and freezes a content hash. A validator rejects `requires-survey`, unmatched ROM/placement signatures, invalid ownership, overlaps with protected mechanics and budgets above limits. Existing registration uses `context.registerObject("spore-sentry", factory)` and `registerObject("spore-shot", factory)`; the future transform registration is a prerequisite. An agent changes one pattern, regenerates the plan/package, runs independent checks, authors new controller inputs and reports changed certification cells. It never edits stock class timers to satisfy the example.

## Validation matrix and reproducible recipes

Each act/character route gets ENTRY, OBJECT, EVENT, CAMERA, BOSS, LIFE, LOAD, REWIND, PRESENT, ROUTE and ORACLE rows per `docs/guide/contributing/level-test-standard.md`, linked from `docs/status/level-test-coverage.md`. Record native contracts separately from intentional mod rules. Every cell records setup/oracle, configuration, command, commit, skips and failure state.

| Independent lane | Required assertion |
| --- | --- |
| Disabled/inactive | Original providers/spawns/assets/physics/RNG and stock route/transition behavior; campaign intent absent even when jar is enabled. |
| Owner/data | Poisoned registration publishes nothing; invalid plans/art fail before harmful play; callback faults identify/pending-disable the owner/dependents and abort safely. |
| Encounter | Before/at/after tell/fire/recovery, harmful versus eligible contact, shield/hurt windows, scarcity budget, allocation pressure, offscreen re-entry and child cleanup. |
| Lifecycle | Physical posts, zero-ring respawn, death/restart, repeated loads, stage entry/exit, boss defeat and real next-act control. No duplicate transforms or leaked owners. |
| Rewind | Capture non-default A; advance inputs to B; destroy/mutate graph; restore A; replay to B twice. Include live shots, hit/death, checkpoint, camera, terrain/palette and prepared resources. At reset loads prove timeline isolation and rejection of stale handles. |
| Route/presentation | Cold entrance→mechanics→boss→exit with authentic inputs; GPU-visible tells/hazards, palette/art readiness and audio lifecycle. Positioned captures prove local behavior only. |

Use actual presets **320/352/400/528/800** from `configuration/WidescreenAspect.java`. The standard and older AIZ/HCZ rows still mention 512/640; those are historical/custom-width evidence, requiring documentation reconciliation rather than invented preset mappings. For enabled donors/teams, run the standard’s width × donor short lifecycle cross-product, meaningful axis traversals, short rewind per axis, character-specific routes and risky combined cases. Initially reject unadvertised mod teams/donors explicitly; full product compatibility expands only with evidence.

Future commands, **not run in this research round**:

```sh
# Existing stock baseline; ROM must be discovered at its actual absolute path.
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kMhzAuthoredRoute,TestMhzCheckpointRoutes,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  '-Ds3k.rom.path=/absolute/path/to/user-supplied.gen' test

# Existing tools; future example/script/jar names are deliverables to create.
python3 examples/hardened-s3k/build.py
python3 tools/testing/maven_queue.py exec:java \
  '-Dexec.mainClass=com.openggf.tools.InputLogAuthorTool' \
  '-Dexec.args=--script target/hardened-s3k/mhz1.script --out target/hardened-s3k/mhz1.bk2 --game s3k'
python3 tools/testing/maven_queue.py exec:java \
  '-Dexec.mainClass=com.openggf.tools.GameplayCaptureTool' \
  '-Dexec.args=--game s3k --zone mhz --act 1 --width 320 --main sonic --sidekick none --rom /absolute/path/to/user-supplied.gen --mod target/hardened-s3k/hardened-s3k.jar --input target/hardened-s3k/mhz1.bk2 --out-dir /absolute/task-captures/mhz1'
```

Inspect `state.csv` before frames/video; run `TestGameplayCaptureSmoke` when graphics setup changes. Captures are gameplay evidence, not native parity. Normal deterministic launches force external mods off: example integration must explicitly install the packaged trusted mod, as existing sample tests do; `GameplayCaptureTool --mod` is the explicit capture path. Verify ROM identity and skips; a zero-exit skipped suite proves nothing. Use future focused mod tests plus relevant guards and actual `run_categories.py --base <integration-base>` selection/preflight for implementation delivery; no research engine suites. Promote a new survey tool only if recurring need warrants it; existing `FloorSegmentSurveyProbe`/`ObjectArtContactSheetProbe` are test probes, not a published authoring API.

## Documentation, decisions and research record

Ship a deterministic Java-21/two-artifact build/package/validate recipe, manifest/API/trust instructions, no-ROM-byte jar inventory, installation/disable/recovery guide, and JVM/native-image support table. Include encounter/ring/resource templates; a complete before/after tutorial; valid/invalid plans; route/seed/config/hash/replay manifests; certification matrices; practice/assist rules; stage examples and capture chapter map. Teach agents to cite owning methods and changed matrix rows, with worked fault, rewind and rejected-plan examples. Creator guidance belongs under `docs/modding/`, matrices under `docs/architecture/validation/levels/`, and decision/rejection evidence in the existing dated design artifact.

Product decisions to refine before later implementation: choose target audience/playtest difficulty, whether Kaizo medals require unassisted completion, and the release order and advertised character breadth of chapter packs. The full-campaign goal is retained; the first six-act pack is an intermediate cut, and Knuckles work gets its own route scope rather than inheriting historical task exclusions. Recommended defaults above let blueprint integration proceed without waiting. Ring-reduction percentage, pilot tell duration and encounter locations are authoring hypotheses to resolve by survey/playtest, not policy decisions requiring another approval ceremony.

Research checks: `git rev-parse HEAD` matched the base; initial and final `git status --short` were empty. Only read-only source/doc/test/disassembly exploration ran; no tests, build, capture, probe, dependency install or Git mutation. Two delegated investigations covered route inventory and stage APIs; their substantive findings are incorporated above. Both leaf tasks completed; no nested work remains active. All new gameplay/acceptance claims await future execution.

## Prototype implementation decisions — 2026-10-07

Original base remains `6d817a9d74f135714f3da59ab9aab156cc09473e`. The
[implementation plan](../plans/2026-10-07-hardened-s3k-prototype.md) executes only
the local prototype; the MVP and campaign/Stage Trials gates above remain future
work. The maintained JVM authoring values are in `examples/hardened-s3k`; the
YAML sketch above remains a proposal, not a parser contract.

The production sensor survey rejected extending the encounter to the right:
the upper shelf ends beyond `0x1DB0`, before the native spring and mushroom
platform. The final rectangle is `(0x1D10,0x100)..(0x1DB8,0x1D0)`, with the
unchanged post/approach, sentry `(0x1DA0,0x1AD)` and exit `(0x1DB0,0x1AC)`.
The shelf contains no original rings. Inventing a retained native identity or
using a mod-drawn ring would bypass its native owner; a typed coordinate-only
native ring addition supplies one recovery ring at `(0x1D70,0x1A8)` instead.
The engine assigns its distinct identity beyond the complete original inventory.

The reusable placement contract preserves the concrete native level and every
native object. It removes only unretained ambient rings inside explicit bounds
and appends same-owner registered objects/native rings. Registration is
transactional and frozen; decoding and prepared/deferred installation retain
their owners. No enemy taxonomy, global spawn-rate rewrite or blanket event
removal is introduced. The first integrated gameplay source checkpoint is
`936d55fe7` (child `3f4992e93`); it carries no test-pass claim.

Launch is a dedicated enabled patch module with a session-owned title and
ledger. The normal launcher verifies the surveyed logical ROM hash. Explicit
fresh-centre and no-save opt-ins preserve native defaults and run before stock
save attachment; the fresh hook never controls native death or stage return.
The frame controller separately opts into native input in PLAY. Neutral ordinary
entry rows let the native art/fade owners finish; menus hold the world. Existing
golf keeps its neutral default.
Independent review rejected unconditional checkpoint retry before contact:
the menu now names an explicit fresh attempt until the physical post is active,
then uses native death re-entry. The choice is captured by rewind. Completion
waits for both volleys and full recovery, avoiding immediate second-shot disarm.

These contracts do not depend on either peer branch. Integration overlap is
expected in `ModContext`/`ModRegistrationPlan`/`ModBackedGamePatch` and candidate
signature pins with Mutators, and module/input/session owners with Multigame.
Those teams own mutator policy/configuration and exclusive worker driving;
neither authority is implied by this prototype's native-input opt-in. Final
PR evidence will identify the exact changed-file overlap. The existing release
descriptor already declares the unpublished 0.7 candidate; the policy hook
forbids descriptor edits during ordinary candidate-pin regeneration, so it stays
semantically unchanged while runtime description and normalized pins change.

The [local MHZ1 matrix](../validation/levels/s3k-mhz-act1.md#post-two-ambush-local-prototype--2026-10-07)
tracks actual execution/capture obligations. Placement commit `8c89262b9` and
session commit `84459ddcd` preserve the original base; all temporary imports were
reversed before their coherent integration. Their combined compiled API export
matches the normalized 0.7 pin (20,124 lines). Placement's 60 distinct focused
cases and session's 55 focused cases pass without skips. The encounter geometry
and all eleven core methods pass across bounded corrected runs; fixture corrections
changed no production physics or timing.

The first production walkthrough reached physical post contact, both volleys,
clear, native post retry and title return with actual GPU frames and stereo PCM.
It rejected a seemingly sufficient headless entry: MHZ1's cold native camera
owner forces its distant introduction focus, leaving the local entrance invisible
while follow catches up. The bounded correction marks an actually admitted fresh
position for that load's native camera initialization; it does not fabricate a
checkpoint, write camera coordinates from the mod, remove events, or persist an
entry override through death or stage return. The marker is cleared/consumed at
load boundaries. The integrated packaged example and four mandatory S3K
regressions passed 65 cases with zero skips at 14:19 UTC. The camera follow-up
observed the correct native death camera `0x1CC0`; its initial new fixture
incorrectly expected the banked value, and the one-line assertion correction
passed its corrected matched rerun at 14:52 UTC. The actual merged `d5e6eb613`
compiled-source focus passed 122 cases without skips; its compiled 20,126-line
candidate export matched the committed pin. Final polished capture and broad
validation remain pending. An exploratory capture or focused result is not a
broad pass.

## Owning references

- [Level test standard](../../guide/contributing/level-test-standard.md).
- [Current route coverage](../../status/level-test-coverage.md).
- [MHZ authored-route fixture](../../../src/test/java/com/openggf/tests/TestS3kMhzAuthoredRoute.java).
- [MHZ checkpoint fixtures](../../../src/test/java/com/openggf/tests/TestMhzCheckpointRoutes.java).
- [AIZ terrain owner](../../../src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java).
- [Native special-stage owner](../../../src/main/java/com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageManager.java).

### Entry-owner follow-up

Merged `d5e6eb613` GPU/PCM and native-window walkthroughs reached physical post
contact, two volleys, safe clear, native post retry and a separate fresh launch.
They rejected two presentation details: the title's initial moving controls
collided with its fixed footer, and the first PLAY rows appeared before native
terrain finished loading. A queue-handle-only gate would miss
`deferredFreshLevelRuntimeArt`, and holding ENTRY prevents that producer from
running. The correction advances neutral ordinary rows, removes the duplicate
manual fade step and releases only in `afterTick` after a completed neutral row,
minimum presentation, fade/title readiness and game-owned art readiness.

The additive query-only `RuntimeArtCoordinator.levelEntryArtReady()` defaults to
true. S3K composes actual deferred terrain, physical/prepared module work, direct
work, marked fresh handoff consumption and native title/enemy producer state.
It forces no service and reads no diagnostic/trace rows. Its lazy provider
reference preserves stock initialization; existing rewind owners capture the
queried state. The title footer waits for its moving panel to settle. The new focused run completed 144 cases with no skips: 143 passed, while the
physical checkpoint retry exposed an unconsumed mandatory native title request.
ENTRY now calls existing `CourseControl.finishInitialPresentation()` before its
neutral production row; the native omitted-title lease, teardown and enemy-art
handoff still own that work. Both native retries explicitly check pending-card
consumption and art readiness. The corrected seven prototype cases and actual
runtime policy case pass with no skips. Queue/provider and mandatory S3K cases
passed in the preceding run. The actual compiled candidate export has 20,127
lines, SHA-256 `5a00245f286003f11aa6e5d6b497c5cf5d95c4131786c5414d0c3332a5fea7d0`,
and matches the normalized pin. Independent source review of the new query and
title-consumption boundary found no blocking issues. Final audiovisual observation
and broad validation remain separate gates.

The D5 trace check completed three methods with no skips but three assertions:
AIZ 57 errors, first 20,302 (animation `0`/`5`); complete AIZ 99 errors, first
25,589 (animation `0x13`/`5`); HCZ 4,699 errors, first 9,482 (air `1`/`0`). A
separate clean 5b3 destination-baseline run completed the same three methods with
no skips, reproducing each exact assertion message, total and first-error field.
They are attributable inherited failures for D5, not newly declared route passes.
Its first waiting invocation ended 130 before admission for an unknown reason;
it produced no test result. One reconciled normal replacement supplied the
actual baseline evidence. The later entry-query source still requires its own
trace check. The native missing-ROM screenshots/state log show recovery;
its three-frame desktop video failed X11 GetImage and is rejected as footage.

### Destination reconciliation after entry polish

Coherent entry source `84e1a9a6f` completed the same three trace methods with no
errors or skips and the literal 57/99/4,699 assertions from the clean 5b3 baseline.
This covers the new native queue/provider query before the next destination merge.
Exact destination `2fc65c847` is then merged as `259a9a48f`; its stock power-up rules
retain `restoreLevelMusicAfterInvincibility=true` for every stock game. Both the
new upstream power-up description and this prototype's contracts survive the
single version-comment conflict. The prior title callback, delegated forwarding
and unavailable two-player branch are preserved. Auto-merged API pins are
provisional until exported from the actual compiled merged source. The candidate
stays unpublished 0.7; published baselines are unchanged.

One shared clean 2fc three-method baseline is queued for all prototype teams;
the earlier 5b3 summary remains historical. Final merged candidate trace,
packaging, audiovisual and combined validation still require observed results.
The pre-2fc entry diagnostic confirms title frame 0 has no footer collision and
first PLAY frame 274 contains native foreground and Sonic. Its safe path reaches
post 313, both volleys 380/404, clear 675, real-post retry 840 and separate fresh
launch 1,035. The maintained failure input still produces actual zero-ring death
352 and real-post retry 531. These diagnostics have real GPU frames and varying
stereo PCM, but are not the newly merged source certificate.

The missing-ROM recorder fix waits for the first native display to settle the
owned window geometry before recorder attachment. The bounded pre-2fc probe
fully decodes 363 desktop frames and observes error/menu/error/menu with 337
native input rows. The exact owned PID/title is viewable by default, without a
frameless workaround, at 960×672; native focus/input are observed. Recorders stop
with codes 0/255 and the engine/sink clean up. Exit 255 alone is not audio success;
final video extent and actual device samples must be inspected. This caller uses
unmodified `Engine.display` at a diagnostic 60 Hz and does not certify
`Engine.loop`, physical HID hardware or speakers. Final merged recapture remains
required; the earlier three-frame missing-ROM movie remains rejected.
