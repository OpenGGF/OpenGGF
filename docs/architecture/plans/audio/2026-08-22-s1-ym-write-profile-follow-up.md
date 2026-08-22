# S1 YM write-profile follow-up

Implement a separate typed Sonic 1 YM service timing profile from the 68K
source calculation in the Task 7 audit. The work must model the shipped
`FixBugs = 0` branches of `FinishTrackUpdate`, `WriteFMIorII`/`WriteFMI`/
`WriteFMII`, `cfSetVoice`/`SetVoice`, and `cfStopTrack`, including busy-poll
branch variants and music-voice restoration.

Acceptance must reproduce isolated and overlapping `SndB5_Ring` FM5 groups
without selecting by sound ID, route, frame, or register fingerprint; keep the
profile owned by the S1 configuration and add ROM-backed regression controls.
This plan is intentionally not executed in Task 7.
