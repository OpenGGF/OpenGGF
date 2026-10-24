# Continuing architectural guard cleanup

The user authorized repeated cleanup loops until no actionable guard work
remains or progress is blocked. The starting integration base is
`624e95b4892533d23ae898d8127f277038777f70` on `develop`, after a fresh
fetch and fast-forward check. Each loop retains bounded ownership, observed
behavior checks, independent review and delivery through the main branch.
Standard-speed delegates are required throughout.

The continuing goal remains active after each delivered loop. Supported
boundaries and genuine evidence gaps must be identified explicitly; reductions
in one inventory do not close unrelated obligations.

## Remaining inventory at the start

- Low-level frozen dependencies: 156 records. Pattern/PatternDesc and palette
  contracts are API-pinned; their package names alone do not establish a safe
  migration. The background-renderer lifetime decision remains closed.
- Rewind field dispositions: 17 exact keys after the false-floor correction.
  These are unresolved dispositions, not 17 demonstrated runtime defects.
- Ambient test setup: 102 methods across 100 files. The first bounded migration
  addresses `TestGameStateManager` and `TestSonic3kMonitorObjectInstance`.
- Root concrete-game references: Engine has two; GameLoop has eight. Four of
  GameLoop's entries are unused imports. The actual ownership cases are editor
  object-table naming, glowing-sphere bootstrap and gumball debug controls.
- Lifecycle inventory: 582 destruction calls in 503 callers and ten dynamic-add
  calls in ten callers. Slot/respawn behavior must justify family migrations.
- Cross-game host donation and supported public submission boundaries remain
  explicit architectural decisions, not aggregate-zero targets.

## First continuing loop

Three independent tracks ground low-level responsibility families, rank the
remaining rewind obligations, and migrate two ambient-fixture tests using
existing construction/fixture boundaries. The coordinator also traces root
orchestration. The intended result is a small set of complete responsibility
cuts with exact allowance removal, not broad layer normalization.

Two plausible editor shapes are an internal family-aware name capability on
the native registry or editor-project composition that produces the same name
lookup. The former would follow the existing unpinned
`ObjectPropertyProvider`/`ObjectPreviewProvider` pattern and keep table selection
with the registry; the latter must avoid merely moving concrete coupling into
a shared helper. Any selection must preserve decorated-registry fallback,
explicit S3KL/SKL metadata and the existing creator API.

For bonus orchestration, the provider already exposes `getActiveType()`.
Bootstrap selection should be tested through that semantic state. Game-specific
debug filters need an internal stage-owned capability if their root calls move.
No new Mod API method or copied numeric zone constant is assumed necessary.

Four confirmed unused GameLoop imports are removed in this loop, lowering its
concrete-reference maximum from eight to four and its class-size maximum from
3311 to 3307. This is stale source-budget cleanup, not removal of the remaining
bonus bootstrap/debug responsibilities. Their four concrete references and
Engine's two editor references remain queued for a caller-complete ownership cut.

The selected implementation family for this loop is S2 piece-backed fragments,
alongside fixture cleanup and source-guard hardening. Research predicts missing
render submissions after destructive restore for BreakableBlock and OOZ Launcher
fragments; existing null-art graph checks do not establish their rendering.
The worker must reproduce nonempty draws before choosing a complete art recipe.

## Method-reference permission gap

Invocation-shaped patterns such as `LevelManager.getInstance(` failed to match
the scanner's existing `LevelManager.getInstance::reference` operation. Two new
controls were run against the unchanged scanner at the integration base. Both
failed, with zero errors/skips: five runtime fallback references were expected
but zero found, and two normalized LevelManager references were expected but
the inventory was empty. Baseline admission took 72 seconds; Maven compilation
and execution took 86 seconds.

The chosen shape passes the AST access kind into policy matching. Only method
references trim a pattern's trailing `(`, and the remaining suffix must equal
`::reference`, preventing similarly named methods from matching. The emitted
operation remains a reference, so direct-call approvals cannot pay for it.
An alternative would synthesize invocation text while storing a different
operation string; keeping the actual access kind is clearer and avoids that
representation mismatch. No public API or production source changes.

All 39 scanner/consumer checks passed with zero failures, errors or skips after
the fix: 12 zone-event checks, 19 object-service checks, seven runtime-scanner
checks and one object-scanner check. All XML method identities were inspected
and consumed reports removed. The successful Maven execution took 33.421
seconds; its admission duration was not retained. Independent review found no
blockers. The reviewed diff hash
`fa49daea3a637b755e0b06e14988d5c3fee7d5e052b0a9699639d64470c0236a`
matches the committed three-file slice `232a8d84184aa951768f5fc6694a021d583a803f`.
The source scanner remains syntax-only; this change does not claim type or
import attribution.

## Fixture ownership checkpoint

Commit `58f99a6ecb6e959bcb3301d5a978dd06b96ca8db` replaces ambient setup
with direct construction in TestGameStateManager and the existing named S3K
fixture in both classes named TestSonic3kMonitorObjectInstance. The four-file
diff has eight insertions and 41 deletions. The coordinator reviewed the complete
diff; all test bodies and scanner logic are unchanged. Four baseline methods
are removed across three files, reducing 102 methods/100 files to 98/97.

The first focused run passed 43 cases. After the second monitor migration its
two affected cases passed again, and a separate fresh-JVM singleton guard passed
eight cases against the final baseline. All runs had zero failures/errors/skips.
These are separate executions, not 53 distinct cases. No production code or
allowance was added. The worker is clean, with no active job, and its branch is
composed in the coordinator tree. Delivery and cleanup remain coordinator-owned.

Both invocations used Java 25, Lua 5.4 and the current absolute queue wrapper
from the coordinator worktree. DISPLAY, WAYLAND_DISPLAY, JAVA_TOOL_OPTIONS and
JDK_JAVA_OPTIONS were unset. The baseline selected only the two new methods.
The successful selection was:

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" --lean -Dmse=off \
  '-Dtest=TestRuntimeAccessSourceScanner,TestObjectServicesMigrationGuard,TestZoneEventRuntimeAccessGuard,TestObjectGuardSourceScanner' test -B
```

`MAIN_CHECKOUT` stands for the existing absolute main-checkout path. No ROM or
native rendering prerequisite applies to these source controls.

## Next ownership cut

The low-level research partitions all 156 records by responsibility. The exact
Pattern/PatternDesc family has 47 records, rather than the earlier approximate
49. Three underwater implementations duplicate ratio eligibility, fixed-point
arithmetic and saturation: RenderContext's public compatibility method,
GraphicsManager's cached-RGB uploader and AppearancePalettes' private rows.

| Responsibility family | Frozen records |
|---|---:|
| Pattern / PatternDesc | 47 |
| Legacy underwater derivation | 17 |
| Other Palette values | 19 |
| RenderContext game identity | 6 |
| Ambient composition calls | 17 |
| Audio ROM typing | 8 |
| Data-layer runtime/provider contracts | 9 |
| Dirty-pattern orchestration | 3 |
| UI orchestration | 6 |
| PreparedPiece compatibility | 18 |
| Background-renderer lifetime | 5 |
| Fade rewind participation | 1 |
| Total | 156 |

These are committed allowances, not a fresh live census. The rule also forbids
dependencies between its low-level layers; audio-to-ROM and data-to-audio edges
therefore appear despite its runtime-oriented name. Pinned types require
caller-complete migrations or an explicit supported-boundary decision, not
silent API removal.

The next loop should compare a shared package-private graphics transform against
relocating the legacy method alone. The shared transform can serve PaletteView
and cached RGB representations while existing owners retain allocation, cache,
fade and publication behavior. The public adapter must preserve `getColor`
subclass dispatch, input sampling and failure behavior. Research predicts an
explicit 12-record removal and five replacement adapter records, 156 to 149;
this is unmeasured and requires review and strict evaluation. Moving the legacy
method alone would leave both production implementations duplicated.

The first independent design corrected that estimate to 151. Preserving legacy
paired `getColor` dispatch and failure precedence requires retaining its sampling
boundary; extracting nine field reads into one private RGB adapter would add
four replacement identities. Both independent designs and the cross-judge
converged on 151. The selected second design centralizes only eligibility,
fixed-point ratios and saturation, retaining representation-specific traversal.
It avoids the first design's new ratio scratch array and two batch interfaces.
This owns numerical policy, not the complete underwater pipeline or reduction.
Both configured architect seats currently resolve to GPT-6.1-Sol;
their proposals are independent, but no model-family diversity is claimed.
The next-loop worker has started in its own worktree from the unchanged graphics
base. Its mandatory before/after controls preserve dispatch, alias observations,
failure precedence and actual underwater donor/appearance pixels.

## Live loop checkpoint

Coordinator branch `bugfix/ai-architecture-loop3-20261024` is at
`78168d9b8f4c6dabc7968366720c3da7f46825b2`, containing scanner commit
`232a8d841`, fixture commit `58f99a6ec` and S2 fragment commit `006fb6f02`.
The plan, four unused GameLoop imports, two source-budget values and coordinator
documentation are uncommitted; combined checks cover the bounded source cleanup.
The complete guard run in session `35703` and composed API/S3K session have
finished. No coordinator Maven request remains active. The fixture worktree is
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-a3a4962e`, clean and retained until push.
The S2 fragment worker owns `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-44afbf86`.
Both rendering failures have been reproduced and the explicit immutable art
recipe is implemented. Its worker has no live request. Independent static review
accepted the final five-file code/test/baseline hash
`c8cc09c05dcd491e183b28f1c401f315ed682b698d9d7fcc3517c7ae07af4b09`,
which the coordinator matched. The audit prose was reviewed by the coordinator.
Composed verification is complete; integration and cleanup follow. No task-owned
test request has been restarted for queue admission or tooling versions.

The next-loop worktree `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-45f9b563`
belongs to the underwater policy worker and must be retained during this loop's
cleanup. Additional next-loop workers address Rexon/Sol child ownership and the
LBZ2 ship's live flame retirement, in worktrees `t3-9899ae0b` and `t3-16179da1`
under the same worktree root. Their starting base is the current main
commit `624e95b48`; reconciliation with this loop is coordinator-owned.

The S2 selections establish 26 distinct cases through a 24/25 run followed by
the corrected retirement case and disposition guard, both passing. The one
failure was a new direct-step test that lacked cached camera bounds; runtime
source did not change for its correction. There was no single all-green 26-case
invocation. The worker's remaining XML contained 16 passing cases because the
narrow rerun replaced the launcher suite report. Those five current reports and
the generated 15-key disposition report were inspected and removed; earlier
case outcomes and exact selections are preserved in the linked field audit.
Renderer arguments and retirement flags are covered, not GPU pixels or automatic
managed-slot release. Complete S3K routes remain outside these local checks.

The actual display-free Java 25/Lua 5.4 environment passed category preflight.
The change-based plan against the fixed base selects all 3,662 smoke classes;
broad triggers include the scanner, source/fixture guards, the baseline resource
and import-only GameLoop edit. Existing fixture test bodies are unchanged;
the new scanner controls failed before the matcher change and passed afterwards;
the S2 correction is confined to immutable art recipes and renderer lookup, with
movement and slot calls unchanged. This understood impact supports the focused
evidence plus full fresh-JVM guards, API pins and required S3K stability checks
instead of all ordinary smoke categories. No full-suite run is requested.

The composed API/S3K selection completed in coordinator session `73751` with
71 distinct cases across six XML reports, zero failures/errors/skips: 11 API,
eight AIZ1 skip, six bootstrap, three decoding, and both level-loading classes
(36 and seven). Admission took 36 seconds; Maven execution took 86 seconds.
The source was `78168d9b8` plus the documented import-only GameLoop edit,
source-budget reductions and prose. Its 12 consumed XML/text files were removed.
The separate fresh-JVM guard profile completed with 749 distinct cases across
90 reports, zero failures/errors/skips. Admission took 38 seconds and Maven
execution 290 seconds. It covered the current GameLoop budgets, all 33 ArchUnit
checks and the exact 15-key field baseline. The ambient inventory is 98 methods
across 97 files. Lifecycle identities remain unchanged: 513 caller keys, 582
destruction calls and ten dynamic additions. All 180 consumed XML/text reports
and two generated inventories were inspected and removed.

Both composed selections used this environment and the existing absolute ROMs:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

For the guard command, replace only the exact `-Dtest` argument with `-Pguards`.
`ROM_ROOT` is the absolute existing ROM directory. These commands ran from the
coordinator worktree; they retained its own build output. No broad category or
release-only full-suite execution is claimed.

The next-loop underwater worker committed `d480283a4` after 89 before/after
behavior cases and 33 strict ArchUnit checks passed with zero skips. Its six new
EGL cases inspect underwater donor textures and appearance pixels. The measured
frozen allowance is 151, matching the corrected design. Independent code review
and next-loop composition remain pending; this result is not part of the loop
delivered here.

## Validation and continuation

Before integration, the required fast-forward pull advanced main from the
pinned base to `0ffa7f3c7`, bringing a substantial independently developed
Stageplay campaign. The coordinator merged it without textual conflicts. The
OOZ launcher's new player-release hook is independent of the fragment recipe;
both survive. Incoming identity codecs and five player-field policies are
retained, along with the new 154th package permission and current API pins.
The earlier 749/71 results describe the pre-upstream source. New composed
verification covers fragment/fixture behavior, affected native-release controls,
API/S3K stability and fresh guards; upstream Stageplay's full feature validation
is outside this cleanup's claim. Existing worker jobs keep their original base
and requests; subsequent composition will reconcile them here.

At composed commit `622833925eb827955cd37d731d533b232b51d088`, the affected
behavior/API/S3K selection passed 171 distinct cases across 15 reports with zero
failures/errors/skips. It selected the four S2 fragment/object classes, direct
GameStateManager and both monitor fixtures, TestStageplayNativeStateRelease,
TestStageplayObjectDirection, and the same five API/S3K selectors above. Admission
took 22 seconds and Maven 92 seconds, including recompilation. The fresh complete
guards then passed 753 distinct cases across 91 reports, zero failures/errors/skips,
after 73 seconds of admission and 320 seconds of execution. The increased guard
selection comes from upstream; all 33 ArchUnit checks passed. Actual XML case
identities and skips were inspected, and the 30 behavior plus 182 guard XML/text
reports removed. No new runtime changes followed these results.

The CI push-range policy check from `0ffa7f3c7` passed, auditing 14,571 entries.
This delivery preserves the independently merged Stageplay behavior and the
main workspace's unrelated dirty submodules and untracked user files. The goal
continues with underwater policy and remaining field dispositions; completing
this loop does not establish that architectural work is exhausted.

Workers own focused checks; the coordinator owns composed validation and
delivery. Future Maven invocations use the main-checkout wrapper by absolute
path, retaining their worktree cwd, target and required flags. Existing requests
remain alive. No release-only full suite is authorized for ordinary cleanup.

At each delivery, inspect the category plan against that loop's pinned base.
Use proportionate behavior, API, structural and required domain checks, inspect
actual identities/skips, and remove consumed diagnostics. Record exact source,
commands, outcomes, open obligations and live job handles here at checkpoints.
Hard or lengthy work is not a blocker. Reclassifying an exception requires
positive ownership and compatibility evidence. A justified supported boundary
can remain when no architectural improvement follows from removing its count.


## Level animation provider ownership worker (2026-10-24)

The isolated T3 checkout `t3-d5767fb6`, branch
`bugfix/ai-level-animation-provider-owners-20261024`, starts at `2020d2e57a`,
matching main `develop` when inspected. This bounded follow-up moves the whole
`AnimatedPaletteProvider` and `AnimatedPatternProvider` contracts from `data`
to the existing `level.animation` package. Their `Level` parameters, manager
returns and `IOException` declarations are unchanged. The three native game
implementations, LevelManager and TestModZoneRuntimeProfile change imports only;
all implementation bodies and custom-zone disable/error paths remain intact.
No forwarding interface, factory, model, API pin, guard rule, store key or guard
setting is introduced. The only frozen-record edits remove the two providers'
existing parameter/return pairs; no replacement identities are admitted.

Compatibility review checked all source references, the curated API roots,
recursive public/protected signature traversal, signature pins and internal
terminals. The annotated `data.Game`, `PlayerSpriteArtProvider` and
`SpindashDustArtProvider` remain unchanged. `Game` does not expose these two
unannotated animation providers through its signatures; native subclasses'
implementation interfaces are outside the creator surface. The compiled exact
candidate/recursive inventory check remains required before commit, so absence
from the textual pin alone is not treated as proof.

The change-based plan selects all smoke categories for these shared-package
paths. Focused validation is proportionate here because the source change is
exhaustively limited to package/import relocation and four record removals.
Existing tests cover custom-zone animation disabling, S1 palette fallback,
S2 production graph assembly/palette ownership, S3K tile VBlank publication and
HCZ palette cycling/ownership, plus the complete compiled Mod API inventory.
A separate strict TestArchUnitRules invocation covers layers, cycles and package
edges with frozen updates disabled. The parent owns complete guards, mandatory
S3K loading/bootstrap checks and composed delivery. These checks do not certify
all zone routes, intermediate rendered frames or external clients of unsupported
engine internals. No release-only full suite is selected.

The unchanged runtime source described above passed 67 distinct cases across eight
XML reports, zero failures/errors/skips (three seconds admission, 89 seconds Maven).
This includes the exact dependency-complete candidate pin, annotation inventory
and internal-terminal checks. The selection used:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestModZoneRuntimeProfile,TestSonic1PaletteOwnershipFallbackResolution,TestSonic2PatternAnimatorGraphAdapter,TestSonic2PaletteOwnershipIntegration,TestS3kZoneAnimateTilesPublication,TestS3kHczPaletteCycling,TestS3kPaletteOwnershipRegistryIntegration,TestModApiSignatureSurface' \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  test -B
```

The strict structural invocation replaces only the selector with
`-Dtest=TestArchUnitRules`. Tool preflight passed in this launch environment;
all three existing absolute ROM paths match the documented CRC32/SHA-1 identities.
`MAIN_CHECKOUT` and `ROM_ROOT` name the existing main checkout and its ROM directory,
as in the coordinator command above; the wrapper was invoked by absolute path.

The separate strict run passed all 33 distinct ArchUnit cases with zero
failures/errors/skips (six seconds admission, 31.090 seconds Maven). Actual XML
confirmed the low-level layer, package-cycle and top-level edge checks executed.
Compiled providers exist only at their new locations. No runtime edit followed
these checks; only this evidence append remained. The inspected nine XML and nine
text reports are disposable diagnostics; no pass cache or receipt is retained.
