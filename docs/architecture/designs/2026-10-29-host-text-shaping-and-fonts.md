# Host text: shaping backend, fonts and layout owner

Date: 2026-10-29. Base: develop `d425f50b2` (contains `b54676a18`). Scope: the fonts/layout
workstream of the i18n delivery. Catalogs, locale selection and translated labels are owned
elsewhere; this owner receives plain strings plus an explicit language.

## Decision

`com.openggf.graphics.text.HostText` is the single owner of host-interface measurement,
fitting, wrapping, shaping, caret/hit geometry and glyph selection. `MenuPixelFont` draws its
`ShapedLine`s; `MenuStyle`, `MenuChrome`, `MenuDetailsScreen`, `MenuTextEditor`,
`EditorHudCanvas` and `EditorTextRenderer` measure through it. ROM, gameplay HUD and the debug
`PixelFont` paths are untouched.

- Bidi: `java.text.Bidi` (first-strong unless the language says otherwise). Graphemes and line
  breaks: `java.text.BreakIterator` (JDK 25: extended clusters incl. Indic conjuncts and emoji
  ZWJ; Thai dictionary breaks; CJK kinsoku). All `java.base`, no AWT.
- Shaping: HarfBuzz 12.3.2; rasterization: FreeType 2.14.1, both from LWJGL 3.4.1
  `lwjgl-freetype` natives (linux x64/arm32/arm64, windows x64/x86/arm64, macos x64/arm64).
  `lwjgl-harfbuzz` supplies only the Java bindings: its own `libharfbuzz` lacks the `hb_ft_*`
  integration, but LWJGL's FreeType library exports the complete HarfBuzz API, so
  `OpenTypeFace` sets `Configuration.HARFBUZZ_LIBRARY_NAME=freetype` before first use and the
  POM carries no HarfBuzz natives. Native image needs reflection on `FreeType.getLibrary`
  (`reflect-config.json`), which HarfBuzz's initializer calls.
- Glyph availability is not shaping. `HostFace.SIMPLE_SCRIPTS` (Latin, Greek, Cyrillic, Han,
  kana, Hangul, …) may be drawn one glyph per cluster by bitmap faces; any other script is
  drawn only by an OpenType face through HarfBuzz. Otherwise the cluster is reported in
  `ShapedLine.missing()` and drawn as a box. Unifont's isolated Arabic/Tamil glyphs are never
  used as a fake success.
- Shaping script tags come from ICU's maintained Unicode alias/ISO 15924 data,
  not guessed prefixes of Java enum names. This keeps Malayalam (`Mlym`),
  Gurmukhi (`Guru`), Tamil (`Taml`) and other added font scripts code-free.
- Fonts are data: `fonts/host-fonts.json` lists faces in fallback order with optional
  `preferredFor` BCP 47 tags (Han variants, reserved line height) and per-role pixel sizes. A
  new language whose script is covered needs no change; a new script adds a font, licence and
  manifest entry. There is no per-language enum.
- Look: printable ASCII keeps the original fixed 6x8 / bold 9x10 cells pixel-for-pixel.
  `MenuGlyphs` adds authored Cyrillic and composed Western/Central European accents on the
  same grid (capitals take a five-row form under a mark, the 5x7 LCD convention), so
  es/fr/de/pt/it/pl/id/ru stay native, bold labels included. CJK and Hangul use Fusion Pixel
  10px (ja, zh-Hans, zh-Hant forms selected by language). Arabic/Urdu, Devanagari, Bengali and
  Thai use hinted Noto (UI variants where available) at 11-13 px, the smallest sizes found
  legible in probes; they keep the caller's cell baseline and report overhang through
  `LineMetrics` (compact pitch 16-17 px instead of 8) rather than shrinking.
- Language context: `TextLanguage` (explicit `Locale` + direction) per call, or the
  UI-thread `HostTextContext` that the locale owner activates at a frame boundary;
  `revision()` keys cached wraps/hitboxes. `MenuPixelFont.setLanguage` pins a font (for
  example a language picker). Nothing calls `Locale.setDefault`.

## Rejected

- **JDK `java.awt.font.TextLayout` as the runtime owner.** On the JVM it shapes correctly
  (it embeds HarfBuzz), but native packaging would need AWT natives plus fontconfig on Linux,
  and GraalVM CE AWT support on macOS is not available for the release target; its HarfBuzz
  also varies with the user's JDK build. Kept as an independent same-font reference in
  `TestHostText`: glyph-ID sequences for ar/ur/hi/bn/th match exactly.
- **stb_truetype / bitmap fonts only.** No shaping: Arabic joining, Indic reordering and
  conjuncts are impossible. Fusion Pixel and Unifont stay for simple scripts only.
- **Fusion Pixel for Cyrillic and accents.** Legible, but loses the bold label weight and the
  native look; authored glyphs on the native grid were preferred.
- **Pre-rasterizing all glyphs.** Glyphs upload on demand into eight 256x256 pages; an
  exhausted budget flushes, resets and continues (`glyphPagesResetWhen…` test).

## Evidence

- JVM shaping probe (temporary, outside the repo) rendered ar/ur/hi/bn/th at 9-14 px; 8-10 px
  were illegible for Bengali/Devanagari, 11-13 px legible. LWJGL HarfBuzz glyph IDs equalled
  `Font.layoutGlyphVector` for every sample.
- Native image (GraalVM CE 25.4.4.1.1 pinned by `tools/native-image/toolchain.py`, LWJGL
  `unsafe` core, this resource/reflection config, flattened `libfreetype.so` + `liblwjgl.so`,
  Linux x64): ar, ur, hi, bn, th, ja, zh-Hans, ko, ru and es shaped completely with the same
  glyph IDs as the JVM and non-zero FreeType coverage; Thai/Japanese wraps were identical to the
  JVM, so the Thai break dictionary is present.
- Offscreen surfaceless-EGL `TestHostTextRendering`: all 18 sample languages reach the
  framebuffer inside their measured boxes at compact and label sizes; no ink escapes.

## Limits

- Windows and macOS native packages were not built locally; they rely on the same LWJGL
  natives and configuration and need the release matrix (or a manual dispatch) to prove them.
- Urdu uses Naskh (Noto Sans Arabic UI), not Nastaliq: Nastaliq's vertical extent does not fit
  a 224-line screen. Native-speaker review of all joined scripts is still required.
- Complex-script glyphs are antialiased outlines at native pixel size beside 1-bit lettering.
- Text fields use shaped visual insertion edges and horizontal glyph clipping, as described
  below. IME composition is a separate platform capability.
- The i18n integration migrates settings rows, the master-title carousel, project/import/
  mutator menus and the legal body to measured width, fitting, wrapping and script-aware
  line pitch. Legal pagination and language-dependent caches refresh on language changes.

## Visual field-caret follow-up

The follow-up starts from integrated i18n commit `d2b0f09ee`. The existing owner stays:
`HostText` produces one complete shaped line and its visual insertion edges;
`ShapedLine` exposes grapheme-safe caret navigation with bidi affinity.
`MenuTextEditor` stores the logical insertion offset and edge affinity, and scrolls
a horizontal pixel viewport over that full line. The renderer clips glyph quads
and interpolates texture coordinates in projection pixels, independent of desktop
GL scissor state or framebuffer scaling. It does not re-shape a logical substring
when the viewport moves, which would change Arabic joining at the edges.

Left/Right, including controller caret buttons, follow visual order. Home/End
select the left/right visual field edges; Backspace/Delete continue deleting the
preceding/following logical grapheme. Numeric, address and path fields use an
explicit LTR paragraph base, while free text uses its first strong character.
Font language still selects script forms. Missing glyphs remain visible boxes
in the editable field, keeping UTF-16 insertion offsets tied to the original
value; the details screen retains codepoint escapes for inspection.

A separate reversed-string editor was rejected: it would duplicate bidi ownership
and break mixed runs and shaping. Prefix measurement and logical-substring
scrolling were rejected because they measure a different shaped context from
the displayed value. Platform IME composition, selection, mouse placement and
clipboard improvements are outside this bounded caret correction.

Caret geometry records full-precision shaped cluster extents. At a bidi run
boundary the caret retains its attachment to the preceding or following cluster;
a visual arrow step can change the insertion point at the same x. The ordered
walk visits every grapheme boundary and is reversible. Ligatures use GDEF caret
coordinates when the font supplies the expected number of interior boundaries;
otherwise they divide the cluster advance evenly. The optional
`hb_ot_layout_get_ligature_carets` symbol is called through the existing LWJGL
JNI dispatcher: LWJGL's complete `OpenType` initializer also requests symbols
absent from this FreeType library. A native without the optional symbol uses the
even-division fallback. The call signature was checked against LWJGL's generated
binding; Linux execution is covered by the caret tests. Windows/macOS execution
remains unproven locally.

The all-language editor check also exposed a fixed-height Hindi footer below the
224-line viewport. Tall-script editors now measure each vertical section and use
the existing compact font role for keypad and footer controls. Key rows reserve
the maximum actual ascent/descent in that row. ASCII layouts retain their
original positions and sizes. The real renderer checks text, keypad-focus and
numeric fields in all 20 locales for ink bounds and separation of hints, keys,
details and footer rows.

Validation is bounded to the changed host-text owner and direct UI consumers.
The change-based category plan selects 4,291 smoke classes through the coarse
shared-graphics rule; gameplay, physics, ROM decoding, audio and network behavior
are unchanged. Focused caret/font tests and exact offscreen crop comparisons
cover the shared changes; menu/editor/configuration/catalog/API consumers and
fresh affected structural guards cover integration. No release full-suite or
new Windows/macOS native qualification is claimed.

Observed checks for this follow-up (all Maven commands use
`python3 tools/testing/maven_queue.py --normal -Dmse=off`, Java 25, Lua 5.4,
and unset `DISPLAY`/`WAYLAND_DISPLAY`; render selections add
`-Dopenggf.headless.gl=surfaceless-egl`):

- `-Dtest=TestMenuTextEditor#arabicCaretStartsAtVisualEndAndRightArrowEditsThePreviousLogicalBoundary test -B`:
  initial witness failed, expected caret x=15 versus x=38.
- `-Dtest=TestShapedLineCarets,TestHostText,TestMenuTextEditor,TestMenuPixelFont,TestHostTextRendering,TestTranslatedMenuRendering test -B`:
  60 cases, one failure in the newly covered Hindi field footer, zero errors/skips.
  All caret/ligature, input and exact-crop checks passed. The initial build also
  caught an ambiguous Java `getLibrary()` import; the HarfBuzz owner is now explicit.
- `-Dtest=TestMenuTextEditor,TestTranslatedMenuRendering,TestEditorProjectMenu,TestStageplayMenuPresentation,TestMenuRenderAlignment,TestChildMenuFeedback,TestMenuDetailsScreen,TestMenuPathBrowser,TestUiLanguageSettings,TestLanguagePickerScreen,TestMenuInput,TestMenuRepeat,TestNameFont,TestEditorExportInput,TestBundledUiCatalogs,TestModApiSignatureSurface test -B`:
  after the measured footer correction, 98 passed, zero skips. Includes real
  text/keypad/numeric surfaces in every bundled locale.
- `-Pguards -Dtest=TestArchUnitRules,TestNativeImageResourceGuard,TestRawGlfwTestSetupGuard,TestUiCatalogUsageGuard,TestProductionSingletonClosureGuard,TestBuildToolingGuard#activeSourcesMustRejectRetiredSessionProtocol test -B`:
  85 passed, zero skips, in a fresh guard JVM.
- `-Dtest=TestMenuTextEditor,TestEditorProjectMenu,TestStageplayMenuPresentation,TestMenuRenderAlignment,TestChildMenuFeedback test -B`:
  after preserving the legacy diagnostic-font window for long ASCII paths,
  41 passed, zero skips. This delta does not change the shaped rendering path.

The three unrelated guard failures attributed during the original i18n delivery
were outside this follow-up's selection; this is not a claim that all repository
guards or smoke classes are green. All queued follow-up commands acquired
admission without a measured wait.

## IME completion and review gate

This continuation starts at `21bf62e55`. The earlier callback received committed
characters only. The pinned LWJGL 3.4.1 build also exposes experimental preedit
callbacks. Local binding and Linux-symbol inspection confirmed those entry
points. [LWJGL's release notes](https://github.com/LWJGL/lwjgl3/blob/3.4.1/doc/notes/full.md)
describe the experimental extension. Vanilla GLFW documentation does not describe
that extension.

Sol grounded the native fork and current callers. Opus independently compared a
composition field on `InputHandler` with a window-owned text session. Both chose
a session with owner identity, frame renewal, keyboard suppression and native
reset acknowledgment. The simpler field-only option would spread reset,
composition ownership and candidate positioning among consumers.

`Composition` copies UTF-32 callback data into immutable text, UTF-16 clauses,
focused clause and grapheme caret. `TextInputSession` belongs to one input
handler. `GlfwTextInputAdapter` owns the new native operations. It buffers ordered
key and character events until a poll completes, so later preedit callbacks can
claim an earlier key from the same native batch. Committed text keeps its single
existing queue. An empty-preedit acknowledgment ends stale-commit quarantine;
a reset request alone does not. An unclaimed composition cannot block gameplay.

`MenuTextEditor` keeps preedit separate from saved text and shapes the complete
display line once. Shared visual ranges place clause underlines, including bidi
and ligature ranges. The caret uses the composition's trailing affinity at its
end. Existing scene and course capture calls use their context identity. A host
strip presents their uncommitted text without adding a creator API. Existing
active editor chrome keeps its character routing; a host lease or pending reset
still receives the native acknowledgment it owns.

The [experimental fork's IME guide](https://github.com/LWJGL-CI/glfw/blob/4ba21109cb43553f458dd4b31b02abf6e57d7949/docs/input.md#ime-support)
requires application preedit drawing on Windows, macOS and Wayland. X11's default
over-the-spot style does not deliver preedit callbacks. The adapter enables X11
on-the-spot callbacks and leaves candidate drawing with the OS. That X11 mode is
experimental. The fork also has backend limits for resetting Wayland composition
and positioning X11 candidates. The session therefore retains stale-commit
quarantine until native acknowledgment. Headless captures do not qualify native
candidate windows or every OS IME.

A task-owned Xvfb, private D-Bus and IBus 1.5.34 setup exercised the production
adapter and `MenuTextEditor` on Linux. Packages were extracted into a private
prefix and checked against the local signed package database's SHA-256 values.
The first setup attempt used a denied mirror. A later short temporary path fixed
the Unix socket path limit. IBus simple-engine Unicode input required its
`IBUS_ENABLE_CTRL_SHIFT_U` flag; direct IBus probing identified that setup issue
before attributing it to the application.

The observed native preedit was `u`, `u4`, `u4f`, `u4f6`, `u4f60`. It committed
`你` once. Holding the committing Enter did not accept the editor. A fresh Enter
accepted it after release. The isolated display and bus process groups stopped
at probe exit. This verifies one real X11/IBus path. Windows, macOS, Wayland and
native candidate-window placement still require their own OS checks.

The implementation owner's final focused selection passed 125 tests with no
skips. Four surfaceless-EGL tests passed with no skips. Their native-size PNGs use
synthesized composition callbacks and cover Japanese clauses, Chinese scrolling,
Korean, combining and supplementary text, mixed Arabic and Latin, commit,
focused-clause negative control and the host strip. The source catalogs remain
machine drafts. Separate Opus and Sol linguistic reviews precede translation
corrections and integration; their agreement cannot certify native fluency or
legal equivalence.

The final input pass added a regression for starting native preedit after a
gamepad press. It reproduced stale gamepad hints before any character committed.
Owned, nonempty preedit now selects keyboard prompts immediately. The 128-case
selection passed 127 cases and exposed an editor source-wiring assertion that
still required the moved callback inside `Engine`; the assertion now follows
`GlfwTextInputAdapter`. This was a test ownership update, not a native callback
regression. The real isolated IBus sequence also passed after the prompt fix.

The corrected editor wiring check and the two EGL render classes then passed
all six cases with zero skips. The final change-based plan selects 4,293 smoke
classes and separate guards because the input adapter crosses shared consumers.
That combined run is scheduled after linguistic corrections, not repeated on
unchanged baseline and integration trees.

Independent final code review found one focus-ordering defect. A native release
queued before focus loss was discarded with presses and characters. GLFW had
already recorded the release, so it would not synthesize it again. The next
fresh Enter could stay suppressed. A focused regression reproduced that
failure. The adapter now retains queued releases while discarding other events
on focus loss; ordinary lost-focus input still cannot reach a refocused field.

After the focus-ordering correction, `TestTextInputSession`,
`TestKeyboardTextCapture` and `TestMenuInput` passed 37 cases with zero skips.
The reproducer had failed before the correction. Its first queued run waited
nine seconds; the corrected selection acquired admission without a measured wait.


The combined smoke run on `b0ea12e01` exposed an unwanted
`control -> graphics` edge from composition boundary validation. The pure-JDK
`Graphemes` helper now lives in `com.openggf.util`; input, editor fitting and
layout keep one segmentation implementation. The permitted utility edges are
`control -> util` and `editor -> util`, recorded with the original four i18n
presentation edges in the canonical architecture permission document. No input
path needs a graphics or native-font owner to validate its text.

The same run caught root method-size drift. The scene-finishing step now owns
graphics flush, host preedit and the scene shader phase, in that order. The
`Engine.init` budget drops from 164 to 159 lines after callback extraction;
`displayFrame` drops from 224 to 221 after extracting that step. Source
architecture checks pass without raising either budget. The first focused
guard rerun still used an incomplete permission-table edit; correcting both
utility edges made all 43 architecture/review cases pass. The other 143 cases
in that focused guard selection had already passed, including source budgets,
resource metadata, production closure, catalog use and Mod API signatures.
