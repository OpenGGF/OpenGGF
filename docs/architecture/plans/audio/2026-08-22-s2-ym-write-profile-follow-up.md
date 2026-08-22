# S2 YM write-profile follow-up

Implement a separate typed Sonic 2 YM service timing profile from the Z80
source calculation in the Task 7 audit. The work must model shipped
`FixDriverBugs = fixBugs = 0` behavior through `zWriteFMIorII`/`zWriteFMI`/
`zWriteFMII`, `zSetMaxRelRate`, `zFinishTrackUpdate`, `cfSetVoice`/
`zSetVoice`, and `cfStopTrack`, including uncontended bank waits and music
voice restoration.

Acceptance must reproduce isolated and overlapping `Sound35_RingRight` FM5
groups without selecting by sound ID, route, frame, or register fingerprint;
simultaneous VDP-DMA behavior requires a typed bus-timing input rather than a
fitted constant. This plan is intentionally not executed in Task 7.
