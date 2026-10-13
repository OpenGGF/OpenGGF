# Sonic3K Chaos Mode research and proposed design

Sonic3K Chaos Mode adds periodically selected, temporary mutators to normal
Sonic 3 & Knuckles gameplay. The aim is an unpredictable but playable run:
cosmetic jokes, helpful physics changes and short control surprises occur
often; teleports and major movement disruption occur rarely. The player
chooses the effect pool, interval and duration multiplier. Stream voting is
a later extension.

This is a research and design proposal, not an implemented mod. The initial
scope is native S3&K characters and teams in ordinary acts. Donor characters,
custom characters, special stages and bonus stages require separate
qualification; they must not inherit support just because a policy exists.
The requested research-first pass does not implement or change the Mod API.

## Inspiration from existing chaos mods

[Vice City's ZChaos](https://github.com/Zolika1351/ChaosModGTAVC) documents a
default 30-second interval, configurable effect lengths and enablement,
on-screen timer/effect displays, difficulty controls and Twitch voting.
Voting can choose the majority winner or use vote proportions as selection
probabilities, with a separate window, game display or HTML overlay.

Its [effect catalogue](https://zolika1351.pages.dev/chaos/gtaiiivc) mixes
presentation changes, altered controls and physics, helpful interventions
and disruptive teleports. Examples include widened characters, invisible
characters, mirrored screens, changed gravity and a brief fake teleport that
returns the player. Some effects explicitly preserve collision while
changing appearance. That separation translates well to Sonic sprites.

The useful pattern is a countdown followed by a named surprise, with each
effect having its own duration and eligibility. Sonic needs a gentler
adaptation: short platforming errors can lose an entire route, and stock
scripted transport, camera locks and terrain changes limit valid relocation.
The proposal therefore includes positive effects and reserves severe changes
for a small probability budget.

[GTA V Chaos Mod's effect metadata](https://github.com/gta-chaos-mod/ChaosModV/blob/master/ChaosMod/Effects/EffectData.h)
also separates weights, duration types, conditions, groups, incompatible IDs
and voting exclusions. Its
[changelog](https://github.com/gta-chaos-mod/ChaosModV/blob/master/CHANGELOG.md)
records reduced repeat weighting and fixes for effects surviving pauses or
failing to restore camera behavior. These support making compatibility,
repeat prevention and cleanup explicit parts of the design.

All catalogue effects and numeric defaults below are proposals for OpenGGF,
not claims that these games use the same balance or implementation.

## Current OpenGGF capabilities

Reviewed `develop` at `13add5b0b918a1f7ff7e343c8ccc858ad4527d85` on
2026-10-13. The [release descriptor](../../../mod-api-release-policy.properties)
declares the unpublished `0.7.0` candidate API. Findings are from source
inspection, not gameplay qualification.

- [ModContext](../../../src/main/java/com/openggf/mods/code/ModContext.java)
  registers creator-owned mutator definitions transactionally, with a limit
  of 32 per owner. Thirty Chaos definitions fit. Reuse policy constructors
  under Chaos's ownership; do not depend on another installed example mod's
  IDs.
- [MutatorPolicy](../../../src/main/java/com/openggf/mods/mutators/MutatorPolicy.java)
  has 11 sealed host-interpreted kinds. Mods can compose new definitions and
  configurations using those kinds, but cannot implement additional kinds
  themselves.
- Current reusable LIVE policies include head scaling, player invisibility,
  dry Sonic gravity, gameplay pacing, hurt-ring scatter and badnik-defeat
  rebound. Their current limits are documented below. LOAD policies for
  ring, checkpoint and monitor placement are unsuitable for timed effects.
- [MutatorSessionState](../../../src/main/java/com/openggf/mods/mutators/MutatorSessionState.java)
  and `MutatorWorldAccess` are internal, outside the supported public API.
  LIVE currently means admission when the configuration menu resumes.
  There is no supported automatic temporary activation/expiry port.
  Its user-configuration history is capped at 1,024 events; repeated Chaos
  activations must not consume that journal indefinitely.
- The [Mutator Lab example](../../../examples/example-mutators/src/main/java/mutators/MutatorsMod.java)
  demonstrates a native-game patch with a title screen and in-level
  [LevelInputOverlay](../../../src/main/java/com/openggf/game/LevelInputOverlay.java).
  The existing configuration screen is flat and final; it does not provide
  the requested available/selected list layout or category metadata.
- [SnapshotRandom](../../../src/main/java/com/openggf/mods/state/SnapshotRandom.java),
  owner service bundles and rewind adapters provide useful state primitives.
  A zero RNG seed is absorbing and must be replaced with a nonzero fallback.

## Recommended architecture

Use an S3K `GamePatch` over the native module, with a Chaos-owned controller,
effect catalogue, menu and countdown/active-effect HUD. Preserve the native
title/save flow, events, bosses and transitions; the example's fresh-run-only
title is a pattern to adapt, not a reason to remove stock save selection.
The installed/enabled mod decorates S3K normally, but its controller stays
dormant until the player enables Chaos in the title or pause menu. Current
`GameplayLaunchRequest` has no separate mode-selection field; this approach
does not require a new launch contract. Configuration and effect factories
run inside the existing creator fault boundaries.

Three approaches were considered:

| Approach | Assessment |
| --- | --- |
| Mod-owned controller using supported stock-loop observation and temporary policy leases | Recommended. Keeps effect design and scheduling in the mod while the host retains gameplay authority, composition, timing and cleanup. Needs narrow public API additions. |
| Declarative sequence scheduler entirely interpreted by the host | Viable alternative. Reduces creator callbacks, but puts pool, random parameter, cooldown and voting abstractions into a larger shared scheduling contract. Reconsider if several mods need the same scheduler. |
| Direct internal session calls or taking over the frame controller | Rejected for the maintained mod. Internal calls depend on unsupported contracts and overwrite user preferences; frame control changes the ordinary gameplay lifecycle. |

The following names describe responsibilities, not existing public classes
or frozen API signatures:

1. **Chaos catalogue:** 30 stable local IDs registered as mod-owned mutators.
   Each effect has a category, severity, base duration, maximum duration,
   conflict family, prerequisites and bounded random configuration choices.
   Definitions describe capabilities; inclusion in the random pool does not
   make the mutator permanently enabled. Keep catalogue preferences separate
   from persistent mutator enable flags.
2. **Chaos controller:** selects eligible effects and samples configuration
   once at admission using `SnapshotRandom`. Captures its complete state in
   a fresh per-world `GameServiceBundle`/rewind graph. It does not access the
   engine singleton or mutate live sprites directly.
3. **Stock-loop observer, proposed public addition:** exposes read-only
   lifecycle/phase and timing information, including world generation,
   level visit/load cause, eligible presentation boundaries, executed native
   ticks and teardown. It cannot replace, suppress or reorder native owners.
   Admit queued changes before the relevant native consumers run.
4. **Temporary mutator leases, proposed public addition:** an owner-bound
   facade validates this mod's registered LIVE definition and sampled
   configuration, admits a bounded contribution atomically and returns a
   generation-bound activation identity. Expiry or cancellation removes
   only that contribution and recomputes the effective policy. Expose a
   read-only semantic view of effective policies and admission reasons, so
   cross-owner eligibility does not require internal session access.
   One-shot policies execute once at admission and journal their activation
   identity; they are not reapplied on every tick of a timed lease.
5. **New semantic consumers:** support additional presentation, input,
   movement and coherent relocation policies. Shared engine code consumes
   semantic capabilities; it must not branch on the Chaos mod name. New
   kinds require coordinated API descriptor/version/signature updates under
   the [compatibility policy](../mod-api-compatibility.md).

The [GameplayFrameController](../../../src/main/java/com/openggf/game/mode/GameplayFrameController.java)
is not a neutral timer hook: `GameLoop` selects an alternative controlled
iteration path that skips parts of ordinary level-mode lifecycle handling.
Do not adopt it solely to receive ticks. Similarly,
[hosted stock gameplay runs](../../modding/guides/gameplay-runs.md) deliberately
strip mod gameplay content and cannot launch Chaos unchanged.

## Configuration and timing

The main menu has **Duration between effects**, **Effect duration
multiplier**, and **Available** / **Selected** lists. Selecting an Available
row and choosing Add moves it into Selected; Remove moves it back. Support
keyboard/controller navigation and explicit buttons, rather than requiring
dragging. Search and category filters help locate effects. Each row shows
its category, severity, duration and a short description. Selected means
eligible for future draws, not currently running.

| Setting | Proposed default and behavior |
| --- | --- |
| Duration between effects | 30 active-play seconds; range 5–120. One selection opportunity per interval. |
| Effect duration multiplier | 1.0×; range 0.25–3.0×. Applies to each effect's base duration, bounded by its own maximum. |
| Selected effects | Balanced preset selects 28 effects; Gravity Roulette and Glitch Step start unselected. Add All is an explicit player choice. |
| Overlap | At most two timed effects, at most one nonvisual effect, and at most one member of each conflict family. |
| Seed | Generate once per new run; advanced settings can accept a nonzero repeatable seed. |
| Pause / clear | Pause Chaos freezes its countdown and cancels its current effects. Clear Active Effects cancels current effects while keeping future selection enabled. |

Use a captured **logical active-presentation clock** independent of Game
Speed's native tick multiplier. Pause, configuration holds, setup, fades,
results, death recovery, stage visits and rewind do not advance it. Expiry
must still be serviced on eligible presentations that execute zero native
ticks; multiple fast native ticks must not multiply the countdown. Rendering
and GPU frame count do not advance RNG or timer state. The observer and host
clock need this explicit contract before implementation.

For example, a 20-second effect at 1.5× lasts 30 active-play seconds. Gravity
Hiccup has a 3-second maximum regardless of the global multiplier. Instant
teleports have no ongoing duration; the multiplier does not repeat them or
increase their probability. Fast Forward changes interactive tick pacing,
not velocities or the desktop frame limiter. Unless a row specifies a shorter
maximum, timed Ordinary effects cap at 60 seconds and Disruptive effects at
15 seconds; timed Major effects use their explicit short maximum.

During play, show the next-effect countdown, active effect names and remaining
time. Keep the Chaos display readable during screen mirroring or inversion.
Menu edits apply atomically on Resume; interval changes start a fresh interval,
and duration changes affect future activations. Removing an active effect
cancels its lease. Empty selection shows `No effects selected` without rolling
RNG. Unsupported entries remain visible with the reason they cannot currently
run; selection does not grant missing capabilities.

## Rarity and compatibility

Category describes what changes. Severity describes how disruptive it is;
a physics effect can be as disruptive as a teleport. Choose a severity tier
first, then an eligible effect within that tier:

- **Ordinary: 90%.** Cosmetic effects and manageable/helpful physics changes.
- **Disruptive: 9%.** Short confusing controls, brief gravity changes and
  small local displacements.
- **Major: 1%.** Progress-changing teleports and unstable movement patterns.

At a 30-second interval, an unconstrained Major draw averages once per
50 minutes of active play. This is a probability, not a scheduled event;
eligibility and cooldowns can make it less frequent. Removing ordinary
effects must not renormalize Major effects to 100%. If a tier has no eligible
effects, fall back to a lower severity tier, or leave that opportunity quiet.
Never fall upward. Make this visible when only rare effects are selected.

Apply a 180-second shared cooldown after a Major activation, suppress all
Chaos during the first five seconds of stable spawn/respawn, and avoid the
last three effects when enough alternatives exist. With a small pool, shorten
repeat exclusion deterministically so a single ordinary effect can still run.
No effect may overlap its own activation.

Host availability checks exclude death, fades, transport attachments and
transitions. Only compatible cosmetic effects may start during bosses or
scripted scenes; relocation and gravity reversal require separately
qualified free-movement contexts. Treat stage entry, act loads and respawns
as lifecycle boundaries, not opportunities to force an effect through.

Current gravity, head-size and pacing policies multiply contributions with
clamps, while stealth flags combine. Allowing several variants of the same
family produces surprising compounded values, so Chaos admits only one.
Availability must consider effective policies from other owners as well:
for example, another mod's No Rings makes Ring Confetti ineligible. Expiry
removes the Chaos contribution; it never resets another mod's setting or
restores an old snapshot of the world.

Ring Confetti additionally requires the effective composed Ringfall policy
to allow full-inventory scatter at 100% with no optional cap. Other owners'
Ringfall contributions can reduce that percentage, impose a cap or retain
the native ceiling. Skip this effect when the full behavior cannot be
admitted; do not silently promise full scatter while applying a reduced one.

## Proposed catalogue of 30 effects

Durations are base active-play seconds before the multiplier. **Reuse** means
the current semantic policy exists, but automatic scheduling still needs
the proposed observer/lease API. **New** means an additional host policy,
consumer or contract is required. Random parameters are chosen once per
activation, except where a captured envelope or pulse sequence is the effect.

### Visual changes

All visual effects preserve collision, targeting and gameplay coordinates.
Head effects use reviewed ROM anatomy masks and native pose fallbacks.

| ID | Effect | Behavior | Base | Severity | Support |
| --- | --- | --- | --- | --- | --- |
| 01 `bobble-head` | Bobble Head | Pick a 150–200% head size and a supported leader/team target. | 20 s | Ordinary | Reuse `BigHead` |
| 02 `pulse-head` | Throbbing Heads | Smoothly pulse reviewed head pixels between normal and a sampled maximum. | 20 s | Ordinary | New captured envelope over the head policy |
| 03 `invisible-player` | Where Did Sonic Go? | Hide leader/team body and appendage; retain collision, HUD and sound. | 10 s | Ordinary | Reuse `PlayerStealth`; attached effects remain visible |
| 04 `rainbow-player` | Disco Sonic | Cycle the playable sprite's presentation colours smoothly. | 20 s | Ordinary | New presentation tint; preserve native palette ownership |
| 05 `negative-world` | Negative Zone | Invert gameplay colours while the HUD and Chaos UI remain readable. | 15 s | Ordinary | New scene colour transform |
| 06 `mirror-view` | Mirror Universe | Mirror the gameplay image horizontally; controls retain their normal meaning. | 12 s | Disruptive | New scene transform; no input remap |
| 07 `upside-down-view` | Australian Sonic | Turn the gameplay image upside down without changing gravity. | 8 s | Disruptive | New scene transform |
| 08 `quake-camera` | Tiny Earthquake | Add bounded camera shake without moving the collision world. | 10 s | Ordinary | New presentation-only camera offset |
| 09 `zoom-pulse` | Breathing Camera | Gently zoom in/out about the player without changing viewport simulation or object admission. | 15 s | Ordinary | New scene transform; cap zoom and avoid clipped UI |
| 10 `afterimages` | Too Many Sonics | Draw fading recent sprite poses behind the player. | 20 s | Ordinary | New bounded presentation trail |

### Physics changes

| ID | Effect | Behavior | Base | Severity | Support |
| --- | --- | --- | --- | --- | --- |
| 11 `moon-gravity` | Moon Gravity | Sample dry Sonic gravity at 25–60%; longer float, with native jump impulse and other movement owners. | 15 s | Ordinary | Reuse `DrySonicGravity`; dry native Sonic only |
| 12 `heavy-gravity` | Lead Shoes | Sample dry Sonic gravity at 150–200%, making ordinary falls heavier. | 10 s | Ordinary | Reuse `DrySonicGravity`; dry native Sonic only |
| 13 `spring-legs` | Spring Legs | Increase normal jump impulse within a bounded range; preserve shield/character abilities. | 12 s | Ordinary | New semantic jump policy |
| 14 `ice-shoes` | Ice Shoes | Reduce ordinary ground friction for slippery braking. | 15 s | Ordinary | New traction policy |
| 15 `sticky-shoes` | Sticky Shoes | Increase ground friction and braking; retain enough movement to traverse the route. | 10 s | Ordinary | New traction policy |
| 16 `fast-forward` | Fast Forward | Sample 150–200% native gameplay pacing; optional audio follows. | 10 s | Ordinary | Reuse `GameplaySpeed` |
| 17 `slow-motion` | Slow Motion | Sample 50–75% native pacing for a brief helpful breather. | 15 s | Ordinary | Reuse `GameplaySpeed` |
| 18 `reverse-controls` | Wrong Way! | Swap left/right at the gameplay input boundary; menus stay normal. | 6 s | Disruptive | New semantic input remap |
| 19 `bunny-hop` | Can't Stop Hopping | Request a fresh ordinary jump on eligible landings, with captured input edges. | 8 s | Disruptive | New native jump/input policy |
| 20 `ring-confetti` | Ring Confetti | Hurt spills during the effect make the full carried inventory recoverable instead of only the native capped scatter. It still loses the full inventory. | 15 s | Ordinary | Reuse `Ringfall(100, 0, true)`; effective-policy and resource admission |
| 21 `badnik-pogo` | Badnik Pogo | Sample 150–250% native badnik-defeat rebound, with an 8–12 px/tick cap. | 15 s | Ordinary | Reuse `DefeatKnockback`; no arbitrary launch or boss damage |
| 22 `wind-gust` | Surprise Gust | Add a mild, capped horizontal force with sampled direction; respect transport and scripted movement. | 8 s | Disruptive | New movement-force policy |
| 23 `gravity-hiccup` | Gravity Hiccup | Briefly reverse gravity once, then remove the reversal contribution. | 2 s; max 3 s | Disruptive | New reversible gravity owner; safe contexts only |
| 24 `gravity-roulette` | Gravity Roulette | Play a captured short sequence of normal/reversed gravity pulses. | 4 s; max 5 s | Major | New gravity pulse policy; starts unselected |

The current gravity policy accepts only positive 25–200% dry Sonic gravity;
it does not provide zero/reversed gravity, underwater gravity, Tails flight
or Knuckles glide behavior. Gravity Hiccup is therefore a separate capability,
not a negative random configuration for the existing policy.

### Displacement

Displacement uses a proposed host-owned relocation operation. Raw
`NativePositionOps` coordinate writes do not by themselves reconcile
attachments, sensors, camera locks, teammates and scripted state.
`CourseControl.restore` instead restores a compatible course snapshot,
including world/player/camera state, within the controlled gameplay path.
It rolls back the world and cannot implement the proposed relocation while
preserving rings, time and current world state.

| ID | Effect | Behavior | Base | Severity | Support |
| --- | --- | --- | --- | --- | --- |
| 25 `checkpoint-roulette` | Starpost Roulette | Relocate to a random currently valid checkpoint destination in this act. | Instant | Major | New admitted destination catalogue and relocation |
| 26 `last-starpost` | Back to the Starpost | Relocate to the last activated post without resetting rings, time or world state. | Instant | Major | New relocation; skip if no post or already nearby |
| 27 `blink-step` | Blink and You'll Miss It | Relocate a short distance left/right to a host-validated nearby landing. | Instant | Disruptive | New collision-aware local relocation |
| 28 `buddy-swap` | Trading Places | Atomically exchange nearby eligible leader/sidekick positions. | Instant | Disruptive | New team relocation; skip solo, distant or attached teammates |
| 29 `rubber-band` | Rubber Band | Mark a safe point, then try to return there after a brief delay. | 6 s; max 8 s | Disruptive | New captured delayed relocation; revalidate on return |
| 30 `glitch-step` | Glitch Step | Perform occasional small, validated position skips with a strict travel budget; retain collision. | 4 s; max 5 s | Major | New bounded relocation sequence; starts unselected |

Starpost Roulette initially draws from encountered valid destinations.
Unvisited checkpoints can join the pool only when act/route qualification
proves that reaching them preserves the current terrain/event/camera state.
Do not choose arbitrary coordinates or assume every layout starpost is safe.
Relocation preserves the checkpoint bank unless an effect explicitly requests
otherwise. Instant actions are not undone at duration expiry. Rubber Band's
return is a second admitted action, not a world restore; cancel it if the
level visit changes or its destination becomes invalid.

## Lifecycle, ownership and rewind

Normal expiry recomputes the current policy from remaining contributions.
Release transient leases directly on mod disable, owner quarantine, death,
respawn, full restart, act/level changes or leaving ordinary gameplay. Start a
fresh interval after the five-second stable-spawn grace. Special/bonus stages
retain native behavior and return through the same admission/grace rule.
An in-place menu Resume preserves the session; a new world generation does
not inherit old activation IDs or pending relocation commands.

Capture RNG state, active clock/fraction, countdown, admitted settings
revision, active IDs/configurations/expiry, pulse/trail state, severity and
repeat cooldowns, pending one-shots and delayed-return destination identity.
Visual trails reset coherently on relocation and load. Host leases restore
with the world before consumers/player recreation. The host must revoke
faulted-owner leases even if the creator's cleanup callback throws.

Durable menu preferences do not rewind. Historical admitted settings do:
rewinding across an activation restores the original sampled configuration
and expiry. Store accepted external decisions and menu revisions in a
separate bounded outcome journal tied to the retained rewind horizon, with
explicit trimming/backpressure. Replaying a retained future uses the accepted
outcome without repolling the network or rerunning a creator factory. A new
decision after rewind creates a new branch. One-shot activation identities
prevent double application on a service boundary while still allowing the
action to replay when rewinding before it.

## Future streamer mode

Put a `DecisionSource` boundary between candidate generation and effect
activation. Local weighted RNG supplies version one. Later, voting receives
three eligible candidates plus Mystery from the same selected pool and
severity gate. Mystery resolves only within that round's admitted tier or a
lower tier. Viewers cannot bypass rarity, compatibility or host safety by
voting repeatedly for a teleport.

Expose candidate names, round ID, deadline, vote counts, active effects and
countdown to an OBS browser overlay. Support majority and proportional voting;
the host selects a tie/fallback using captured RNG. One latest vote per viewer
per round prevents message spam becoming additional votes. Reject stale round
IDs and revalidate the winner at admission; use the recorded fallback if its
prerequisites changed. Keep the countdown/deadline on the same active-play
clock, so pausing does not consume a voting round.

A local external bridge is a reasonable first streaming integration: it owns
platform authentication and chat I/O, while a bounded session inbox accepts
validated outcomes on the engine thread. Existing scene TCP/race services do
not provide a general Twitch/YouTube client or persistent gameplay inbox.
No transport or platform integration is selected or implemented in this pass.
Disconnect/no votes falls back to the local selector. Record the admitted
round outcome for rewind; do not replay live network side effects. Future
competitive/multiplayer runs need explicit shared-seed/authority rules and
must not assume local-only Chaos is compatible with stock racing protocols.

## Suggested implementation order and acceptance checks

1. Add the stock-preserving observer, temporary lease contract and rewind/fault
   cleanup. Build the Chaos menu and HUD. Start with the eight catalogue rows
   marked Reuse, with real support/eligibility explanations; the complete
   proposed catalogue must not appear to be delivered at this stage.
2. Add presentation/input/traction/jump/force policies in bounded groups,
   using ROM-backed native assets and existing renderer/service boundaries.
   Qualify gravity reversal separately from dry gravity scaling.
3. Add coherent relocation and per-act destination qualification, then the
   six displacement effects. Add voting only after local outcomes and rewind
   are deterministic.

Focused checks should cover weighted selection with small/empty pools,
no upwards severity fallback, sampled bounds, no-repeat reduction, lease
expiry/composition, pause/zero-tick/multi-tick timing, owner faults and teardown.
Rewind across activation, expiry, settings edits, one-shots and delayed return;
verify matching forward replay and timeline isolation on loads. Compare native
S3&K routes with Chaos disabled to detect changes in ordinary owners.

Create an effect/support matrix across Sonic, Tails, Knuckles and native
teams; record unsupported donor/character/stage combinations. Qualify ordinary
routes and bosses, water, scripted transport, death/respawn and terrain changes.
For each certified displacement destination, check camera/sensor/team state,
capture/restore and replay in the current event/terrain state. Link affected
act-route obligations from the existing
[coverage backlog](../../status/level-test-coverage.md), following the
[level-test standard](../../guide/contributing/level-test-standard.md).

Use focused regressions plus the repository's change-based smoke selection
for actual shared/API implementation. S3K loading/bootstrap/decoding and AIZ1
guards remain required. Interactive Game Speed needs separate owned-display
pacing evidence: canonical trace/capture ticks do not qualify its wall-time
presentation. No engine, ROM, route or audiovisual tests were run for this
documentation-only research pass.
