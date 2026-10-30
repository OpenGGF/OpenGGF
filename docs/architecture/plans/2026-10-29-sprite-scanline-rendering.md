# AIZ sprite culling and optional scanline limits

The reported AIZ1 screenshot shows normal Sonic idle mapping frame 186 with
the lower body absent. Matching its intact ROM-backed head pixels locates the
camera at (6023, 944), with Sonic centred at (6176, 1116). The foreground-plant
layout and parallax formulas yield 304 declared sprite pixels on screen row
175 and 352 on row 176, before Sonic. His pixels disappear on row 176.
These counts reconstruct the candidate inputs; the offscreen engine capture
and regression checks below must corroborate the correction.

`Render_Sprites`, `loc_1AD54` (sonic3k.asm:36376-36407), rejects whole objects
outside their declared width/height before mapping expansion. AIZ plants use
half-width 32 and half-height 48/60 according to their variant
(`AIZForegroundPlant_FrameData`, sonic3k.asm:60498-60505). OpenGGF currently
submits every loaded plant, including those projected entirely offscreen by
parallax. The shared SAT evaluator correctly charges submitted offscreen
pieces, so changing that rule would conceal the producer defect.

Implementation:

- Cull plants using their parallax-adjusted position, native half-extents,
  selected viewport and vertical-wrap mask before submitting mapping pieces.
  Retain all pieces of a partially visible plant, as the ROM does.
- Add persisted `display.spriteScanlineLimit`, default true, on Settings >
  Display under Picture. Read it when preparing each native sprite table.
  False removes the per-line count and pixel-width budgets, including their
  overflow carry; explicit sprite masking and table capacity remain intact.
- Keep the ordinary SAT processing helpers native by default. Pass the live
  presentation preference explicitly at the engine ingress.

Rejected approaches: disabling limits to hide the bug would leave bad inputs;
discarding all offscreen pieces in the shared evaluator would change native
budget semantics for partially visible objects. Neither fixes the producer.

Validation: fail the plant-culling regressions on the base; cover native and
wide viewports, both variants, partial visibility and vertical boundaries.
Check both disabled count/pixel budgets and retained mask/table behavior,
saved settings through the real menu, and ROM-backed offscreen captures at
the reported location. Inspect the change-based smoke selection against
445feabf8, run its applicable coverage once, and attribute disputed failures
with matched focused base/current checks. Integrate into develop, push only
develop, and remove the fully accounted-for task worktree/branch.

Checkpoint: base source 445feabf8; task worktree
`.worktrees/ai-s3k-sprite-scanline`, branch `bugfix/ai-s3k-sprite-scanline`.
The baseline positioned offscreen capture reproduces the absent lower body at
(6176, 1116), idle mapping 186, camera (6016, 944). It waited 728 seconds and
Maven took 17.163 seconds. Java 25 `test-compile` passed in 1m34s. With unchanged
production source, `TestAizForegroundPlantRendering` ran three cases: two
expected failures (`parallaxPlantsOutsideTheViewportDoNotConsumeSpriteCapacity`
and `cullingUsesTheSelectedViewportAndVariantHeight`), zero errors/skips;
partial visibility passed. Queue wait was 656 seconds; Maven took 1m24s.
The plant correction, persisted default-on display preference, explicit optional
line-budget path, localized menu text and mutable API pin are implemented.
Candidate `test-compile` passes in 1m49s. The separate two-case preference request was cancelled before execution because
the existing 17-class focused job covers both cases; no separate pre-setting
failure run was completed. The corrected gameplay capture passes: native line limits remain enabled and
Sonic has his body/shoes. The complete 60-row state CSV is byte-identical to
the baseline; 1,425 image pixels differ, bounded by X104..211 / Y63..191.
Its queue wait was 941s and Maven took 1m22s (including recompilation and the
existing Discord worker exit warning). The focused regression selection resolves 18 classes and passes 191 cases with
zero failures/errors/skips (queue 933s; Maven 46.341s). This includes the
regression's red-to-green cycle, real EGL pixels in both replay paths, native /
wide / H32 limits, UI save/reload and live runtime consumption, configuration,
i18n and API pins, and every required S3K loading/bootstrap gate. The native320
menu capture was inspected; the existing native400 request remains queued
(session 92515). Fresh structural guards are queued (28157).

The inspected change-based plan selects 4,321 smoke candidate classes plus
guards. Proportionate validation replaces that broad run here: the native
algorithm/default path is unchanged; only local plant admission and an explicit
presentation-budget gate change behavior, with direct boundary, real-renderer,
UI/persistence and contract coverage above. No physics, clocks, assets, build
or selection policy changes. No broad/full suite pass is claimed.

Focused command (the ROM property points to the original locked-on dump,
CRC32 63522553; use its existing absolute path):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/path/to/jdk-25 LUA_BIN=lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl '-Ds3k.rom.path=/path/to/original-s3k.gen' \
  '-Dtest=TestAizForegroundPlantRendering,TestSpriteSatLimits,TestSpriteSatMaskPostProcessor,TestGraphicsManagerSpriteSatReplay,TestSpriteSatPixels,TestConfigCatalog,TestSettingTiming,TestSparseUserConfig,TestEngineSettingsScreen,TestBundledUiCatalogs,TestUiCatalogUsageGuard,TestModApiSignatureSurface,TestModApiPinPolicy,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test -B
```

The next base is develop a3b021ec4 (DEZ lift-only behavior plus its existing
interface comment and evidence). No sprite/settings code overlaps. Rebase the
local task commit before the pending guards execute, retain both coverage notes,
and reuse the focused result for the unchanged sprite/settings sources. Preflight passes
with Java 25 and explicit `LUA_BIN=/usr/bin/lua5.4`; the ambient Lua was newer
and rejected before tests. Existing main-workspace changes and submodules are
outside this task. Integration/push remain pending.


## Guard correction checkpoint (2026-10-30)

The first fresh guard run on rebased 4a20a7eef executes 812 cases, with three
failures and no errors/skips (queue 459s; Maven 2m56s). Two are this task's
same new edge: `GraphicsManager.endSpriteSatCollectionAndReplay` directly
called `GameServices.configuration`, rejected by
`TestArchUnitRules#low_level_layers_do_not_depend_on_runtime_layers` and
`TestAudioPresentationArchitectureGuard#sourcePublicationKeepsTheExactReviewedRomDependencyDelta`.
No architecture baseline is relaxed. The correction binds a live
`BooleanSupplier` from `EngineServices` through `SpriteSatInternalAccess`;
unbound component renderers retain native limits. The closure retains only
the configuration service, and reconfiguration replaces it. An added
production-table test exercises replacement and restoration of the engine
context. These four Java files are uncommitted pending corrected validation.

The third failure is
`TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline`, reporting only
`HCZCGZFanObjectInstance$FanPlatformChild#objectRef#fanChild` in untouched
code. A matched isolated check is queued on destination a3b021ec4 (session
35804); attribution is pending. The corrected feature/architecture/rewind
selection is queued in session 53670, and an identical gameplay capture
through the new bootstrap binding is queued in 64230. Compilation passes;
no corrected test result or delivery is claimed yet. Menu captures at both
320 and 400 have been inspected and retained. The source changes did not
alter their settings layout.


Corrected focused run: 272 cases, one failure, no errors/skips (queue 486s;
Maven 2m05s). The 192 feature cases and 79 architecture cases pass, including
engine-context replacement/restoration. The exact reviewed dependency audit
reports 123 records with Added=[] / Removed=[]. Only the same HCZ field key
remains. The first baseline attempt used lean heaps and javac exhausted heap
during test compilation; no test executed and its pre-existing XML is stale.
The required normal-heap retry is queued in 89395. Destination has advanced to
0354fa953 with editor-only camera/drawing changes and its architecture rule;
HCZ source is byte-identical to 445feabf8. Fold the unpublished wiring fix into
the task commit, rebase onto that destination, and run fresh combined guards.
The existing gameplay capture request remains alive and will consume the
updated tree. No architecture or rewind baselines are changed by this task.
