# LRZ corkscrew P2 standing-bit dispatch

Base: boulder correction `c01df0568`, whose parent is origin/develop e6a8b4a34.
Same original ROMs, JDK25, EGL and exact commands/environment as the
[boulder audit](2026-11-03-lrz-boulder-lifetime.md).

Native row3413 captures P2, row3414 advances him to1245,03D9 with velocities
1500,0500 and ground velocity0610, and row3415 retains all those values.
CPU routine remains6; object_control43, on-object true, airborne false.
The engine instead advanced to124A,03DB, velocities0500,0200, ground0620.

The temporary object probe executed the P1 direct pose tail at ground0750:
its raw ride frame changes F9 -> F8 while P2 is riding with accumulator00060000.
`sub_42262 (sonic3k.asm:87551-87565)` deliberately uses addq.b in FixBugs=0;
the fixed branch would reload d6. `Sonic_Load_PLC2 (sonic3k.asm:25255-25296)`
returns early on the same DPLC frame, but a changed frame installs an aligned
art source, leaving the later P2 memory-bit selection at1 rather than4.
The clean P2 standing bit is consequently missed on that pass. Its existing
object_control43 prevents recapture, so P2 holds instead of advancing.

The object now retains native standing bits (including captures under dirty bit1)
and its last direct P1 DPLC frame. This is semantic object state, not register/CPU
emulation. It also claims the existing direct-mapping gate while captured:
`loc_138C8 (sonic3k.asm:26290-26300)` skips Animate_Tails under object_control bit1.
Without that gate a diagnostic candidate fixed physics but overwrote P2's held
pose with ordinary walk animation at3415. That candidate was not committed.
All temporary probes are removed.

The two-player unit witness captures P2 under clean bit4, checks a held pass on
P1's new FA frame, then checks resumption on the same FA frame. Existing corkscrew
capture/midride/release rewind spots pass. Ordinary command adds
TestLrzCorkscrewObjectInstance and TestLrzCorkscrewRewindSpot to the boulder
selection: 83 tests, no failures/errors/skips.

Four LRZ r7 reports, matched before/after:

| Class suffix | Before | After | First error |
| --- | --- | --- | --- |
| SonicTailsLrzSegmentTraceReplay | 8114 | 7451 | 3415 tails_x_speed -> 3791 player_animation_id |
| TailsFullChainLrzSegmentTraceReplay | 214 | 214 | 2120 rings unchanged |
| TailsFullChainLrz2SegmentTraceReplay | 1051 | 1051 | 0 camera_y unchanged |
| TailsFullChainLrz3SegmentTraceReplay | 1074 | 1074 | 0 y_speed unchanged |

Zero skips. Expanded error-cell comparison first removes tails_g_speed at3415;
first addition tails_mapping_frame7036 follows the remaining3791 divergence.
The other three complete error lists are identical. No first frontier earlier,
no newly red class. The segment profile independently measured8114@3415 on the
boulder commit; final segment confirmation is recorded in the delivery report.
No shared CPU/collision/loading code or trace comparison contract changed.
Whole-act viewport/donor/roster and comprehensive two-player rewind breadth remain
inherited matrix gaps; these bounded checks do not certify the full route.
