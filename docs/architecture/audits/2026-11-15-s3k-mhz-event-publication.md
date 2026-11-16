# MHZ event publication — 2026-11-15

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (`develop`).

## ROM and owners

- A full search of `Screen_shake_flag` in the pinned locked-on disassembly
  finds one MHZ-specific object writer: `loc_76164`
  (sonic3k.asm:157133-157144), the end-boss escape threshold, writes `$14`.
  MHZ's miniboss, ordinary objects, screen events and seamless act transition
  have no flag writes. `LevelSetup` (sonic3k.asm:102239-102240) clears the words.
  Other matches belong to other zones, intros/endings or shared setup/shake code.
- The survey was correct about a missing connection: the boss wrote the
  game-wide boolean, while MHZ setup read a private word with no production
  writer. The boolean has no MHZ shake consumer; the shared flag remains intact.
- `MHZ2_ScreenEvent` (sonic3k.asm:112488-112492) applies the preceding shake
  offset to the foreground X copy. Background calculation precedes the next
  `ShakeScreen_Setup` (sonic3k.asm:104234-104255).
- `MHZ1_BackgroundInit` (sonic3k.asm:112310-112317) initializes `Events_fg_0/1`.
  `Adjust_BGDuringLoop` (sonic3k.asm:104157-104179) advances those physical and
  logical camera words in background events, not in a renderer.
  `MHZ1_BackgroundEvent` (sonic3k.asm:112320-112365) retains the logical word
  across Load_Level and rebases the physical sample by `$4200`.
- `MHZ_Deform` (sonic3k.asm:112368-112398) publishes BG camera X/Y and two
  animation bands without shake cancellation. `sub_554B8`
  (sonic3k.asm:113164-113202) cancels shake before the X ratio and restores it
  only to the primary BG word. The old renderer applied that to ordinary MHZ too.
  Early MHZ2 background/redraw branches return through `PlainDeformation`;
  only the boss-area tail reaches `loc_554B4` (sonic3k.asm:113145-113158).
  The former routine-8 test incorrectly expected setup on the early wait path;
  it now witnesses that the shake words remain unchanged on that ROM return.
- `loc_555D2` (sonic3k.asm:113291-113307) publishes pillar helper X positions
  from the native scroll ramp. Objects consume these on the next object pass.
  This gameplay write in `SwScrlMhz` was an additional finding beyond the survey.

## Witness and implementation

`TestS3kMhzScreenPublication.renderingAnotherCameraCannotChangeTheRetainedWorld`
loads actual MHZ2, captures the registered world, renders a different camera and
compares every non-parallax owner. On the base it failed on four bytes of
`zone-runtime.stateBytes` (1 failure, 0 errors, 0 skips), proving the renderer
changed the retained animation publication. No trace state supplies gameplay.

The event owner now holds the loop words, deformation outputs, helper positions
and composed `S3kScreenShake`. The runtime adapter reads that owner; the renderer
only projects. The act handoff carries the event loop words, not a renderer
snapshot. A framed same-build payload captures the scalar publication and all
three shake words. Validation uses an isolated probe before mutation and rejects
legacy scalar-only data, invalid framing, encodings and publication word ranges.
The boss writes only the timed flag through the zone adapter.

No changed algorithm selects a fixed-bug branch: loop arithmetic and the ramp's
DIVU remainder retain shipped behavior. Native render/collision coordinates are
kept distinct from viewport inset projection.

## Designs considered

1. Keep a renderer accumulator with a better rewind hook: rejected because a
   rebuild can still replace gameplay publication and the act handoff still
   needs presentation state to continue gameplay.
2. Put publication and shake in the runtime adapter, leaving event phases in a
   separate sidecar: rejected because it splits a single background dispatch
   and its rewind boundary across independent payloads.
3. Chosen: event-owned publication, runtime read adapter, stateless projection;
   use the established HCZ composition without changing `S3kScreenShake`.

## Verification

All test commands used verified ROM properties (`missing=none`); every completed
selection had zero errors and zero skips. Runtime source is unchanged between
the candidate comparison and the final test-only correction below.

Exact Maven invocations (output redirection to `target/mhz-*.log` omitted):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestS3kMhzScreenPublication test
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kMhzScreenPublication,TestMhzComposedRewindState,TestSonic3kMHZEvents,SwScrlMhzTest,TestSonic3kMhzRuntimeStateRegistration,TestMhzRewindFidelitySweep,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,TestS3kMhzAuthoredRoute,TestS3kMhzAct2AuthoredRoute,TestMhzCheckpointRoutes,TestMhzBossObjects,TestS3kMhzPatternAnimation' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kMhzScreenPublication,TestMhzComposedRewindState,TestSonic3kMHZEvents,SwScrlMhzTest,TestSonic3kMhzRuntimeStateRegistration,TestMhzRewindFidelitySweep,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,TestS3kMhzAuthoredRoute,TestS3kMhzAct2AuthoredRoute,TestMhzCheckpointRoutes,TestMhzBossObjects,TestS3kMhzPatternAnimation,*Mhz*TraceReplay' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kMhzAuthoredRoute,TestS3kMhzAct2AuthoredRoute,*Mhz*TraceReplay' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dtest=TestSonic3kMHZEvents test
```

1. Red base witness: 1 test, 1 expected failure; wait 336s, Maven 95s.
2. Compile-only attempt: failed on my annotation import and shake getter return
   type; corrected before execution. No tests ran; Maven 56.671s.
3. Initial focused run: 388 tests, 9 failures; wait 520s, Maven 143s. Five
   publication/schema expectations or defects were corrected; four route
   failures required baseline attribution.
4. Candidate focused + traces: 410 tests, 26 failures; wait 846s, Maven 177s.
   Of these, 21 trace and four route failures match baseline. The remaining
   event witness used grounded Y=$400 (which enters the custom-layout branch).
   Its test-only correction declares airborne Y=$450 and asserts routine 8 is
   retained before checking that setup is bypassed.
5. Baseline at the pinned SHA, separate clean detached cowtree: 25 tests,
   25 failures; wait 983s, Maven 159s. All failure identities match the candidate;
   MHZ1's expected-set display ordering alone differs. Actual set is `{pulley}`.
6. Corrected event-only rerun: 64 tests, 0 failures/errors/skips; wait 492s, Maven 37.571s.

Trace comparison: all 21 JSON reports have identical error/warning counts,
bootstrap errors, frame/field coverage, verification groups, complete grouped
errors/warnings and replay gates. Both totals are **9,397 grouped errors**, not
raw per-row mismatches. No new/worse mismatch or frontier was observed; these
are failing replays, not parity certification. Per-class baseline = candidate:

| Replay class | Grouped errors | First frame / field |
| --- | ---: | --- |
| TestS3kMhzZoneSliceTraceReplay | 3191 | 6958 / rings |
| TestS3kKnucklesSuperMhz2SegmentTraceReplay | 901 | 1120 / y |
| TestS3kKnucklesSuperMhz3SegmentTraceReplay | 568 | 1483 / y_speed |
| TestS3kKnucklesSuperMhz4SegmentTraceReplay | 142 | 1795 / y_speed |
| TestS3kKnucklesSuperMhz5SegmentTraceReplay | 17 | 256 / rings |
| TestS3kKnucklesSuperMhz6SegmentTraceReplay | 377 | 168 / y_speed |
| TestS3kKnucklesSuperMhzSegmentTraceReplay | 25 | 0 / y_speed |
| TestS3kSonicTailsMhz2SegmentTraceReplay | 614 | 1220 / tails_y_speed |
| TestS3kSonicTailsMhz3SegmentTraceReplay | 144 | 914 / camera_y |
| TestS3kSonicTailsMhz4SegmentTraceReplay | 11 | 1021 / routine |
| TestS3kSonicTailsMhz5SegmentTraceReplay | 30 | 759 / x |
| TestS3kSonicTailsMhz6SegmentTraceReplay | 529 | 433 / camera_x |
| TestS3kSonicTailsMhz7SegmentTraceReplay | 11 | 2107 / routine |
| TestS3kSonicTailsMhz8SegmentTraceReplay | 13 | 1672 / player_mapping_frame |
| TestS3kSonicTailsMhz9SegmentTraceReplay | 1756 | 865 / tails_y |
| TestS3kSonicTailsMhzSegmentTraceReplay | 11 | 1276 / routine |
| TestS3kTailsFullChainMhz2SegmentTraceReplay | 233 | 3371 / y |
| TestS3kTailsFullChainMhz3SegmentTraceReplay | 192 | 1156 / camera_x |
| TestS3kTailsFullChainMhz4SegmentTraceReplay | 318 | 2319 / x_sub |
| TestS3kTailsFullChainMhz5SegmentTraceReplay | 304 | 218 / y_speed |
| TestS3kTailsFullChainMhzSegmentTraceReplay | 10 | 1389 / routine |

All four inherited route issues: MHZ1 observes only `pulley`, missing its other
five required checkpoints; MHZ2 route parameter 1 observes no required markers;
parameters 2 and 3 die at input 36000 and 26152 respectively. Assertions and
identities match baseline (set ordering normalized for the MHZ1 diagnostic).

Queue and Maven times above are separate; waits overlap across the two trees.
User wait was zero; unmeasured research/editing/host overhead is unknown. Logs
and comparison JSON stay disposable under `target/`. No category/full/smoke
run was made: the lead owns smoke and independent review. No new trace frontier
was chosen or moved, so the frontier log is unchanged. Route/configuration and
visual parity beyond the selected checks remain inherited coverage gaps.

`git diff --cached --check` passes. `disasm_citations.py check --strict` on
all 19 staged paths finds three pre-existing stale citations in the manager's
HPZ/CNZ branches (120901, 180510-180515, 180666-180723); all three texts are
present at the base SHA. They are outside the permitted MHZ edits. The same
strict check on the five MHZ production files plus this audit passes (17 ok,
2 near, 8 unverifiable). An unrelated scddisasm checkout/pin warning remains.
