# Stageplay Day Off corrections

Engine base: `81f25c89f04d402c68998106cab17c0ddd79d928`; editor base: `dd5ee43`.
Both repositories use `.worktrees/stageplay-dayoff-bugs`, branch
`bugfix/ai-stageplay-dayoff-bugs`. Delivery is local commits only. The lead owns
integration and broad smoke. No hook, policy, public cue-record shape, version,
ROM, disassembly pin or production-promo file changes are required.

## Findings and corrections

Paths below are relative to the engine checkout. Line numbers refer to the final
source in this task. Regression names identify executable evidence, not a claim
that an entire game or level is certified.

| Brief item | Root cause and owning correction | Regression evidence |
| --- | --- | --- |
| 1. Entry music fades | `Sonic2LevelInitProfile.java:142` sends the correct native entry `$F9`, but the existing automatic-playlist lease covered song publication only. The deferred mailbox could dispatch the fade after an authored cue. `StageplayLiveWorld.java:229` now establishes the shared entry policy before native loading; `LevelMusicAccess.java:19` and `AudioManager.java:2665` suppress entry music controls before enqueueing. Housekeeping and ordinary SFX continue. This is shared entry ownership, without a game/zone/frame exception. | `TestStageplayAudioLifecycle.entryFrameMusicIsNotFollowedByTheNativeEntryFade` failed with an authored PlayMusic followed by FadeOutMusic, then passed. Eight `authoredSongsSurviveNativeEntriesInOfflinePcm` cases cover S1/S2/S3K, same-game replacement, cross-game cuts, donor music, and local cues 0/8/40. They require the correct music asset, zero driver/sequencer fade-out, nonzero final-second PCM and a native Jump effect admitted under entry ownership. Existing NONE/PREPARED/STOP/seek/close controls and `TestSonic2LevelInitProfile` retain unowned native behavior. |
| 2. Native IDs and names | `StageplayAudio.java:80` previously used soundId as an engine ID. S2 requires a 31-entry permutation; S1 music is identity; S3K native credits `$DC` select engine `$33`; all three SFX catalogs use native numbers already. `game/audio/RomSoundCatalog`, profile-owned native inventories and S2 metadata, audio prepare/dispatch, catalog output, screenplay compilation and the shared editor form now agree. | `nativeEmeraldHillNumberSelectsEmeraldHillRatherThanMetropolis` failed with engine 130 instead of 129. Named screenplay and editor regressions also failed before implementation. All now pass. `TestRomSoundCatalog` covers every S2 entry, identity mappings, credits/ring separation, SFX, names, wrong-kind/system IDs, ordering and immutability. Native credits play through both current and donor producers. Three ROM-backed CLI catalog cases pass. |
| 3. Repeated diagnosis | `StageplayWorkspace.java:1698` concatenated job message, result message and an overlay reason that already included the result. Route and Direction Details now use one composed diagnosis, retaining motion and counts. | `routeDetailsShowsTheDiagnosisOnceAndRetainsWorkCounts`: expected one occurrence, observed three before; passes after for both Details paths. |
| 4. Advice depends on Attempt frames | `StageplayRoutePlanner.java:365` already reserved Run nodes/ticks; the production note's claim that the check never ran was incorrect for this base. Its diagnostic inherited the attempted window, ran after detours, and discarded an unsuccessful diagnosis. Diagnosis now runs before detours, within the same aggregate nodes/ticks/deadline, using a horizon bounded by the continuous shot, next actor key and maximum route horizon. A longer successful rehearsal asks for more Attempt frames as well as Run. It never supplies an applicable patch. Specific direct settling/landing/charge explanations survive the fallback. | `ghzWalkAdviceDoesNotDisappearAtTheDefaultAttemptWindow` failed at 300 and passed at 419 before the fix; both pass after. The 300 case requires the longer-window qualification. The synthetic gap, sealed-wall, cancellation/tiny-budget, native charge and cold replay controls pass. An older exact wording assertion now permits the explicit longer-window qualification. |
| 5. Truncated result | `StageplayRouteOverlay.java:83` supplied an overlong sentence to the HUD's one-line fitter. The budget status is now “Search limit reached.” Uncertainty and advice remain in F1. | `routeResultSentenceFitsTheActualSidePanelWithoutEllipsis` reproduced “reachability is unk...” at 1080p, then passed at 720p, 1080p and 1024x576. |
| Lead 1. Goal legend | `StageplayHud.java:323` used white for the goal, independently of the diamond. `StageplayRouteOverlay.goalColor()` now owns both. | The production offscreen picture test failed on white `$FFFFFF` versus failed-goal `$FF7887`, then passed. Successful goals use the same shared cyan selection. |
| Lead 2. Command name | `StageplayWorkspace.java:1829` still named “Plan collision route”. It now says “Plan route”. | `planningStatusUsesTheCurrentPlanRouteCommandName` failed before and passes after. |
| Lead 3. Dialogue placement | `StageplayWorkspace.java:1330` bottom-aligned new lines while the screenplay compiler used Y=12. Both now use the compiler's single `DEFAULT_DIALOGUE_Y` authoring default. | `editorAndScreenplayStartDialogueAtTheSameTopMargin` observed 140 instead of 12 before the fix, then passed and matched the compiled default. |
| Lead 4. Picker default | `StageplayMenuPresentation.java:73` defaulted snap off except on route forms. Placement pickers now start on; camera framing explicitly stays off. `StageplayWorkspace.beginPositionPick` constructs a new actor's ground performer from the form, rather than requiring a previously saved selection. Existing explicit-footprint requirements for object floor queries are retained. | `playerAndObjectPlacementPickersStartWithSnapEnabled` failed on Add actor before; it now covers Add actor, shot placement, object key metadata and unsnapped camera framing. `firstActorSnapsUsingTheCharacterBeingAddedRatherThanAnExistingSelection` checks actual confirmed Y, source-game/character/path and absence of premature scene edits. |
| Lead 5. Native abilities | `StageplayLiveWorld.java:554` reported the sprite class's raw secondary-ability declaration. `reportedAbility` at line 590 gates it through that sprite's own game rules. Raw sprite dispatch remains unchanged. | Three negative native/donor tests failed before; three S3K positive controls passed. All six `TestStageplayLiveAbilityReportingRom` cases now pass at frames 0 and 1, including S3K donors in older scenery. |
| Lead 6. Encoder path | The launcher changes cwd to the checkout for execution, but its path normalization omitted ffmpeg. `tools/stageplay/stageplay.py:95` now resolves paths containing a directory against caller cwd and retains PATH lookup for bare names. | `test_stageplay_launcher.py`: red 3 failures/2 passes, green 5 passes, zero skips. Covers relative, ./, ../, absolute and bare names, including spaces and repository decoys. |

## Authoring contract and evidence

Numeric cues now use native request IDs directly; there is no migration or old-ID
mode. In-repository examples and both frontends' picture/playback fixtures preserve
the intended EHZ track using decimal 130. Names resolve to the same canonical native
number. Export retains precise numbers. The S3K catalog lists the combined game's
native soundtrack; engine-only `$100+` driver selector extensions are excluded.
No arrangement field or public record change was introduced. The native `$2E` key
`MINIBOSS_S3` names its disassembly slot; its display title correctly says S&K,
matching the locked-on driver instead of inheriting the engine enum's misleading
“Sonic 3” title.

The production folder `$PRODUCTION_ROOT` was read only. Historical
notes and probes remain intact. The before/after gallery is
`$REVIEW_ROOT/index.html`, with six pairs
and relative PNG paths: route Details, side panel, result menu, named audio,
Add actor picker and new dialogue. Before route pictures use the starting
production code; additional before pictures were captured before their respective
corrections. Only picture inventory/assertions were added for capture. All twelve
original comparison PNGs are retained, plus a final desktop named-music picture.
The page preview loaded all thirteen images without
console errors; original-size pictures were inspected for text, colours and placement.

The native reference check used the pinned S2 and S3K revisions, not their differing
main-checkout working copies. The final citation checker reports `ok=4, unverifiable=8`:
constant-table declarations do not provide routine anchors for eight references.
Their labels/ranges were checked directly in the pinned Git objects. No reference
pin or unrelated dirty submodule was changed.

## Validation commands and results

All engine Maven invocations ran from the engine task checkout through
`$ENGINE_ROOT/tools/testing/maven_queue.py`, with
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`. No task JVMs overlapped. Rendering commands
unset DISPLAY and WAYLAND_DISPLAY and selected surfaceless EGL. ROM properties
were absolute existing files with the required SHA-1 identities.

`ENGINE_ROOT` and `EDITOR_ROOT` are the main checkout roots. `REVIEW_ROOT` is
the external `stageplay-dayoff-bugs-review` directory; `PRODUCTION_ROOT` is the
read-only `sp-promo-a` directory. In the commands below,
`Q=$ENGINE_ROOT/tools/testing/maven_queue.py`
and `R=$ENGINE_ROOT`. These abbreviate the exact paths used.

```bash
# First UI red run: 2 tests, 2 expected failures, zero skips.
python3 "$Q" --lean -Dmse=off '-Dtest=TestStageplayRouteWorkspace#routeDetailsShowsTheDiagnosisOnceAndRetainsWorkCounts,TestStageplayHud#routeResultSentenceFitsTheActualSidePanelWithoutEllipsis' test
# First UI green: 33 passed, zero skips.
python3 "$Q" --lean -Dmse=off '-Dtest=TestStageplayRouteWorkspace,TestStageplayHud,TestStageplayRouteOverlay' test

# Audio/ID/name red run: 4 expected failures, zero errors/skips.
python3 "$Q" -Dmse=off -Dsonic2.rom.path="$R/s2.gen" '-Dtest=TestStageplayAudioLifecycle#entryFrameMusicIsNotFollowedByTheNativeEntryFade+nativeEmeraldHillNumberSelectsEmeraldHillRatherThanMetropolis,TestStageplayScriptCompiler#namedMusicAndNativeNumbersCompileToTheSameCue,TestStageplayAudioWorkspace' test

# Full focused candidate and final pictures: 300 tests, 299 passed, zero skips.
# Its sole error was a synthetic object-picker form missing required fields.
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.hudPictures=true -Dopenggf.stageplay.routeEvidence=target/dayoff-after -Dsonic1.rom.path="$R/s1.gen" -Dsonic2.rom.path="$R/s2.gen" -Ds3k.rom.path="$R/s3k.gen" '-Dtest=TestStageplayAudioLifecycle,TestRomSoundCatalog,TestStageplayAudioWorkspace,TestStageplayScriptCompiler,TestStageplayScriptDecompiler,TestStageplayScriptRoundTrip,TestStageplayTool,TestStageplayRoutePlanner,TestStageplayPlayerRouteRom,TestStageplayLiveAbilityReportingRom,TestStageplayDialogueWorkspace,TestStageplayPositionPicker,TestStageplayRouteWorkspace,TestStageplayHud,TestStageplayRouteOverlay,TestStageplayRouteOverlayPictures,TestStageplayHudPictures#captureDayoffAudioCue,TestSonic2LevelInitProfile,TestLevelMusicCoordinator,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test

# Repaired complete picker class, layout, updated native-ID fixtures and real playback:
# 20 passed, zero skips. This preceded the catalog ownership correction below.
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.hudPictures=true -Dsonic1.rom.path="$R/s1.gen" -Dsonic2.rom.path="$R/s2.gen" -Ds3k.rom.path="$R/s3k.gen" '-Dtest=TestStageplayPositionPicker,TestStageplayLivePlayback,TestEditorProjectMenu,TestStageplayHudPictures#captureScreenplayImportRefineAndExportForms+captureStageplayCastTimelineAndCommands' test

# Python launcher safety: 5 passed, zero skips.
python3 -B -m unittest discover -s tools/testing -p 'test_stageplay_launcher.py' -v
# Pinned citations: exit 0; four verified routine citations, eight unanchorable constants.
python3 tools/disasm/disasm_citations.py check src/main/java/com/openggf/game/sonic2/audio/Sonic2Music.java src/main/java/com/openggf/game/sonic3k/audio/Sonic3kAudioProfile.java --strict --limit 12
```

The other red iterations are bounded diagnostics: the GHZ 300/419 check reproduced
one expected failure; the added UI/ability run reproduced seven requested failures.
Two compilation mistakes and malformed dialogue/PCM test fixtures were corrected
before counting their behavioral evidence. PCM capture now follows the production
exporter's end/reopen lease sequence at native shot replacement; a missing capture
lease was not an engine defect. The corrected dialogue red run showed Y=140 versus
12 while all 21 audio lifecycle cases and three catalog cases passed.

Guard, desktop build/check outcomes and final local commits are recorded in the
completion section below. This is focused and guard evidence, not broad smoke,
release-suite, exhaustive trace or level-certification coverage.


## Catalog ownership correction

The first required guard run (`env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q"
-Dmse=off -Pguards test -B`) completed 778 checks: 776 passed, two failed, zero
skipped. Both failures were introduced by this task's initial catalog placement:
`TestArchUnitRules.low_level_layers_do_not_depend_on_runtime_layers` rejected the
low-level audio catalog reading concrete game enums;
`core_runtime_cycle_cluster_does_not_gain_top_level_edges` rejected editor → audio.
No ratchet, allowlist, signature pin or hook was edited.

The corrected catalog lives in `game.audio`. Each existing game audio profile
owns its immutable native inventory, and the existing `GameModuleRegistry`
composition root selects that detached metadata without creating a playback
session. Editor, compiler and playback consume that game metadata. This retains
the tested mappings and UI while respecting both dependency boundaries.


The ownership change was checked with 173 passing tests, zero failures/errors/skips:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsonic1.rom.path="$R/s1.gen" -Dsonic2.rom.path="$R/s2.gen" -Ds3k.rom.path="$R/s3k.gen" '-Dtest=TestRomSoundCatalog,TestStageplayAudioWorkspace,TestStageplayAudioLifecycle,TestStageplayScriptCompiler,TestStageplayTool,TestStageplayLivePlayback,TestSonic2LevelInitProfile,TestLevelMusicCoordinator,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test
```

Maven execution took 1m38s after 32s queue waiting. Source comments were then
reconciled with the pinned S3K references; that citation check passed as recorded
above. No behavior changed during citation cleanup.


The corrected required guard run passed **778 checks, zero failures/errors/skips**:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off -Pguards test -B
```

It waited 300s for admission and executed Maven for 6m28s. The two new architecture
failures are resolved, with no policy changes. A later focused catalog check covers
the remaining inherited `$2E` display-label correction; its architecture check
also runs against that final source.


The extra arrangement-title regression ran red first: one failure, no errors/skips,
expected “Mini-Boss (S&K)”, observed “Mini-Boss (Sonic 3)”. The profile-owned native
catalog now supplies the correct display title while preserving the native slot
key and both request/playback numbers. This changes authoring metadata only;
the existing raw engine enum and its other consumers remain unchanged.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" --lean -Dmse=off '-Dtest=TestRomSoundCatalog#sonic3SlotDescribesTheArrangementUsedByTheLockedOnDriver' test
```


Final catalog and architecture follow-up: **81 passed, zero failures/errors/skips**
(47 catalog cases plus 34 architecture checks). Maven execution was 1m35s, with
zero queue waiting. No production changes followed this run before the initial handoff.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off '-Dtest=TestRomSoundCatalog,TestArchUnitRules' test
```


## Desktop and completion

The desktop checkout installed the final engine into its private `.m2/repository`
using the requested script. The engine package and private installation succeeded:

```bash
# cwd: $EDITOR_ROOT/.worktrees/stageplay-dayoff-bugs
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk scripts/install-engine.sh "$R/.worktrees/stageplay-dayoff-bugs"
```

The focused desktop package selection covers its shared chrome/input contracts,
both updated EHZ fixtures, screenplay import/refinement/export, and the actual
native named-music combo. Its pictures use surfaceless EGL and no desktop input.


The focused desktop package completed **22 tests, zero failures/errors/skips**:
20 `TestStageplayChrome` cases and two explicit picture cases, as confirmed in the
completed run's XML. Maven execution took 14.614s, with zero queue waiting. The
private repository fetched missing build dependencies during that time.

```bash
# cwd: $EDITOR_ROOT/.worktrees/stageplay-dayoff-bugs
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 "$Q" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.desktopPictures=true -Dsonic1.rom.path="$R/s1.gen" -Dsonic2.rom.path="$R/s2.gen" -Ds3k.rom.path="$R/s3k.gen" '-Dtest=TestStageplayChrome,TestStageplayChromePictures#captureScreenplayImportRefineAndExportForms+captureActualDesktopStageplayWithRomSetAndActors' package -B
```

The actual desktop image shows a complete “Emerald Hill ($82)” combo, compact
Frame/Action/Music rows, collapsed Advanced and visible Apply/Close/Help controls.
It is retained as `after/desktop-audio.png` in the same relative-path gallery.
The generated desktop `config.yaml.example` matched the engine template byte for
byte and was removed; it is not an authored task change.


Desktop local commit: `e41ab4a3e42bee16320270fea15d4268a64648f9`.
The engine commit containing this audit also contains every engine correction
listed above. Its exact SHA is included in the handoff. Validation used the stated
base plus this task's working changes; only documentation followed the final
catalog/architecture check and engine installation.

All requested fixes have passing affected checks. Earlier behavioral red runs,
the repaired test-fixture error, and the initial guard failures are accounted for
above. No hook/policy changes, broad smoke, release suite, merge, push or
production-folder edit was performed. Both local branches/worktrees are retained
for the lead's integration and broad validation. The gallery preview confirmed
six comparisons and thirteen loaded images, without console errors; source JSON,
relative image paths, mirrored skills and staged whitespace also passed inspection.


## Lead taste review: shared F1 details follow-up

The follow-up starts at engine `80c4cde689b5c483c31063aa67fe9c916e26b900`
and desktop `e41ab4a3e42bee16320270fea15d4268a64648f9`. The audio form and
side panel were accepted; the lead rejected the remaining legacy F1 help screen.
`MenuDetailsScreen.render` still called the blue-header `MenuStyle.page`, used a
large title and two footer lines, and treated diagnoses and action labels as a
single scrollable paragraph. Removing duplicate concatenations had not corrected
that presentation owner.

Three regressions ran red before implementation: compact-title/Back/footer,
structured route F1 content, and native check/cross/separator glyphs. All three
failed on the expected legacy behavior, with zero errors/skips. Fresh baseline
captures (two tests, zero skips) use that committed production source plus picture
inventory changes, including failed/successful routes and ordinary audio help.

The shared component now renders compact dark help panels with a yellow rule,
section headings, one value column, actual buttons and one arrow footer. Route
results provide a plain meaning, evidence-backed Try bullets, Settings, dim Search
counts and a primary Plan another route action. Apply remains its existing fresh
check action and offers its replacement hint only when available. More preserves
the original diagnosis and complete values. The same component serves editor,
import, settings, text-entry and path help; no gameplay or route-search algorithm
changes belong to this follow-up.


Focused follow-up history: the first implementation passed 42 checks. The expanded
shared-consumer/capture selection then ran 103 checks, with 102 passing and one
failure: `TestMenuTextEditor.detailsExposeUnicodeAndFullValidationErrorWithoutChangingTheValue`
exposed loss of Up/Down scrolling in a read-only legacy help view. Restoring that
control while retaining button navigation in structured views passed all 42
checks in the matched repair selection, including the complete text-editor class,
long help, project forms and route actions. Zero skips occurred.

The new desktop baseline fixture initially nested an ImGui context and its picture
fork exited with code 1 after the 20 ordinary chrome tests passed. The fixture now
reuses its active context and draws the shared fallback directly. That corrected
baseline picture method passed, without an engine change. This was a capture
fixture issue, not an observed production crash.

The final visual pass also removed action-label inventory from the route's More
body: the exact diagnostic, settings and counts remain there, and actions remain
buttons. Original legacy action inventories remain accessible in generic help.


Final route semantics and recapture: 17 passed, zero failures/errors/skips,
including Back, replan and fresh Apply button behavior. It waited 162s for shared
admission and executed Maven for 1m41s. The actual failed route shows 53 samples,
8 nodes and 432 ticks. The found route shows 4.2s and zero jumps. Both and the
expanded More view were visually inspected at their original resolution. The
new ordinary audio-help capture verifies headings and the shared value column.

Follow-up commands use the same Java 25 environment and `Q`/`R` paths defined above:

```bash
# Requested regressions: 3 expected failures, no errors/skips.
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" --lean -Dmse=off '-Dtest=TestMenuDetailsScreen,TestMenuPixelFont#resultMarksAndSeparatorsUseNativeGeometryRatherThanQuestionMarks,TestStageplayRouteWorkspace#routeF1UsesAPlainResultSectionsAndButtonsWithEngineeringBehindMore' test
# Fresh baseline pictures: 2 passed, no skips; original production source.
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.hudPictures=true -Dopenggf.stageplay.routeEvidence=target/dayoff-details-before -Dsonic2.rom.path="$R/s2.gen" '-Dtest=TestStageplayRouteOverlayPictures,TestStageplayHudPictures#captureDayoffAudioCue' test
# Initial implementation: 42 passed, no skips.
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" --lean -Dmse=off '-Dtest=TestMenuDetailsScreen,TestMenuPixelFont,TestEditorProjectMenu,TestStageplayRouteWorkspace' test
# Expanded consumers/pictures: 102/103 passed, one read-only scrolling regression.
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.hudPictures=true -Dopenggf.stageplay.routeEvidence=target/dayoff-details-after -Dsonic2.rom.path="$R/s2.gen" '-Dtest=TestMenuDetailsScreen,TestMenuPixelFont,TestEditorProjectMenu,TestStageplayRouteWorkspace,TestMenuTextEditor,TestMenuPathBrowser,TestEngineSettingsScreen,TestContentImportScreen,TestStageplayRouteOverlayPictures,TestStageplayHudPictures#captureDayoffAudioCue' test
# Repair selection: 42 passed, no skips; complete affected classes.
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" --lean -Dmse=off '-Dtest=TestMenuDetailsScreen,TestMenuTextEditor,TestStageplayRouteWorkspace,TestEditorProjectMenu' test
# Final More cleanup and route recapture: 17 passed, no skips.
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.hudPictures=true -Dopenggf.stageplay.routeEvidence=target/dayoff-details-after -Dsonic2.rom.path="$R/s2.gen" '-Dtest=TestStageplayRouteWorkspace,TestStageplayRouteOverlayPictures' test
```


The final engine package was installed with the same `scripts/install-engine.sh`
command recorded above. Desktop final package/check selection passed 21 tests
(20 chrome plus one picture), zero failures/errors/skips, in 6.805s of Maven
execution with no queue waiting:

```bash
# cwd: $EDITOR_ROOT/.worktrees/stageplay-dayoff-bugs
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 "$Q" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.desktopPictures=true -Dsonic1.rom.path="$R/s1.gen" -Dsonic2.rom.path="$R/s2.gen" -Ds3k.rom.path="$R/s3k.gen" '-Dtest=TestStageplayChrome,TestStageplayChromePictures#captureActualDesktopStageplayWithRomSetAndActors' package -B
```

Desktop follow-up commit: `c8b84c223889b0f1c2caecd800c7bcb4dfb9a2de`. Its baseline capture uses the
previous privately installed engine; the final capture uses this follow-up's
engine source. Both are retained in the gallery. The accepted audio form and side
panel are retained unchanged; failed and found route details, optional More,
ordinary help and the desktop F1 fallback show the new component.


Final source owners for this presentation follow-up:

- `src/main/java/com/openggf/game/MenuDetailsScreen.java:83`: shared compact reader,
  scrolling, More and button rendering/input.
- `src/main/java/com/openggf/editor/project/EditorProjectMenu.java:96`: structured
  details entry, current values and the ordinary-help fallback.
- `src/main/java/com/openggf/editor/stageplay/StageplayWorkspace.java:1703`: plain
  route result/meaning, observed advice, settings/counts and existing route actions.
- `src/main/java/com/openggf/graphics/MenuPixelFont.java:126`: native result marks
  and separators alongside the arrow glyphs.


The final review page has nine before/after pairs and two supplementary after
pictures (expanded More and the accepted desktop music form): 20 images loaded
successfully in the HTML preview with no console errors. The 1100px-wide preview
reported 5489px content height and captured its first 4000px; the new original
screenshots were also inspected individually. Image paths in the durable page
remain relative. This is offscreen visual evidence, not a desktop focus/input run.


The first follow-up guard invocation waited 172s, then was cancelled externally
with exit 130 during `TestBuildToolingGuard`. It had reported no failing checks,
but did not complete and is not counted as a guard pass. Its wrapper/processes
exited and no active duplicate guard request remained before replacement.
A fresh required guard invocation follows against the unchanged source.


The replacement full guard invocation completed: 778 tests, one failure, zero
errors/skips; Maven execution was 5m16s after 288s waiting. The sole failure was
`TestArchUnitRules.stageplay_boundaries_have_explicit_owners`: a workspace timing
fallback dereferenced `StageplayRoutePoint.frame()`, outside the existing approved
workspace boundary. Its calculation now lives in `StageplayRouteOverlay`, the
existing transient owner of samples and authored route commands. The workspace
reads only its integer performance duration. This preserves the pictured behavior
without editing an allowlist, hook, signature pin or policy. The affected complete
architecture class and route presentation/action classes are rechecked below;
the other guard cases were unaffected by that ownership-only extraction.

The duration query is now owned by
`src/main/java/com/openggf/stageplay/editor/StageplayRouteOverlay.java:72`.
Authored command duration remains separate from the complete verification-window
sample count; this is why the pictured 4.2-second performance has 300 samples.


Ownership repair validation passed **57 checks, zero failures/errors/skips**:
all 34 `TestArchUnitRules` checks, all 16 route workspace cases and all seven
route overlay cases. It waited 516s and executed Maven for 1m34s:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off '-Dtest=TestArchUnitRules,TestStageplayRouteWorkspace,TestStageplayRouteOverlay' test
```

Guard coverage is therefore the completed 778-case profile with its sole failed
owner corrected and rechecked through the entire affected architecture class.
This is combined bounded evidence, not a claim that the previous red profile
invocation was green. The unchanged unrelated guard classes are not repeated.
The engine is privately reinstalled once more for the desktop check so the final
artifact contains the ownership extraction as well as the reviewed presentation.


After the extraction, the final engine package/private installation succeeded and
the same desktop package command passed 21 tests again, zero failures/errors/skips,
in 7.440s with no queue waiting. Its refreshed F1 image is byte-for-byte identical
to the reviewed image. No Java changes followed the 57-check ownership repair.

The full follow-up guard command was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$Q" -Dmse=off -Pguards test -B
```

Its one failed architecture check is resolved by the complete 34-check architecture
rerun recorded above. The unrelated 777 passing cases from that completed profile
remain bounded evidence for unchanged behavior; the previous cancelled attempt is
not counted. No hook/policy edit or broad smoke run was needed. Follow-up commits
are local only, with clean worktrees retained for the lead; exact engine/desktop
head SHAs are in the completion handoff. Generated test reports and capture work
are removed after the retained review images and results have been inspected.

## Order-dependent MHZ2 snapshot follow-up

Starting integration head: `a04ae4b20` (the Day Off commits, the lead's
`a7cd9cef3` Apply-first refinement, develop `633d0e807`, and guide reconciliation).
The lead reported `TestS3kMhzAct2AuthoredRoute`'s team invocation failing inside
the exact 50-class smoke batch 65, while both the isolated route and the same
batch on develop passed. The reported batch contained 2,925 tests with one
failure and no errors/skips. These are lead-provided attribution results, not
additional runs by this follow-up.

The production owner is `RewindCodecs.CollectionCodec.capture`. MHZ's
`MhzMushroomCatapultObjectInstance.childRiders` is a final identity-map-backed
unordered set. Capturing it wrote participant references in identity-hash bucket
order. Restoring recreated sprites could reverse that order without changing
membership or gameplay. The reported differing bytes, 39 and 44, are the first
bytes of the two little-endian participant IDs in `childRiders`: main player
`1` and sidekick `2`. The set begins at byte 34 (four-byte size, followed by
one presence byte plus four ID bytes per rider). `InstaShieldObjectInstance`
appeared in the assertion's inventory of dynamic objects; it was not a differing
field.

Both the catapult and the collection codec were unchanged from develop
`633d0e807`. This is an allocation-history-dependent encoding defect exposed by
the integrated branch, rather than evidence of a surviving music lease, catalog
selection or reported-ability flag. A 27-class prefix of the same list passed
759 cases here; that narrowed check was not treated as proof of a repair.
No additional singleton resets or test ordering controls were introduced.

The shared codec now encodes unordered player sets in stable participant-ID
order, sorting only a capture copy. Lists and sequenced sets retain their
encounter order; live identity semantics and final-set restoration stay intact.
This extends the existing stable encoding policy for identity-keyed player
maps. No game/zone condition, snapshot-diff filtering, fitted allocation pattern,
hook or policy change is used.

Three regressions were added to `TestRewindPlayerReferenceCodecs`:

- `mushroomCatapultRiderSnapshotsIgnoreRecreatedIdentityHashOrder` exercises the
  actual catapult through ordinary piece contact and compact capture/restore.
  It failed before the fix at byte 39, matching the smoke assertion.
- `unorderedPlayerSetsCaptureStableMembershipAcrossRecreatedIdentities` covers
  duplicate character labels, null membership, replacement references, unchanged
  live iteration and in-place restoration. It failed before the fix too.
- `orderedPlayerSetsAndListsRetainTheirEncounterOrder` is the passing control
  for deliberate order and repeated list entries.

Participant roles are assigned opposite to measured set iteration in the
recreated state. The regressions are deterministic; they do not guess identity
hashes or depend on the previous test's allocations. Before implementation, the
complete player-reference class ran 12 tests: 10 passed, two expected failures,
zero errors/skips. Queue waiting was 21s; Maven execution was 27.213s. The prefix
run waited 6s and completed Maven in 2m09s with no failures/errors/skips.

Commands run from the engine task checkout, with
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`, DISPLAY/WAYLAND_DISPLAY unset, and the
same `Q`/`R` meanings used above:

```bash
# Investigation prefix: exactly the first 27 comma-separated classes in the
# lead-provided mhz-batch-classes.txt, including all three MHZ2 route invocations.
python3 "$Q" -Dmse=off -Psmoke "-Dtest=$PREFIX" -Dsonic1.rom.path="$R/s1.gen" -Dsonic2.rom.path="$R/s2.gen" -Ds3k.rom.path="$R/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test

# RED, before the codec change: 12 cases, two expected failures.
python3 "$Q" --lean -Dmse=off '-Dtest=TestRewindPlayerReferenceCodecs' test

# Codec consumers, real catapult behavior, and the required S3K load controls.
python3 "$Q" -Dmse=off '-Dtest=TestRewindPlayerReferenceCodecs,TestRewindCollectionCodecs,TestRewindObjectReferenceCodecs,TestRewindHelperCodecs,TestRewindRecordCodecs,TestRewindSchemaRegistry,TestMhzMushroomCatapultObjectInstance,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' -Dsonic1.rom.path="$R/s1.gen" -Dsonic2.rom.path="$R/s2.gen" -Ds3k.rom.path="$R/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test
```

The complete focused selection passed 128 cases with zero failures/errors/skips,
including both package-qualified classes matched by `TestSonic3kLevelLoading`.
Queue waiting was 10s; Maven execution was 1m28s. This is bounded regression and
consumer coverage, not a complete smoke-suite result. No Java changes followed
this focused pass.

The integration replay uses the lead's complete `mhz-batch-classes.txt` as
`BATCH` (50 comma-separated class names, MHZ2 at position 27, no duplicates),
without narrowing or adding classes:

```bash
python3 "$Q" -Dmse=off -Psmoke "-Dtest=$BATCH" -Dsonic1.rom.path="$R/s1.gen" -Dsonic2.rom.path="$R/s2.gen" -Ds3k.rom.path="$R/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test
python3 "$Q" -Dmse=off -Pguards test -B
```

The exact 50-class replay passed all 2,925 cases, with zero failures/errors/skips.
`TestS3kMhzAct2AuthoredRoute` ran all three incoming routes successfully inside
that same fork (18.50s). Queue waiting was 41s; Maven execution was 8m38s.
The full smoke suite was not repeated: this was the specific lead-requested
batch replay, after the deterministic regressions and focused consumers passed.

The complete `-Pguards test -B` run then passed 778 cases with zero
failures/errors/skips, including all 34 ArchUnit checks. Queue waiting was 137s;
Maven execution was 5m24s. All follow-up runs were sequential, on `a04ae4b20`
plus the codec/test changes described here; later edits only recorded evidence
in documentation. The prepared commit message was checked with
`git interpret-trailers --parse`; hooks were used normally. Delivery is a local
follow-up commit on the existing task branch, with no push or merge. Temporary
run logs are consumed and removed; the lead's supplied evidence is preserved.
