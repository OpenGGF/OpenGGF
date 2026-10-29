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
- Text-field carets are logical; right-to-left field text is not visually mirrored, and IME
  composition is a separate platform capability.
- Screens that still measure with `String.length()` or `COMPACT_ADVANCE` (settings rows,
  master title carousel, project/import/mutator menus, legal body pitch) must move to
  `MenuChrome.compactWidth`, `HostText.fit/wrap` and `lineHeight` as their labels migrate.
