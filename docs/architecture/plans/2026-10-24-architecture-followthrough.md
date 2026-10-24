# Architecture guard and ownership follow-through

## Scope and fixed contracts

The user approved the preceding architect review's recommendations. The
integration base is `0b2c3c2a9127b080137b231ba7e7cfd9e3b1e164` on `develop`,
fetched and fast-forward checked before implementation. The main checkout's
dirty reference submodules and unrelated untracked files are outside this task.
The coordinator works in `bugfix/ai-architecture-followthrough-20261024`.

Deliver the demonstrated guard repairs, move title-card rendering into its
domain owner, audit the remaining rewind dispositions and static-state
classifications, and establish the background renderer's lifetime and API
constraints. The last item is an investigation, not a commitment to remove five
frozen records. Broader RenderContext, foundational value-type and audio-tool
reorganizations remain later work. No blanket lifecycle substitution is allowed.

The preceding independent GPT-6-Astra and GPT-6.1-Sol candidates compared
horizontal guard/layer normalization with complete bounded ownership migrations.
Both selected ownership migrations. The cross-review preferred Sol's explicit
public signature and emitted-submission checks, and placed demonstrated stale
permission repairs first. The two configured models belong to one model family.

## Ownership and caller-first design

- Guard worker owns event-source permission enforcement and its controls, plus
  bounded hardening of object-service bridge matching where existing scan data
  supports it. Remove the three solved AIZ/HCZ whole-file exemptions; pin MGZ's
  remaining accesses to their actual callers and counts. Missing or unreadable
  source must fail. Permanent composition bridges remain explicit.
- Title-card worker owns `TitleCardSpriteRenderer`, its three manager imports,
  the exact resolved frozen records, and the exception guide's related accounting
  and stale detailed descriptions. S1/S2/S3K managers keep calling the same
  `public static renderSpritePiece(GraphicsManager, TitleCardMappings.SpritePiece,
  int, int, int, int, int)` implementation, now under `game.titlecard`.
  Delete the old location without a forwarding facade. Preserve VRAM translation,
  clamping, bounds, flip/palette bits and submission order. Canonical
  `graphics.SpritePiece` expansion has different iteration order, so geometry
  unification is deliberately excluded. Fourteen resolved records and 181 to 167
  remaining low-level records are expectations until verified.
- Rewind worker owns the complete 23-field audit, safe evidence-backed central
  dispositions, static-state coverage recognition and focused tests. Its audit
  records every field's capture/reconstruction owner and executed graph evidence
  or exact unresolved obligation. A graph declaration does not execute its named
  test. Provider-owned special-stage adapters require actual initialized
  registration, restore and lifetime evidence. The module-selection exemption
  retains its session-boundary justification.
- Coordinator owns background-renderer investigation, shared release prose,
  this plan, composition/review, combined validation, integration/push and cleanup.

Reject changes that introduce duplicate rendering values, type erasure,
compatibility wrappers solely to hide dependencies, new global state, or
unexplained replacement allowances. `ObjectLifetimeOps` and raw
`setDestroyed(true)` have different respawn semantics. Counts are not a mandate
to replace them.

## Validation and delivery

Workers run only affected focused checks through the Maven queue, with separate
worktree build trees. The coordinator inspects the change-selected category plan
against the fixed integration base and owns one combined selection. Use Java 25,
Lua 5.4, absolute existing ROM paths, and explicit surfaceless EGL with desktop
display variables unset for graphics. Inspect actual tests and skips. Structural
guards use a separate fresh JVM. Include the four required S3K stability classes
where applicable. Routine work never invokes the release-only full suite.

Use focused behavior and guard controls when the final impact remains bounded;
state and justify any replacement of broad category selection. If shared runtime
behavior grows beyond that boundary, use the appropriate change-selected smoke
coverage once. Attribute disputed failures with matched narrow baseline/current
checks, not duplicate broad suites. Record exact outcomes and limits here.

Integrate into the main workspace's existing `develop` without switching it.
Push only that branch after applicable checks and policy validation. Remove only
the task's fully accounted worktrees/branches after successful delivery. Preserve
unknown or unmerged work. Consume and acknowledge category diagnostics; retain
no pass cache, receipts or copied raw logs.

## Background-renderer investigation

The five frozen records describe a real reverse dependency, but do not establish
that the GPU allocation belongs to a gameplay-mode instance. Current ownership
has separate boundaries:

- `EngineContext` holds the process graphics owner. The public
  `GraphicsManager.getBackgroundRenderer()` returns null without an initialized
  native context or in logic-only headless mode. Otherwise it lazily constructs
  and initializes a `BackgroundRenderer` and returns that same instance.
- `LevelRenderer` and the HCZ high-priority background renderer consume that
  instance. `LevelManager.initBackgroundRenderer()` reserves capacity through a
  render command; display-width changes resize column buffers. Per-level inputs
  do not imply per-mode ownership of the native allocation.
- `AbstractLevelInitProfile.levelTeardownSteps()` explicitly resets graphics.
  `GraphicsManager.resetState()` releases the background instance and its native
  resources while retaining context availability. The next accessor creates a
  fresh renderer. Full `cleanup()` also marks the context unavailable.
- `GameplayModeContext.tearDownManagers()` resets gameplay managers but does not
  reset graphics. `SessionManager` clears runtime-managed references on mode
  retirement. `LevelManager` deliberately supports editor rendering after
  gameplay teardown. Moving background disposal into ordinary gameplay teardown
  would change that lifetime.

These conclusions come from the current method bodies in `GraphicsManager`,
`AbstractLevelInitProfile`, `GameplayModeContext`, `SessionManager`,
`LevelManager`, `LevelRenderer`, and `HczBgHighPriorityTileRenderer`.
`TestBackgroundRendererOwnership` characterizes no-context/headless behavior,
stable lazy identity, actual native texture release on level reset, recreation,
and reuse of one graphics owner across two separate native contexts. It uses
component EGL contexts and the production background getter; it does not claim
to exercise a complete editor or suspended-session route. Its execution result
belongs in the validation section below.

The candidate API pin includes the getter's concrete return type and both
`BackgroundRenderer` constructors. Moving allocation alone cannot remove the
getter's return-type dependency. Moving the public renderer type itself changes
the candidate contract. The candidate is unpublished and can evolve through the
normal descriptor/version/signature process; its pin is not an immutable public
release promise. This task deliberately preserves that contract.

The selected outcome is to retain the graphics/context resource owner and all
five allowances in this wave. A later API migration can place the GPU-only
implementation and renderer-facing values in `graphics`, with level composition
supplying scroll, columns, shimmer and priority inputs. That requires a complete
caller and candidate-API migration, including `BackgroundColumnRemap` inputs.
Reject a level-owned wrapper that merely forwards the pinned getter or causes
gameplay teardown to retire resources needed by editor rendering. Reject an
`Object`-typed accessor or duplicate GPU renderer solely to hide the five edges.
The investigation therefore closes with a specific lifetime decision and tests,
not a promise of immediate frozen-count reduction.

## Checkpoint

- Updated integration base is unchanged at `0b2c3c2a9`.
- Guard hygiene is composed at `e69c2abb1672df51341fd68b0c0b40c4bbe57b24`.
  Its four test-source files passed 37 focused tests with zero failures, errors
  or skips, using Java 25 and:

  ```bash
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
    '-Dtest=TestZoneEventRuntimeAccessGuard,TestObjectServicesMigrationGuard,TestRuntimeAccessSourceScanner,TestObjectGuardSourceScanner' test -B
  ```

  The worker inspected XML identities and skips. Independent read-only review
  found no blockers. Lexical caller identities deliberately group lambdas into
  their enclosing member; syntax matching does not claim type attribution or
  remove inherited method-reference gaps in patterns ending with `(`.
- Title-card commit `df7f46e84d701978cc0e667acc97dc76ae6c05c1` is composed.
  Ten corrected characterization cases passed against the original renderer.
  After the move, 57 focused tests passed with zero failures, errors or skips:
  ten rendering/order cases at 320/424 pixels, eleven API checks, nine accounting
  checks and 27 ArchUnit checks. The unchanged renderer body and precisely
  fourteen removed frozen identities were compared directly. The final command,
  with the same display-free environment as the background checks, was:

  ```bash
  python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
    -Dopenggf.headless.gl=surfaceless-egl \
    '-Dtest=TestTitleCardSpriteRenderer,TestTitleCardManagerRenderOrder,TestModApiSignatureSurface,TestArchUnitRules#low_level_layers_do_not_depend_on_runtime_layers+package_slices_are_free_of_cycles+core_runtime_cycle_cluster_does_not_gain_top_level_edges+shared_layers_do_not_depend_on_game_specific_packages+object_packages_do_not_access_global_game_services,TestArchitecturalReviewGuard' test -B
  ```

  The initial original-renderer fixture omitted configuration from its mocked
  engine context. That test setup was repaired before the clean baseline run;
  no production behavior was changed to satisfy it.
- Rewind commit `46d62d09dfe726a1de4539e061004a69ee6a14be` is composed.
  Its [audit](../audits/2026-10-24-rewind-disposition-review.md) covers all 23
  original fields, the five accepted policies and 18 retained obligations, plus
  initialized provider ownership and the one remaining static exclusion. The
  final evidence comprises 180 distinct passing tests across 26 classes with no
  skips. The audit records both exact selections and the corrected fixture-only
  coordinate assertion. Independent review found one incorrect closure: the
  false-floor child list is rebuilt densely after a child retires, while the
  captured collapse cursor still uses list positions. The cited eight-child
  test does not cover partial collapse. Restore that original baseline key and
  remove its new transient policy; the underlying runtime defect is not repaired
  by this classification campaign. No other blockers were reported.
- Tool preflight passed with Java 25, Lua 5.4 and PowerShell. The background
  ownership and native-state checks passed: five tests, zero failures, errors or
  skips, at base `0b2c3c2a9` plus the new characterization test. The command was:

  ```bash
  env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
    JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
    python3 tools/testing/maven_queue.py --lean -Dmse=off \
    -Dopenggf.headless.gl=surfaceless-egl \
    '-Dtest=TestBackgroundRendererOwnership,TestBackgroundRendererStateRestoration' test -B
  ```

  Shared admission took 635 seconds; Maven execution, including the clean
  compilation, took 91 seconds. These intervals are distinct. The command
  session `52846` completed successfully and has no remaining process.
- All implementation subagents use standard speed (`serviceTier=default`) after
  the user's correction. Changing the T3 selection interrupted their turns; they
  resumed in the same worktrees with their changes and test requests preserved.
- All implementation slices are composed at `51905ab35b9d27585991fae0b1d1d82a209d6b88`
  plus coordinator documentation and the background characterization test.
  The final category plan selects all 3,661 smoke candidates. Its shared or
  unclassified triggers include the test scanner, metadata/policy tables, moved
  renderer and import-only callers, and baseline resources. The two scanner
  consumers and their negative controls were executed. Exact rewind policies
  document existing reconstruction for five previously uncaptured list fields;
  the shared capture algorithm is unchanged. The title-card rendering body is
  unchanged and its actual submissions/pixels/API were checked. This bounded
  impact supports focused validation instead of all ordinary smoke categories.
  Guard request `79803` was cancelled before admission because the review
  correction superseded its source state; it executed no tests. A fresh complete
  guard request completed in session `77402`: 743 tests across 89 XML reports,
  zero failures, errors or skips, including all 32 ArchUnit checks. The corrected
  18-field disposition inventory and single static exclusion passed. Admission
  took 331 seconds and Maven execution took 6 minutes 35 seconds. Required S3K stability,
  title-card lifecycle/rewind and the existing complete false-floor graph check
  completed in session `79997`: 77 tests across nine XML reports, zero failures,
  errors or skips. Both classes named `TestSonic3kLevelLoading` executed. Admission
  took 665 seconds and Maven execution took 31.632 seconds. The shared worktree
  lock serialized these runs; waiting intervals overlap other work and must not
  be added as total delivery time. No full-suite run was requested.
- During validation, the destination advanced to
  `d3deeb692bb61745148361266796effa1ce9a3c0`. Its executable changes add fifteen
  qualified small-test names and bump an advisory tooling revision; there are no
  Java, POM, selection or fixture changes. Its qualification record includes
  Python safety checks and 326 actual automatic-lane test executions. This
  disjoint upstream change was merged without conflicts after both Maven jobs
  completed. The fixed pre-task base remains the accounting reference.
  `PYTHONPATH=tools/testing python3 -m unittest test_run_category_small test_run_category_schedule`
  passed all 31 cases after reconciliation. Engine sources, test selectors and
  fixtures were unchanged by that merge, so the completed Java evidence remains
  applicable. The submitted wrappers retained their earlier loaded tooling and
  normal budgets. Future Maven submissions use the main-workspace wrapper by
  absolute path, as requested; no existing job was restarted for the revision.

The complete guard command used the same display-free environment, Java 25 and
Lua 5.4 as the earlier checks. In these command records, `PAYDOWN_ROM_ROOT`
substitutes the supplied absolute main-checkout path containing the three
checksum-verified ROM files:

```bash
export PAYDOWN_ROM_ROOT=/absolute/path/to/existing/OpenGGF-checkout
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$PAYDOWN_ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PAYDOWN_ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$PAYDOWN_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

Report modification times were bounded to this completed invocation before
counting its 89 reports, avoiding older XML from earlier focused checks. The
current build-tooling guard passed all 123 cases; the historical October 23
failure is not present in this run.

The final focused command used the same environment and three absolute ROM
properties as that guard command, with no profile and this exact selector:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSonic2TitleCardManagerRewind,TestSonic3kTitleCardManagerRewind,TestGameLoopTitleCardLifecycle,TestFalseFloorBlockRewindGenericRestore' \
  "-Dsonic1.rom.path=$PAYDOWN_ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PAYDOWN_ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$PAYDOWN_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

All owned Maven sessions have completed. Relevant documentation links and all
23 audit rows were checked; the final field baseline has 18 keys. Final source
review retained the false-floor obligation and the five background-renderer
records rather than lowering counts without a complete behavior contract.
The known-bug ledger links the source-reviewed false-floor issue without claiming
a runtime reproduction. No trace frontier, configuration, published API, skill
or intentional ROM discrepancy changed.
