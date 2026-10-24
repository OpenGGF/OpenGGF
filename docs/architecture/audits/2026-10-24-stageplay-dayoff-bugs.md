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
| 3. Repeated diagnosis | `StageplayWorkspace.java:1697` concatenated job message, result message and an overlay reason that already included the result. Route and Direction Details now use one composed diagnosis, retaining motion and counts. | `routeDetailsShowsTheDiagnosisOnceAndRetainsWorkCounts`: expected one occurrence, observed three before; passes after for both Details paths. |
| 4. Advice depends on Attempt frames | `StageplayRoutePlanner.java:365` already reserved Run nodes/ticks; the production note's claim that the check never ran was incorrect for this base. Its diagnostic inherited the attempted window, ran after detours, and discarded an unsuccessful diagnosis. Diagnosis now runs before detours, within the same aggregate nodes/ticks/deadline, using a horizon bounded by the continuous shot, next actor key and maximum route horizon. A longer successful rehearsal asks for more Attempt frames as well as Run. It never supplies an applicable patch. Specific direct settling/landing/charge explanations survive the fallback. | `ghzWalkAdviceDoesNotDisappearAtTheDefaultAttemptWindow` failed at 300 and passed at 419 before the fix; both pass after. The 300 case requires the longer-window qualification. The synthetic gap, sealed-wall, cancellation/tiny-budget, native charge and cold replay controls pass. An older exact wording assertion now permits the explicit longer-window qualification. |
| 5. Truncated result | `StageplayRouteOverlay.java:75` supplied an overlong sentence to the HUD's one-line fitter. The budget status is now “Search limit reached.” Uncertainty and advice remain in F1. | `routeResultSentenceFitsTheActualSidePanelWithoutEllipsis` reproduced “reachability is unk...” at 1080p, then passed at 720p, 1080p and 1024x576. |
| Lead 1. Goal legend | `StageplayHud.java:323` used white for the goal, independently of the diamond. `StageplayRouteOverlay.goalColor()` now owns both. | The production offscreen picture test failed on white `$FFFFFF` versus failed-goal `$FF7887`, then passed. Successful goals use the same shared cyan selection. |
| Lead 2. Command name | `StageplayWorkspace.java:1760` still named “Plan collision route”. It now says “Plan route”. | `planningStatusUsesTheCurrentPlanRouteCommandName` failed before and passes after. |
| Lead 3. Dialogue placement | `StageplayWorkspace.java:1329` bottom-aligned new lines while the screenplay compiler used Y=12. Both now use the compiler's single `DEFAULT_DIALOGUE_Y` authoring default. | `editorAndScreenplayStartDialogueAtTheSameTopMargin` observed 140 instead of 12 before the fix, then passed and matched the compiled default. |
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
zero queue waiting. No production changes followed this run.

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
