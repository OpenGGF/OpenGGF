# Second Strike — complete AIZ prototype and S3&K campaign roadmap

Status: **design proposal**, requested on 2026-10-15. This document scopes future
work; it does not claim that the encounters, policies or campaign below exist.
Source inspection uses `develop` `1f612ff7271d175ea17eb775e786845d5b2ee12a`.
The [existing implementation/evidence plan](../plans/2026-10-13-second-strike-prototype.md)
retains the history and limits of delivered work.

## Identity and boundaries

**The familiar game has learned your habits.** Second Strike is an authored hard
campaign for experienced players: recognizable routes and native movement, with
enemies, terrain and set pieces that punish automatic play. It can be spiteful,
but the player must be able to understand what happened and devise a reliable
answer. Teach a rule, twist it, combine it, then provide breathing space.

Build a curated campaign rather than a global enemy/physics multiplier or a
collection of isolated challenge rooms. Post Two Ambush remains a separate
encounter concept. This scope does not turn Second Strike into its trial flow.

Keep native acceleration, friction, jump height, collision, shields, flight,
gliding, climbing and attack rebound. Keep normal lives, checkpoints, game over,
save/continue, act results and character-specific progression. Do not defeat
Tails or Knuckles' abilities merely to force Sonic's intended solution. Changes
to enemies and platforms must have a visible cause; no tracking teleport,
invisible wall, unexplained immunity or compulsory blind damage.

Difficulty comes from **placement, commitment, timing and route decisions**.
There is no blanket five-times-speed rule, universal HP increase, random attack
spam or dependence on a particular replay's frame numbers.

## What already exists

The [playable example](../../../examples/second-strike/README.md) currently authors
the forest opening and early fire continuation of apparent AIZ Act 1. Later true
Act 2 is substantially stock. Existing ingredients are:

- Monkey-art spotters and plant-art artillery take two separated hits; guards
  take three. Guards have a 36-update tell, a committed charge of up to 225px at
  ten pixels/update, dash-only immunity and a 72-update brake.
- Artillery gives a 36-update tell, two ten-seed releases twelve updates apart,
  then a 108-update reload. One plant owns at most twenty live seeds.
- Early-fire Caterkillers use twelve native body segments and native movement,
  head vulnerability, body contact and firing roles.
- Five original ring placements cover apparent Act 1: three forest, two early
  fire. Ordinary non-shoe monitors are Robotnik boxes. Recollection works.
- Fire Breath gains a warned delayed follow-up at three HP or fewer. The first
  Chaos board has twelve turn gates; Gumball has three recovery springs.
- Native placement admission, identity/lifetime/rewind, strict creator packaging
  and paired creator-kit exports are present. These are foundations to extend,
  not grounds for advertising an already-complete hard campaign.

The earlier gentler cold-route movies and the later positioned enemy reel do
not qualify the full harder route proposed here.

## Complete AIZ prototype: the delivery boundary

Deliver both **apparent acts**, from native entry through playable HCZ, including
Sonic solo, Sonic with CPU Tails, Tails solo and Knuckles' materially different
route. The forest/fire bank reload is part of apparent Act 1; it is not permission
to count that continuation as the whole of Act 2.

The content target is twelve authored encounter groups, two native boss remixes
with character-appropriate branches, a small set of level changes and one signature
event prototype. Twelve groups includes traversal lessons and recovery space;
it does not mean twelve consecutive multi-enemy arenas. Preserve normal flow
between groups. Ordinary terrain can remain recognizable and fast.

### Resource and reward contract

- Retain **one to five original world-ring placements per ordinary apparent
  act**. AIZ1 keeps its current five; AIZ2 gets its own curated budget. Select
  rings for meaningful route decisions, not five arbitrary equally spaced gifts.
- Keep native scatter and recollection in the main campaign. Unrecoverable
  falling rings are a later cruelty experiment, so they do not obscure whether
  the authored encounters themselves are well tuned.
- Curate ordinary monitors as Robotnik or empty, with truthful visible contents.
  Keep speed shoes as an optional, conspicuous temptation. Do not silently turn
  boss/reward actors into ordinary monitor replacements. Native stage prizes
  may increase the ring counter; scarcity is not a counter cap.
- Preserve giant rings, emerald acquisition, perfect rewards and Super/Hyper
  progression. Do not require rare shields, damage boosts or bonus winnings to
  survive the compulsory route. Prove checkpoint retries with zero rings.
- **Proposed explicit bonus exception:** selected, visibly marked checkpoints
  offer one earned invitation after their authored encounter is cleared,
  independent of combat ring count. The post selects an authored stage family;
  the native entry, gameplay, reward and return owners still execute it. Start
  with one invitation per act, including the existing Gumball variant. Do not
  grant twenty invisible rings or introduce a separate currency UI.
- This invitation policy is new scope, not an existing API. Unmarked posts keep
  native eligibility. Native selection starts at twenty rings: 20–34 selects
  Slots, 35–49 Glowing Spheres, 50–64 Gumball, repeating the native bands. Five
  placements alone do not provide that access. Invitation consumption and its
  interaction with checkpoint death, save/continue and rewind need an explicit
  native-owner state contract before implementation; no duplicate reward or
  repeated entry while a return is pending.

### Level and encounter programme

Coordinates identify inspected original placements, **not proven new routes**.
Implementation must re-resolve the original ROM inventory and test traversal.

| ID | Section / encounter | Proposed level and enemy work | Required player answer |
| --- | --- | --- | --- |
| A1 | Forest — No Free Lunch | Native shoes near `$1790` and monkey at `$1838,$410`; teach a spotter/guard separately, then one alarm link. Keep room to watch a full cycle. | Observe, interrupt the caller or bait the committed defender. Shoes are optional. |
| A2 | Forest — Flame Under the Vines | Rhino `$1C40,$3C0`, plant `$1DC0,$27C`, vines `$1DE0/$1F30,$360`; author the charge and landing lanes together. | Swing past the threat, or bait the dash and punish braking. Never cover take-off, landing and retreat simultaneously. |
| A3 | Forest — Crown or Cover | Ride vine `$2200,$1E0`, plant `$2370,$37C`, upper crown region `$2754–$279C`; preserve different lower/upper protection rings. | Trade a defended lower path for an exposed upper landing. Flight and glide remain useful. |
| A4 | Forest — Rotten Timber | Falling log `$2500,$300`, crumbling ledges `$2470,$3B0` and `$2590,$38C`; one unstable landing, one reliable exit. Limit any ROM-block terrain edits to the authored cluster. | Read the damaged support, keep momentum or take the stable alternative. |
| A5 | Forest — Hollow Tree Exit Interview | Hollow tree near `$2D00,$3CC`; put the ambush after native player release and on a visible exit landing. | React after control returns. The native stunt and fire transformation remain spectacle and a breathing space. |
| A6 | Fire continuation — Ten Ticks of Trust | Native log bridge `$06E6,$4F8`; a visibly brittle variant supports its landing for roughly ten object updates, then uses native-style breakup. | Commit or fall onto a taught catch route; the first lesson is recoverable. |
| A7 | Fire continuation — The Living Floor | Long chains near `$0850,$328`, `$0AE0,$560`, `$0C90,$620`, plant `$09F0,$3FC`; one chain bypass and one head-attack opportunity. | Route around the body, recognize the head window, then handle a committed fan without camping. |
| A8 | True Act 2 — Burnt Fork | Log bridge `$1AE5,$4F8`, vine `$1B66,$2EC`, plant `$1B40,$1FB`, chain `$1B00,$580`; upper/lower alternatives. | Choose the exposed vine or the defended lower crossing; losing momentum must leave a solution. |
| A9 | True Act 2 — Corkscrew Orchard | Vine `$1E00,$360`, spiked logs at `$1E80/$2040/$2130/$21E8,$530`, plant near `$1E58,$6D8`; emphasize native traversal rather than constant attacks. | Use a temporary safe lane and its next landing; dropping to the lower route remains playable. |
| A10 | True Act 2 — False Floor Duet | Flipping bridges `$2C40,$514` and `$2E40,$5F4`; use native period/direction configuration before adding a new hook. | Wait safely, cross on the rhythm, or bypass with a native character ability. |
| A11 | True Act 2 — Last Chance Ladder | Bridges near `$3268,$5D4`, `$32E6,$758`, `$3528,$218`; one reliable anchor, one committed crossing and the final scarce-ring decision. | Execute the learned combination, then reach a short safe approach before the finale. |
| A12 | Sonic/Tails set piece — The Bouncer | Prototype a wheeled Cluckoid entering from the right during the native bombing run; clear wind-up, pulsed wind and recovery gaps. Knuckles instead gets a compact ambush on his native finale approach. | Read bombs and wind together; remain in a taught manoeuvring lane rather than outrunning everything. |

**Platform correction:** ordinary native collapsing platforms already start a
seven-update collapse delay. Replacing that with ten is not automatically harder.
The fire log bridge above normally uses subtype base plus `$30` (56 updates for
the inspected subtype), so a ten-update **brittle log** is a meaningful variant.
Judge the complete support/breakup behaviour, not the nominal timer alone.

Start with object placement and supported native variants. Permit up to three
small, reviewed collision/terrain-edit clusters per act where they add a genuine
route decision; do not rebuild the entire map. Use original ROM blocks and the
native mutation pipeline. Every removed support must have a demonstrated
alternative and a recoverable checkpoint entry.

An evasive landing platform is a **stretch experiment** in A8: visibly prepare
its movement before a committed jump, remain deterministic, leave stable footing,
and never teleport or change the player's jump. Ship the ordinary fork if the
variant cannot meet those requirements.

### Badnik polish and new patterns

Keep the existing 2/3/2 hit counts while authoring the complete route. More health
or more projectiles is not the next design step.

| Family | Next work | Counterplay / limit |
| --- | --- | --- |
| Spotter / Monkey Dude | Interrupt its raised-coconut tell; show a brief acknowledgement from defenders that heard the alarm. Keep some ordinary monkeys as a visual reference. | Killing the caller stops future alerts; already committed attacks finish. One bounded alert, not repeated ambient SFX spam. |
| Guard / Rhinobot | Make the existing 225px flaming charge a consistent encounter language; review its tell, brake pose and vulnerable return at native resolution. | No mid-dash tracking or surprise offscreen activation. Never make a mandatory platform landing coincide with unavoidable contact. |
| Artillery / Bloominator | Add one authored split-fan recipe that deliberately leaves a traversal lane, alongside the existing wide fan. | Commit aim before release, retain reload; do not increase the twenty-seed producer budget. Teach each pattern alone. |
| Caterkiller Jr | Use the twelve-part train as a moving barrier with recognizable head attacks; review bends, slopes and viewport edges. | Keep native body/contact roles. Do not make every segment independently durable or extend the chain again. |
| Wheeled Cluckoid | Separate event prototype with native cross-zone ROM art, a visible wind-up, bounded pressure and pauses. | Not an AIZ-native object-ID swap. Its physics, art and loop participation must be admitted explicitly. |

Before new combat content, audit the current multi-hit separation contract with
Sonic+CPU Tails and short platforms. Source currently requires a 24-update
cooldown **and all native participants outside a 48×64 region** before another
hit; nearby P2 could prolong collision-disabled recovery. This is a source-grounded
risk, not a reproduced runtime failure. Acceptance is one hit per real attack,
bounded understandable recovery, native bounce, final-hit-only animals/score and
no sidekick-induced permanent harmless or untouchable state.

Use encounter authoring to limit overlapping pressure: one wide-volley producer
covering a compulsory crossing at a time; at most two learned enemy roles in an
introductory combination. Multiple distant actors can exist. Do not silently
despawn committed shots to manufacture a safe solution. Slot exhaustion must
retain a valid bounded pattern and correct owner cleanup.

### Events and boss scope

**Fire transformation and tree:** preserve the native capture/release, terrain
mutation, art preparation, palette, camera and apparent/physical-act handoff.
New ambushes begin at semantic release/readiness boundaries, never at an authored
movie frame or during an unavoidable scripted hold.

**Bombing run:** make A12 the signature new event, but investigate it before
spending the full content budget. The ship retains its native scroll and exit.
Its coordinate wraps must move the wheeled enemy, attached wind presentation and
any owned children atomically with the native participants. Merely adding the
enemy would leave it behind on a `$200` wrap. No invisible wall, extra compulsory
loops, player-physics changes or arbitrary camera writes. If the prototype is not
readable in the native viewport or cannot retire cleanly, keep the native bombing
run with authored native hazards and record the rejected chaser. Knuckles never
enters this Sonic/Tails event state machine; do not force him into it.

**Fire Breath miniboss (`0x91`, six native HP):** keep the two-phase identity.
The healthy boss teaches its normal attacks; the damaged boss adds the existing
warned delayed second breath. Review whether sixteen visible warning presentations
are enough, preserving a reliable recovery window. Keep native HP, hit response,
arena, defeat, reward and results. An expert kill can legitimately skip the late
attack; do not hold the boss alive to force the showcase.

**AIZ2 waterfall boss (`0x92`, eight native HP):** add one clearly signalled
alternating/follow-up pressure pattern through its arm/propeller owner, then a
preserved punish window. Keep native conceal/reveal, relocation, scroll, capsule
and onward progression. Sonic/Tails and Knuckles use different arena data;
Knuckles already has additional native fire behaviour. Do not stack a generic
double-projectile multiplier onto both. Validate each branch independently.

Mirrored Fire Breaths, two full Eggman/Eggrobo roots and five-times-speed bosses
are **not requirements of the complete AIZ prototype**. A later duo experiment
needs one encounter owner, two subordinate actors, one camera/defeat/results
authority and one retirement path. Reject invisible crossfire, incompatible
global boss state or broad singleton surgery for a spectacle feature.

### What makes this prototype finished

1. Complete native entry → forest → fire reload → Fire Breath/results → true
   Act 2 → character-appropriate finale/boss → playable HCZ. Record real cold
   routes for the four reference configurations, not only positioned witnesses.
2. Enter every retained checkpoint with zero rings and ordinary lost-momentum
   states; prove a playable retry and repeated restart, with no compulsory hurt,
   rare shield, precise RNG or impossible Knuckles jump.
3. Exercise each new platform, alarm, committed attack, body graph, boss phase,
   bonus invitation and wrap boundary through capture/restore/forward replay,
   death, unload and owner fault. Native timing/physics remain reference-owned;
   authored values belong to the mod.
4. Maintain the [level-testing matrix](../../guide/contributing/level-test-standard.md):
   actual supported widths, donors, characters and team shapes; short entry,
   interaction, lifecycle and rewind axes plus representative complete routes.
   The four reference routes are not an excuse to mark other exposed combinations
   unsupported. Record compatibility gaps without claiming unexecuted coverage.
5. Verify actual 320px readability and wider-view activation/culling, native
   sprites, warning/recovery animation, palette ownership, and one-shot SFX.
   Follow the existing checkpoint/timer/boss/transition owners and four required
   S3K bootstrap/loading checks.
6. Play the authored bonus invitation and giant-ring paths through success and
   failure, reward once and return once. Do not infer these from fixture-only
   direct entries or silently make scarce-ring detours inaccessible.
7. Strict packaged creator validation remains at zero unsupported references;
   the matching engine/SDK and ROM-free authored recipes rebuild and execute.
   Use proportionate focused/domain checks, then one lead-owned change-selected
   batched smoke run when implementation risk requires it, with fresh guards.
   Release-only full suite is not routine development validation.
8. Show a short gameplay reel of the actual finished harder revision. Headless
   engine checks and offscreen captures only; no shared-desktop automation.

## Implementation work packages and dependencies

| Order | Bounded outcome | Main ownership / dependency |
| --- | --- | --- |
| M0 — Combat and resources | Resolve the hit-separation risk; lock ring/monitor/invitation rules; write the actual encounter/character matrix. | Mod combat/economy; native bonus owner only for a validated opt-in invitation seam. |
| M1 — AIZ1 authored route | A1–A7, brittle log, readable fire handoff, polished Fire Breath, fresh cold route. | Level/encounter authoring plus narrow native bridge and release-boundary support. |
| M2 — AIZ2 route and finale | A8–A11, investigate then admit/reject A12, distinct Knuckles finale, both waterfall-boss branches. | Native event/loop contract before the chaser consumer; boss child owner before its mod recipe. |
| M3 — Complete AIZ prototype | Zero-ring checkpoint recovery, both apparent acts and HCZ handoff, bonus access, breadth/rewind/audio/art/creator-kit closure. | One integration lead owns combined checks and release scope; workers own focused files/tests only. |
| M4 — Sonic 3 campaign half | HCZ → MGZ → CNZ → ICZ → LBZ, each complete before advertising the first-half campaign. | Reuse proved challenge families; inspect each native route and owner first. |
| M5 — S&K campaign body | MHZ → FBZ → SOZ → LRZ; wind, footing, light and industrial combinations. | Native season/autoscroll/lighting/lava owners; character-specific coverage. |
| M6 — Finale and whole product | HPZ → SSZ → DEZ → eligible Doomsday; all emerald boards, bonus families, resource/endings/save audit. | Native character progression and ending owners; fuel exception; integrated whole-campaign acceptance. |

These are ordered playable milestones, not calendar estimates. Native route
blockers are prerequisites to their zone's hard remix; this scope does not quietly
absorb unrelated parity repair or certify unimplemented engine routes.

## Full S3&K roadmap

Use native campaign order, not raw zone-table order. In particular, Flying Battery
follows Mushroom Hill. Knuckles ends through his own Sky Sanctuary branch; he
does not silently inherit Sonic's Death Egg/Doomsday story.

| Wave | Zone / identity | Level, enemy and event concepts | Boss direction and delivery gate |
| --- | --- | --- | --- |
| AIZ prototype | **AIZ — The island fights back** | Twelve groups above: committed defenders, brittle supports, route forks, fire transformation and the Bouncer investigation. | Six-HP Fire Breath and eight-HP waterfall remixes; both apparent acts and character-appropriate HCZ exit. |
| Sonic 3 half | **HCZ — Pressure and breath** | Defended underwater junctions; faster risky lane versus slower reliable air; readable wall-chase obstruction clusters and recovery pockets. | Follow-ups punish habitual positions. Keep underwater physics, breath duration and dependable air; independently budget Knuckles' water/arena branch. |
| Sonic 3 half | **MGZ — The ground betrays you** | Cracked supports and visible catches; guarded top-launch landings; one false safe perch after a taught example. | Preserve the native attackable pass. Tops/momentum remain native; Sonic/Tails assisted routes and Knuckles' branch are distinct. |
| Sonic 3 half | **CNZ — Malicious rhythm** | Stagger balloons, bumpers and moving platforms; committed shots pressure waiting pockets; timed optional shortcuts. | Alternating pressure with a readable cadence. Keep native barrels unchanged and Knuckles' late-act/exit route separate. |
| Sonic 3 half | **ICZ — Unstable shelter** | Warned avalanche obstructions; falling ice and defended interior ledges; a fast shelter path with a recoverable lower route. | A volley temporarily closes one approach. Keep ice friction; Sonic snowboard, Tails skip and Knuckles teleporter stay different openings. |
| Sonic 3 half | **LBZ — Escalating security** | One bounded reinforcement after an alarm; clearing a sentry opens a lane; escalating launch-facility combinations. | Alternate strike windows within native boss/launch flow. Retire added threats before scripted control; qualify Knuckles' corridor/presentation. |
| S&K body | **MHZ — The forest changes its mind** | Visible committed wind, guarded mushrooms/pulley exits, season-dependent encounter arrangements and a taught chase remix. | Pressure through a bounded native follow-up. Preserve seasonal art/events, Knuckles' opening and looping chase ownership. |
| S&K body | **FBZ — No spare footing** | Remove selected outdoor safety platforms after teaching tubes/grabbers; defend transfer points; add a deliberate risky shortcut. | Alternating magnetic/attack windows. Prove every character, waiting cycle and missed grab before considering removal of all outdoor platforms. |
| S&K body | **SOZ — The tomb refuses to help** | Golem makes short advance/retreat hops; tomb lights fade faster; ghosts stay aggressive while light controls their size/count. | Golem must retain net progress toward quicksand. Start with readable darkness; pitch-black is an extreme experiment requiring readable silhouettes and recovery. No whole-boss 5× clock. |
| S&K body | **LRZ — Industrial fault cascade** | Taught crusher/lava/collapse sequences, defended switches and elevator exits, distinct rest pockets and upper/lower choices. | Delayed second hazard after a familiar attack. Keep fire immunity/gravity and Knuckles transport; preserve boss sublevel and HPZ handoff. |
| Finale | **HPZ — The personal duel** | One concise traversal test; the story hinge remains short and the sanctuary remains a reward/progression space. | More deliberate punishable feints in Sonic/Tails' Knuckles duel. Playable HPZ `$1601` and Super sanctuary `$1701` are separate scenes; do not add that duel to Knuckles' story. |
| Finale | **SSZ — The collapsing final exam** | Recombine previously taught supports, committed volleys and defended transfers; preserve native character-specific collapsing routes. | One recognizable extra beat for returning bosses; Knuckles' final two-fight sequence has its own recovery budget, defeat and ending. |
| Finale | **DEZ — Hostile orientation** | Two-sided gravity-switch puzzles with visible future danger; bounded drones and paired firing lanes; learned safe spots get a counter. | Warned follow-ups retain native damage delivery and final-arena control. Knuckles is optional level-select research, not a new story path. |
| Finale | **Doomsday — The last fuel calculation** | A few authored asteroid/ring risk–reward lanes and a taught missile manoeuvre; native steering and dash. | **Explicit five-ring exception:** rings are flight fuel. Keep Sonic-only eligibility and prove a first clear with a realistic mistake budget, not only a perfect fuel route. |

### Emeralds, bonus stages and practice

- **All fourteen Chaos/Super boards:** individually authored turn gates, committed
  choices and valid perfect routes. Existing bounded empty-cell red/bumper edits
  are a foundation; a reachability flood fill is not proof a full board/perfect
  route can be completed. Keep blue/ring content and emerald outcomes native.
- **Gumball:** use the existing bounded spring recipe, native strengths, REP,
  rewards and return. The three-spring variant is the current first example.
- **Glowing Spheres and Slots:** separate family-specific authoring and acceptance.
  Explore risk lanes/recovery and more demanding navigation respectively; do not
  change slot RNG/payouts or pretend the Gumball spring policy governs these games.
- **Super/Hyper and endings:** preserve transformations, thresholds, emerald
  persistence, reward semantics and character endings. Audit ordinary ring
  scarcity separately from fuel stages and special/bonus resources.
- **Practice later:** an explicit separate practice profile may offer infinite
  lives, quick native checkpoint retries and stage practice. It must not farm or
  mutate campaign saves/emeralds, and is not required for the complete AIZ slice.

## Architecture and modding constraints

Keep a small set of reusable families: placement/terrain recipes, committed enemy
roles, event pressure, boss follow-ups, resource/admission policy and separate
special/bonus-stage recipes. Do not build a universal difficulty engine, behaviour
tree framework or generic raw-native-field editor for this campaign.

| Concern | Mod/content owns | Native engine owns |
| --- | --- | --- |
| Encounters and economy | Original placement selection, group/pattern recipes, ring choices, monitor contents, visible identity. | Atomic validation/admission, native level identity, original record provenance, placement lifetimes and world reset. |
| Platform / terrain variants | Bounded brittle-support values and reviewed ROM-block edits. | Standing participants, solid collision, breakup children, mutation surface, recreation/rewind and stock defaults. |
| Badniks | Alert/pattern/health choices and authored combinations. | Native contact/rebound/destruction/score/animals, assets and supported factories/services. |
| Set pieces | Opt-in pressure recipe and participating actor graph. | Semantic phase entry/release, camera/scroll/wrap, control, native completion and atomic participant movement. |
| Bosses | Bounded follow-up recipe at a meaningful attack boundary. | Arena, root/children, art readiness, damage/invulnerability, defeat/reward and progression. |
| Bonus invitation | Which authored challenge earns an invitation and its intended family. | Eligibility/consumption lifetime, stage entry/reward/return and checkpoint/save/rewind integration. |

Use `ModContext` transactions/fault boundaries and immutable validated recipes.
No engine checks for `second-strike` identity, raw placement-ID allocation,
singleton access from objects, copied native AI, live tile writes or mutable
gameplay statics. Zone-native code can expose the smallest semantic policy;
shared code consumes the contract, not a new game/zone/mod-name carve-out.

Extend the current native placement path without wrapping `Sonic3kLevel` in a
generic copied level. Use `LevelMutationSurface` / `ZoneLayoutMutationPipeline`
for actual geometry. Art comes from the ROM pipeline; Cluckoid's MHZ/SKL assets
require explicit cross-zone admission. Disabled/absent policies retain shipped
native behaviour.

New actor/child state needs exact identity, capture/recreation and lifetime
ownership. Decide committed-shot survival after ordinary combat death separately
from unconditional retirement on unload/quarantine. Native warning, audio,
palette and attached-render owners must stay coherent; a visual transform cannot
change collision by accident. New public API seams require actual compiled
signature regeneration, candidate-policy handling and zero-warning creator-kit
consumer checks, not an assumed auto-merged pin.

## Experiments that do not expand the committed AIZ scope

| Idea | Investigation / promotion rule |
| --- | --- |
| Landing platform evades Sonic | One visible, deterministic optional-route example with stable fallback; reject tracking teleport or character dead ends. |
| Unrecoverable falling rings | Separate later cruelty variant; prove the main scarce-ring encounters first. Preserve a clear visual distinction and reward/access rules. |
| Tails damage hurts the player | Later shared-fate option only; reject unavoidable CPU-caused deaths or broken solo/duplicate-team semantics. |
| Mirrored minibosses / extra Eggrobo | One encounter authority with two subordinate actors; reject unseen crossfire, duplicate results, unresolved globals or broad singleton redesign. |
| Pitch-black SOZ | Later extreme setting with readable mandatory cues and recoverable controls; not the default first darkness prototype. |
| Speed-based jump height / global 5× speed | Outside the campaign contract. Preserve native player physics; author particular enemy waits/recovery instead. |

## Grounding and next action

Read-only exploration inspected the current example, native AIZ event/boss and
platform owners, native bonus coordinator, zone inventories and character ending
coverage. The two original AIZ object-placement research tables matched the root
ROM bytes during that exploration (`$1E44DA`, 924 bytes; `$1E4876`, 4,512 bytes).
Disassembly remains a development reference, never a runtime fallback.

Key owners: `secondstrike/routes/SecondStrikeRoutes`, `SecondStrikeBadnik`,
`GuardBadnik`, `ArtilleryBadnik`, `SecondStrikeBosses`, native `Sonic3kAIZEvents`,
`AizCollapsingLogBridgeObjectInstance`, `AizFlippingBridgeObjectInstance`,
`AizMinibossInstance`, `AizEndBossInstance`, `AizEndBossPropellerChild` and
`Sonic3kBonusStageCoordinator`. See the [AIZ inventory](../research/s3k-zones/aiz-analysis.md),
[rule placement](../per-game-rule-placement.md), [modding handbook](../../modding/index.md)
and [coverage backlog](../../status/level-test-coverage.md).

The recommended next implementation slice is **M0 plus A1–A4**, followed by a
playable AIZ1 milestone. Resolve the combat contract and prove meaningful level
changes before multiplying content, then tackle the event/loop seam and AIZ2.
This design task ran no engine builds, tests, captures or performance measurements.
