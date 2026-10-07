# Mutators prototype implementation

Accepted design: [mutators blueprint](../designs/2026-10-07-mutators-blueprint.md).
Original base: `6d817a9d74f135714f3da59ab9aab156cc09473e` (`develop`). Delivery is a
feature-branch PR; the main checkout is outside this task's edit authority.

## Bounded product

JVM, trusted boot-prepared `example-mutators`, Sonic solo in Sonic 2 Emerald Hill
Act 1. Dry ordinary air gravity and selective player presentation. Normal jump,
hurt, death, scripted movement, targeting, objects and collision retain native
owners. No placement filters, new levels, broad character/donor promises,
hot installation, native creator bytecode or modified-game recording support.

## Work and evidence

1. Typed definitions, independent action/option scopes, admitted/effective state,
   atomic groups, safe capabilities, owner faults, separate saved preferences and
   rewind snapshots. Contract child owns isolated branch and focused tests.
2. Injected semantic movement/presentation policies, stock identity and native SAT
   admission. Gameplay child owns isolated branch and focused tests.
3. Lead integrates one world-owned state, explicit load causes and modal host UI
   using the existing native level loop; maintains title/configuration/restart/hub
   flow and buildable human/agent example.
4. Lead observes actual GPU frames and PCM in outside-repository task captures,
   exercises repeated launch/exit and recovery, and runs combined change-based
   validation plus affected domain tests. Independent review checks high-risk
   boundaries and final original-base-to-head diff.
5. Fetch destination, assess overlap, commit/push only feature branch, raise and
   register PR. Account for child edits/worktrees/processes; retain PR tree.

No prototype gate is marked complete until executed evidence is recorded in the
existing design and example guide. Later MVP/product stages remain future work.

## Recovery and rejected approaches

The first implementation lead stopped on provider capacity, with the contract
commit `bff89b4fa1632a2612b7e2e973a940c56984dadd` integrated and gameplay/host
sources preserved. Recovery retains that commit and the original base. The
gameplay child's structural `@RewindTransient` binding remains excluded from
sprite history; the standalone hook's declaration heuristic was not bypassed
or satisfied with a fake pin. Genuine host API additions belong in the combined
candidate signature update.

The final read-only review exposed two delivery blockers. A preference save
failure at Start/Resume could be overwritten by loading or acknowledgment, so
transition requests must retain the visible retryable draft on failed save.
Persistence now gates Start and Resume before any policy publication, and gates
Restart/Return before a command can leave the menu. A still-unavailable settings
directory holds these actions; closing the Engine window remains available.
The capture's offline PCM lease did not survive the production title-to-level
audio rebuild: a stock GPU reproduction with 600 neutral inputs and Start
completed 622 title rows before `beginCaptureMode() not called`. The existing
manager-owned live capture handle supplies the needed carry/rebind lifetime;
there is no new audio producer or speaker path.
The producer's presentation rate must still match capture FPS before attaching:
checking only the requested lease rate would miss PAL/configuration mismatch and
silently truncate or pad packets. Independent repair review caught this guard
regression before the focused invocation was admitted.

A 300-frame initial wait never left the stock title, and the example's original
240-frame prefix therefore did not prove its menu path. Capture inputs must
wait for the native intro/title readiness before selecting controls. Custom
controls should appear only when they can accept input. The synchronized CSV
and actual engine window, rather than an input script alone, establish the
visited phases. Completed repair, integration and audiovisual results are
recorded below before delivery.

Final committed review of `f6e1f7a47` found a nullable overlay command falling
through to restart, and global Escape bypassing title-menu ownership while
drawing its prompt over the header. Null now means `NONE`; restart has an explicit
branch. Stock titles retain their Escape default, while the common title owns
Back, saves before Return to hub, clears the host prompt and keeps input held
after acceptance until retirement. Actual normal-source GPU/PCM capture also
showed the title Start cue selecting a missing WAV fallback before the gameplay
audio profile was bound. The example uses the cached native Sonic 2 `$BC` ROM
SFX instead. The earlier captures are attributed to their completed source;
repaired-source recapture remains required before delivery.

## Final-source observations

Implementation commit `ea74ed8620` and destination merge `f6e1f7a477` retain the
original base and merge `5b3a63641033506fc0d89ad5188a0c97fae29089` by intent.
The merge preserves `onNewGameFromTitle`, delegated forwarding and the unavailable
two-player title branch. Final repair `6bd7c052a6` resolves nullable commands,
title Escape ownership, accepted-fade input and the native Start cue. The final
source-only resolution review found no remaining concrete blocker. Its scope
does not substitute for runtime or broad verification.

The final compiled candidate export has 20,333 lines: 287 additions and no
removals against the actual destination, retaining both upstream additions.
The descriptor and `ModApiVersion.CURRENT` remain unpublished 0.7 candidate.
The final prepared example contains no ROM, image or sound assets.

Normal-source captures are retained outside Git in the task capture directory,
under `gpu-verified` and `native-verified`. The maintained `capture.script`
produces 2,077 synchronized GPU/state rows and 1,661,600 stereo 48 kHz PCM frames.
Actual frames 180, 600, 704 and 774 establish intro gating, title backing and
slider/checkbox/enum clearance. Frames 1,130, 1,360 and 1,570 establish airborne
play, hidden player with world/HUD retained, and restored presentation. Frame
1,800 is the native restart title card; frame 2,076 is fresh native play.
Configuration holds 1,218–1,254 and 1,467–1,514 keep position, velocity and camera
constant. Revision transitions occur at Start 796 and Resume 1,255/1,515.

The maintained 98-action window walkthrough uses normal `Engine.loop`, its tick
limiter and freshly compiled classes. It exercises help, all option types,
native play, pause, Stealth, focus return, resume, restart, hub, two fresh
launches, title Back and window close. The default window manager mapped the
exact owned PID/title at 960×672, depth 24, without `override_redirect`. Child-only
Pulse/keyutils preload and driver-vsync settings are recorded by the helper;
no global window/audio settings changed. Close returned zero, every owned
process stopped, and the sink, focus window and display were released.
Native device PCM contains 101.8 seconds of stereo 48 kHz output, with varying
AC signal around menu actions, gameplay and relaunch. This qualifies the
recorded device stream, not physical speakers or a hardware controller.

Earlier isolated patched-classpath/no-audio captures, stalled explicit-show
probes and incomplete input-release walkthroughs remain diagnostic evidence.
The default-mapping result is specific to the final launch path; it does not
retroactively qualify those attempts. The maintained helper preserves exact
ownership, release/focus hazards and independent cleanup rather than shipping
an Engine-wide window-management workaround.

Focused repair invocation 50600 compiled 3,715 production and 3,579 test sources
and passed 20 cases with zero failures/errors/skips. After the accepted-title
edge repair, invocation 83824 passed the two affected cases with zero skips.
The five Python helper failure-injection checks passed. The original child
evidence remains separately attributed: contract 45 cases; gameplay 243
unaffected cases plus seven repaired presentation fixtures, all without skips.

The combined selection uses the actual merged destination SHA above, not HEAD:
3,015 ordinary classes across all categories plus guards in a fresh JVM.
The recent destination audit measured about 72 minutes ordinary and 215 seconds
guards. A 120-minute per-invocation timeout excludes queue waiting; ten minutes
without output stops the invocation. Timeout is incomplete validation. Affected
S2 trace segments and SDK verification are separate obligations. Completed
results and inherited-failure attribution will be appended before PR delivery.

## Later executable destination reconciliation

Destination `2fc65c8479570f16ebd9830115485ee369c2b1e6` adds executable Survivors,
power-up music ownership, Infinite regression and memory-tool changes. The
unadmitted 5b3-based combined request 35021 was normally cancelled (exit 130)
after exact owned PID/CWD/waiter and zero-child confirmation; no Maven or tests
started. Its 3,015-class plan and older captures remain historical evidence.

The second intent merge retains the upstream `PowerUpRules` constructor and
`restoreLevelMusicAfterInvincibility` flag, stock `true` factories and the actual
host-mode music gate in `AbstractPlayableSprite`. Its only conflict was API
version commentary; both contracts are retained without a version/status bump.
Normal focused invocation 35517 compiled 3,715 production and 3,579 test sources
and passed 106 cases across 16 classes, zero failures/errors/skips. This includes
native S2 revision/recreation replay, Gravity/presentation, admission/preferences,
host commands, API pin/policy, hybrid rules and stock music restoration. The source
freeze had zero changes between submission and completion.

The pin was regenerated from those actual compiled merged classes: 20,335 lines,
287 own additions and no removals against 2fc. Equality with the provisional
automatic merge was checked rather than treating text union as proof. SDK,
candidate trace/domain and merged-source audiovisual results are recorded below.
Shared baseline runs belong to their original owners; the older 5b3 failure set
is not a 2fc certificate. The subsequent tool-only destination merge supplies
the final combined integration base, never HEAD.


## Integrated 2fc audiovisual observation

Merge `157401f252` is the source for the new normal-class captures in the task's
outside-repository `gpu-integrated-2fc` and `native-integrated-2fc` directories.
Invocation 35517 compiled these classes; the cached window version caption is
not used to identify the source. The freshly rebuilt example jar is 8,063 bytes,
SHA-256 `484ad7c83b7f96940df18e6a7b3f331b616c6cbc0c73bc701b80b878da16b090`.

GPU invocation 66030 completed with 2,077 state/PNG rows and 1,661,600 stereo
48 kHz PCM frames. CSV was inspected before frames 180, 600, 704, 774, 1,130,
1,360, 1,570, 1,800 and 2,076. The introduction gates controls, title text has
backing and option rows leave room for pending captions. Frame 1,360 is real
airborne LEVEL play at (549,586), two rings, revision 2, with the player hidden
and native world/HUD retained. Mapping changes from 61 to 65 between frames
1,130 and 1,135. Configuration holds retain position, velocity and camera;
Resume publishes revisions 2 and 3. Restart traverses the native title card
and resumes fresh gameplay. PCM has varying AC signal around native menu cues,
Start, Resume and gameplay; the former Start WAV fallback is absent.

Normal Engine invocation 93319 completed the maintained 98 actions using its
normal tick limiter. The exact owned PID/title mapped through the default window
manager at 960×672, depth 24, map state 2, without an unmanaged-window override.
Images establish help, slider/checkbox/enum editing, native play, Stealth,
restored presentation, restart, hub, two fresh launches and title Back. Close
returned zero, all three owned processes stopped, and the sink/focus window/display
were released. The 101.8-second stereo 48 kHz Pulse monitor stream has varying
AC signal around actions and gameplay. Muxed native and GPU videos contain
3,054 and 2,077 video frames respectively, both with stereo audio. Child-only
keyutils/Pulse and driver-vsync settings remain recorded; physical speakers
and hardware controllers were not observed.

The separate missing-ROM default mapping invocation 15517 timed out after
90 seconds; its own 20-second thread sample was in `glfwShowWindow`. Cleanup
stopped its owned process and removed its sink. This does not negate the
completed default mapping above or establish a universal display failure.
Frameless missing-ROM diagnostics are qualified separately; their action labels
alone do not establish the visible transition.

The final merged missing-ROM frameless diagnostic 72947 observes ROM NOT FOUND and
Escape Back to the same Sonic 2 hub selection, then clean close and owned cleanup.
It does not qualify a second Start attempt; long holds and focus changes can span
UI states, and action/image names are not evidence of the intended transition.
The original timed workaround 10169 is likewise not retry proof.


## Final validation base and completed package/domain checks

The final destination merge `dc0ab6933129dc330d9c36a95ddcd0e58b27ad7d` incorporates actual
`37a57ebdbe62864737f39e7b14c72932fbaf3d74` by intent, without conflicts.
Its six upstream tool/documentation paths change diagnostic acknowledgment
locking and Python safety checks; no engine Java, API, POM, hooks or category
selection changed. The 2fc compiled pin, focused tests, SDK and captures above
remain applicable by that exact source comparison and retain their attribution.

Normal queued invocation 66942 completed `-B -Dmse=off -DskipTests verify`
with exit zero in 37.568 seconds. Binary, SDK and Javadoc artifacts and the SDK
artifact verifier passed. Tests were explicitly skipped; this is packaging
verification, not an ordinary test pass or native-image certification.

Normal trace-replay invocation 30095 selected the two EHZ1 segment classes and
three S3K AIZ/AIZ-slice/HCZ-slice classes, with all three original absolute ROM
properties and Lua 5.4. At source `157401f252`, 21 cases completed in 91 seconds:
18 passed, three assertion failures, zero errors/skips, Maven exit one.
Both S2 segments and 16 additional S3K cases passed. Each `replayMatchesTrace`
failure literally equals the clean exact-2fc shared three-method baseline:
AIZ 57 errors, first 20302 animation `0x0000/0x0005`; AIZ slice 99, first 25589
animation `0x0013/0x0005`; HCZ slice 4,699, first 9482 air `1/0`.
This is inherited divergence evidence, not a passing trace claim.

The completed ordinary comparison source is `4cfb745646d9439cdb9c07d53d0670dd0fb3fe58`:
26,222 cases, 28 assertion failures, zero errors and 62 skips; fresh guards
672 passed, zero skips. Its full 28 messages and 62 skip identities/reasons are
in the existing mod-framework readiness plan. Runtime Java, POM, hooks and
normal category runner are unchanged from 4cf through 2fc. The sole Java test
delta is `TestInfiniteSonic`; an existing Linux Sitar candidate class report
exercises that exact 2fc test source (238 cases, 206 pass, 32 literal baseline
skips, new driver observation pass). That class evidence is neither a clean
2fc runtime baseline nor a whole-suite result. The clean 2fc full request was
cancelled with exit 130 and no results. No redundant full baseline is submitted.

Final combined selection and preflight use actual 37a as base. Expected cost
uses the completed 4cf measurement: 85.96 minutes ordinary plus 221.57 seconds
fresh guards. A 150-minute per-invocation limit excludes queue waiting; the
existing ten-minute no-output limit remains. Timeout means incomplete. Final
candidate identities, full assertions and first causal skip reasons will be
compared; only the SSZ Tails object-blob hexadecimal identity is normalized.
The final ordinary/guard outcome is pending at this checkpoint.
