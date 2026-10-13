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

The [top 50 additional candidates](../research/2026-10-13-chaos-effect-shortlist.md)
rank further adaptations from Zolika's other published catalogues. They remain
a separate shortlist; the 55-effect pool and its stable IDs below are unchanged.

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

The catalogue also lists DVD Screensaver, Gravity Field, Forcefield and
Magnet Cars. These suggest another source of Sonic effects: moving native HUD
readouts, attracting or repelling pickups/enemies/projectiles, and temporary
encounters assembled from the game's existing objects and badniks. Motion
must have an explicit owner; a visual distortion and an actual force field
need different contracts even when they look similar.

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

The HUD/object/native-encounter expansion was researched at
`bd06ece222404acd081858730bab5310e10331a7`; that preceding commit changed only
the design and its index. Native routines and current engine behavior remain
the basis for reuse, with host additions explicitly proposed below.

- [ModContext](../../../src/main/java/com/openggf/mods/code/ModContext.java)
  registers creator-owned mutator definitions transactionally, with a limit
  of 32 per owner. The expanded pool has 55 independently selectable effect
  IDs, mapped to 32 parameterized backing definitions below. The cap counts
  definitions, not custom menu entries. Reuse policy constructors under
  Chaos's ownership; do not depend on another installed example mod's IDs.
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

1. **Chaos catalogue:** 55 stable effect IDs mapped to 32 Chaos-owned mutator
   definitions. Several choices can use one definition with different modes
   or configurations; they remain independently selectable in the pool.
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

### Effect identity and backing definitions

Keep the catalogue effect ID, registered definition key and activation ID
distinct. For example, DVD HUD and Orbiting HUD share `hud-motion` but have
different catalogue IDs and mode parameters. They cannot overlap. Preferences
store the selected effect-ID set outside the definition option schema;
55 selection booleans would exceed one definition's 32-option limit.

The proposed families below total 32 definitions and fit the unchanged
32-owner cap. Row numbers refer to catalogue IDs, which remain stable
when new entries are added. These groupings do not make future policies
available today.

| Backing definition | Catalogue rows |
| --- | --- |
| `head-scale` | 01 |
| `head-envelope` | 02 |
| `player-stealth` | 03 |
| `player-tint` | 04 |
| `scene-colour` | 05 |
| `scene-transform` | 06, 07, 09 |
| `camera-shake` | 08 |
| `sprite-trail` | 10 |
| `dry-gravity` | 11, 12 |
| `jump-impulse` | 13 |
| `ground-traction` | 14, 15 |
| `game-speed` | 16, 17 |
| `input-remap` | 18 |
| `landing-jump` | 19 |
| `hurt-scatter` | 20 |
| `defeat-rebound` | 21 |
| `movement-force` | 22 |
| `gravity-pulse` | 23, 24 |
| `checkpoint-relocation` | 25, 26 |
| `local-relocation` | 27, 29, 30 |
| `team-relocation` | 28 |
| `hud-motion` | 31, 32, 33, 35 |
| `hud-scale` | 34 |
| `hud-visibility` | 36 |
| `object-presentation` | 37, 38, 39, 40 |
| `object-field` | 41, 42, 43, 44, 45, 46, 47 |
| `spring-response` | 48, 49 |
| `reward-ring-emitter` | 50 |
| `bumper-encounter` | 51, 52 |
| `badnik-fire-cadence` | 53 |
| `ghost-encounter` | 54 |
| `native-gust` | 55 |

Each definition has a fixed capability set, and its factory must always
return exactly one policy for each declared capability. Keep current
`BigHead` separate from a future envelope policy rather than switching
capability types by mode. Each proposed multi-mode family uses one consistent
policy kind, with at most 16 mode choices. `object-field` jointly validates
target and mode: ring orbit is valid, but badnik/projectile orbit is not
implicitly enabled by the same schema. The current Choice schema alone
cannot express that cross-field restriction.

Admission enforces one lease per backing family, as well as declared
cross-family conflicts. Existing definition conflicts cannot name the
definition itself and cannot distinguish its catalogue variants. Capture
all three identities and the sampled configuration for rewind.

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
| Selected effects | Balanced preset selects 52 effects; Gravity Roulette, Glitch Step and Bullet Magnet start unselected. Add All is an explicit player choice. |
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
teleports and native-sequence triggers have no globally scaled duration;
the multiplier does not repeat them, stretch native phases or increase their
probability. Fast Forward changes interactive tick pacing,
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

## Proposed catalogue of 55 effects

Durations are base active-play seconds before the multiplier. **Reuse** means
the current semantic policy exists, but automatic scheduling still needs
the proposed observer/lease API. **New** means an additional host policy,
consumer or contract is required. Random parameters are chosen once per
activation, except where a captured envelope or pulse sequence is the effect.
**Native reuse** means an existing object or motion owner supplies the
behavior, but it still needs proposed Chaos admission, configuration and
lifecycle wiring. It does not mean a current public policy already supports
the effect. The original 30 IDs are retained; rows 31–55 expand the pool.

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
| 31 `dvd-hud` | DVD HUD | Reflow native readouts into a compact plaque that bounces off screen edges like a DVD logo. | 20 s | Ordinary | New captured HUD-motion policy |
| 32 `hud-orbit` | Orbiting HUD | Move the compact HUD plaque in a small orbit around the player's screen position, clamped onscreen. | 15 s | Ordinary | New HUD-motion mode |
| 33 `shy-hud` | Shy HUD | Make the plaque drift away when the player approaches it onscreen; cap speed and reflect at edges. | 15 s | Ordinary | New HUD-motion mode |
| 34 `hud-scale` | Breathing HUD | Pulse the native plaque between 75% and 125% scale without changing any counter value. | 15 s | Ordinary | New HUD-scale policy |
| 35 `hud-shuffle` | HUD Musical Chairs | Shuffle the readout positions once, keeping each label attached to its matching value. | 15 s | Ordinary | New HUD-motion mode |
| 36 `hud-hide` | Where Are My Rings? | Briefly hide stock readouts; the Chaos countdown and menus remain visible. | 5 s; max 6 s | Disruptive | New HUD-visibility policy |
| 37 `giant-badniks` | Big Trouble | Enlarge eligible badnik bodies visually, keeping native collision and AI. | 12 s | Ordinary | New whole-object presentation; not Big Head reuse |
| 38 `tiny-badniks` | Pocket Badniks | Shrink eligible badnik bodies visually to 75% size while retaining native collision. | 12 s | Ordinary | New whole-object presentation |
| 39 `spinning-badniks` | Badnik Pinwheels | Slowly spin eligible badnik artwork around its producer anchor; gameplay motion stays native. | 10 s | Ordinary | New whole-object presentation |
| 40 `jelly-objects` | Jelly Objects | Gently squash and stretch eligible independent badniks and pickups without moving collision geometry. | 12 s | Ordinary | New whole-object presentation |

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
| 41 `ring-magnet` | Ring Magnet | Attract eligible nearby rings toward the leader without replacing the current shield. | 15 s | Ordinary | Native reuse: lightning attraction owner plus new field policy |
| 42 `ring-repulsion` | Get Away, Rings! | Apply a mild outward force to nearby eligible rings; inventory and normal pickup rules stay intact. | 8 s | Disruptive | New field mode using native dynamic-ring ownership |
| 43 `badnik-magnet` | Badnik Magnet | Gently pull a bounded set of qualified free-moving badniks toward the player. | 6 s | Disruptive | New field mode and per-object motion adapters |
| 44 `badnik-repulsion` | Personal Space | Push qualified nearby moving badniks away; do not relocate anchored enemies or terrain. | 6 s | Disruptive | New field mode and per-object motion adapters |
| 45 `ring-orbit` | Ring Satellites | Gather a bounded set of eligible rings into a small orbit; native touch collection still works. | 15 s | Ordinary | New field mode with captured ring phase/motion |
| 46 `projectile-magnet` | Bullet Magnet | Curve a few eligible damaging projectiles toward the player with strictly capped force. | 3 s; max 4 s | Major | New field mode; starts unselected |
| 47 `projectile-repulsion` | Bullet Repellent | Nudge eligible projectiles away, preserving their native collision and shield-deflection state. | 6 s | Disruptive | New field mode and independent-projectile adapters |
| 48 `super-springs` | Super Springs | Boost qualified spring launches to 110–135%, respecting native directional caps. | 12 s | Disruptive | Native reuse: spring launch owner plus new response policy |
| 49 `gentle-springs` | Sleepy Springs | Reduce qualified spring launches to 75–90%, respecting required-route minima. | 8 s | Disruptive | Native reuse: spring launch owner plus new response policy |
| 50 `ring-rain` | It's Raining Rings | Emit a small shower of real, short-lived ROM-backed ring pickups near the viewport. | 10 s | Ordinary | New reward emitter; do not invoke native hurt-ring spill |
| 53 `projectile-overdrive` | Badnik Overdrive | Qualified firing badniks reload faster and emit their own native projectiles under a shared shot budget. | 8 s | Disruptive | Native reuse: firing routines plus new cadence/admission policy |
| 54 `soz-ghost-party` | Sandopolis Ghost Party | Temporarily admit a small SOZ2 ghost encounter with its native art/AI and captured encounter-local state. | 12 s | Disruptive | Native reuse: Hyudoro behavior plus isolated ghost context/art loading |
| 55 `windy-day` | Windy Day | Trigger one native Cluckoid pressure burst from a captured fixed source, with optional bounded native wind debris. | Instant trigger; native burst | Major | Native reuse: shared Cluckoid pressure consumer and isolated gust/debris owner |

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
| 51 `bumper-ahead` | Bumper Ahead! | Spawn a native CNZ bumper just ahead within contact range, so normal collision pushes the leader away from it immediately. | Instant | Disruptive | Native reuse: bumper collision/launch; captured one-use encounter |
| 52 `bumper-behind` | Rear Bumper! | Spawn the same bumper just behind within contact range for an immediate native bump in the opposite direction. | Instant | Disruptive | Native reuse: shared bumper family with a different placement mode |

Starpost Roulette initially draws from encountered valid destinations.
Unvisited checkpoints can join the pool only when act/route qualification
proves that reaching them preserves the current terrain/event/camera state.
Do not choose arbitrary coordinates or assume every layout starpost is safe.
Relocation preserves the checkpoint bank unless an effect explicitly requests
otherwise. Instant actions are not undone at duration expiry. Rubber Band's
return is a second admitted action, not a world restore; cancel it if the
level visit changes or its destination becomes invalid.

## HUD, object fields and native encounters

### HUD and object presentation

The existing [HudProfile](../../../src/main/java/com/openggf/level/objects/HudProfile.java)
is immutable and installed at load for owned zones; `registerHudProfile`
does not provide live stock-act motion. Add a semantic HUD-presentation
consumer to both the ordinary HUD and VBlank counter preparation in
[LevelRenderer](../../../src/main/java/com/openggf/level/LevelRenderer.java).
Labels and values share the same transform, with native art, palettes,
counter updates and warning flashes. Moving the HUD must not change time,
rings or lives, or affect gameplay input/camera coordinates.

The stock HUD spans almost the full 224-pixel height, leaving little room
for vertical DVD motion. Reflow all four readouts into a compact native-art
plaque first. Measure its bounds before movement; reflect its rectangle
inside the logical viewport at 1–2 pixels per active-presentation tick.
Capture position, velocity and phase. Orbit uses a 16–32-pixel radius;
Shy HUD uses capped drift away from the player's screen position. Keep the
Chaos status and pause menu stationary. Rewind restores the historical HUD
position rather than restarting the animation.

Whole-object transforms apply after native admission, around an explicit
producer anchor. Start with at most eight qualified independent visible
badnik bodies/pickups, scale 75–150%, and one rotation per four seconds.
Exclude solids, platforms, terrain, gates, transport, boss parts and ambiguous
multipart bodies. Preserve object-load windows, priorities, touch flags,
collision and AI. Reject overlapping whole-body/head transforms on the same
subject unless their composed presentation has been qualified.

### Object fields and ring ownership

A shared field policy declares a target class, mode, radius, acceleration,
velocity cap and affected-object budget. Qualified motion owners consume
the intent before their own integration; the controller does not loop over
all objects and overwrite coordinates. Grounded actors require terrain-aware
adapters. Anchored badniks, parent-constrained children, platforms, bosses
and scripted actors are ineligible until separately qualified.

Use the native [RingManager](../../../src/main/java/com/openggf/level/rings/RingManager.java)
attraction machinery for Ring Magnet without granting or replacing a shield.
Preserve its 32-attracted-ring ceiling, single pickup identity and captured
dynamic motion. Promotion consumes the original placement identity before
collection. At expiry, remove the field and retain current position/velocity;
coalesce with genuine lightning attraction or transfer to the existing
bouncing-ring owner. Never restore the original placed ring as a duplicate.
Repel and Orbit are ineligible while native lightning attraction owns those
rings unless a precedence rule is implemented. Allocation failure skips
Chaos promotion rather than automatically awarding a ring. No Rings and
native pickup eligibility still apply.

Badnik/projectile fields initially use a 96–128-pixel radius, at most four
badniks or eight independent projectiles, acceleration no greater than
`$10` in native 8.8 units and added velocity no greater than 1 pixel per
native tick. Attraction must not reactivate damage on a shield-deflected
projectile. Capture affected identities and resolve them after recreation;
expiry removes the force rather than resetting positions. Bullet Magnet
uses the Major tier because converging damaging shots is substantially more
dangerous than attracting pickups.

Strong/weak springs modify a qualified native launch site, preserving its
direction, contact admission, nudges, control locks, plane changes and caps.
Required-route minima override the sampled weak multiplier. Expiry affects
future contacts, not a player already launched.

Ring Rain needs a distinct reward-pickup emitter. Native `spawnLostRings`
also changes held-ring/loss state and shared spill animation, so it is not
a reward-rain helper. Propose two rings per active-play second, at most 16
live and 24 total per activation, three-second individual lifetimes and
viewport-local validated positions. Reserve native gameplay slot capacity;
if admission fails, skip emission. Expiry stops the emitter; existing pickups
finish their bounded lifetime.

### Temporary native encounters

Reuse native behavior with explicit ROM-backed encounter asset preparation,
injected services, captured state and host-owned lifetime/cleanup. An object
class existing in the engine does not establish that its art or required
zone services are available in another act. Admit the complete prepared
encounter atomically before scheduling it; loading assets during a hit or
silently drawing a substitute is not acceptable.

Ahead/behind bumpers share one exclusive definition. Place the bumper inside
the native player-contact envelope and admit it before the relevant collision
pass; its normal object slot consumes the touch and applies the launch.
The host must publish the new root into the response list consumed by the
first eligible player touch pass: S3K normally uses the previous list, so
construction alone does not establish an immediate hit. If admission misses
that boundary, defer the whole encounter to the next eligible pass.
Do not directly set player velocity to imitate the hit. Treat the encounter
as one-shot, with at most two live bumpers and a two-second fallback lifetime;
capture its fired state so restoration cannot generate a second hit. Defer
the effect during death, scripted attachments or incompatible geometry.

[CnzBumperObjectInstance](../../../src/main/java/com/openggf/game/sonic3k/objects/CnzBumperObjectInstance.java)
implements ROM `Obj_Bumper` / `sub_32F56`. Its touch box has 8-pixel X/Y
half-extents; normal Sonic contributes 8 pixels in X, so placement must be
strictly inside the combined envelope rather than some arbitrary distance
ahead. Stationary subtype zero is appropriate. The native response replaces
X/Y velocity with a radial `$700` launch and performs its own state, sound,
animation and score changes; Ahead pushes backwards and Behind pushes
forwards. Those consequences are part of reuse, not a generic force impulse.

Badnik Overdrive changes qualified firing reload/cadence only. It must not
run the entire badnik update multiple times, advance unrelated movement or
animation clocks, or recreate projectiles with hand-fitted parameters. Reuse
the owning spawn routine, origin, velocity, collision, sound and child state.
Propose a 2–3× firing rate, at most eight Chaos extra shots per producer and
24 across the encounter, with excess emissions skipped. Capture counters,
producer/child identities and origin ownership. Stop extra emissions on
expiry; remove only Chaos-owned extra children through their normal lifecycle,
preserving unrelated native projectiles and shield deflection.

Initial cadence adapters should target
[Bloominator](../../../src/main/java/com/openggf/game/sonic3k/objects/badniks/BloominatorBadnikInstance.java)
and the
[EggRobo gun arm](../../../src/main/java/com/openggf/game/sonic3k/objects/badniks/EggRoboGunArmChildInstance.java).
Bloominator separates its idle firing wait from native animation-frame
shot emission; the gun arm has an explicit `$5F` cooldown. Shorten qualified
waits while retaining their respective emission sequences. EggRobo's shot
still gets its native harmless muzzle-charge phase. Blastoid and other
animation/child-gated shooters need separate adapters; Cluckoid's wind is
not a generic projectile-spam candidate.

SOZ2 ghosts need an encounter-local captured darkness/escalation context;
raw ghost/controller spawning outside SOZ2 cannot assume the native zone's
lighting services or unlock gates. Reuse Hyudoro art and AI through an
explicit semantic context, retaining the native SOZ2 context as the default
when Chaos is disabled. Do not alter another act's terrain or impersonate
the real SOZ lighting owner. Start with at most two ghosts, a short warning
appearance before damaging behavior, and a bounded disappear phase at expiry.
Qualify cross-act services and palette/art residency before adding a route
to eligibility; an ordinary ghost effect must not depend on a hidden SOZ2
checkpoint or Knuckles-only state.

[SozHyudoroBodyObjectInstance](../../../src/main/java/com/openggf/game/sonic3k/objects/SozHyudoroBodyObjectInstance.java)
already owns native appearance, bobbing, darkness response, edge wind-up,
world-space attack, contact and fading. It currently depends on a concrete
[SozHyudoroControllerObjectInstance](../../../src/main/java/com/openggf/game/sonic3k/objects/SozHyudoroControllerObjectInstance.java)
whose darkness comes from the SOZ runtime. Replace that narrow dependency
with a semantic darkness/lifecycle context implemented by the native
controller and the encounter owner. Keep the native controller's checkpoint/
Knuckles gates for actual SOZ play. The encounter supplies its own progression;
darkness level five reaches the native attack route with a 60-dispatch
wind-up. This is edge-entry behavior, not continuously homing ghosts.
Use an object-identity-compatible context interface or explicit rewind codec:
changing the declared field type must not silently lose captured references.

Cross-act encounter art needs explicit descriptors and renderer readiness.
The bumper uses CNZ level-art tiles and palette line two; reading those tile
numbers from another act would select unrelated art. Ghosts have a ROM-backed
standalone descriptor in the SOZ2 plan, but the existing
[standalone loader](../../../src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtProvider.java)
searches the current act plan. Prepare the original source sheet/mappings/
DPLC and intended object-scoped palette view without reloading another
zone's whole PLC or overwriting the active act's palette. Constructor and
`spawnChild` allocation order do not provide descendant lifetime ownership;
the encounter must track and retire its exact graph.

### Windy Day and the native Cluckoid gust

Windy Day deliberately differs from Surprise Gust's mild force policy.
[CluckoidBadnikInstance.applyWindPressure](../../../src/main/java/com/openggf/game/sonic3k/objects/badniks/CluckoidBadnikInstance.java)
implements ROM `sub_8E388`: it adds 1–16 whole pixels directly to native
player X while preserving X subpixel, rather than changing velocity, input
or Y. Strength is `16 - floor(abs(dx)/16)`, with strict `abs(dx) < 256`,
`abs(dy) < 64`, facing-side and duck/spindash/object-control gates. Its
potential to push a player across a platform makes this a Major effect,
even though its category is Physics Changes.

Extract that pressure consumer once for both the real Cluckoid and an
injected, captured NativeGust owner. Do not copy its calculation, spawn an
invisible bird, simulate a projectile, repeatedly run the full badnik update
or substitute a player-velocity setter. The real Cluckoid retains native
detection, animation and cooldown behavior when Chaos is disabled.

Choose direction at admission and fix the gust source 128 pixels behind
the activation-time leader in that wind direction, at the same centre Y.
The initial native pressure is eight pixels and naturally attenuates as
the player moves; do not reposition the source to chase them. Preserve the
native frame-7-through-D pressure phase of 70 object dispatches (approximately
1.17 seconds at normal 60-step pacing), with at least 97 refractory dispatches
and one live Chaos gust. This is an instant trigger of a captured native
sequence, so the duration multiplier does not extend or repeat it. Hold its
encounter slot until the native sequence completes or lifecycle cleanup
cancels it. Host death/transition admission and cleanup still apply; the
pressure routine itself has no death gate.

Optional debris reuses the native harmless leaf/pollen behavior and reads
`V_int_run_count & 7`, not the Chaos clock. Bound it to nine live children,
with encounter-owned expiry, while recording phase, emission gates and the
sound latch. The MHZ leaf/pollen sheets need explicit ROM-backed cross-act
art preparation. Debris deletion currently releases MHZ pollen accounting,
so give encounter debris an isolated lifetime/counter context rather than
decrementing the active zone's count. Bird/arrow art and the concrete
Cluckoid parent graph are unnecessary. Expiry removes only this gust and
its descendants; it does not reset player position or motion.

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
Also capture HUD transforms, promoted-ring ownership, affected object IDs,
encounter emitter/firing counters, extra-child ownership, ghost-context state,
and gust source/facing/dispatch counters/debris context. All temporary objects
need recreation plus captured state; cleanup
must not indiscriminately delete the native object class they reuse.
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
   proposed catalogue must not appear to be delivered at this stage. Keep
   effect IDs separate from the 32 backing definition keys from the start.
2. Add HUD, object presentation, input/traction/jump/field policies and
   bounded native encounter packs in independent groups,
   using ROM-backed native assets and existing renderer/service boundaries.
   Qualify gravity reversal separately from dry gravity scaling.
3. Add coherent relocation and per-act destination qualification, then the
   relocation-based displacement effects. Immediate bumper encounters instead
   retain native collision/launch ownership. Add voting only after local outcomes and rewind
   are deterministic.

Focused checks should cover weighted selection with small/empty pools,
no upwards severity fallback, sampled bounds, no-repeat reduction, lease
expiry/composition, pause/zero-tick/multi-tick timing, owner faults and teardown.
Check HUD bounds/label-value pairing on supported viewports, ring promotion
without duplicate pickup identities, native shield/field precedence, object
opt-in and slot budgets, native bumper touch ordering, firing counters and
ghost behavior/art availability outside SOZ2. Verify native scene/transition
owners retain their normal behavior with Chaos disabled. Check Cluckoid
pressure/subpixel behavior, native dispatch/V-int gates, isolated pollen
accounting and one-shot gust replay.
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
