# Sonic 2 CPZ, CNZ, OOZ, and MTZ Compatibility Design

Date: 2026-07-13

## Scope

Audit and remediate Chemical Plant, Casino Night, Oil Ocean, and Metropolis for arbitrary configured sidekicks, active widescreen viewports, and cross-game donation. Flying Battery is excluded. Existing native trace behavior is a hard constraint.

## Design decision

Use object-local identity extensions rather than a shared two-slot compatibility framework. Each affected object keeps its ROM-shaped P1/P2 fields and dispatch order. Third-and-later participants use identity-keyed extension state encoded through compact-rewind `PlayerRefId` values. Objects that already scale through generic solid contacts, player queries, or identity ownership receive audit conclusions and regression coverage instead of production changes.

## Slice 1: ForcedSpin

`ForcedSpinObjectInstance` retains `sonicPastTrigger` and `tailsPastTrigger` as the native prefix. It binds those holders to stable player identities and stores crossing state for further sidekicks in an identity map. Initialization, horizontal/vertical crossing, reverse crossing, range checks, and the native-P2 horizontal flight-recovery exception apply independently per participant. Roster reorder must move state with the actor rather than the index. Omitted/dead players lose extension state without transferring it to a replacement. Compact rewind restores all keys through replacement player instances.

ForcedSpin is a status/control mechanic, not a spin-dash actuator. Donation changes presentation through the playable animation profile; it does not require a traversal fallback.

## Slice 2: OOZ popping platforms and pressure springs

`OOZPoppingPlatformObjectInstance` retains its native main/P2 lock holders and native shared movement timing. Extension riders receive independent identity-owned locked state. The wait-to-rise decision considers every standing participant, while the shared launch begins once and launches every captured rider at the apex in main, native-P2, then configured-extension order. Death, omission, unload, reorder, and rewind replacement release or relink the correct identity without changing native two-player timing.

`OOZSpringObjectInstance` is expected to scale through the shared solid-contact callback. The audit will verify that it has no player-owned persistent state or native-only query. If confirmed, only focused main-plus-three coverage is added.

## Slice 3: MTZ tubes, nuts, and long platforms

`MTZSpinTubeObjectInstance` retains its native main and sidekick `CharacterState` holders. Further sidekicks receive identity-keyed `CharacterState` objects and execute after the native prefix. Each participant independently enters, oscillates, follows the path, exits, and cleans up forced control. Roster reorder/omission, death, unload, and compact rewind replacement preserve ownership.

`NutObjectInstance` retains `p1`, `p2`, and their standing/contact latches. Extension players receive independent action/standing state keyed by identity and run after the native P1/P2 actions while the nut's shared position/routine remains single-owner. Activation remains standing-and-running displacement, matching the MTZ nut behavior; no spin-dash capability or donation fallback is required.

`MTZLongPlatformObjectInstance` already uses `MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED` for player proximity and generic solid contacts for riders. The audit will add main-plus-three coverage for proximity and rider behavior only if current coverage is insufficient. World-space stop points and camera/event thresholds remain unchanged.

## CPZ and widescreen audit

`CPZSpinTubeObjectInstance` already owns character state by player identity and an identity-keyed active-owner table. Add or retain coverage proving main plus three independent tube participants, replacement-player rewind restoration, and cleanup. Do not rewrite a green identity path.

Search CPZ/CNZ/OOZ/MTZ event and object code for hardcoded visible-screen activation/culling. Widen only values proven to represent the visible viewport. ROM world coordinates, authored movement extents, camera locks, and native object-range constants remain unchanged. Any widened predicate must be exact at 320 pixels and tested at exposed widths where relevant.

## Rewind and lifecycle contract

- Native owner references and extension maps are compact-captured when they survive a frame.
- Player references restore through `PlayerRefId`, never stale Java identity.
- Final collection fields use rewind-stateful values or supported compact collection codecs.
- Object unload clears only control actually owned by that object.
- Omitted or invalid extension participants cannot leave stale object-control, standing, pinball, or tube state.
- Native P1/P2 quirks visible in green traces are preserved and documented rather than normalized.

## Verification

Follow red-green TDD per slice. Focused tests cover main plus three sidekicks, native ordering, simultaneous interaction, death, unload, omission, reorder, and replacement-player rewind. Run compact scalar deletion, rewind coverage, static-state rewind coverage, graph/lifecycle tests, and relevant CPZ1/2, CNZ1/2, OOZ1/2, and MTZ1/2/3 traces. Trace fixtures remain comparison-only. Record audit conclusions and the no-spin-dash-fallback result in compatibility documentation and the changelog.
