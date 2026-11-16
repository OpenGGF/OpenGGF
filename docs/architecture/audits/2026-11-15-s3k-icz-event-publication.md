# ICZ event publication — 2026-11-15

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f`. Scope: ICZ only.
## Owners and ROM evidence

- Events currently own shake flag/sample/applied sample and snow-fall state;
  the scroll handler owns Events_fg_0/1 loop history, intro auto-scroll phase,
  and a second indoor gate. Runtime state delegates reads to events but also
  recomputes indoor state without history. No ICZ collision-difference words.
- `ICZ1_BackgroundInit` (sonic3k.asm:110163-110201) initializes loop history.
  `ICZ1_IntroDeform` (sonic3k.asm:110379-110409) advances loop history and the
  auto-scroll longword; it removes X shake before scaling, then restores it.
- `ICZ1SE_Init` (sonic3k.asm:110141-110157) consumes the preceding shake sample
  horizontally; WaitQuake consumes it vertically. These are distinct axes.
- `ICZ1_BigSnowFall` (sonic3k.asm:110430-110461) writes the flag, never samples.
  `ShakeScreen_Setup` (sonic3k.asm:104234-104256) owns all three shake words;
  positive countdowns index contiguous tables, including beyond $14.
- ICZ1 intro/snowfall tail-call setup; refresh/normal and ICZ2 do not.
  `ICZ2_BackgroundInit` (sonic3k.asm:110710-110752) and
  `ICZ2BGE_Normal` (sonic3k.asm:110769-110855) own indoor selection/history.
  Initial X comparison differs from the old Java predicate: below $1000,
  Y >= $580 selects indoors. Preserve shipped logic, not the survey.
- `loc_8AC54` (sonic3k.asm:188894-188900) writes #$10 once on column landing.
  The Java object's private timer/boolean ownership has no ROM equivalent.

## Shake-active flag inventory (baseline Java)

These are two unrelated booleans, neither the ROM S3K shake word; exact bridge callers follow.

| Flag | S3K writers | S3K readers | Reset / ROM equivalent |
| --- | --- | --- | --- |
| `GameStateManager.screenShakeActive` | `Sonic3kICZEvents.updateAct1BackgroundEvent` clears; `updateBigSnowFall` sets (falling and landed); `IczSegmentColumnObjectInstance.updateTimedShake` sets/clears; `MgzMinibossInstance.setScreenShakeActive` sets/clears; `MhzEndBossInstance` sets; `Sonic3kLBZEvents` sets; `Lbz2RobotnikShipInstance` clears/sets | `Sonic3kLBZEvents`, `CutsceneKnucklesLbz2Instance`; snapshot capture/restore also copies it | `resetSession` clears; `resetForLevel` does **not** clear. S2 Screen_Shaking_Flag abstraction; S3K sites represent writes/reads of Screen_shake_flag instead. |
| `Sonic3kLevelEventManager` FBZ bridge → `Sonic3kFBZEvents.screenShakeActive` | manager `setScreenShakeState`/`setScreenShakeActive` forward only when FBZ exists; `S3kFbzEventWriteSupport` callers: `Fbz2SubbossRumbleController` sets state, `Fbz2SubbossLaserChild` clears state, `FbzEndBossEventControlInstance` clears active; FBZ events init/arena flow/setters and runtime restore write | FBZ event background flow; `Fbz2SubbossRumbleController`; `FbzEndBossEventControlInstance`; `FbzZoneRuntimeState` and FBZ visual probes read through event accessors | FBZ `init` clears. Separate FBZ abstraction of S3K Screen_shake_flag. No ICZ write reaches it. |

Every ICZ game-wide write has **no ICZ gameplay reader**, but can leak to the
LBZ readers because level reset retains the boolean. It has no required S3K
cross-zone reader: ROM LevelSetup clears the actual shake word and both offsets
(`LevelSetup`, sonic3k.asm:102232-102240). Do not remove the shared boolean or
change LBZ/FBZ in this lane. `Sonic3kSessionResetPolicy` copies fresh session
state; game-state snapshot restoration is preservation, not another consumer.
MGZ's event-local same-named boolean is a third, separate field, not this flag.

## Designs considered

1. Capture scroll-handler history separately: rejected; it leaves two indoor
   owners, permits renderer calls to advance gameplay and splits rewind units.
2. Make runtime adapter a second producer: rejected; events already own palette
   and shake timing, so this would split the same ROM background-event boundary.
3. Events own continuation and completed publication; runtime delegates and the
   handler projects it. Adopt this with a single framed scalar-plus-shake payload.

## Witness and checks

The real ICZ2 load witness enters indoors, moves across the retained
bridge range at X=$1900,Y=$400, and rebuilds the renderer. Baseline loses the
indoor publication. This is a bounded component witness, not route certification.


The first witness run (`target/icz-red.log`) selected `TestIczScreenPublication`
with `-Dmse=off -Dopenggf.headless.gl=surfaceless-egl`: 1 test, 1 expected
scroll-word failure, 0 errors/skips. All ROM properties found. Initial attempt
to redirect that log failed because target did not exist; no Maven started.
The first candidate selection (`target/icz-focused.log`) ran 142 tests,
2 failures, 0 errors/skips: the old-format ICZ payload fixture and transient
inventory needed updating for the composed owner. Both real-load witnesses
passed. Subsequent validation is recorded below when complete.

All Maven commands use `python3 tools/testing/maven_queue.py -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl '-Dtest=<selection>' test`.
Initial focused selection: `SwScrlIczTest,TestSonic3kIczRewindRoundTrip,
TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,
TestSonic3kLevelEventRewindSnapshot,TestIczSegmentColumnObjectInstance,
TestIczScreenPublication`.
Final selection adds `TestIczComposedRewindState,TestS3kIczPaletteCycling,
TestS3kIczPatternAnimation,TestS3kIczSegmentColumnGraphRewind,
TestS3kAdditionalScrollProjection,TestSonic3kIczResourcePublicationFailures,
TestSonic3kIczSlideTerrain`.

Strict citation check on ICZ owned files and this audit: no stale citations.
Checking every touched file additionally finds three inherited stale references
in unrelated manager sections (HPZ ShakeScreen_Setup and Change_Act2Sizes /
Process_Sprites). They are outside the allowed ICZ-only shared-file edits.
The optional local SCD reference differs from its pin; ICZ evidence uses the
verified skdisasm pin. `git diff --check` passes.

Final focused selection: **189 tests, 0 failures, 0 errors, 0 skips**.
Initial delivery queue waits: 68s/301s/633s; Maven execution106s/108s/108s; user wait0s.
Other investigation/tool time is unmeasured (unknown). No full suite/category smoke or native pixel qualification ran in this lane;
the lead owns the delivery smoke and independent review. The renderer retains
only derived output buffers, not loop/phase/indoor gameplay history.

Trace selection (same wrapper/flags on candidate and detached baseline):
`TestS3kIczZoneSliceTraceReplay,TestS3kTailsFullChainIczSegmentTraceReplay,
TestS3kSonicTailsIczSegmentTraceReplay,TestS3kSonicTailsIcz2SegmentTraceReplay,
TestS3kKnucklesSuperIczSegmentTraceReplay`.
Baseline: 5 tests, 5 divergence-assertion failures, 0 errors/skips; queue 764s,
Maven execution 117s. No comparison input hydrates gameplay.

| Replay | Baseline frames / grouped errors | Candidate |
| --- | --- | --- |
| ICZ zone slice | 25245 / 2430 | identical |
| Knuckles Super ICZ | 20008 / 1267 | identical |
| Sonic/Tails ICZ2 | 6282 / 1724 | identical |
| Sonic/Tails ICZ | 17947 / 1824 | identical |
| Tails full-chain ICZ | 22361 / 1157 | identical |

Baseline warnings and bootstrap errors/warnings are zero in all five reports.
Grouped reports are not raw per-field totals; unchanged groups cannot certify
unrecorded values inside a span. All five complete report JSON objects compare equal, including error identities,
values, spans, frame counts, bootstrap and replay gates. No new/worse reported
mismatches. This does not turn the inherited red frontiers into route passes.

Candidate traces: 5 tests, 5 matching divergence failures, 0 errors/skips;
queue 1052s, Maven execution 68s. All ROM properties were found in every run.
Total measured queue wait across the five jobs: 2818s; Maven execution: 507s
(jobs overlap, so these sums are not elapsed task wall time). User wait 0s;
other investigation/tool time unknown. No Maven job remains queued/running.
Baseline remains in `.worktrees/icz-publication-baseline`, clean at the base SHA,
with its own ignored reports/build output for the lead's comparison. Task branch
and worktree remain for draft review; no destination merge/push is authorized.

## Independent-review follow-up — 2026-11-16

Reviewed head `2585d5abd8`; all findings confirmed, none rejected.
1. FIXED: loc_53704 (sonic3k.asm:110194-110201) selects BG $10 at indoor
   BackgroundInit, before resize can set Events_fg_5. Real-load restart witness.
2. FIXED: production ICZ now resolves Level_frame_counter through the zone
   service accessor. Manager count resets independently; reload-only rows advance
   the native clock without ordinary manager update. Native LF17/count1 witness.
3. FIXED: samples -5..5; snow offset/subpixel consistency and [-$12E,0] bounds;
   velocity 0..$92400 in $2400 steps (current clamp first occurs at step65).
   Intro longs stay $800-aligned with read/increment pairing across 32-bit wrap.
   Tests cover invalid neighbors, valid edges, wrap, and actual falling/settling.
4. FIXED coverage: retained indoor left of $1000 below $720; initial $580/$720
   and X edges; column fall timer emits one $10 write (already correct).
   Malformed-manager test locates the exact framed component, not sibling offsets.
R1 RECORDED: ICZ1_BigSnowFall (sonic3k.asm:110430-110450) branches around landing
   on the crossing call. Java's two ifs clamp/start settle one call early.
R2 RECORDED: act0 X<$3940 advances loop history/auto phase regardless of routine;
   ROM ICZ1_IntroDeform (sonic3k.asm:110379-110409) runs only selected BG paths.
R3 RECORDED: fresh-dispatcher load publishes intro once before first update;
   positioned loads bypassing it publish only in first update (one vs two steps).
Red command used the same wrapper/GL flags with TestIczScreenPublication and
TestIczComposedRewindState: 17 tests, 5 expected failures, 0 errors/skips;
queue670s, execution38.473s. Focused selection above rerun: 204 tests, 0 failures,
0 errors/skips; queue744s, execution102s. Five-class trace rerun: 5 matching
failures, 0 errors/skips; all five report JSON objects equal baseline; queue715s,
execution67s. Baseline reused: clean matching source/inputs/selection/Java25.0.5.0.3.
No jobs remain. Follow-up queue2129s/execution207.473s; user wait0; other time unknown.
