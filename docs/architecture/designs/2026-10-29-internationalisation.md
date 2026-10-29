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


## Independent model review and corrections, 2026-10-29

The user requested separate Opus 5.5 and Sol translation inputs, resolution of
actionable issues, screenshots and integration. Each model read the same fixed
44,036 authored English/target pairs against `21bf62e55`, covering all 379 chunks
in 19 non-English catalogs. There are 18 complete 2,444-message catalogs and a
44-message es-419 overlay. Chunk hashes, coverage, exact old strings, identities
and replacement uniqueness were checked before application. The reviewers did
not read each other's findings. Neither review is native-speaker approval.

Opus 5.5 returned 1,907 suggestions, including 966 errors and 941 clarifications.
Its main inputs distinguish animation keyframes from keyboard keys, original-ROM
behavior from local ownership, actors from playable characters, literal form
tokens and multiplexing from audio mixing. GPT-6.1 Sol returned 905 suggestions,
including 836 errors and 69 clarifications. Its inputs include reversed size
limits, dependency/dependent direction, held/released controls, source-game
resources, installation state and unassigned bindings. Both identify recurring
source ambiguities rather than treating every English word as one glossary term.

The reports share 471 message identities. They propose different replacements
for 309. Bounded reconciliation resolves each difference by source meaning,
consumer behavior and glossary consistency. Its 325 explicit decisions include
125 Opus choices, 146 Sol choices, 36 merged corrections and 18 legal dismissals;
two legal entries were also conflicts. Merges preserve independent corrections
in the same sentence. The lead additionally checked actual creator titles and
kept No Rings and Ringfall recognizable in their interaction notice. This adds
one Arabic correction and refines twelve other accepted replacements.

The resulting edit accepts 2,324 linguistic corrections. Escaping literal FFmpeg
examples changes another 32 target messages, for 2,356 changed target values.
The two English help patterns also change their ICU representation. Twenty
source descriptions now explain ambiguous terms; their 360 authored target
source hashes are deliberately refreshed without approving any translation.
The English message count remains 2,444 and the language set remains 20.

| Locale | Opus findings | Sol findings | Changed target values |
| --- | ---: | ---: | ---: |
| ar | 134 | 27 | 150 |
| bn | 111 | 46 | 135 |
| de | 77 | 40 | 100 |
| es-419 | 1 | 1 | 2 |
| es | 91 | 39 | 110 |
| fr | 87 | 51 | 112 |
| hi | 141 | 77 | 169 |
| id | 87 | 48 | 118 |
| it | 81 | 40 | 104 |
| ja | 74 | 25 | 88 |
| ko | 82 | 35 | 100 |
| pl | 94 | 59 | 120 |
| pt-BR | 71 | 54 | 103 |
| pt-PT | 57 | 58 | 90 |
| ru | 126 | 56 | 155 |
| th | 189 | 35 | 201 |
| ur | 187 | 102 | 228 |
| zh-Hans | 110 | 53 | 135 |
| zh-Hant | 107 | 59 | 136 |

Eighteen Sol suggestions would replace unused legal drafts with English; Opus
also suggests Arabic and Polish legal wording changes. They are dismissed for
this delivery. `CatalogRepository` already requires current source and exact
translation review hashes plus `reviewed:true` before a critical translation
can display. Every authored target entry remains `reviewed:false`, with its
original Haiku provenance and machine quality. The runtime therefore keeps the
canonical English legal text. Preserving the unused drafts leaves material for
an exact future human review. A runtime regression covers draft rejection,
valid reviewed text, edited translation rejection and stale-source rejection.

A consumer probe found a separate runtime defect in both FFmpeg help fields.
The settings reader supplies no arguments, while token examples such as
`{videoIn}` were parsed as required message arguments. The actual production
reader threw for both persisted settings. ICU quoting now makes all thirteen
token examples literal in English and every authored target. A focused
regression calls both help readers in every locale and checks that token names
and braces remain visible. The native-viewport render selection also exercises
those two recording settings in every locale.

The complete 2,324 proposed linguistic replacements passed the production ICU
parser, argument/select contracts, formatting samples at 0/1/2/5/11/21/100 and
compact glyph completeness before application. Final checks load the changed
source and targets together, including the corrected literal-token contract.
Model suggestions and structural checks do not establish idiom, regional tone,
dynamic grammatical agreement or legal equivalence. Arabic, Bengali, Hindi,
Thai and Urdu particularly need native assessment; Korean, Japanese and the
regional Chinese, Portuguese and Spanish catalogs also retain editorial limits.
Windows/macOS packaging and every OS IME remain separate platform obligations
described in the font design.


## Delivery validation

The change-based command used base `21bf62e55` with two ordinary workers,
surfaceless EGL, verified original ROM discovery from the main workspace, all
4,293 smoke candidate classes and separate guards. It completed all 87 ordinary
batches on IME commit `b0ea12e01`. Ordinary results were 35,497 cases, one error
and 49 skips. Guards ran 811 cases with six failures and no skips. This was
bounded smoke coverage, not the release-only full suite. Preparation took
32.65 seconds; ordinary execution took 1,646.18 seconds and guards 161.13
seconds. Shared admission added queue waits, including one 696-second wait.
These execution durations do not describe total delivery wall time.

The gameplay error reproduces on base `21bf62e55` with the identical class
selection and environment. Three guard failures also reproduce unchanged on
that base. They remain outside this host-text delivery.

| Existing failure | Baseline assertion |
| --- | --- |
| `TestObjectSolidContactController.realMultipartLandingsPublishIndependentChildStandingBits` | `GameServices.gameState()` requires an active gameplay mode at `landOnPiece:224`. |
| `TestRewindCoverageGuard.noNewCoverageGapsBeyondBaseline` | `MhzEndBossInstance#objectRef#hitProxy` lacks rewind coverage. |
| `TestBuildToolingGuard.s3kStaticSessionStateDebtShouldNotGrow` | HCZ `slotRetired` and AIZ2 `cutsceneKnucklesSlot` remain unrecorded static-session debt. |
| `TestNoAssertionFreeDiagnostics.noAssertionFreeTestMethodsUnderTestsTree` | `TestTestReelExtension#bodyPasses` has no direct assertion oracle. |

The matched baseline gameplay class ran eight cases with one identical error
and no skips. The selected baseline guard check ran 39 cases with four
failures and no skips; its additional failure was a stale architecture
permission count introduced in the earlier i18n delivery. This delivery fixes
that count along with both new IME architecture failures. The canonical
permission document now matches the executable set, and the pure-JDK grapheme
utility removes the input-to-graphics dependency. Root method budgets decrease
after extracting the scene-finishing step.

All 49 smoke skips were inspected. They include opt-in captures, benchmarks,
soaks, native-driver/creator-fixture probes and a CPZ spin-tube capture/release
assumption. No skipped local UI check is represented as a renderer pass.
The reviewed-catalog focused selection passed 76 cases with zero skips and
wrote 240 actual 320×224 EGL screen captures, including both FFmpeg help fields
in every locale. Twelve IME specimens use synthesized composition callbacks;
they do not qualify an OS candidate popup. The real isolated X11/IBus witness
is described in the font design.

After the Unicode utility relocation, the final normal-memory focused selection
passed 93 cases with zero failures, errors or skips. It includes input sessions,
shaped caret navigation, editor HUD layout, text fields, both FFmpeg help
readers, ICU catalogs and actual EGL renders. All 240 screen pixel buffers are
identical to the captures before this package-only relocation. A constrained
1 GiB invocation had failed during test recompilation; it is not counted as a
pass. The successful rerun uses the normal memory lane. The real owned
X11/IBus composition and held/fresh Enter sequence also passed after relocation.

Focused final selection, with `JAVA_HOME` set to Java 25 and `LUA_BIN` to Lua
5.4. Set `I18N_SPECIMENS_DIR` and `IME_SPECIMENS_DIR` to the desired output
directories before running:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  OPENGGF_I18N_UI_SPECIMENS="$I18N_SPECIMENS_DIR" \
  OPENGGF_IME_UI_SPECIMENS="$IME_SPECIMENS_DIR" \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestUiLanguageSettings,TestMessageCatalogs,TestBundledUiCatalogs,TestTranslatedMenuRendering,TestMenuTextEditorCompositionRendering,TestHostTextRendering,TestMenuTextEditor,TestHostText,TestShapedLineCarets,TestTextInputSession,TestEditorHudLayout' test -B
```

The affected guard selection passed 143 cases outside the two initially failing
architecture classes; the corrected architecture/review classes then passed
43 cases in a fresh guard JVM. No unchanged broad run is repeated after these
bounded fixes. The combined broad run's failures and skips remain reported
above; this is not a claim that all repository tests pass.

The isolated gameplay class on the final development tree also matches the
base exactly: eight cases, the same single error and no skips. The failure
identity, exception type and assertion text match. Consumed category diagnostics
were acknowledged and deleted after inspecting every failure and skip.


### Complete menu text

Settings grows from 320 to the 400-pixel 16:9 preset and centers that content
frame in wider viewports. Rendering, pointer hit-testing, nested dialogs and
native text-input caret rectangles use the same frame. Settings keeps the
caption groups, dependent-row indentation and light row face; measured label
and value columns wrap to two rows, then page focused overflow. Details keeps
the complete current value, default and stored form.

Other built-in menus use measured wrapping, paging or an advertised complete
reader. F1/controller Details opens host readers; editor modal sheets use
F1/R3 while their existing canvas shortcuts keep their roles. Time Attack uses
F1/R3 for complete read-only views, retaining its normal Room details/history
page. Readers consume navigation and confirmation so reading cannot apply a
setting, join a room or launch content. They show the retained chat history,
not messages the session has already discarded, and never disclose credentials.

New pages must provide a complete route for every preview. Prefer concise copy
and measured wrapping, then scrolling or paging; use an ellipsis only when the
full value remains discoverable. Fitting diagnostics remain enabled for such
previews. Account for active-script ink and line pitch, not only ASCII cells.
The review covers engine-owned menus and the bundled companion UI; arbitrary
creator-authored custom scenes and original-ROM lettering keep their own owners.
