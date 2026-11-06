# S3K post-return KosM queue investigation

Base: `e4b8857a8` (origin/develop at task start). Scope: the reported
`kosm-stuck-after-return` cluster. No FBZ zone/object code changed.

## ROM ownership and the actual defect

`loc_5FC4 (sonic3k.asm:7543-7547)` clears direct Kosinski, KosM and Nemesis
work on entry to Level. The initial level PLC and active-character PLC precede
`loc_62CC (sonic3k.asm:7769-7781)`, whose locked loop services the direct queue,
VSync, title sprites, Nemesis and module queue. The title queues four archives
in `Obj_TitleCardInit (sonic3k.asm:62131-62205)` and waits for KosM retirement
before creating children. Once its gates open, Level loads terrain through
`loc_6310 (sonic3k.asm:7789-7794)` / LoadLevelLoadBlock.

`loc_6468 (sonic3k.asm:7880-7915)` performs initial Process_Sprites and then
sets the surviving title owner's Wait2 counter to $16. The first LevelLoop
VSync/object iteration is still pending. `Obj_TitleCardWait2 (sonic3k.asm:62289-62340)` drains that counter and the higher-slot children,
then calls LoadEnemyArt. `LoadEnemyArt (sonic3k.asm:64321-64353)` appends the
actual zone/act enemy archive batch. `Process_Kos_Module_Queue (sonic3k.asm:2758-2823)` starts a module or, after the direct FIFO drains,
queues DMA and retires/shifts the parent. No change to that production owner
was required.

The chain harness's compared-bonus-return Option B instead sought to
`returnOffset + 1` and declared one row consumed. Production's title release
was setup-only. This skipped destination row zero without executing its object
pass. The fix seeks to returnOffset and attaches comparison at zero. It neither
adds a synthetic service nor changes readiness admission.

Executed instrumentation observed the returned AIZ provider still unarmed at
level counter 34 and consuming TITLE_OWNER at counter 35, after raw row 34's
completion had already found no direct child. Overflow retained:

| Parent | Source | Destination | Child |
| --- | --- | --- | --- |
| KosM #21 | $36800C | $A900 | direct #33 |
| KosM #22 | $367DCA | $A540 | not started |
| KosM #23 | $3681FE | $ABE0 | not started |

Their independently produced fingerprints match the recording's enemy-art
batch exactly. The first child is `c3e8ddd3…`; its completion is raw34
PRE_MAIN_LOOP and its parent `65c8c371…` completes at raw35 POST_OBJECTS.
After restoring row zero, the ordinary production owners submit and retire
this batch. The timing port creates or retires no work. The
[hardware-timing authority boundary](../designs/2026-07-27-cross-game-hardware-timing-trace-contract.md)
and four-entry tripwire remain unchanged.

## FBZ witness disproves the common-cause hypothesis

Tails FBZ2 has already retired its entry enemy parents #529–531. Its stuck
head is instead parent #532, source $0DB406 / VRAM $B400, submitted by
Sonic3kSSEntryRingObjectInstance through queueBadnikExplosionArt. Child #805
has fingerprint `3c96d8b9…`. The recording assigns parent #532 to miniboss
art (`c71c8ae2…`, source $1652B4), with child #805 `40791c75…` at raw1275.
The engine's miniboss consequently becomes parent #533 and later results art
fills the remaining slots. This is an extra production ring-restoration
submission, not a post-return batch that failed to retire. Why that ring exists
or retires on this standalone route remains for its owning fleet; no suppression,
renumbering, queue enlargement or FBZ fix is included.

## Measured validation

| Class / segment | Before | Candidate |
| --- | --- | --- |
| TestS3kMegaRunChain, seg0 | 1 camera error, row4545 | unchanged |
| same, seg1 gumball | 16 errors, first row1300 x | unchanged |
| same, seg2 returned AIZ | 12,260 errors; row34 queue busy; throws at row1550 | 112 errors; first row186 animation; no non-camera physics error; reaches next bonus |
| same, seg3 | not reached | new coverage: separate production-ownership failure, first mismatch row172 y_speed |
| TestS3kTailsFullChainFbz2SegmentTraceReplay | FIFO exception after 3270 rows; no completed comparison report | same stuck identities and exception |

Both witness runs execute two tests without skips; Mega changes from an error
to a failure at a later frontier, and FBZ remains an error. These are not green
trace passes. The 112 returned-AIZ errors are 111 animation errors plus a
camera-Y mismatch at row2002, beyond the old incomplete stop at1550.

Negative-control/error-list gate: passed. Temporarily restoring the skipped-row
behavior reproduces exactly 12,260 returned-AIZ errors and the row1550 FIFO
throw. Reapplying the fix gives 112 errors. Diffing every error tuple
(row, field, ROM value, engine value) removes 12,149 errors, first at row34
queue busy; all 111 animation errors are identical. There are no additions in
the previously compared rows. The only added returned-AIZ error is camera Y
at row2002, beyond the old stop and after the existing row186 animation
frontier. At row2002 the comparison records:

| State | ROM | Engine |
| --- | --- | --- |
| Player x, y | $02E6, $02A0 | $02E6, $02A0 |
| Player x speed, y speed | $005C, -$04B0 | $005C, -$04B0 |
| Sidekick present | false | false |
| Camera x, y | $0251, $025C | $0251, $025B |

No new early divergence or newly red class appears in the measured witnesses.
The next bonus segment is newly reached coverage, not a previously green
segment. Both diagnostic halves ran one test without skips. The temporary
observer and the abandoned provider probes are absent from the change.

The focused ordinary command passed 146 tests, zero failures/errors/skips:
TestS3kKosModuleQueue (including non-mutating overflow diagnostics),
TestHardwareTimingAuthorityGuard, TestTraceRunReplayWalkerControlFlow,
TestS3kAiz1SkipHeadless, both TestSonic3kLevelLoading classes,
TestSonic3kBootstrapResolver and TestSonic3kDecodingUtils.

Initial broad baselines completed r6: 886 tests / 13 failures / 1 error /
7 skips; r7: 117 / 83 / 12 / 0. The pending segment baseline was cancelled
before admission after diagnosis established that the correction is confined
to test-harness cursor accounting. There is no production scheduling fix, so
the shared-runtime three-profile before/after gate is not applicable. No
three-profile after result or full-suite pass is claimed. The production
queue edits are diagnostic only: local host submission receipts are outside
rewind state and never consulted for queue service or admission.

## Commands

All commands unset DISPLAY, WAYLAND_DISPLAY, JDK_JAVA_OPTIONS, MAVEN_ARGS and
LD_LIBRARY_PATH; set JAVA_HOME=/usr/lib/jvm/java-25-openjdk,
LUA_BIN=/usr/bin/lua5.4, JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl,
and prepend $HOME/.local/bin to PATH. ROM arguments were:

```bash
R="$HOME/code/projects/OpenGGF"
rom_flags=("-Dsonic1.rom.path=$R/s1.gen" "-Dsonic2.rom.path=$R/s2.gen"
  "-Ds3k.rom.path=$R/s3k.gen" "-Dkis2.rom.path=$R/kis2.gen"
  "-Dsonic1.rev00.rom.path=$R/s1-r0.gen" "-Dsonic2.rev00.rom.path=$R/s2-r0.gen")
# Initial complete baselines, each on the pinned unchanged base:
python3 "$R/tools/testing/maven_queue.py" -Dmse=off -B -Ptrace-replay \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  "${rom_flags[@]}" "-Dsonic.rom.path=$R/s1.gen" test
# Same command with -Ptrace-replay-r7; trace-segments cancelled while queued.
# Before/after witnesses in the task development checkout:
# The first diagnostic before run also passed -Dopenggf.debug.kosm=true.
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestS3kMegaRunChain,TestS3kTailsFullChainFbz2SegmentTraceReplay' \
  "${rom_flags[@]}" test
# The negative/reapplied-fix pair uses the same command with:
# -Dtest=TestS3kMegaRunChain
# Its temporary comparison-only observer records existing FrameComparison
# values; it is removed afterwards, and changes no comparison or gameplay.
python3 tools/testing/maven_queue.py -Dmse=off -B \
  '-Dtest=TestS3kKosModuleQueue,TestHardwareTimingAuthorityGuard,TestTraceRunReplayWalkerControlFlow,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "${rom_flags[@]}" test
```

The abandoned title-owner hypothesis's queued negative unit check was
cancelled before execution; its temporary test and provider probes were removed.

## Frontier-log entry for the integrating lead

2026-11-03 — S3K post-return KosM investigation, base e4b8857a8:
TestS3kMegaRunChain (-Ptrace-replay-r7), returned aiz_2, 12,260 errors /
row34 queue.s3k_kos_module.busy / FIFO throw at1550 → 112 errors /
row186 player_animation_id / reaches next gumball (later production-ownership
failure). Cause: compared-bonus-return Option B skipped row0 after setup-only
title release. No production queue scheduling or timing-authority change.
Negative control reproduces the throw; error-list diff removes12,149 queue
errors, preserves111 animation errors and adds only camera_y at2002 beyond
old coverage (ROM$025C / engine$025B; player/sidekick state matches).
Tails FBZ2 remains unchanged: extra ring-restoration parent532 precedes the
recorded miniboss parent532, so this is a separate producer/entry frontier.
Focused guards/loading:146 passed,0 skipped. See this audit for commands,
baselines and coverage limits. The lead owns docs/status/trace-frontier-log.md;
this task does not edit it.
