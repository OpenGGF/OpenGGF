# Sonic 2 ARZ, HTZ, and MCZ compatibility remediation

Date: 2026-07-12

Scope: Aquatic Ruin Zone, Hill Top Zone, and Mystic Cave Zone. Flying Battery Zone is explicitly excluded because it is under separate ownership.

## Result

| Area | Multi-sidekick | Widescreen | Cross-game donation |
| --- | --- | --- | --- |
| ARZ vine switch | Main player and every configured sidekick keep independent grab/release state. Native P1 then P2 scalar behavior remains first; extension state is identity-owned and rewindable. Death, roster omission, reorder, and replacement-player rewind restore retain the correct actor. Extension owners are released on unload; native slots preserve Obj7F's trace-visible `MarkObjGone` control quirk. | No screen-width-dependent trigger was found. | Jump releases the switch; traversal does not require spin dash. No donor workaround is needed. |
| MCZ moving vine | Main player and every configured sidekick keep independent grab/release state. Native P1 then P2 behavior remains first; extension state is identity-owned and rewindable. Death, roster omission, reorder, and replacement-player rewind restore retain the correct actor. Extension owners are released on unload; native slots preserve Obj80's trace-visible `MarkObjGone` control quirk. | Existing motion and deletion use world/placement semantics rather than a hardcoded visible width. | Jump releases the vine; traversal does not require spin dash. No donor workaround is needed. |
| HTZ seesaw | The native P1/P2 standing references remain first. Further standing players are retained by identity and launched in configured sidekick order with the same exact launch velocity. Standing references are PlayerRef-backed for rewind replacement. | Shared seesaw geometry is world-coordinate based. No camera-width trigger was found. | The seesaw launch routine only clears pre-existing spin-dash state; activation and traversal do not require spin dash. No donor workaround is needed. |
| Whisp visible activation | Player targeting is unchanged. | Horizontal and vertical visible-edge checks use the active viewport dimensions. Strict edge operators and object radii are unchanged, preserving exact 320x224 behavior while allowing activation in visible widescreen space. | No spin-dash dependency. |
| ARZ/HTZ/MCZ event handlers | Boss/event sidekick bounds already use the shared event-manager team path. | Audited thresholds are ROM-authored world camera positions. HTZ constants `224` and `320` are lava background offsets, not viewport dimensions, and must not be widened. Boss locks therefore remain deterministic at wider widths. | No event route requires spin dash. |

## Rewind ownership

`VineSwitchObjectInstance` and `MovingVineObjectInstance` preserve their native two scalar slots for trace parity, but bind those slots to stable player identities. When roster order changes, state migrates with the actor rather than the slot. Extension maps and native owner references use compact rewind PlayerRefs, so restoring into recreated player instances does not retain stale Java references.

`SeesawObjectInstance` captures its native standing references plus the identity-backed extension standing set. Its shared angle remains a native two-participant calculation; additional participants extend launch participation without changing native geometry or timing.

## Regression coverage

Focused tests cover:

- main player plus three configured sidekicks;
- native ordering and independent ownership;
- roster reorder and omission;
- death and unload cleanup;
- replacement-player rewind restoration through `PlayerRefId`;
- exact HTZ launch velocity for all standing participants;
- Whisp activation at native 320px and a wider viewport;
- compact rewind policy and coverage guards.

Trace replay reference data remains comparison-only. These changes do not hydrate engine state from trace files and do not introduce zone, route, or frame carve-outs.
