# Sonic 2 Native Competition and Human-P2 Design

**Date:** 2026-08-09

**Status:** Design-ready; runtime capability unavailable

## Outcome

Sonic 2's human second player is not a variant of the current CPU-sidekick
path. In World REV01 it exists only as part of the native competition product:
title selection, a four-entry competition level select, forced Sonic/Tails
participants, independent player state, two cameras and display regions,
two-camera object lifetime, competition-specific object rules, and results
progression.

OpenGGF cannot safely advertise that product by changing the monitor gate or
by setting the existing sidekick's `cpuControlled` flag to false. Both changes
would create an invented one-camera cooperative mode and would leave input,
death, scoring, object streaming, rendering, results, and rewind behavior
internally inconsistent. This design therefore narrows the current work to an
executable architecture campaign. Production behavior remains unavailable
until every activation gate below is satisfied in one coherent route.

## Requirements

1. Implement the shipped World REV01 competition mode, not generic co-op.
2. Model P1 and P2 as explicit playable slots and model CPU Tails as a distinct
   control role; never infer human P2 from `!isCpuControlled()` or from list
   position.
3. Preserve ordinary Sonic+CPU-Tails behavior through every prerequisite
   migration.
4. Keep S2-specific rules and data in S2 owners. Shared runtime code may learn
   semantic participant/view/state contracts, but may not branch on game or
   zone names.
5. Source gameplay and presentation data from the user-supplied ROM. The
   disassembly supplies labels and behavior evidence only.
6. Capture and restore mode, player-slot, camera/view, results, object-loading,
   and object-reference state through rewind.
7. Do not advertise the title option or launch profile before a complete
   title-to-results production route and native trace evidence exist.

## Source authority

The authority is Sonic 2 World REV01, SHA-1
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, compiled with
`gameRevision = 1` (`docs/s2disasm/s2.asm:20`) and `fixBugs = 0`
(`docs/s2disasm/s2.asm:27`). The implementation must retain the shipped,
unfixed paths at every nearby conditional.

The minimum authoritative catalog is:

| Contract | REV01 source |
|---|---|
| Title option establishes `Two_player_mode`/copy and enters the 2P level select | `s2.asm:4535-4571` |
| 2P level order is EHZ1, MCZ1, CNZ1, then Special Stage | `s2.asm:11848-11887`, `LevelSelect2P_LevelOrder` |
| Competition forces Sonic in `MainCharacter` and Tails in `Sidekick` | `s2.asm:5160-5196`, `Level_SetPlayerMode` / `InitPlayers` |
| The level initializes both camera blocks from the same start position | `s2.asm:14777-14813`, `LevelSizeLoad` |
| `DeformBgLayer` follows each human through separate camera/boundary/scroll blocks | `s2.asm:15124-15180` |
| H-int swaps the lower display to the P2 plane, V-scroll, and sprite table | `s2.asm:616-825`, `1184-1222`, `H_Int` |
| 2P object placement tracks loaded blocks and respawn cursors for both cameras | `s2.asm:32920-33535`, `ObjectsManager_2P_*` |
| CNZ selects its 2P object layout while the other competition zones share their native layout | `s2.asm:32943-32952`, `33780-33798` |
| Ring draw/build and many object art paths use the compressed 2P presentation | `s2.asm:31456-31485`, `31882-32195`, `Adjust2PArtPointer*` / `BuildRings` |
| Rings, lives, time, score, checkpoints, collected rings, and monitors are per player | `s2.constants.asm:1754-1782`; representative behavior at `s2.asm:25069-25205`, `25756-25888`, `41151-41217`, `44768-44770` |
| Human P2 may break a monitor; CPU Tails in one-player mode may not | `s2.asm:85333-85343`, `Touch_Monitor` |
| Act/zone/game/special-stage results own competition progression | `s2.asm:10708-11210`, `TwoPlayerResults*` |

The ROM also clears `Two_player_mode` for water levels during level
initialization (`s2.asm:4850-4858`). The shipped competition level list avoids
those levels. This is evidence that the supported product is the native
competition route, not an arbitrary two-human launch into every ordinary act.

## Current engine boundary

| Area | Current owner | Why it cannot represent native competition |
|---|---|---|
| Title route | `TitleScreenManager`, `StartupRouteResolver`, `GameLoop` | S2 always emits `ONE_PLAYER`; the generic `TWO_PLAYER` route currently falls through to an ordinary zone 0/act 0 level load. It is an action token, not a mode owner. |
| Session/team | `WorldSession`, `SaveSessionContext.SelectedTeam`, `GameplayTeamBootstrap` | A session has no play style or participation roles. Every configured secondary sprite is constructed as a CPU sidekick with `SidekickCpuController`. |
| Input/update | `SpriteManager` | P1 input is applied to every non-CPU playable. P2 input feeds only the first CPU sidekick controller for manual override/respawn. Multiple non-CPU sprites make `getMainPlayable()` deliberately ambiguous. |
| Object participation | `ObjectPlayerQuery`, `ObjectPlayerParticipationPolicy`, `ObjectTouchResponseController` | `nativeP2OrNull()` means “first sidekick,” and the sidekick touch path intentionally omits human ring-scatter/death behavior. |
| Camera/render | `Camera`, `LevelManager`, `LevelRenderer`, `GraphicsManager` | One focused sprite, one camera/boundary block, one viewport/projection pass, and one foreground/object visibility window exist. |
| Object/ring lifetime | `ObjectManager`, `RingManager` | Placement and post-camera updates consume one camera X. Rewind registers one main plus `sidekick(i)` identities. |
| Player state/HUD | `GameStateManager`, `LevelGamestate`, `AbstractPlayableSprite`, `HudRenderManager` | Score/lives/continues, rings, and time are single shared values; both playables delegate ring access to the same `LevelState`; one HUD is rendered. |
| S2 mode rules | `Sonic2LevelEventManager`, S2 objects | `isTwoPlayerMode()` is an explicit false boundary. The monitor correctly blocks the current CPU sidekick, but has no truthful human-P2 role to query. |
| Results/progression | `GameLoop`, S2 special-stage/results owners | No native 2P level-select or results state machine owns act/zone/special-stage progression. |

Controller-2 bindings are useful infrastructure, but their existence is not a
human-P2 slot. Likewise, `ObjectPlayerParticipationPolicy.NATIVE_P1_P2` is an
object targeting convention; it does not establish control authority or
competition lifecycle.

## Product contract

The first supported capability is local, two-controller, native S2
competition:

- P1 is Sonic and reads logical controller 1.
- P2 is Tails and reads logical controller 2 directly. No
  `SidekickCpuController`, catch-up, despawn, or leader-follow state is attached.
- The title's 2 PLAYER selection enters the native competition level select.
- The selectable entries and progression are EHZ, MCZ, CNZ, and the native 2P
  special-stage series, in REV01 order.
- Normal acts run two vertically stacked logical views with per-player camera,
  HUD, sprite culling, and object/ring visibility.
- Rings, time, score, lives, checkpoints, collected-ring count, monitor count,
  act results, zone results, and overall results remain independent per slot.
- Competition-only monitor/random-item/teleport behavior is selected by the
  mode owner and participant slot, never by an object-local approximation.
- Either controller may advance the 2P level-select and results screens where
  the ROM ORs their Start presses.

Ordinary one-player Sonic+CPU-Tails remains a separate product state. A free
act selector with two humans, shared-screen co-op, network play, more than two
players, and arbitrary character pairing are non-goals.

## Architecture

### 1. Session and route ownership

At final activation, add a shared semantic `LocalCompetitionProvider` contract
to `GameModule` and make `GameLoop` delegate `TitleActionRoute.TWO_PLAYER` to
it. Modules without a provider reject the route cleanly; the route must never
fall through to an ordinary level load. Before an S2 implementation is ready,
the existing route can be made fail-closed without adding a dormant provider
interface.

`Sonic2CompetitionSession`, owned by `Sonic2GameModule` for the active
`WorldSession`, is the sole owner of the S2 state machine:

```text
UNAVAILABLE -> LEVEL_SELECT
  LEVEL_SELECT --normal zone--> LEVEL(act 1) -> ACT_RESULTS -> LEVEL(act 2)
                                              -> ACT_RESULTS -> ZONE_RESULTS
       ZONE_RESULTS --zone tie--> TIEBREAKER_SPECIAL -> SPECIAL_RESULTS
                                                      -> ZONE_RESULTS
       ZONE_RESULTS --campaign incomplete---------------------> LEVEL_SELECT
  LEVEL_SELECT --special entry--> SPECIAL_STAGE <-> SPECIAL_RESULTS
                                     -> SPECIAL_ZONE_RESULTS -> LEVEL_SELECT
  completed campaign -> GAME_RESULTS -> TITLE
```

The act-one handoff is an explicit transition, not a reload shortcut. REV01
increments `Current_Act`, writes `Current_Act_2P = 1`, restores both
`Two_player_mode` words, clears both starpost indices, clears `Score` and
`Score_2P`, resets both next-extra-life thresholds to 5000, and returns through
`GameModeID_Level` (`s2.asm:10812-10835`). Level initialization then resets the
per-act ring/timer/HUD transients. It retains both lives, `Current_Zone_2P`, and
the already-published act-one entries in `Results_Data_2P`. The act-two result
sets `Current_Act_2P = 2` and derives zone totals from those retained results;
it must not erase act one before `ZONE_RESULTS` (`s2.asm:10836-10866`).
Zone ties enter a tiebreaker special stage and return through special results to
the pending zone result. Selecting the special-stage entry instead advances the
native multi-act special-stage series before its aggregate result
(`s2.asm:10867-10986`).

It owns the semantic equivalent of `Two_player_mode`,
`Two_player_mode_copy`, `Current_Zone_2P`, `Current_Act_2P`,
`Results_Screen_2P`, `Results_Data_2P`, and `Game_Over_2P`. Objects and level
events consume typed questions such as `isCompetitionLevelActive()` and
`slotOf(player)` through injected services. No static/global flag, launch
configuration boolean, or zone test may duplicate that authority.

The S2 title manager does not emit `TWO_PLAYER` until the production provider
reports the full route available. A direct launch profile, if added later,
must select the same provider state machine rather than setting a level-local
flag.

### 2. Explicit playable slots and control roles

Introduce a production-owned participant registry with stable slots `P1` and
`P2` and independent control roles `HUMAN` and `CPU_SIDEKICK`. The registry is
the source for:

- main/focused participant selection;
- logical input routing;
- native P1/P2 object queries;
- collision/touch-response semantics;
- update and render order;
- player rewind identities; and
- HUD/state lookup.

Ordinary play registers P1/HUMAN and, when configured, P2/CPU_SIDEKICK.
Competition registers P1/HUMAN and P2/HUMAN. The existing
`AbstractPlayableSprite.cpuControlled` bit remains only a compatibility mirror
during migration and must not remain an authority at activation.

`GameplayTeamBootstrap` creates the exact competition pair when requested by
the session owner. It must not obtain that behavior from
`SelectedTeam.sidekicks()` or a user-selected ordinary team. `SpriteManager`
routes controller 1 or controller 2 by slot; only `CPU_SIDEKICK` invokes
`SidekickCpuController`.

### 3. Per-player gameplay state

Create a slot-indexed state surface used by production one-player code before
competition is activated. Each `PlayerCompetitionState` owns the ROM-shaped
fields required by the relevant mode: lives, score, next-extra-life threshold,
rings, timer, checkpoint save, rings collected, monitors broken, HUD dirty
flags, and act-completion state. Session-wide emerald/continue state remains in
the owner indicated by REV01 rather than being duplicated mechanically.

`AbstractPlayableSprite.getRingCount()` / `addRings()`, lost-ring spawning,
damage/death/restart, monitor contents, checkpoints, signposts, HUD, and results
resolve state by participant slot. Ordinary P1 behavior must remain identical;
ordinary CPU Tails retains the existing no-ring-scatter/no-independent-death
contract. Competition P2 takes the human damage/death path and mutates only
P2's state.

### 4. View and render graph

Generalize the gameplay composition root from one camera to an ordered
collection of semantic views. A `GameplayView` binds a participant slot,
`Camera`, viewport, scroll state, culling region, and HUD region. Ordinary play
uses one full-height view. S2 competition supplies two half-height logical
views, with P1 above P2.

`LevelFrameRuntimeUpdater`, `LevelManager`, parallax/scroll owners,
`LevelRenderer`, sprite/object/ring renderers, water/effect passes, debug
overlays, and camera rewind consume the view collection. Shared code branches
only on the number and semantics of views. S2 owns the native 2P vertical bias,
art-pointer/DPLC rules, interlace-equivalent presentation, and view-specific
scroll values derived from REV01.

The OpenGL implementation need not emulate the physical Mega Drive H-int, but
its composed output must match the two native display regions: independent
foreground/background offsets, sprite culling/tables, and HUDs without state
bleeding across the split.

### 5. Object and ring lifetime

Replace single-camera placement with a view-window set. S2 competition supplies
two camera windows and an S2-owned `Sonic2CompetitionPlacementPolicy` modeling
`ObjectsManager_2P_*`: per-player loaded block windows and cursors, a shared
live object set, and unload only when neither player's window needs a block.

The policy must preserve REV01 respawn-index ordering and CNZ's 2P object-layout
selection. `RingManager` receives the same multi-view treatment. Combining the
two camera rectangles into one large bounding box is rejected: it changes
allocation pressure, load order, persistence, and offscreen update lifetime.

Rewind captures both window/cursor graphs and preserves stable object slot and
player reference identities. `PlayerRefId` becomes slot-based; a human P2 must
never restore through `sidekick(0)` merely because that was its list position.

### 6. S2 resources and level initialization

An S2 competition level profile owns:

- the REV01 competition level order and zone/act conversion;
- forced Sonic/Tails player mode;
- P2 life/HUD PLC and compressed 2P player/object art adjustments;
- two camera blocks and view-specific scroll initialization;
- CNZ competition object layouts and every competition-only object subtype;
- two-player ring/sprite build rules; and
- the current mode copied across special-stage/results transitions.

The profile must inventory every compiled `Two_player_mode` and
`Two_player_mode_copy` read in REV01 and assign it to a concrete owner before
activation. Existing `Sonic2LevelEventManager.isTwoPlayerMode()` becomes a
semantic query of `Sonic2CompetitionSession`; it does not grow a mutable local
boolean.

### 7. Object interaction semantics

`ObjectPlayerQuery.nativeP2OrNull()` resolves slot P2 from the participant
registry, independent of CPU/human control role. Touch response selects the
human or CPU-sidekick rules from the participant role.

The S2 monitor retains its current one-player behavior throughout migration.
At final activation, its ROM branch becomes:

```text
P1 -> may break when the remaining native checks pass
P2/HUMAN + active competition -> may break
P2/CPU_SIDEKICK -> may knock down from below, but may not break from above
```

The contents object uses the breaker slot for per-player lives/rings/score and
monitor counters. Random/teleport competition contents are owned by the S2
competition item policy. No `if (player2)` exception may be added solely in
`MonitorObjectInstance`.

### 8. Results and special-stage handoff

`Sonic2CompetitionPresentation` owns the ROM-backed 2P level-select and results
screens. `Sonic2CompetitionSession` alone decides the next state; `GameLoop`
only hosts the provider's screen/level transition requests.

The existing Sonic 2 special-stage implementation must be audited as a
dependency, not assumed reusable. Activation requires the native
`Two_player_mode_copy`/`SS_2p_Flag` route, two direct human inputs, per-player
ring results, tiebreaker handoff, and return to the correct competition result
state. Ordinary one-player special-stage behavior must remain unchanged.

### 9. Rewind and lifecycle

The following state participates in a single frame snapshot:

- competition session phase and selected entry;
- both participant registrations, slots, and control roles;
- both player sprites and direct input edge state;
- both per-player progress records and checkpoints;
- both cameras/views, scroll values, and render-page state;
- two-camera object/ring placement cursors and loaded-block ownership;
- competition object-policy state, results totals, and special-stage handoff;
- all object references to P1/P2 using stable slot identities.

The session owner applies this reset/retain matrix at its principal boundaries:

| Boundary | Reset | Retain |
|---|---|---|
| title -> 2P level select | selected entry cursor defaults, current act, per-act state | newly initialized lives and empty competition result ledger |
| level select -> act 1 | both per-act score/ring/time/checkpoint/collected-ring/monitor fields and both next-life thresholds | selected zone, lives, existing completed-zone ledger |
| act 1 results -> act 2 | `Current_Act_2P = 1`; both starposts, scores, ring/time/checkpoint/collected-ring/monitor fields; next-life thresholds to 5000 | both lives, selected zone, act-one result entries, competition mode/copy restored active |
| act 2 results -> zone results | `Current_Act_2P = 2`; live level/view/object state | both act result entries and derived zone totals until results publication completes |
| zone/special results -> level select | live level/view/object state and per-act fields | lives and `Results_Data_2P`, except the ROM's game-over reset path |
| game results/title return or session clear | all competition state, participants, views, result ledger, and copied mode | no competition state |

Death/restart and special-stage entry/return receive separate source-backed
rows in the implementation inventory before activation. Rewind never crosses a
mode boundary unless the existing boundary policy captures the complete
before/after composition.

## Evidence strategy

Unit tests alone cannot prove this feature. The campaign requires:

1. ROM-contract tests for the level order, state defaults, resource/layout
   pointers, 2P art adjustments, object-layout selection, and result tables.
2. Production-route tests that enter through the S2 title selection and never
   mutate private state or construct a human P2 by hand.
3. Two-controller integration tests proving P1 and P2 input isolation,
   independent damage/rings/time/score/lives/checkpoints, camera divergence,
   view culling, object retention, and results handoff.
4. Rewind tests that capture both players/views/placement cursors and use
   `assertSame` on restored slot identities before deterministic replay.
5. Dedicated World REV01 BK2 traces for EHZ, MCZ, CNZ, and the 2P special-stage
   path, recorded with trace-v5 tooling. Ordinary one-player traces cannot
   prove competition because `Two_player_mode` stays zero.
6. A rendered split-screen capture compared at the view seam, art compression,
   sprite visibility, and both HUDs. Physics-only traces cannot prove the
   presentation path.

Trace data remains comparison-only. It may not hydrate mode, player, camera,
score, object-window, or results state.

## Activation gates

The S2 title manager may expose 2 PLAYER, and configuration documentation may
advertise human P2, only when one reviewed production route proves all of the
following together:

1. `TWO_PLAYER` delegates to the S2 competition provider and cannot fall
   through to ordinary level start.
2. Title, 2P level-select, all four entries, act/zone/game results, special
   stages, and title return form the REV01 state machine.
3. P1/HUMAN, P2/HUMAN, and P2/CPU_SIDEKICK are explicit and input-isolated.
4. Per-player rings, time, score, lives, checkpoints, collected rings, monitor
   counters, death, and HUDs are independent where REV01 requires.
5. Both cameras/views, scroll paths, render regions, sprite/ring culling, 2P
   art rules, and seam behavior match native evidence.
6. Object and ring loading uses two window/cursor sets with correct shared
   retention, order, CNZ layout, and allocation behavior.
7. Every shipped `Two_player_mode`/copy gate reachable from the competition
   route has a named production owner and test.
8. Monitor breaking, contents, random items, teleport, signposts, and result
   metrics resolve the correct participant slot without CPU-sidekick leakage.
9. The complete state/reference graph survives rewind and every session/level
   reset boundary.
10. Dedicated trace-v5 and rendered evidence passes for EHZ, MCZ, CNZ, and the
    2P special-stage/results path on the verified REV01 ROM.
11. The JDK 21 focused suites, all S2 ROM-backed tests, architecture/rewind
    guards, and the full regression comparison introduce no failure.

## Safe delivery order

Prerequisite slices may land before activation only when each one replaces a
live ordinary-play path and is production-used immediately:

1. Make the existing unsupported `TWO_PLAYER` title action fail closed instead
   of starting an ordinary level. Do not add a provider contract yet.
2. Replace main/sidekick positional inference with the participant registry in
   ordinary play, preserving P1/HUMAN plus P2/CPU_SIDEKICK behavior.
3. Route existing one-player progress, input, object queries, touch response,
   and rewind identities through stable slots.
4. Generalize the single camera/render/object/ring path to a one-element view
   collection and prove output/lifetime parity.
5. Add the shared provider host contract together with the S2 competition
   session, per-player state, two-view placement and rendering, exact S2
   resources/policies, presentation screens, special-stage handoff, traces,
   and title activation as one coherent product campaign.

No slice may add an inaccessible S2 mode flag, unused human-P2 factory,
unconsumed second camera, dormant results manager, or object-local monitor
exception. If a prerequisite migration cannot replace a live current owner in
the same change, keep it in design/tests rather than `src/main`.

## Current disposition

The audit closes the ambiguity but not the runtime gap. A complete native
competition implementation is defensible only after the role/state/view and
two-camera lifetime migrations above; attempting those migrations and the S2
product route in this remediation item would be a broad, high-regression
architecture campaign that displaces the current S3K release priority.

This assignment therefore stops at a materially narrowed design and executable
implementation plan. Current one-player and CPU-sidekick production behavior,
including the monitor gate, remains unchanged. The next safe code change is the
fail-closed `TWO_PLAYER` host contract followed by a production-used participant
role migration, each developed test-first and reviewed independently.
