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
