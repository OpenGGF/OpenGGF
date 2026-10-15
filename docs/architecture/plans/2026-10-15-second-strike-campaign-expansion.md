# Second Strike — remaining S3K campaign implementation

Implementation authorized 2026-10-15. Binding scope is the
[campaign design](../designs/2026-10-15-second-strike-campaign-scope.md);
root owns the separate delivery plan and integration. Pinned pre-task base:
`76022570e8233823e1e36419666a0f29c768f4b6` on `develop`.

This tree owns new `secondstrike.campaign` recipes, focused tests and non-AIZ
native owners. Public registration boundary is
`public static void secondstrike.campaign.SecondStrikeCampaign.register(ModContext)`.
AIZ/root owns the call, shared economy/combat/registration and sequenced public
API descriptors/signature pins. No arbitrary decoded native placement edits,
copied AI or creator imports of native internals are admitted.

## Wave ownership and acceptance

| Wave | Implementer / owned tree | Bounded outcome |
| --- | --- | --- |
| C1 west | Sol `/root/c1_west`; `ai-second-strike-c1-west` | HCZ/MGZ recipes, specific native owner changes and focused tests |
| C1 east | Sonnet 5.5; `ai-second-strike-c1-east` | CNZ/ICZ/LBZ recipes, specific native owner changes and focused tests |
| C2 | Lead plus next bounded Sol/Sonnet slices | MHZ → FBZ → SOZ → LRZ, character-specific traversal and native completion |
| C3 | Lead plus next bounded slices | HPZ/SSZ/DEZ/eligible Doomsday; fourteen boards and three bonus families |

Each stable wave receives a fresh Opus delegated batch review. No broad suite is
submitted by this team; root owns composed change-selected smoke and guards.
Workers queue focused tests in their own trees, preserve live requests and
report fresh assertions/skips. Source presence and positioned witnesses do not
qualify a complete route.

## Implementation and evidence matrix

All entries initially mean **source mapping / implementation pending**. No cold
route, checkpoint recovery, rewind, capture or completion is certified here yet.
Sonic solo, Sonic + CPU Tails, Tails solo and Knuckles are reference shapes where
native progression permits them; other exposed donor/viewport/team combinations
remain inherited coverage obligations. Production capture presets are 320, 352,
400, 528 and 800 pixels; inherited 512/640 configuration overrides are research
shapes and do not qualify those production presets.

| Zone / scene | Acts and character boundaries | Native identity / work | Actual implementation | Route / checkpoint / rewind / capture evidence |
| --- | --- | --- | --- | --- |
| HCZ | 1/2; separate Knuckles water/arena | Defended junctions, reliable air, wall chase, punishable boss follow-up | Finite native bridge/enemy/boss presets and facades in west tree; creator recipes drafted separately | Focused owner/event/lifecycle checks, final facade check pending; no integrated/cold/air/catch/character-route proof |
| MGZ | 1/2; assisted Sonic/Tails and Knuckles | Cracked support/catch, guarded top landing, native attackable boss pass | Finite native bridge/enemy/boss presets and facades in west tree; creator recipes drafted separately | Focused owner/event/lifecycle checks, final facade check pending; no integrated/cold/air/catch/character-route proof |
| CNZ | 1/2; Knuckles late act/exit | Staggered rhythm, waiting-pocket pressure, unchanged barrels | Finite native presets/facades and creator drafts in east tree; scope gaps remain | Corrected12 native cases passed; affected native/consumer/rewind batch pending; no integrated/cold/character-route proof |
| ICZ | 1/2; snowboard / Tails skip / Knuckles teleporter | Warned shelter, recoverable lower lane, native friction | Finite native presets/facades and creator drafts in east tree; scope gaps remain | Corrected12 native cases passed; affected native/consumer/rewind batch pending; no integrated/cold/character-route proof |
| LBZ | 1/2; Knuckles corridor/presentation | Bounded alarm reinforcement, sentry lane, native launch/boss control | Finite native presets/facades and creator drafts in east tree; scope gaps remain | Corrected12 native cases passed; affected native/consumer/rewind batch pending; no integrated/cold/character-route proof |
| MHZ | 1/2; Knuckles opening, seasons | Committed wind, mushroom/pulley exit, seasonal arrangements/chase | Native Cluckoid PULSED enum/facade/owner drafted in separate lead tree; no MHZ binding | Focus66309 queued/frozen; no route/season/chase/boss/readability proof |
| FBZ | 1/2; all native characters | Taught tube/grabber transfers and recoverable missed grabs | Pending | Pending; no safety support removed |
| SOZ | 1/2; native routes | Non-stalemate golem, readable fading lights, native ghost count/size | Native STOCK/TOMB in isolated lead tree; corrected leftward hop cycle; no creator activation | Prior98cases plus15ghost-withdrawal pass; corrected golem+positioned arena42902 pending; no cold-route proof; pitch black parked |
| LRZ | 1/2 plus boss sublevel; Knuckles transport | Crusher/lava/collapse cadence, switches and HPZ handoff | Stable annotated LRZ2 ECHO_JET source packet335f23e7 retained by root; no creator activation | 16focused pass0skip; mock presentation/controlled graph only; no pixels/zero-ring safe-pocket/cold-route proof |
| HPZ playable | `$1601`; Sonic/Tails story | Concise traversal and punishable personal duel | Native FEINT duel/copy/facade draft in isolated lead HPZ tree; no creator activation | Six controlled FEINT tests have passing focused evidence after clock repair; native FEINT story followup52190 queued; no cold-route/capture certification |
| Super sanctuary | `$1701`; native emerald progression | Reward/progression scene; distinct from HPZ duel | Pending | Pending |
| SSZ | Character-specific scenes; Knuckles two fights/ending | Learned supports/transfers, returning boss extra beat | Pending | Pending |
| DEZ | 1/2/finale; Knuckles level-select research only | Visible gravity choices, bounded drone/firing lanes, final control | Pending | Pending |
| Doomsday | Native eligible Sonic only | Fuel risk/reward and missile lesson; exempt from five-ring scarcity | Pending | Pending |
| Chaos boards | Seven native boards | Individually authored turn commitments, native blue/ring/perfect outcomes | New existing-policy recipe draft for slots 0–6 | Focus pending; no full-board/perfect clear proved |
| Super boards | Seven native boards | Individually authored turn commitments, native emerald persistence | New existing-policy recipe draft for slots 8–14 | Focus pending; no full-board/perfect clear proved |
| Gumball | Native invitation/admission/reward/return | Existing native-strength recovery spring family | Existing first example only | Campaign evidence pending |
| Glowing Spheres | Native family | Authored recovery/risk navigation, native rewards | Pending | Pending |
| Slots | Native family | Demanding navigation; stock RNG/payouts | Pending | Pending |

## Compact checkpoint

- Lead current SHA: `76022570e8233823e1e36419666a0f29c768f4b6`; uncommitted
  plan plus `CampaignBlueSphereBoards` and its focused tests. No pass claimed.
- Sol child: native agent `/root/c1_west`, branch `feature/ai-second-strike-c1-west`.
- Sonnet child task:
  `node:delegated-task:command%3Amcp%3Ae2c49b22-ef64-43d3-b46f-ac1f66a333e7%3Adelegate-task%3Asecond-strike-c1-east-sonnet-20261015-r1`.
- Both child trees created from pinned base under `worktree_build_slot(main)`;
  clean at creation, hooks installed. Main/foreign work untouched.
- Shared boundary: root accepted `SecondStrikeCampaign.register(ModContext)`;
  AIZ owns registration. Semantic contract requests are mapped before public
  API edits. Decoded admission remains factory binding/original ring retention.
- Original ROM verified at `${OPENGGF_MAIN}/Sonic and Knuckles & Sonic 3 (W) [!].gen`:
  CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`; Java 25.0.4.1.
- Root approved finite west facades `CollapsingBridgeFactory`, `HydrocityFactory`,
  `MarbleGardenFactory`, native-owned enum presets, supported boss facade return
  `AbstractObjectInstance`, and later immutable `SandopolisChallengeRecipe`
  service. AIZ owns inventory; root owns compiled pins/SDK/service admission.
- Lead board focused queue command session `65039`: `--lean -Dmse=off
  -Dtest=TestSecondStrikeCampaignBoards -Ds3k.rom.path=<verified-original> test -B -q`.
  Terminal after 1001 seconds admission wait: 3 tests, 1 failure, 1 error, zero
  skips (fresh XML inspected). An authored stage-3 gate overlapped the native
  opening; the test also incorrectly treated every blue rectangle as a native
  convertible loop. Repaired after terminal: moved that gate, corrected three
  nonempty original cells, removed a stage-13 diagonal pair that actual native
  admission proved closed a collectible route. A pure ROM-data probe through
  native admission now accepts all fourteen recipes. Native converter accepted
  232 rectangular loops on these campaign boards; the regression compares the
  stock converter's accepted results and rejects invented rewards. This data
  diagnostic is neither a suite pass nor full-clear/perfect-route proof.
  Request `60790` admitted after1536s, then actual authored conversion spent
  more than eight CPU minutes in native `dfsWalk` (owned Surefire3809014,
  test line87), with no completed XML. Stopped the owned wrapper normally:
  terminal130; wrapper3713626/Maven3807337/Surefire3809014 verified absent.
  This is incomplete validation, not an admission timeout or a board pass.
  Queued diagnostic94523 was withdrawn before execution because its proposed
  thread interruption was not a sufficient stopping rule. Diagnostic37811's
  Surefire timeout30/exitTimeout5 did NOT stop the CPU-bound fork. Normal owned
  cancellation reached terminal130; actual wrapper3848226, Maven3865232 and
  Surefire3866850 are verified absent. Its last marker identifies authored
  stage2 rectangle18,26,4,3, after stock returned. Canonical ROM graph inspection
  shows red gate22,26 joins that loop to the native wall network: component
  10vertices/10edges/onecycle becomes372/554/183. Changed only that gate to a
  native bumper3; other gates and native converter unchanged. Every stock-null
  and stock-non-null comparison remains. Current request82627 (wrapper3918791)
  selects all three board tests, with a real external60s deadline beginning at
  the actual Surefire Java fork, normal wrapper cancellation, bounded verified
  descendant escalation/reaping. Queue wait has no execution deadline. Request
  82627 terminal0 after911s admission; fresh XML3passed/0failure/error/skip,
  1.039s class time. All14 native admission checks and all null/non-null
  rectangular comparisons completed;232 stock-convertible loops retain exact
  ring results. Actual fork finished normally; no live descendants. Added real
  `SecondStrikeCampaign.register(ModContext)` using existing supported
  `registerService("campaign-boards", BlueSphereLayoutPolicy.class, policy)`.
  Root/AIZ must compose it atomically with removal of their old board service;
  the unchanged Gumball policy remains theirs. No full-board/perfect-clear proof.
- Lead independently owns `ai-second-strike-c2-soz` on
  `feature/ai-second-strike-c2-soz`, created under main lease; hooks installed.
  Native golem/light/ghost STOCK/TOMB changes and focused tests are uncommitted.
  Queue command session `65149` selects `TestSozMiniboss,TestSozLightAndGhosts,
  TestSozLightingState,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,
  TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`, verified original ROM,
  `-Dopenggf.headless.gl=surfaceless-egl`; terminal exit0 after 1233 seconds
  admission wait. Fresh XML: 98 tests, zero failures/errors/skips across eight
  classes (both fully qualified `TestSonic3kLevelLoading` names matched).
  Affected native classes contributed 17 golem, 14 light/ghost and 7 lighting
  checks. This is the source before the subsequent withdrawal marker edit,
  not a final whole-tree certification. Build-only test compilation also passed.
- SOZ authored values are pending evidence: golem commits native anticipation
  facing then `+0x100,-0x80,+0x100` short-hop cycle, stock hit/sink flow; light
  actual interval 600 versus stock 900 (reload 599/899), same palette/torch/boss
  hold; native ghosts can attack at nonzero light with native counts/size/fade.
  The controlled-floor golem check observed positive three-hop displacement.
  **Acceptance correction:** calling this pitward was wrong: the native route
  reaches the left-hand pit below `$4200`, while positive motion clamps at
  `$4438`. The positive-biased draft is rejected as pit-progress evidence and
  will be corrected to a negative bias only after request `41886` reaches
  terminal. Actual arena floor/sink, fixed visible facing and cold routes remain
  unproved. After the first terminal, a captured
  `challengeCharge` marker retires extra low-light aggression immediately when
  current recipe lookup becomes absent; native dark-five attacks remain native.
  Root owns actual fault-driven service withdrawal. Affected-only request
  `41886` selects `TestSozLightAndGhosts` (one added withdrawal/stock-respawn case),
  verified ROM and surfaceless EGL; terminal0 after1482s admission wait. Fresh
  XML15 passed, zero failures/errors/skips. No repeated bootstrap/SDK run.
  After terminal, corrected the golem cycle to `-$100,+$80,-$100`, removing the
  mistaken left clamp that would prevent reaching the actual pit. Added a
  positioned native-floor/sink/zero-ring/facing/replay check; its incorrect test
  import was repaired, and build-only20924 passed. Affected native focused
  request42902 selects `TestSozMiniboss,TestSozTombNativeArena`; queued/frozen,
  no sink claim.
- Lead also owns `ai-second-strike-c2-lrz` / `feature/ai-second-strike-c2-lrz`,
  created under main lease and hooks installed. Root accepted the later C2
  `LavaReefFactory.flameThrower(ObjectSpawn,LavaReefHazardRecipe)` shape,
  `STOCK/ECHO_JET`, supported `AbstractObjectInstance` return. Build-only
  `test-compile` passed. Focused queue selects `TestLrzEchoJet,TestLrzFlameThrower`
  with `--lean` and surfaceless EGL, command session `26137`; source frozen.
  `26137` terminal0 after1634s admission wait: fresh XML12 passed, zero
  failures/errors/skips (4echo+8stock). After terminal added exact `$29` guard,
  negative/null cases, native90-slot exhaustion, real-manager warning/child
  recreation and opt-in native unload retirement. Build-only compile passed
  after correcting a test import. Affected request32672 terminal0: fresh XML
  16passed, zero failures/errors/skips (8echo+8stock). All five owned paths are
  staged and frozen as later-C2 packet:
  `${TASK_HANDOFFS}/c2-lrz-native.patch`,
  SHA256 `335f23e7cb85a5b7c990b63e1bed758617f18ac5230ad818768ea181647e3146`,
  21771bytes, base760. Normal commit hook rejected pending candidate pin;
  annotations/index retained, no bypass. Root retained packet for after C1.
  Local plan `2026-10-15-second-strike-c2-lrz.md` is included. No runtime API,
  pixel, safe-pocket, cold-route or actual host-quarantine certification.
  Echo leaves native first burst and full original idle, adds 32 warning updates
  then 24 firing updates, native flame lifetime/velocity/shield/clock. The
  proposed warning pulses the ROM nozzle palette at fixed geometry, with no
  damage-shaped harmless flare; missing art/visibility cancels the extra burst.
  Presentation-call tests are not pixel/readability proof. No LRZ recipe bound yet.
- Sonnet early east packet is native-only Clamer CADENCE, ICZ stalactite WARNED,
  LBZ alarm BOUNDED with a retiring native Flybot escort; no public facade or
  consumer activated. Its live request is background `b40lmmi54`, source frozen.
  ICZ2/LBZ2 have no stalactite/alarm respectively; those acts plus CNZ traversal,
  boss, branch, checkpoint/cold/rewind work remain required open scope.
- Root accepted east zone-specific facade roots `CarnivalNightFactory.clamer`,
  `IceCapFactory.stalactite`, `LaunchBaseFactory.alarm`, each supported
  `AbstractObjectInstance` return and finite native recipe enum. Native Clamer
  must not infer the recipe from original-index parity: after its existing
  request drains, use explicit CADENCE/STAGGERED_CADENCE and parameterless tell
  hold; the authored recipe chooses original records. No native owner move.
- West request `27688` terminal: admission wait 1286 seconds, Maven execution
  30.703 seconds, 8 tests / 0 failures / 2 errors / 0 skips, fresh XML inspected
  by its worker. Two fixture constructors omitted the existing required native
  HCZ boss construction services; six behaviour checks passed. Worker repairs
  fixture, adds actual rider/capacity/rewind checks, corrects boss facade return
  and performs the affected fresh check before a stable native/API packet.
  Request `64588` was superseded before execution at 302 seconds queue wait
  because its synthetic sheet lacked the MGZ type-1 fragment frames (intact3,
  debris4/5). Worker repaired it to all nine native frame slots and requires
  actual fragment children in rewind. Native source remained unchanged. Fresh
  frozen request `57789` keeps `TestWestCampaignNativeRecipes` (10 cases),
  `TestWestCampaignBridgeLifecycle` (3 manager lifecycle cases) and existing
  `TestSonic3kMgz2EndBossEvents`, original ROM and surfaceless EGL.
  Request57789 terminal:1338s admission, Maven25.050s; fresh XML51cases,
  50pass/0failure/1error/0skip:38stock event+3manager lifecycle+9native cases
  passed; direct reconstruction fixture omitted RewindIdentityTable. Native
  source unchanged. Worker repaired context, added all eight exact-ID guards,
  and queued60584 selecting only the one failed method and16 facade input
  cases. Reconstruction and all eight wrong-ID cases passed. Three valid
  badnik cases used overstrict reference identity for the native same-value
  dynamic spawn; repaired to full record equality. Remaining request46541
  selects only eight valid facade rows, queued/frozen. No duplicate38+3+9 run.
  Build-only test compilation passed after the earlier factory return fix.
- Lead also owns `ai-second-strike-c3-hpz` / `feature/ai-second-strike-c3-hpz`,
  created under main lease and hooks installed. Root accepted later C3
  `HiddenPalaceDuelRecipe STOCK/FEINT` and
  `HiddenPalaceFactory.duel(ObjectSpawn,HiddenPalaceDuelRecipe)`, restricted to
  native `$82/$28`, supported actor return. Draft preserves native copy to SST48,
  full native charge tell and 8 HP, alternates a real dash with native guard
  feint seeded31 for32 actual updates before native jump/glide. No sanctuary
  `$1701`/Master Emerald change or Knuckles-player duel. Focused tests draft
  covers copy, actual zero-ring non-invincible upper-side punish, native real
  dash, recreation and native Knuckles deletion; build-only `40957` passed.
  After correcting a fixture's reused copy-slot setup, focused request `81851`
  selects `TestHiddenPalaceDuelRecipe,TestS3kHpzKnucklesFightHeadless`, original
  ROM and surfaceless EGL; terminal11cases/10pass/1failure/0error/0skip.
  All five existing stock-story tests passed. Diagnostic14652 (one failed
  method) reproduced only scalar byte24 `$46` versus `$78`: the direct fixture
  did not rewind its external update clock, feeding70 versus120. Added full
  difference output and restored that test-owned clock, no native source
  change. Corrected one-method request85643 is queued/frozen.
  Final blow/theft/teleport, real pixels, cold traversal and checkpoint evidence
  remain open. Original ROM provenance is exact: `$1601` object table
  `$1FCC7A`, index31 at `$1FCD34` bytes `11f0042c8228`; later consumer binds only
  that original record and all fields. Master Emerald index37/id`$B0` and
  sanctuary `$1701` stay excluded. Current controlled actor tests use synthetic
  coordinates and are not evidence for authentic binding. No public
  descriptor/inventory/pin or creator registration edit.
- Root and AIZ reserve `BlueSphereLayoutPolicy` registration to the actual
  stable campaign provider. After `60790` terminal, add the real provider class
  and hand it over with board class/tests. AIZ removes only its existing board
  registration atomically in composition; Gumball remains its existing policy.
  No duplicate registration, stub, reflection fallback or interim shared call.
- No Opus review spent on plans; no route/capture/completion claim. Next: consume
  exact focused results, repair actual failures, deliver source-ready API packets
  before creator activation, then compose C1 and request stable Opus wave review.

Shared release/coverage-index changes go to root as coherent patches. Lead tree
is retained for integration. Child removal occurs only after stable commits are
accounted for; app histories and unknown/unmerged work are preserved.

- Later C2 lead MHZ tree `ai-second-strike-c2-mhz`, branch
  `feature/ai-second-strike-c2-mhz`, base760, created under normal main inventory
  lease, hooks installed. Root approved `MushroomHillWindRecipe STOCK/PULSED`
  and `MushroomHillFactory.cluckoid(ObjectSpawn,recipe)->AbstractBadnikInstance`,
  exact native SKL90/non-null; authenticated MHZ consumer binding remains
  separate. AIZ confirms no overlap. Native Cluckoid keeps its original sound
  latch, animation, detection, force mechanics, low-three-bit V_int debris gate
  and97-update cooldown (seed96). Captured phase gates only pressure/debris
  20on/12off; no shared wind-helper or creator/common enemy edits. Native
  constructor/default/recreation/slot/unload tests drafted. Build38286 failed
  on nonexistent test method `rewindKey()`, corrected to `GameRng.key()`;
  build-only34135 passed. Focus66309 lean selects only new
  `TestMushroomHillPulsedWind` and existing `TestCluckoidBadnikInstance`, EGL,
  queued/frozen. No route, art readability or host-quarantine proof.

- West stable source-ready packet:23staged paths, base760, staged tree
  `98717af32e167df8253eb623cddab48418841790`, patch SHA256
  `bd891b1162afc15f0bce021c8b62f2143b2723cab49b0b059f1c6af2b77b00e9`
  at child `target/c1-west-native-api.patch`. Normal hook awaits composed pin;
  no bypass. Request46541 terminal0,8valid facade rows passed0skip,
  1606s admission/25.047s Maven. Total67 unique affected valid identities
  across focused repaired requests, not a full suite or route proof. Root has
  independently verified the packet and is composing real C1 inventory/export;
  do not modify this staged source. West's next existing-agent slice is FBZ.

- The real board provider and board class compiled directly against this tree's
  actual `target/classes` engine with Java25 under its own build lease. No
  external publication/ModContext runtime behavior is inferred from javac.
- FBZ existing Sol worker is assigned a new isolated C2 tree after its stable C1
  handoff. Event control will retain native registryAC dispatch and commit the
  fresh finite enum only to the returned accepted Fbz boss before first update;
  failed allocation/other native SK owner cannot sample it. No shared registry
  edit is needed. The one-boss-lifetime committed recipe survives service
  withdrawal natively; actual fault-before-birth integration belongs to root.
