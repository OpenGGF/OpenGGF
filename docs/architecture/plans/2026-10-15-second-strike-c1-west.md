# Second Strike C1 west: Hydrocity and Marble Garden

## Owned packet and state

Private tree `ai-second-strike-c1-west`, branch
`feature/ai-second-strike-c1-west`, base/HEAD
`76022570e8233823e1e36419666a0f29c768f4b6`.
The intact native/API packet includes eleven native owners/events, five recipe
enums, three facades and three focused test classes. Shared registration, API
inventory/pins, changelog and coverage indexes belong to the integration lead.
No main-workspace edit, push, merge or desktop automation was performed.

Creator files `HydrocityCampaign.java` and `MarbleGardenCampaign.java` currently
contain only inactive ring-registration skeletons and are excluded from this
native packet. Complete authored recipe/monitor/ring consumers and a real-ROM
registration test are preserved as `target/HydrocityCampaign.java.draft`,
`target/MarbleGardenCampaign.java.draft` and
`target/TestWestCampaignRegistration.java.draft`. Activation waits for composed
API admission/export. Parent owns `SecondStrikeCampaign.register(ModContext)`.
The ring skeletons alone do not satisfy campaign identity.

## Native contracts

All existing constructors retain STOCK. New mutable preset flags participate
in native generic rewind capture/reconstruction. No HP, damage, player physics,
water/air, checkpoint, camera-lock or character-ending rules change.

- `CollapsingBridgeRecipe`: STOCK/BRITTLE. STANDARD supports cap standing delay
  at min(native,16), with a one-pixel intact-art tremor during countdown;
  collision center, fragment wave and rider release remain native. TRIGGER and
  MGZ_STOMP are unchanged.
- `HydrocityEnemyRecipe`: STOCK/ALERT. TurboSpiker detection $60 to $80;
  facing commitment, launch prep, shell motion and children remain native.
- `MarbleGardenEnemyRecipe`: STOCK/LANDING_GUARD. Mantis and Spiker detect
  $40 to $60. Mantis jump -$600/gravity $38 and Spiker seven-update rise,
  side-child and projectile graphs remain native.
- `HydrocityBossRecipe`: STOCK/COUNTER_CADENCE. HCZ1 selects one additional
  finite strafe per damaged pattern (HP<=3), never replenishing during a cycle.
  HCZ2 alternates damaged blade intervals $3F/$5F (Sonic/Tails) or $4F/$5F
  (Knuckles), retaining original motion/phase/character branches.
- `MarbleGardenBossRecipe`: STOCK/COUNTER_CADENCE. MGZ1 damaged ceiling pause
  $3F to $1F, retaining both full $7F rumble warnings. MGZ2 Sonic/Tails
  alternate damaged zoom waits $5F/$9F and retain the complete raw $FF movement
  interval. Knuckles changes only damaged wait between original drops $3F to
  $1F. Event-spawned roots sample the optional immutable enum once; absence is
  STOCK. Creator admission/export of that service is owned by root.

Enums are `com.openggf.game.sonic3k.difficulty.*`. Supported facades are under
`com.openggf.mods.objects`; boss returns use supported AbstractObjectInstance.
Exact native-ID guards precede construction in all eight methods:

| Facade method | Native ID | Recipe | Return |
|---|---|---|---|
| CollapsingBridgeFactory.create | 0F | CollapsingBridgeRecipe | AbstractObjectInstance |
| HydrocityFactory.turboSpiker | 96 | HydrocityEnemyRecipe | AbstractBadnikInstance |
| HydrocityFactory.miniboss | 99 | HydrocityBossRecipe | AbstractObjectInstance |
| HydrocityFactory.endBoss | 9A | HydrocityBossRecipe | AbstractObjectInstance |
| MarbleGardenFactory.mantis | 9D | MarbleGardenEnemyRecipe | AbstractBadnikInstance |
| MarbleGardenFactory.spiker | 9C | MarbleGardenEnemyRecipe | AbstractBadnikInstance |
| MarbleGardenFactory.miniboss | 9F | MarbleGardenBossRecipe | AbstractObjectInstance |
| MarbleGardenFactory.knucklesBoss | A2 | MarbleGardenBossRecipe | AbstractObjectInstance |

Every signature takes `(ObjectSpawn, Recipe)` and preserves the complete native
spawn value. A badnik may publish its own equal-value dynamic spawn record;
Java reference identity is not a placement contract.

## Original records and authored draft

Original existing ROM path:
`${REPO_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen`.
`REPO_ROOT` denotes the existing main-checkout root; the test invocation used its
resolved absolute original path. Verified CRC32 `63522553`, SHA-1
`cfbf98c36c776677290a872547ac47c53d2761d6`. No ROM copies or links.

| Bank | Sprite pointer / ring pointer | Objects / rings | Draft retained ring placement IDs |
|---|---|---|---|
| HCZ1 | 1E6090 / 1E758A | 383 / 440 | 1,109,145,217,433 |
| HCZ2 | 1E6990 / 1E7C70 | 510 / 259 | 1,64,127,232,253 |
| MGZ1 | 1E8088 / 1E8B38 | 455 / 506 | 1,85,169,337,505 |
| MGZ2 | 1E932C / 1E9B36 | 342 / 352 | 1,88,146,233,349 |

Draft consumers bind a small exact-original subset: two HCZ1 dry supports,
HCZ transfer TurboSpikers, five ordinary supports in each MGZ bank, eight
MGZ Spikers and six Act2 Mantis guards, plus original bosses. Only ordinary
ID1 non-shoe monitors bind the shared Robotnik monitor. Hidden ID80 boss
rewards, bubble generators, checkpoints, giant rings, native tops, trigger
bridges and subtype2x stomp supports remain untouched. Inventory markers select
native banks, never player positions. Unknown inventories pass through.
This is ROM authoring evidence; no actual route was executed.

Rejected: HCZ2 chase-speed edit. HCZ2WallObjectInstance is only the invisible
solid; Sonic3kHCZEvents.act2BgWallMove jointly owns visible wall offset, native
$E000/$14000 steps, $680/$A88 player gates, stop/release and scrolling. Altering
only the actor would desynchronize visible and solid walls. There is no recovery
proof for a faster chase. Native transfer defense is drafted instead; a changed
chase sequence remains a scope gap if final acceptance requires one.

## Focused verification

All commands ran in this private tree with Java25 and its own Maven queue.
Measurement hazards and fresh XML/assertions/skips were inspected. No broad,
bootstrap or duplicate API runs were submitted by this worker. Two build-only
`python3 tools/testing/maven_queue.py -Dmse=off test-compile -B` commands passed
(Maven1m17s and1m18s); they are compilation evidence only.

Completed selections (prefix `python3 tools/testing/maven_queue.py --lean -Dmse=off`):

1. `-Dtest=TestWestCampaignNativeRecipes test -B` (session27688): wait1286s,
   Maven30.703s;8 cases,6 pass/2 fixture errors/0 assertion failures/0 skips.
   HczEndBoss construction lacked ObjectConstructionContext; corrected fixture.
2. `-Dtest=TestWestCampaignNativeRecipes,TestWestCampaignBridgeLifecycle,TestSonic3kMgz2EndBossEvents -Dopenggf.headless.gl=surfaceless-egl '-Ds3k.rom.path=${REPO_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen' test -B`
   (57789): wait1338s, Maven25.050s; stock events38 pass, bridge3 pass,
   native recipes9 pass/1 fixture error;0 assertion failures/0 skips. Direct
   reconstruction lacked an identity table; corrected capture/restore fixture.
3. `'-Dtest=TestWestCampaignNativeRecipes#reconstructionRestoresPresetsAndTheCommittedTimers,TestCampaignNativeFactories' -Dopenggf.headless.gl=surfaceless-egl test -B`
   (60584): wait358s, Maven1m26s; reconstruction1 pass; facade16 cases13 pass/
   3 assertion failures/0 errors/0 skips. All8 invalid-ID guards passed. Three
   valid badnik cases used assertSame on an equal-value native dynamic record;
   corrected to full ObjectSpawn equality after inspecting its owning constructor.
4. `'-Dtest=TestCampaignNativeFactories#correctKindRetainsExactOriginalSpawn' -Dopenggf.headless.gl=surfaceless-egl test -B`
   (46541): wait1606s, Maven25.047s, terminal2026-10-15T19:00:50+01:00;
   fresh XML8 pass/0 failures/0 errors/0 skips. No live test request remains.

Two superseded requests were cancelled BEFORE execution for concrete fixture/
source expansion:1280 (121s) and64588 (302s). They provide no test evidence.
The latter synthetic mapping sheet had only4 frames while fragments use4/5;
corrected to9 frame slots and asserted actual children before claiming replay.
No gameplay fix was inferred from fixture errors. Completed unaffected passes
were not repeated. Across scoped runs, the current affected contracts have
67 unique passing cases:38 stock events,10 native presets,3 bridge lifecycle,
16 facade cases. This is bounded evidence across exact selections, not a full
current-tree suite or a green delivery claim.

Native tests assert finite budgets, selected waits and preserved native movement/
warning durations at state transitions. They do not show a player landing hits
in a complete fight. Bridge tests use actual ObjectManager/RewindRegistry with
synthetic mapping geometry: two riders release, full90-slot fragment denial
cannot strand the parent, and countdown5 restore/forward replay recreates actual
fragments. Direct recreation restores seven owner flags. Mid-wave rider state,
full boss child graph replay and MGZ1 flag recreation remain uncovered.

## Route and capture limits / initial handoff

All four acts still lack complete campaign routes for Sonic solo, Sonic/CPU
Tails, Tails and Knuckles; real air recovery, zero-ring checkpoint retries,
visible MGZ catches/guarded top landings, load/respawn isolation and integrated
boss punish/ending proof are unexecuted. No production viewport coverage is
claimed (production presets320/352/400/528/800;512/640 are research overrides).
No cold SDK registration/package or gameplay capture was run here.

Handoff: intact23-file native/API packet staged. Normal commit hook rejected
the detectable @ModApi surface delta because root-owned candidate signatures
are not yet regenerated. No bypass or annotation stripping was attempted. The
first hook also rejected a machine-local documentation path; it is now expressed
through REPO_ROOT, preserving the actual absolute-path execution claim. Supply
the stable staged binary patch/base/SHA256 to root for genuine API inventory/
pins/export and common-doc composition, then creator activation. That was the original native packet handoff; the separately assigned C2 FBZ
work now lives in its own tree and does not alter this packet.


## R1 repair and creator followup (2026-10-15)

Original staged native tree `98717af32e167df8253eb623cddab48418841790` and its
23-file packet remain immutable; the real index is still that tree. Root compiled
the original accepted API with AIZ/provider (36397) before these creator classes
were authored. No shared provider registration, API pins or inventory was edited.
The source-ready nine-Java overlay is based on that tree and produces
`110659ba1345e88d66e8724b189a0a954bc06f27`; its SHA-256 is
`eef13c650a39cd992460576237ec539b199e78dd1d4727952f0245e6ad2e006d`.
The exact sorted-path/NUL/content/NUL Java overlay fingerprint is
`d4485602a96ff2f83e4df2aa53734d4ff31aa270b871b4b69e27cadc7f238313`.

Opus R1 correctly found that LANDING_GUARD widened Spiker's open/launcher gates,
violating the approved detection-only contract. The repair leaves96 only in
`updateDetect`; `updateOpen` and both side launchers use native64. Negatives at
64/80/95 cover both body/launcher sides and presets. Actual native rise8 moves,
launcher animation and projectile/gravity are checked. No projectile AI was
copied into creator code.

Root selected service-only MGZ2 for BOTH Sonic/Tails and Knuckles. Removed the
public `MarbleGardenFactory.knucklesBoss` and its creator binding/import, retaining
the internal Knuckles constructor/enum/event path. The original A2 record
(3E78,0000) starts ROM Check_CameraInRange word_6C688; Java registry construction
is inherited ungated while the event separately creates its boss at3D20,0068.
No registry parity repair or A2 route qualification is claimed. The record stays
untouched; actual one-encounter route proof remains required.

At MGZ event recipe intake, catch ONLY neutral `ObjectCallbackAbortException`
as STOCK. Original bossSpawned/generic flag, character/music/currentBossId and
allocation order remain intact, with no retry/reset/duplicate. Controlled native
first-abort and absent-provider witnesses cover both character branches, later
updates and full-SST outcome. Real ModContext/OwnedModServices fault composition
is root-owned and remains separate.

`HydrocityCampaign.register(ModContext)` and
`MarbleGardenCampaign.register(ModContext)` author exact original ROM bank
bindings and curated native ring IDs, without raw record edits. Unknown banks
pass through. Static tests exercise actual creator source and decoded original
four-bank inventories, exact recipe/monitor/ring choices and preservation of
protected native records (including A2), plus registration idempotency. Air,
ordinary physics, checkpoint and trigger/STOMP actors remain native. Authored
recovery intent is not empirical reliable-air, visible-catch or guarded-landing
route evidence.

Followup checks use the same lean Maven prefix and EGL/original-ROM options as
above. `35098` completed4 consumer cases,0 failures/errors/skips before R1
(wait2356s,Maven27.624s,19:58:45+01:00); its A2 binding is superseded.
Build-only79423 failed two Collection.getFirst fixture compilation sites;
37920 then failed a generic assertInstanceOf fixture ternary (NO tests ran).
Corrected fixtures compiled in51843 (23.565s,20:16:16+01:00).

`89642` ran exact selector
`TestWestSpikerDetectionContract,TestWestMgzRecipeBirth,TestWestCampaignRegistration,TestSpikerBadnikInstance#nearbyPlayerOpensBodyAndLeftLauncherFiresProjectile`
with `--lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl` and the actual absolute
original S3K ROM path. Fresh XML:15 pass,0 failures/errors/skips (4+6+4+1),
wait887s,Maven27.453s,terminal2026-10-15T20:33:14+01:00. The overlay fingerprint
above remained unchanged. No live C1 request remains. Earlier67 cases describe
historical bounded selections, including the now-removed two A2 facade cases;
they are not a full pass on this repaired tree.

Full character/team/viewport routes, real air/zero-ring recoveries, visible
supports/catches, MGZ2 cue/punish windows, one-encounter Knuckles arena and cold
strict SDK/packed-host admission remain unverified. No capture was run. The
shared coverage/changelog and final candidate pin are composed by root.


## R2 first-abort reversal and initial-warning witness

Root reversed the earlier R1 first-abort fallback after assessing the composed
host: quarantine does not revoke assembled/cached object/controller callbacks.
A NEW neutral ObjectCallbackAbortException must propagate to host frame discard,
teardown and SessionManager.clear. The historical R1 source and89642 pass remain
immutable evidence of the rejected approach, not the current contract. Removed
the MGZ catch; absent/already-withdrawn or successful null/STOCK still preserves
native STOCK. Native tests now assert propagation before character allocation,
including full SST, without ticking/retrying/rolling back a partially failed
encounter. Root owns the actual composed-host teardown witness.

R2's Spiker warning-cycle observation is accepted with detection-only semantics.
No open/launcher hysteresis was added. Actual body/child execution at body-range80
(near launcher-range60) produces three18-update warning cycles over54 updates:
24 RISE,3 OPEN,24 DESCEND,3 DETECT; three native children, zero projectiles or
launcher ATTACK updates. Stock stays DETECT for all54. Thus96 is initial warning
range only; this does not claim wider projectile pressure or a landed attack.

Build-only73345 compiled the new native physical capture fixture in27.017s
(terminal20:41:04+01:00). Then74882 ran the normal queued selection
`TestWestMgz2ArenaEvidence,TestWestMgzRecipeBirth,TestWestSpikerDetectionContract#initialDetectionOnlyPumpsThreeNativeWarningCyclesOutsideTheProjectileGate`
with EGL and the actual absolute original ROM path. Fresh XML:9 cases,8 pass,
1 assertion failure,0 errors/skips; wait465s,Maven1m19s,
terminal2026-10-15T20:56:24+01:00. Six MGZ propagation/absence witnesses and the
single Spiker multi-cycle witness pass. Frozen four-Java overlay SHA-256:
`24976e256c3c17b0d2b092ab8f37cb2b3627cd8dd69aa797d6b9e2e7fb463963`.
Passing native repair is handed off separately from the failing arena witness.

## M4 positioned physical arena evidence and stopping boundary

New `TestWestMgz2ArenaEvidence` uses production GameplayCaptureSession / GameLoop,
original native assets,320x224 surfaceless EGL. Existing boot module decorator
supplies only the immutable native COUNTER_CADENCE enum; this is a fixture, not
creator/SDK/packed-host qualification. Declared seeds are Sonic+CPU Tails
(3BD0,0700) and Knuckles (3BD0,0100). Only ordinary P1 R until native commitment,
then neutral input. No HP, ring, physics, invincibility, camera or actor-state
writes after positioned boot; no registry change. Root enumeration excludes
expired actors and records class, slot, layout index, recipe, routine and HP.

Sonic+CPU Tails passed: native camera clamp/arena event commits frame98 and
allocates one actual MgzEndBossInstance,slot4,layout-1,COUNTER_CADENCE=true,HP8.
Exactly one live root is observed through frame398, with player alive/0 rings
and camera(3C80,06A0), ending in initial swing routine48. State and real PNGs were
inspected; frame120 visibly contains Sonic/Tails/native arena assets. This is
one-authority evidence over that bounded interval, not a complete encounter.

Knuckles FAILED entry at frame133: no commitment, event routine2,zero live boss
roots, death at(3B98,017C),camera(3AF8,00A0). Native movement relocates the declared
seed left to3AF8 byframe30, then R follows the descending slope into the death
plane; one native ring is collected on the last frame. CSV precedes PNG review;
frame120 visibly shows the slope/Knuckles, while initial seed/render does not
qualify a supported grounded start. This is an unqualified positioned entry/input
blocker, NOT a confirmed engine parity defect and NOT evidence that Knuckles
has one authority or lacks the inherited A2 gate problem. No duplicate was
witnessed; A2 was not admitted in this failed approach. Exact failed seed/input
and negative assertions are preserved; no silent registry/input change made it
pass. The failed witness is a separate source packet, not a green integration
claim. Captures use external task directory from OPENGGF_M4_CAPTURE_ROOT:
`${TASK_CAPTURE_ROOT}/second-strike-m4-mgz2/{sonic,knuckles}`, state.csv,
authority.csv, outcome.txt and sparse frame PNGs. No shared desktop was used.

Stop at this blocker as assigned. Both branches still lack full one-encounter
qualification; Sonic's damaged zoom cue/5F wait, native256-update air sweep,
ordinary-input safe answer/punish and Knuckles real arena authority remain
unmeasured. Do not infer them from initial swing or source/unit timing checks.
No live worker request remains. Preserve original index98717/native packet,
110659/eef13c source-ready history and8d496 checkpoint; R2 repair and M4 source
are versioned incremental packets, never overwrites of prior evidence.
