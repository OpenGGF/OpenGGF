# Invisible-block status7 and LRZ row3791

Base: corkscrew correction 6ea9f054b on origin/develop e6a8b4a34.
The native aux row3791 identifies slot6, Obj_InvisibleBlock at0CF0,0592,
subtype11, object status88. P1 has status08 (standing), object_control00,
animation05/frameBA. Engine diagnostics execute the same contact with balance1,
anim06/frameA4, otherwise matching motion, subpixels, camera and rings.
P2 is CPU routine4, object_control81, airborne, not riding.

`Obj_InvisibleBlock (sonic3k.asm:42701-42706)` sets status bit7. Both hurt
families do likewise at43313-43318 and43480-43485. `Sonic_NotRight
(sonic3k.asm:22488-22494)` tests that bit before calculating edge balance.
The family now overrides the existing suppressesObjectEdgeBalance hook. No
shared contact, movement, animation, CPU or loading implementation changed.

Matched four-class r7 replay with the exact environment/ROMs/command from the
[boulder audit](2026-11-03-lrz-boulder-lifetime.md): Sonic+Tails7451@3791
player_animation_id ->7449@4630 status_byte. Expanded row/field comparison
removes player_animation_id at3791 first and adds no cells. Solo Tails214@2120
rings, LRZ2 1051@0 camera_y and LRZ3 1074@0 y_speed are byte-for-byte identical
error lists. All completed; no skips, newly red class or earlier frontier.

Ordinary selection extends the corkscrew audit's83 tests with
TestSonic3kInvisibleBlockObjectInstance,
TestSonic3kInvisibleHurtBlockHObjectInstance, TestS3kInvisibleHurtBlockV and
TestS3kInvisibleBlockRewindGenericRestore:103 tests pass without failures,
errors or skips. The segment-profile confirmation and remaining frontier
investigation are recorded in the delivery report. This is bounded local
validation; matrix breadth and full-act certification remain inherited gaps.

## Final segment confirmation and handoff boundary

The exact S+T class also completed under -Ptrace-segments at7449 errors,
first4630 status_byte, zero skips, matching r7. A temporary setPushing probe
executed true at093B,04F1 from
ObjectSolidContactController.processInlineObjectForPlayerInternal (contact.pushing).
It was removed. Player and camera motion still match; the native status is01
versus engine21, with later mapping-frame fallout4631..4696. Native aux has no
solid_object_cont_entry, velocity-write or position-write events at4630: only
object/CPU/interact/oscillation/pause/air-countdown snapshots. The nearby moving
small door is a candidate, not a proved cause. No shared correction is proposed
from those incomplete observations. A follow-up needs a native SolidObjectFull
entry/exit witness and matched engine contact geometry; any resulting shared
contact change requires the lane's three full trace profiles before and after.
This frontier is unresolved, not an A/B entry-provenance ruling or a green trace.

Final branch delivery has three separate object-local fixes. No full trace
profiles, broad smoke or full suite were run; no shared runtime implementation
changed. LRZ2/LRZ3 entry/carried-state frontiers were deliberately left unchanged.
