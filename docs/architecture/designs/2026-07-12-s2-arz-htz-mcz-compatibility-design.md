# Sonic 2 ARZ, HTZ, and MCZ Compatibility Design

## Scope

Audit every implemented Aquatic Ruin, Hill Top, and Mystic Cave act for multi-sidekick, widescreen, and cross-game-donation compatibility. Flying Battery is explicitly excluded. Existing trace-passing native behavior must remain unchanged.

## Participation architecture

Objects with native P1/P2 state retain those two fields and their exact P1-then-P2 processing prefix. Third and later sidekicks use object-local identity-keyed state rather than roster indices. VineSwitch and MovingVine extension state records the grabbed flag and release delay per `PlayableEntity`; HTZ Seesaw extension state records standing ownership per player. Any cross-frame player collection is centrally marked `CAPTURED` and must round-trip through `PlayerRefId` codecs.

Roster omission and reorder never transfer state to another actor. A replacement instance inherits state only through an explicit rewind restore using the same `PlayerRefId`. Death, hurt/debug invalidation where applicable, object unload, and off-screen destruction release only identities owned by that object, clearing forced/object control without touching unrelated players.

Shared mechanics remain main-driven where the ROM has one global outcome. Native P2 behavior is extended to additional sidekicks only after P1 and P2 have executed. Shared vine-trigger writes and seesaw motion occur once per native state transition, not once per extension participant.

## Widescreen architecture

Hard-coded `320`/`224` checks are changed only when they represent the visible render or activation rectangle. Such checks consume the shared live viewport width/height, producing exactly the original arithmetic at 320×224. World-coordinate event thresholds, authored camera locks, object travel limits, and ROM placement windows remain unchanged.

The first known target is Whisp's `Render_Sprites` overlap predicate. Its object radius and one-frame wait/activation ordering remain native; only the screen dimensions become viewport-aware.

## Donation audit

ARZ, HTZ, and MCZ routes will be checked for mandatory spin-dash activation. Existing roll/spring/vine/seesaw traversal is assessed through canonical animation/capability APIs. A workaround is added only if a route is demonstrably blocked for a donor whose effective capabilities lack spin dash; no donor-name, trace-route, or frame carveout is permitted.

## Testing and regression gates

Focused tests cover main plus three sidekicks, native ordering, simultaneous ownership, exact release velocities, death/unload, omission, reorder, runtime replacement, non-empty PlayerRef rewind restoration, and unrelated-control preservation. Widescreen tests pin Whisp activation at both 320 and a wider viewport.

Run both rewind coverage guards, scalar-codec coverage where touched, existing focused vine/seesaw/Whisp tests, and the relevant ARZ/HTZ/MCZ trace replay classes. Record known-red frontiers exactly and reject any earlier frontier or increased divergence caused by this branch.

## Documentation

Add a compatibility audit note describing changed mechanics, world-versus-screen event findings, donation conclusion, trace evidence, and the strict Flying Battery exclusion. Update the changelog for user-visible compatibility improvements.
