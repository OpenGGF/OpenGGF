# Host interface internationalisation

## Problem

Contributors should be able to add or improve any language without changing Java.
The initial set combines the ten languages with the most total speakers with
languages serving Sonic audiences. This concerns OpenGGF's own menus, settings,
dialogs and built-in editor; original ROM text, game artwork, creator content,
machine identifiers and diagnostic protocols retain their existing owners.

Grounding at `b54676a1864d0c7ad8cd12570eead7e4073c68c3` found an existing
presentation/persistence split in `EngineSettingLabels` and `ConfigCatalog`, but
ASCII-only `MenuPixelFont`, fixed-cell layout and English label-based command
selection. `NameFont` has Unicode bitmap coverage but explicitly lacks shaping
and bidirectional ordering. Translation catalogs alone cannot satisfy this task.

## Usage: caller's view

Contributors initialise a UTF-8 ARB catalog, translate useful messages, run the
catalog checker and submit the resource diff. Java and language registries are
not edited. Partial translations are useful: missing or stale entries fall back
to current English and their coverage remains visible.

```java
MenuChrome.header(font, width, UiMessages.text("settings.screen.keep_display_settings_caps"), null);
String countdown = UiMessages.text("settings.display.revert_countdown",
        Map.of("seconds", trial.secondsRemaining()));
String label = UiMessages.text("editor.editor_command_palette.playtest_current_edits_1ci1094");
// Dispatch uses the command's stable action identity, never this label.
```

Existing widgets accept resolved `String` values. The formatter retains the
actual selected message's locale internally, so English fallback uses English
grammar. Unicode layout consumes complete resolved strings, explicit UI locale
and Unicode paragraph direction; it never parses message patterns. Capturing
`UiMessages.current()` provides an immutable view for a presentation transaction.

## Shape

- Source ARB fragments under `src/main/resources/i18n/source/` together define one
  English message inventory. Each ID has exactly one owner and whole-message
  wording; named ICU arguments carry numeric/text contracts and translator notes.
- Target ARB catalogs under `i18n/locales/` contain their canonical BCP 47 tag,
  endonym, quality provenance and translations. Build-time discovery generates
  the resource index. A new locale needs no per-language Java branch or native
  resource allowlist change.
- Internal `com.openggf.i18n` owns strict parsing, contracts, source fingerprints,
  locale negotiation, explicit per-message fallback and ICU formatting. No ARB,
  Jackson or ICU implementation objects escape to screens. Formatter instances
  are private and protected against concurrent mutation.
- `UiMessages` owns active presentation language, immutable views and a revision.
  Successful settings persistence publishes a language change on the UI thread.
  Failed saves and cancelled drafts leave the active language unchanged. Cached
  labels, wrapping and hitboxes include the revision. `Locale.setDefault` is not
  used; machine normalisation remains `Locale.ROOT`.
- The typography owner supplies shaping, bidi, Unicode boundaries, measured
  fitting/wrapping and drawing through the same geometry. Font fallback is data,
  not a language-name switch. ROM/native and public creator font contracts stay
  compatible. English pixel appearance is retained where practical.

The public host-internal surface remains small:

```java
final class UiMessages {
    static LocaleView current();
    static String text(String id);
    static String text(String id, Map<String, ?> arguments);
    static long revision();
    static Locale locale();
    static void selectLanguage(String preference); // auto or BCP 47
    static List<UiLanguage> languages();
}
// API sketch; LocaleView is an immutable final class.
interface LocaleView {
    String text(String id);
    String text(String id, Map<String, ?> arguments);
    Locale locale();
    long revision();
}
```

This interface hides catalog shape, parser lifecycle, grammar, missing/stale
policy and language discovery. Callers know only stable message IDs, arguments
and widget overflow policy. Strict runtime checks and source-use validation
carry the argument-safety cost accepted instead of generated Java methods.

## Synthesis decision

Two GPT-6.1 design runners independently explored runtime catalogs and compiled
typed message implementations. Runtime catalogs are the base: they minimise
build/API churn and fit existing string widgets. Grafts from the compiled design
are stable action identities, generated discovery, actual-locale fallback,
source fingerprints and native packaging checks. Per-message generated Java
functions are rejected because their compiler/bootstrap contract adds more
maintenance than the current UI needs. The Opus 5.5 cross-family comparison
also selected runtime catalogs. Its required grafts are a source-use guard and
single-file locale/review metadata; its String-adapter conditions are formatter
argument isolation, Unicode-safe fitting, per-frame locale consistency and test
teardown restoration. Opus 5.5
owns font implementation at the user's request; fast mode is disabled.

The String compatibility boundary is deliberate. Rich text span APIs are not
being added to the public Mod API. Directional isolation and layout policy
belong behind the shared presentation boundary, not at individual call sites.

## Coverage policy and evidence

The [reported Ethnologue 2026 total-speaker ranking](https://www.visualcapitalist.com/ranked-the-worlds-most-spoken-languages-in-2026/)
gives English, Mandarin, Hindi, Spanish, Standard Arabic, French, Bengali,
Portuguese, Indonesian and Urdu. This is a secondary reproduction; the primary
Ethnologue page returned HTTP 403 during research. Speaker totals are L1 plus
L2, not native speakers only.

No reliable worldwide top-ten Sonic-player country ranking was found.
[Country film grosses](https://www.boxofficemojo.com/title/tt18259086.htm/) are an
audience proxy, combine US/Canada in their domestic figure, and are not gamer
counts. [Google Trends](https://support.google.com/trends/answer/4355212) measures
normalised search interest rather than audience size. We therefore use
[SEGA's actual Sonic text-language footprint](https://sega.co.jp/en/release/261026_1.html)
as the additional practical coverage set, without presenting an invented rank.

The initial locale inventory is `en`, `zh-Hans`, `zh-Hant`, `hi`, `es`,
`es-419`, `ar`, `fr`, `bn`, `pt-BR`, `pt-PT`, `id`, `ur`, `ja`, `it`, `de`, `ru`,
`pl`, `ko`, `th`. Regional variants are independent content, not script or flag
substitutions. Same-script negotiation can find a supported regional locale;
explicit scripts never silently cross to another script. Message fallback
follows declared acyclic parents and finally English, using the message owner's
locale for plurals and number formatting.

## Contributor and translation quality contract

The canonical source describes context, parameters and whole sentences. Target
messages may reorder parameters and add their language's plural branches.
Duplicate JSON keys, unknown IDs, malformed ICU, argument/type mismatches and
missing `other` branches fail validation. No regular-expression formatter or
English-only plural branch is implemented. Source changes make prior
translations stale; editing a translation invalidates its review stamp.

Structurally valid current machine drafts are usable, with honest provenance
and review status. They are not described as native-speaker reviewed. Native
contributors can improve a subset without completing the language. Legal text
retains accessible canonical English; automated drafting cannot certify legal
equivalence. No runtime network translation or client credentials are needed.

[Haiku 5.5](https://www.anthropic.com/claude/haiku) supplied the initial machine
drafts, not native-speaker quality certification. Review representative short
actions, ambiguous Sonic/editor terms, plural/select messages and mixed RTL
paths/numbers. Lock placeholders, provide a glossary and screen context, validate
every response, and preserve reviewed entries. Model agreement or backtranslation
does not substitute for native-speaker review. API requests, if used, are local
authoring operations and their provenance is recorded.

## Tradeoffs accepted

- Runtime argument validation and usage checks replace generated typed methods.
- Fonts, ICU data and Unicode layout increase package size in exchange for
  reproducible scripts independent of OS-installed fonts.
- Script-safe line heights can reduce visible rows. Paging/scrolling preserve
  complete legal/help content at 320 by 224; text is not shrunk to hide overflow.
- Machine-assisted catalogs accelerate initial coverage but retain explicit
  review limitations. Partial and stale states are not counted as complete.

## Alternatives considered

Compiled ARB-to-Java functions provide compile-time parameter safety and static
reachability, but introduce a custom message compiler and generated API churn.
Fluent offers strong translator-led grammar, but its JVM/native implementation
would need an additional qualification. JDK `ResourceBundle` and `MessageFormat`
alone leave named plural/select grammar and CLDR differences in callers.

## Implementation order and acceptance

1. Prove fonts and layout on representative joined scripts, mixed directions and
   real native-scale output. JVM/native packaging are separate evidence.
2. Implement source/target validation, immutable locale views, generated discovery
   and contributor commands. Demonstrate adding a locale with resource files only.
3. Replace label-based command dispatch, then migrate all host menu/editor text
   and dynamic messages. Preserve machine tokens and creator/ROM boundaries.
4. Add saved `ui.language`, ROM-free startup selection, live cache invalidation,
   failure/retry/cancel and paused-game behavior. Keep full text reachable.
5. Seed the combined locale set, validate provenance/coverage and actual shaped
   screen bounds. Update guides/config/API pins and package resources.
6. Run affected focused checks and the change-based smoke selection once on the
   combined candidate. Attribute disputed failures with matched baseline tests;
   integrate into the unchanged main branch, push and clean accounted worktrees.

## Open risks

Native text-library/resource support and legibility at the smallest viewport
require observed proof, not catalog presence. CJK menu translation does not
imply OS IME composition support. Country coverage is a documented proxy policy,
not a measured top-ten player ranking. The delivery record must distinguish
implemented behavior, executed checks, draft quality and untested platforms.


## Implemented delivery evidence

The source currently contains 2,444 messages. Eighteen full target catalogs carry
current source fingerprints and explicit machine provenance. `es-419` is a
44-message overlay on Spanish. English-identical tokens do not count toward
translated coverage. The critical legal body remains canonical English until an
exact translation receives a matching review stamp.

The contributor CLI's Python safety tests and `check --runtime` pass: the latter
loads the actual production ICU parser rather than relying on the quick parser.
The Java caller guard checks literal IDs, named argument contracts and declared
dynamic adapters. The complete resolved catalog set formats and shapes without
missing glyphs in the bundled-font check. These are structural and rendering
checks, not linguistic review.

A Linux x64 probe compiled the real catalog and font owners with the pinned
GraalVM CE 25.4.4.1.1 toolchain and this repository's resource/reflection metadata.
All 20 locales formatted a plural message and shaped completely on the JVM and
in the native executable; mixed-direction punctuation and critical English
fallback also passed. The first native run exposed ICU's reflective
`NumberFormatServiceShim`; its constructor registration is now explicit. This
probe is narrower than a full engine native-image build. Windows and macOS
native packages have not been built locally.


Stageplay presentation has explicit form/menu kinds and producer-owned compact
action labels/primary metadata. These restore titles, help and physical placement
pickers without inspecting translated titles or callback labels. Duplicate-label
and French/Arabic title regressions exercise the real picker and independent
actions. The existing public host `Action`/`Field` records retain their shape.

At the native viewport, measured settings/reset/save dialogs, the selected hub,
game setup (including save/discard prompts) and language recovery are rendered in
all 20 languages by `TestTranslatedMenuRendering`. Intentionally clipped carousel
neighbours are excluded from that bounds assertion. Long focused hub controls
page through measured text; long confirmation choices stack with matching pointer
rectangles. This is bounded screen evidence, not a linguistic review.


The measured dependency ratchet admits the four host-presentation edges
`editor -> i18n`, `game -> i18n`, `testmode -> i18n` and `i18n -> graphics`.
The last edge activates only the shared font language context on the UI thread;
it does not acquire gameplay services. The Stageplay presenter consumes only the
canonical `PuppetCommandKind` enum to choose form intent, with negative guard
witnesses excluding the controller and playback owner. It remains an editor
adapter, not a runtime playback dependency.

A read-only export inspection of the pinned LWJGL FreeType native artifacts found
all 22 functions used by the shared shaper on Windows x64/x86/ARM64, macOS
x64/ARM64 and Linux ARM32/ARM64. Mach-O exports were read from the export trie;
this inspection is not a native execution or engine-package qualification.
