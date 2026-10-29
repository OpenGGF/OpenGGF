# Contribute a translation

OpenGGF's menus and built-in editor use UTF-8 ARB catalogs with ICU MessageFormat.
You can improve any useful subset or add a language without changing Java. Original
game text and artwork remain ROM-owned; creator-authored content is separate.

## Add or improve a language

Edit an existing file in `src/main/resources/i18n/locales/`, preserving message IDs
and named arguments. English wording, context and argument examples are in
`src/main/resources/i18n/source/`. To start another language:

```bash
python3 tools/i18n/catalogs.py init sr-Latn --name "Srpski (latinica)"
python3 tools/i18n/catalogs.py check
python3 tools/i18n/catalogs.py status sr-Latn --details
```

This creates one file, using a BCP 47 language tag and an endonym. The build discovers
catalogs automatically: no Java enum, language registry or per-language native
allowlist needs editing. Empty, missing or stale messages fall back to current
English. Do not copy English to inflate coverage; identical entries are reported
separately. Proper names, code tokens, file extensions and shortcut keys can
legitimately retain their spelling.

Regional catalogs may declare existing same-language, same-script catalogs in
`@@parents`. English is the final fallback. Explicit scripts never silently cross
to another script. Partial contributions remain useful and selectable.

## Translate complete messages

Move named arguments to suit your grammar, preserving their names and numeric/text
types. Translate complete sentences, including plural/select branches:

```json
"menu.text.limit": "Limite : {count, plural, one {# caractère} other {# caractères}}"
```

Plural categories belong to the target language; Arabic can use zero/one/two/few/
many/other, while Japanese generally uses other. Every plural/select needs other.
Keep semantic select keys (for example character codes) stable and translate the
branch text. Use ICU apostrophe quoting for literal braces. The pattern
`'{videoIn}'` displays `{videoIn}`; an unquoted `{videoIn}` declares a required
runtime argument. Static code examples need the quoted form. See [ICU messages](https://unicode-org.github.io/icu/userguide/format_parse/messages/).

Keep terminology consistent: rings are collectibles, an act is a stage act, a donor
supplies another game's assets, rewind restores timeline history, and playtest runs
an authored level. Preserve trademarks and machine values. Prefer concise labels
without dropping meaning; layout measures, wraps, scrolls or pages text rather than
shrinking it below a script's readable size.

Short labels need their screen context. Add a source description when a word
has several meanings. These distinctions caused errors in the initial drafts.

| Label | Meaning |
| --- | --- |
| Stageplay key | Animation keyframe; resource and runtime keys remain identifiers. |
| Enter a block/chunk | Open the nested editor. |
| Actor position | Position of a cinematic cast entity. |
| Install disabled | Install the mod while leaving it disabled. |
| Search top/bottom | Y coordinates of the bounded route-search region. |
| Unbound | No keyboard input is assigned. |
| Down Keys | Down selects the on-screen letter/symbol keyboard. |
| yes/no in the crop form | Literal ASCII input values accepted by the form. |

Keep creator-supplied names recognizable in cross-references, including
`No Rings` and `Ringfall`. Their display names belong to the content owner.

## Check, refresh and review

Quick checks require Python 3, no ROM, engine build or translation account:

```bash
python3 -m unittest discover -s tools/i18n -p 'test_*.py'
python3 tools/i18n/catalogs.py check
```

These check JSON, IDs, metadata, fingerprints and fallback relationships. They do
**not** parse ICU. With the required Java 25 setup, run the actual production parser:

```bash
python3 tools/i18n/catalogs.py check --runtime
```

This compiles and invokes CatalogTool through the repository Maven queue. CI also
checks caller contracts, ICU grammar, locale plural rules and bundled glyph coverage.
A structural pass does not certify fluency or native-speaker approval. Preview real
screens at 320 by 224 and wider sizes, including long help, confirmation dialogs,
joined scripts and mixed-direction paths/numbers.

Source fingerprints include wording, meaning-bearing notes and argument contracts.
When English changes, check the new source and revise your translation before
explicitly refreshing its fingerprint:

```bash
python3 tools/i18n/catalogs.py stamp sr-Latn --ids menu.text.limit
python3 tools/i18n/catalogs.py review sr-Latn --reviewer "Your name" --ids menu.text.limit
```

Stamping clears old approval. Review records the named reviewer and exact source
and translation hashes; it must represent a review you actually performed. Editing
reviewed text invalidates approval. Machine origin is preserved. Critical legal
wording uses canonical English until the exact translation has been reviewed.
Current machine drafts remain usable with visible quality status in the picker.

## Fonts and initial coverage

The initial set combines the reported 2026 top ten total-speaker languages with
SEGA's Sonic language coverage, regional Spanish/Portuguese, and both Chinese
scripts. Initial translations are Haiku 5.5 drafts. Opus 5.5 and GPT-6.1 Sol each
reviewed the initial catalog inventory; their corrections remain machine drafts
and do not certify native fluency or legal equivalence.
See the [architecture and evidence](../../architecture/designs/2026-10-29-internationalisation.md).

Fonts are ordered in `src/main/resources/fonts/host-fonts.json`. A language using an
existing script needs no renderer changes. A new script may need a redistributable
font, licence, manifest entry and shaping/render checks: glyph presence alone is
insufficient for joined scripts. See the [font design and platform limits](../../architecture/designs/2026-10-29-host-text-shaping-and-fonts.md).
Text-field Left/Right navigation follows visual order, including mixed RTL text;
Home/End select the visual left/right edges. Backspace/Delete remove logical
grapheme clusters. Scrolling preserves the full shaped line. Text fields draw OS IME
preedit, focused clauses and a shaped caret without saving uncommitted text.
Scene and course text capture show a host composition strip. A real isolated
X11/IBus check verifies composition, one commit and release before Enter accepts
the field. Windows, macOS and Wayland IMEs remain unverified locally. The
experimental GLFW extension also limits Wayland reset and X11 candidate placement.
See the linked font design for platform evidence and limits.

Runtime translation is offline. No API key or paid translator is required to
contribute. Submit catalog and licensed font-resource changes through the normal
contribution workflow.
