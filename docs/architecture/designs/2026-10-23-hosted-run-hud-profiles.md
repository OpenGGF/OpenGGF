# Hosted-run native HUD profiles

Base: `67eb665fe` (packed native hundredths). The Time Attack GUI thread owns
layout and adoption; this change supplies engine capability only.

## Contract and decisions

- `RunHandle.setHudProfile(profile)` holds an immutable presentation override for
  one run. It is reapplied after level-ready callbacks and during ordinary hosted
  drawing/stepping, and cleared before the run-end callback. Null reveals the
  current zone profile, rather than assuming the stock layout. Ended handles
  cannot change a later run. The run policy still suppresses lives.
- Store the override separately in the renderer: installing a destination profile
  must not overwrite it, and restoring must reveal the latest destination profile.
  Debug and bonus-stage layouts keep their existing special handling.
- `LevelOverlayCanvas.hudOriginX()` uses the canvas width and the installed HUD's
  anchor. No state is mutated by measuring. A missing HUD uses a centered native
  frame; bonus-stage HUDs use origin zero.
- TIME readout geometry shares the separator advance with drawing. Whole seconds
  occupy 32 px; hundredths occupy five 8-pixel digits plus two measured separators
  (52 px with the current three-pixel ink, 56 px before art is measured).
- A complete alphabet, label-only hiding and custom glyph assets were considered
  but deferred: the actual consumer keeps native TIME/RINGS and can draw BEST/SPLIT
  with its existing compact font. No ROM-only asset policy exception is needed.

## Consumer and validation scope

Time Attack will hide SCORE/LIVES and move TIME/RINGS to y=8/24, then align its
compact overlay with the native origin. This task does not edit that GUI.

The change-based plan selects all 3,615 smoke candidates because the API and HUD
bridges are shared/unclassified. Focused HUD rendering, hosted-run lifecycle,
creator UI, destination resolution, presentation-mutator and API-policy checks plus
structural guards replace that broad selection for this bounded presentation change.
No simulation algorithms, clocks, fonts or selection policy change. The release
policy descriptor was reviewed: API 0.7.0 remains an unpublished candidate, so its
values stay unchanged; the candidate signature pin and ModApiVersion documentation
carry these additive methods. Preflight uses
`LUA_BIN=/usr/bin/lua5.4`; Java 25 and tool prerequisites passed. This is not a
full-suite or exhaustive trace claim.

Focused verification (development tree based on `67eb665fe`):

```sh
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestHudRenderManager,TestHostedRunController,TestCreatorUi,TestHudRenderManagerWidescreen,TestModHudProfileResolution,TestHudPresentationRuntime,TestRunPolicyLivesCounter,TestHudViewportWiring,TestModApiSignatureSurface,TestModApiReleasePolicy,TestModApiPinPolicy,TestGameplayRunPolicy,TestHostedRunLeadIn,TestHostedRunActCompletionWiring' test -B
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestHostedRunController,TestModApiPinPolicy,TestModApiReleasePolicy' test -B
```

The first run completed 158 tests with no skips: 156 passed and two policy checks
rejected the temporary descriptor comment. After removing it and extending the
lifecycle regression to assert lives suppression, the affected 45 tests passed
with no skips. All selected identities passed across these runs. The local default
ROM paths resolved without skips; their S1/S2/S3K hashes match the repository's
REV01/locked-on identities. No exhaustive ROM trace sweep was run.

Structural validation also passed:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

722 tests passed with no failures, errors or skips (6:21 execution, 9:04 queue wait).
No Java source changed after the successful focused checks. Documentation-only
result recording does not require repeating the unchanged checks.
