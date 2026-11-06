# Mega chain segment 3: Slots clock and premature source exit

Base: `cff5686b490c0f5b5ef3cf02adf26b59cc55a9e5` (`origin/develop` at lane start).
This is a found-not-fixed investigation. No runtime, comparator, cursor,
fixture or ownership policy changes are proposed.

## The ownership failure is downstream

The manifest uses zero-based segments. Segment 2 is `aiz_2`, whose recorded
entry is AIZ1 and which crosses into AIZ2. Segment 3 is `slots`, not a level
or title-card segment. Segment 4 is the returned AIZ2 (`aiz_3`). Engine act
indices are zero-based: `act=1` in the failure means AIZ2.

`AbstractRunChainTest.HeadlessRunCoordinatorAdapter` captures the production
owner before a step. It forwards that mode and identity, together with the
current cursor/exhaustion state, to `TraceRunPlaybackCoordinator.afterStep`
and `afterProduction`. This allows the final step to publish under its source
owner even when it changes mode. It does not allow another mode to keep
producing a bonus segment's rows. `ownsCurrentSegment` requires a bonus
segment's mode and bonus identity to match. Source closure also requires
completion of its comparator publication; reaching an early stage exit cannot
stand in for the unconsumed source rows.

At cursor 10103 the pre-step production owner is already TITLE_CARD with the
returned AIZ2 identity, while segment 3's recorded end is cursor 10342. The
failure correctly rejects this. Widening bonus ownership to TITLE_CARD would
let a returned level produce rows that belong to Slots, concealing divergence.

## Executed cause evidence

Temporary, read-only prints at the option-cycle state dispatch and cage state
changes were enabled with a local diagnostic property. They were removed
before final validation. Both the continuous chain and the standalone
`TestS3kSlotsBonusTraceReplay$Knuckles$Segment1` use the same Slots recording
(offset 9142, 1200 rows; standalone directory `bonus_slots`).

| Observation | Continuous Mega chain | Standalone Slots |
| --- | --- | --- |
| Initial option-cycle clock | 9002 | 9097 |
| Initial spin resolves | 9006, prize 100 | 9101, prize 100 |
| Cage captures player, level counter | 47 | 47 |
| Target selection clock, state 8 → 12 | 9050 | 9145 |
| Deceleration → locking clock | 9056 | 9151 |
| Locking → resolved clock / prize | 9170 / 0 | 9379 / 10 |
| Cage enters release state, level counter | 168 | 327 |
| Cage launches player, level counter | 172, angle $00 | 332, angle $80 |

The chain's first non-camera physics error is precisely the launch at row172:
ROM y speed $0000, engine $0400. ROM `interact_state` independently holds
`object_control=$81` from row47 through331 and releases at332. The standalone
replay matches that release and passes (one test, no skips).

The recorded pre-row-zero V-int is9097; row zero carries $238A (9098).
The chain starts its option system 95 V-ints behind. This precedes the first
physics mismatch, changes the clock-dependent reel computation, resolves a
zero payout instead of ten rings, and changes the cage release trajectory.
The later title-card ownership loss is a consequence. The same production
Slots implementation reproduces the recording when started independently at
its own legitimate initial bootstrap boundary.

The target-selection branch also distinguishes the clocks without fitting a
constant. At9050 the low byte is$5A; the ROM's byte rotate-right-three yields
$4B (75), beyond the74 fixed-table weight, selecting the RNG fallback. At9145
it is$B9, rotated to$37 (55), selecting the fixed$05/$55 target row in
`byte_4C8B4 (sonic3k.asm:100236-100244)`. The latter resolves the ten-ring
prize. This is source-derived arithmetic on the observed branch inputs; the
executed state/payout prints above corroborate its consequence.

This establishes the upstream clock deficit and executed downstream mechanism,
not which earlier transition lost each of the95 V-ints. The deficit must be
traced back through the represented entry/return/load loops before changing
clock service. Neither copying segment3's recorded counter into the chain nor
adding95 ticks is a supported fix. No causal claim is made that all95 originate
in a single title card, or that the number generalizes to another movie.

## Shipped ROM ownership

`LevelLoop (sonic3k.asm:7916-7954)` waits for VSync, increments the level
counter, runs sprites and tests `Restart_level_flag`. A restart jumps to
`Level`; it does not wait for a trace segment boundary. Slots reel options run
at the end of that same LevelLoop, after sprite processing.

`loc_4C416 (sonic3k.asm:99685-99707)` seeds reel words from the global
`V_int_run_count`. `loc_4C480 (sonic3k.asm:99724-99758)` derives reel velocities
and target selection from that clock; `loc_4C4F8 (sonic3k.asm:99766-99784)`
also mixes its word with RNG on the random-target path. The locking countdown
reads the clock at `loc_4C54C (sonic3k.asm:99795-99805)`.
`VInt_Done (sonic3k.asm:558-559)` increments this global counter; a level-local
frame counter is not a substitute.

`loc_4C026 (sonic3k.asm:99426-99459)` captures the player and starts the reel
cycle. `loc_4C21C (sonic3k.asm:99557-99570)` waits for resolution and then
enters payout. `loc_4C250 (sonic3k.asm:99579-99600)` launches only when the
rotation angle passes its mask, clears object control and reverses rotation.
The clock therefore changes gameplay, not just presentation.

`loc_4BC54 (sonic3k.asm:99027-99052)` restores the saved zone/act, clears the
bonus-entry flag, and sets `Restart_level_flag` after the goal fade. The
subsequent level load sets Game_mode bit7 at
`Level (sonic3k.asm:7536-7541)`. Its locked title loop
`loc_62CC (sonic3k.asm:7769-7781)` services VSync, sprites and art queues.
`loc_6468 (sonic3k.asm:7880-7915)` performs the initial sprite pass, arms the
surviving title owner, and clears bit7 before LevelLoop. Title ownership can
survive into LevelLoop: `Obj_TitleCardWait2 (sonic3k.asm:62289-62340)` waits
for its counter and children before loading enemy art and deleting itself.
Thus a post-return AIZ2 title card is expected; entering it while the recording
is still in Slots is the problem. It is not evidence that TITLE_CARD should
own a bonus segment, or that this failure occurs at the AIZ1→AIZ2 act advance.

## Segment 2 is an independent frontier

The unchanged returned-AIZ comparator reports112 errors:111 in animation,
one in physics (camera Y). At row186 the first error is player animation
ROM$00 versus engine$20; position, velocity, ground speed, status and camera
agree there. Mapping-frame differences follow through row251. The only
physics-group error is camera Y at2002: ROM$025C, engine$025B.
No non-camera player physics mismatch is reported for segment2. The later
Slots capture matches through row171 and its launch error follows the
clock-dependent cage path above. No evidence links these presentation errors
to the global V-int deficit. Leave them as the next independent animation /
one-pixel camera frontier; this investigation does not fix them.

## Validation

No behavior changed between the before and final runs: the final source tree
matches the pinned base byte-for-byte outside this audit. Temporary probes only
printed state and have been removed. The full-profile conditional gate for
shared code changes does not apply; no full profiles or full ordinary suite
were run. These are bounded, explicitly selected chain/prefix checks.

| Class | Tests / failures / errors / skips on unchanged source |
| --- | --- |
| TestKis2CompleteEmeraldRunChain | 2 / 1 / 0 / 0 |
| TestS1CompleteEmeraldRunChain | 1 / 1 / 0 / 0 |
| TestS1CompleteEmeraldRunPrefix | 2 / 0 / 0 / 0 |
| TestS1GhzMazeRoundTripChain | 2 / 0 / 0 / 0 |
| TestS2CompleteEmeraldRunChain | 1 / 1 / 0 / 0 |
| TestS2CompleteEmeraldRunPrefix | 1 / 0 / 0 / 0 |
| TestS2EhzHalfpipeRoundTripChain | 2 / 1 / 0 / 0 |
| TestS3kBonusRoundTripChain | 2 / 0 / 0 / 2 |
| TestS3kSonicTailsCompleteEmeraldRunChain | 1 / 0 / 1 / 0 |
| TestS3kSonicTailsCompleteEmeraldRunPrefix | 3 / 0 / 0 / 0 |

R6 totals:17 tests,4 failures,1 error,2 skips. Maven execution224 seconds,
queue wait771 seconds (reported independently). The two BonusRoundTrip skips
are absent optional run directories `s3k-aiz-gumball-roundtrip` and
`s3k-aiz-pachinko-roundtrip`, not missing ROMs. The Sonic/Tails full-campaign
error is the manifest's absent recorder-observed `expected_movie_end_mode`.
KIS2 stops at segment14's missing level-load boundary (cursor65856), with
segment2 rings first diverging at826. S1 reports queue differences starting
in segment12 at101. S2 stops at segment19 destination admission (cursor113342,
offset113340), with segment19 sidekick Y first differing at5084 and dynamic-art
gap mismatches. The EHZ halfpipe witness fails its two dynamic-art return gaps
(first gap expected logical frame9675, actual9610). None is a candidate
regression: these measurements run the unchanged base implementation.

| R7 class | Tests / failures / errors / skips on unchanged source |
| --- | --- |
| TestS3kKnucklesSuperEmeraldRunChain | 2 / 0 / 1 / 0 |
| TestS3kMegaRunChain | 1 / 1 / 0 / 0 |
| TestS3kTailsFullChainRunChain | 1 / 0 / 1 / 0 |
| TestS3kTailsFullChainRunPrefix | 3 / 0 / 0 / 0 |

R7 totals:7 tests,1 failure,2 errors,0 skips. Maven execution94 seconds,
queue wait576 seconds. Both full-campaign errors reject manifests missing
recorder-observed `expected_movie_end_mode`; their companion prefix checks
remain independently executed. No complete-campaign pass is claimed.

| Mega segment / axis | Before | Final repeat after probe removal |
| --- | --- | --- |
| seg0 AIZ | 1 error, first4545 camera_y | same |
| seg1 Gumball | 16 errors, first1300 x | same |
| seg2 returned AIZ | 112 errors, first186 player_animation_id | same |
| seg3 Slots | 7723 errors, first172 y_speed | same |
| Walk | TITLE_CARD ownership loss, cursor10103 | same |

The final Mega Surefire failure still contains exactly the two requested axes.
Group totals also match: seg2 physics1 / animation111; seg3 physics7428 /
animation295. This is unchanged-source repeat evidence, not a successful fix
or a claim to have compared every error tuple. There is no changed behavior
requiring the added-error attribution gate. No comparison was suppressed or
rebaselined. The passing standalone witness is the discriminating control for
the proposed clock mechanism; no corrective candidate exists to revert.

Citation checking (`--strict`) and staged whitespace checks pass. Only this
audit is committed; runtime, shared run-chain code, fixtures, comparisons and
frontier log remain untouched. The requested lane delivery is a branch push
for lead integration, not a merge into develop. The next investigation should
sample production clocks at earlier entry/load/return boundaries, localize the
first deficit, then derive the missing service from the owning ROM loops.
Total delivery wall time was not measured; queue times above are separately
observed, not test execution time.

## Reproduction commands

All measurements use the lane environment and ROMs (the standalone diagnostic
needs only S3K). Diagnostics set `-Dopenggf.debug.slotSurvey=true` while the two
temporary print sites exist; this property has no committed implementation.

```bash
unset DISPLAY WAYLAND_DISPLAY JDK_JAVA_OPTIONS MAVEN_ARGS LD_LIBRARY_PATH
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
export JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl
export PATH="$HOME/.local/bin:$PATH"
R="$HOME/code/projects/OpenGGF"
rom_flags=("-Dsonic1.rom.path=$R/s1.gen" "-Dsonic2.rom.path=$R/s2.gen"
  "-Ds3k.rom.path=$R/s3k.gen" "-Dkis2.rom.path=$R/kis2.gen"
  "-Dsonic1.rev00.rom.path=$R/s1-r0.gen" "-Dsonic2.rev00.rom.path=$R/s2-r0.gen")
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestS3kMegaRunChain "${rom_flags[@]}" test
# Diagnostic standalone control (same environment):
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestS3kSlotsBonusTraceReplay$Knuckles$Segment1' \
  -Dopenggf.debug.slotSurvey=true "-Ds3k.rom.path=$R/s3k.gen" test
# Other chains/prefixes, with the Mega repeat included in r7:
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestKis2CompleteEmeraldRunChain,TestS1CompleteEmeraldRunChain,TestS1CompleteEmeraldRunPrefix,TestS2CompleteEmeraldRunChain,TestS2CompleteEmeraldRunPrefix,TestS3kSonicTailsCompleteEmeraldRunChain,TestS3kSonicTailsCompleteEmeraldRunPrefix,TestS1GhzMazeRoundTripChain,TestS2EhzHalfpipeRoundTripChain,TestS3kBonusRoundTripChain' \
  "-Dsonic.rom.path=$R/s1.gen" "${rom_flags[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestS3kMegaRunChain,TestS3kKnucklesSuperEmeraldRunChain,TestS3kTailsFullChainRunChain,TestS3kTailsFullChainRunPrefix' \
  "${rom_flags[@]}" test
python3 tools/disasm/disasm_citations.py check --strict \
  docs/architecture/audits/2026-11-03-s3k-mega-slots-clock.md
```

The initial Mega measurement and probe repeat both fail with the same reported
frontiers. The diagnostic repeat used S3K's absolute ROM flag and the print
property; no other game executes in that class. Before the first measurement
this fresh worktree had no Surefire/trace reports. Subsequent runs reuse the
same report owners for unchanged payloads; results are attributed to completed
invocations, not to filenames alone.

## Frontier-log text for the integrating lead

2026-11-03 — Mega segment3 ownership investigation, base cff5686b4:
segment3 is Slots, not the AIZ2 title entry. Continuous run starts Slots option
clock at9002 instead of9097; executed state probes show target draw9050 versus
standalone9145, payout0 versus10, and cage release172 versus332. ROM
interact_state holds object_control=$81 until332. Segment3 first physics error
is row172 y_speed ROM$0000 / engine$0400; later TITLE_CARD at cursor10103
correctly fails source ownership before recorded closure10342. The standalone
same-recording Slots witness passes. No cursor seek, counter hydration,
ownership relaxation or runtime fix. Origin of the upstream95-V-int deficit
within earlier transition accounting remains open. Segment2 independently
retains111 animation errors (first186, anim ROM$00 / engine$20) and camera_y
at2002 (ROM$025C / engine$025B). See this audit for validation and limitations.
