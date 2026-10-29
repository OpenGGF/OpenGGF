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
