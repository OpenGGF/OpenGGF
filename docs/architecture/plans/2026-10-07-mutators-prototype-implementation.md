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
5. Fetch destination, assess overlap and prepare a clean verified implementation
   handoff. The user's final phase holds feature push/PR for root-owned Opus polish
   and a separate promo-video task. Account for child edits/worktrees/processes;
   retain the lead branch/tree for that integration and the eventual single PR.

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
This checkpoint preceded the completed red 37a run and repairs below.


## Broad regression repair before composed handoff

The preserved 37a run at `6e9af21ec5` completed, not cancelled: 3,015 selected,
3,013 ordinary reports, 26,294 tests, 36 failures, one error and 62 skips in
4,084.35 seconds. Fresh guards ran 672 cases, three failures, zero skips in
221.51 seconds. Full comparisons found nine additional failed identities
(including the error), twelve changed inherited assertions and identical skip
identities/reasons. This was not a verified handoff. Consumed diagnostics were
acknowledged after the complete bounded comparison was retained outside Git.

Three direct-level capture contracts had changed globally: fade advancement,
player reference rebinding and the input-last CSV schema. Those changes now apply
only to the explicitly new title-first path. Existing stock capture/authoring
callers retain their driver and CSV semantics; no physics or trace data is altered.
The preemptive global fault callback gate also broke existing direct module and
VM-fatal semantics. Persistent owner availability remains authoritative for
mutator admission/restoration, while the established callback boundary retains
its fatal/error behavior. Focused failing identities and final broad results will
verify the repair; no pass is inferred from this source diagnosis.

Guard failures identify new game/level dependencies on concrete mod types and
growth of GameLoop/LevelManager. Pending-command retention is extracted from
GameLoop; session/resource ownership is inverted through engine-owned
provider/service types, retaining per-world state and rewind ordering. Ratchets,
allowlists and source budgets remain intact. Published destination composition
preserves Sitar SMPS/scene APIs and the later launcher-only successor, with final
compiled pin, SDK, audio/rewind and audiovisual checks still required.

Root now owns the requested subsequent Opus polish implementation and separate
promo-video stages. Feature push/PR is held for their integration. This lead must
first produce a clean verified implementation handoff; no Opus worker is launched
by the lead, and the scope remains the prototype rather than the future product.


Composed repair check `72221` completed on 2026-10-08 at 02:36 UTC against the
uncommitted `02796b4` intent merge and recorded repairs: 217 tests, 216 passes,
one inherited DEZ assertion (`expected: <96> but was: <0>`), no errors/skips.
The full assertion matches the qualified baseline after removing only the
exception prefix. New callback/sample, representative DEZ/LRZ capture routes,
SSZ input author, world/modal, composed SMPS/audio rewind and API/Javadoc/SDK unit
checks pass. All 10,041 frozen source hashes are unchanged. Maven exited 1 before
its artifact verify phase; no artifact pass is claimed from that invocation.
The compiled 20,392-line export equals the composed candidate pin: destination
20,105 lines plus 287 prototype additions, zero removals. Boundary services stay
engine-internal and do not add a creator API root. Descriptor/runtime stay 0.7.0
candidate. The three blocking structural assertions, composed artifact verification,
final AV/domain and actual-destination combined comparison remain pending.


The bounded guard/artifact request `22933` completed 2026-10-08 at 02:47 UTC:
101 structural tests passed with zero failures/errors/skips, including all three
previously failing cycle/size assertions. Architecture allowlists and large-class
budgets are unchanged. SDK preparation, Javadoc packaging and artifact verification
completed successfully (44.192 seconds execution after 485 seconds queue wait).
Current jars contain no obsolete moved policy classes. Composed-source audiovisual,
trace/domain and final full ordinary/fresh-guards comparison are still pending.

## Published capture composition and implementation handoff gate

Repair merge `0a874c23712aaeb746bfc308997e86dc8e1aa47c` preserves original
`6d817a9` provenance and published `02796b4` ancestry. Normal hooks passed.
Fresh normal-class GPU/PCM capture contains 2,077 synchronized frames and
1,661,600 stereo 48 kHz PCM frames. Inspected frames include title 600, help 620,
options 704/774, native animation 1,130/1,135, Stealth 1,360, restored body 1,570,
restart title card 1,800 and new level 2,076. Both configuration hold intervals
retain position, velocity and camera. Native capture 15466 closes zero and
cleans every owned resource; actual frames show Gravity/Stealth/resume/restart,
hub retirement and two fresh launches. Exact owned PID/title, 640×448,
depth 24 and viewable state are recorded. Default WM mapping failed within
90 seconds; the successful path uses only this window's frameless mapping and
child-only Pulse/keyutils/vblank environment, with the normal Engine loop limiter.
Device PCM includes changing AC samples around navigation and play; neither
physical speaker output nor an isolated SFX waveform is claimed.

Native action filenames are not state evidence: the first named help image
remained Home. A bounded 0.25-second press/release follow-up visibly opens Help,
but its Escape image still shows Help. Controlled production-menu Enter/Escape
Back cases both pass; synthetic host sampling remains a capture limitation.
The original helper input lesson and cleanup tests remain maintained.

Fetched destination `33d3976c53304dbbea1c695914ecdd7bfc64cf9d` includes
optional pixel readback and fixture teardown improvements, not merely prose.
Its intent composition preserves the title-first branch in draw-only rendering.
Focused invocation 98392 compiles the composed source and passes 39 cases:
six real title/level/results pixel checks (including a new S2 title no-readback
check), two fixed-key Help Back cases and 31 public-policy/signature checks;
zero failures/errors/skips, 74 seconds execution after 80 seconds queue wait.
Fresh actual compiled export is byte-identical to the 20,392-line candidate pin.
Descriptor/runtime remain unpublished 0.7.0 candidate. No published pin changed.

Invocation 83012 ran 21 explicitly selected trace cases: 18 passed, three exact
inherited assertions, zero errors/skips. Its invalid `-Ptrace` name did not
activate a profile; this is recorded as incomplete normal-profile qualification.
The corrected `trace-replay` profile completed after composition, as recorded below. No shared
baseline is duplicated. Final combined validation uses the actual destination,
full change-based selection and fresh guards, 150-minute execution caps and the
existing ten-minute no-output rule; waiting is excluded. Latest published-base
cost is about 109 minutes ordinary plus 3.6 minutes guards, not an ETA.
All full assertion/skip identities must match the qualified baseline before
handoff. Source remains lead-owned; feature push/PR is held for root-owned Opus
polish and its separate promo-video stage.


## Verified implementation handoff, 2026-10-08

At engine source `0a827ab96ab7db2f475803b8b4e9d50095ea89f5`, the normal
combined command was:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 33d3976c53304dbbea1c695914ecdd7bfc64cf9d --max-minutes 150 --run
```

Run `20261008T033426Z-f1428fb2` completed: 3,031 selected ordinary classes,
3,029 reports, 26,458 tests, 28 assertion failures, zero errors and 62 skips
in 5,158.69 seconds. The separate fresh guards JVM completed 86 reports and
672 passes, zero errors/skips, in 216.70 seconds. Overall exit was one.
All full failure assertions and all skip identities/first causal reasons match
the qualified baseline: 27 assertions literal after exception-prefix removal,
and the complete 2,951-character SSZ assertion after only the verified
`RewindObjectStateBlob@hex` normalization. There are no new, worsened,
unattributed or omitted failures, changed skips or ROM skips. This is inherited
failure qualification, not a green full-suite claim. All nine additional
ordinary failures are resolved, all twelve changed inherited assertions match
again, and all three structural blockers pass without raised budgets/allowlists.
The complete ordinary plan includes both level-loading classes and the other
mandatory S3K classes with no reported failures/errors/skips. Mutator classes
have none; Infinite retains exactly the same 32 skip identities/reasons.

All 10,185 frozen hashes were unchanged. Automatic raw XML cleanup and capped
rolling logs were respected; complete results, selected classes and available
class summaries were inspected. The consumed runner directory was acknowledged
and deleted. The lightweight full comparison and 21-case repair inventory remain
outside Git in the task scratch directory; no raw diagnostic archive is retained.

Corrected invocation 60500 used `-Ptrace-replay`, the two EHZ segments and three
S3K classes with all three original absolute ROM properties and Lua 5.4:
21 cases, 18 passes, three full assertions literally equal to shared clean
2fc SAME3, no errors/skips, exit one, 87 seconds. Neither the unchanged inherited
trace divergence nor the earlier invalid-profile invocation is called trace-green.
Invocation 34844 completed `-B -Dmse=off -DskipTests verify` in 34.652 seconds;
it deliberately skipped tests while verifying the current binary/SDK/Javadoc.

The destination advanced to `09cfcc0f882305d6a0c6a4fc05b600d3ca27a888` during
that run. Its 24 paths add Sitar example presentation, example tests and prose;
engine Java/resources, API, POM, hooks and runner are byte-unchanged from 33d.
Intent merge `d4dc7a8e91e5ae55cbebffc7b4ef18e112f4a7d5` retains published ancestry
and both measurement-catalogue entries. Actual-destination selection still has
3,031 full ordinary classes plus fresh guards; actual tool preflight passed.
The completed full shared-engine check is retained at its exact 33d attribution.
The bounded latest composition command selected `TestSitarHeroArcade`,
`TestSitarHeroPerformers`, `TestModdingDocumentationLinks`, and the five public
API/policy classes through queued Maven `verify`. Invocation 91665 passed all
69 cases in eight fresh classes with zero errors/skips, then verified binary,
SDK and Javadoc artifacts (44.751 seconds execution). It exercises the changed
upstream examples against the composed contract without repeating unchanged
engine/trace/AV checks. The upstream owner separately qualified all seven new
Sitar cases against 141 passing baseline cases and fresh 672 guards. This is
focused composition evidence, not a full 09cf suite claim.

Fresh compiled export remains byte-identical to the 20,392-line candidate pin;
unpublished 0.7.0 status and published pins remain unchanged. Native Engine and
Mutator presentation bytes are unchanged by the latest example-only merge, so
previous captures retain their source attribution and applicability.

Final normal-class GPU invocation 92650 contains 2,077 synchronized frames and
1,661,600 stereo 48 kHz PCM frames. Current frames 620, 704, 1,360 and 2,076
were inspected; CSV and PCM exactly equal the preceding composed capture.
A 700-neutral-frame control (20500) has identical PCM through frame 603. Help's
CONFIRM `0xB5` at 604 produces a nonconstant output contribution (difference
AC RMS 1,438.005 over frames 604–610). NAVIGATE `0xCD` at 600/602 produces no
observed PCM difference; audible navigation is a polish opportunity, not a
completed observation. Native monitor PCM is changing, but no physical speaker
or physical-controller claim is made. The successful current native path uses
only the owned window's frameless mapping; default WM viewability timed out.
Native Help is observed, while a synthetic Escape screenshot remains Help;
controlled production-menu Enter/Escape Back cases pass separately. Check exact
owned focus/readiness at the event before diagnosing routing on shared DISPLAY.

Useful footage, commands, input logs, state CSV and PCM live under the task's
outside-repository `captures/gpu-final-33d`,
`captures/native-composed-0279-unmanaged`, `captures/native-composed-help-0279`
and `captures/gpu-title-audio-control-33d`. All owned media processes/sinks closed;
all child changes are integrated/accounted and child worktrees removed after
committed-blob proof. Harness history and the feature tree/branch are retained.
The original independent review resolved its blockers; this composed diff also
received lead boundary/API/presentation/cleanup inspection and root factual
checks, not a newly delegated generic review. Feature push/PR is deliberately
held for root-owned Opus hands-on polish and a separate promo-video task.
The handoff remains the bounded prototype, not completion of future MVP gates.

## Hands-on presentation polish, 2026-10-08

Claude Opus 5.5 played the handed-off source `87d67e9e68` (base `09cfcc0f88`)
through title, How to play, configuration, native play, play hold, Resume,
restart and hub, then polished the presentation without widening scope.

Acceptance checklist:

- [x] Title card leaves the native Sonic 2 emblem visible and slides up once the
  backdrop is interactive; the old header/tagline panels covered the logo.
- [x] Footer language is player-facing: "Effects stay off until you switch them
  on." on the title; "Live: Resume  Load: Restart  Launch: new game" in play
  (45 of 46 columns at 320 px; the previous legend was cut off).
- [x] How to play names the live bindings: configured P1 A/B/C, Left/Right and
  Pause/Start keys through `ButtonPrompts.keyName`, or the pad's buttons once a
  controller is the last input. Defaults read "Space: jump" and "Enter or
  Backspace: settings while playing" instead of Genesis A/B/C.
- [x] Option help and mutator descriptions wrap on whole words into two 46-column
  lines (`MutatorConfigurationScreen.wrap`); example strings were shortened to fit.
  Informational notices clear when focus moves; errors such as "Save failed"
  stay visible until the next action, so the save-failure hold is unchanged.
- [x] The moving focus no longer hides the previous row's label: row backgrounds,
  then focus, then text. Rows extend from the right in a short cascade after a
  page change; sliders mark the native default in gold with a knob, and an
  adjustable focused value shows `< >`.
- [x] Play-hold configuration dims the held native frame instead of covering it.
  The screen never enabled `GL_BLEND`, so every sub-1 alpha had been opaque; it
  now blends with the caller's state restored and destination alpha preserved.
- [x] Navigation SFX: see below. No requested/admitted/effective, rewind, save,
  fade or command semantics changed; lifecycle CSV equals the handed-off capture.

**Navigation cue.** A per-frame probe of the Sonic 2 request latch on the real
capture path showed the title's `Obj0E_FlashingStar_Move` re-triggering
`SndID_Sparkle` (`$A7`, `zSFXPriority` `$70`) every 19 frames from frame 468 to
637. `SndID_Blip` (`$CD`, `$6F`) is rejected while one plays. That is native
arbitration, the same the ROM title menu's (`Obj0F`) blip receives (the engine's
stock title has no such menu; Start exits it), so frames 600/602
stay silent and are documented, not "fixed". A separate engine defect was found
behind it: `TitleScreenManager` called `AudioManager.stopAllSfx()` when the star
was deleted and in `skipToFinalState`; neither `Obj0E_FlashingStar_Move`
(`s2.asm:26748-26750`) nor `TitleScreen_SetFinalState` (`:27068-27162`) stops
sound. The forced stop bypassed `cfStopTrack`'s priority reset, leaving the
latch at `$70`, so every later blip a mod plays over the stock title was
rejected until another `$70`+ sound ended. Level music start clears the latch,
so the stock title-to-level path itself was unaffected. Both calls are removed; `TestTitleScreenAudioRegression` failed
before the change (latch 112) and passes after it.

Matched PCM controls (outside-repository `opus-polish/captures/cue-*`) wait 700
frames, then press Down at 700 and 702. After the fix the PCM prefix is identical
to its neutral control through frame 699, then the blips contribute AC RMS
712.192 (700–702) and 695.297 (702–704). Before the fix the entire 765-frame run
is bit-identical to its neutral control. The two neutral controls differ only in
656–699, the last twinkle's tail. In the maintained walkthrough the fix changes
PCM only at 656–699. No physical speaker output is claimed.

**Native Help Back.** The earlier Help run's video shows Help about 1.7 seconds
after its Return key, and the run closed 1.8 seconds after Escape, so no Back
result could yet be visible. The earlier unmanaged walkthrough's
`how-to-play.png` and `configuration.png` both show Home, while the next page
proves every key in between was processed. Neither run establishes a routing
defect. `Engine.applyWindowActivation` pauses and clears keys on GLFW focus loss,
so the window helper now records X input focus at each key and can fail closed
with `require_focus`. The polish run on the unmodified `Engine.loop` (owned
frameless mapping, child-only Pulse/keyutils/vblank settings as before) sent 38
keys, all with owned focus, and allowed about three seconds before each
screenshot: Help at 26.2 s, Escape, then Home at 29.7 s. It also shows the
configuration, Gravity 50%, held play dimmed under the menu, Stealth hiding
Sonic after Resume, and Return to game hub reaching game select; close exit 0
and every owned process and sink were removed. This observes native Back; it is
not default window-manager, physical controller or speaker certification.

Validation (proportionate focused; the runner's plan falls back to the full
suite only because example/tool paths are unclassified): one normal queued
invocation of 35 consumer classes with all three original ROM paths: the
Mutator, S2 title, S2 request/oracle, S2-title mod (Survivors, Putt Putt),
public API and documentation-link classes. 478 tests: 475 pass, one opt-in
`TestSonicSurvivors.balanceProbe` skip, and two failures in
`TestPuttPuttParadise.normalDevelopmentBootUsesRealTitleRouteAndCompletesOverlayWhileAimHolds`
(`[1]` and `[2]`, line 1090, `expected: <true> but was: <false>`). Base source
in the same tree fails identically: this tree has no working-directory `s2.gen`
link, which the test's default configuration resolves, while the earlier full run
used a tree with hook ROM links. The failure is environmental and not caused by
this change. A fresh `-Pguards` JVM ran 86 classes, 672 tests, with no
failures, errors or skips. `tools/media` unit tests pass. This is not a
full-suite pass. Commits: title fix `a2b89a071`, helper focus record
`89e5e3689`, then the presentation polish and this record.

Rejected: delaying menu input until the twinkles end (a fitted title timing in a
shared screen); a louder navigation cue to beat `$70` (inauthentic and still
subject to arbitration); a global `stopAllSfx` priority repair (no production
caller remains, and audio-wide changes are out of scope).
