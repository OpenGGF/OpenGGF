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

## Second continuing loop

The integration base is now delivered `2020d2e57a4b3a4cece9083730c89eb9c71d29a4`.
The clean coordinator is `bugfix/ai-architecture-loop4-20261024` in
`.worktrees/ai-architecture-loop4-20261024`. Water merge `05b41c4a8` includes
`d480283a4` and test correction `3e1ccf985`, preserving the intervening Stageplay
graphics changes without textual conflicts. Both published counts now say 151.

Independent static review accepted the numerical policy, sampling/alias/failure
semantics and exact nine-removal/four-addition inventory. It found that the six
new EGL cases unconditionally loaded Linux's `libEGL.so.1` on other platforms.
The coordinator verified that path. The follow-up adds exactly two imports and
method-level `@EnabledOnOs(OS.LINUX)`; all six cases executed on Linux, passing
with zero skips. The original 89-case before/after and 33-check ArchUnit evidence
remains scoped to the worker's original base. The post-Stageplay graphics selection
subsequently passed all 89 cases at
`05b41c4a8` plus this continuation prose: eight reports, zero failures/errors/skips,
including all six Linux EGL cases. Admission took 59 seconds and Maven 81 seconds.
The 16 consumed XML/text reports were inspected and removed. Graphics source and
these tests remain unchanged by the later LBZ merge; composed graph/API/S3K and
complete guard checks are still pending.

The water-only change-based plan selected 3,723 smoke classes across all categories;
the final four-family composition selects 3,727. This is
primarily because the frozen resource is shared. Actual-environment preflight
passed for Java 25 and Lua 5.4. The mathematical operations are unchanged,
representation traversals and allocation sites remain local, and literal GPU
expectations exercise the affected upload and appearance paths. This supports
focused rendering, affected graph replay, API/S3K stability and complete fresh
guards for the bounded composition; it does not certify every smoke category.
Reassess this scope if new cross-cutting behavior enters the loop.

Rexon/Sol commit `db2afc661` received independent review. Five corrected
baseline controls failed before the fix; graph/object 11, policy registry 21 and
disposition/metadata seven passed separately afterward, all without skips.
The nearest-parent helper is removed for these two families. Optional captured
child identities preserve sparse Rexon links and flying Sol ownership, with
inventories rebuilt only after all fields settle. Review found that no-table
same-instance capture/restore erased existing structural links, because absent
identity context was encoded as authoritative null IDs. The inherited public
no-argument contract does not exclude this mode. The coordinator verified that
path and requested a narrow correction: omit link extras only without a table,
while preserving authoritative table-backed nulls. Managed ownership and policy
findings otherwise had no blockers. Follow-up `48119fc3d` reproduced four failures
across the two public capture forms, then passed 17 affected graph/object cases
and four expanded authoritative-null controls, all with zero skips. Its six-line
capture-only guard returns the superclass snapshot without extras when a table
is absent. Restore behavior and central policies remain unchanged. The coordinator
inspected that exact correction; merge `4b420e402` preserves every audit section
and removes precisely these two keys, leaving 11. The 21 policy checks were not
repeated. Source/tests match the corrected worker commit.

LBZ ship commit `098ed5633` reproduced and corrected flame removal ordering, with
18 distinct passing cases across two selections. Independent review found a
missing constrained-capacity boundary: failed camera/flame allocation marks an
unregistered child destroyed without calling unload, leaving it in the newly
captured list. The coordinator verified the source path and requested a focused
reproduction/correction. Inspect the analogous boss addition as well. Preserve
native allocation attempts, deletion flags and retry behavior; do not weaken the
identity codec or conceal arbitrary unresolved references. Follow-up `e504754e6`
reproduced all three rejected-allocation cases, including the boss append, then
passed all 21 graph/cameo cases with zero skips. The coordinator inspected its
three admission checks and new capacity assertions: allocation calls, initialization,
destruction flags and retry behavior are unchanged. This bounded correction
resolves the review finding; no repeated delegated review was needed. Merge
`0c5cae80c` preserves the upstream player-control owner and both independently
appended audit sections. The combined field baseline is now 14 keys.

ICZ miniboss commit `1478f794e` starts from upstream `0ffa7f3c7`. The corrected
baseline produced one pass before creation and six null-orb restore errors at
progressed/defeat boundaries, with zero skips. Existing array codecs can restore
all fields, including final recipe values and indices, after private reconstruction
constructors make those helpers eligible. A permanently owned camera gate retains
fresh defaults and captured mutable state. Actual compact capture now includes
inherited `childComponents`; no exclusion or generic-framework change was needed.
The final 34 focused cases passed, including seven graph cases, one disposition,
three render-bucket and 23 existing encounter cases, all without skips.
Independent review found no blockers, confirming full reflected helper capture
and constructor ordering. Merge `d1df1f45c` retains every audit section and removes
only ICZ's key from the current 14, leaving 13. Its source/tests match the reviewed
commit. GPU output, player contact and full-route certification remain outside
the focused evidence. Composed checks are still pending.

### Composed verification and participant correction

The composed selection at `591c24f18` ran 187 cases across 17 reports, with two
failures and four errors, zero skips. All six red cases came from the eight-case
LBZ ship fixture: upstream's newly captured `visualControlOwners` correctly
rejected synthetic players absent from its identity context. The other 179
cases passed, including all 19 S2 ownership/codec cases, 34 ICZ cases, 42 upstream
holder controls, 71 API/S3K cases and 13 ride-cameo cases. Admission took 159
seconds and Maven 84 seconds.

The coordinator traced `ObjectManager.registerPlayersInto`: it registers the
camera's main player and the injected sidekick roster. This isolated fixture
has no focused main player; each synthetic participant now enters that service
roster, with an explicit encodable-identity assertion before the ship acquires
it. This preserves its camera coordinates, allocator, lifecycle recording and
distinct participants for overlapping ships. No production code or identity
codec changed. The affected eight-case class then passed with zero failures,
errors or skips, after 17 seconds of admission and 25.791 seconds of execution.
The other 179 cases were unchanged and were not repeated. There was no single
all-green 187-case invocation. Exact identities were inspected and all 34
remaining XML/text reports removed.

The combined selector was:

```text
TestS2RexonSolOwnershipRewind,TestS2BadnikChildGraphRewind,TestRewindFixS2Batch3Codecs,TestRewindFixS2Batch5Codecs,TestLbz2RobotnikShipRewind,TestLbz2RideCameoInstances,TestStageplayAcquiredControlRelease,TestIczMinibossGraphRewind,TestIczMinibossFieldDisposition,TestIczMinibossRenderBuckets,TestS3kIczMinibossObject,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
```

Both commands used the same display-free Java 25/Lua 5.4 environment, absolute
main-checkout wrapper, `-Dmse=off`, surfaceless EGL flag and three absolute ROM
properties documented for the prior loop above, ending `test -B`. The narrow
repair selected only `TestLbz2RobotnikShipRewind`. Complete fresh guards remain
pending. The later root-orchestration worker is independently committed and
reviewed, and stays outside this tested candidate.

### Root orchestration queued behind this composition

The remaining six concrete root references encode two responsibilities. A
registry-owned optional table-name capability is preferable to moving the
special case into another shared helper: the native registry interprets the
existing `ModObjectZoneSet`, while Engine retains decorated-registry fallback
when metadata or the native capability is absent. Keep `ObjectRegistry` and
`GameModule` pins unchanged, following the existing internal property/preview
capabilities. Verify explicit S3KL/SKL names independently of the active level.

Bonus startup already has provider-owned type, zone and bootstrap information.
Compare semantic active-type selection with provider zone mapping, pinning the
existing startup/repair boundaries before removing the concrete zone import.
Debug controls can use an internal stage-owned capability analogous to
`BonusStagePlayerPriorityPolicy`; do not add pinned provider methods. Preserve
key/modifier handling and account explicitly for gumball filter effects while
other bonus types are active. These are proposed bounded cuts, not completed
ownership or measured guard reductions.

### Loop 4 optional native-reference schema blocker

The isolated schema worker starts from exact parent candidate
`2de8b23d7a8f11bd1316f8b5cdcde2573bc41e53`; the reviewed water, LBZ ship and
ICZ changes are untouched. The parent supplied the fresh 753-case guard result:
752 passed and `TestRewindArchitectureGuard#objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage`
rejected RexonHead/SolFireball capture/restore (four new methods). No guard
baseline or architectural allowance changes here.

Only `RexonHeadObjectInstance.parent`, `.linkedHead` and
`SolFireballObjectInstance.parent` select a private optional mode of the existing
object-reference codec. Their shared exact-policy keys are explicitly CAPTURED
and participate in the existing reachability audit. Class-only lookup and all
other reference fields retain strict encoding/resolution. No table writes an
uncaptured marker whose restore preserves the current link even when a restore
table exists. A supplied table writes authoritative null for null/retired targets
or a stable ID for a registered target; missing restore targets resolve to null,
with resolved-type validation retained. Identity metadata remains true for closure
diagnostics. Snapshots contain reference bytes, not live object pointers. The two
actors lose their public manual hooks and private extras; parent inventories and
null-safe recreation remain unchanged. API pins and public schema contracts do
not change.

The parent's untracked optional-codec source was copied alone and acknowledged
before editing, with matching SHA256
`e29e89b26afba5866753264274fbbfc08c0f1c274f142938987eaa3bfe258e3e`.
Its supplied baseline on unchanged `2de8b23d7` ran 18 cases: nine failures
(three each for absent capture table, unregistered retired target and missing
restore target), nine replacement/null/type controls passing, zero errors/skips.
That matching baseline was reused, not rerun. Locally the original 18 plus four
existing strict controls passed; after six additional uncaptured-link and
class-only strict controls, the final codec selection passed 28 cases with zero
failures/errors/skips. Opaque snapshot values are asserted empty.

An attempted reuse test was rejected by actual manager evidence: the exploratory
18-case domain run had seven failures, zero errors/skips. Three were the proposed
eligibility/reused-child assertions: `ObjectRewindTypeSafety` still uses
`GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit`, which excludes
reference fields even when the compact schema captures them. Both children
therefore remain recreation fallbacks; no dummy state or safety-policy change was
added. The two proposed reuse tests were removed and the original fallback
assertions retained. The other four failures asserted the deleted private extra's
presence; these now assert compact state plus no subclass extra and retain all
authoritative-null/link-clearing behavior checks. The existing three-case
`TestRewindInPlaceObjectRestore` passed in that run and is not repeated. Admission
took 17 seconds and Maven execution 28.491 seconds.

Source/test review tree `6fdac3b4c689acaa7819df890254634d31262e9a` has seven
changed files relative to the parent and diff SHA256
`4291863f46b0c79ae010b45ec6c4ab8f369db57b09bc25aa96bd59e7c4773099`.
All local commands use the worker's own `target/`, Java 25 at
`/usr/lib/jvm/java-25-openjdk`, `LUA_BIN=/usr/bin/lua5.4`, and unset DISPLAY,
WAYLAND_DISPLAY, JAVA_TOOL_OPTIONS and JDK_JAVA_OPTIONS. `MAIN_CHECKOUT` denotes
the existing main checkout and `ROM_ROOT` its existing ROM directory; the three
ROM files below were verified against the documented CRC32/SHA-1 identities.

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" --lean -Dmse=off \
  '-Dtest=TestOptionalObjectReferenceCodecs,TestRewindObjectReferenceCodecs' test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" --normal -Dmse=off \
  '-Dtest=TestS2RexonSolOwnershipRewind,TestS2BadnikChildGraphRewind,TestRewindFixS2Batch3Codecs,TestRewindFixS2Batch5Codecs' \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" --normal -Dmse=off -Pguards \
  '-Dtest=TestRewindPolicyRegistry,TestRewindSchemaRegistry,TestRewindFieldDispositionGuard,TestRewindFieldDispositionAudit,TestCapturedPolicyCompactReachabilityGuard,TestRewindReferenceClosureValidation,TestRewindArchitectureGuard,TestModApiSignatureSurface' test -B
```

The exploratory command used the same domain flags with selector
`TestS2RexonSolOwnershipRewind,TestRewindInPlaceObjectRestore`. The corrected
19-case domain command above passed with zero failures/errors/skips, after 24
seconds of admission and 29.039 seconds of Maven execution. The scoped guard/API
command ran 78 cases: 77 passed, one new metadata-test error, zero failures/skips.
The error was an overbroad test lookup of the unrelated stale transient policy
`defeatExplosionController`. No unrelated policy changed: the new test instead
discovers actual runtime fields, then checks the exact closed optional set, its
CAPTURED dispositions, identity metadata and compact-path reachability. Existing
CAPTURED-policy auditing still rejects stale keys. Architecture's four checks,
including the original blocker, and all 11 API signature checks passed. This run
had zero admission wait and 30.775 seconds of Maven execution.

Only `TestCapturedPolicyCompactReachabilityGuard` was repeated, using the last
command with that exact selector. All seven cases passed, zero failures/errors/
skips, with no admission wait and 26.500 seconds of Maven execution. The other
71 unchanged cases were not repeated; there was no single all-green 78-case
invocation. XML identities/skips were inspected and consumed reports removed.
These are bounded schema, object-graph and manager checks, not route/viewport/
donor/team certification or a full-suite pass. Complete composed guards,
change-based smoke, integration and delivery belong to the parent; no full-suite
or duplicate complete-guards run was submitted. No live job remains.

## Root orchestration owner slice — 2026-10-24

Worker branch `bugfix/ai-root-orchestration-owners-20261024` starts at integrated
develop `2020d2e57a4b3a4cece9083730c89eb9c71d29a4`, in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-9220cd9e/.worktrees/ai-root-orchestration-owners-20261024`.
This bounded slice owns Engine/GameLoop routing, native registry/coordinator
capabilities, relevant behavior controls and exact root source budgets. It does
not own other workers' rewind policies/audit rows, the changelog, exception guide,
composed S3K/complete guards, integration, push or worktree cleanup.

Usage-first comparison: a bounded editor-project helper could remove Engine's
references, but would keep the native registry test and enum conversion in shared
code. A registry-owned optional `ObjectTableNameProvider`, analogous to the
existing property/preview providers, gives that interpretation to its owner.
Engine keeps the decorated base registry for editing and default names, querying
the root registry capability only for non-null explicit table metadata. The native
registry converts `ModObjectZoneSet`; explicit S3KL/SKL selection must ignore the
active level. ObjectRegistry and GameModule remain pinned and unchanged.

A separate bonus orchestration service would duplicate the existing lifecycle
and installer. Instead, `BonusStageFrameLifecycle` can ensure the active provider's
declared bootstrap through `BonusStageBootstrapInstaller`. Entry already calls
`onEnter(type)` then loads `getZoneId(type)`; startup bootstrap installation remains
at the existing title-card boundary. Ordinary-frame repair uses the active type,
not a second zone-to-type inference. Native Gumball/Slots declare no bootstrap;
Pachinko's existing spawn/factory and presence test remain unchanged. A provider
with a remapped zone or a different declared bootstrap supplies its own semantics.
Null/NONE providers must not request a bootstrap. No extra native allocation or
clock/frame/update-order change is intended.

The separate unpinned `BonusStageDebugControls` capability owns the two diagnostic
actions. The host keeps F11/Insert, debug-view, fresh-press and all modifier gates.
Native S3K dispatch retains the gumball filter effects in all three native bonus
types. Keep the executing provider through the frame tail so a completion handoff
does not drop an already admitted diagnostic action. No pinned provider methods
or creator API expansion is needed.

Before runtime edits, naming controls passed all four cases, installer controls
all three and existing gumball controls both cases. Native debug gating passed
three parameterized cases (each checks both keys, debug on/off, all eight modifier
sides, and held keys). The frozen-frame repair/order control then passed separately.
The initial new harness omitted the selected source key and frame context/token;
its three assertion failures and context/frame errors were fixture defects, fixed
without changing runtime source. These are bounded characterization results, not
a single all-green invocation. Subsequent validation and exact commands follow.

The selected implementation removes Engine's two and GameLoop's four concrete
references. Both caps are now exactly zero. Engine remains 3,942 effective source
lines; GameLoop falls from 3,307 to 3,305, and its class budget is tightened to
3,305. No other source allowance, pinned signature or rewind policy is changed.

Here `MAIN_CHECKOUT` denotes the existing absolute main checkout discovered by
`git worktree list`, as in the earlier command record. All worker Maven commands
ran from the worker directory above through
`python3 $MAIN_CHECKOUT/tools/testing/maven_queue.py`.
The launch environment was `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`,
`LUA_BIN=/usr/bin/lua5.4`, with `DISPLAY`, `WAYLAND_DISPLAY`, `JAVA_TOOL_OPTIONS`
and `JDK_JAVA_OPTIONS` unset. The leased `-v` probe reported Java 25.0.5.0.2
(76 seconds admission). Every test command used the following common arguments,
followed by the exact selectors recorded below and `test -B`:

```text
-Dmse=off -Dopenggf.headless.gl=surfaceless-egl
```

Characterization/negative-control command history, on the pinned base plus new
controls (runtime routing still unchanged):

- `-Dtest=TestEngineEditorProjectObjectNames,TestGameLoopBonusStageOwnership,TestBonusStageBootstrapInstaller,TestGumballDebugVisibilityFilter`:
  13 cases; the three new debug cases failed because the visibility assertion
  omitted its source, and the repair case errored on its missing context.
- `-Dtest=TestGameLoopBonusStageOwnership`: four cases; debug's three passed,
  repair errored on its missing frame token.
- `-Dtest=TestGameLoopBonusStageOwnership#pachinkoRepairFollowsOverlayAndPrecedesTheFrozenFrameTailWithoutDuplicating`:
  the corrected repair case passed, no skips.
- `-Dtest=TestEngineEditorProjectObjectNames#explicitTableUsesTheInternalRootCapabilityWithoutRequiringANativeConcreteClass,TestGameLoopBonusStageOwnership#activeProviderRepairsItsDeclaredBootstrapWithoutInferringTypeFromTheLoadedZone+diagnosticActionsBelongToTheProviderCapabilityAndDoNotChangeOtherStagesGlobals`:
  all three failed as intended, with no errors/skips: decorated instead of selected
  root names; no declared-bootstrap query; no provider-owned diagnostic dispatch.
  The two interfaces existed for this run, but root/native routing was unchanged.

On the implementation above, the exact behavior selection was:

```text
-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen
-Dsonic2.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog 2 (W) (REV01) [!].gen
-Ds3k.rom.path=$MAIN_CHECKOUT/Sonic and Knuckles & Sonic 3 (W) [!].gen
-Dtest=TestEngineEditorProjectObjectNames,TestGameLoopBonusStageOwnership,TestGameLoop,TestGameLoopBonusPlayerPriority,TestBonusStageBootstrapInstaller,TestGumballDebugVisibilityFilter,TestS3kBonusStageHeadlessBoot,TestEditorProjectLifecycle#s3kObjectFamilyPropertiesRefreshLabelsAndUndoToTheDocumentSavepoint
```

Each `-D` line is one quoted shell argument. All three existing absolute ROMs
matched the documented CRC32/SHA-1 identities. Result: 134 distinct cases across
eight reports, zero failures/errors/skips; Maven 77 seconds, no admission wait.
The case counts were names 5, GameLoop 101, priority 3, editor projection 1,
bootstrap installer 7, new bonus controls 12, existing gumball controls 2 and
native S3K bonus boot 3. The new controls also cover null/NONE providers,
unsupported diagnostics, no Gumball/Slots bootstrap allocation, actual declared
factory use, and provider retirement during the debug tail. XML identities and
all skip/failure elements were inspected, then consumed reports removed.

The affected source/API selection uses the same launch environment/common
arguments, plus:

```text
-Pguards
-Dtest=TestArchitecturalSourceGuard#rootGameLoopAndEngineDoNotGainConcreteSonicDependencies+releaseCriticalLargeClassesDoNotGrowWithoutExtraction+rootDispatchMethodsDoNotGrowBeyondCurrentBudgets+engineDoesNotGainNewLargeMethods,TestModApiSignatureSurface,TestModApiPinPolicy
```

It is a targeted guard/API selection, not complete guards. This worker runs no
broad categories, exhaustive/full suite or trace sweep. The coordinator retains
composed S3K/complete-guard, changelog/exception-guide, integration and delivery
obligations. No runtime changes followed the 134-case behavior result.

The targeted guard/API invocation passed 19 distinct cases across three reports:
four exact root/source-budget checks, eleven API-surface checks and four API-pin
policy checks. There were zero failures/errors/skips. Admission was 39 seconds;
Maven execution was 29 seconds. XML identities and skips were inspected and
consumed reports removed. The API descriptors, signature pins, ObjectRegistry,
GameModule and BonusStageProvider remain byte-for-byte unchanged. These results
cover the same runtime/test sources as the 134-case behavior invocation; only
this evidence prose changed afterwards. No task-owned Maven job remains live.
The worker retains its clean committed branch/worktree for parent composition;
it does not push or remove it. Full native routes, pixels, exhaustive traces and
complete guards are outside this worker's validation claim.

## Bounded S1 music parser ownership slice

The implementation worktree is `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-659c450e`,
branch `bugfix/ai-s1-music-parser-owner-20261024`, based on the observed main
`2020d2e57a4b3a4cece9083730c89eb9c71d29a4`. It was already isolated; this slice
does not update its base or perform integration. The parser and loader at this
base exactly match parent candidate `2de8b23d7a8f11bd1316f8b5cdcde2573bc41e53`.

The complete production caller search finds only `Sonic1SmpsLoader`, already
in `game.sonic1.audio.smps`. Shared audio neither constructs nor calls back into
the concrete parser. The four test callers are `TestSmpsHeaderConstruction`,
`TestSonic1SfxData`, `TestSmpsSequencerInstrumentLoading` and
`TestSmpsDataEndianParsing`; all move their references together. Historical
research/changelog mentions are not live callers. The candidate API pin names
neither `Sonic1SmpsData` nor `SmpsHeaderDecoder` nor `FmVoiceOperatorOrder`.

Moving the whole `Sonic1SmpsData` responsibility beside its loader and SFX
parser gives its existing S1-only policy an S1 owner. Leaving it frozen in
shared audio would retain the exception without a shared consumer. A factory
hierarchy adds no missing boundary here. The move changes only package/imports:
the entire parser javadoc and class body compare byte-for-byte with the base.
Both public constructor shapes, construction dispatch, one-based PSG envelope
lookup, operator normalization, endian/bounds/exception behavior and retained
ROM/audio references are intact. Existing public shared helpers remain the
lower-level dependencies; no facade, duplicate parser or new allowance is added.

After checking the new package identity and absence of the old source, the sole
exact record in `189a6718-1f70-4e38-8b78-fdb0af52fa74` is explicitly removed.
Its store is now empty. The stored rule key/description and disabled store
creation/updates are unchanged. Low-level allowance delta is zero: this supplied
base retains its 156 records, while parent candidate `2de8b23d7` retains 151
after its separate palette work. Published inventory prose, complete guards,
required S3K checks and composition remain parent-owned. The obsolete generated
old-package parser class was removed before post-move compilation, preventing a
stale class from masking the naming result.

Validation uses the existing tests, without adding tests that repeat the move.
Commands run from this worktree with its own `target/`:

```bash
MAIN_CHECKOUT=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
ROM_ROOT=$MAIN_CHECKOUT
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
export PATH="$JAVA_HOME/bin:$PATH"
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS
FOCUSED='TestSmpsHeaderConstruction,TestSonic1SfxData,TestSmpsSequencerInstrumentLoading,TestSmpsDataEndianParsing,TestSmpsInstrumentParsing,TestSmpsFmVoiceWriteProfiles'
ROM_BEHAVIOR='TestSonic1UnifiedAudioPresentationRomIntegration,TestSoundTestWavExport,TestHubMusicRomThemes#sonic1TitleThemeLoadsAndPresentsWithoutADevice'
AFFECTED='sonic_named_classes_live_under_matching_game_packages+low_level_layers_do_not_depend_on_runtime_layers+per_game_packages_do_not_cross_depend+shared_layers_do_not_depend_on_game_specific_packages+shared_code_does_not_construct_concrete_sonic_provider_classes+package_slices_are_free_of_cycles+core_runtime_cycle_cluster_does_not_gain_top_level_edges+imported_classes_include_application_code'
ROM_WAV='TestSoundTestWavExport$RomBackedSfxExport#renderToWavCompletesRomBackedElectricSfxWithAudiblePcm'
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$FOCUSED" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$FOCUSED,$ROM_BEHAVIOR" "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards "-Dtest=TestModApiSignatureSurface,TestArchUnitRules#$AFFECTED,TestArchitecturalSourceGuard#lowLevelGraphicsAndAudioDoNotGainGameplayServiceLookups,TestArchitecturalReviewGuard#archUnitFrozenRulesDoNotAutoUpdateDuringNormalRuns,$ROM_WAV" "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" test -B
```

The discovered existing S1 ROM is `$ROM_ROOT/s1.gen`, verified CRC32 `AFE05EEE`
and SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`. No ROM/link is created or
changed. Baseline at `2020d2e57` passed 18 cases, with zero failures/errors/skips;
admission waited 157 seconds and Maven reported 1:15. The post-move run passed
23 cases with zero failures/errors/skips, including the identical 18 baseline
class/method identities, all three S1 final-PCM integration cases, the S1 hub
theme and the outer WAV frame-count case. Admission waited 61 seconds and Maven
reported 1:23. Exact XML inspection showed that the outer WAV selector omitted
its nested ROM-backed case, so the final command explicitly selects that case
without repeating the completed outer case.

The staged `src/` diff SHA-256 is
`be759896933caab22ac0d405b861859b6b91f92792eced52e3689c3372809007`;
both post-move commands use this unchanged source/resource slice over the base.
The final command passed 43 cases with zero failures/errors/skips: 11 API
surface cases, 29 ArchUnit cases, the two source/update controls and the explicit
ROM-backed WAV case. All eight requested architecture identities are present.
The ArchUnit engine executes all 27 rule fields despite the field selector,
plus the two selected methods; this is not a complete guard-profile run.
Admission waited 12 seconds and Maven reported 37.393 seconds. The API pin
remains byte-identical, and the naming rule passes against its empty store.
All three Maven sessions are complete. Exact XML identities/failures/skips
were inspected and consumed reports removed. No category-run diagnostics were
created; no task-owned validation jobs or diagnostic files remain pending.
These are bounded behavior/contract checks; no full suite, audio sweep, trace
frontier or new zone certification is claimed.


## Third continuing loop: isolated composition

The fifth-loop coordinator starts from destination `2020d2e57`, then includes the
fourth-loop schema composition `71d919022` before adding the independently
reviewed follow-ups. Its branch is `bugfix/ai-architecture-loop5-20261024` in
`.worktrees/ai-architecture-loop5-20261024`; no destination branch switch occurs.

The composed heads are root orchestration `85d7e1f41`, panels `57fa987e7`,
ring evidence `fc17fb82e`, S1 parser `b85ba5a1c`, AIZ actor links `493aeddd7`,
and wall controllers `028cf0916` (including `6cf7d4bbe`). Append-only plan/audit
conflicts retain every worker's complete evidence block. Audit rows 1, 11, 12,
18 and 19 retain their respective reviewed dispositions. Baseline conflicts are
resolved by the exact removed-key sets, preserving prior S2, ship and ICZ
closures; both actual-path audit methods are retained as separate tests.
The current baseline has seven keys. Root concrete references are zero/zero,
GameLoop's source cap is 3,305, and the naming store is empty. The low-level
store remains 151 until a separately qualified animation-contract move is merged.
These are committed allowances and source budgets, not a combined test result.

Root capability review found no blockers; its 134 behavior and 19 targeted
API/guard passes remain worker observations. Panel snapshots retain their 21
values and fresh enclosing owner; the reported 16 final affected passes and
18 earlier unaffected passes do not form one all-green invocation. Ring work
adds six real-GHZ1 graph cases, retaining the arbitrary-parent recipe obligation;
no runtime or policy changes are hidden by that test-only slice. The parser body
is byte-identical and all live callers move together, with no supported pin change.
AIZ review accepts removal of two private histories and exact captured Knuckles
links within the normal parent-first retirement path; early collector removal
is untested. The wall-controller review confirms actual generic capture and deep
snapshot copying, ordered current-RNG rebinding and unchanged retirement.

The coordinator verified its empty-state compatibility edge: unconditional
services lookup made a service-free empty public-object restore fail. Follow-up
`028cf0916` reproduces three controls, then passes all 12 affected cases, with zero
skips, after clearing entries and returning before lookup for an empty array.
The original 48 behavior passes plus the corrected path audit remain separately
scoped. A normal initialized encounter's nullable box rigs are an adjacent
source-derived restoration risk, not yet a reproduced defect; the controller
checks deliberately seed FIGHT/DONE with rigs null. This remains queued as a
separate bounded investigation, not a claim of complete encounter restoration.

The parent retains required composed S3K/API and fresh complete guards, actual
change-plan/preflight inspection, documentation/policy checks, integration and
push. Later Knuckles-inventory, freezer-debris and animation-provider work are
still separate. No parent Maven job has been submitted for this candidate yet.


### Fourth-loop acceptance and delivery checkpoint

Schema commit `043c869e8455a615118c848a1b0a11506d5ab737` is merged as
`71d91902202d2cb1899633a78c33e45f5c1ca820`. Source, tests, POM and testing tools
match the worker commit exactly. Independent static review found no blockers.
Its final seven-file source/test diff fingerprint is
`4291863f46b0c79ae010b45ec6c4ab8f369db57b09bc25aa96bd59e7c4773099`;
the earlier `321124c` tree predates the corrected metadata test. Stale-key
validation covers the three shared exact-policy constants, not arbitrary future
optional-only metadata. No manual-override baseline, API pin or allowance grew.

The complete fresh guard profile then passed at `71d919022`: **754 distinct
cases across 91 XML reports, zero failures, errors or skips**. The previous
manual-override blocker executed and passed; all seven compact-policy metadata
cases and all 33 ArchUnit cases executed. Admission wait was zero, and Maven
reported 6 minutes 21 seconds. The command used the same unset display/JVM-option
environment, Java 25, Lua 5.4, absolute main-checkout wrapper and three verified
absolute ROM paths documented above, with:

```text
-Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl test -B
```

This supersedes the red 753-case guard result after the schema correction. The
89 rendering cases, repaired eight-case ship selection, unchanged other 179
composed cases, and worker's corrected 19 S2 graph/28 codec controls retain their
stated source and coverage limits. Required API/S3K checks passed in the composed
selection; schema API checks passed again after the reference change. These are
focused behavior and complete structural guards, not a complete smoke or public
release full-suite claim. The new codec mode is field-selected only for the three
S2 links; strict defaults have separate negative controls. That bounded impact
supports the focused selection rather than the 3,727-class all-category plan.

The committed allowance is 151 low-level records, with 11 field-disposition keys,
98 ambient setup methods across 97 files and 154 top-level package permissions.
The 582 destruction and 10 dynamic-add call inventory remains unchanged. The
root orchestration, panel, ring-evidence, parser and AIZ work now being composed
in a separate fifth-loop checkout is outside this result. In particular, root
reference and naming reductions there are not delivered by this fourth loop.
A final fetch found the destination still at `2020d2e57`; main's inherited dirty
submodules and untracked files are preserved. Final prose/policy checks,
integration, push and accounted-for worktree cleanup follow this checkpoint.


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


### Fifth-loop validation selection

Delivered destination `a236a1276ec7020eaca28134e6fa6d4f345c8e07` is merged by
`e143088fa`; only append-only evidence needed reconciliation. Animation-provider
commit `1f55021bb` is merged by `497b52109`. The coordinator compared both entire
interface bodies and all five consumer bodies with the base after removing only
package/import lines: they are unchanged. The exact four frozen records disappear
without additions, leaving 147 in this composition, meeting the former <=150
target. Supported pins and store keys remain unchanged. The next ownership target
is internal SAT-replay descriptor construction; it requires a separately qualified
migration, and the interim count must hold at 147.

Actual-environment preflight passed with Java 25 and Lua 5.4. The change-based plan
against pinned delivered `a236a1276` selects 3,734 smoke candidates across every
category, driven by root files, relocated contracts and shared frozen resources.
The known caller-complete moves preserve implementation bodies; focused controls
cover root dispatch/capabilities, the actual animation consumers in all games,
SMPS/WAV behavior, destructive owner-bound graph replay and API/S3K stability.
The parent therefore selects 41 exact class/method selectors (including explicit
nested WAV and all four required S3K selectors), then complete fresh guards.
This is proportionate focused verification, not execution of the all-category
smoke plan or the release-only full suite. Estimated execution is a few minutes,
with queue wait separate and unbounded. Finish the selected invocation, inspect
all exact identities/failures/skips, repair regressions narrowly, and expand only
for new evidence or unresolved coupling. Do not repeat unchanged broad coverage.
Later Knuckles, freezer and initialized-box-rig work stays outside this candidate.

### Fifth-loop focused results, correction and acceptance

The 41-selector composed run at `e849ed5a4` passed 354 distinct cases across
42 reports, with zero failures, errors or skips. Admission took 189 seconds and
Maven 1 minute 41 seconds. Both physical level-loading classes, all mandatory S3K
identities, and the explicitly selected nested ROM-backed WAV case executed.
The exact selector, with the common display-free environment, absolute wrapper
and three verified absolute ROM properties above, was:

```text
-Dtest=TestEngineEditorProjectObjectNames,TestGameLoopBonusStageOwnership,TestGameLoop,TestGameLoopBonusPlayerPriority,TestBonusStageBootstrapInstaller,TestGumballDebugVisibilityFilter,TestS3kBonusStageHeadlessBoot,TestEditorProjectLifecycle#s3kObjectFamilyPropertiesRefreshLabelsAndUndoToTheDocumentSavepoint,TestS3kLbzMinibossGraphRewind,TestLbzMinibossPartBuckets,TestS3kLbz1MinibossAndTransitionHeadless#fatalHitInstallsWaitBeforeFirstDecrementForNativePlayersAndRewinds,TestLbzWallExplosionsRewind,TestLbzWallExplosionsEmptyRewind,TestRewindFieldDispositionAudit,TestS1RingGroupGraphRewind,TestSmpsHeaderConstruction,TestSonic1SfxData,TestSmpsSequencerInstrumentLoading,TestSmpsDataEndianParsing,TestSmpsInstrumentParsing,TestSmpsFmVoiceWriteProfiles,TestSonic1UnifiedAudioPresentationRomIntegration,TestSoundTestWavExport,TestSoundTestWavExport$RomBackedSfxExport,TestHubMusicRomThemes#sonic1TitleThemeLoadsAndPresentsWithoutADevice,TestS3kAizIntroGraphRewind,TestAizPlaneIntroInstance,TestS3kAizIntroInventoryRewind,TestModZoneRuntimeProfile,TestSonic1PaletteOwnershipFallbackResolution,TestSonic2PatternAnimatorGraphAdapter,TestSonic2PaletteOwnershipIntegration,TestS3kZoneAnimateTilesPublication,TestS3kHczPaletteCycling,TestS3kPaletteOwnershipRegistryIntegration,TestModApiSignatureSurface,TestModApiPinPolicy,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
```

A targeted second AIZ review then rejected previously untested compatibility
edges. A bound emerald's no-table public capture newly required a table. The
intro had a discriminating live phase after routine `$16` and
`releasePlayersForVisualDirection()`: its captured player owner is cleared but
Knuckles remains. Initially null no-table captures also began clearing later
links. The annotated inherited API provides no applicable managed-only
restriction. Separately, a full SST rejects the collector after the parent has
stored it, leaving an unregistered strict reference. These were source findings,
not failures in the earlier 354-case selection.

Commit `5216bea2612d2f8596c4770a128561455246a1ed`, merged as `aa843d5d8`,
reproduced 11 failures in 26 controls, then passed 106 affected cases including
the original 42 AIZ checks, with zero failures/errors/skips. Exactly two fields
select a private context-compatible, managed-strict reference mode: missing
context leaves the current link uncaptured, while a supplied table retains strict
identity validation and authoritative nulls. Default strict and S2 optional-native
wire behavior remain unchanged. The owner clears a rejected collector only after
the original allocation attempt; routine advancement and player flags are intact.
Independent review accepted the exact tree
`7d6db8a6aec8c9071334669805b080527a581e18`. Arbitrary early collector retirement
remains unproven and strict. No signature, annotation, capture override or allowance
grew.

Source, tests, POM and testing tools in the composed correction match the worker
exactly. Fresh parent API/S3K verification at `aa843d5d8` passed all 75 distinct
cases across seven reports, zero failures/errors/skips: API surface 11, pin policy
four, AIZ skip eight, level loading 43, bootstrap six and decoding three. Admission
was zero and Maven 1 minute 32 seconds. Its exact selector was:

```text
-Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
```

The complete fresh guards command then passed **755 distinct cases across 91
reports**, zero failures/errors/skips. Admission took six seconds and Maven
5 minutes 22 seconds. All 33 ArchUnit checks, eight compact-policy checks, four
rewind architecture checks and eight singleton-lifecycle checks executed. This
used the common environment and absolute ROM properties with:

```text
-Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl test -B
```

The earlier 354-case invocation qualifies its unchanged components; it is not
claimed as a rerun after the codec correction. The affected 106-case evidence,
fresh 75-case API/S3K run and complete guards qualify the correction within their
stated bounds. The 84 focused, 14 API/S3K and 182 guard XML/text reports, plus two
inspected inventories, were consumed and removed. The current censuses are seven
field keys and 513 lifecycle identities matching their baselines.

A final fetch found main and origin still at delivered `a236a1276`. This fifth
candidate contains 147 low-level records, zero naming records and zero concrete
root references; its seven field obligations, 98 setup methods/97 files and
154 package permissions remain explicit. The sixth candidate's newer graph
changes and exact-policy guard remain outside this result. Final prose and
policy checks, integration, push and fully accounted-for cleanup follow.


### Sixth-loop exact-policy hygiene

The next coordinator is `bugfix/ai-architecture-loop6-20261024` in
`.worktrees/ai-architecture-loop6-20261024`, created from delivered `a236a1276`
then advanced to fifth-loop composition `e849ed5a4`. Its initial owned slice is
independent of the pending AIZ correction and later graph-family merges.

The CAPTURED reachability guard resolves only CAPTURED keys. An obsolete DEFERRED
entry for `IczMinibossInstance#defeatExplosionController` therefore survived after
that field disappeared. A new exact-policy guard resolves every disposition's
actual declaring field without initializing production owners. Controls cover
all five disposition values, missing owners, missing fields, inherited fields
at the wrong declaring owner, and an owner whose initializer would fail. This
checks stale permissions, not whether each surviving disposition is semantically
correct or every encounter is rewind-complete.

The first attempt stopped at test compilation because the coordinator omitted
`FieldKey`'s import: zero tests executed, 73 seconds Maven time and no admission
wait. After the test-only import correction, unchanged production ran 14 cases:
13 controls passed and the production census failed only for the ICZ key, with
zero errors/skips. Admission took six seconds and Maven 25.997 seconds. The fix
removes exactly that unreachable key, leaving every other field-policy identity
and value unchanged. No frozen allowance or field-debt baseline changes.

The subclass capture Javadoc also incorrectly required every stateful boss or
badnik to add manual capture overrides, conflicting with the automatic schema
and exact override ratchet. It now describes the actual compact/generic routes,
explicit helper and identity dispositions, existing manual paths and the distinct
no-context contract. No runtime method body or supported signature changes.

Focused verification then passed 26 cases across four reports, zero failures,
errors or skips: 14 exact-policy controls, seven CAPTURED reachability checks,
four rewind architecture checks and one field-disposition guard. Admission was
zero and Maven reported 1 minute 19 seconds. The generated field census matches
this seven-key baseline. Reports and the inspected inventory were consumed and
removed. This does not certify the later AIZ fix or unmerged graph work.

Both test commands ran in this coordinator with the display/JVM-option variables
unset, Java 25 and Lua 5.4, using the absolute current main-checkout wrapper:

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Pguards \
  '-Dtest=TestExactRewindPolicyGuard' test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Pguards \
  '-Dtest=TestExactRewindPolicyGuard,TestCapturedPolicyCompactReachabilityGuard,TestRewindFieldDispositionGuard,TestRewindArchitectureGuard' test -B
```

`MAIN_CHECKOUT` is the existing absolute main checkout documented above. The new
class follows the existing guards-profile name convention; its actual complete
profile inclusion remains part of the eventual composed sixth-loop guard run.
No full suite or duplicate broad run was launched for this metadata/Javadoc edit.

### Sixth-loop composition and boundary qualification

The sixth candidate advanced through delivered fifth-loop main
`3ca14002fa2fafeabe1d3e6e370a3f559b6cba4e`. Production and tests were fixed at
`399ea56113dceaf73ef304ad945582924b9ff093` for the following parent runs in
`.worktrees/ai-architecture-loop6-20261024`. The later documentation edits do
not change those executable inputs.

The composition includes the reviewed freezer history removal (`ece291b08`),
initialized LBZ box rigs (`656a8bb58`), corrected LBZ cameo ownership
(`4cce9ad67`), lightning art values (`83dd96cd8`) and ordered S1 ring recipes
(`2c05bf692`). The existing field audit retains each family's red/green commands,
rejected approaches and bounded evidence. Review corrections matter here:
rejected owner admission must not create uncapturable links; absent identity
context differs from authoritative captured null; accepted null ring placements
must also restore; and helper ownership removes the spark's proposed three
transient exclusions. The final cameo annotation allowance decreases from four
to two. No owner override, exclusion, API pin or frozen allowance grows.

Conflict resolution preserves both the four-field optional-native reference set
and AIZ's separate two-field context-compatible, managed-strict set.
`RewindCodecs.java` is byte-identical to delivered main. Both actual-path audit
methods survive the ring/spark test conflict. Baseline reconciliation removes
only the five newly resolved keys from main's seven, leaving the LBZ end-boss
launch history and final-boss spawned inventory. Audit merges retain earlier
sections and merge assigned table rows by identity, rather than concatenating
interleaved command fragments.

The change-based planner selected 3,739 smoke classes because the exact policy
file, field baseline and base-class Javadoc are classified as shared. The actual
policy change selects one additional exact optional field and removes one stale
disposition; the base-class runtime bodies and generic codec are unchanged.
Family-local reconstruction changes have destructive graph, no-context, rendering
and forward-replay checks. A focused consumer selection plus complete fresh-JVM
guards is proportionate here. The updated runner's Java 25/Lua/PowerShell preflight
passed. No release-only full suite or duplicated broad baseline/development run
was submitted.

The parent focused invocation passed **390 distinct cases in 33 reports**, zero
failures, errors or skips. It includes both physical S1 ring and S3K loading
classes, all eight nullable-ring controls, all four spark-art methods (including
actual EGL pixels), both reference modes, API pins, manager closure and all four
mandatory S3K selectors. Admission took 43 seconds; Maven reported 92 seconds.
The complete separate guards invocation then passed **769 distinct cases in 92
reports**, zero failures/errors/skips. Admission took nine seconds; Maven reported
321 seconds. Actual inclusion was checked for all 14 new exact-policy controls,
33 ArchUnit cases, eight compact-policy cases, four rewind architecture cases and
eight singleton-lifecycle cases. The two generated censuses match exactly:
two field keys and 513 lifecycle caller identities. The 66 focused and 184 guard
XML/text reports and both inspected censuses were consumed and removed.

Commands used the existing main checkout, each worktree's own target, display and
JVM-option variables unset, and verified existing World REV01/locked-on ROMs:

```bash
MAIN_CHECKOUT=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
ROM_ROOT=$MAIN_CHECKOUT
FOCUSED_SIX='TestLbzKnucklesInventoryRewind,TestLbz2RideCameoInstances,TestOptionalObjectReferenceCodecs,TestRewindObjectReferenceCodecs,TestAizCollectorObjectReferenceCodecs,TestAizCollectorPublicRewind,TestS3kAizIntroGraphRewind,TestLbzBoxRigsRewind,TestLbzWallExplosionsRewind,TestLbzWallExplosionsEmptyRewind,TestS3kLbzMinibossGraphRewind,TestRewindFieldDispositionAudit,TestS3kIczFreezerGraphRewind,TestS3kIczFreezerObject,TestS1RingGroupGraphRewind,TestS1RingNullRecipeRewind,TestSonic1RingInstance,TestSonic1RingPlacement,TestLightningSparkArtRewind,TestRewindPolicyRegistry,TestRewindSchemaRegistry,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS2BadnikChildGraphRewind,TestObjectManagerRewindSnapshot,TestObjectManagerRewindReferenceClosure'
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$FOCUSED_SIX" \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Pguards \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The result proves bounded restoration and current guard compliance, not every
native encounter route, simultaneous family, player-contact outcome or GPU path.
Spark pixels and the six stock S1 graph cases executed; CPU-only box/freezer/cameo
observations do not imply full-route rendering certification. The first report
inspection incorrectly expected 14 methods in the spark class: its four art
methods plus other classes made up the worker's 14-case selection. XML established
the actual four identities; no test rerun was needed for that inspection error.

Two read-only studies refine the next course without claiming debt removal.
SAT replay at `aa843d5d8` retains its persistent descriptor adapter's four
field/construction/write records. The supplied decoder runs synchronously before
tile append and later state sampling; it can observe raw/cached disagreement,
descriptor identity, returned overrides and exceptions. Expanded palette seven
has raw palette bits three but cached palette seven. Primitive GPU replay does
not eliminate that callback obligation. A primitive callback migration would need
an explicit compatibility decision; moving the adapter or erasing its type merely
changes recorded identities. Whole-row command substitution also has different
integer/float behavior at extreme coordinates. This study ran no tests and
authorizes zero allowance removals.

RenderContext lifetime inspection at `3ca14002f` supports shared GPU bank
allocation, reset boundaries and standalone typed access. Mutable donor colors
are a separate responsibility. The completed donated-team initialization path
may overwrite the main donor row while loading distinct sidekick art; donor-color
rewind and shrinking ordinary sidekick-bank texture height need separate
reproductions. None is established as a runtime defect by source inspection.
The next bounded worker first tests completed-team selection while preserving
standalone refresh semantics. No session-context framework or registry relocation
is justified. Background renderer and PreparedPiece compatibility remain closed
decisions absent new evidence. Low-level frozen records stay at 147.

A separate test-only slice, `9b1242fa5`, constructs FadeManager directly in its
four existing unit cases. Assertions and test identities remain unchanged; the
ambient setup and teardown disappear. The exact setup allowance decreases from
98 methods/97 files to 97/96. `TestFadeManager,TestSingletonLifecycleGuard` passed
12 cases, zero failures/errors/skips, through the same absolute wrapper and Java
25/Lua environment with `-Dmse=off` and no GL requirement. Admission took 166
seconds; Maven reported 78 seconds. Exact XML identities were inspected and all
four reports removed. No production code changed after the 390/769-case parent
runs, and the changed fixture/guard were checked in this separate checkout.
Repeating the other unchanged guards would add no coverage. This final test-only
merge retains that scoped evidence rather than claiming a second complete run.

Main fetch/fast-forward pull found no intervening upstream changes at this
checkpoint. All parent jobs have completed. Documentation/policy checks, final
integration, push and accounted-for cleanup remain delivery obligations. Later
boss-inventory and donated-team work is outside these completed runs.

### Seventh-loop transition and S1 fixture ownership

The sixth loop was integrated and pushed as `591c679b39c61f162a839cde6baaedd387135bc4`.
Its tree matches accepted `a286d527e`; the policy audit checked 14,593 entries.
Remote develop was verified at the integration SHA. All seven fully merged owned
worktrees and branches were removed after checking tracked/untracked content,
generated configuration examples, setup links and retained test identities.
The main checkout's four dirty submodules and five untracked user files remain
untouched. No parent job or cleanup obligation remains from that wave.

The seventh coordinator starts at that SHA in
`.worktrees/ai-architecture-loop7-20261024`. Its first slice changes only tests
and exact fixture accounting. Gargoyle render tests receive their recording
ObjectRenderManager through supplied ObjectServices instead of reflecting into a
session LevelManager. Their renderer also receives an explicitly constructed
GraphicsManager, avoiding the convenience constructor's process fallback. Geyser
checks use the existing named S1 fixture instead of manual engine/bootstrap and
ambient session setup. All nine test identities and assertions remain; the
lifecycle guard removes precisely those two setup keys, reaching 95 methods in
94 files. No runtime implementation, API signature or architecture permission
changes.

On the first source delta, the two classes plus TestSingletonLifecycleGuard
passed 17 distinct cases, zero failures/errors/skips. Admission took 20 seconds;
Maven reported 90 seconds. A final inspection found the renderer convenience
constructor still obtains process graphics; after supplying its own manager,
both affected Gargoyle cases passed again, zero failures/errors/skips, with 40
seconds admission and 25.033 seconds Maven execution. No test source changed
while either request was queued or running. Exact identities were read from XML;
six initial and two final XML/text reports were consumed and removed.

Both commands used the absolute current main-checkout wrapper, Java 25, Lua 5.4,
and unset DISPLAY, WAYLAND_DISPLAY, JAVA_TOOL_OPTIONS and JDK_JAVA_OPTIONS. These
are CPU argument/camera checks and do not require a native GL context or ROM:

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestSonic1GargoyleObjectInstanceRender,TestSonic1LavaGeyserOutOfRange,TestSingletonLifecycleGuard' test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestSonic1GargoyleObjectInstanceRender' test -B
```

This evidence covers the fixture change only. The end-boss launch-history slice
`c82d81f2d` has independent static review acceptance and awaits composition.
The final-boss inventory work is still running. Donated-team candidate
`fe60ed29a` is held for review corrections: sharing normal colors does not
establish identical mod-main/follower water policy, and missing donor ROMs must
abort their test invocation safely. Its original 93-case evidence does not
qualify those pending corrections. Mutable donor-color rewind is a separate
reproduction, with no runtime failure claimed yet. Parent combined validation,
integration, push and cleanup remain outstanding for the seventh loop.

### S2 special-stage overlay ownership

The remaining object-base naming allowance described a standalone ResultsScreen,
not a managed ObjectInstance. Its provider owns update/completion/presentation;
it has no object slot, spawn or collision lifecycle. Making it inherit the object
base would add a false lifecycle merely to satisfy its misleading name. Renaming
inside `objects` was also rejected because it leaves the mode overlay misplaced.
The chosen bounded migration moves the whole responsibility to existing
`game.sonic2.specialstage`: Sonic2SpecialStageResultsScreen, its mappings and its
split-name message clock helper. All three class bodies are identical after
normalizing the screen's name. Constructors, ResultsScreen entry points and
update/draw logic are unchanged. No forwarding class, service hierarchy or new
public signature is added. These concrete helpers are absent from curated API
roots and pins; implementing the unsealed ResultsScreen interface does not add
its implementations to that surface. All maintained Java callers are migrated.

One production results factory, two art loaders, seven test classes and S1's
explanatory comments now name the new locations. Only the exact stored naming
record in `09f6f5b4-f1d1-4f5b-b31a-a55517616ebe` is pruned, one to zero.
The historical rule-description key and disabled freeze updates are unchanged.
The 147 low-level records and API pins are byte-identical. This is responsibility
placement and an honest name, not an algorithm or object-lifecycle migration.

The unchanged overlay at `873e06478` passed 22 cases across its six existing test
classes, zero failures/errors/skips. Admission took 28 seconds; Maven reported
83 seconds. The first post-move attempt stopped during production compilation:
SplitNameResultsMessages had relied on same-package visibility/import resolution.
Zero tests ran; Maven reported 56.971 seconds after 44 seconds admission. Moving
that helper with its sole overlay responsibility, and migrating its two loader
imports plus unit test, corrected the complete owner boundary rather than adding
an unrelated public seam. The final source passed 64 distinct cases across nine
reports, zero failures/errors/skips: the same 22 screen cases, four existing
message-clock cases, eleven API checks and 27 ArchUnit field checks. XML confirms
the object-instance naming rule executed. Admission was ten seconds; Maven
reported 96 seconds. Twelve baseline and eighteen final XML/text reports were
inspected and removed. No source changed while a request was queued/running.

Commands used this coordinator, the absolute current main wrapper, display and
JVM-option variables unset, Java 25, Lua 5.4 and surfaceless EGL. The existing
absolute S2 World REV01 ROM was supplied, without creating links:

```bash
SCREEN_CASES='TestKis2ResultsMessageHandoff,TestSonic2ResultsFixedZeroArt,TestSonic2SpecialStageResultsPlcReadiness,TestSonic2SpecialStageResultsTallyCadence,TestSonic2SpecialStageResultsWidescreenCommands,TestSonic2SpecialStageSuperResults'
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$SCREEN_CASES" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$SCREEN_CASES,TestModApiSignatureSurface,TestArchUnitRules#object_instance_named_classes_extend_object_instance_base" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$SCREEN_CASES,TestSplitNameResultsMessages,TestModApiSignatureSurface,TestArchUnitRules#object_instance_named_classes_extend_object_instance_base" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
```

The final-boss change `c678ba49c` also has independent source-review acceptance
and is merged as `85803e286`. Its constructor-local unmanaged bootstrap retains
allocation expressions/order; managed turrets keep their deferred construction
and existing link codecs. The assigned history key alone leaves the baseline,
which is now empty. Its 58-case worker evidence remains bounded and separate;
parent composed behavior/API/S3K and full guards have not yet run on this seventh
candidate. The two accepted donor-palette changes are still unmerged at this
checkpoint. No integration, push or cleanup is claimed here.

## Donated-team palette selection owner — 2026-10-24

The isolated worker starts at `3ca14002fa2fafeabe1d3e6e370a3f559b6cba4e`
in `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-db2087fd`, on local
`bugfix/ai-donated-team-palette-owner-20261024`. Its boundary is completed
team normal/native-water selection in playable-art initialization. Registry,
allocator, colour models, water transforms, public API and frozen identities
are outside the change; no frozen-identity reduction is expected. The parent
owns composed guards, API/S3K coverage, integration and push.

The reproduced cause: `loadPlayerSpriteArt` intentionally refreshes the
shared donor row for the requested character. Built-in initialization binds
the main renderer before loading sidekick art; the prepared path loads all art
before binding. Both can therefore leave the shared row selected for the last
loaded sidekick. Separate sidekick contexts do not restore the main selection.
On unchanged runtime code, actual S2-host/S3K-donor boot reproduced both orders:
Sonic's colour 2 red was 255 instead of the ROM's 109 (`$0E66`), and Knuckles'
was 109 instead of 255 (`$064E`). The two smaller initializer controls also
reproduced the overwritten main normal row and missing distinct sidekick water
row. Sonic/Tails and duplicate Sonic passed.

The two bounded shapes compared are an explicit secondary-art loading
path that does not select the shared donor row, and completed-team selection by
the initializer before final upload. Adopt the latter: the existing initializer
owns the completed team, reselects its actual built-in donated main before
upload, and attaches each distinct donated sidekick's native-water row at its
existing context binding. This covers built-in and prepared paths, including
runtime refresh. Native scripted host art and mod-supplied palettes retain
their existing owners. `CrossGameFeatureProvider` runtime code is unchanged;
standalone character/default refresh, context identity and typed API remain.

Reject the secondary loader on source comparison, not an implemented A/B run:
it would require a new cross-package provider seam, migrate secondary callers
and still establish sidekick water binding. Re-decoding main art as a final
selection would obscure the owner and repeat observable loading. Final
selection uses only existing palette reads and does not alter art or decode
order. No registry/allocator, colour-profile, water-math, API-pin or frozen-store
change is claimed.

Reproduction controls use actual `GameplayCaptureSession` boot with S2 as host,
S3K Sonic/Knuckles in both orders, Sonic/Tails, duplicate Sonic, and S1 duplicate
Sonic including its existing native underwater row. Expected colours come from
independent supplied-ROM words, not a new selection helper. They inspect both
renderer bindings and actual normal/native-water texture rows, repeat playable
refresh, and reset/reinitialize the provider. Smaller initializer controls cover
different per-character native-water rows in built-in and prepared teams.

Hooks are installed. All three existing absolute ROMs match the documented
CRC32/SHA-1 identities. The leased version probe reported Java 25.0.5.0.2
(353 seconds admission); `/usr/bin/lua5.4 -v` reported Lua 5.4.9. All tests run
from this worker's own cwd with the following common command.
`MAIN_CHECKOUT` denotes the existing parent checkout (and its verified ROMs);
only that path is parameterized here to avoid committing a machine-local home
path. The selectors below are the executed selectors:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$SELECTOR" \
  "-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$MAIN_CHECKOUT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  test -B
```

- Red selector:
  `TestDonatedTeamPalettes,TestLevelPlayableArtInitializerModArt#donatedTeamPublishesMainNormalAndEachCharactersNativeWater`.
  Seven cases ran, five failed, no errors/skips (123 seconds admission,
  Maven 1 minute 26 seconds). Four are the reproduced defects above. The S1
  control's fifth failure was an independent-oracle mistake: REV01 Pal_Sonic
  is PalPointers entry 2 (`$2380`), not REV00's entry 3. Correct the test only;
  retain revision-aware production loading. Both XML and text report pairs
  were inspected and removed.
- Candidate selector:
  `TestDonatedTeamPalettes,TestLevelPlayableArtInitializerModArt,TestCrossGameFeatureProviderRefactor,TestPlayerSpriteRendererPaletteContext,TestGameplayCaptureDonorEntry,TestDonorTailsTailBanks,TestUnderwaterPaletteUpload`.
  **93 distinct cases passed, zero failures/errors/skips**, across eight XML
  reports (one empty outer report; its nested report also contains the outer
  provider test identities). Counts: provider 11, underwater upload 22,
  production team palette five, donor-tail banks nine, initializer 16,
  renderer palette context two, native capture entry 28. Admission took 40
  seconds; Maven took 1 minute 31 seconds. Both original swap failures now pass
  the independent ROM checks and actual GPU row reads. All five production
  cases repeat refresh and provider reset/reinit; shared-context identity and
  same-palette sharing hold. Standalone/default refresh and clearing an absent
  native-water selection also executed. The 16 consumed XML/text reports were
  inspected and removed; no test command remains live.

The tested candidate is the unchanged base above plus only the initializer and
three test files in this worker commit. Existing main/sidekick art loading order,
selection and animation translation stay at their original owners. The native
entry matrix supplies stock S3K and S1/S2 donor controls; it is bounded entry
coverage rather than full route certification. This worker stops at its local
review-ready commit, leaving the checkout and branch for parent review.

Limits: supplied-ROM palette values, completed bindings and identified GPU
uploads establish this owner correction. They do not certify full gameplay,
all donors/hosts, traces, native zone-water derivation or all colour profiles.
Distinct synthetic water rows exercise the existing per-character provider
contract; the actual native-water donor control is S1. Parent-owned composed
guards/API/S3K/integration/push remain outstanding.

### Independent-review correction in the same owner slice

Review of `fe60ed29a76aea7108c38d302f4bae92964f06fd` identified a missing
qualification: equal normal colours do not establish equal water policy. A mod
main can use S1's normal colours with host-derived water, while its donated
Sonic follower needs S1's native underwater row. The prepared initializer reused
the mod main's context, then wrote the follower's native water into it. The
reverse arrangement also reused the donated main's native-water context for a
mod follower. The original 93-case observations above remain valid for their
tested state and selection; they did not cover these mixed-policy aliases.

On unchanged `fe60ed29a` runtime, the following selector used the same common
environment, wrapper, ROM properties and `test -B` recorded above:

```text
TestLevelPlayableArtInitializerModArt#equalNormalModMainKeepsDerivedWaterBesideDonatedSonic+equalNormalModFollowerKeepsDerivedWaterBesideDonatedMain+equalNormalBuiltinSharingRequiresEqualWaterInBothRoutes,TestDonatedTeamPalettes#missingSelectedDonorAbortsBeforeAnyHostBoot
```

Eight cases executed: six failures, zero errors/skips. The initializer report
contains six cases: the supplied-S1-ROM mod-main/Sonic follower case, reverse
mod-follower case, and differing-water cases in both initialization routes
failed their alias assertions. Both equal-normal/equal-native-water sharing
controls passed. The donor-availability report contains two failures: mocked
missing S1 and S3K discovery returned null and caused `NullPointerException`
instead of aborting the affected invocation. Admission took five seconds and
Maven 27.210 seconds. Both XML/text report pairs were inspected and removed.
No user ROM file was renamed, removed or otherwise changed.

Compare two small allocation shapes: always allocate a separate context for a
donated follower, or retain sharing when normal colours and native-water policy
match. Choose the latter on source comparison, preserving equal-water sharing
and existing allocation behavior where compatible. Null means host-derived
water and differs from any native row. The existing initializer reads each
donated built-in character's native row before deciding compatibility, installs
it only on a newly allocated context, and never writes a follower's water into
a reused main context. Mod followers retain derived water; native scripted
followers retain their host binding in both routes. No new registry, API,
palette framework or colour model is introduced.

The donor test now assumes availability before converting the selected donor
file to a path. Two controls mock only discovery and catch the expected abort;
they do not turn the configured real-ROM invocations into skips or touch ROM
files. The mod-main initializer regression uses actual S1 donor normal/water
palettes and art with an S2 host module; its mocked manager boundaries make it
completed-initializer evidence, not a full installed-mod gameplay boot.

The completed affected-family selector is
`TestLevelPlayableArtInitializerModArt,TestDonatedTeamPalettes,TestGameplayCaptureDonorEntry,TestDonorTailsTailBanks`.
It passed **68 distinct cases, zero failures/errors/skips**, in four XML
reports: initializer 24, donated-team palette seven, native capture entry 28,
and donor-tail banks nine. Admission took 113 seconds; Maven took 1 minute 29
seconds. All five configured real-ROM palette invocations executed. The two
missing-donor controls passed by catching the expected abort before any host
boot; they are passing controls, not skipped ROM cases. The ROM-backed
mod-main regression, reverse mod follower, equal/different native-water cases
and native-scripted host-binding cases in both routes all executed and passed.
XML identities and absence of failures/errors/skips were inspected; the eight
consumed XML/text reports were removed. No task-owned command remains live.

The correction changes only the initializer, its two affected test files and
this bounded audit section. The prior 93-case result remains attributed to
`fe60ed29a`, not relabeled as a run of this correction. No unchanged broad
suite, provider or API selection was repeated. This is the focused affected
family recheck, with the common Java 25/Lua 5.4/EGL/verified-ROM command above.
The branch remains on the reviewed worker lineage; parent main `591c679b3` is
not imported or changed. Composed gates and delivery stay parent-owned; this
correction does not close donor-colour rewind ownership.

### Shared donor palette color rewind owner (bounded follow-up)

The isolated implementation checkout is
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-9db4cd35`, branch
`bugfix/ai-donor-palette-rewind-owner-20261024`, based on
`591c679b39c61f162a839cde6baaedd387135bc4`. This follows the read-only
RenderContext study at `3ca14002f`. The shared typed static bank API, allocation
and reset boundaries remain justified. The separate completed-team initializer
candidate `fe60ed29a` is neither merged nor modified here. No allowance decrease
is claimed.

Grounding: `GameplayModeContext.attachLevelManagers` registers the existing
`palette-colors` adapter for level and native-water colors. The active S2 super
controller resolves `SuperStateController.resolvePaletteTarget` to the donor's
normal row, writes ROM transformation colors into its existing Color cells and
immediately calls `GraphicsManager.cachePaletteTexture` for the expanded row.
Controller restore restores its RAM/scripts/physics, explicitly leaving palette
cells to the world owner. `RewindRegistry.restore` uses production registration
order and then its production callbacks. `LevelRewindSnapshotAdapter` does not
replace palettes. Thus donor CPU colors had no registered value capture owner.

The first actual-path reproduction used one S2 Sonic, no followers, on an S3K
MGZ1 host through `GameplayCaptureSession`. It loads normal expected RGB from a
separate donor-ROM palette load, identifies visible blue body pixels against a
tiles-only draw, captures the active gameplay registry, executes 24 ordinary
frames of the actual transformation, restores and redraws without a forward
tick. It does not fabricate snapshot entries or restore a renderer snapshot.
At the unchanged production base, XML identity
`com.openggf.tools.TestDonorPaletteRewind#donatedSuperWriteRestoresLiveAliasesAndPresentedPixelsWithoutForwardTick`
executed once: one failure, zero errors/skips. Its grouped assertions found
color 2 still `0x6D6D6D` instead of prior ROM `0x2424B6`, and 198 differing
previously visible blue body pixels. Palette/Color aliases and shared bank
identity survived. This converts the source-inspection risk into a reproduced
CPU and immediate-presentation defect. Admission was zero; Maven reported
78 seconds. Its XML was inspected and the consumed XML/text reports deleted.

Two complete bounded ownership shapes were compared after grounding these routes:

- Extend the existing gameplay palette color adapter. Capture populated normal
  donor rows as RGB values, rebind each row through the same live registered bank
  on restore, update Color fields in place, and immediately recache using that
  bank's current effective row. Restore donor CPU values before uploads and donor
  uploads before native-water derivation. Keep the native fade/publication owners
  and controller/profile order unchanged. An adapter-local bank lifetime token
  prevents captures from another adapter or retired/replaced bank adopting a new
  bank merely because its GameId or numeric GPU rows match; tokens and RGB values
  contain no renderer references or GPU addresses. Only currently registered banks
  remain in the local binding map. Replacing a Palette within the same live bank
  deliberately rebinds to the current Palette/Color aliases.
- Give CrossGameFeatureProvider a separate registered palette adapter. It would
  capture the active donor's normal RGB, resolve its current render context at
  restore, perform the same in-place write/immediate upload, and need equivalent
  lifetime isolation plus attach/detach and restore-order wiring. Its provider
  pointer resets at level teardown, while the shared bank registry can retain a
  bank and supplies standalone typed access. It therefore adds a second color
  ownership/registration route and does not naturally cover the registered shared
  donor banks. Rejected in favor of the existing world color owner; no alternative
  production implementation, relocation or framework was added.

The selected extension captures only shared donor normal colors. Sidekick bank
height/lifetime, private appearances, donor native-water palette authority,
completed-team initialization and cross-reset gameplay reconstruction are outside
this slice. A reset/replacement control must leave retired rows detached, avoid
recreating missing banks, and avoid recoloring a replacement bank at the same
GameId/row. Existing native surfaces keep their stock three-argument adapter path.

Commands below use the existing `MAIN_CHECKOUT` convention, resolving the actual
absolute wrapper and ROM paths without retaining a machine-local home path:

```bash
MAIN_CHECKOUT=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
```

Commands use the verified existing S2 World REV01
`$MAIN_CHECKOUT/Sonic The Hedgehog 2 (W) (REV01) [!].gen`
(CRC32 `7B905383`, SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`) and
S3K locked-on
`$MAIN_CHECKOUT/Sonic and Knuckles & Sonic 3 (W) [!].gen`
(CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`). Hooks
are installed. The red command, from this checkout with only the new reproduction
uncommitted, was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestDonorPaletteRewind' \
  "-Dsonic2.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$MAIN_CHECKOUT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The change-based planner at the pinned base selects all 3,741 smoke classes
because the internal snapshot and gameplay composition files are shared. That is
disproportionate to adding donor values to the existing color adapter and a single
constructor wiring site: native controllers, renderer bodies, public API pins,
allocation and reset boundaries are unchanged. The worker uses affected palette,
production donor-replay and presentation checks; the parent owns composed API,
mandatory S3K and complete fresh guards, integration and push. No broad engine or
release-only full-suite run was requested by this worker.

The initial green selection was the red command with the exact selector
`-Dtest=TestDonorPaletteRewind,TestPaletteColorStateAdapter,TestSonic2DonorSuperStateRewind`.
It passed 17 cases in three XML reports, zero failures/errors/skips. Admission was
31 seconds and Maven 77 seconds. This qualified the first owner extension and
the original CPU/body-pixel reproduction. The reports were inspected and removed.
A subsequent bounded refinement preserves donor registry upload order and adds
actual GPU-row readback before redraw/V-int, displayed native-fade generation,
forward replay/seek and combined native/donor/water upload-order assertions. The
final check below qualifies those final executable inputs rather than reusing
the earlier 17-case result for them.

The final affected invocation passed **45 distinct XML cases in five reports**,
zero failures/errors/skips, with the final executable sources stable throughout
submission and execution. Admission was 79 seconds; Maven reported 78 seconds.
The original failing identity now passes CPU RGB, immediate GPU-row readback,
visible body pixels, displayed native-fade generation, ordinary forward writes
and forward seek without a palette tick. Alias, same-bank row replacement,
uncaptured-row preservation, upload order, retired/replaced-bank isolation and
new-adapter isolation controls passed. The 22 water cases include all six actual
EGL fade/profile pixel parameters. This command was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestDonorPaletteRewind,TestPaletteColorStateAdapter,TestSonic2DonorSuperStateRewind,TestPaletteUploadLatch,TestUnderwaterPaletteUpload' \
  "-Dsonic2.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$MAIN_CHECKOUT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

Exact final XML identities follow (each method is qualified by its preceding
class; parameter suffixes are the XML identities). All executed, none skipped:

`com.openggf.game.palette.TestPaletteColorStateAdapter`:

```text
nullSurfacesCaptureEmptyAndRestoreNoops
captureRestoreRoundTripsBothSurfaces
keyIsStable
resetForMissingSnapshotLeavesLiveColorsUnchanged
restoreRecachesUnderwaterEvenWhenNormalSurfaceMissing
donorRestoreRebindsCurrentRowsAndPreservesTheirAliasesAndUncapturedRows
restoreDoesNotRecacheWhenGlIsNotInitialized
restoreWritesInPlaceWithoutReplacingColorObjects
donorResetAndReplacementCannotAdoptAnOldCaptureByGameOrGpuRow
restoreRecachesNormalLinesAndUnderwaterWhenGlInitialized
restoreToleratesNullGraphicsSupplierResult
snapshotDefensivelyCopiesConstructorInputs
```

`com.openggf.game.sonic2.TestSonic2DonorSuperStateRewind`:

```text
normalStateStillCapturesTheInFlightRevertPaletteAndShippedResidualByte
activeToRevertedWorldRecreationDeletesTheCurrentStarsOwnerAndRestoresMappings
normalTailsRestoreKeepsTheCharacterPhysicsProfile
midTransformationRestoresPaletteTimerDrainAndOrdinaryMapping
```

`com.openggf.graphics.TestPaletteUploadLatch`:

```text
releaseUploadsPendingLinesAndRestoresImmediateUploads
immediateScenePublicationReleasesARewindHeldFadeWithoutALevelLatch
scenesOutsideTheLevelPipelineUploadImmediatelyWhileLatched
cinematicRevealRestoresNativeFadeAndPublicationOwnershipEvenWhenDrawingFails
latchedWritesReachTheGpuAtTheNextPublication
rangeChangesDoNotPublishPendingNativeLinesBeforeVInt
```

`com.openggf.graphics.TestUnderwaterPaletteUpload`:

```text
unchangedContentReusesPreparedStaging
wholePaletteWhiteFadeAlsoReachesScopedRowsBeyondTheMaskWidth
whiteWaterPublicationKeepsRawWritersPendingAndRestoresTheDisplayedGeneration
whiteWaterCacheUsesTheSaturatedColourStepRatherThanAWrappedByte
nativeStagingBufferIsReusedAndOnlyGrows
toWhiteFadesTheDerivedWaterTargetRatherThanChangingTheTintRatio
headlessOwnerCleanupReleasesNativeStagingAndContentCache
clearingPaletteTexturesReleasesStagingAndAllowsReinitialization
inPlacePaletteMutationForcesAnotherUploadAndPreservesRgbaRules
fromWhiteFadesTheDerivedWaterTargetRatherThanChangingTheTintRatio
expansionToggleReuploadsDirectAndDerivedRowsWithoutChangingNativePaletteBytes
donorRowCacheRecomputesOnlyRowsWhoseContributingContentChanges
underwaterDonorTextureAndRenderedAppearanceKeepFadeAndProfileOrder(Mode, int, DisplayColorProfile, DisplayColorExpansion, String, String, String)[1]
underwaterDonorTextureAndRenderedAppearanceKeepFadeAndProfileOrder(Mode, int, DisplayColorProfile, DisplayColorExpansion, String, String, String)[2]
underwaterDonorTextureAndRenderedAppearanceKeepFadeAndProfileOrder(Mode, int, DisplayColorProfile, DisplayColorExpansion, String, String, String)[3]
underwaterDonorTextureAndRenderedAppearanceKeepFadeAndProfileOrder(Mode, int, DisplayColorProfile, DisplayColorExpansion, String, String, String)[4]
underwaterDonorTextureAndRenderedAppearanceKeepFadeAndProfileOrder(Mode, int, DisplayColorProfile, DisplayColorExpansion, String, String, String)[5]
underwaterDonorTextureAndRenderedAppearanceKeepFadeAndProfileOrder(Mode, int, DisplayColorProfile, DisplayColorExpansion, String, String, String)[6]
structurallyDifferentRowsCannotShareAnUploadKey
limitedExpansionFollowsFadeAndRetainsTransparentColourZero
scopedPublishedBasisTracksNativePublicationAndExactWorldLifetime
realCacheBoundaryUploadsOnceForBothConsumersAndInvalidatesContributingContent
```

`com.openggf.tools.TestDonorPaletteRewind`:

```text
donatedSuperWriteRestoresLiveAliasesAndPresentedPixelsWithoutForwardTick
```

The first green run's 17 identities are precisely the final palette-adapter,
S2-controller and reproduction groups above; the red run executed only the
reproduction identity. All ten final XML/text reports were consumed and removed;
no archive, receipt or retained capture was created. Java 25 was selected explicitly
and accepted by the build JDK gate; the selected Lua binary reports 5.4.9. Headless
capture uses the production no-device audio boot and owned EGL path.

Limits: actual donated gameplay coverage is the supported solo S2 Sonic on dry
S3K MGZ1, not S3K-as-donor or an underwater gameplay route. Water/fade/profile
coverage is the existing renderer component contract. Reset tests prove isolation,
not reconstruction across a reset or migration between gameplay owners. Sidekick
rows, native-water donor authority, completed-team initialization and texture
height concerns remain separate. There are no API-pin, annotation/exclusion,
manual owner override, frozen baseline or allowance edits. Parent-composed checks
and delivery remain outstanding; this worker stops at a local review-ready commit
without pushing or removing its branch/worktree. All worker test jobs completed.

### Seventh-loop composed acceptance

The seventh executable composition is `8b9793756b68b9518d2c4358869cb23837a8cc76`,
based on pushed main `591c679b39c61f162a839cde6baaedd387135bc4`. It contains both
accepted boss-history closures, the S1 fixture slice, the intact S2 overlay move,
donated-team correction `53ff4fa89` and donor RGB restoration `ce52bf334`.
The latter two merges touch separate production owners. Their append-only plan
histories were merged against their actual bases, retaining the earlier rejected
water-sharing shape and its correction. No production or test source changed
after submission of the following composed checks.

The planner selected 3,745 smoke classes because it classifies the snapshot DTO,
mode context, guard accounting and several renamed/comment-only paths as shared.
Inspection established the actual boundary: the existing palette adapter/key is
extended, its legacy constructor and two-array snapshot constructor remain, and
gameplay passes an additional donor-context supplier. There is no new timing,
physics, generic capture algorithm, public API pin or test-selection policy.
The owner-local boss changes and unchanged overlay bodies have focused graph and
behavior evidence. A combined consumer matrix, API/pin checks, required S3K
checks and complete fresh-JVM structural guards therefore replace the broad
selection. Tool preflight passed in the actual Java 25/Lua environment. This is
bounded validation, not all-smoke, full-suite or route certification.

The composed focused invocation passed **437 distinct cases in 45 reports**,
zero failures/errors/skips. All 43 selector names matched executed classes.
Both S3K loading classes executed, along with API pins, registry/legacy palette
consumers, both boss graphs, the results screens, S1 fixtures and the required
S3K controls. The seven donated-team cases include all five real-ROM team cases
and two missing-donor prerequisite controls; the donor-rewind case executed real
CPU/GPU/pixel checks. Admission was 68 seconds; Maven reported 94 seconds.

A separate fresh-JVM, unfiltered guards profile passed **769 distinct cases in
92 reports**, zero failures/errors/skips. Admission was 54 seconds; Maven
reported 325 seconds. Actual XML identities include all 33 ArchUnit checks,
14 exact-policy controls, eight compact-policy controls, four rewind architecture
checks and eight singleton-lifecycle checks. The generated field census and its
baseline are both empty. The lifecycle census still matches all 513 keys and
their counts; no allocation or destruction permission changed. The 90 focused
and 184 guard XML/text reports and both inspected censuses were consumed and
removed after exact identities and skip elements were checked.

Commands used this coordinator's target, existing absolute ROM paths, display
and JVM-option variables unset, Java 25 and Lua 5.4. The MAIN_CHECKOUT/ROM_ROOT
bindings above expand to the current main checkout and its existing ROMs:

```bash
FOCUSED_SEVEN='TestLbzEndBossLaunchRewind,TestLbzEndBossInstance,TestS3kLbzEndBossChildLinksRewind,TestLbzFinalBoss1Instance,TestLbzFinalBoss1GraphRewind,TestLbzFinalBoss1FieldDisposition,TestDonatedTeamPalettes,TestCrossGameFeatureProviderRefactor,TestLevelPlayableArtInitializerModArt,TestDonorPaletteRewind,TestPaletteColorStateAdapter,TestSonic2DonorSuperStateRewind,TestSonic3kSuperStateRewind,TestPaletteColorRewindRegistration,TestGameplayModeContextRewindRegistry,TestGameplayModeContextPlaybackController,TestSessionManager,TestSessionManagerReadAccessors,TestRenderContext,TestPaletteUploadLatch,TestUnderwaterPaletteUpload,TestPaletteFadePresentation,TestAppearancePalettes,TestScopedPalettePresentationRewind,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestRewindFieldDispositionAudit,TestRewindPolicyRegistry,TestS3kSuperProfiles,TestSonic1GargoyleObjectInstanceRender,TestSonic1LavaGeyserOutOfRange,TestKis2ResultsMessageHandoff,TestSonic2ResultsFixedZeroArt,TestSonic2SpecialStageResultsPlcReadiness,TestSonic2SpecialStageResultsTallyCadence,TestSonic2SpecialStageResultsWidescreenCommands,TestSonic2SpecialStageSuperResults,TestSplitNameResultsMessages'
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$FOCUSED_SEVEN" \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Pguards \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

Current allowances are 147 low-level records, zero object-base naming records,
zero earlier package-naming records, zero concrete Engine/GameLoop references,
95 ambient setup methods across 94 files and 154 top-level package permissions.
Cross-game host donation remains ten members in two grouped records. Empty
stored cycles follow explicit ignores; this is not a proof of a globally acyclic
production graph. Empty field debt establishes this audit's dispositions, not
capture/replay correctness for arbitrary routes or handwritten owners.

Donor rewind's gameplay proof remains solo S2 Sonic on dry S3K MGZ1. The
completed-team cases broaden initialization coverage, not team Super-state
rewind, underwater gameplay or every host/donor route. The overlay's tally,
readiness, mapping and command checks do not certify full special-stage routing.
These limits remain explicit despite a green guard profile.

The next components are separate: reviewed shader capacity `187e15346` has
98 focused passes and no frozen delta; reviewed staged GPU command
`ff2f79ad1` has 123 focused passes and exact component accounting
147 − 9 + 5 = 143, retaining the other 138 identities including the four SAT
adapter records. Neither is part of this seventh composition. The native
allocation-bridge study retains all ten count-one permissions: existing child
helpers also alter ownership/probe/reconstruction handling, and changing call
spelling alone removes no responsibility. Specific roles include lowest-free
independent effects, monitor/puff-source SST anchoring, fixed/auxiliary fallback
and movement bootstrap. Their native references use the main SHA's recorded
disassembly pins rather than dirty working heads. Monitor rejected admission
and boss-explosion RNG-before-admission are source-derived concerns needing
real-manager reproduction; no new native failure is claimed from that study.
The monitor concern now has a bounded implementation worker, with the ten
permissions intentionally retained.

A main fetch/fast-forward pull found no intervening upstream changes. All parent
jobs have completed. Final documentation/source-link and policy checks,
integration, push and cleanup still follow; the four dirty submodules and five
untracked user files in main remain outside this work. Shader/command, the next
S1 fixture family and monitor admission remain owned, unmerged follow-ons.

## Monitor contents admission follow-up

The isolated monitor worker at base `591c679b3` reproduced a real source concern
from the native allocation-bridge audit. Full tail occupancy left the parent
holding rejected, unregistered contents, so immediate capture failed before its
next dispatch; the embedded icon could also award rings without admitted
contents. Corrected baseline validation executed eleven graph cases with exactly
those three failures and eight passing controls, zero errors/skips. The
[field-audit continuation](../audits/2026-10-24-rewind-disposition-review.md#s3k-monitor-contents-admission)
records exact identities, commands, pinned native failure branches, rejected
shapes and scope. All ten dynamic-add bridges remain QUALIFIED RETAIN, count one
each, with no wrappers or allowance changes. Minimal parent admission ownership
keeps existing icon/callback dispatch and allocation order. The affected seven
exact classes passed 47 cases, zero failures/errors/skips, including both physical
monitor fixtures, creator content and real child-slot/occupancy/recycle controls.
Only documentation changed afterwards. Consumed XML was inspected and removed;
no live Maven handle remains. The review-ready local branch/worktree stays with
the parent, which owns composed gates, integration and push.

## Ninth-loop S2 named fixture family

Coordinator base `e9d8087aca13f8868c783a4fe001b39aa7015ca7`, branch
`bugfix/ai-architecture-loop9-20261024`; the reviewed monitor admission merge is
`97534dcb558ab5d03e85cc3888cac70ba19dd803`. This separate test-only slice replaces
manual or implicit gameplay opening with the existing
`configureGameModuleFixture(SonicGame.SONIC_2)` boundary in seven setup methods:
`TestSpringObjectInstance`, `TestSpiralObjectInstance`, `TestMonitorObjectInstance`,
`TestTodo10_MTZEventSpecs`, `TestTodo11_SCZEventSpecs`, `TestTodo12_WFZEventSpecs`,
and `TestTodo9_DEZEventSpecs`. Camera resets, construction scopes and existing
cleanup remain in their original order after fixture initialization. Spiral's
previous-module restoration is retained.

A direct service-only fixture would omit the module-owned playable and event
collaborators exercised by these tests. The existing named module fixture supplies
that ownership and central reset without another helper. All non-import source
outside the seven setup bodies is byte-identical to the parent. This preserves
87 test declarations expanding to 91 executed cases and all 271 assertion or
verification calls. Only these seven exact `#setUp` allowance keys are removed;
parallel S1 and engine-only fixture removals must be reconciled by identity.

Observed focused validation used Java 25, Lua 5.4 and the absolute main-checkout
queue wrapper from this coordinator. `MAIN_CHECKOUT` below denotes that existing
checkout, not the task target:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestSpringObjectInstance,TestSpiralObjectInstance,TestMonitorObjectInstance,TestTodo10_MTZEventSpecs,TestTodo11_SCZEventSpecs,TestTodo12_WFZEventSpecs,TestTodo9_DEZEventSpecs,TestSingletonLifecycleGuard' test -B
```

All **99 cases** passed, with zero failures, errors or skips: spring 19, spiral
11, monitor 9, MTZ 15, SCZ 9, WFZ 17, DEZ 11 and lifecycle guard 8. Exact XML
identities and the fork's Java 25/EGL properties were inspected; all 16 consumed
XML/text reports were deleted. Admission waited 72 seconds; execution duration
was not retained. These are ROM-free component checks, not full-route, native
pixel or composed ninth-loop certification. No production source, API pin,
allocation permission or test assertion changed. No task Maven handle remains.

### Engine-only fixture boundary for session tests

This bounded continuation starts at parent/development base
`e9d8087aca13f8868c783a4fe001b39aa7015ca7` on isolated branch
`engine-only-test-fixture`. It owns only the three
session test families below, their shared test fixture, the three matching
singleton-lifecycle allowances and this appendix. No production behavior or
other fixture family's allowances are part of the slice.

Direct explicit `EngineContext` construction requires nine engine services;
most have private constructors, so it would require ambient singleton lookups
or unrelated mocks without handling existing session/resource state. The base's
private `TestEnvironment.resetToBootstrapBaseline()` already owns configuration,
static resets, engine-service binding, capture of the previous module's teardown
profile, session disposal, module reset, teardown steps and post-teardown fixups,
in that order. Naming that existing prefix `resetEngineOnly()` exposes the
needed fixture boundary without another bootstrap implementation. `resetAll()`
retains its camera-bounds reset and gameplay creation after that prefix.

The no-world tests `enterEditorMode_withoutWorldSessionThrows` and
`requireCurrentGameModule_withoutWorldSessionThrows` require no session.
`installThrowsWhenRegistryNotInitialised` and `registryIsNullBeforeAttach`
require an unattached local context. Their original identities and assertions
remain intact. New controls exercise disposal of attached gameplay without a
replacement (including graphics rebinding, replay-close count, art-run shutdown,
idempotence and subsequent LIVE admission), and prior-module teardown/fixup
ordering after clearing an editor session. Existing fixture controls cover
`resetAll()` reopening gameplay and the singleton-reset extension. All 54
original test methods (58 invocations) and their assertions remain byte-for-byte
unchanged; the two new controls are
`TestSessionManager.engineOnlyResetDisposesGameplayWithoutOpeningReplacement`
and `TestSessionManager.engineOnlyResetKeepsPreviousModuleTeardownAndFixupOrder`.

The removed allowance keys are exactly:

```text
src/test/java/com/openggf/game/session/TestSessionManager.java#configureServices
src/test/java/com/openggf/game/session/TestGameplayModeContextPlaybackController.java#configureServices
src/test/java/com/openggf/game/session/TestGameplayModeContextRewindRegistry.java#configureServices
```

This is component accounting, not an integrated ambient-inventory total; the
parallel S1 fixture family owns different keys.

Validation uses Java 25.0.5.0.2 and Lua 5.4.9 with `DISPLAY`, `WAYLAND_DISPLAY`
and `XAUTHORITY` unset. Every Maven command uses the absolute main wrapper
from this worktree. The leased version probe waited 315 seconds; build-only
`test-compile` completed in 1:22. The two new controls were first run with their
reset action calling `resetAll()`; both failed at the no-world assertion with a
replacement `WorldSession`, with zero errors/skips. That rejected arm is not
committed. After using `resetEngineOnly()`, the seven-class run below passed all
76 cases with zero failures/errors/skips (15-second queue wait, 27.905-second
Maven execution). XML confirmed the exact class set and the named negative
preconditions and new controls. A separate exact `TestSingletonLifecycleGuard`
run passed all eight cases with zero failures/errors/skips (428-second queue wait,
29.637-second Maven execution); its XML was inspected. The source/test diff was
unchanged between these passing runs; subsequent edits finish this appendix only.

```bash
# Run from the task worktree; set OPENGGF_MAIN_WORKSPACE to the absolute parent checkout.
# Environment: env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
#   JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestSessionManager#engineOnlyResetDisposesGameplayWithoutOpeningReplacement+engineOnlyResetKeepsPreviousModuleTeardownAndFixupOrder' test -B
python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestSessionManager,TestGameplayModeContextPlaybackController,TestGameplayModeContextRewindRegistry,TestGameplayModeTeardownFailures,TestHeadlessStateTeardownExtension,TestSingletonResetExtension,TestEngineContext' test -B
python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestSingletonLifecycleGuard' test -B
```

The dry change-based plan against `e9d8087ac` selected 3,745 smoke candidates
because `TestEnvironment` and the guard are shared test infrastructure. That
broad run was not launched: this slice promotes an unchanged reset body and
preserves the existing `resetAll()` suffix, with the affected families and reset
consumers covered directly. These are bounded ROM-free checks, not broad smoke,
full-suite, all-guard or ROM/trace certification. Integration, push and worktree
cleanup remain parent-owned.

## Ninth-loop MGZ2 named fixture family

On coordinator `9a6aaa7bf50b322c5bb9f053f34dde39c33d2a2e`, five MGZ2 event
setup methods now use the existing named S3K fixture: `TestSonic3kMgz2BgRiseEvents`,
`TestSonic3kMgz2ChunkEvents`, `TestSonic3kMgz2CollapseEvents`,
`TestSonic3kMgz2EndBossEvents`, and `TestSonic3kMgz2QuakeEvents`. The shared fixture
owns bootstrap reset and module/session initialization; the synthetic levels,
playable setup, previous-module restoration and cleanup remain at their existing
boundaries. The explicit end-boss default-configuration reset is supplied by the
fixture's existing prefix. Direct services alone would omit the module-owned
level-event and playable collaborators these checks exercise.

Only imports and the five setup bodies changed. A normalized comparison verified
that all other source is identical, preserving 94 test methods and all 345
assertion or verification calls. Exactly their five `#setUp` keys leave the
singleton inventory. No production source, runtime algorithm, API pin, lifecycle
permission or test expectation changed.

Focused command from this coordinator, with `MAIN_CHECKOUT` denoting the existing
main checkout and `ROM_ROOT` its verified supplied-ROM directory:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestSonic3kMgz2BgRiseEvents,TestSonic3kMgz2ChunkEvents,TestSonic3kMgz2CollapseEvents,TestSonic3kMgz2EndBossEvents,TestSonic3kMgz2QuakeEvents,TestSingletonLifecycleGuard' \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

All **102 cases** passed with zero failures, errors or skips: background rise
18, chunks 3, collapse 15, end boss 38, quake 20 and exact singleton guard 8.
The S3K SHA-1 was verified as `CFBF98C36C776677290A872547AC47C53D2761D6`.
Every XML method identity matched the source declarations; Java 25, EGL and the
absolute S3K property were inspected in all six reports. The three ROM-backed
chunk cases executed. Admission took 6 seconds; Maven compilation/execution took
29.460 seconds. All 12 consumed XML/text reports were removed. This is event
fixture and ROM-data evidence, not a complete route or native GPU certification.
The final composed S3K/API/guard gates remain parent-owned.

### Data Select engine-only setup continuation

This bounded follow-up starts from accepted commit
`dfdce6eb620b918e41c9aac112b609e009b0a27e` on the renamed isolated branch
`bugfix/ai-engine-only-test-fixture-20261024`. The reviewed `resetEngineOnly()`
boundary and production code are unchanged. The four profile/presentation
`configureEngineServices` hooks keep their `@BeforeAll` cadence; the manager's
`setUp` keeps `@BeforeEach`. After import/bootstrap-call normalization, all
136 existing test bodies, annotations, helper methods and teardown code compare
identically with that base; no assertions or test identities change.

The three host profiles construct their own explicit modules/zone registries;
bootstrap no longer inherits an unrelated world. Presentation still has its
class-level `@RequiresRom(SONIC_3K)`. `RequiresRomCondition` configures the cached
ROM before the class hook and again before every method. Engine-only class reset
retires the initial world without closing the borrowed primary ROM; the original
per-method extension restores its S3K world/module. The existing S1/S2 host-ROM
`configureRomFixture` calls, local try-with-resources ROM lifetimes, and ROM
availability assumptions remain intact.

`TestS3kDataSelectManager#setUp` has no shared ROM/gameplay fixture to retain:
it configures per-test engine services/default configuration, uses temporary
saves and injected `SuccessfulAssets`, and tests donor routing with explicit
mock-audio roots. Its existing configuration reset and extra-team setting stay
in the same order after the engine-only reset. All five allowance keys close:

```text
src/test/java/com/openggf/game/sonic1/dataselect/TestS1DataSelectProfile.java#configureEngineServices
src/test/java/com/openggf/game/sonic2/dataselect/TestS2DataSelectProfile.java#configureEngineServices
src/test/java/com/openggf/game/sonic3k/dataselect/TestS3kDataSelectProfile.java#configureEngineServices
src/test/java/com/openggf/game/sonic3k/dataselect/TestS3kDataSelectPresentation.java#configureEngineServices
src/test/java/com/openggf/game/sonic3k/dataselect/TestS3kDataSelectManager.java#setUp
```

No inspected key is retained. These exact identities are independent of the
parent's seven S2 closures and the parallel branch's three S1 closures; no old
global baseline total is copied or replaced. The fixed focused selection is the
five changed classes, `TestDataSelectSessionController`, and
`TestS3kDataSelectDataLoader`, followed by the exact singleton lifecycle guard.
Existing absolute S1 REV01, S2 REV01 and locked-on S3K ROM files were verified
against the repository's CRC32/SHA-1 identities; none were created or modified.
Maven uses the current absolute main-checkout wrapper from this worktree, Java 25,
Lua 5.4.9, unset display variables and the explicit surfaceless EGL property.
The fixed frontend run completed 151 cases: 150 passed, zero failures/errors,
and one skip (44-second queue wait, 29.216-second Maven execution). XML confirms
11 S1 profile, 18 S2 profile, 20 S3K profile, 75 S3K presentation, 12 S3K manager,
10 shared controller and five S3K loader identities; all changed-class identities
match their source methods. The sole skip is
`TestS3kDataSelectPresentation#visualCapture_selectedSaveSlotShowsRightBodyRail`:
`Assumption failed: GLFW unavailable for visual regression capture`. Its legacy
hidden-GLFW capture utility does not use the EGL property; no pixel-capture pass
is claimed and no desktop fallback was attempted. Every other selected case,
including real S1/S2 host-preview and S3K asset-loader cases, executed. XML also
confirms Java 25.0.5.0.2, the EGL property and all three absolute ROM properties.
The separate exact `TestSingletonLifecycleGuard` run passed eight cases with
zero failures/errors/skips (87-second queue wait, 27.012-second Maven execution);
its fresh XML was inspected. Source/test inputs are identical between the
completed frontend/guard runs; only this appendix was finalized afterward.
Prior engine-family checks are unchanged inputs and are not repeated.

The commands below use neutral variables for the actual verified absolute
parent-checkout and ROM paths; they run from the task worktree:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS1DataSelectProfile,TestS2DataSelectProfile,TestS3kDataSelectProfile,TestS3kDataSelectPresentation,TestS3kDataSelectManager,TestDataSelectSessionController,TestS3kDataSelectDataLoader' \
  "-Dsonic1.rom.path=${OPENGGF_S1_ROM}" \
  "-Dsonic2.rom.path=${OPENGGF_S2_ROM}" \
  "-Ds3k.rom.path=${OPENGGF_S3K_ROM}" test -B
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" \
  --lean -Dmse=off '-Dtest=TestSingletonLifecycleGuard' test -B
```

This is bounded frontend/guard evidence, not broad smoke, a full-suite pass,
all-guard coverage or display-free pixel certification. Integration, push and
worktree/branch cleanup remain parent-owned.

## Bounded player-art provider candidate migration

Implementation starts at `e9d8087aca13f8868c783a4fe001b39aa7015ca7` in isolated
branch `bugfix/ai-art-provider-owner-20261024`. Independent candidate review
accepted moving both complete annotated providers from `data` to the existing
`sprites.art` owner. The initial proposal to edit the release descriptor was
rejected: ordinary unpublished regeneration retains `0.7.0`, candidate status,
empty published baselines and the existing develop/next alias topology. The
descriptor must remain byte-identical. No published compatibility obligation was
found. Old candidate binaries are deliberately retired with compulsory source
import migration and rebuilds; the same manifest range cannot distinguish them.
The [compatibility decision](../mod-api-compatibility.md#player-art-provider-candidate-migration)
and [creator migration](../../modding/getting-started.md#player-art-provider-import-migration)
record both replacements.

The bounded cut moves two definitions, updates 22 direct production consumers
and eight test consumers, and leaves inferred Stageplay factory consumers
unchanged. Native/scripted host art, configured donors, registered mod suppliers,
palette refresh/load order, Tails banks, aliases/restoration and the supplied-ROM
scene decoder/palette wrapper keep their implementation bodies. The pinned
palette method has no checked exception (despite the initial brief's shorthand);
its exact default-null declaration is preserved. Both art-loading methods retain
`throws IOException`, and `Palette`/`SpriteArtSet` remain canonical.

Component accounting is exactly 147 -> 144 frozen low-level records: remove only
PlayerSpriteArtProvider's Palette and SpriteArtSet return edges and
SpindashDustArtProvider's SpriteArtSet return edge. All other records retain byte
content and order. Remove only the `data -> sprites` top-level permission and its
matching cycle ignore; retain `data.Game`, historical rule keys/settings and every
other permission. Empty cycle results apply after remaining exclusions, not to
the global dependency graph. This component does not overwrite guide counts or
compose the parent's separate 143-record command candidate (combined prediction
140, before any later component).

Validation is focused: compile all production/test sources; regenerate the
candidate pin and require precisely 14 old lines -> 14 replacements under only
the two FQN substitutions; preserve recursive annotation cardinality; run API,
SDK, maintained sample/starter, donor/palette/scene/ghost/KiS2 and eight affected
test classes. Generate the actual canonical Javadoc inventory separately and
assert both new pages exist and both old pages do not. A fresh strict ArchUnit
run checks the exact edge/debt cut. The parent owns composed validation, the
four mandatory S3K controls, integration and push. No broad smoke/full suite,
release publication, subdelegation or worktree removal is requested here.


### Observed component evidence

All checks below ran on the pinned base plus the implementation later committed
as `40980eb496a39115e416e5ccd4060968bef453fb`, before the newline/documentation
correction recorded below. No production logic or test body changed after
compilation. `ModApiVersion` changes only its description. The task tree had no target before relocation; explicit
classfile checks after the build found exactly the two `sprites/art` definitions
and no `data` provider classfiles. No clean-build workaround was needed.
`test-compile` compiled 4,336 production and 4,444 test sources, including
maintained racing sources, successfully. Actual tool preflight passed Java 25,
Lua 5.4 and PowerShell prerequisites without executing tests.

The regenerated 22,482-line pin equals the old pin under only the two provider
FQN substitutions and sorting: exactly 14 removals/14 replacements. Recursive
type and canonical `@ModApi` inventories both remain 1,246. The original check
of all 30 direct consumer files used text-mode I/O, which implicitly normalized
newlines; it established normalized-text equality under the FQN substitutions,
not raw byte equality. Independent review established 29/30 literal byte matches
at `40980eb49`; the remaining file also lost two CRLF endings, as corrected below.
Both moved definitions retain byte-identical comments, annotations, declarations
and bodies after package/import normalization. The other 144 frozen records are
byte-identical and ordered. Release descriptor, API roots, internal terminals,
platform allowlist, frozen historical keys/settings, `data.Game` and inferred
Stageplay consumer are unchanged. No permissions were added.

| Completed focused selection | Distinct cases | Failures/errors/skips | Queue wait | Maven execution |
| --- | ---: | --- | ---: | ---: |
| API, SDK, maintained samples and starters (10 classes) | 60 | 0/0/0 | 72 s | 35.584 s |
| Art/donation/scene/ghost/KiS2 behavior (26 selectors, 27 reports) | 488 | 0/0/0 | 38 s | 2 min 39 s |
| Fresh JVM, strict TestArchUnitRules | 33 | 0/0/0 | 156 s | 34.184 s |
| Full canonical Javadoc generation | not a test run | successful page assertions | 319 s | 2.723 s |

Case identities and skip elements were inspected, including all eight affected
classes. Surefire puts CrossGameFeatureProviderRefactor's nine outer cases and
two S3kTailsDonationIntegration cases in the nested report; its empty parent
report is not evidence of execution by itself. All 11 identities were observed.
Both KiS2 checks executed all nine cases with explicit supplied-ROM paths.
S1/S2/S3K matched the documented identities; KiS2 was 3,407,872 bytes, CRC32
`2AC1E7C6`, SHA-1 `6CD0537A3AEE0E012BB86D5837DDFF9342595004`, matching
RomIdentityTable. Donor palette rewind executed its CPU/GPU/pixel case. These
are bounded consumer checks, not all routes or all-smoke coverage.

The canonical generator used its actual annotated inventory into previously
absent `target/art-provider-canonical-javadocs`: 1,248 HTML files, including
`com/openggf/sprites/art/PlayerSpriteArtProvider.html` and
`com/openggf/sprites/art/SpindashDustArtProvider.html`. Both corresponding
`com/openggf/data/` pages were absent, with no duplicate provider pages.
These assertions are separate from the SDK tests' fixture-only inventories.
The generated docs, consumed reports and invocation logs were removed.

Commands used the existing absolute wrapper
`$MAIN_CHECKOUT/tools/testing/maven_queue.py` from this
component's own cwd/target. Each Maven invocation used `-Dmse=off -B`, Java 25,
`LUA_BIN=/usr/bin/lua5.4`, and unset DISPLAY, WAYLAND_DISPLAY,
JAVA_TOOL_OPTIONS and JDK_JAVA_OPTIONS. Queue and execution intervals overlap;
their sums are not elapsed delivery time. The commands/selections were:

```bash
python3 tools/testing/run_categories.py --base e9d8087aca13f8868c783a4fe001b39aa7015ca7 --preflight
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off test-compile dependency:build-classpath -Dmdep.outputFile=target/art-provider-classpath.txt -B
API_ART='TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiJavadocTool,TestModApiSdkPackager,TestPurposeStarters,TestSampleModsPackage,TestSampleFlappyLevelSource,TestSamplePlatformerLevelSource,TestSampleRomArtRemixLevelSource'
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off "-Dtest=$API_ART" test -B
BEHAVIOR_ART='TestCrossGameFeatureProviderRefactor,TestDonorCapabilities,TestLevelPlayableArtInitializerModArt,TestNativeCarriedPlayerArtBinding,TestS3kCanonicalEntryLifecycle,TestS3kHpzSanctuaryLifecycleProduction,TestCrossGameRuleComposer,TestPlayerHeadMaskReview,TestKis2EndingContinue,TestKis2HeadlessBoot,TestDonatedTeamPalettes,TestDonorTailsTailBanks,TestPlayerHeadPresentation,TestSampleFlappyIntegration,TestSampleRomArtRemixIntegration,TestCharacterLooks,TestDonorPaletteRewind,TestStockSceneArtRom,TestDetachedStockScenePictures,TestStageplayTailsRom,TestGhostRendererLayerSelection,TestLocalPlayerLook,TestDynamicArtGhostIsolation,TestSceneRomLibrary,TestSceneArtHelpers,TestStageplayNativeBinding'
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$BEHAVIOR_ART" "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dkis2.rom.path=$ROM_ROOT/kis2.gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Pguards -Dtest=TestArchUnitRules test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off exec:exec -Dexec.executable=/usr/lib/jvm/java-25-openjdk/bin/java -Dexec.classpathScope=runtime '-Dexec.args=-cp %classpath com.openggf.tools.modsdk.ModApiJavadocTool src/main/java target/art-provider-canonical-javadocs' -B
```

Here MAIN_CHECKOUT denotes the existing absolute main checkout, and ROM_ROOT
denotes its root containing the supplied ROMs.
The snapshot was generated by the compiled ModApiSignatureSurface `--snapshot`
entry point with target/classes and the dependency classpath, then compared
against the exact baseline substitution. Remaining full/composed guard and
global-count obligations, the four required S3K controls, independent review,
integration and push belong to the parent. No live command handles remain.


### Review correction: preserve the consumer's mixed line endings

Independent code review found a P3 exactness issue in `40980eb496a39115e416e5ccd4060968bef453fb`:
TestCrossGameFeatureProviderRefactor's baseline lines 2 and 3 ended in CRLF,
and the implementation's text-mode rewrite changed both to LF. The parent
independently reproduced these two byte differences after the intended FQN
substitutions. This was an implementation/comparison error; the original 30/30
claim was too strong, and the original commit remains a 29/30 raw-byte match.

The bounded correction reconstructs only that file from the raw bytes at
`e9d8087aca13f8868c783a4fe001b39aa7015ca7`, applying only the two literal provider
FQN byte substitutions. Relative to `40980eb49`, this adds exactly two `0D`
bytes at the ends of lines 2 and 3 (corrected-file offsets 26 and 68, zero-based),
restoring the existing mixed endings without normalizing the rest of the file.
All 30 consumer files then match baseline raw bytes under only the two intended
FQN byte substitutions. Both moved definitions also pass raw-byte comparison
under their exact package/import replacements. The 14-line pin rewrite and
three-record frozen removal are unchanged, as are the release descriptor,
provider policy and all production logic/test bodies.

Validation for this correction is raw-byte comparison of all 30 consumers and
both definitions, exact pin/frozen-store comparisons, protected-file checks and
Git diff/policy checks. The completed Maven, canonical Javadoc, behavior and
fresh guard runs above are reused; none was repeated for this newline/prose-only
correction. No push, integration, delegation or worktree cleanup is included.

## Ordinary sidekick palette texture capacity

This bounded worker starts at `591c679b39c61f162a839cde6baaedd387135bc4` in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-30ea53a5`, branch
`bugfix/ai-palette-texture-capacity-owner-20261024`. It does not compose the
separate donated-team `53ff4fa89` or donor-color rewind `ce52bf334` changes.
The public static RenderContext registry owns shared row addresses and logical
reclamation; this investigation does not migrate it. Frozen-count reduction is
expected to be zero. Parent-owned API/S3K/combined guards and delivery remain
separate from this worker's focused rendering evidence.

The source-derived concern is now reproduced. The first version of
`TestPaletteTextureCapacity` ran two ROM-free methods against unchanged production
source. A production sprite-priority draw checked literal normal and underwater
colors on eight rows. Six ordinary sidekicks grew the texture to 28 rows;
clearing them and rebuilding one reduced the logical count to eight. All colors
matched before reclamation. Afterwards normal row zero expected `EE2222` but
rendered `22EE22`; the underwater assertions still passed. A second method kept
two independent GraphicsManager hosts in one EGL context: the first retained
an eight-row normal texture while the second grew the registry/texture to 28.
The first host's normal row one expected `22EE22` but rendered `EE2222`;
underwater assertions passed after its published-basis refresh. These are actual
framebuffer pixels, not shader-coordinate calculations or screenshot hashes.

Commands below use the plan's existing `MAIN_CHECKOUT` convention for the
already-resolved absolute main-checkout path requested by the coordinator.
The resource-policy hook rejects user-home prefixes in committed prose; the
executions used the current absolute wrapper, with selectors/flags unchanged.

Exact first red command (Java 25; Lua 5.4; no ROM needed):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestPaletteTextureCapacity' test -B
```

Session `81980` completed with two failures, zero errors/skips. Exact identities:
`reclaimedSidekickBanksKeepNormalAndUnderwaterRowsAtTheirLiteralColors` and
`independentHostsKeepTheirOwnRowsWhenTheSharedRegistryGrowsAndShrinks`, both in
`com.openggf.graphics.TestPaletteTextureCapacity`. Admission took eight seconds;
Maven execution reported 1:19. XML identities/assertions were inspected and the
consumed reports deleted. The fixture uses the existing display-free EGL owner,
is Linux-gated, and fails on EGL initialization failure without desktop fallback.

Two caller-complete owner shapes were compared:

- **Physical capacity belongs to the sampled texture.** Built-in GLSL can obtain
  each bound sampler's height with `textureSize`, independently for normal and
  underwater rows. This naturally covers distinct hosts, standalone textures,
  deferred commands and a shared registry that changes without an upload to
  every host. No CPU query, texture resize, new global or palette representation
  is needed; the existing normalized sampling/filter/clamp semantics remain.
- **Synchronize physical size with the logical count.** Resizing the normal
  texture on both growth and shrink at upload time would fix the single-host
  upload case but fails the independently retained host case. A complete version
  needs reconciliation at every draw boundary and for both textures, copying
  displayed bytes rather than admitting pending palette/fade writers. It adds
  texture mutation/readback, cache invalidation and GL-state obligations to all
  consumers. The independent-host pixel failure rejects upload-only resizing;
  draw-time synchronized resizing is unnecessarily invasive for this defect.

The selected shape is per-sampler physical capacity. The shared logical count
and sidekick reclamation semantics remain unchanged, with no process high-water.
Normal and underwater textures may keep different physical heights; each lookup
must use its own denominator. The complete built-in consumer family comprises
hedgehog, water, scalar/instanced priority, tilemap and both slot shaders. Palette
composition, fade/profile conversion, publication and cache/lifetime owners
remain responsible for the same bytes and upload timing.

The expanded red selection used the same command, base production source and
class, adding ten parameter identities in this order: `DIRECT`, `BATCHED`,
`INSTANCED`, `WATER`, `INSTANCED_WATER`, `PRIORITY`, `INSTANCED_PRIORITY`,
`TILEMAP`, `CNZ_SLOTS`, `S3K_SLOTS`. Session `81291` ran 11 cases with 11
failures, zero errors/skips, after 59 seconds admission and 27.681 seconds Maven
execution. Two were fixture errors before reclamation: the legacy batcher reads
the background-water binding flag at execution, and a packed tile descriptor
with alpha zero is intentionally discarded. Executing each binding separately
and marking the descriptor valid corrected only the fixture. Session `49116`
then ran the identical selection against unchanged production: all ten paths
matched their normal/underwater literal colors before reclamation and failed
afterwards with normal row zero `EE2222` → `22EE22`. The independent-host method
failed at its original registry-growth assertion. All 11 cases failed, zero
errors/skips; admission was 17 seconds and Maven execution 26.842 seconds.
The XML parameter identities `[1]` through `[10]`, complete failure messages and
zero skips were inspected; both expanded-run report sets were consumed/deleted.

Implementation changes only the seven GLSL palette samplers. `textureSize` is
available in their existing GLSL 4.10 contract. Java upload, cache, reset, palette
bank and GL-state code is unchanged. The public ShaderProgram count setter and
host writes remain available for supplied/custom shader programs; built-in
lookups no longer use that logical count as physical storage size. Both normal
and underwater slots can receive a standalone texture. All row addressing keeps
the existing normalized filtering/clamping, transparency, water split and color
processing. This is owner correctness with zero allowance removals, not a type
or API migration.

`python3 tools/testing/run_categories.py --base 591c679b39c61f162a839cde6baaedd387135bc4`
was inspected after the shader edit. The unclassified GLSL resource paths select
all 3,741 smoke classes and guards. This worker instead uses the bounded focused
selection below: every changed built-in shader is drawn with literal expected
pixels, and its surrounding publication/composition/reset consumers are checked.
No Java, build, selector, physics, API pin or runtime color-composition policy
changes. Parent-owned combined API/S3K/guards and integration remain outstanding;
neither broad smoke nor release-only full-suite coverage is claimed here.

Exact focused green-candidate command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestPaletteTextureCapacity,TestUnderwaterPaletteUpload,TestPaletteUploadLatch,TestAppearancePalettes,TestScopedPaletteGraphicsAccess,TestRenderContext,TestSpritePresentation,TestPaletteFadePresentation,TestScopedPalettePresentationRewind' \
  '-Dsonic1.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-30ea53a5/Sonic The Hedgehog (W) (REV01) [!].gen' \
  '-Dsonic2.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-30ea53a5/Sonic The Hedgehog 2 (W) (REV01) [!].gen' \
  '-Ds3k.rom.path=/mnt/LinuxData/T3/worktrees/OpenGGF/t3-30ea53a5/Sonic and Knuckles & Sonic 3 (W) [!].gen' \
  test -B
```

These are existing root ROMs, read in place: S1 CRC32 `AFE05EEE`, SHA-1
`69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`; S2 `7B905383`,
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`; S3K `63522553`,
`CFBF98C36C776677290A872547AC47C53D2761D6`. Hashes were verified for this run.
No ROM copy, rename or new link was used. Rendering is Linux EGL evidence;
macOS/Windows drivers, arbitrary custom shader behavior, native slot face art
and full gameplay routes are outside these checks.

The focused command completed green in session `82376`: 98 distinct cases,
zero failures/errors/skips across nine XML reports. Admission took 161 seconds;
Maven execution took 26.361 seconds. Tested source is base `591c679b3` plus this
seven-shader correction and the final test fixture; subsequent edits are prose
only. All XML case identities were inspected for duplicates and skipped/error
nodes before consuming the reports.

| Executed class | Cases |
| --- | ---: |
| TestAppearancePalettes | 5 |
| TestPaletteFadePresentation | 5 |
| TestPaletteTextureCapacity | 11 |
| TestPaletteUploadLatch | 6 |
| TestRenderContext | 29 |
| TestScopedPaletteGraphicsAccess | 3 |
| TestSpritePresentation | 14 |
| TestUnderwaterPaletteUpload | 22 |
| TestScopedPalettePresentationRewind | 3 |

The exact eleven formerly red capacity identities now pass, including all ten
ordered parameters above and the independent-host method. They check the same
literal normal/underwater colors before reclamation, after fewer banks are
rebuilt, and after palette texture deletion/recreation. Slots additionally check
blend/depth, active texture and program restoration. The independent-host case
now reaches both hosts' post-reclamation assertions. No height high-water or
changed logical count was used to make those pixels pass.

Additional executed pixel identities include
`underwaterDonorTextureAndRenderedAppearanceKeepFadeAndProfileOrder` parameters
`[1]`–`[6]` in TestUnderwaterPaletteUpload (literal colors for raw/analog,
full/limited and black/white fades), and all three
TestScopedPalettePresentationRewind methods:
`capturedNativeRowsSurviveTablesRestoreAndBankChangesAfterDeferredSubmission`,
`priorityWaterKeepsScopedRomRowsAndPublishedHostRowsAcrossDeferredRelocation`,
`lateScopedReallocationKeepsPriorityWaterBindingsForLaterDrySprites`. The
remaining executed classes cover logical row reuse/growth, publication latches,
appearance composition, upload cache invalidation and resource resets. This is
bounded component/consumer evidence, not a full-suite, route or all-platform pass.
No queued/running worker request remains. All consumed Surefire diagnostics are
deleted; no pass cache, archive or receipt is retained. Frozen baseline and API
pins are unchanged. Hooks and final staged whitespace/policy checks are part of
the local commit; integration, push and checkout cleanup remain parent-owned.

### Bounded PatternRenderCommand scalar ingress (2026-10-24)

This worker is grounded at `591c679b39c61f162a839cde6baaedd387135bc4` in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-add1a2e8`, on local branch
`bugfix/ai-pattern-command-scalar-boundary-20261024`. Its production scope is
`PatternRenderCommand` and four `GraphicsManager` callers plus one private
adapter. Parent aggregate counts, guides, changelog, API pins, interfaces and
combined delivery obligations are outside this cut.

The command is neither `@ModApi`, a curated/pinned type nor an internal terminal.
The pinned, unsealed `GLCommandable` interface does not support every concrete
implementation. All supported typed `GraphicsManager` ingress and `PatternDesc`
remain intact. The three internal typed command factories and private typed
initializer are deleted, including the unused no-manager factory's ambient
`GameServices.graphics()` lookup. The maintained direct-factory tests migrate to
the scalar stages or actual manager ingress; all existing test method identities
remain.

The selected shape deliberately preserves observable initialization boundaries:

1. Package-private `obtain(Entry,int,GraphicsManager)` polls the pool, validates
   the manager, resets presentation fields, and copies texture/entry state.
2. The manager adapter calls `capturePalette(desc.getPaletteIndex())`. That stage
   stores the full cached palette integer, samples presentation replay, then calls
   the existing virtual underwater getter.
3. Only after that getter returns does the adapter read cached H flip, V flip
   and priority and call `finish(boolean,boolean,boolean,float,float,float,float)`.
   Finish captures mask and ghost state, copies X/width/height, leases the command,
   then resolves display height and computes float Y in the original order.

A null descriptor or underwater failure still consumes the acquired pool entry
without registering it or acquiring a lease. Later fields retain their previous
recycled values on that failure. A display-height failure occurs after leasing
and geometry writes, so the retained command can be discarded. Deferred palette
replay still runs at execution and recycles on an exception. These are observed
controls, including winning exception identity, rather than inferred cleanup
improvements.

One-shot eager scalar reads were rejected. A temporary negative control moved
flip/priority reads before replay/underwater sampling in the original initializer.
Both discriminating tests went red: the successful callback's descriptor mutation
was lost (`true` expected, `false` observed), and an underwater throw left a newly
written flip instead of recycled state (`false` expected, `true` observed). The
negative control is not retained. Whole commands are also not replaced with
`obtainRows(0,8)`: the whole-SAT control at `Integer.MIN_VALUE` proves the original
float conversion/subtraction differs from row-path integer overflow.

Byte comparisons against the grounding commit retain `obtainRows` and its UV
resolver, the command's entire replay/execution/presentation-merging/recycling/
teardown tail, `prepareReplayDesc`, `PatternDesc`, and `SpritePresentation`.
Batch admission, retries, flushing and SAT preparation are unchanged. Preparation
controls observe the actual shared descriptor identity, raw palette bits three
with cached palette seven, synchronous mutation, decoder-returned overrides and
decoder exceptions before tile append. All four SAT scratch dependencies remain.

The exact reviewed frozen resource is
`src/test/resources/archunit/frozen/40afa018-d450-4b84-8cb9-d539d75d8062`.
For the following identity accounting, `PRC` is
`com.openggf.graphics.PatternRenderCommand`, `GM` is
`com.openggf.graphics.GraphicsManager`, `PD` is `com.openggf.level.PatternDesc`,
and `Entry` is `com.openggf.graphics.PatternAtlas$Entry`:

| Removed owner/member | Exact dependency records | Count |
| --- | --- | ---: |
| `PRC.obtain(Entry,int,PD,int,int)` | PD parameter; call to `com.openggf.game.GameServices.graphics()` | 2 |
| `PRC.obtain(Entry,int,PD,int,int,GM)` | PD parameter | 1 |
| `PRC.obtain(Entry,int,PD,float,float,float,float,GM)` | PD parameter | 1 |
| `PRC.init(Entry,int,PD,float,float,float,float,GM)` | PD parameter; calls to `PD.getPaletteIndex()`, `PD.getHFlip()`, `PD.getVFlip()`, `PD.getPriority()` | 5 |

The five replacement identities belong solely to private
`GM.obtainPatternCommand(Entry,int,PD,float,float,float,float)`: its PD parameter
and those same four cached getters. No other allowance is added or renamed.
The measured audit is **147 - 9 + 5 = 143**, with every other 138 record retained
byte-for-byte and in its original relative order. In particular, the constructor
call to `PD.<init>()`, `GM.reusableReplayDesc` field type, and
`GM.prepareReplayDesc(SpriteSatEntry,int,boolean,boolean,int)` calls to
`PD.set(int)` and `PD.setPaletteIndex(int)` remain. No automatic refreeze or
store-update flag was used. After removing incidental file-order noise, the
committed store's identity set was compared with the resource actually tested;
they are identical.

All Maven invocations used the existing absolute main-checkout wrapper from the
worker cwd, Java 25 and Lua 5.4, with desktop display variables and injected JVM
option variables unset. The leased `-v` probe reported Java `25.0.5.0.2` after
184 seconds of admission waiting. The ROM files below already existed; their
CRC32/SHA-1 matched the repository's REV01 S1/S2 and locked-on S3&K identities.
No links or copies were created. The following spells out the common command
flags and the actual selections (the final tests are ROM-free rendering and
architecture controls, not ROM-route certification):

```bash
MAIN_CHECKOUT=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
pattern_check() {
  env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
    JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
    python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
    -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
    "-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen" \
    "-Dsonic2.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
    "-Ds3k.rom.path=$MAIN_CHECKOUT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=$1" test -B
}
PATTERN_FOCUSED='TestGraphicsManagerReinit,TestPooledRenderCommandDiscard,TestRenderProjection,TestGraphicsManagerPatternAtlasPageOrdering,TestGraphicsManagerSpriteSatReplay,TestSpriteSatReplayPixels,TestSpritePreparationSnapshots,TestSatReplayBatching,TestPresentationPatternBatching'
# Original production, added characterization tests:
pattern_check "$PATTERN_FOCUSED"
# Corrected test fixtures, production still unchanged:
pattern_check 'TestGraphicsManagerPatternAtlasPageOrdering,TestRenderProjection'
# Temporary eager-read negative control only:
pattern_check 'TestGraphicsManagerPatternAtlasPageOrdering#underwaterSamplingSeesAcquiredPrefixAndPaletteBeforeMutatingLaterAttributes+nullDescriptorAndUnderwaterThrowConsumePoolWithoutFinishingOrRegistering'
# Final staged implementation:
pattern_check "$PATTERN_FOCUSED,TestArchUnitRules#low_level_layers_do_not_depend_on_runtime_layers"
```

The first run completed 96 cases, with 13 fixture errors and zero skips: the
page-ordering reflection helper incorrectly sought renderer fields on the manager
class (12 setup errors), and the projection fixture used an ungoverned virtual
ID (one error). Fixing those fixtures and rerunning just their two classes passed
16/16, zero skips; production remained unchanged. The initial run independently
passed both actual EGL pixel methods and all 49 preparation snapshot cases.
The temporary eager-read run completed two cases, both expected assertion
failures, zero errors/skips. Their exact identities are the two method names in
the negative command above. That run's source was replaced by the staged design.

The final command passed **123 cases, zero failures/errors/skips**, completed at
`2026-10-24T06:47:15+01:00`. XML established 96 renderer cases in the nine selected
classes (12, 2, 6, 8, 8, 4, 5, 49, 2 in class-name order) plus 27 ArchUnit rules.
The ArchUnit engine expanded the requested single-field selector; it did not
execute only one rule. Its XML includes the low-level frozen rule, package
cycles, singleton boundaries, game naming and shared/per-game dependency rules.
This is not the combined guards profile or the parent's API/S3K selection.
Reported admission waits for the four test commands were 0, 60, 49 and 48 seconds;
Maven execution times were about 83, 25, 81 and 92 seconds respectively. These
intervals are not a measured total delivery duration.

The final Java/source diff SHA-256 (`git diff --binary -- src/main src/test/java`
against the grounding commit) is
`22785b4034b55283f08db8ea53a9ad67e85b7568332988abc498fb9f2cf3a5ce`.
The sorted low-level identity-set SHA-256 is
`ab8156d3600b26b2991ad2d3fe0ec2bfdfd43b7cc218235f7e78cc3ab2ec4396`.
Source stayed stable during compilation and execution. Pixel controls clear and
read the actual EGL target, exercise all flip/priority combinations, cached
palette seven, explicit atlas IDs, fractional scaling, strip UVs, direct and
instanced SAT clipping and native masks. CPU controls retain framebuffer-height
switching, physical page order, full-pool prefix/throw state, deferred replay
exceptions, the existing 600-drop identities and repeated manager reinitialization.
No full route, cross-platform GL, exhaustive float-space, performance or complete
smoke/guard/API certification is claimed.

Every run's XML identities, failures and skips were inspected and the consumed
Surefire diagnostics removed. No category run or live Maven request remains.
Hooks are installed; this worker stops at its local review commit. Combined
guards/API/S3K validation, aggregate documentation reconciliation, integration,
push and worktree cleanup remain parent-owned; this worker neither pushes nor
removes its branch/worktree.

### Sonic 1 moving-block and badnik fixture slice

This test-only slice starts at `591c679b39c61f162a839cde6baaedd387135bc4` in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-5744aa69`, local branch
`bugfix/ai-s1-badnik-test-fixtures-20261024`. It removes only these three ambient
setup allowance keys; the scanner and all other allowances remain unchanged:

```text
src/test/java/com/openggf/game/sonic1/objects/TestSonic1MovingBlockObjectInstance.java#setUp
src/test/java/com/openggf/game/sonic1/objects/badniks/TestBuzzBomberLifecycle.java#setUp
src/test/java/com/openggf/game/sonic1/objects/badniks/TestSonic1CaterkillerBodyChaining.java#setUp
```

Direct construction and the existing named module fixture were compared against
each subject's dependencies. Moving block already injects its object services
and real solid-execution registry, but `TestPlayableSprite` inherits engine
services from `AbstractSprite` and resolves its physics profile and collision
rules from the current module in `AbstractPlayableSprite`. Its two real-player
checkpoint/carry cases therefore use
`TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_1)`, retaining the
real object manager and S1 oscillator reset. Teardown clears the session,
bootstrap module, oscillation table and static camera bounds.

Buzz Bomber's existing synthetic flying/static objects exercise the real object
manager's placement and persistence boundary, without player/module state.
Caterkiller's body-state cases likewise need no session; its head-destruction
case still uses the real manager's VBlank counter and production S1 destruction
path, including replacement-explosion slot inheritance. Both classes now create
an independent default configuration and real camera, inject `StubObjectServices`,
and use the explicit eight-argument manager constructor. This avoids both the
no-argument camera's `GameServices.configuration()` lookup and the convenience
manager constructor's ambient service selection. Caterkiller's five unused
`GameServices.level()` lookups are removed. Both classes reset static camera
bounds, including vertical wrapping, before and after each case. A named fixture
for these two classes would add session/module ownership their subjects do not
consume; a mocked manager would remove the boundary being tested.

Source comparison preserved all 11 existing test identities, all 39 assertion
statements verbatim, and the test-body tokens/timing after accounting for the
exact fixture substitutions. No production code, helper framework, new tests,
expected behavior or other guard key changed. No other-game dependency required
retaining one of the three allowances. The parent's separate Geyser/Gargoyle
removals are absent from this base and are not restored or edited here; this
slice's composition delta is exactly three keys.

The queued version probe reported Java 25.0.5.0.2. The following focused command
passed **19 distinct cases in four XML reports**, with zero failures, errors or
skips: moving block 2, Buzz Bomber 3, Caterkiller 6, exact lifecycle guard 8.
Admission took seven seconds; Maven reported 92 seconds including compilation.

```bash
MAIN_CHECKOUT=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  --lean -Dmse=off \
  '-Dtest=TestSonic1MovingBlockObjectInstance,TestBuzzBomberLifecycle,TestSonic1CaterkillerBodyChaining,TestSingletonLifecycleGuard' \
  test -B
```

The Java sources stayed unchanged from queue submission through completion.
Every XML class/method identity was matched to its source; all eight consumed
XML/text reports were removed, and diagnostics/trace-report directories were
empty. These checks require neither ROMs nor GL and establish only the existing
object/manager cases and exact guard, not native Buzz Bomber flight, full-route
coverage, API/S3K checks or the combined guards run. Combined validation,
integration and push remain parent-owned; this slice stops at a local commit.

### Renderer scalar ingress (bounded ninth-cut implementation, 2026-10-24)

Worker branch `bugfix/ai-renderer-scalar-ingress-20261024` in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-b0bf14b1` fast-forward merged the requested
accepted eighth code `3e0811d71113a7c03a3a3b75a15acd848d1bb242` from its automatic
`e9d8087aca13f8868c783a4fe001b39aa7015ca7` checkout before work. The main workspace,
submodules and unrelated links were not changed. This worker owns a local commit
only; guide/changelog composition, integration, push and worktree cleanup belong
to the parent.

Both GPU renderers are outside the recursive creator API. The five existing
public typed GraphicsManager declarations and their pins remain unchanged.
Descriptor interpretation now belongs to five private GraphicsManager adapters.
Each adapter retains the original renderer receiver for that attempt, admits
first, reads cached flags, then appends once. Palette sampling remains at all ten
original call sites: ordinary three, strip four, shadow two and full SAT one.
Renderers consume the atlas entry and primitives, with no new per-tile allocation,
descriptor duplication, erasure, callback wrapper or public facade.

Instanced admission preserves inactive/capacity/page checks followed by the
priority-shader, water-shader, underwater, ghost-active, colour and alpha getter
sequence and its short circuits. Append does not repeat admission; its conditional
mask getter remains after cached flags, and priority true still writes zero.
Batched admission preserves entry-null precedence after active/capacity rejection;
its manager state remains captured at endBatch. Shadow reads neither palette nor
priority. Whole, strip, shadow and dedicated SAT flush/retry/fallback/cancel tails
stay independent, including retained batched-strip palettes and resampled
instanced retry palettes. Integer/float geometry order, strip pixel centres and
unclamped indices, and cached display heights are retained.

The unused no-argument constructors and Batched singleton field/accessors are
removed together. Explicit constructor bodies are byte-for-byte unchanged:
manager-null before config-null, height before arrays, and instanced debug after
arrays. No ambient lookup was moved to another helper. There was no stale exact
singleton allowance to remove; the graphics source-scan inventory remains intact.

Exact frozen delta in `40afa018-d450-4b84-8cb9-d539d75d8062`:

- Remove Batched `addPattern(Entry,int,PatternDesc,int,int)`,
  `addStripPattern(Entry,int,PatternDesc,int,int,int)` and
  `addShadowPattern(Entry,PatternDesc,int,int)`: each descriptor parameter plus
  getHFlip/getVFlip, nine records.
- Remove Instanced `addPattern(Entry,int,PatternDesc,int,int)` and
  `addStripPattern(Entry,int,PatternDesc,int,int,int)`: each descriptor parameter
  plus getHFlip/getVFlip/getPriority, eight records.
- Remove both no-argument constructors' GameServices.graphics/configuration calls,
  four records.
- Add GraphicsManager `tryAddBatchedPattern`, `tryAddBatchedStrip` and
  `tryAddBatchedShadow` with the corresponding former Batched signatures: each
  descriptor parameter plus getHFlip/getVFlip, nine replacement records. Add
  `tryAddInstancedPattern` and `tryAddInstancedStrip` with the corresponding former
  Instanced signatures: each descriptor parameter plus
  getHFlip/getVFlip/getPriority, eight replacement records. `Entry` above denotes
  `com.openggf.graphics.PatternAtlas$Entry`, and `PatternDesc` denotes
  `com.openggf.level.PatternDesc`.

Thus 143 - 21 + 17 = 139. The other 122 records retain exact bytes and order,
SHA-256 `534e90402270023d23adf6e2e86d887f9677fa9d71e1d1aaa3042bcaf6677864`.
Historical rule keys and read-only freeze settings are unchanged. Rows method
bodies, staged PatternRenderCommand, replay descriptor decoder and its four
identities, PreparedPiece, background, RenderContext, shader capacity, API version,
signature pins and terminal policy are unchanged. Only obsolete method links in
renderer/SAT comments were repaired around those closed bodies.

Before production edits, 12 new CPU characterization cases and all three EGL
pixel cases passed: 15 tests, zero failures/errors/skips. Two initial test oracles
were corrected before this pass: atlas coordinates include the existing
0.01-pixel inset, and SAT entry resets the global mask to 0xF. No production
behavior was changed to satisfy those assertions. Pixel checks clear and read the
owned target, assert actual legacy/instanced/shadow command types, and use literal
quadrant, two-row strip and half-intensity shadow expectations. CPU checks cover
cached/raw disagreement, palette seven and unrestricted palette arguments,
conditional/throwing getters, rejected admission, untouched arrays/counts/queues,
4096/4097 capacity, old/new palette sampling, fresh rejection, shadow throw,
physical page order (existing 0 -> 1 -> 0 fixtures), SAT scratch mutation and
interrupted replay.

An initial scalar candidate passed 111 cases across eleven focused classes.
Deliberate negative controls then made whole-tile flag reads eager and strip
append revalidate admission: three selected tests produced exactly two failures
(mask sequence changed and admission duplicated) and one null-descriptor error,
with no skips. The mutations were restored. Self-review found that re-reading the
manager's renderer field after a callback could change the receiver. A thirteenth
CPU test reproduced that scalar-candidate bug as a null append receiver; retaining
one receiver per attempt fixes the Java dispatch boundary without retaining it
across retries. The initial 111-case pass does not certify that final adjustment.

Final source passed **112 distinct cases in eleven XML reports**, zero failures,
errors or skips, including all thirteen CPU ingress cases and all three actual EGL
cases. Maven reported 76 seconds including compilation, separately from queue
waiting. This final run supersedes the earlier 111-case candidate. The fresh
strict API/ArchUnit/accounting run completed as described below. No broad smoke or full suite is submitted by
this worker. The change-based plan selected 2,932 smoke classes; bounded focused
coverage is used under the explicit task instruction. Actual-environment category
preflight passed Java/Lua/PowerShell prerequisites without executing tests.

All Maven invocations use the current absolute queue wrapper from the intended
worktree, with Java 25, Lua 5.4 and no DISPLAY, WAYLAND_DISPLAY, JAVA_TOOL_OPTIONS
or JDK_JAVA_OPTIONS. EGL remains explicitly selected; these synthetic component
checks need no ROM paths. The parent's eighth smoke covers its earlier source and
does not cover this cut. The worker's inherited architecture allowance prose is
147; the parent's separate accounting edits are 143. Parent-owned prose must be
composed to 139 before claiming a green combined documentation/accounting gate.


Reproduction commands (from this worktree):

```bash
MAIN_CHECKOUT=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --base 3e0811d71113a7c03a3a3b75a15acd848d1bb242 --preflight

FOCUSED='TestPatternRendererIngress,TestGraphicsManagerPatternAtlasPageOrdering,TestPooledRenderCommandDiscard,TestPresentationPatternBatching,TestRenderProjection,TestSatReplayBatching,TestShadowBatchAtlasPages,TestGraphicsManagerReinit,TestGraphicsManagerSpriteSatReplay,TestSpritePreparationSnapshots,TestSpriteSatReplayPixels'
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$FOCUSED" test -B

GUARDS='TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestArchUnitRules,TestArchitecturalSourceGuard,TestArchitecturalReviewGuard,TestProductionSingletonClosureGuard,TestSingletonLifecycleGuard'
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$GUARDS" test -B
```

The pre-production characterization selector was
`TestPatternRendererIngress,TestSpriteSatReplayPixels`. The deliberate negative
selector was `TestPatternRendererIngress#admissionCallbacksPrecedeCachedReadsAndAppendDoesNotRevalidate+everyInstancedRejectionShortCircuitsNullDescriptorAndLaterGetters+lowPriorityAloneSamplesTheAlreadyMappedGlobalMaskAfterAdmission`.
The additional receiver red control selected
`TestPatternRendererIngress#admissionKeepsTheOriginalReceiverWhenManagerCallbackReplacesItsField`.
All use the same queued environment above, without broad profiles.

The final Java/source diff SHA-256 (`git diff --binary` from the pinned base,
restricted to `src/main` and `src/test/java`) is
`49c755a02747c5efd75a8cdefe62fd8f990fb4b01e916fadc5bda4691ec7ef64`.
The sorted 139-record identity-set SHA-256 (source locations removed) is
`5329f1c982fe20f7345514ee1b5ac68c912936fd1ac46ee278c4a3bdcc939e30`.
No source changed between final focused submission and completion. Documentation
accounting composition remains parent-owned; these hashes identify this worker's
code and reviewed store, not the parent's running smoke.


The fresh `-Pguards` selection completed **199 distinct cases in eight XML
reports: 198 passed, one failure, zero errors/skips**. Maven reported 41 seconds
including preparation, separately from queue waiting. Passing reports were API
signature 11, API pin policy 4, API release policy 15, all ArchUnit rules 33,
architectural source guard 77, production singleton closure 42, and singleton
lifecycle 8. Eight of nine architectural review cases passed, including the
read-only freeze and grouped-record accounting controls. The sole failure is
`TestArchitecturalReviewGuard.archUnitPublishedBaselineCountsMatchFrozenStore`:
actual source store 139 versus inherited documentation 147. Other documented
rule counts match. The base already carried store 143 beside prose 147; that is
static baseline evidence, not a separately executed baseline guard result.
The parent's independent prose is at 143 and must be composed to 139. This worker
leaves that parent-owned artifact unchanged and does not claim a green combined
accounting gate; parent composition and a mirror recheck are required before
integration. No new API permission, signature pin, terminal or frozen-rule setting
was needed.

The final source fingerprint stayed unchanged through focused and guard execution.
All XML class/method identities, failures and skips were inspected. Consumed test
reports, temporary mutation snapshots and task-owned diagnostic logs were removed;
no category run or live worker Maven handle remains. Hooks are installed. The
worker stops at its local commit, without push, integration, branch deletion or
worktree removal. Coverage is bounded component/renderer behavior on Linux EGL;
no broad smoke, full suite, cross-platform GL, native gameplay/trace parity,
performance or exhaustive arithmetic claim is made.

### Ten-key direct-manager and engine-only continuation

This slice fast-forwarded the clean existing task branch normally from
`1cb7d3570b965cab6de095fa3bf0982cf34f608f` to parent
`b888984f8758863a419b5b5e4f660ce2a5e68ae4`, after verifying no owned Maven jobs.
The updated parent includes its seven S2 and five MGZ setup closures. This slice
owns only the ten test classes below, their exact lifecycle allowances and this
appendix; the reviewed reset helper and production code are unchanged.

Timer error reporting and both terrain tests now own real manager instances.
Terrain result ordering also uses `resetEngineOnly()` for its dummy playable's
engine configuration. The other seven setups use that reviewed boundary;
provider lifetime keeps `@BeforeAll`, with the remaining hooks at `@BeforeEach`.
The mutation pipeline begins without a world. Resolver tests retain their own
S3K world creation, manager attachment, team selection and big-ring return setup.
Every existing teardown and helper remains unchanged, including scoped logger
cleanup and the original session/configuration cleanup paths.

Exact source comparison against the pinned parent preserves all 50 method
identities and assertions. Forty-eight test bodies are byte-identical. The only
two body edits replace the terrain-session locator with the locally owned manager:
`TestTerrainCollisionManager.testSensorResultOrdering` calls
`manager.getSensorResult(sensors)`, and
`TestTerrainCollisionManagerReset.resetStateClearsPooledResults` aliases
`manager` as its existing `tcm` variable. Sensor scans, pooled-result assertions,
timer subjects/ticks and all expected values remain intact.

The removed allowance keys are exactly:

```text
src/test/java/com/openggf/tests/TestTodo30_TimerErrorReporting.java#setUp
src/test/java/com/openggf/physics/TestTerrainCollisionManagerReset.java#setUp
src/test/java/com/openggf/physics/TestTerrainCollisionManager.java#setUp
src/test/java/com/openggf/game/TestZoneLayoutMutationPipeline.java#setUp
src/test/java/com/openggf/game/sonic2/TestSonic2PlcArtRewindSnapshot.java#setUp
src/test/java/com/openggf/level/rings/TestRingManagerRewindSnapshot.java#setUp
src/test/java/com/openggf/game/TestGameModuleProviderLifetimes.java#configureEngineServices
src/test/java/com/openggf/sprites/managers/TestSpriteManagerUpdateOrder.java#configureRuntime
src/test/java/com/openggf/sprites/playable/TestOnObjectAtFrameStartSnapshot.java#configureRuntime
src/test/java/com/openggf/game/sonic3k/TestSonic3kBootstrapResolver.java#setUp
```

No inspected key needs retention. The separate eighth-wave S1 moving-block,
Buzz Bomber and Caterkiller keys remain untouched; final count composition and
its guide/changelog prose belong to the parent.

The fixed ten-class run passed all 50 cases with zero failures/errors/skips;
XML matches every source test identity and the exact selector, including all six
`TestSonic3kBootstrapResolver` cases. The fork reports Java 25.0.5.0.2, and the
launch environment has Lua 5.4.9. Queue admission waited 35 seconds; Maven reported
1:22 execution. These checks need no ROM or GL rendering; display variables are
unset and no EGL context is requested. The separate exact singleton lifecycle
guard passed all eight cases with zero failures/errors/skips; its fresh XML
identities and fork version were inspected. Queue admission waited 31 seconds;
Maven execution was 28.094 seconds. The two submitted jobs kept fixed selections
and source inputs throughout execution. Their 22 exact XML/text reports were
consumed after inspection; no result archive or pass cache was created.

The commands use the current absolute parent-checkout wrapper from this task's
own working directory and target. `OPENGGF_MAIN_WORKSPACE` below denotes that
absolute parent checkout:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" \
  --lean -Dmse=off \
  '-Dtest=TestTodo30_TimerErrorReporting,TestTerrainCollisionManagerReset,TestTerrainCollisionManager,TestZoneLayoutMutationPipeline,TestSonic2PlcArtRewindSnapshot,TestRingManagerRewindSnapshot,TestGameModuleProviderLifetimes,TestSpriteManagerUpdateOrder,TestOnObjectAtFrameStartSnapshot,TestSonic3kBootstrapResolver' test -B
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" \
  --lean -Dmse=off '-Dtest=TestSingletonLifecycleGuard' test -B
```

This is bounded fixture/guard evidence. No broad/full suite, unchanged frontend
or engine-family rerun, ROM/trace replay or pixel validation was submitted.
The parent retains the composed four-selector S3K/API/guards checks and delivery;
this slice ends with a local commit and retained branch/worktree.


### Eighth-through-tenth composed validation checkpoint

The frozen composed executable state is `19619db8078530563baf973c8ec31dc576a067bc`
in `bugfix/ai-architecture-loop10-20261024`. It includes the accepted palette
texture-height fix, pooled-command and renderer scalar boundaries, S1 fixtures,
monitor admission correction, S2/MGZ and engine/frontend/direct fixture families,
and complete art-provider candidate migration. The later Game, palette-decoder,
module-fixture and sensor/event slices are separate and are not certified here.
No executable source or tested allowance changed during the following jobs.

Parent-observed checks on that exact state:

| Selection | Reports / cases | Failure / error / skip | Timing |
| --- | --- | --- | --- |
| A: art/donor/scene/ghost consumers, monitor and required S3K controls | 36 / 585 | 0 / 0 / 0 | Maven 3:23; queue wait not retained |
| B: GPU command/backends, SAT, palette capacity, appearance/water/fades | 19 / 201 | 0 / 0 / 0 | Maven 29.722s; queue wait not retained |
| C: candidate API, SDK/Javadoc fixtures, samples and starters | 10 / 60 | 0 / 0 / 0 | wait 288s; Maven 36.372s |
| Separate fresh guards JVM | 92 / 769 | 0 / 0 / 0 | Maven 5:20; exact queue wait not retained |

The three focused selections contain 846 cases. Their exact XML identities,
Java25 and EGL launch properties were inspected; all four absolute ROM properties
were supplied. The four required S3K selectors account for 60 executed cases:
bootstrap6, decoding3, game-package level-loading36, headless AIZ skip8 and the
tests-package level-loading7. No required ROM control silently skipped.
The guards include all33 ArchUnit rules with store updates disabled, all9
architectural-review checks, all77 architectural source checks and all8 singleton
lifecycle controls. The previously reported renderer component's published-count
failure is resolved by the composed 136-record ledger. Source was clean and
unchanged before and after the commands.

The commands below retain the executed argument sets. `MAIN_CHECKOUT` denotes the
actual absolute main checkout, including its existing verified ROMs; the commands
ran from the coordinator's own directory/target with the absolute current queue
wrapper. Queue waits and overlapping jobs are not added into an elapsed-delivery
claim.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 'python3' "${MAIN_CHECKOUT}/tools/testing/maven_queue.py" '-Dmse=off' '-Dopenggf.headless.gl=surfaceless-egl' '-Dtest=TestCrossGameFeatureProviderRefactor,TestDonorCapabilities,TestLevelPlayableArtInitializerModArt,TestNativeCarriedPlayerArtBinding,TestS3kCanonicalEntryLifecycle,TestS3kHpzSanctuaryLifecycleProduction,TestCrossGameRuleComposer,TestPlayerHeadMaskReview,TestKis2EndingContinue,TestKis2HeadlessBoot,TestDonatedTeamPalettes,TestDonorTailsTailBanks,TestPlayerHeadPresentation,TestSampleFlappyIntegration,TestSampleRomArtRemixIntegration,TestCharacterLooks,TestDonorPaletteRewind,TestStockSceneArtRom,TestDetachedStockScenePictures,TestStageplayTailsRom,TestGhostRendererLayerSelection,TestLocalPlayerLook,TestDynamicArtGhostIsolation,TestSceneRomLibrary,TestSceneArtHelpers,TestStageplayNativeBinding,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kMonitorGraphRewind,TestSonic3kMonitorObjectInstance,TestSonic3kMonitorContentOverride' "-Dsonic1.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${MAIN_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dkis2.rom.path=${MAIN_CHECKOUT}/kis2.gen" 'test' '-B'

env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 'python3' "${MAIN_CHECKOUT}/tools/testing/maven_queue.py" '-Dmse=off' '-Dopenggf.headless.gl=surfaceless-egl' '-Dtest=TestGraphicsManagerPatternAtlasPageOrdering,TestGraphicsManagerReinit,TestPooledRenderCommandDiscard,TestPresentationPatternBatching,TestSatReplayBatching,TestRenderProjection,TestGraphicsManagerSpriteSatReplay,TestSpritePreparationSnapshots,TestSpriteSatReplayPixels,TestPaletteTextureCapacity,TestGraphicsManagerFadeRebinding,TestRenderContext,TestUnderwaterPaletteUpload,TestUnderwaterPaletteRendering,TestAppearancePalettes,TestPaletteUploadLatch,TestPaletteFadePresentation,TestNativeWhitePaletteFade,TestScopedPalettePresentationRewind' "-Dsonic1.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${MAIN_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dkis2.rom.path=${MAIN_CHECKOUT}/kis2.gen" 'test' '-B'

env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 'python3' "${MAIN_CHECKOUT}/tools/testing/maven_queue.py" '-Dmse=off' '-Dopenggf.headless.gl=surfaceless-egl' '-Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiJavadocTool,TestModApiSdkPackager,TestPurposeStarters,TestSampleModsPackage,TestSampleFlappyLevelSource,TestSamplePlatformerLevelSource,TestSampleRomArtRemixLevelSource' "-Dsonic1.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${MAIN_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dkis2.rom.path=${MAIN_CHECKOUT}/kis2.gen" 'test' '-B'

env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 'python3' "${MAIN_CHECKOUT}/tools/testing/maven_queue.py" '-Dmse=off' '-Dopenggf.headless.gl=surfaceless-egl' '-Pguards' "-Dsonic1.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${MAIN_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dkis2.rom.path=${MAIN_CHECKOUT}/kis2.gen" 'test' '-B'
```

All 314 corresponding XML/text reports were consumed after inspection
(72 + 38 + 20 + 184). No pass cache, validation receipt or log archive was made.
The fresh full canonical art-provider Javadoc/artifact proof remains separately
attributed to the accepted component; the parent C selection above verifies its
maintained tooling controls rather than claiming another canonical generation.

The eighth composition's original broad smoke request remains alive:
`20261024T061914Z-01fb5ad5`, based on pre-task destination `e9d8087aca`, in the
separate loop-eight checkout at `3e0811d711` plus its owned count-prose edits.
It selected3746 candidates in75 ordinary batches. At this checkpoint it remains
incomplete; no broad or fully green delivery claim follows from the focused and
guard passes. Its observed HCZ native-pixel failure occurs at GLFW initialization
before graphics/ROM setup. The exact isolated baseline command on unchanged
`e9d8087aca` reproduced the same class, method and failure message with the same
Java25/EGL environment and ROM paths. Thus this is an inherited no-display
prerequisite, while that native-pixel oracle remains unvalidated. Legacy GLFW,
opt-in measurement/capture and external-mod prerequisites are inspected as skips,
not counted as exercised coverage. Final smoke results and acknowledgment follow
only after the existing request completes.

### Qualified SMPS restore-boundary retention

The independent grounding and design judgment retain exactly the two legacy
`SmpsSequencer` convenience constructors that sample `GameServices.audio()`
during construction. New callers with an owned context already use explicit
`MusicRestoreSink` overloads. The proposed caller-complete injection alternative
would migrate four production and100 test/fixture calls, plus backend/tool
composition, and deliberately change construction-time configured-root sampling
to stable backend-lifetime ownership. No legitimate observable restore defect
establishes that new lifecycle requirement here. `AudioManager.presentationOwner()`
is the lazy singleton, not the configured `EngineServices` owner. Neither it,
a backend callback nor a no-op substitutes for manager mutator/timeline behavior.

The normal presentation path's explicit deferred natural-restore callback stays
unchanged. Retain both frozen records with delta0 and preserve validation order,
detached/inferred hosts, descriptor trust and publication. Reopen only for a
concrete owner-lifetime requirement with actual restore effects, command history,
suppression and foreground behavior before/after; identity differences alone are
characterization. This is source-based qualified retention, not a runtime fix or
claim that all audio architecture is exhausted.

Integration/push and safe worktree accounting remain pending. Later local slices
retain their own commits, tests and coverage limits.



### Palette decoder ownership continuation

This slice starts from composed parent `19619db8078530563baf973c8ec31dc576a067bc`
in isolated branch `bugfix/ai-palette-decoder-owner-20261024`. The copy-on-write
checkout cloned 14,596 tracked files with native FICLONE and no fallback; hooks
were installed. The parent remains frozen for its independent validation.

`PaletteLoader` constructs canonical `level.Palette` values from Sega-format
bytes and owns the corresponding ROM reads. Move the complete utility beside
that model in `com.openggf.level`; keep `Rom` as its explicit source. Five
production consumers (all three water providers, KiS2 chip art and S3K results)
and two test consumers need only the FQN substitution. The class body, constants,
private constructor, four public methods, checked exceptions, short-input and
extra-input behavior, allocation and failure order remain byte-identical.
No wrapper, new model, cache, public overload or decoding policy is introduced.

Source inspection finds no `@ModApi`, curated root, recursive signature-pin or
internal-terminal identity for this utility. A supported returned `Palette` does
not pull every utility consuming it into the recursive API closure. This move
therefore predicts unchanged candidate pins and release descriptor, unlike the
separately reviewed art-provider and Game candidate migrations. Retaining the
utility in `data` would retain a runtime-model construction responsibility in
the low-level layer; moving only its byte reads would split a small complete
decoder and leave the same reverse dependency. Those alternatives are rejected.

Exactly ten frozen records have `data.PaletteLoader` as owner. Removing them
leaves the other 126 records byte-identical and ordered, with no replacements.
The `data -> level` permission and cycle exception remain because `data.Game`
still uses the edge in this slice. Rule names/settings and all other allowances
remain unchanged. Published low-level count prose follows the exact store.

Qualification will compile this fresh checkout, run the three water-provider
classes, KiS2 chip-art and S3K results controls, water rewind and the four
required S3K stability selectors. A separate fresh JVM will evaluate API pins,
strict ArchUnit and published-count/source controls. No new tests mirror this
intact relocation; independent existing ROM-word and behavior assertions are
the useful checks. Source/body equality and exact frozen delta are checked
separately. Results and limits remain pending until those commands finish.


Decoder qualification completed against the unchanged implementation described
above. The first command passed **146 cases, zero failures/errors/skips** in
11 XML reports: S1 water19, S2 water22, S3K water28, KiS2 chip art6, S3K results2,
water rewind9 and the required S3K selectors60 (including both level-loading
classes). Java25, the explicit EGL flag and all four existing absolute ROM paths
were inspected in every report. Queue admission waited115 seconds; Maven reported
1:20 execution. Fresh compilation contains `level/PaletteLoader.class` and no
`data/PaletteLoader.class`. The 22 XML/text reports were consumed.

A separate fresh-JVM command passed **45 checks, zero failures/errors/skips**:
33 ArchUnit rules with store updates disabled, 11 API signature controls and
`archUnitPublishedBaselineCountsMatchFrozenStore`. Queue wait79 seconds; Maven
execution36.745 seconds. The three exact XML reports and launch properties were
inspected and their six XML/text outputs consumed. The generated candidate pin
and release descriptor remain byte-identical to the base.

Both commands used the following common environment and verified ROM arguments
from this isolated checkout. `MAIN_CHECKOUT` and `ROM_ROOT` denote the actual
absolute main checkout and existing ROM directory; no ROM was copied or relinked
to satisfy a test:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${MAIN_CHECKOUT}/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestSonic1WaterDataProvider,TestSonic2WaterDataProvider,TestSonic3kWaterDataProvider,TestKis2ChipArt,TestS3kSpecialStageResultsBackdrop,TestWaterSystemRewindSnapshot,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=${ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dkis2.rom.path=${ROM_ROOT}/kis2.gen" test -B
```

The second command retained that environment, ROM and EGL argument set, replacing
the test selector with
`-Pguards '-Dtest=TestModApiSignatureSurface,TestArchUnitRules,TestArchitecturalReviewGuard#archUnitPublishedBaselineCountsMatchFrozenStore'`.

Independent read-only review accepted the move at stable source fingerprint
`6a34dfe2a16c18274e8789036520fc1b35b07c6748ba68012017d953e9c47a14`;
its test statements were explicitly source-only, and the executed outcomes above
are parent observations. Root also verified the complete decoder and seven
consumers as raw bytes after only the specified package/import substitutions,
and all126 retained frozen records in order. Review found no missed maintained
consumer or supported closure; external users of this unsupported utility FQN
need rebuilding. Historical design paths remain historical.

This proves the intact bounded ownership move, not every native water route,
malformed-input combination or GPU behavior. Existing S1/S2 water controls have
limited successful-decoding breadth; body identity preserves their untested
semantics without inventing duplicate implementation tests. No broad smoke was
repeated for this slice. Parent composition, push and cleanup remain outstanding.



### Module/world fixture family continuation

This later-composition slice starts from accepted commit
`279a4c858f444ee50977c72f9bc61092ac316ebb` on new local branch
`bugfix/ai-module-fixture-family-20261024` in the same isolated worktree. The
previous engine-only branch remains at that commit for parent cleanup. Initial
state was clean with no owned Maven jobs. The frozen coordinator candidate is
not changed. Scope is the ten test classes below, their exact guard keys and
this appendix; production code and reviewed fixture helpers are unchanged.

Module selection follows source authority rather than test names. SBZ events,
S2 event snapshots, MCZ boss tests and ARZ/MCZ scroll tests previously selected no
module; they now use `resetAll()`, preserving the ordinary S2 bootstrap/runtime
baseline established by `GameModuleRegistry` and the global teardown extension.
In particular, the SBZ class's locally constructed S1 handler does not justify
silently changing its surrounding runtime to S1. CNZ retains its explicit S2
selection through the enum fixture; spring, Mega Chopper and MGZ scrolling retain
their explicit S3K selection through the S3K enum fixture.

Tornado keeps its previous-module capture and S2 bootstrap write, then passes
`GameModuleRegistry.getCurrent()` directly into the existing module fixture.
Java evaluates that argument before fixture teardown, preserving the original
getter's live-world precedence instead of forcing S2 based on the class name.
MGZ captures its previous module before the S3K fixture. Both original restoration
paths are byte-identical, including Tornado's ordering and camera-bound cleanup.
SBZ construction-scope setup/cleanup, synthetic levels, Mega Chopper's level-state
injection, camera initialization, input/tick loops and every teardown remain
unchanged. ARZ still reads session screen-shake state; MCZ retains its one-argument
handler and compatibility setter into that same state, with no unrelated supplier.

Exact comparison after setup/import normalization preserves all 150 test bodies,
identities, assertions and helper methods. The removed guard keys are exactly:

```text
src/test/java/com/openggf/game/sonic1/events/TestSonic1SBZEvents.java#setUp
src/test/java/com/openggf/game/sonic2/TestSonic2CnzMutationPipeline.java#setUp
src/test/java/com/openggf/game/sonic2/TestSonic2LevelEventRewindSnapshot.java#setUp
src/test/java/com/openggf/game/sonic2/objects/TestTodo4_MCZBossCollision.java#setUp
src/test/java/com/openggf/game/sonic2/objects/TestTornadoObjectInstance.java#setUp
src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kSpringObjectInstance.java#setUp
src/test/java/com/openggf/game/sonic3k/objects/badniks/TestMegaChopperBadnikInstance.java#setUp
src/test/java/com/openggf/game/sonic3k/scroll/SwScrlMgzTest.java#setUpRuntime
src/test/java/com/openggf/level/scroll/SwScrlArzTest.java#setUp
src/test/java/com/openggf/level/scroll/SwScrlMczTest.java#setUp
```

No inspected key needs retention. The separate eighth-wave S1 moving-block,
Buzz Bomber and Caterkiller keys remain untouched; integrated totals are not
copied from this older branch or used to change parent count prose.

The fixed ten-class run passed all 150 cases, with zero failures/errors/skips.
XML confirms the exact selector and every source identity: SBZ 20, CNZ 1, S2 event
snapshots 9, MCZ boss 17, Tornado 44, S3K spring 24, Mega Chopper 4, MGZ scroll 14,
ARZ scroll 6 and MCZ scroll 11. The fork reports Java 25.0.5.0.2; Lua 5.4.9 is in
the launch environment. Queue admission waited 20 seconds; Maven execution was
27.719 seconds. These checks use synthetic data and injected services, with no
real ROM/display prerequisite or observed skip; no GL context or ROM path is
needed. The separate exact singleton lifecycle guard passed all eight cases with
zero failures/errors/skips. Its fresh XML identities and Java 25 fork version
were inspected. Queue admission waited 92 seconds; Maven execution was 26.946
seconds. Both submitted jobs retained fixed selections/source until completion.
Their 22 exact XML/text reports were consumed after inspection; no result archive
or pass cache was created.

The commands use the current absolute main-checkout wrapper from this worktree's
own directory/target. `OPENGGF_MAIN_WORKSPACE` denotes that absolute checkout:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" \
  --lean -Dmse=off \
  '-Dtest=TestSonic1SBZEvents,TestSonic2CnzMutationPipeline,TestSonic2LevelEventRewindSnapshot,TestTodo4_MCZBossCollision,TestTornadoObjectInstance,TestSonic3kSpringObjectInstance,TestMegaChopperBadnikInstance,SwScrlMgzTest,SwScrlArzTest,SwScrlMczTest' test -B
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" \
  --lean -Dmse=off '-Dtest=TestSingletonLifecycleGuard' test -B
```

This is bounded fixture/guard evidence, not broad smoke, full-suite, arbitrary
incoming-module permutations, ROM route or pixel certification. No previous
family was rerun. This branch remains local for a later parent composition;
integration, push and branch/worktree removal are outside this slice.


### Direct-subject and engine-only fixture family continuation

This later-composition slice starts from accepted module-family commit
`9a4f7113807abc2b33879d7906ec74e446703c2f` (tree
`4a4195752b47a458d29193010a062c6c463092f4`) on new local branch
`bugfix/ai-direct-fixture-family-20261024` in the same isolated worktree.
The engine-only branch remains at `279a4c858`, and the module-family branch at
`9a4f711`; no parent/frozen-candidate branch is changed. Initial state was clean
with no owned Maven jobs. Scope is eight changed tests, their exact allowance
removals and this appendix. OilSurfaceManager and the S1 monitor class are
retained unchanged. Production, shared helpers, guard scanning and all other
allowances remain unchanged.

Constructor-time grounding precedes the fixture choice. FZ's constructor and
`initializeBossState()` initialize local state without consulting services; its
two escape-hit consumers already inject `TestObjectServices` before attacking or
invoking the flight update. They now receive the owned real camera. MGZ swinging
platform construction computes local chain positions; its real managers already
receive explicit cameras/services. The counter-unload family likewise owns the
real eight-argument `ObjectManager`, including its construction scopes and
synthetic water/level services. These three classes now construct real cameras
from standalone configuration reset to defaults and clear the ambient session.
Only `GameServices.camera()` receivers in FZ and counter-unload consumers change
to the owned camera field; manager constructors, camera values/bounds, execution
slots, update counts and assertions remain intact. An unloaded LevelManager's
`getRomZoneId()` returns -1, so MGZ's registry retains the same legacy S3KL
selection with no world; no S3K world is inferred from its class name.

The other five migrations use the reviewed `resetEngineOnly()` because
`AbstractSprite` captures `EngineServices.current().configuration()` during
construction: rewind reference codecs, AIZ vine logic, monitor-icon timing,
lightning-shield tests and MGZ twisting-loop tests. Their tested
paths need no session-owned manager. Late injection was checked explicitly:
`ShieldObjectInstance` captures its renderer during construction, but these
lightning-shield tests intentionally construct without a context or loaded art;
`getRenderManager()` already returns null before the existing later service
injection. Adding a renderer-bearing construction context would change that
negative control and was rejected. Monitor constructors initialize local icon
state; existing post-construction service injection and manager-owned scopes
remain in place. All helper bodies, explicit module/rule selection, camera-bound
updates and teardown cadence are preserved. Twisting-loop setup still starts by
clearing the session and retains its explicit 1024-by-1024 camera bounds.

Retained key:
`src/test/java/com/openggf/tests/TestOilSurfaceManager.java#setUp`.
The real local oil manager is not the only consumer: the existing
`suffocatesAfterSubmersionCountdownExpires` test calls `applyOilSuffocateDeath()`
through the sprite, then `applyDeath()` freezes `currentCamera()` for the main
player. That camera is session-owned. Engine-only reset would remove a required
native owner, and injecting a replacement sprite runtime or broadening this
batch to a module/world fixture would change the grounded shape. Both test and
allowance remain byte-identical; no removal is claimed.

The S1 monitor key is also retained unchanged:
`src/test/java/com/openggf/tests/TestSonic1MonitorObjectInstance.java#setUp`.
The initial engine-only attempt ran all 92 invocations but produced two errors:
`monitorTopContactStillLandsWhenPlayerWasBouncedUpward` and
`adjacentMonitorLandsJumpLatchedPlayerAfterSameFrameMonitorBreakBounce`. Both
solid-contact paths land the real sprite through `setAir(false)`, which calls
`currentGameState().resetItemBonus()`. An active session is required despite the
explicit local object manager and camera mocks. The attempted setup and allowance
removal were reverted exactly to the pinned base; injecting a different game
state would change the consumer authority. No assertion or body was repaired to
fit the proposed fixture. That rejected run had 0 failures, 2 errors and 0 skips;
queue wait was 62 seconds and Maven execution 31.004 seconds. XML matched all ten
class/method sets, including all four parameter rows. Its source-diff SHA-256 was
`e144767f30b231a64c56f6fd033cf1f364e58c0ed6d03d478abef1cf5495402e`;
the inspected 20 XML/text reports were consumed before the corrected run.

Exactly these eight keys are removed (the separate eighth-wave S1 keys are
untouched; no integrated parent inventory total is inferred):

```text
src/test/java/com/openggf/game/rewind/schema/TestRewindPlayerReferenceCodecs.java#setUpRuntime
src/test/java/com/openggf/game/sonic1/objects/bosses/TestSonic1FzBossEscapeHitCue.java#setUp
src/test/java/com/openggf/game/sonic3k/objects/TestAizVineHandleLogic.java#setUp
src/test/java/com/openggf/game/sonic3k/objects/TestMGZSwingingPlatformObjectInstance.java#setUp
src/test/java/com/openggf/level/objects/TestObjectManagerCounterBasedDynamicUnload.java#setUp
src/test/java/com/openggf/tests/TestMonitorIconTiming.java#setUp
src/test/java/com/openggf/tests/TestSonic3kLightningShieldObjectInstance.java#setUp
src/test/java/com/openggf/tests/TestS3kMgzTwistingLoopObject.java#setUp
```

Mechanical comparison against the base preserves all 89 source test methods
(88 ordinary plus one four-row parameterized method: 92 expected invocations),
including the two retained classes' 18 unchanged methods. Apart from setup/imports,
the new camera fields and justified camera receiver replacement, class contents
are identical, including assertions, timing, helper and teardown bodies.
The normalized ten-class SHA-256 is
`d191332845c6046c2856f8938a4ef9514fbfbf9327f00e90cdc29db25a7e41b2`.
The fixed submitted source fingerprint, SHA-256 of
`git diff --binary 9a4f711 -- src/main src/test pom.xml`, is
`cd8cd8b8a283f9643e5ed93d45069e000f4112975d1c79f4ac6dc62af81a2b5a`.
Baseline runtime results are not independently measured; identity and count
comparisons here refer to the pinned source, with runtime results below.


The corrected fixed ten-class selection passed all 92 invocations with zero
failures, errors or skips: reference codecs 9, FZ cue 2, vine logic 7, MGZ
swinging platform 5, counter-unload 15, monitor icon timing 2, retained Oil 11,
retained S1 monitor 7, lightning shield 3 and twisting loop 31. XML identities
match every source method; the twisting-loop parameterized method has all four
rows, and the fully qualified S1 monitor selector excludes the separate
`game.sonic1.objects` class with the same simple name. Java was 25.0.5.0.2 and
Lua 5.4.9. This corrected run waited 0 seconds and Maven executed for 28.909
seconds. The ten fresh XML and ten text reports were inspected and consumed;
the submitted source fingerprint remained fixed through execution. No real ROM
or graphics context is reached by this selection, so no ROM property or EGL flag
was needed; desktop display variables were unset.

Exact focused command, from this isolated worktree using the current absolute
main-workspace queue wrapper (`OPENGGF_MAIN_WORKSPACE` denotes that checkout):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" \
  --lean -Dmse=off \
  '-Dtest=com.openggf.game.rewind.schema.TestRewindPlayerReferenceCodecs,com.openggf.game.sonic1.objects.bosses.TestSonic1FzBossEscapeHitCue,com.openggf.game.sonic3k.objects.TestAizVineHandleLogic,com.openggf.game.sonic3k.objects.TestMGZSwingingPlatformObjectInstance,com.openggf.level.objects.TestObjectManagerCounterBasedDynamicUnload,com.openggf.tests.TestMonitorIconTiming,com.openggf.tests.TestOilSurfaceManager,com.openggf.tests.TestSonic1MonitorObjectInstance,com.openggf.tests.TestSonic3kLightningShieldObjectInstance,com.openggf.tests.TestS3kMgzTwistingLoopObject' test -B
env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN_WORKSPACE}/tools/testing/maven_queue.py" \
  --lean -Dmse=off '-Dtest=TestSingletonLifecycleGuard' test -B
```


The separate exact singleton guard passed all eight source identities, with zero
failures, errors or skips. Its queue wait was 282 seconds; Maven execution was
28.096 seconds. The exact selector, Java version and every identity were checked
in fresh XML, then both guard reports were consumed. These per-command queue and
execution intervals are not summed into delivery elapsed time. No user-response
wait occurred; other work time is unmeasured.

The final slice closes eight requested keys and explicitly retains two; it makes
no claim about the composed parent's inventory. Only bounded fixture and exact
guard coverage was run, including one rejected fixture attempt and one corrected
run with the same ten selectors. No broad/full suite, previous-family rerun,
ROM/trace/pixel validation, arbitrary incoming-module matrix, production change
or shared-helper change is included. The branch remains local for later parent
composition, with both earlier branches preserved. Integration, push and branch/
worktree removal remain outside this slice.



### Event snapshot and ground-sensor fixture continuation

This slice starts from `9a4f7113807abc2b33879d7906ec74e446703c2f` in isolated
branch `bugfix/ai-sensor-event-fixtures-20261024`. It applies the independent
source grounding of three unresolved fixture keys: close two and retain
`TestGameLoop#setUp`. No production or shared fixture helper changes are needed.

`TestSonic3kLevelEventRewindSnapshot` uses `resetAll()`: its original setup selected
no S3K module, so selecting S3K by class name would change runtime authority. The
local managers still initialize handlers and typed zone sidecars against camera,
sprite and registry services. Keep their direct capture/restore and adapter
enumeration assertions; these do not prove registration of the local manager in
the active runtime's provider-owned registry. Its session-clear teardown stays.

`TestGroundSensor` uses the explicit S2 module fixture before constructing and
installing its static mock level override. Both reset and session disposal clear
that override, so installing it first is invalid. Preserve the maps, sprite
construction, sensor observations and override-clear then session-clear teardown.
Body-level bootstrap module writes and reverse-gravity changes remain unchanged;
the live S2 world's module still has precedence, so those names do not establish
native cross-game execution. The existing `resetAllClearsStaticLevelManagerOverride`
control exercises the relevant cleanup behavior. All consumer bodies/helpers and
assertions remain byte-identical after only import/setup normalization.

Retain `TestGameLoop#setUp` unchanged: it clears overrides and resolves display
state while preserving persisted preferences. The existing reset fixtures call
`resetToDefaults()` and also replace module/world ownership. A simple wrapper
around its current bootstrap calls would only hide that obligation. A later
closure needs a preference-preserving lifecycle owner with incoming-module
capture, prior teardown/fixups and explicit replacement-world semantics, backed
by discriminating behavior controls. This slice introduces no such fixture.

Remove exactly the event-snapshot and GroundSensor guard keys. The local baseline
changes 55 to 53; it still includes three S1 keys independently closed in the
parent. Compose deletions by identity: parent62 minus the module component10
minus these2 gives50. Do not copy this older branch's allowance set wholesale.

Focused qualification selects the two affected classes and exact singleton
lifecycle guard; the unchanged GameLoop class is retained rather than rerun as
a ceremony. No ROM/display prerequisite or shared reset implementation change
is introduced. Exact command, XML outcomes and limits remain pending.


The focused command passed **77 cases, zero failures/errors/skips**: all38 event
snapshot methods, all31 sensor methods and all8 singleton lifecycle guard cases.
XML method sets match the complete source identities exactly, including
`resetAllClearsStaticLevelManagerOverride` and the snapshot corruption/rollback
controls. The fork reports Java25; Lua5.4 was supplied. Queue wait26 seconds;
Maven execution1:32. No ROM/display assumption was skipped. Six XML/text reports
were inspected and consumed; source stayed fixed throughout the queued job.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${MAIN_CHECKOUT}/tools/testing/maven_queue.py" --lean -Dmse=off \
  '-Dtest=TestSonic3kLevelEventRewindSnapshot,TestGroundSensor,TestSingletonLifecycleGuard' \
  test -B
```

Root compared both complete test files after only import/setup normalization:
all69 consumer bodies, assertions and helpers are unchanged. `TestGameLoop` is
byte-identical; only the two specified allowance lines disappear, with zero
additions. The independent grounding's retention limits above still apply. This
is bounded fixture evidence, not a new native cross-game, local-manager registry
registration, full-route or pixel guarantee. No broad smoke was rerun. The local
branch remains for later composed validation, integration, push and cleanup.

### Native SMPS retained-ROM ownership continuation

This component starts at accepted local composition
`48dbe26d014a30460a6784cfcefb1040aafe44ae`, on isolated branch
`bugfix/ai-native-smps-rom-owner-20261024`. The completed residual grounding
recommended only this two-record correction; its other qualified retentions and
the closed SMPS restore-owner decision remain unchanged. The parent stays frozen
for its own focused qualification. Component results do not inherit or supersede
parent composition checks, integration, delivery or cleanup.

`AbstractSmpsLoader` never reads its retained ROM. Its shared caches and hexadecimal
dispatch now use an explicit protected no-argument constructor. Each native reader
retains a private final `Rom`; public native `(Rom)` constructors, profile factories
and callers keep their signatures. No close, snapshot, lookup, port, erasure or
transfer of caller-owned ROM lifetime is introduced. A game-owned `RomSmpsLoader`
superclass was rejected: it would preserve pre-initializer binding but add a type
and hierarchy without a behavior requirement.

Initializer inspection preceded that choice. S1 has only static logger/constants
and default-null native instance fields. S2 allocates two stateless decoders and
three maps; neither decoder has an instance initializer or constructor observing
ROM. S3K allocates only its bank map. None observes `this.rom`, invokes a ROM
callback or publishes the loader. Binding after initializers and before unchanged
preparation therefore preserves observable order: S1 vector area then PSG
envelopes; S2 names, eager SFX cache, then music/name mappings; S3K decompression,
Z80 tables, S3 tables, modulation envelopes, PSG envelopes and global instruments.
Null/closed/reader-backed behavior, reads and exception handling stay at existing
sites. No validation or eager read is added. Complete native files are byte-identical
after only field/constructor-edit normalization, preserving decoder, dispatch,
mapping and cache bodies. Combined normalized native source SHA-256:
`b38b4379e8cf6632dd06c2cee35b7f5de7b6840e77a55a4b5490c03f1db098ac`.

The actual `ModApiSignatureSurface.recursiveTypes` visitor starts at curated roots
and follows public/protected signatures, generic ancestors/interfaces, nested
types, records and annotation references. It does not walk implementation bodies
or enumerate concrete implementors of returned interfaces. Factory inheritance
is native audio profile → `AbstractAudioProfile` → `GameAudioProfile`; the supported
factory returns `SmpsLoader`, not a native implementation. Base/native loaders are
unannotated and absent from curated roots, recursive signature and internal-terminal
pins. Supported `Rom` use does not export every consumer. Unsupported external
subclasses using the old protected base `(Rom)` constructor or field must rebuild
with no-argument base construction and their own retained reference. This limited
cost adds no supported compatibility promise. Candidate signature/terminal pins,
`ModApiVersion` and release descriptor remain byte-identical to the parent.

Delete exactly these records from frozen store
`40afa018-d450-4b84-8cb9-d539d75d8062`:

```text
Constructor <com.openggf.audio.smps.AbstractSmpsLoader.<init>(com.openggf.data.Rom)> has parameter of type <com.openggf.data.Rom> in (AbstractSmpsLoader.java:0)
Field <com.openggf.audio.smps.AbstractSmpsLoader.rom> has type <com.openggf.data.Rom> in (AbstractSmpsLoader.java:0)
```

The inventory is **126 → 124**, zero additions; all retained records are
byte-identical and ordered. Resulting store SHA-256:
`4173fbdbadd154e0a8a0c20b99c440c5666ecaeaa0edaca3ec2430bb10ab6c7d`.
Six directional `audio -> data` records remain. Top-level permissions, cycle
ignores and rule source are unchanged. Both `SmpsSequencer` convenience
constructors and their exact `GameServices.audio()` records stay unchanged.
Game relocation is separate; no projected Game reduction is counted. Decoder,
art, scalar and fixture decisions are inherited unchanged.

The change-based planner selected all 3,747 smoke candidates because native paths
and the frozen store trigger shared/unclassified rules. That is disproportionate
for this mechanically bounded reference move; the user explicitly excludes broad/
full suites. Use focused behavior and separate fresh strict architecture/API/count
checks. Actual launch-environment preflight passed Java 25, Lua 5.4 and PowerShell.
Existing absolute ROMs were verified in the main checkout (`MAIN_CHECKOUT`):

| File | CRC32 | SHA-1 |
| --- | --- | --- |
| `s1.gen` | `AFE05EEE` | `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b` |
| `s2.gen` | `7B905383` | `8bca5dcef1af3e00098666fd892dc1c2a76333f9` |
| `s3k.gen` | `63522553` | `cfbf98c36c776677290a872547ac47c53d2761d6` |

KiS2 is not affected. No ROM files or links changed. Fresh `clean test-compile`
succeeded: 4,336 production sources and 4,446 test/maintained-consumer sources
compiled with release 25, including all four racing source roots. Maven execution
was 1:22; this build-only command needed no test admission. Deprecation/unchecked
warnings remained. Source/test inputs stay fixed from submission through exit.
Source fingerprint (SHA-256 of
`git diff --binary 48dbe26d014a30460a6784cfcefb1040aafe44ae -- src/main src/test pom.xml`):
`d464bbe2a379b8c0ee642cefac570be92934b62f106943ff1e3264c30c9e484b`.

The focused run passed **90 cases, zero failures/errors/skips**. XML identities
match source methods, the three nested DAC chant cases and all 25 `zoneLoads`
parameter rows. The simple loading selector reaches both maintained
`com.openggf.game.sonic3k.TestSonic3kLevelLoading` (36) and
`com.openggf.tests.TestSonic3kLevelLoading` (7). Other counts: S1 dispatch 2,
S2 Saxman readiness 8, S3K voice resolution 5, native-profile production DAC
factories 3, ROM audio integration 10, S3K profile 2, AIZ1 skip 8, bootstrap
resolver 6 and decoding utilities 3. The production DAC path installs each native
profile and ROM through AudioManager; its existing factory is exercised for all
three games. S2 readiness also opens the verified file-backed ROM directly;
`TestEnvironment.currentRom()` routes through RomManager's logical reader-backed
catalogue for the `@RequiresRom` controls.

Every XML reports Java `25.0.5.0.2`, explicit surfaceless EGL and all three exact
absolute ROM properties. The live wrapper environment confirmed both display
variables and both Java option variables unset, with Java 25 and `/usr/bin/lua5.4`
selected; Lua reports 5.4.9. Queue wait was 126 seconds, Maven execution 30.199
seconds. The 14 XML and 14 text reports, including the zero-case outer DAC
container, were inspected and consumed; no raw output was archived. The submitted
source fingerprint remained unchanged through exit. This is component behavior
coverage, not inherited parent validation.

Exact commands below use `MAIN_CHECKOUT` for the current main checkout and the
verified ROM directory. All launch from this isolated checkout with its own
`target/`:

```bash
QUEUE=(env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
  python3 "${MAIN_CHECKOUT}/tools/testing/maven_queue.py")
"${QUEUE[@]}" -Dmse=off clean test-compile -B
"${QUEUE[@]}" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestSonic1SmpsLoaderSfxDispatchBoundary,TestSonic2SaxmanLoadReadiness,TestS3kVoiceResolution,TestCrossGameSegaDac,TestRomAudioIntegration,TestSonic3kAudioProfile,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=${MAIN_CHECKOUT}/s1.gen" \
  "-Dsonic2.rom.path=${MAIN_CHECKOUT}/s2.gen" \
  "-Ds3k.rom.path=${MAIN_CHECKOUT}/s3k.gen" test -B
"${QUEUE[@]}" -Dmse=off -Pguards \
  -Darchunit_freeze.store.default.allowStoreCreation=false \
  -Darchunit_freeze.store.default.allowStoreUpdate=false \
  -Darchunit_freeze.refreeze=false \
  '-Dtest=com.openggf.tests.TestArchUnitRules,com.openggf.mods.TestModApiSignatureSurface,com.openggf.mods.TestModApiPinPolicy,com.openggf.tests.TestArchitecturalReviewGuard,com.openggf.tests.TestArchitecturalSourceGuard#lowLevelGraphicsAndAudioDoNotGainGameplayServiceLookups' test -B
```

Planning/preflight used the same unset variables, `JAVA_HOME` and `LUA_BIN`, with
`python3 tools/testing/run_categories.py --base 48dbe26d014a30460a6784cfcefb1040aafe44ae`
and its separate `--preflight` invocation. Neither command ran tests.

The separate fresh strict invocation passed **58 checks, zero failures/errors/
skips**: all 33 `TestArchUnitRules` identities, 11 signature checks, 4 pin-policy
checks, all 9 architectural review guards (including published counts) and the
selected low-level graphics/audio source guard. XML identities match the exact
source selection, including the two temp-directory method signatures. Every XML
reports Java `25.0.5.0.2` and all three explicit frozen store creation/update/
refreeze properties `false`. The checked-in frozen configuration also remains
unchanged with writes disabled. Precise inventory comparison after execution
still gives exactly the two requested deletions and no replacements; all other
frozen stores, keys, permissions, pins, version and descriptor are unchanged.
Queue wait was 149 seconds, Maven execution 39.215 seconds. Five XML and five text
reports were inspected and consumed. There are no live task-owned Maven jobs,
pass caches or raw log archives. Queue and execution intervals are distinct;
other task time is unmeasured, with no user-response wait.

This is a clean local component for independent parent review and composed gates.
Integration, push and worktree/branch cleanup belong to the parent. No remote
push or broad/full suite was performed. No PCM references are regenerated; no performance, native-pixel,
exhaustive trace or whole-route certification is claimed. Null/closed failure
behavior is grounded by unchanged read/exception bodies and initializer inspection,
not a new exhaustive invalid-ROM matrix.
