# Bug competition — Team Beta submission

Finalized claims: **B01–B10, ten discoveries and ten proposed verified fixes**,
subject to the official judge and integration checks. The frozen
`2026-10-18-bug-competition-judging-rules.md` was read before finalizing.
Discovery stopped at these ten roots. Variants within each row count once.

## Source state and ownership

- Common base: `50348f299a2cc152a3c7e641b96974df504e2990`.
- Submitted code/test commit: `0e7da8f15c87de4c1edbe002f4cace6b56f73090`.
- Branch: `bugfix/ai-bug-competition-beta`.
- Worktree: `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-6a2e33fe`.
- All runs below executed with HEAD at the common base and uncommitted task
  changes. G2 tested the exact final source/test state subsequently committed
  as `0e7da8f15c87de4c1edbe002f4cace6b56f73090`. This audit is documentation only.
- R1 production was the common base. R2 production for B03–B10 still matched
  the common base; only the independent B01/B02 production fixes were present.
  G1 contained all fixes, with B10's unsuccessful January 1 local-date attempt.
  Between G1 and G2, production changed only B10's fixed ZIP date to January 2;
  B09 also gained scene/reskin test parameters. Unaffected G1 checks were reused.
- Beta lead task client request: `bugcomp-20261018-beta-discovery-r1`; backing
  T3 thread: `thread:delegated-task:command%3Amcp%3Ac0d6a3ca-3c84-4ab0-a0cb-e06f2690c5b9%3Adelegate-task%3Abugcomp-20261018-beta-discovery-r1`.
  Native child task IDs: `/root/settings`, `/root/editor`, `/root/resource_tools`.
  All contributors used **OpenAI Codex GPT-6.1-Sol**. The lead authored B01/B02,
  coordinated and observed every Maven run, inspected the combined diff and
  reports, updated documentation, and committed. Children edited bounded owned
  files and ran no Maven jobs or commits.

## Claims and evidence

Method names below identify the committed regression; R1/R2/G1/G2 refer to the
exact focused commands in the following section.

| ID / contributor | Distinct root and supported trigger | Expected contract | Observed common-base failure | Regression and correction |
| --- | --- | --- | --- | --- |
| **B01**, Beta lead | `KosinskiReader.decompress` and `ResumableKosinskiDecoder.decodeCommand` mask the largest backward distance to zero. A valid short match after 256 literal bytes encodes offset byte zero; a valid long match after 8192 literals encodes the maximum window distance. | `docs/s2disasm/s2.asm`, `Kos_RLE` `.loop2` initializes `d2=-1` before replacing its low byte, yielding -256; `Kos_SeparateRLE` preserves the signed 13-bit range, yielding -8192. Decoder APIs accept standard Kosinski streams, including these ordinary format boundaries. | R1: short-match output at index256 expected17, got0 in the synchronous decoder; long-match resumable decoder threw `ArrayIndexOutOfBoundsException` at8192. | `TestKosinskiWindowBoundaries#shortOffsetZeroCopiesFrom256BytesBackInBothDecoders` and `#longOffsetZeroCopiesFrom8192BytesBackInBothDecoders`. Preserve full positive distances256/8192 instead of wrapping them to zero. Both decoders and inspection now agree; G1 also passed existing vectors and seven ROM-backed KosM tests. These are one distance-normalization root. |
| **B02**, Beta lead | `DplcStaticFlattener.applyDplcRemap` splits noncontiguous DPLC requests into tiles but places tiles as if the original multi-tile piece were unflipped. Supported 2x2 mapping piece with H/V flips and two noncontiguous requests. | The flattener promises equivalent absolute-source mappings. `SpritePieceRenderer.renderPreparedPiece` reverses tile positions as well as in-tile pixels for mapping flips. Callers include `Sonic3kObjectArt`, `FireShieldDashArt`, and creator `RomArtMaterializer`. | R1: expected X positions `[3,3,-5,-5]`, got `[-5,-5,3,3]`. The corresponding Y positions were also unreversed in the baseline source. | `TestDplcStaticFlattener#splitPiecesKeepWholePieceFlipPositions`. Reverse each split tile's position on the corresponding flipped axis, retaining tile flags, palette and priority. Existing contiguous/unflipped cases and the new geometry case passed G1. |
| **B03**, `/root/settings` | `GlfwKeyNameResolver.normalise` uses default-locale uppercase. On a Turkish installation, ordinary lowercase bindings `right_shift` and `i` resolve incorrectly. | `CONFIGURATION.md` key-binding section documents case-insensitive GLFW names, including modifiers. Names are configuration syntax and must resolve independently of the user's display language. | R2: `right_shift` expected Right Shift344, resolved to fallback Space32. | `TestConfigurationKeyBoundaries#lowercaseKeyNamesWorkOnTurkishInstallations`. Normalize with `Locale.ROOT`. Real configuration-service integer/chord resolution passed G1; the test restores the JVM locale. |
| **B04**, `/root/settings` | `LegacyAwtKeyCodeMapper.toGlfw` lacks keypad mappings. Supported legacy `config.json` with default AWT arrow sentinels and keypad bindings retains AWT96/107 as GLFW codes. | `ConfigMigrationService` promises AWT-to-GLFW compatibility; `CONFIGURATION.md` documents migration of legacy files. AWT `VK_NUMPAD0=96` must remain physical keypad0, rather than GLFW grave accent96; `VK_ADD=107` must become keypad add334. | R2: keypad0 expected320, got96. | `TestConfigurationKeyBoundaries#upgradingLegacyKeypadBindingsPreservesTheirPhysicalKeys`. Add digit/arithmetic keypad translations; checks keypad0/add and persisted reload. Existing migration/chord/name suites passed G1. All keypad variants are one missing translation-family root. |
| **B05**, `/root/settings` | `EngineSettingsScreen.updateCapture` unconditionally skips modifier keys, with no release path. Enter physical binding capture, then tap/release Right Shift. | UI promises `Press a key or chord`; `CONFIGURATION.md` promises physical capture. Bare modifiers are supported player buttons: stock P2 A is Right Shift and P2 Start is Right Control. | R2: capture prompt remained open after the supported modifier tap. | `TestEngineSettingsScreen#tappingAModifierCanCaptureItAsAPlayerButton`. Defer modifiers until release, allowing a following ordinary key to complete a chord; clear pending state on cancel/start/success. Bare key save/reload plus `#heldModifiersStillCaptureChordsAndCancelledModifierTapsAreForgotten` passed G1. |
| **B06**, `/root/editor` | `LevelEditorController.toggleActiveLayer` changes FG/BG without refreshing the composition target. With different FG/BG blocks at the same cell, descend into FG, switch layer, then H-flip the BG cell. | `docs/guide/creating/level-editor.md` documents active-layer composition, L layer switching and H/J cell flips. Local composition preserves other placements and source definitions. | R2: BG map placement remained block1; the requested local edit was rejected because the selected block still described FG. | `TestEditorCompositionTargets#changingLayerAtBlockDepthEditsTheNewLayerAndKeepsTheTerrainBrush`. Refresh selection from the new active target. G1 verifies BG edit, FG/source preservation, unchanged brush, undo and redo. |
| **B07**, `/root/editor` | `LevelEditorController.adjustSelectedChunkSolidTileIndex` computes the new collision index from the chunk brush instead of the selected block cell. Brush shape0, selected cell shape1, then increment with `]`. No layer switch is involved. | Editor guide documents `[`/`]` adjusting the selected chunk's shape; `EditorHudPainter` displays the actual cell's shape. An increment must advance the visible target, independent of the retained painting brush. | R2: selected shape expected2, remained1 because brush0+1 was written as the target's existing value. | `TestEditorCompositionTargets#shapeIncrementStartsFromTheTargetCellProfileInsteadOfTheChunkBrush`. Resolve the adjustment basis from `selectedBlockCellPreview`. G1 verifies target2, shared source/brush preservation, undo/redo and nearby collision/controller/HUD consumers. Independent from B06's layer-switch invalidation. |
| **B08**, `/root/resource_tools` | `ProjectScaffolder.classPrefix`/`displayName` assume every accepted manifest ID starts with a Java identifier and has nonempty hyphen-separated parts. Normal `init --id 3d-rings` produces invalid Java; `rings--plus` crashes. | `docs/modding/concepts/id-semantics.md` explicitly permits `[a-z0-9][a-z0-9-]{0,63}`. `docs/modding/ggfmod.md` promises a complete creator starter for accepted identities. | R2: generated `3d-rings` sources failed javac (exit1); `rings--plus` threw `StringIndexOutOfBoundsException` on an empty segment. | `TestProjectScaffolder#acceptedManifestIdsProduceCompilableJavaNames`, both parameters. Skip empty separator parts and prefix digit-leading derived Java names. Generated real sources compile; existing starter/package consumers passed G1/G2. Both cases are one ID-to-generated-name root. |
| **B09**, `/root/resource_tools` | Starter manifest interpolation emits valid identity `null` as bare YAML, so the parser sees a null node instead of the mod ID string. This is independent of B08: `NullMod` is valid Java. | The same accepted ID grammar permits `null`; `ModManifestParser.requiredText` requires textual IDs. Every starter must produce a readable manifest for an accepted identity. | R2: all four tested starter modes rejected their own generated manifest with `Missing or null required field: id`. | `TestProjectScaffolder#acceptedYamlKeywordIdsRemainManifestStrings`. Quote generated ID/name scalars in the template/simple generators and maintained-starter ID substitution. G2 passed object, music, character, standalone, scene and reskin parameters. All interpolation sites count once. |
| **B10**, `/root/resource_tools` | `JarPackager` uses a fixed epoch instant with `JarEntry.setTime`, which encodes host-local DOS dates and timezone-dependent extended timestamps. Package the same valid input under UTC and America/Los_Angeles. | `docs/modding/ggfmod.md` and `JarPackager` promise deterministic jars. Host timezone must not change the same distributable's bytes. `CreatorCatalogProbe` shares the timestamp consumer. | R2: identical input produced jars of356 versus374 bytes. G1's first local-date correction still differed at byte60. | `TestJarPackager#packageBytesAreIndependentOfHostTimeZone`, unchanged byte-equality oracle. Use fixed local DOS date1980-01-02 with `setTimeLocal` in both consumers. G2 passed byte equality, stable timestamp/name order, ordinary packaging and creator-catalog checks. |

## Commands and completed outcomes

All Maven invocations used the shared queue from this worktree; the leased
version probe reported Maven3.10.0 and Java25.0.4.1. No worker launched another
test/build job. Source/test edits were frozen during each run.

**R1**, session90093, production at common base:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestKosinskiWindowBoundaries,TestDplcStaticFlattener' test
```

Exit1: six tests, two assertion failures, one error, zero skips. The failures
are B01 short offset and B02 split flip; the error is B01 long offset. Maven
reported1:15 execution, queue wait0s.

**R2**, session32625, B03–B10 production unchanged from common base, B01/B02 fixed:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestConfigurationKeyBoundaries,TestEngineSettingsScreen#tappingAModifierCanCaptureItAsAPlayerButton,TestEditorCompositionTargets,TestProjectScaffolder#acceptedManifestIdsProduceCompilableJavaNames+acceptedYamlKeywordIdsRemainManifestStrings,TestJarPackager#packageBytesAreIndependentOfHostTimeZone,TestKosinskiWindowBoundaries,TestDplcStaticFlattener' test
```

Exit1:18 tests, seven assertion failures, five errors, zero skips. Twelve
failing cases demonstrate the remaining eight roots; the six utility cases
passed. Maven reported1:14 execution; observed queue wait9s.

**G1**, session50721, all fixes with B10's first local-date attempt:

The only machine-local argument below is represented by
`OPENGGF_COMPETITION_MAIN`, the parent main checkout supplied in the task.
The executed property was its absolute `s3k.gen` path. This notation avoids
committing a user-home path; no ROM was copied or linked. Identity was checked:
4194304 bytes, CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`.

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off "-Ds3k.rom.path=$OPENGGF_COMPETITION_MAIN/s3k.gen" '-Dtest=TestKosinskiWindowBoundaries,TestKosinskiDecompressor,TestKosinskiModuled,TestResumableKosinskiDecoder,TestDplcStaticFlattener,TestConfigurationKeyBoundaries,TestEngineSettingsScreen,TestConfigMigrationService,TestConfigKeyNameResolution,TestConfigKeyChordResolution,TestKeyChord,TestGlfwKeyNameResolver,TestLegacyConfigMigration,TestEditorCompositionTargets,TestEditorCollisionCommands,TestEditorInteractionSafety,TestEditorBgLayer,TestLevelEditorController,TestEditorHudControls,TestProjectScaffolder,TestPurposeStarters,TestJarPackager,TestCreatorCatalogProbe' test
```

Exit1:258 tests in23 exact focused classes, one failure, zero errors/skips.
Only `TestJarPackager#packageBytesAreIndependentOfHostTimeZone` remained red;
all nine other fixes and nearby consumers passed. `TestKosinskiModuled` ran
seven ROM-backed tests, zero skips. Maven reported1:17 execution, queue wait0s.
The XML totals/skips and precise failure were inspected before diagnostics cleanup.

**G2**, session54606, final source/test state in submitted code commit:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestJarPackager,TestCreatorCatalogProbe,TestProjectScaffolder' test
```

Exit0:20 tests, zero failures/errors/skips. Maven reported1:18 execution,
queue wait0s. This reran only consumers affected by the final timestamp
correction and additional starter parameters; unchanged G1 coverage was not
repeated. Task wall time and overlapping worker intervals were not measured.

## Evidence limits, rejected experiment and delivery boundary

- **NOT SUBMITTED as another bug:** the first B10 correction used
  `setTimeLocal(1980-01-01T00:00)`. G1 killed that approach with the unchanged
  byte comparison. January1 midnight equals the JDK's pre-1980 sentinel and
  still creates a timezone-derived extended timestamp. The owning
  [OpenJDK25 implementation](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/java.base/share/classes/java/util/zip/ZipEntry.java#L221-L240)
  explains why the final January2 local date avoids it. This is one B10 root,
  not an extra claim.
- B01 proves valid format boundaries using hand-derived streams and the
  owning ROM routine, with real AIZ1 archive compatibility. It does not claim
  a particular campaign trace previously reached those offsets.
- B02 verifies geometry/metadata through the pure mapping transformation;
  no GL pixel capture or native gameplay comparison was performed.
- Settings tests use real configuration persistence and headless physical
  input events, restoring changed locale/timezone state. No ambient desktop
  automation was used. Editor tests use supported mutable level fixtures and
  inspect mutation, source ownership, brush state and history.
- Starter tests compile generated Java and parse/package produced resources;
  the developer's complete external Maven authoring project build was not run.
- The measurement-hazard table in
  `docs/agent-workflow/briefing-trace-rounds.md` was read. These are focused
  results, not a broad smoke/full-suite or trace-sweep result. No full suite,
  broad smoke, PR, integration, push or task-worktree cleanup was performed.
  Parent owns judgment and delivery-wide smoke/integration/push. No additional
  findings are submitted from discarded private hypotheses.
- Existing changelog reliability prose was extended in `CHANGELOG.0.7.md`;
  capture semantics were clarified in `CONFIGURATION.md`. No README theme,
  API signature pin, gameplay clock, trace authority or ROM asset was changed.
- All child work ended; no persistent CLI session or unmanaged worker process
  was created. Generated diagnostics were inspected and remain disposable
  worktree-local output, with no archived raw logs or validation receipts.


## Author response A1 — canonical Phase 2 sample synchronization

This is the single bounded implementation response requested after combined
smoke, not a new bug claim or another opposing review round. The original
B01–B10 ledger above is preserved verbatim. The parent reports Alpha supported
all ten discoveries and accepts those discoveries; fix credit remains subject
to delivery. No unrelated baseline failures are addressed here.

**Cause and accepted objection.** B09 corrected the authoritative default
`ggfmod init` manifest template to quote ID/name strings, but omitted the
canonical checked output in
`src/test/resources/mods/sample-mod-src/project/src/main/resources/META-INF/openggf-mod.yaml`.
The fixture README defines that project as exact checked-in output of
`ggfmod init --id phase2-sample --package example.phase2sample`.
`TestPhase2SampleModIntegration.buildInitializedSample` regenerates the whole
tree and `assertTreesEqual` checks inventory and `Files.mismatch == -1` before
building the sample. Three downstream tests share that helper.

The parent's completed observations in run `20261018T092507Z-2617724c`, combined
candidate `02f3b84a2` plus the frozen optional-ffmpeg test/doc inputs, were:

- `destroyedCreatorBadnikSubmitsTheGamesBadnikHitSound`
- `realCreatorSampleBuildsAndRegistersAuthoredResourcesWithoutRom`
- `realCreatorSampleLoadsZoneObjectRewindAndKeyedSave`

Each failed `Checked sample differs from real ggfmod init at
src/main/resources/META-INF/openggf-mod.yaml`, expected `-1`, actual `21`.
This is a task-introduced sample-mirror regression, not a false baseline B09
claim or an additional discovery/refutation claim. A source-only comparison
of the frozen Beta fixture to the current rendered manifest template also
reports byte21; after the two-line correction it reports `-1`.

**Minimal repair and state.** Quote only `id: "phase2-sample"` and
`name: "Phase2 Sample"` in that one canonical manifest. The generator,
strict tree/byte assertions and other maintained sample manifests are unchanged.
The worker inventory found no additional mirror requiring regeneration.
The exact test input is frozen Beta HEAD
`8b35a246800b744d1aba52a0d122380d9029885e` plus this two-line fixture edit;
its Git blob is `16d8207acbe8c0c526a0248f33237238ab0013e7`.
The only additional change is this labeled audit append; runtime/test Java
and all other test resources retain frozen Beta contents.

**Focused command.** From the Beta worktree above, the recorded command uses
the three absolute ROM paths in the parent's main checkout; below
`OPENGGF_COMPETITION_MAIN` denotes that supplied path, as in G1, to avoid
committing a machine-local home path. No ROM copy/link was created.

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off "-Dsonic1.rom.path=$OPENGGF_COMPETITION_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_COMPETITION_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_COMPETITION_MAIN/s3k.gen" '-Dopenggf.headless.gl=surfaceless-egl' '-Dtest=TestPhase2SampleModIntegration,TestProjectScaffolder,TestSampleModsPackage,TestPurposeStarters' test
```

Session70692 completed successfully at `2026-10-18T10:01:26Z`: **22 tests,
zero failures/errors/skips**. Breakdown: `TestPhase2SampleModIntegration`4,
`TestProjectScaffolder`13, `TestPurposeStarters`3, `TestSampleModsPackage`2.
All three reported downstream failures now pass, alongside the fourth Phase2
reskin case; the S2 ROM-backed zone/object/rewind/keyed-save test executed
without a skip. This also verifies canonical tree/byte equality, actual sample
build/SDK lifecycle, generated starters and all nine maintained sample packages.
The queued wrapper reported61s admission wait and Maven45.195s execution;
these intervals are distinct. Surefire XML totals, named tests and skips were
inspected before consuming diagnostics. No claim of broader smoke coverage
follows from this focused run.

**Participants and limits.** Beta lead owns the repair, focused run and commit.
Existing child `/root/resource_tools` performed a bounded source-only contract
and mirror inventory; it edited no files and launched no Maven process.
No new agent/session was created. The existing three failing tests remain the
regression oracle; no implementation-mirror tests were added or assertions
weakened. No broad smoke/full-suite, integration, push or PR is performed by
Beta in this response. The integration tree remains untouched while the
parent owns the active combined smoke and subsequent amendment integration.
