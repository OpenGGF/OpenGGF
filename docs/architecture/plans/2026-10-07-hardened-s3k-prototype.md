# MHZ1 Post Two Ambush implementation

Accepted scope is the [Hardened blueprint](../designs/2026-10-07-hardened-s3k-blueprint.md)
prototype: one local native MHZ1 encounter, solo Sonic, donor off, 320px,
no save. Original implementation base is `6d817a9d74f135714f3da59ab9aab156cc09473e`.
The later full-act, chapter, campaign and Stage Trials gates remain future work.

1. Survey the physical second post's floor, headroom, placements, rings and
   camera using production ROM decoding. Freeze an explicit bounded encounter.
2. Add transaction-owned immutable placement plans without replacing the native
   level, objects, terrain, events or PLC owners. Add explicit fresh-entry,
   no-save and native-input controller opt-ins with stock defaults.
3. Author the resident-art sentry and recreatable projectiles. Capture admitted
   simulation phase, committed aim and finite projectile state through rewind.
4. Integrate the title, lesson, entry, play, checkpoint retry, results and clean
   exit using existing engine presentation/input/audio owners.
5. Build/package the maintained JVM example; document human and agent authoring,
   valid/invalid plans, supported cells and observed evidence. Capture actual
   graphics and PCM outside the repository from committed input programs.
6. Verify focused behavior, two-cycle replay, fault containment, native checkpoint
   reload, the four mandatory S3K regressions and AIZ→HCZ safety. Review the actual
   combined selection, preflight and run required category/guard validation.
7. Independently review high-risk boundaries and the final base-to-head diff;
   fetch/assess develop, push only the feature branch and open/link its PR.
   Account for children and retain the PR tree for review.

Parallel ownership: placement child owns frozen plan registration/application;
encounter child owns geometry, gameplay objects and focused mechanics; session
child owns the three launch/input opt-ins. The lead owns presentation, integration,
authoring documentation and delivery. Each child uses a separate copy-on-write
worktree and queued focused Maven commands. Their commits and exact verification
are reconciled in the outside-repository task ledger before integration.

Current composition is `6fbddbdca82d5396e1160ae4142f7e46ff99f6cc`, with published
integration base `02796b4c497c3aa887f77a8d8e9697b7009d2b4c`. Original `6d817a9d`
remains an ancestor. Intent merges preserve upstream `onNewGameFromTitle`,
DelegatingGameModule forwarding, unavailable TWO_PLAYER title branch, stock
invincibility music rules and the subsequent banked SMPS/scene contracts. The
ModApiVersion comment and compatibility prose retain both API intents. The
published successor adds only a Sitar architecture document and launcher beyond
`be3c3141`; it changes no engine, tests, API, POM, hooks or selection policy.

The actual composed package request selected 20 class names and produced 21 fresh
XML reports: **197 passes, zero failures/errors/skips**, exit 0, including audio
saved-duration/rest, frozen bank programs, audio producer rewind, scene music,
actual API/SDK classes, all four mandatory S3K regressions and seven packaged
prototype cases. The compiled candidate export has **20,186 lines**, SHA-256
`c694532e5905a864c830015db49e2648941fb9e27ff69805488630efadff3b20`, byte-equal
to the pin. Candidate 0.7.0 and published baselines are unchanged. The maintained
creator jar was rebuilt and SDK-validated; its 18 entries are classes and
`META-INF/openggf-mod.yaml`, with no ROM payload.

The composed normal trace-profile request ran exactly
`TestS3kAizTraceReplay#replayMatchesTrace`,
`TestS3kAizZoneSliceTraceReplay#replayMatchesTrace` and
`TestS3kHczZoneSliceTraceReplay#replayMatchesTrace`. All three full assertions
literally match the completed clean 2fc baseline: 57 differences / first frame
20302 animation 0/5; 99 / frame 25589 animation 0x13/5; 4699 / frame 9482 air 1/0.
There are no errors or skips. These are inherited failures, not passing traces.
The original baseline request 80733 ended 130 before admission; its cause remains
unknown and its validation incomplete. Historical 151-case 2fc focus and corrected
22-case API/SDK focus remain source-attributed; three nonexistent earlier selector
names provide no coverage.

Actual composed audiovisual evidence is outside Git at
`${OPENGGF_TASK_SCRATCH}/hardened-s3k/captures`.
The maintained controller programs drive production gameplay; no trace values
supply state or readiness. Inspected PNGs include the first animated title,
first released PLAY, committed aim, volleys, results and checkpoint reentry.

| Capture | Observed state and decoded evidence |
| --- | --- |
| `composed-safe-01` | 1316 GPU frames / 1,052,800 stereo PCM frames; native post313, tell332, lock368, volleys380/404, clear675, post respawn840, title951, separate fresh1035. Full 60fps 640×448 movie decodes. |
| `composed-failure-01` | 715 GPU frames / 572,000 stereo PCM frames; post279, lock337, volley349, actual zero-ring death352, post respawn531, title642. Full movie decodes. |
| `composed-native-safe-02` | Genuine native X11 press/release input; 1376 state rows / 1429 decoded desktop frames, post372, volleys438/462, clear735, native respawn900, title1011, separate fresh1106. Stereo48k device PCM AC RMS1195/1201 per channel. |
| `composed-missing-rom-01` | 337 native rows / 360 decoded frames: actual ROM-not-found0, preserved S3K selection90, repeated refusal180, responsive menu300. Both recorder exits0; device PCM AC RMS1804 per channel. |

The native captures identify an AMD Radeon RX9070XT / Mesa26.2.4 OpenGL4.6
backend, exact owned Engine PID/title/viewable geometry and focused window.
This host's explicit GLFW show call stalled; only that owned window was mapped
frameless. Child-only `ALSOFT_DRIVERS=pulse`, private `PULSE_SINK`,
`LD_PRELOAD=/usr/lib/libkeyutils.so.1`, `vblank_mode=0` and
`__GL_SYNC_TO_VBLANK=0` were recorded. Engine/recorders stopped and private sinks
unloaded. This is unmodified Engine.display at diagnostic60Hz, not certification
of normal WM initialization, Engine.loop cadence, hardware controllers or speakers.
The GPU tool separately observes actual ROM PCM: title AC1247/1251, volley-one
1769/1806 and volley-two1743/1816 per channel, with no flat10ms volley windows.

The first native recipe began movement before native ENTRY released (PLAY285
versus logical274), so its later encounter did not clear. It is rejected as a
successful walkthrough. `native-safe.script/.bk2` adds a neutral entry margin
and completes the native path; this changes authored input, not engine readiness
or physics. Earlier D5/pre2fc media and recorder255 diagnostics remain historical.

Independent placement, session, mechanics, integration, readiness and native-card
consumption reviews found no blockers. The 67 own source/example/API deltas after
be3 are identical to the reviewed pre-be3 implementation. Child commits were
accounted before their clean merged trees/branches were removed; histories and
light results are retained outside Git. No peer branch is imported. Mutators owns
overlay/configuration/lifecycle, Multigame exclusive worker step/input/media, and
this branch bounded native placements, launch and input opt-ins.

Final combined validation is still pending. Actual published-base selection is
3032 ordinary classes across all ten categories plus separate fresh guards:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 02796b4c497c3aa887f77a8d8e9697b7009d2b4c --max-minutes 150 --run
```

Java21/Lua5.4/PowerShell preflight passes. Latest measured published-base ordinary
cost is about109 minutes plus3.6 minutes fresh guards; the150-minute execution
cap and10-minute no-output cap exclude queue waiting. Timeout is incomplete.
All concrete failures/skips must be compared to qualified published-base evidence,
not totals. The base has28 inherited ordinary assertions and62 expected skips;
this is not a green whole-suite claim or a replacement for candidate validation.
Consumed runner diagnostics will be acknowledged after inspection.

The user subsequently requested root-owned Claude Opus5.5 hands-on polish, then a
separate promo-video task. After these implementation gates, the lead supplies a
clean exact-source handoff outside Git and holds feature push/PR for polish
integration. Native semantics and prototype scope remain fixed; full-act,
chapter/campaign and Stage Trials gates remain future work.
