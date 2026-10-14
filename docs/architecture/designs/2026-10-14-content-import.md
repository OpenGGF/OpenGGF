# Shared content import

The task extends the level-editor delivery on develop
`88fc03dacd9835437070d40109d62b97a665f944`: add real editor examples to the guide
and let users drop levels, level ZIPs, mods and mutators onto an import screen.

## Flow and ownership

**Advanced → Import content** and the editor command palette open the same screen.
Window drops at the idle master title or editor open it directly. Existing
dialogs, transitions and gameplay retain input ownership; a refused drop is
discarded, so it cannot unexpectedly reopen when gameplay ends. The GLFW
callback copies its transient UTF-8 names only. The next UI frame admits the
drop, and a virtual worker reads and validates the content. A path field and
the existing controller file browser provide the same workflow without dragging.

The shared screen, renderer and inspection service live in the existing
`com.openggf` host-composition package beside `ModManagerScreenHost` and
`ModSubsystem`. They connect editor and mod owners without exposing an importer
as a gameplay provider. The initial new `content` package introduced four edges
into the frozen runtime cluster; relocating these host coordinators corrected
that boundary without widening the dependency ratchet or adding an exemption.

`ContentImportService` recognizes a complete `.ogfproject`, a canonical exported
level directory or `level.json`, and a level ZIP with its definition at the root
or inside one enclosing folder. The existing strict parser reads the assets
through bounded `ModAssetRoot` snapshots; archive entry names never become
extraction paths. Native block geometry distinguishes S1/S2 v1 exports; v2 uses
the existing explicit S3K metadata. Destination host validation still happens
before a project is published.

The screen shows the name, type and final action before opening or installing.
Already parsed project/level data enters `EditorProjectWorkspace` through its
existing save/discard/cancel barrier. Cancellation, malformed input or rejected
host presentation leaves the authored document intact. Imported level exports
become new editable projects; their source folders and ZIPs are not rewritten.

Mods and mutators use the same package path: mutators are code-mod contributions,
not a new loose file format. Packed mod JARs, packed mod ZIPs and ZIPs containing
one JAR are structurally inspected without defining creator classes. The prepared
candidate retains the exact immutable bytes that the author reviewed. Installation
publishes a new `<manifest-id>.jar` without replacing another file, changing
enablement, or granting trust. Existing repository IDs, bundled IDs and retained
preference records conflict rather than restoring a prior enablement silently.
The title refreshes its catalog after installation; users enable/trust/apply
through **MODS**, which opens after successful installation. Active gameplay/editor
runtimes do not hot-install packages. A requested application quit waits for a
confirmed installation to finish before completing the normal quit flow.

Import/project modals claim global display and capture shortcuts before GameLoop
input dispatch. Capture chord history still advances while suppressed, so closing
a dialog with a held key cannot start a recording. An existing global shader
picker retains input until closed and blocks window drops.

Actual editor captures exposed a rendering owner bug: the S1/S2 arena-mask branch
read `GameServices.gameState()` after the editor parked gameplay. It now reads the
state injected into its `LevelManager`, just as the rest of the retained renderer
does. The editor owns fresh presentation state; gameplay boundary constants and
branch conditions are unchanged. A real S1/S2 mode-handoff regression plus
offscreen world drawing covers the failure.

## Decisions and limits

- A shared screen reuses the existing path browser, menu styles and project
  transactions instead of introducing separate drop implementations per feature.
- General ZIP extraction was rejected: bounded archive snapshots already provide
  the strict parser with all assets and avoid filesystem path publication.
- Automatic enablement or trust was rejected: dropping a package does not express
  a decision to execute its creator code. Inspection is static preparation.
- Automatic replacement of installed mods was rejected: updates need the existing
  ID/version/trust procedure and rollback copies, described in the installing guide.
- Multi-package batches and arbitrary source-tree builds remain separate work.
  Drop one project, level or mod package at a time.

## Verification

The lead owns one worktree build/test session. Focused checks cover all-game
projects/exports, archive bounds and traversal, ambiguous packages, immutable-byte
installation, conflicts, cancellation and modal input ownership. Real-ROM editor
workspace/lifecycle checks cover save/discard/cancel and rejected presentation;
offscreen captures supply the guide pictures. Relevant S3K loading/bootstrap/AIZ
checks and structural guards remain part of delivery verification. Exact completed
commands/results and any coverage limits are recorded below before integration.

All commands ran through `tools/testing/maven_queue.py -Dmse=off ... test -B`
on Java 25.0.4.1. ROM-backed runs used the original absolute main-workspace
`s1.gen`, `s2.gen` and `s3k.gen` paths; their CRC32/SHA-1 identities match the
repository's REV01/locked-on table. Offscreen checks used
`-Dopenggf.headless.gl=surfaceless-egl`, not desktop automation. Preflight passed
with `LUA_BIN=/usr/bin/lua5.4` after the ambient Lua version failed its check.

The change-based map selected 3,278 smoke candidates because Engine composition
and the new import package fall back to shared coverage. That selection is
disproportionate to file inspection and modal handoffs: no gameplay algorithm,
ROM decoder, timing contract, build or selection policy changes. Focused coverage
of the affected owners/consumers plus separate guards replaces the broad smoke
run; this is bounded validation, not a full-suite pass.

The expanded command used this exact test selector, the graphics property above,
all three absolute ROM properties, and `-Dopenggf.editor.guidePictures=true`:

```text
TestContentImport*,TestTitleContentImportAccess,TestEditor*,TestMasterTitle*,TestTitleInputOwnership,TestMenuTextEditor,TestMenuPathBrowser,TestModAssetRoot*,TestModCatalogValidator,TestModManifestParser,TestModRepositoryScanner,TestModStateStore,TestExternalContentPolicy,TestModManagerScreen*,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
```

It completed 675 cases across 63 candidate classes: 673 passed, two failed,
zero errors/skips (session 24247, queue 308s, Maven 80s). The new capture needed
an explicit test-host viewport after derived aspect resolution. The other failure
was `game.sonic3k.TestSonic3kLevelLoading#headlessFreshRuntimeLoadArmsHandoffWithoutRetainingRenderedTitleCard`:
expected handoff `-1`, actual `194` after 35 service ticks.

The exact handoff method passed alone on baseline `88fc03dac`. The following
ordered pair reproduced the identical assertion, value and skip status on both
baseline and current task inputs; each completed two tests with one failure and
no errors/skips. No native timing was changed to fit that test.

```text
TestEditorProjectPresentation,com.openggf.game.sonic3k.TestSonic3kLevelLoading#headlessFreshRuntimeLoadArmsHandoffWithoutRetainingRenderedTitleCard
```

The existing presentation fixture leaves graphics projection width 426 behind.
The native title-card teardown model reads that width for element retirement,
extending the tail past the test's native-width 35-tick assumption. The new guide
capture restores its viewport/projection/batching state. Baseline/current ordered
pair queue waits were 151s/117s and Maven times 25.684s/72s. This inherited
ordering failure remains; the importer adds no new/worsened failure there.

Fresh-JVM required S3K coverage then passed 60 cases, no failures/errors/skips
(session 82506, queue 1s, Maven 26.952s). Its selector was
`TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
with the same graphics flag and absolute `s3k.rom.path`. The short loading name
matches both existing loading classes; the formerly failing handoff case ran.

The renderer-owner fix was checked with
`TestEditorGuidePictures,TestEditorArenaMaskOwnership,TestLevelBoundsMaskGeometry,TestLevelSpritePresentation,TestEditorProjectLifecycle,TestEditorProjectPresentation`:
all 26 non-capture cases passed with no errors/skips. The remaining screenshot
failure exercised an incorrect library-hover-only setup; the refresher now uses
the normal library-confirm command and `Engine.syncEditorState()` before drawing.
Its native terrain example uses available ROM blocks rather than the persistence
fixture's repeated single-pattern floor. A full EHZ clone was rejected because
its shared bank contains missing native art; no replacement art was invented.

The final capture-only command (`-Dtest=TestEditorGuidePictures`, all three ROM
properties, the graphics flag and `-Dopenggf.editor.guidePictures=true`) passed
one case with zero failures/errors/skips (session 65367, queue 427s, Maven
23.878s). All six PNGs were visually reviewed and copied byte-for-byte into the
guide; each is 852×448, an exact 2× enlargement of the native 426×224 capture.
Added local links resolve, `AGENTS.md`/`CLAUDE.md` match and whitespace checks pass.

The separate `LUA_BIN=/usr/bin/lua5.4` guard run (`-Pguards`) completed 681 cases:
680 passed, one new ArchUnit dependency-edge failure, zero errors/skips
(session 20630, queue 416s, Maven 4m24s). The failure identified the initial
`content → control/editor/game/graphics` edges described above. Other guards
passed; the corrected placement is verified with focused architecture and
import-owner checks rather than repeating unrelated trace guards.

The corrected placement passed all 178 cases, zero failures/errors/skips
(session 60293, queue 21s, Maven 1m25s), with this exact selector plus the
graphics flag and all three ROM properties:

```text
TestArchUnitRules,TestArchitecturalReviewGuard,TestArchitecturalSourceGuard,TestContentImport*,TestTitleContentImportAccess,TestTitleInputOwnership,TestEditorProjectLifecycle,TestEditorProjectWorkspace
```

An earlier attempt at that final check was canceled while awaiting admission
(session 93568); Maven had not started and it supplied no validation result.
The completed run above verifies the final production source. Import/editor
focused coverage, fresh required S3K coverage and the affected structural guards
pass. The wider combined run retains only the precisely attributed baseline
ordering failure described above; no release-only full suite was run.
