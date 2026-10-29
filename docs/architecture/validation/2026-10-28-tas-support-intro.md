# S2 introductory-text P2 Start investigation — 2026-10-28

Scenario: `s2-intro-p2-pause:1`. Implementation base:
`8945c0a789b27be57b2361c70f561f31872a76ca`. This worker owns only the
intro/title investigation, its precise tests/probes, and this record. The parent
owns classification, discrepancy/changelog reconciliation and combined checks.

Result: the representative mechanism is implemented and qualified. Recommend
promoting this same scenario ID from its historical limitation to supported
within the route limits below. No shared art/PLC, host rewind or hardware model
was added.

## Question and historical evidence

[5735S author notes](https://tasvideos.org/5735S), published as
[3584M](https://tasvideos.org/3584M), report a one-frame saving from P2 Start on
the last visible introductory-text frame and an incorrectly loaded title.
The downloaded original GMV SHA-256 is
`b2e1971907e6938e4e73f944b203dcefb7f8164e5a8852f09b5badc00407a49c`.
Its zero-based input rows include P2 Start at 245, 380 and 403, followed by P1
Start at 404. These are movie input rows, not synchronized engine ticks or
completed native object updates.

The historical engine test deliberately reaches its modeled text fade timer
21/22. Neutral and P2 Start both take 17 updates to `ACTIVE`. Preserve that
observation: it does not identify the native inlet, prove the native fade
duration changes, or establish title output correctness. No old defect should
be restored to retain the old limitation wording.

## Immutable native source audit

All citations use the superproject's S2 gitlink
`34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`, read through `git show`.
The assigned checkout's unrelated dirty disassembly references are preserved.
The supplied original `s2.gen` (absolute path recorded in external provenance) matches
SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.

- `TitleScreen` (s2.asm:4280-4330) loads the credit text, fades it in, loads art
  with interrupts disabled and fades it out; these steps contain no input skip
  branch.
- `Pal_FadeToBlack` (s2.asm:3370-3383) runs 22 palette/PLC iterations, without
  an input-dependent counter branch. `Vint_Fade` (s2.asm:1068-1071) calls
  `Do_ControllerPal` (s2.asm:1153-1178), which samples both pads.
- `TitleScreen` (s2.asm:4402-4459) clears **only** `Ctrl_1`, then runs
  `RunObjects` and `BuildSprites` once before the title's palette fade. The ROM clear
  at `$3BA8` is byte-checked as `4278F604`.
- `Obj0E_Sonic` (s2.asm:26325-26338) calls the skip owner before dispatching its
  secondary routine. `TitleScreen_SetFinalState` (s2.asm:27068-27162) reads both
  pressed bytes, consumes their Start bits, installs the final objects, deletes
  the palette changer and writes the final normal palettes.
- `Pal_FadeFromBlack` (s2.asm:3262-3285) clears its normal-palette range and then
  advances only palettes and PLC work, without running title objects.

This yielded the subsequently confirmed hypothesis: a P2 edge surviving the final text
sample can reach the initial title-object update, whereas P1's edge is cleared.
It is not evidence for shortening the text fade or emulating a CPU/VDP.

## Base engine audit and implemented behavior

At the implementation base, `TitleScreenManager` approximated the intro with 22-update fades, a
96-update text hold and a 16-update title fade. `updateIntroTextFadeOut` ignored
input. `beginTitleFadeIn` unconditionally submitted the initial sparkle, and
`updateFadeIn` advanced the title objects while the ROM palette fade does not.
`skipToFinalState` selected final palettes from static loader resources. There was
no normal/target palette owner for the pre-fade overwrite described above.

The implementation runs the initial title-object update before the global
palette fade, admitting only the final text sample's P2 pressed edge. It does
not shorten either text fade. The title palette fade now runs its native 22
iterations without updating title objects or scrolling. The first object update
sets the existing object clock to 1; that clock remains frozen during the fade.

An early final-state branch precedes the initial sparkle and writes the final
normal palettes. The following fade clears those writes. The existing
`introComplete`/`sonicPaletteLoaded` state expresses that ownership; no new
mutable input latch or snapshot adapter is introduced. Rendering preserves the
ROM-backed target line 1 and clears normal-only lines 0/2/3. A post-fade skip
retains the normal palettes. Reinitialization refreshes the existing caches.

`TitleScreenManager` supplies no snapshot/capture/restore contract. The live
rewind owner currently admits level and special-stage steppers, not title
screens. This is an inherited limit, not a blocker to qualifying the semantic
title behavior with a real session, reused boot and precise local state checks.
No new title-host rewind subsystem is in this stream's scope. The actual title
provider is reused across three lifetimes in the pixel regression instead.

## Native experiment and validation

The first display-free GPGX batch replayed the original movie's first 620 input
rows, changing only the row-380 pulse for neutral, row-379 early, row-381 late,
held 379–381 and P1-only controls. Every case produced 735 observation rows,
including 470 pad-sample hooks, one title input clear, 31 title loops and 101
published-frame observations. The P2 Start pulse at 245 was positively sampled
as `$8080`; observer dispatch and both-pad input were working.

All six cases reached the actual initial title object at row 567 and the first
ordinary title-object update at 590. The old Gens movie's row-380 pulse did not
reach that inlet on this runtime. No case completed the intro or launched the
level within this window. Equal state/pixel observations are **unreached-control
evidence**, not evidence that the P2 mechanism is absent. The first batch waited
3438.135 seconds for admission and executed for 23.425 seconds. No setup RAM
writes were made; the verified runtime dependency binding remained unchanged.

Detailed pad hooks identify the final text-fade sample at **565**, with the fade
counter at zero. There are no pad reads at 566–567 while the plane maps load;
the initial title object runs at 567. Initialization time and input-copy time
are different boundaries. The published frame at original row 380 was also
visually reviewed: it still shows the Sega logo, not the credit text.

The bounded follow-up uses neutral ingress after the original 245 pulse, six
alternatives around sample 565 (boundary, neutral, early 564, late 566, held
564–566 and P1-only), and identical P2/P1 title-loop Start controls at 590/591.
Each case is bounded to 630 native frames. An inlet assertion requires the
initialization hook to observe the independent expected P2 held/pressed word:
`$8080` for the boundary pulse, `$8000` for held Start and `$0000` for the
remaining controls. The P1 clear is inspected at the following object hook,
because the clear-instruction hook runs before that instruction.

An earlier queued follow-up incorrectly used initialization 567 as the input
boundary; it was canceled before execution. A queued version of the corrected
probe also had a P1-zero assertion at the pre-instruction clear hook; that guard
was removed before execution. Neither canceled request produced native
observations or supports a negative verdict. The actual follow-up inputs are
authored comparisons, not a canonical original-movie replay and never engine
timing/input authority.

All six corrected inlets were reached and their assertions passed. The batch
completed 630 frames per case, producing 554 observation rows for the early-skip
case and 560 for each control, plus 61 published images per case. Admission wait
was 134.118 seconds and execution 19.275 seconds. No RAM setup writes or native
patches were used; the runtime dependency fingerprints remained unchanged.

| Input at final sample | P2 word at initialization | Initial intro-complete | Level/title-card handoff |
| --- | --- | --- | --- |
| Fresh P2 Start at 565 | `$8080` | `$FF` | row 590, mode `$8C` |
| Neutral | `$0000` | 0 | row 591, mode `$8C` |
| P1 Start at 565 | `$0000` | 0 | row 591, mode `$8C` |
| P2 Start at 564, released at 565 | `$0000` | 0 | row 591, mode `$8C` |
| P2 Start at 566, after the final read | `$0000` | 0 | row 591, mode `$8C` |
| P2 held 564–566 | `$8000` | 0 | row 591, mode `$8C` |

The P1-only control observes `$8080` before the clear instruction and `$0000`
at the subsequent initial object hook. Only the fresh P2 edge survives. All
cases use the same later P2 Start at 590 and P1 Start at 591. The saving is
**one title-loop iteration of launch eligibility**, not a shorter fade.

Independent ROM palette expectations match the observed normal-palette hashes:

- Final normal lines written before the entry fade, with line 1 still zero:
  `20ce7b02c87c11a5e3db568c452997a43fa97caa5044b6fd1fb53fb7d1b818c6`.
- After that fade, only target line 1 survives:
  `746e52495d9ef0e2367ac1c3cdc18669b13950e3a6073a708bd452801de756f8`.
- An ordinary post-fade skip restores all final lines:
  `0c430d9abd0d5c23b3525ab3c921b1b3db7bfafc2faa90b0faeb1eb46ea70c39`.

The reviewed corrupted native frame shows **Tails alone on black**, with 3069
nonblack pixels. Native Tails frame 4 has 13 pieces, all selecting palette 1;
that palette survives. The ordinary control shows the full title, and all five
controls have identical published pixels at 591. Published VRAM differs only
in the sprite-table range `$F800–$F98F`; art bytes `$0000–$BFFF` are identical
(SHA-256 `7f6db236ff3c4a52648c8edd25a99b4c7d155dc1019731ef26a0e35fabfd6bbb`).
This is no evidence of a shared art-slot alias. Frame-end memory alone is not
being used to claim earlier scanline residency.

Java 25 `test-compile` passed on the unchanged implementation base in 1m36s.
The original focused engine limitation test then passed: 1 test, 0 failures,
0 errors, 0 skips; its actual two title sessions still take 17 updates for both
neutral and P2. Maven reported 35.497 seconds execution after a reported 3829
seconds queue wait; the selected test itself reported 0.868 seconds.

The new regression was observed red on unchanged production code:
`p2FinalTextSampleLetsTheFirstTitleLoopStartLaunch` failed because initial
`introComplete` was false (1 test, 1 failure, 0 errors/skips). Maven execution
was 33.684 seconds after 112 seconds reported queue wait.

Post-fix request 73570 was canceled before execution: exit 130, last reported
wait approximately 60 seconds, zero tests executed, unknown external cause.
Neither worker nor parent requested that cancellation. This is incomplete
validation, not a test failure or policy decision. The parent authorized one
exact recovery with unchanged source, flags, ROM, backend and resources.

That recovery ran 36 cases: 30 passed, 6 failed, 0 errors/skips (reported queue
wait 50 seconds, Maven execution 1m34s). All six failures were the new helper's
incorrect expectation that the host game mode changes immediately. The actual
host commits `EXITING`, then executes its existing fade callback before loading
the first act. The helper now checks both that exact committed iteration and
the eventual actual playable first-act load. No shared host code changed.

The affected `TestTasS2IntroP2Start` class was rerun: **8 tests, 0 failures,
0 errors, 0 skips**, 1.509 seconds test time, Maven execution 1m43s, zero reported
queue wait. The 28 supporting title/audio cases passed in the recovery and
were not repeated: no runtime logic changed afterward, only the launch helper
and citation wording. This is qualification across those two runs, not a claim
that one 36-case invocation was green.

The 8 intro cases cover the fresh-P2 positive, all five native controls,
unchanged text-to-interactive fade duration (both 23 host updates), frozen
object/scroll clocks during the palette owner, actual first-act launch, real
EGL pixels, a surviving Tails control against an all-black false success, and
corrupted → ordinary → corrupted reuse of the same provider/ROM/GL context.
The reused capture retains the same `NullAudioBackend`; synthesis and title
sound ownership are separately covered by the supporting intro/audio tests.

Supporting counts were: intro input phases 3, native title art 3, native title
controls 3, Sega fade timing 6, title preview 7, star timing 1, title audio 5.
Every supporting case executed; none skipped. XML summaries were inspected
before consuming the task-owned Maven diagnostics.

## Exact checks and tested source

All Maven checks ran in the assigned worktree, with Java 25 at
`/usr/lib/jvm/java-25-openjdk`, `LUA_BIN=/usr/bin/lua5.4`, and unset display
variables. In the commands below `ROM` resolves to the supplied original
absolute `s2.gen` path recorded in native provenance; it was never copied or
linked. `INTRO_ARTIFACTS` is the external `intro/` task directory.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off test-compile

# Common environment/arguments for the following exact selectors:
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
ROM="$HOME/code/projects/OpenGGF/s2.gen"
INTRO_ARTIFACTS="$HOME/scratch/agent-tmp/codex/tas-engine-support-2026-10-28/intro"
Q=(env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=$ROM")

# Historical current-engine baseline; later, affected final 8-case check:
"${Q[@]}" -Dtest=TestTasS2IntroP2Start test
# New regression before implementation:
"${Q[@]}" '-Dtest=TestTasS2IntroP2Start#p2FinalTextSampleLetsTheFirstTitleLoopStartLaunch' test
# Original canceled request and its one authorized exact recovery:
"${Q[@]}" '-Dtest=TestTasS2IntroP2Start,TestSonic2IntroInputPhases,TestSonic2SegaScreenFadeTiming,TestSonic2NativeTitleControls,TestTitleScreenStarTiming,TestSonic2NativeTitleArt,TestSonic2TitlePreviewProvider,TestTitleScreenAudioRegression' test

# Native reference batches; these launchers use the immutable archived host:
python3 -B "$INTRO_ARTIFACTS/run_probe.py" original neutral p2-early p2-late p2-held p1-only
python3 -B "$INTRO_ARTIFACTS/run_input_latch_probe.py" boundary neutral p2-early p2-late p2-held p1-only
```

Native host source is TraceChaser pin
`703bd92d32634ce5ebac60625b48c60ef5500427`, built in the owned external directory
under build/activity leases. The explicit BizHawk 2.11.0.0 preflight passed.
Native execution used ordinary shared admission with the unmodified 7 GiB /
8-core reservation. Each invocation had one live request/observer. Queue waits
overlap and must not be summed as elapsed delivery time; total delivery wall
time and all unmeasured coordination intervals are not claimed here.

The comparison-only [native probe preserved in the implementation history](https://github.com/OpenGGF/OpenGGF/blob/8b7a7d33bc4f1c8f6bcb9c03cf0cc950064bae03/tools/bizhawk/S2IntroInputLatchProbe.cs)
is byte-identical to the observed source (SHA-256
`0e277c62c3a387645ced132f5d6d1b8f70360c6a98b1b83ad62bb748855ea425`).
Its fixed rows describe this bounded diagnostic, not engine admission logic.
The source is also retained with the external reference artifacts; no new
implementation remains under the legacy TraceChaser-forwarder directory.
Full source/runtime/input fingerprints and raw observations remain outside the
repository in the task's native provenance and output directories.

Final tested Java delta against the implementation base, computed with
`git diff 8945c0a789b27be57b2361c70f561f31872a76ca --` over the manager and its two
changed test classes, has SHA-256
`082197e97f41ea7f69584e1b69dd96288a084b8c40fef81e34670af1182c9b98`.
The runtime logic is unchanged from the 36-case recovery; the final focused
run includes the corrected helper and citation comments. No smoke, release
full suite or S3K gates were run by this worker; the parent owns combined gates.

The strict citation check on the manager, input-phase tests and this record
returned zero findings (10 `ok`, 4 `near`, 9 unverifiable anchors). The latter
count is a citation-tool coverage limit, not proof for those anchors. The new
native expectations also have the independent ROM-byte and palette checks
described above. Markdown local links, shell-block syntax and staged whitespace
were checked; the dirty disassembly references were not changed.

## Scenario and route limits

The ID stays `s2-intro-p2-pause:1`. The historical
`currentLimitationP2StartAtIntroBoundarySavesNoFrame` selector is retired in
favor of `p2FinalTextSampleLetsTheFirstTitleLoopStartLaunch`, supported by the
control and pixel cases in the same class. The old 17-update result remains
recorded above and in its original Git history; no defect was restored.

Qualification is a real 320-pixel S2 title session through first-act launch,
with the capture's Sonic/no-sidekick gameplay setting. It does not certify the
whole published Gens movie, original coldboot input-row alignment, every team or
viewport, long idle title animation, or pixel-identical intermediate fades.
Existing Sega visual/text-load approximations remain. Full title-host
rewind/capture/restore remains excluded; there is no claim of a new title rewind
feature. No native/trace observation hydrates engine gameplay, and no CPU,
VDP/beam/status loop, hardware protocol, arbitrary RAM overwrite or frame-fitted
admission has been added.

No tests or native probes remain active. Only the fully accounted-for local
task branch/worktree are retained for the parent. Main branch integration,
combined validation, central policy/changelog/classification/reel changes and
push/cleanup remain parent-owned.
