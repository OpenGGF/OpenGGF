# AIZ sprite culling and optional scanline limits

Implemented in `bc2be4c8e`, integrated into `develop` by `5f0dc96eb`.
The integrated code, resources, build/tooling and hook inputs match the
validated candidate exactly. Documentation updates do not change those inputs.

## Cause and implementation

The reported AIZ1 screenshot shows normal Sonic idle mapping frame 186 with
the lower body absent. Matching its intact ROM-backed head locates the camera
at (6023, 944), with Sonic centred at (6176, 1116). The foreground-plant layout
and parallax formulas reconstruct 304 declared sprite pixels on screen row
175 and 352 on row 176, before Sonic. His body disappears on row 176.
The engine capture below corroborates the dropout and correction; these
reconstructed counts are not a recorded native sprite-table measurement.

`Render_Sprites`, `loc_1AD54` (sonic3k.asm:36376-36407), rejects whole objects
outside their declared width/height before mapping expansion. Plants use
half-width 32 and half-height 48/60 according to their variant
(`AIZForegroundPlant_FrameData`, sonic3k.asm:60498-60505). OpenGGF submitted
loaded plants even when parallax projected them entirely offscreen. Their
pieces consumed both table capacity and per-line width ahead of the players.

Plants now use their projected position, native half-extents, selected
viewport and vertical-wrap mask before submitting mappings. All pieces of a
partially visible plant remain admitted, matching the ROM's object-level test.
No gameplay, clock, art or rewind state is added.

`display.spriteScanlineLimit`, default true, is saved under Settings > Display >
Picture as **Sprite scanline limit**. False removes the per-line count and
pixel-width budgets, including overflow carry. Explicit masks and total
sprite-table capacity remain active. Existing configuration enum ordinals are
preserved by appending the key; the mutable 0.7 API pin includes it. The reviewed
release descriptor remains unpublished candidate 0.7.0.

Engine bootstrap binds a live `BooleanSupplier` through `SpriteSatInternalAccess`.
It retains only the configuration service and is replaced when engine services
are configured again. Graphics consumes the injected value; unbound component
renderers and ordinary processing helpers retain native defaults.

## Rejected approaches

- Disabling budgets to hide the bug would leave incorrect producer inputs.
- Dropping individual offscreen pieces in the shared evaluator would break
  native width charging for partially visible objects.
- A first implementation read `GameServices.configuration` inside GraphicsManager.
  Fresh guards rejected that new low-level-to-runtime edge through both
  `TestArchUnitRules#low_level_layers_do_not_depend_on_runtime_layers` and
  `TestAudioPresentationArchitectureGuard#sourcePublicationKeepsTheExactReviewedRomDependencyDelta`.
  The bootstrap binding removes the edge without changing any architecture
  baseline. Its corrected audit reports 123 records, Added=[] / Removed=[].

## Verification

Base source: `445feabf8`. Development used the isolated local branch
`bugfix/ai-s3k-sprite-scanline`. Rebase preserved upstream DEZ lift maintenance
(`a3b021ec4`) and editor quarter-scale rendering/navigation (`0354fa953`),
including both coverage records. No sprite/settings conflicts occurred.

- On unchanged production source, `TestAizForegroundPlantRendering` ran three
  cases with two expected failures, no errors/skips: offscreen admission and
  variant-height rejection failed; partial visibility passed. All three pass
  after correction. Native320 / wide528 and both variants are covered.
- Corrected focused selection: 272 cases, 271 pass, one inherited failure,
  no errors/skips. The 192 feature/API cases and 79 architecture cases pass,
  including live preference changes, engine-context replacement/restoration,
  UI save/reload, 320/400 layout, H32/H40/wide budgets, retained masks/capacity,
  real EGL pixels in both replay paths, i18n/API pins and all required S3K
  loading/bootstrap checks. Basename selection resolves both level-loading classes.
- Final fresh guards on `bc2be4c8e`: 812 cases, 811 pass, one inherited failure,
  no errors/skips. Both introduced architecture failures are resolved.
- The remaining failure is exactly
  `TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline`, reporting
  `HCZCGZFanObjectInstance$FanPlatformChild#objectRef#fanChild`. A matched isolated
  run on unmodified destination `0354fa953` produces the identical test identity,
  exception type and full assertion. The HCZ source, analyzer and coverage
  baseline are unchanged from the pre-task source. No rewind baseline is relaxed.
- The first baseline attempt used lean heaps and javac exhausted heap during
  test compilation. No test executed; stale XML was rejected. The normal-heap
  retry above is the actual baseline result.
- Java 25 compilation, English catalog checks and strict pinned checks of
  the new owner citations pass. Preflight uses explicit Lua 5.4; the ambient newer Lua was
  rejected before tests. No category run was launched, so no category diagnostic
  directory or validation receipt is retained.

The inspected change-based plan selected 4,321 smoke candidate classes plus
guards. Proportionate validation replaces that broad run: the native evaluator
is unchanged by default, the plant change is local, and the optional presentation
budgets plus their bootstrap binding have direct boundary, real-renderer,
UI/persistence and contract checks. No task changes to physics, timing, ROM art,
build or selection policy. This is bounded validation, not a broad/full-suite pass
or native-emulator pixel certification.

## Commands and captures

Use the existing absolute original locked-on ROM path (CRC32 63522553) in the
ROM property; the following paths are neutral placeholders.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/path/to/jdk-25 LUA_BIN=lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl '-Ds3k.rom.path=/path/to/original-s3k.gen' \
  '-Dtest=TestAizForegroundPlantRendering,TestSpriteSatLimits,TestSpriteSatMaskPostProcessor,TestGraphicsManagerSpriteSatReplay,TestSpriteSatPixels,TestConfigCatalog,TestSettingTiming,TestSparseUserConfig,TestEngineSettingsScreen,TestBundledUiCatalogs,TestUiCatalogUsageGuard,TestModApiSignatureSurface,TestModApiPinPolicy,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestAudioPresentationArchitectureGuard,TestArchUnitRules,TestRewindCoverageGuard' test -B

# Separate fresh guard JVM; same Java/Lua environment and display isolation:
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -Pguards test -B

# Matched unmodified-destination check, with normal heaps:
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestRewindCoverageGuard' test -B
```

`GameplayCaptureTool` used S3K AIZ1, Sonic+Tails, centre (6176,1116), 60 neutral
frames, capture-from 59, native320, no video, explicit EGL and no desktop display.
The baseline reproduces the missing body. The corrected capture restores body
and shoes with the registered default limit enabled (no explicit user override).
Its complete state CSV is byte-identical to the baseline, including camera
(6016,944) and idle mapping 186. There are 1,425 changed pixels, bounded by
X104..211 / Y63..191. Repeating through the corrected bootstrap binding produces
byte-identical PNG and CSV output. MenuDesignCaptureTool's new focused scene
was inspected at native320 and 400: label, toggle and help fit without clipping.
Durable screenshots/CSVs remain outside the repository in the task capture directory.

Queue waits are separate from execution and overlap; do not sum them as elapsed
work. Individual Maven times: baseline capture 17.163s after 728s queue; initial
culling regression 1m24s after 656s queue; corrected focus 2m05s after 486s queue;
final guards 4m05s after 607s queue; matched normal baseline 37.539s after 536s
queue; final binding capture 17.216s after 643s queue. Compilation/preflight and
other intervals are not included in those individual measurements.
