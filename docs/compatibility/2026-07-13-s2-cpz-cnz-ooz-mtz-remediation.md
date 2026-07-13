# Sonic 2 CPZ, CNZ, OOZ, and MTZ compatibility remediation

Date: 2026-07-13

Scope: Chemical Plant Zone, Casino Night Zone, Oil Ocean Zone, and Metropolis Zone. Flying Battery Zone is explicitly excluded because it is under separate ownership.

## Result

| Area | Multi-sidekick | Widescreen | Cross-game donation |
| --- | --- | --- | --- |
| CPZ spin tubes and events | `CPZSpinTubeObjectInstance` already processes `ALL_ENGINE_PLAYERS` and stores tube state in an identity-keyed map. Compact rewind uses PlayerRefs, so replacement sprites do not inherit stale Java identities. | CPZ event thresholds `$2680` and `$2A20` are ROM-authored world camera positions. The boss lock remains fixed to the arena and must not expand with viewport width. | Tube entry is positional; its spin-dash-release sound does not imply a spin-dash input requirement. No workaround is needed. |
| CNZ interactive routes and events | Spiral tubes, triangle bumpers, and wire cages were remediated in the preceding CNZ compatibility pass. The current audit found no remaining native-P2 alias in mandatory traversal. | `CNZSlotMachineRenderer` keeps a 320×224 logical playfield while separately reading the actual GL viewport for conversion. Boss/event constants are world-space arena coordinates. Neither category should be widened. | Bumpers, tubes, cages, and slot-machine exits are contact or position driven. No spin-dash dependency was found. |
| Forced-spin crossings | Main, native P2, and later sidekicks retain independent crossing state. The native two scalar fields execute first; identity-owned extension state handles death, omission, reorder, and PlayerRef replacement without changing native timing. | Trigger lines are world coordinates and contain no visible-width assumption. | Forced rolling is supplied by the object; donated S1 movement does not need spin dash to cross it. |
| OOZ popping platforms and springs | Player-triggered popping platforms capture, carry, launch, and release every standing participant in native order. Live standing bits remain authoritative at the apex. OOZ springs use independent extension launch/carry latches instead of sharing native P2 state. | Object motion is world-coordinate based. The audited `224` values in OOZ launch debris are vertical playfield/lifetime bounds, not horizontal widescreen locks. Shared placement/windowing owns horizontal visibility. | Activation is standing/contact driven. No spin-dash dependency was found. |
| MTZ spin tubes | Native P1/P2 state remains first. Later sidekicks have identity-owned sine/path state, invalid/omitted riders are released, unload clears forced control, and compact rewind restores replacement PlayerRefs. | Paths and entry rectangles are ROM-authored world coordinates. No camera-width gate is used. | Entry is positional. The release sound is presentation only; no spin-dash input is required. |
| MTZ nuts and long platforms | Nuts retain native P1/P2 action fields and extend standing/action state by player identity. Every standing extension can align and drive the nut without aliasing P1 or P2. `MTZLongPlatformObjectInstance` already queries the extended participant roster for proximity and solid interaction. | MTZ event thresholds `$2530`, `$2980`, and `$2A80` and the `$2AB0` arena lock are world-camera coordinates. `MTZPlatformObjectInstance`'s `224` is a vertical fall threshold. These are not horizontal viewport widths. | Nuts are driven by standing and horizontal displacement; they never test spin-dash state. Therefore S1 donation can operate them by running, matching the requested MTZ-nut behavior without a donor-specific branch. |

## Rewind and lifecycle ownership

Forced-spin crossings, OOZ platform locks, OOZ spring extension latches, MTZ tube paths, and MTZ nut action states are keyed by `PlayableEntity` identity and declared as captured or transient according to their frame lifetime. Compact collection keys resolve through `PlayerRefId`, preserving ownership after sprites are recreated. Objects that suppress player control release the exact captured identity on death, omission, or unload; nut state owns no forced-control latch.

## Widescreen classification

The audit treats ROM camera trigger and arena coordinates as world-space invariants. Expanding those values with viewport width would delay locks and expose death pits or boss-space geometry. Screen-space rendering code may use the active viewport, while fixed 320×224 logical render coordinates remain valid when a later transform maps them to the actual viewport. No horizontal 320-pixel object/event check requiring remediation was found in this scope.

## Regression coverage

Focused tests cover main plus three sidekicks, native-first ordering, independent crossing/carry/action state, death/omission cleanup, tube unload release, live OOZ apex standing behavior, and compact replacement-player restore. Relevant S2 trace replays remain comparison-only; no trace data is written into engine state and no zone, route, or frame carve-out was added.
