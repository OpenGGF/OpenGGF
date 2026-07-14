# Native-Tails Flappy Mod Design

## Goal

Rebuild `sample-flappy` as the first destination after starting Sonic 3&K. The mod
uses the real visible Tails player and native flight behavior instead of hiding the
player behind a controller-owned bird. The game remains endless and does not need to
transition into Angel Island Zone.

This design consumes the reusable S3K mod-zone adapter specified in
`2026-07-14-s3k-mod-zone-adapter-design.md`.

## Confirmed behavior

- S3K is the base game.
- A fresh no-save or new-slot launch enters Flappy before any stock level.
- Tails is the sole main player for that launch; saved/configured teams are not
  rewritten.
- Tails begins in native flight automatically.
- Jump retains native Tails flight lift, animation, and audio.
- Flight fatigue is disabled only for this zone.
- Player left/right input is ignored.
- The run advances through world space at a fixed speed while the camera keeps Tails
  near a stable horizontal screen position.
- Touching a pipe or leaving the visible playfield through its top or bottom causes
  unconditional death.
- Passing a pipe pair adds one to the ring counter, which is presented as `SCORE`.
- The stock score row is hidden; the life display remains.
- Restart begins a fresh run with score zero and flight active.

## Generic game-start and gameplay contributions

The engine gains small, immutable, owner-tagged policy contributions rather than a
Flappy-specific mode.

### Initial destination

`ModZoneContribution` gains an explicit game-start insertion point. The zone
progression plan represents prepended zones separately from results-driven
`insertAfter` anchors. Fresh no-save starts and genuinely new save slots use the
first enabled prepended destination. Existing saves, clear restarts, level select,
time attack, and explicit direct launches keep their requested destination.

Disabling Flappy restores the stock S3K initial destination. Because this sample is
endless, it defines no results successor.

### Session team selection

A launch-team contribution selects an owner-tagged `CharacterKey` for a matching
destination. Resolution happens before sprite bootstrap and produces a session-local
team. It does not mutate `config.yaml`, the data-select choice, or the save's durable
team. The Flappy policy selects S3K Tails as player one and no sidekicks.

The runtime validates that the requested character exists and is playable for the
resolved module. Failure aborts launch with an owner-attributed diagnostic rather
than silently substituting Sonic.

### Input and movement

A gameplay-scoped input filter receives effective player input and may suppress
controls but cannot mutate raw controller state. Flappy removes left and right and
passes jump unchanged. The filter is installed in `GameplayModeContext` and is absent
from normal S3K sessions.

A separate deterministic movement policy supplies fixed forward progression. It
does not synthesize right input, so facing, acceleration, and flight animation are
not driven by fake controls. Camera tracking uses the normal player focus and lead
behavior to keep Tails at approximately the chosen screen X while objects remain in
world coordinates.

### Initial ability and flight duration

A playable ability policy can initialise an existing character ability and adjust
its duration rules for the current gameplay context. Flappy activates the existing
`TailsFlightController` after spawn and after restart, and marks fatigue as disabled.
The controller still owns vertical acceleration, lift, animation transitions, and
sound. No shared Tails constant is changed, and ordinary S3K levels preserve stock
activation and fatigue.

### HUD presentation

An immutable HUD profile controls row visibility, label art, position, and metric
selection. It cannot change counters. Flappy hides the stock score row, places the
existing S3K `SCORE` label at the rings-row position, and renders the ring counter as
its value. Time and lives remain in their stock locations. The low-rings warning is
disabled because the counter no longer represents collectible-ring health.

The ring counter is run-local Flappy score. There are no collectible ring placements,
and the stock score counter is neither incremented nor persisted for the minigame.

## Player-owned gameplay

The old controller no longer hides, teleports, or substitutes for the playable
sprite. Tails' playable collision bounds are authoritative.

Pipes are namespaced mod objects with rendered upper/lower pieces and a single
scoring gate per pair. A pipe touch requests unconditional playable death, explicitly
independent of rings, shields, invincibility, super state, or ordinary hurt rules.
The gate increments the ring counter once when Tails' centre passes its world X.

A gameplay controller checks Tails' centre against the camera's visible top and
bottom bounds before native top-edge flight clamping can conceal the crossing. A
crossing requests the same unconditional death. Death and restart use the normal
player/level lifecycle; the mod does not implement a parallel life state.

Pipe generation seed/state, active pipe pairs, consumed scoring gates, forward
progression, and the ring-backed score are captured for deterministic rewind. A
backward seek may make an unpassed gate eligible again only if both player position
and gate state rewind to before the crossing.

## Art ordering fix

The distorted pipes are a general converter defect. `ArtConverter` currently emits
the tiles belonging to a multi-tile mapping piece in row-major order, while the
Genesis mapping renderer consumes column-major order. The converter changes to tile
column first, then tile row. `SpritePieceRenderer` remains unchanged because its
ordering matches native ROM mappings.

A non-square converter fixture gives every tile a distinct marker, bakes it, and
reconstructs it through mapping order. This test must fail before the converter fix.
Square-only fixtures are insufficient because a transpose can appear plausible.

## Palette and presentation correctness

The previous black bird/life icon and recoloured HUD were palette-ownership bugs, not
pipe-order symptoms. The S3K adapter supplies the active Tails character palette as
host-owned line 0. The Flappy export declares only the level-line entries used by its
sky and pipes. It does not ship copyrighted character colours or overwrite HUD-owned
entries.

Palette validation rejects reserved writes and indexed pixels that reference an
undeclared entry. Composition order is host character, creator zone ownership, then
HUD ownership for visible HUD entries. Tails and the life icon therefore share the
correct host palette while pipe/background colours cannot recolour UI text.

Integration assertions inspect palette values and indexed pattern/mapping references,
not merely the presence of frames. A deterministic lossless rendered fixture uses
exact pixel probes to verify the visible Tails sprite, life icon, `SCORE` label,
background, and rectangular pipes.

## Fault boundaries and cleanup

All policies register through `ModContext` transactions and use the creator fault
boundary. Required-policy failure aborts the Flappy launch; the engine never starts a
partial version with Sonic, horizontal control, stock fatigue, or the wrong HUD.

Asset geometry, palette ownership, character availability, policy conflicts, and API
version are validated before session publication. Later owners may replace a policy
only where the contract explicitly permits deterministic decoration; exclusive
conflicts produce findings. Gameplay-context teardown removes every installed policy.

## Compatibility and non-goals

The public policies use recursive `@ModApi` immutable types and injected services.
Together with the S3K adapter they advance the creator API from 2.2.0 to 2.3.0. The
sample declares `>=2.3.0 <3.0.0` and is rebuilt only against the matching SDK.

This work does not add a mod GUI, a general HUD editor, arbitrary physics scripting,
stock S3K event emulation, a Flappy completion sequence, or persistence of high
scores. It does not modify native Tails physics outside the resolved Flappy context.

## Verification

Test-first delivery covers:

1. fresh-start insertion and stock fallback when the owner is disabled;
2. session-local Tails selection without configuration/save mutation;
3. input filtering that suppresses left/right and preserves jump;
4. deterministic fixed forward progression and camera tracking;
5. automatic native flight, preserved lift/animation/audio hooks, and no fatigue;
6. stock Tails activation and fatigue in ordinary S3K levels;
7. HUD row visibility and ring-counter-to-`SCORE` mapping;
8. exactly-once scoring and reset behavior;
9. unconditional death from pipes and both visible vertical boundaries;
10. rewind restoration of progression, pipes, scoring gates, and score;
11. column-major non-square art conversion and unchanged native ROM rendering;
12. host character/HUD palette ownership and hostile palette rejection;
13. rendered pixel probes for Tails, life icon, HUD, background, and pipe pieces;
14. sample packaging, zero-finding validation, API compatibility guards, and the
    relevant S3K/mod regression suites.

Delivery rebuilds both engine artifacts, builds `sample-flappy-mod.jar` against the
new SDK, validates it, and places it in `mods/` for manual play testing. Generated
artifacts are not committed.
