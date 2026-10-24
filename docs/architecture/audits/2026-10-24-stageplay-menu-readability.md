# Stageplay menu readability review

Task: make directing approachable in both editors before the public demonstration.
Engine baseline: `905a01cf4`; desktop baseline: `2b2b106`.
Workspaces: each repository's `.worktrees/stageplay-menus`, branch
`feature/ai-stageplay-menus`. The lead owns integration and broad smoke validation. The lead approved the second visual checkpoint and authorized the complete
conversion, final spacing fixes and shared in-level position picker. No hook or policy changes are required.

## Survey, worst first

The survey covers all shared `StageplayWorkspace` forms/actions and
`StageplayObjectAuthoring`, plus the engine HUD and desktop Stageplay chrome.
The production picture tests exercise the same forms in both frontends.

| Rank | Screens | Problem and chosen presentation |
| --- | --- | --- |
| 1 | Plan playable route; walking object route; route progress/ready/failure | Destination and performance choices competed with search limits and object dimensions. Keep Who, Destination and Motion first; frames, limits and future object footprint controls belong in one Advanced section. One result sentence; F1 retains native replay evidence, diagnostics and counts. |
| 2 | Place on floor, result/empty/refusal screens | Native surface terminology, dimensions and caveat paragraphs obscured the placement. Keep Who, Column and a short prompt; Advanced holds search range and footprint. Result rows show editable coordinates; F1 retains surface, angle, proposal/refusal evidence and caveats. |
| 3 | Actor actions, move/jump/stop/wait/release/crouch/look/roll, spawn, visibility | Long enum lists and target identity fields overwhelmed ordinary actions. Use human action names and cycling choices; put object identity/offsets and optional timeouts in Advanced. Keep waits' condition and required timeout visible. |
| 4 | Object properties, mode keys, parts, exclusions, visual commands, world commands | Native implementation names, bulk part syntax and repeated behaviour caveats dominated the forms. Use named objects, grouped positions, short mode choices and one Advanced section. Full source caveats/citations remain in F1. |
| 5 | Dialogue line, speaker/profile menus, reveal timing, typer, decoration, captions | Text/timing competed with panel geometry and optional assets. Keep text and timing first; Advanced retains panel layout, speaker-name override and optional assets. Keep deliberate per-letter timing as an explicit specialist editor. |
| 6 | Camera key, shake, camera menu/direction | Follow parameters and implementation prose competed with framing. Keep framing/easing first; Advanced contains tracking parameters and the shake seed. |
| 7 | Scene/set and scene changes | Output size, source fingerprint and project paths made a long source form. Keep scene/set and transition choices first; Advanced holds output dimensions and source settings. |
| 8 | Audio cues | Raw enum lists and verbose labels. Use named choices, short labels and a single hint; all sound/file controls remain reachable. |
| 9 | Speaker voice and presets | Six fields fit, but numeric ranges and raw enum casing distracted from choosing a voice. Named choices; Advanced holds variation/length, with exact bounds and note meanings in F1. |
| 10 | Film presets; open/save/import/export and confirmations | Already short. Keep their workflows; apply the shared readable row layout, complete-value help and visible footer. Import/export explanations become short hints with full details. |
| 11 | HUD cast/timeline/commands/route workbench; desktop cast/Direction/timeline/commands/status | Desktop inspector repeated multiple explanatory paragraphs above actions. Put controls first and expand Camera help, Playback details, Route details or Direction help when wanted. Both route workbenches use one short result sentence. |

## Final presentation and interaction

The two visual checkpoints established the approved component: short title, one dim
hint, yellow section headers, subtle checker panel and title rule, one fixed value
column at 45%, inline choices and one collapsed Advanced row. The final round
left-aligns every value, draws distinct arrow masks, aligns the selected-row fill
with the title rule and reserves 20 logical pixels above every primary action
(versus 12 before Advanced). Dialogue text previews use actual line breaks and
truncate only the last of two preview lines. The shared presenter applies these
conventions to every form/action family in the ranked inventory. Desktop uses native
ImGui inputs, combos/segmented choices, yellow headings, tooltips and a collapsing
header. Both frontends keep the original callbacks, complete values and full errors
behind F1; source labels/ranges are preserved separately from short display labels.
Desktop controller input retains the shared HUD form and path browser.

The picker is transient workspace state, separate from the scene and undo history.
Enter on a HUD Position/Column row suspends the form. Keyboard/d-pad movement
accelerates while held; Shift/right bumper gives one-pixel movement. S/controller X
toggles snap; Enter writes fields and returns, Esc returns without changes. The
crosshair starts at the current value, with a coordinate tag and queried floor marker.
The picker gives the level the HUD space previously occupied by panels and the
timeline. The prepared preview camera follows it; its ordinary level bounds allow panning
through the full set. Desktop Pick uses the same state and painter on the canvas:
hover points, click confirms, drag/scroll pans. Scene/root/playhead changes invalidate
a pending pick. Numeric entry remains available (F2 in HUD). Position pairs are one
row; relative offsets and screen layout positions stay numeric. Ground queries use
the existing persistent preview host and bounded prepared-world query, with no
picker-owned worker/probe launches. A terrain marker is a proposal, not collision
certification. Applying the form retains the original validation/undo path.

The Direction inspector's paragraph walls became headings, concise state, action
buttons and tooltips. Route chrome uses short result sentences and colour swatches;
full planner diagnostics remain accessible. The initial independent conversion draft
was reverted to honor the lead's three-screen checkpoint. Generic monochrome headers,
right-aligned values and compact plus-like arrows were rejected in those reviews;
the final component follows the approved yellow/grouped design instead.

## Evidence and validation

Before pictures use engine `905a01cf4` and desktop `2b2b106` production code. Only
the picture inventory and stale fixture inputs were corrected/expanded first.
Those corrections select the object before opening its route form, replace the
removed boolean facing input and use the current screenplay version. Baseline
pictures and the real worker's proposed/failed/applied route evidence are saved
outside Git. Ground result layouts in the broader survey use explicit UI fixtures;
they do not claim terrain or native route certification.

The final gallery is
`${REVIEW_DIR}/index.html`. It contains before/after pairs for the complete captured inventory plus the new
picker in both editors. Earlier checkpoints remain separately accessible. No desktop display, focus, live audio device,
ROM copy/link or parallel task JVM sweep is used. Both feature branches are committed locally for the lead. No merge or push is authorized.

Historical checkpoint checks below are bounded to their recorded source states;
final-round checks are recorded separately at the end.

### Checkpoint history

The first checkpoint passed 56 focused engine tests and 20 desktop checks/captures;
the second passed 60 engine checks/captures and 20 desktop checks/captures, all
without skips. Both desktop packages built. Those results cover only their
three-screen source states, not this later whole-inventory conversion. The lead
approved round two, requested the final layout nits and authorized all remaining
screens. The user then requested in-level picking and the larger 20-pixel action gap.
The checkpoint galleries preserve the reviewed images separately.

### Final round

The first whole-inventory focused command (106 tests) compiled successfully and
passed 103, with three obsolete title assertions: Save Stageplay → Save scene,
Declared native appearance exclusions → Appearance limits, and raw OBSTRUCTED/FOUND
headings → Floor unavailable/Floor placements. Tests now require the short headings
and retained raw refusal detail in F1. All initial picker and font checks passed.
The subsequent combined focused/picture command validates the final source and
additional picker acceleration, Column, camera and stale-playhead behavior.
Final completed results and exact selections are recorded below after inspection.

The combined engine command used `-Dmse=off
-Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.hudPictures=true` and all
three absolute ROM properties, with
`-Dtest=TestEditorProjectMenu,TestMenuPixelFont,TestStageplay*Workspace,TestStageplayPositionPicker,TestStageplayHud,TestStageplayHudPictures,TestStageplayRouteOverlayPictures test`.
It passed **127 tests, zero skips**, after 1,070 seconds in the queue and 99 seconds
of Maven execution. All 78 engine pictures were regenerated; the first visual review
confirmed distinct arrows and the larger button gap, and identified a clipped
picker-row footer, leftover object/speaker labels and underused pick-mode canvas
space. Those were corrected with a targeted rerun rather than repeating unchanged
workspace coverage. A desktop compile caught one incorrect ActorPose accessor;
it uses `actorId()` after correction.

The targeted post-review command retained the same environment/ROM properties and
used `-Dtest=TestEditorProjectMenu,TestStageplayHud,TestStageplayPositionPicker,TestStageplayHudPictures,TestStageplayRouteOverlayPictures test`:
**32 passed, zero skips**, after 446 seconds of queue waiting and 101 seconds of
Maven execution. It regenerated all 78 engine pictures. All were visually reviewed;
all 56 visible primary buttons also passed pixel readback spacing inspection with
a minimum 20 logical pixels above the fill. Distinct arrowheads/stems, left-aligned
numbers, aligned selection edges, real dialogue line breaks and the large picker
view/floor marker are visible. The final engine packaged/installed into the Editor's
private Maven repository through `scripts/install-engine.sh` successfully.

Desktop route/picker evidence passed with the real preview worker, including a
synthetic offscreen canvas click through the frontend queue. The first desktop
run exposed two old decimal-display assertions and a picture-fixture error:
creating/destroying a nested ImGui context invalidated the existing capture host.
Restoring the previous context handle alone still crashed at ImFontAtlas::Clear;
that approach was rejected.
The fixture now reuses its existing chrome for those modal pictures; standalone
survey captures own one context at a time. This is a test-helper correction, not
a change to application context ownership. The decimal assertions now expect the
integer display. All 20 desktop command/input tests then passed, zero skips.

A final consistency pass pairs shake amplitudes without making them a level
position, and uses short sentence-case action titles. Desktop picture inspection
also found a missing black-circle status glyph; marks now use geometry and long
status lines get an ellipsis. Inspector readouts round fractions to two decimals,
while numeric form inputs retain their complete values. The first native route
result window needs layout warmup before capture; its picture test now waits four
draws and requires the menu to remain open. These affected changes are covered by
the final checks recorded below.

The separate `-Dmse=off -Pguards test -B` run completed **777 tests, zero skips**,
with one failure: `TestArchUnitRules.stageplay_boundaries_have_explicit_owners`.
The new UI helpers had directly consumed Stageplay records, and the HUD added a
Frame dependency. No policy or allowlist was changed. Semantic translation now
stays with the already-owned Workspace and ObjectAuthoring boundaries: the shared
presenter receives string choices/cast names, and the cursor receives plain view
and floor coordinates. The workspace owns ground-report interpretation and camera
projection. The affected architecture class and authoring/picture checks are rerun
below; the 776 other guard results remain applicable to their unchanged boundaries.

`-Dmse=off -Pguards -Dtest=TestArchUnitRules test -B` passed **34 checks, zero
skips**, including the exact ownership rule that failed above. The existing
policy remains unchanged; the UI no longer imports Stageplay value/service types.

The final refactored engine authoring command used the same EGL/picture/absolute
ROM settings and exact selectors for EditorProjectMenu, MenuPixelFont, Dialogue,
Ground, Object, Puppet, Route and main Stageplay workspaces, PositionPicker,
StageplayHud, HudPictures and RouteOverlayPictures: **117 passed, zero skips**.
The mistakenly named CameraWorkspace selector matched no class; the actual
`TestStageplayDirectingWorkspace` is checked separately below. All 78 engine
pictures were regenerated and the 56 primary-button gap checks still passed.

`--lean -Dmse=off -Dtest=TestStageplayDirectingWorkspace test` passed **11 tests,
zero skips**, completing the final 128-check authoring selection across the two
commands. The install started while this request was waiting was stopped before
admission and is restarted only after completion; task test JVMs did not overlap.

The final desktop command used the same EGL, absolute ROM properties and
`-Dopenggf.stageplay.desktopPictures=true`, plus the engine demo's absolute
`openggf.stageplay.liveScene` path, with
`-Dtest=TestStageplayChrome,TestStageplayChromePictures,TestStageplayRouteOverlayPictures test`:
**29 passed, zero skips**. All 90 desktop pictures were refreshed against the final
installed engine. The final queued `-Dmse=off -DskipTests package` succeeded.
The gallery contains **168 comparisons** (78 HUD, 90 desktop), preserving all
original PNGs and the two earlier checkpoints. Its inline form uses lossless WebP
copies verified pixel-for-pixel against the PNGs to fit the preview transport limit.
The picker pictures show actual prepared-world floor proposals in both frontends.
All requested menu families are converted; no hook, policy, saved scene format,
renderer/export content or gameplay physics changes were made. Panel coordinates
and relative offsets remain numeric; numeric entry remains available for world
positions. Broad smoke is assigned to the lead, not claimed here. Both branches
remain local for the requested review/delivery flow; no merge or push.

Final gallery HTML preview reported no console errors and verified the 168-item
inventory and both filters (78 HUD / 90 desktop). All 334 original before/after
PNGs have lossless WebP counterparts whose decoded pixels match exactly. Both
worktrees pass whitespace checks and the Stageplay skill mirrors match. Required
trailers and the final co-author block are parsed before each local commit.

### Lead review round 3

Follow-up to engine `9fe299275` and desktop `f6a6c88a0`. The ten-pixel HUD
rows were rejected: they made the route's selected Motion chips touch even though
the primary-button gap passed pixel inspection. Restore the original eighteen-pixel
row height in the shared component and start the rows at y=52, leaving twelve pixels
below the subtitle block. The existing visibility budget scrolls longer forms;
neither rows nor the twenty-pixel primary-action gap are compressed. An additional
Plan route capture scrolls to Apply so both the Motion spacing and button gap can
be reviewed.

Both frontends use the same picker painter. The faint cyan cross is replaced by a
short yellow floor line with a dark outline and a thin vertical guide of separated
yellow dashes from the cursor toward the queried terrain surface. Ground queries,
placement coordinates and confirmation/cancellation are unchanged. The guide and
skill mirrors, and the desktop README, describe the new marker.

Focused regression checks first reproduced the missing subtitle gap and yellow
floor marker (14 tests, exactly two failures). The row regression also checks
normal Motion spacing, scrolling to the primary action and its retained gap; the
marker regression checks the yellow floor line and separate thin vertical dashes.

The new scrolled picture exposed a clipped F1 Help hint. When the complete footer
would overflow, the shared renderer now reduces only the double spaces between
hints; the labels and arrow glyphs stay intact. The focused regression checks this
Apply-state footer as well.

Final engine command (display variables unset, Java 25, all three absolute ROM
properties and surfaceless EGL):
`maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.hudPictures=true -Dsonic1.rom.path=${ROM_ROOT}/s1.gen -Dsonic2.rom.path=${ROM_ROOT}/s2.gen -Ds3k.rom.path=${ROM_ROOT}/s3k.gen -Dtest=TestEditorProjectMenu,TestStageplayHud,TestStageplayPositionPicker,TestStageplayHudPictures,TestStageplayRouteOverlayPictures test`:
**34 passed, zero skips**. All 79 HUD pictures were refreshed and inspected,
including the new scrolled Plan route picture. The 42 visible primary buttons have
a minimum twenty-pixel gap in pixel readback; the longer forms scroll. The final
route picture shows all four normally spaced Motion rows and the gap before Who;
the separate Apply view keeps its full footer. All refreshed engine lossless WebP
copies decode to the original PNG pixels.

The affected architecture selection,
`maven_queue.py -Dmse=off -Pguards -Dtest=TestArchUnitRules test -B`, passed
**34 checks, zero skips**. This round changes only drawing/spacing and its focused
regressions; it adds no dependencies or policies. The earlier complete guard run's
other checks remain unchanged. The final engine was packaged and installed into
the desktop editor's private Maven repository with `scripts/install-engine.sh`.

Desktop command (display variables unset, Java 25, the same EGL/absolute ROM
settings, `-Dopenggf.stageplay.desktopPictures=true` and the engine demo's absolute
`openggf.stageplay.liveScene` path):
`maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.desktopPictures=true -Dtest=TestStageplayChrome,TestStageplayRouteOverlayPictures test`:
**21 passed, zero skips**. Nine route/picker pictures were regenerated against the
final installed engine and inspected; the other 81 desktop pictures remain
applicable because native form/chrome presentation is unchanged this round. Both
picker pictures visibly show the yellow floor line, dashed guide and coordinate
tag over the prepared level. The editor's queued
`maven_queue.py -Dmse=off -DskipTests package` succeeded.

The review page now contains **169 comparisons** (79 HUD, 90 desktop), with verified
lossless copies of the refreshed PNGs. Documentation whitespace checks and the
Stageplay skill mirror check pass. No further screen conversion, policy edit,
physics change, merge or push is part of this follow-up; broad smoke remains with
the lead. Both local commits carry the required parsed co-author trailers.
