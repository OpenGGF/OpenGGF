# Master-title preview provider migration

## Scope and decisions

The user requested implementation after reviewing the nine frozen
`MasterTitleRomPreview` mapping dependencies. Integration base:
`d7732f78e0818f1ebe6d2b81d8ca83c11e0f4c9c` on `develop`, fetched and
fast-forward checked before creating `bugfix/ai-title-preview-providers`.
Preserve the main checkout's reference submodules and unrelated untracked files.

Expose a detached CPU-only title-preview capability through `GameModule`.
Per-game providers own ROM offsets, mapping calls, animation tokens and composition.
Shared helpers own raster operations and token caching. The master title retains
catalogue resolution, source invalidation and GL texture lifetime. Resolve stock
capabilities by card identity, independently of the active/suspended module or
launch donor settings; consume the exact supplied ROM, including logical views.
Decode all ROM bytes before returning a sequence, because the file loader closes
the ROM before later animation frames are requested.

Mapping-only getters were rejected: inlined constants are invisible to the nine
bytecode records and would leave game-specific behavior in shared orchestration.
Reusing live title-screen managers was rejected: initialization/reset can change
global audio, palettes and fade state. Resolving the current module was rejected:
a suspended session stays published while the hub renders other stock cards.

The capability and its small image/sequence values join the unpublished 0.7 API
candidate. Explicit decorator forwarding and the normalized signature pin must
track the addition; the release descriptor remains candidate 0.7.0.

## Implementation and validation

- Record pre-extraction pixel/token fingerprints for verified S1 REV01,
  S2 REV01 and locked-on S3K, ticks -1 through 336 plus 10000, independently
  parameterized so a missing ROM cannot skip subsequent games.
- Extract three providers and generic CPU helpers; keep the shared loader free
  of concrete game imports and game-specific dispatch. Preserve existing focused
  raster/composition controls under their owning packages.
- Exercise explicit capability dispatch, unsupported/error handling, decorator
  forwarding, detached animation after ROM close, real reader windows/composites,
  token caching and unchanged animated output. Do not publish extracted ROM art.
- Review the candidate API delta, then prune exactly the nine resolved frozen
  identities with normal store updates still disabled. Update the migration queue.
- Inspect the change-based smoke plan and use proportionate focused menu, ROM,
  API and structural checks, plus the required four S3K loading/bootstrap/AIZ
  controls. No release-only full suite or duplicate unchanged broad runs.
- Record commands, failures/skips and coverage limits here; integrate into the
  main checkout's existing `develop`, push it, then remove only accounted work.

## Checkpoint

The baseline was characterized in the isolated task worktree before extraction.
ROM SHA-1 identities match repository requirements. Tool preflight passes with
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and `LUA_BIN=/usr/bin/lua5.4`; the initial
ambient-Lua probe failed before executing tests. The coordinator owns validation.

The first combined focused run compiled all consumers and ran 65 tests with one
failure and no skips. All three pixel/token fingerprints matched the original.
The failing isolation test caught full S3K module construction reading
`GameServices.configuration()` through the title and level-select managers.
The independent review also identified the module class's donor registration.
Replace that attempted module construction with direct detached-provider assembly
in `GameModuleRegistry`, following its existing stock menu-audio boundary. Keep
the module capability/decorator contract for module consumers. A ROM-free
resolution check now complements the ROM-backed isolation check.

The change-based dry plan selects 3626 smoke candidates before the final guard/API
documentation changes. This mechanical ownership move changes no gameplay
algorithm or GL upload behavior. Use the nine focused preview/menu/decorator
classes, the affected API/Javadoc/SDK/maintained-sample checks, the required S3K
loading/bootstrap/decoding/AIZ checks and three complete architecture guard
classes. This bounded replacement avoids unrelated gameplay smoke routes;
it is not a complete smoke-inventory pass. Expected execution is a few minutes
plus unmeasured queue delay. Stop on failures and resolve them narrowly.


## Verification evidence

Validation used the pinned base plus this task's uncommitted final source in
`.worktrees/ai-title-preview-providers`. The shared record file was empty for the
strict guard run; automatic store updates stayed disabled. The reviewed API
snapshot adds exactly 33 lines, all for the preview capability, nested image and
sequence, and the module/decorator accessors; it removes none. The release
policy remains unchanged at candidate 0.7.0. An attempted explanatory comment
in that descriptor was removed because its strict parser accepts only key=value
lines; the description belongs in the compatibility guide and `ModApiVersion`.

Every Maven invocation below used `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`,
`LUA_BIN=/usr/bin/lua5.4`, and unset `DISPLAY`/`WAYLAND_DISPLAY`. For ROM checks,
`ROM_ROOT` is the absolute main-checkout directory containing the existing,
verified user-supplied images; no ROM files were created or moved:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestMasterTitleRomPreview,TestMasterTitlePreviewInvalidation,TestMasterTitleSuspendedGame,TestTitlePreviewCapabilities,TestTitlePreviewPixels,TestDelegatingGameModuleCoversInterface,TestSonic1TitlePreviewProvider,TestSonic2TitlePreviewProvider,TestSonic3kTitlePreviewProvider' \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The initial 65-test run had exactly the S3K service-isolation failure described
above, with no errors/skips. After direct capability composition and the new
ROM-free isolation control, the same command narrowed to
`-Dtest=TestTitlePreviewPixels,TestTitlePreviewCapabilities` passed all 15 tests,
with no failures/errors/skips. The 51 other focused cases remained unchanged.
The initial baseline recording used `-Dtest=TestTitlePreviewPixels` against
unchanged production: three deliberately unrecorded digest assertions failed,
with no errors/skips, supplying the original fingerprints. These fingerprints
are compatibility evidence for existing preview output, not new ROM-parity claims.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiJavadocTool,TestModApiSdkPackager,TestSampleModsPackage,TestS3kAiz1SkipHeadless,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.tests.TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestModApiPinPolicy' test -B
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestArchUnitRules,TestArchitecturalReviewGuard,TestArchitecturalSourceGuard' test -B
```

The compatibility/S3K run executed 95 tests with no failures/skips and one
`TestModApiPinPolicy.actualPinFilesExactlyMatchDescriptorAndCandidateSurface`
error: the rejected descriptor comment. All other selected cases passed,
including the new preview Javadoc test and all nine maintained sample packages.
After restoring the descriptor, the narrow policy rerun passed all four tests,
with no failures/errors/skips. The final affected selection comprises 279
distinct tests across the corrected focused, compatibility and guard runs.
The three complete architecture classes passed **118 tests, no failures/errors/skips**.
No unrelated broad guard failure was claimed fixed. The full smoke inventory,
release-only suite and native emulator parity were not run. All Java production
and test sources compiled. `git diff --check`, changed-document relative links,
and the unchanged AGENTS/CLAUDE mirror passed. Diagnostic output is temporary;
no category run directory or validation receipt was created.
