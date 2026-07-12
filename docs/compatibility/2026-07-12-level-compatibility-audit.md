# Sonic 1, Sonic 2, and Sonic 3K Level Compatibility Audit

**Date:** 2026-07-12

**Scope:** Sonic 1 main stages, Final Zone, and Ending; Sonic 2 main stages through Death Egg; Sonic 3K Angel Island, Hydrocity, Marble Garden, Carnival Night, IceCap, and Launch Base.

**Excluded:** Flying Battery Zone, because it is being changed in a separate workstream; special stages, bonus stages, competition stages, menus, and credits demos except where they provide trace evidence for a main stage.
**Method:** Read-only static audit of event handlers, traversal objects, player-participation policies, camera/viewport calculations, donation capability composition, and committed tests/traces. No production code was changed and the full trace fleet was not executed.

## Executive conclusion

The engine-wide foundations are compatible with the requested features, but zone-level adoption is incomplete.

- **Multi-sidekicks:** The core sprite, collision, touch-response, rewind, and player-query layers support arbitrary sidekick lists. Many scripted traversal objects still use native P1/P2 slots, a single shared “P2” state, or only the focused/main player. This is the broadest compatibility gap.
- **Widescreen:** Rendering, camera width, general visibility, and level-wall safety have shared support. There is almost no zone-level alternate-width route coverage. Boss/event thresholds, native-screen object scans, and scripted camera choreography remain high risk. Camera thresholds must not be “fixed” by moving the maximum camera position left; that can make native world-coordinate event thresholds unreachable.
- **Cross-game donation:** No mandatory spin-dash blocker was confirmed in Sonic 1 or Sonic 2. Raw host animation IDs still create control/presentation risks. In the audited S3K range, **MGZ Dash Trigger is a confirmed S1-donation mechanic incompatibility** because it strictly requires a spin-dash animation/flag that an S1 donor does not provide. Whether every mandatory route depends on it still requires runtime reproduction.
- **Trace safety:** Native compatibility must remain the baseline: donation off, extension behavior inactive, and 320x224. Compatibility paths must be capability-, participation-, or viewport-driven and must evaluate identically under the native configuration.

The highest-risk areas are:

1. S1 SBZ and LZ scripted traversal and global transitions.
2. S2 SCZ/WFZ Tornado and team choreography; MTZ tubes/nuts; shared-P2 vines and palette triggers.
3. S3K LBZ forced carriers/launchers, followed by HCZ/MGZ/CNZ native-P1/P2 traversal objects.
4. Widescreen boss/event entry safety in every game.
5. MGZ S1-donor route reproduction and, if required, a fallback for the dash-trigger/platform pair.

## Status vocabulary

| Status | Meaning |
|---|---|
| Confirmed | Static code establishes a feature-level incompatibility directly, such as `NATIVE_P1_P2`, a two-element capture array, a raw required spin-dash check, or a fixed-width active scan. It does not by itself prove that an entire stage is incompletable. |
| High risk | The code has a strong failure mechanism, but route-level reproduction is still required. |
| Covered | The mechanic explicitly iterates all engine players, owns identity-keyed per-player state, or uses the live viewport for screen semantics. This does not replace an end-to-end test. |
| Trace fixture | A committed replay fixture/test exists. It does **not** mean the trace was freshly run or is currently green. |

Severity is separate from evidence status:

| Severity | Meaning |
|---|---|
| Critical | Can affect global progression, mandatory forced control, lethal geometry, or an entire scripted team transition. |
| High | Likely to break a common traversal mechanic, boss/event safety, or one or more participants. |
| Medium / medium-high | A real compatibility risk with a narrower route, presentation, or recovery impact. |
| Low / low-medium | No blocker found; focused verification is still needed for animation, radius, timing, or cleanup differences. |

## Requirements

### Goals

1. Every in-scope stage must be completable and safe with the main player plus zero, one, or at least three CPU sidekicks.
2. Every in-scope stage must preserve event ordering, camera locks, object availability, terrain safety, and boss containment at every width currently exposed by `WidescreenAspect`: 320, 352, 400, 528, and 800 pixels. Experimental release labeling does not remove an exposed preset from test scope.
3. Every mandatory route must be completable with donation off and with valid supported donors, including an S1 donor that has no spin dash. Same-game donation remains intentionally disabled.
4. Existing native trace behavior must remain unchanged unless a separately verified accuracy fix intentionally advances a trace frontier.

### Non-goals

- Replacing ROM-accurate two-player behavior in the native configuration merely to make an extension more symmetric.
- Adding route-, frame-, trace-, or zone-name exceptions to shared runtime logic.
- Treating a visually wider screen as wider authored level geometry. The level wall remains based on the native 320-pixel design width.
- Auditing the concurrently edited Flying Battery Zone.

### Acceptance criteria

- Native solo and native Sonic+Tails behavior remains unchanged at 320x224.
- Each scripted capture/transport mechanic either supports every engine player independently or documents and safely enforces an intentional participation policy without trapping, killing, or permanently controlling excluded players.
- Every boss/event sequence locks and releases the camera before a player can leave authored terrain at every supported width.
- No on-screen object is absent, prematurely inactive, or prematurely despawned solely because it lies beyond the native right edge.
- S1 donation can complete every mandatory route. Capability fallbacks activate only when the required capability is unavailable.
- All compatibility state is rewind-captured where it persists across frames.

## Exploration synthesis

Three independent read-only passes covered Sonic 1, Sonic 2, and the in-scope Sonic 3K zones.

### Existing shared foundations to reuse

- [`ObjectPlayerQuery`](../../src/main/java/com/openggf/level/objects/ObjectPlayerQuery.java) deduplicates the main player and arbitrary sidekick lists.
- [`ObjectPlayerParticipationPolicy`](../../src/main/java/com/openggf/level/objects/ObjectPlayerParticipationPolicy.java) distinguishes native P1/P2 behavior from all-engine-player extensions. `NATIVE_P1_P2` concretely excludes sidekick index 1 and above.
- [`PlayerCapabilityRules`](../../src/main/java/com/openggf/game/rules/PlayerCapabilityRules.java) owns spin dash, elemental shield, Insta-Shield, and flight availability.
- [`CrossGameRuleComposer`](../../src/main/java/com/openggf/game/rules/CrossGameRuleComposer.java) correctly keeps host runtime rules while importing explicit donor capabilities.
- [`WidescreenAspect`](../../src/main/java/com/openggf/configuration/WidescreenAspect.java) defines the supported widths.
- General object visibility/despawn and placement have widescreen-aware paths in `AbstractObjectInstance` and `ObjectPlacementController`. Sonic 2 overrides the shared behavior through [`S2ObjectWindowing`](../../src/main/java/com/openggf/game/sonic2/objects/S2ObjectWindowing.java), whose fixed `LOAD_AHEAD=0x280` and `UNLOAD_COMPARE=0x280` windows can leave visible wide-screen objects unloaded or prematurely unloaded, especially at 800 pixels and at coarse-window edges near 528 pixels.
- `ObjectManager` collision/touch phases generally receive all players, but an object's ordinary `update(frame, player)` call still supplies the primary player. Objects with proximity, capture, scripted movement, or ownership state must query participants explicitly.

### Cross-cutting evidence

- S1 has only a small set of explicit all-player object adaptations, including bridges, breakable walls, spikes, and Final Zone false-floor cleanup.
- S2 contains both good identity-keyed implementations (for example CPZ tubes and CNZ flippers) and unsafe shared-P2 implementations (vines, palette switchers, forced-spin triggers).
- S3K has strong all-player event work in several zones, but many ROM two-slot carriers and capture objects deliberately remain `NATIVE_P1_P2`.
- Existing widescreen tests focus on camera/configuration/UI foundations. No in-scope zone has a complete alternate-width route and boss/event matrix.
- Trace replay bootstrap forces the recorded team and donation off, but it does **not** currently snapshot/force/restore display aspect or viewport width. Native-width isolation is therefore a prerequisite before trace replay can be relied on as an automatic 320-pixel guard. Compatibility tests must be additive, not replacements for trace replay.

## Per-zone audit

### Sonic 1

All gameplay acts have committed complete-run trace fixtures/tests. This audit did not freshly execute them.

| Zone | Multi-sidekicks | Widescreen | Donation | Primary findings |
|---|---|---|---|---|
| Green Hill 1-3 | Medium risk | High risk | High risk | Bridge participation is all-player, but collapsing/proximity routines remain primary-driven. GHZ boss progression uses camera X/Y. Breakable walls compare raw S1 roll animation, so donated characters may fail a valid rolling smash. Spawned wall fragments—not the intact traversal wall—retain a fixed `cameraX + 320` cull path and can disappear early in a wide view. |
| Marble 1-3 | High risk | High risk | High risk | Push blocks accept per-player contacts but update shared motion from the primary player; simultaneous opposing pushes are unproved. Boss/vertical transitions are camera-driven. Smash-block fragments retain fixed native-width culling, while the larger systemic risk is S1 counter-based placement's fixed ROM out-of-range limit. The push-block `320` is a margin and the collapsing-floor helper is width-driven, so they are not classified as viewport-width defects. Smash blocks include a rolling-state fallback, reducing but not removing raw-animation risk. |
| Spring Yard 1-3 | High risk | High risk | Medium risk | Bumpers accept every player's contact but store a single pending touched player, so same-frame contacts can overwrite. Act 2 vertical and boss logic mix camera state with the focused player. Monitors compare a raw S1 roll animation. |
| Labyrinth 1-3 | **Critical** | High risk | High risk | Wind tunnels and water slides operate on the focused sprite; breakable poles capture/update the primary player and explicitly exclude CPU players; the air-countdown sidecar is P1-owned. The confirmed risk is missed transport/safety for extra sidekicks; stale control still requires reproduction. Dynamic water uses camera X plus focused-player Y. Tunnel/slide/pole code forces raw S1 float, slide, walk, and hang IDs. |
| Star Light 1-3 | High risk | High risk | High risk | Seesaw owns one standing-player reference and assumes the first playable is main. Elevator and staircase state is primary-oriented. Boss activation/lock is camera-X driven. Seesaw/ball/boss paths force raw S1 spring/roll animations. |
| Scrap Brain 1-3 | **Critical** | **Critical** | High risk | Teleporter and junction own one controlled player and test the primary only; running discs and spin conveyors have similar ownership risks. A sidekick pit fall can request the global SBZ2-to-SBZ3 transition prematurely. SBZ3-to-FZ locks only the focused player. Late camera-X triggers occur around pits and arena boundaries. |
| Final Zone | High risk | High risk | Medium-high risk | Boss targeting/AI is primary-oriented, although false-floor cleanup is all-player. Camera-X progression and one-way locks need wide arena safety tests. Roll-state fallback lowers donation risk compared with S1 walls. |
| Ending | Medium risk | Medium risk | High risk | Choreography hides, moves, replaces, and locks the focused player only; extra sidekicks can remain active/visible. It forces a raw S1 wait animation. Wide views can reveal off-stage content. |

Key files:

- [`Sonic1LZWaterEvents.java`](../../src/main/java/com/openggf/game/sonic1/events/Sonic1LZWaterEvents.java)
- [`Sonic1TeleporterObjectInstance.java`](../../src/main/java/com/openggf/game/sonic1/objects/Sonic1TeleporterObjectInstance.java)
- [`Sonic1JunctionObjectInstance.java`](../../src/main/java/com/openggf/game/sonic1/objects/Sonic1JunctionObjectInstance.java)
- [`Sonic1LevelEventManager.java`](../../src/main/java/com/openggf/game/sonic1/events/Sonic1LevelEventManager.java)
- [`Sonic1BumperObjectInstance.java`](../../src/main/java/com/openggf/game/sonic1/objects/Sonic1BumperObjectInstance.java)
- [`Sonic1SeesawObjectInstance.java`](../../src/main/java/com/openggf/game/sonic1/objects/Sonic1SeesawObjectInstance.java)

### Sonic 2

Most acts have committed trace fixtures. Notable gaps found by static inventory are EHZ2 full replay and a full two-boss Death Egg fight replay; fixture presence elsewhere is not a fresh green assertion. A separate cross-zone issue applies to every row: S2's fixed object placement/load-unload window is not fully viewport-width-aware.

| Zone | Multi-sidekicks | Widescreen | S1 donation | Primary findings |
|---|---|---|---|---|
| Emerald Hill 1-2 | High risk | High risk | Low risk | Bridge deformation samples main plus native P2 only. EHZ2 boss thresholds and arena use camera-left coordinates; the level wall stays safe but the wide band exposes beyond the lock. No spin-dash gate. |
| Chemical Plant 1-2 | Tube covered; medium residual risk | High risk | Low risk | CPZ tubes are a positive identity-keyed, all-player implementation. Act 2 water/boss events remain focused-player/camera driven. Tubes force roll state, so S1 donor traversal should work; animation restoration still needs testing. |
| Aquatic Ruin 1-2 | High risk | High risk | Low risk | Platforms iterate all players, but vine switches map all extra sidekicks onto shared P2 grab/release state. Whisp uses a hardcoded 320x224 visibility predicate and can attack late in a wide view. |
| Casino Night 1-2 | High risk | High risk | Low risk | Flippers and bumpers are strong all-player examples. Forced-spin triggers still own only Sonic/Tails booleans. CNZ bumper placement scans a fixed `0x150` window, narrower than wide viewports, so visible far-right bumpers can be absent. |
| Hill Top 1-2 | High risk | High risk | Low risk | Seesaw stores exactly two standing players and samples native P2 only. Earthquake and boss thresholds are native camera coordinates; overlay and arena need visual/safety verification. |
| Mystic Cave 1-2 | High risk | High risk | Low risk | Moving vines include extras but collapse all non-main players into one P2 state, allowing concurrent grabs/releases to overwrite. Boss camera lock and dense traversal need width tests. |
| Oil Ocean 1-2 | High risk | High risk | Low risk | Oil-slide events correctly process all players. Popping platforms and OOZ springs sample native P2 only, so extras can miss inherited launch or platform state. Launchers force roll/velocity; no spin-dash input is required. |
| Metropolis 1-3 | High risk | High risk | Low risk | Spin tubes and nuts own P1/P2 state only. The nut is activated by standing and horizontal movement, not spin dash; an LRZ-bike-style S1 workaround is not needed here. Dense wide placement deserves slot-pressure testing. |
| Sky Chase | **Critical** | Medium-high risk | Low risk | Tornado scripted movement, camera push, input, and completion are main-player-only. Extra sidekicks are not attached or advanced as a team and can fall, despawn, or disrupt transition. |
| Wing Fortress | **Critical** | High risk | Medium risk | Event control lock affects only the focused player. Tornado dock/jump choreography is main-only. Moving vine and palette switcher collapse all extras into shared P2 state. Wide view can expose scripted ship elements before native event thresholds. |
| Death Egg | Medium-high risk | High risk | Low risk | Boss/cutscene progression is main-camera driven and extra sidekicks are not explicitly bounded through the Mecha Sonic-to-Robot transition. Mecha uses native `cameraX + 0xA0` centering, left-biasing the fight in widescreen. No spin dash or super state is required to finish. |

Key files:

- [`BridgeObjectInstance.java`](../../src/main/java/com/openggf/game/sonic2/objects/BridgeObjectInstance.java)
- [`VineSwitchObjectInstance.java`](../../src/main/java/com/openggf/game/sonic2/objects/VineSwitchObjectInstance.java)
- [`MovingVineObjectInstance.java`](../../src/main/java/com/openggf/game/sonic2/objects/MovingVineObjectInstance.java)
- [`ForcedSpinObjectInstance.java`](../../src/main/java/com/openggf/game/sonic2/objects/ForcedSpinObjectInstance.java)
- [`MTZSpinTubeObjectInstance.java`](../../src/main/java/com/openggf/game/sonic2/objects/MTZSpinTubeObjectInstance.java)
- [`NutObjectInstance.java`](../../src/main/java/com/openggf/game/sonic2/objects/NutObjectInstance.java)
- [`TornadoObjectInstance.java`](../../src/main/java/com/openggf/game/sonic2/objects/TornadoObjectInstance.java)
- [`Sonic2WFZEvents.java`](../../src/main/java/com/openggf/game/sonic2/events/Sonic2WFZEvents.java)

### Sonic 3K through Launch Base, excluding Flying Battery

AIZ is documented as trace-green. HCZ, MGZ, CNZ, ICZ, and LBZ have trace fixtures/tests, but this audit does not claim they are currently green.

| Zone | Multi-sidekicks | Widescreen | S1 donation | Primary findings |
|---|---|---|---|---|
| Angel Island 1-2 | High risk | Medium-high risk | Medium risk | Giant/normal ride vines and hollow tree track native P2 only. The Act 2 capsule has mixed native-P2 handling while the boss-end controller handles all players, risking stale control on extras. Bombing auto-scroll and debris/tree helpers retain native offsets. No confirmed spin-dash blocker. |
| Hydrocity 1-2 | **Critical** | Medium-high risk | Medium risk | Conveyor belts, twisting loops, breakable bars, hand launcher, water skim, and some boss paths are native-P1/P2 only. Wall chase/water wall/end capsule have good all-player handling, showing inconsistent policy rather than a missing framework. Native camera-relative activation remains untested at wide widths. |
| Marble Garden 1-2 | High risk | High risk | **Confirmed mechanic incompatibility** | Twisting loop, pulley, and top platform use native P1/P2; drill/passenger event and capsule handle all. Miniboss/drilling choreography retains native screen offsets. `MGZDashTriggerObjectInstance` strictly requires spin-dash animation/flag, so an S1 donor cannot activate its paired platform. Mandatory-route impact still requires reproduction. |
| Carnival Night 1-2 | High risk | Medium-high risk | Low-medium risk | Spiral tube, wire cage, triangle bumper, parts of cylinder, and generic auto-spin exclude extras. Vacuum tube, trap door, hover fan, barber pole, and boss are all-player/extended examples. Boss/cutscene paths use native screen constants. No mandatory manual spin-dash gate found. |
| IceCap 1-2 | High risk | Medium-high risk | Low-medium risk | End-boss frost capture, freezer, stalactite, ice spikes, and snow-pile P2 handling are native-only. Snowboard crash, tension platform, and breakable wall have extended/all-player paths. Snow pile recognizes spin dash as one response but also breaks from sufficient grounded speed; no confirmed route blocker. |
| Launch Base 1-2 | **Critical** | **Critical** | Medium risk | Cup elevator, player launcher, ride/lowering grapples, tube elevator, rolling drum, and exploding trigger are native-P1/P2 only. Third-plus players can be left behind, crushed, or retain control. LBZ2 launch manually centers camera with `playerX - 0xA0` and advances it through a lethal sequence; collapse/copy-window math also needs every width. No strict spin-dash gate found, but forced animation/state compatibility must be proven. |

Key files:

- [`AizHollowTreeObjectInstance.java`](../../src/main/java/com/openggf/game/sonic3k/objects/AizHollowTreeObjectInstance.java)
- [`HCZBreakableBarObjectInstance.java`](../../src/main/java/com/openggf/game/sonic3k/objects/HCZBreakableBarObjectInstance.java)
- [`HCZHandLauncherObjectInstance.java`](../../src/main/java/com/openggf/game/sonic3k/objects/HCZHandLauncherObjectInstance.java)
- [`MGZDashTriggerObjectInstance.java`](../../src/main/java/com/openggf/game/sonic3k/objects/MGZDashTriggerObjectInstance.java)
- [`MGZPulleyObjectInstance.java`](../../src/main/java/com/openggf/game/sonic3k/objects/MGZPulleyObjectInstance.java)
- [`CnzWireCageObjectInstance.java`](../../src/main/java/com/openggf/game/sonic3k/objects/CnzWireCageObjectInstance.java)
- [`IczSnowPileObjectInstance.java`](../../src/main/java/com/openggf/game/sonic3k/objects/IczSnowPileObjectInstance.java)
- [`Sonic3kLBZEvents.java`](../../src/main/java/com/openggf/game/sonic3k/events/Sonic3kLBZEvents.java)
- [`LbzCupElevatorInstance.java`](../../src/main/java/com/openggf/game/sonic3k/objects/LbzCupElevatorInstance.java)
- [`LbzTubeElevatorInstance.java`](../../src/main/java/com/openggf/game/sonic3k/objects/LbzTubeElevatorInstance.java)

## Trace-fixture inventory

This appendix is a reproducibility map, not a fresh pass report.

- **Sonic 1:** `TestS1Ghz{1,2,3}CompleteRunTraceReplay`, `TestS1Mz{1,2,3}CompleteRunTraceReplay`, `TestS1Syz{1,2,3}CompleteRunTraceReplay`, `TestS1Lz{1,2,3}CompleteRunTraceReplay`, `TestS1Slz{1,2,3}CompleteRunTraceReplay`, `TestS1Sbz{1,2,3}CompleteRunTraceReplay`, and `TestS1FzCompleteRunTraceReplay` cover every main gameplay act and Final Zone.
- **Sonic 2:** level-select fixtures exist for ARZ1/2, CNZ1/2, CPZ1/2, HTZ1/2, MCZ1/2, MTZ1/2/3, OOZ1/2, SCZ, and WFZ; `TestS2Ehz1TraceReplay` covers EHZ1; `TestS2DezEndingLevelSelectTraceReplay` covers the committed Death Egg/ending route. The inventory found no EHZ2 replay and no separate full two-boss DEZ replay. Relevant classes live under `src/test/java/com/openggf/tests/trace/s2`.
- **Sonic 3K in scope:** `TestS3kAizTraceReplay`, `TestS3kAizCompleteRunTraceReplay`, `TestS3kHczCompleteRunTraceReplay`, `TestS3kMgzTraceReplay`, `TestS3kMgzCompleteRunTraceReplay`, `TestS3kCnzTraceReplay`, `TestS3kCnzCompleteRunTraceReplay`, `TestS3kIczCompleteRunTraceReplay`, and `TestS3kLbzCompleteRunTraceReplay`. The frontier log documents AIZ green; fixture existence alone is the only claim made for later zones here.

## Architecture decision

### Player participation

Every mechanic must declare its semantics using the existing participation policy:

- Preserve `MAIN_ONLY_NATIVE` or `NATIVE_P1_P2` only when native behavior requires it **and** excluded extension players are kept safe, cannot trigger global state incorrectly, and cannot retain stale control.
- Use `MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED` when every extra CPU sidekick should receive the native P2 behavior.
- Use `ALL_ENGINE_PLAYERS` for hazards, terrain modifiers, arena bounds, releases, global cleanup, and the **effects** of an accepted team transition. Transition authority remains separate: only the main/progression owner may request a global act transition; once accepted, cleanup, relocation, and release cover all engine players.
- Use identity-keyed state for captures, cooldowns, trigger crossings, carrier phases, and ownership. Never map every sidekick index above zero onto one P2 boolean/slot.

Native two-slot state should not simply be enlarged in place when doing so would alter trace-visible update order. Keep the native path/order and add an extension-owned per-player state path whose behavior is inactive with no extra sidekicks.

### Widescreen semantics

Classify each coordinate before changing it:

1. **ROM world-coordinate event threshold:** keep the threshold constant unchanged unless reference evidence proves different semantics. At width 320, compare the existing raw camera operand exactly. At extension widths, use a verified native-equivalent camera-coordinate helper only where route tests prove raw wide-camera X activates too late; that helper must collapse exactly to `camera.getX()` at width 320 and must cover easing/locks, not only centered steady-state movement.
2. **Authored level wall or terrain edge:** use the native 320-pixel design width, not viewport width.
3. **Visible-screen edge, spawn, cull, or attack activation:** derive from the live viewport.
4. **Screen center or choreography offset:** use a width-derived center only for the extension path, with exact native equivalence at width 320.
5. **Boss/transition safety:** test that event state and bounds engage before any player reaches missing/lethal geometry. Do not reduce reachable camera X to hide empty space; documented boss thresholds may otherwise never fire.

Prefer the existing camera, deadzone, viewport, placement, and object-range helpers. Add a shared semantic helper only if at least three audited call sites require the same distinction.

### Donation behavior

- Gameplay runtime queries the effective injected `PlayerCapabilityRules`; it must not query donor identity or `DonorCapabilities` directly. `DonorCapabilities` remains composition input owned by `CrossGameRuleComposer`.
- Preserve whether the ROM tested animation, status, or control state. When the ROM tested animation, compare against the active player's profile-resolved animation ID (for example the donor-resolved roll ID), not `getRolling()` and not a hardcoded host ID. Use movement/control predicates only where the ROM tested movement/control state.
- Preserve explicit host animation assignments only when a translation/profile layer guarantees the active donor mapping.
- For the MGZ dash actuator, keep native spin-dash activation unchanged when spin dash is available. When it is unavailable, use the smallest semantic fallback that proves sustained intentional activation (for example, grounded running/pushing against the actuator above a verified threshold). Label the compatibility rationale clearly, but gate it by missing capability, not by zone or donor ID.

### Trace and rewind safety

- Compatibility state that survives a frame must participate in generic rewind capture or an existing schema/adapter.
- Trace data remains comparison-only and read-only.
- Native trace runs must snapshot/force/restore width 320, disable donation, and reproduce exactly the trace-recorded native team. No extension-added sidekicks may leak into the run.
- Every compatibility fix gets a focused native-off regression before its extension cases.

## Feature design: compatibility verification harness

The audit should be converted into a reusable parameterized harness rather than one-off manual checks.

### Configuration axes

- Team: main only; main + one sidekick; main + three distinct sidekicks; main + three duplicate characters.
- Width: 320, 352, 400, 528, 800.
- Donation: off; S1 donor; S2 donor; S3K donor where the host/donor pairing is valid. Same-game donation is excluded by design.
- Route: mandatory route first, then alternate routes containing audited traversal objects.

### Required assertions

- Every participant either completes the scripted mechanic or is deliberately excluded and remains safe/uncontrolled.
- Capture/control flags, collision state, animation overrides, and camera locks are released for every participant.
- A sidekick cannot request a global act transition ahead of the main progression owner.
- The event routine reaches its terminal state; boss spawns; camera min/max bounds engage; no player crosses missing geometry.
- Objects visible in the wide extension exist, update, collide, and do not prematurely despawn.
- Donated animation/capability differences do not block mandatory traversal.
- A rewind round trip during capture/choreography restores per-player ownership and phase.

### Test layers

1. Focused object tests for simultaneous interactions and per-player state.
2. Headless event/route tests at every width.
3. Donation route tests with capability assertions.
4. Paired native assertions proving exact 320/no-extra/donation-off behavior is unchanged, followed by existing native trace replays.
5. Targeted visual captures for wide boss arenas, overlays, and scripted scenes.

## Prioritized implementation plan

This is an audit backlog, not authorization to implement all items in one branch. Each wave should use tests first and preserve disjoint ownership where possible.

### Wave 0: Baselines and guards

- Inventory every zone object using `NATIVE_P1_P2`, `nativeP2OrNull`, fixed two-slot player arrays, shared-P2 booleans, raw host animation predicates, and fixed native screen scans.
- Create an explained baseline so new violations fail while existing items are migrated deliberately.
- Baseline additions require a documented native-accuracy reason; newly implemented mechanics may not grow the compatibility baseline.
- Extend trace configuration snapshot/bootstrap/restore to isolate display aspect and force width 320 while preserving the trace-recorded native team and donation-off invariant.
- Add a parameterized zone compatibility fixture with explicit native configuration assertions.
- Verification: focused guard tests plus `mvn "-Dtest=TestObjectPlayerQuery,TestWidescreenNativeRegression,TestCrossGameRuleComposer,TraceReplaySessionBootstrapConfigTest" test`.

### Wave 1: Global-transition and forced-control safety

- S1: SBZ pit-transition ownership, teleporter, junction, and LZ tunnel/slide/pole control.
- S2: SCZ/WFZ Tornado team choreography and WFZ focused-player input lock.
- S3K: LBZ launchers, elevators, grapples, drums, exploding trigger, and LBZ2 launch camera sequence.
- Tests: simultaneous four-player entry, sidekick-first failure, death/respawn during control, and rewind mid-sequence.
- Verification: focused object/event tests, corresponding headless completion tests, then native zone traces.

### Wave 2: Shared-P2 and fixed-capacity mechanics

- S2: ARZ/MCZ/WFZ vines and palette triggers; HTZ seesaw; EHZ bridge; CNZ forced-spin; OOZ spring/platform; MTZ tube/nut.
- S3K: AIZ vines/tree/capsule; HCZ conveyors/loops/bars/launcher; MGZ loops/pulley/top platform; CNZ tube/cage/cylinder; ICZ freeze/hazard objects.
- Convert extension behavior to identity-keyed state without changing native order.
- Verification: focused 1/2/4-player tests and rewind graph tests.

### Wave 3: Widescreen event and object audit

- Add mandatory-route and event checkpoints for **every in-scope act** at all five widths; final-act boss containment is a required subset, not the whole widescreen matrix.
- Correct S2's shared fixed placement/load-unload window before relying on per-zone results, while preserving its native counter/slot behavior and avoiding dense-area slot inflation.
- Fix other screen-edge semantics such as the separate S2 CNZ bumper scan, ARZ Whisp activation, and fixed custom object culls.
- Validate native-world thresholds before changing any event condition.
- Add visual capture checks for HTZ overlay, AIZ bombing, LBZ launch/collapse, Death Egg centering, and wide arena bands.
- Verification: widescreen suite, zone event tests, and native trace regressions.

### Wave 4: Donation semantics

- Replace hardcoded S1 animation IDs in walls, monitors, poles, tunnels, slides, junctions, teleporters, and scripted results with active-profile-resolved animation IDs while preserving whether the ROM tested animation versus status/control state.
- Audit the 36 S2 host-animation assignments in traversal code for translated donor presentation and control cleanup.
- Reproduce MGZ mandatory routes with an S1 donor. If a required route depends on the dash actuator, implement and document the capability-driven fallback.
- Run mandatory-route completion with every supported donor; rerun native traces with donation off.

### Wave 5: Full fleet and documentation

- Run all focused compatibility tests, all in-scope complete-run traces, rewind coverage guards, and the normal Maven suite.
- Update `docs/TRACE_FRONTIER_LOG.md` only when a trace frontier moves, regresses, or a full sweep is used to choose work.
- Update known discrepancies only for intentional, reviewed extension divergences.
- Keep Flying Battery changes and verification in its separate owning workstream.

## Integration report

- Changed files: this audit document only.
- Production behavior: unchanged.
- Tests run: none; evidence is static inspection and committed test/fixture inventory.
- Existing user changes in the worktree were not modified.
- Unresolved risks: runtime reproduction is still required; a fixture's existence is not evidence that it currently passes.

## End-to-end review

### Blockers before claiming compatibility

1. No complete multi-sidekick route matrix exists.
2. No complete alternate-width zone/boss matrix exists.
3. MGZ's dash-trigger/platform mechanic is incompatible with an S1 donor; mandatory-route impact has not yet been reproduced.
4. S1 SBZ, S2 SCZ/WFZ, and S3K LBZ contain global-transition or forced-control paths that do not safely cover arbitrary sidekicks.

### Residual risks after the planned work

- Very wide views can reveal unauthored space even when collision safety is correct.
- Enlarged active object windows can increase fixed-slot pressure in dense S2/S3K areas; load-ahead and despawn semantics must remain distinct.
- Duplicate-character teams stress art banks and rewind identity beyond ordinary traversal logic.
- Boss AI may intentionally target only the main player while arena hazards and cleanup must still affect the full team; tests must preserve that distinction.

### Human-review checklist

- Confirm the zone scope and risk ordering.
- Confirm that native P1/P2 behavior may remain ROM-accurate while extension-only state supports additional sidekicks.
- Confirm the intended MGZ no-spin-dash activation behavior before implementation.
- Confirm whether 800-pixel `SUPER_32_9` remains exposed after the audit; while exposed, it remains in compatibility test scope even if labeled experimental.
- Approve separate implementation branches/workstreams rather than a single cross-game compatibility mega-change.

