# Architecture ratchet maintenance and ownership paydown

## Scope and delivery

The user approved the architecture review's course and requested
`starch-rival:architect` before the ownership changes. The integration base is
`b979916808b7f9379ede2b5dcdde1bcdc8bf8357` on `develop`, fetched and
fast-forward checked before creating `bugfix/ai-architecture-ratchet-paydown-20261023`.
Main's dirty reference submodules and unrelated untracked files are preserved.

Deliver guard accuracy, the shared menu animator, execution-time graphics
ownership for the legacy AniPLC strategy, and one bounded rendering-value
ownership cut. Keep public compatibility, native SAT ordering/word semantics,
graphics rebinding and ROM ownership intact. Master-title preview capabilities
are a subsequent migration, not part of this delivery. No broad gameplay-manager
decomposition or blanket lifecycle-call replacement is authorized by this scope.

## Architect phases and boundaries

- [x] Ground: trace the two ownership seams and their historical constraints.
- [x] Sketch: independent GPT-6-Astra and GPT-6.1-Sol candidates, followed by
  one cross-judge and a documented synthesis. These are the user's configured
  pstack roles; both belong to the same model family.
- [x] Agree: record the selected caller usage and types; proceed under existing
  authorization without another routine checkpoint.
- [x] Implement: fill in the selected ownership changes and guard repairs.
- [x] Scrap check: redesign if implementation repeatedly needs ownership
  workarounds, duplicated representations or public-contract escape hatches.
- [x] Verify the selected coverage and attribute the sole pre-existing failure.
- Delivery uses `develop` only, with accounted worktree/branch cleanup after push;
  the final delivery message records the operational result.

The coordinator owns `TestArchitecturalSourceGuard`, ArchUnit rules/stores,
shared documentation, composition and delivery-wide validation. A separate
worker owns rewind field-disposition/throw guards, their inventories, and
caller-specific lifecycle allowances. Production ownership workers begin only
after synthesis and receive disjoint files and focused checks.

## Guard repair contracts

- Source-size limits measure the substantial dispatcher bodies as well as their
  wrappers; multiline signatures and braces in comments/literals cannot evade
  method discovery. Tighten obsolete source/reference headroom explicitly.
- Rewind field allowances match the actual current capture-path audit; resolved
  keys fail until pruned. Remove the obsolete ICZ segment-column scan exclusion.
- Lifecycle exceptions identify their caller rather than sharing a global count
  that permits substitution. Preserve existing production behavior and justified
  reconstruction/slot-allocation exceptions.
- Preserve frozen ArchUnit identities and disabled normal store updates. Measure
  actual package directions before removing unused permissions. A zero cycle
  store after ignores is not proof of an acyclic complete graph.

## Validation plan

Workers run only affected focused checks through the shared Maven queue; the
coordinator owns combined validation. Use Java 25 and existing absolute ROM
paths. Rendering checks use the owned surfaceless-EGL path, never the shared
desktop. Inspect test identities and skips, not only Maven exit status.

Guard changes get discriminating scanner/baseline negative controls. Ownership
changes retain animation cadence and graphics rebinding controls, and exercise
SAT raw tile words, priority, clipping, ghost style and replay ordering. Inspect
the category plan against the integration base after composition; explain any
proportionate focused replacement before launch. Broad required coverage uses
batched smoke plus guards, never release-only full-suite. Attribute disputed
failures with matched narrow baseline/current checks. Do not duplicate unchanged
completed coverage or retain consumed diagnostic directories.

## Combined coverage decision

The final change-based dry plan selects all 3621 smoke classes because shared
animation code, guard infrastructure and inventories trigger shared/unclassified
rules. Review confirms bounded behavior: menu animation is a package move;
AniPLC selects its existing graphics collaborator at execution time; geometry
arithmetic, admission and replay order are unchanged under a new owner. API pins
are unchanged. Guard helpers never run in gameplay. Independent final production
review found no actionable defects. The initial 3616-class observation preceded
the five new ordinary test classes.

Use 25 focused ordinary classes plus the complete fresh-JVM guard profile
(90 source candidates). This replaces the disproportionate all-category
launch under the repository's proportionate-validation policy. Compile all
consumers; cover the required four S3K loading/bootstrap/decoding/AIZ classes,
S2 ROM animator integration, real EGL SAT pixels, CPU masking/ghost/head replay,
API signatures and guard-parser negative controls. Expected execution is a few
minutes from isolated checks, with queue delay unknown. Stop on failures, attribute
and repair narrowly; do not widen to release-only full-suite. This is bounded
validation, not a full smoke-inventory pass or exhaustive trace sweep.

Tool preflight initially rejected ambient Lua; it passed with installed
`LUA_BIN=/usr/bin/lua5.4`, JDK 25.0.5 and PowerShell. Both preflights ran before
any combined test execution.

Commands use the actual launch environment (`ROM_ROOT` denotes the absolute
main-checkout ROM directory; its existing S1 REV01, S2 REV01 and S3&K files
were verified against the repository identity table):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py --base b979916808b7f9379ede2b5dcdde1bcdc8bf8357 --preflight

# Ordinary checks, one queued invocation in the composed parent tree:
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path=${ROM_ROOT}/s1.gen \
  -Dsonic2.rom.path=${ROM_ROOT}/s2.gen \
  -Ds3k.rom.path=${ROM_ROOT}/s3k.gen \
  '-Dtest=TestSpritePiece,TestSpritePieceRendererSatPreparation,TestGraphicsManagerSpriteSatReplay,TestSatReplayBatching,TestSpriteSatMaskPostProcessor,TestSatGhostLayering,TestPlayerHeadPresentation,TestSpritePresentation,TestSpritePreparationSnapshots,TestPlayableMutatorPresentation,TestModApiSignatureSurface,TestS3kSpriteMaskSupport,TestPresentationSpritePixels,TestSpriteSatReplayPixels,TestMenuBackgroundAnimator,TestScriptFramesApplyStrategyGraphicsRebind,TestAniPlcScriptStateSnapshot,TestSonic2PatternAnimatorGraphAdapter,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestObjectLifecycleCallAudit,TestRewindFieldDispositionAudit,TestS3kIczSegmentColumnGraphRewind' test -B

# Separate fresh guard JVM, same Java/Lua environment:
python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

## Review and implementation evidence

Source budget identities now distinguish class/signature, reject ambiguous names,
and require the root owner. The independent guard reviewer also found anonymous/
local caller collisions. The composed lifecycle scanner assigns ordinals per
caller and type name to every declaration, including declarations without calls.
Two swap controls require removed/new identities at an unchanged total; comments
and formatting preserve keys. No current baseline keys needed relocation.

- `30d5a3119`: source/parser budgets and package census. The initial two scanner
  controls failed as expected; all 76 source checks then passed. The live ArchUnit
  census found exactly seven stale permissions (159 → 152), explicitly pruned.
  One earlier attempt stopped at compilation due to a missing throws declaration
  in the new pixel fixture; fixed before running assertions.
- Original-runtime pixel characterization: `TestSpriteSatReplayPixels` passed
  1 test, zero skips at the pinned base with the new test. Queue wait 694 seconds,
  Maven execution 30.524 seconds. It traverses actual submission, SAT and GPU replay.
- `b2170f274c0106040298bf5fcb4d81ddc082cc7d`: shared menu and injected graphics;
  18 tests across four classes passed without failures/errors/skips. Framework
  store 1 → 0; cross-game 14 → 10 members, host donations unchanged.
- `bac8446dc65c77210d58e00ec9ca5486bd815226`: graphics-owned geometry/replay;
  126 tests across 13 classes passed without failures/errors/skips, including
  API signatures and existing EGL pixels. Low-level store 187 → 181: exactly
  23 identities removed and 17 ingress accessor identities added.
- `cdf524af2085221fa48f2cf59ab573ec7db6fe08`: 41 stale rewind field exemptions
  pruned (64 → 23), obsolete ICZ recreate exclusion removed, 582 destroy/10 add
  calls pinned to 513 identities. Final seven-class worker run passed 57 tests,
  zero skips. An intermediate 49-test run had three intended negative failures
  and two incorrect fixture assertions; corrected before the final pass.
- `19181819103d6f43aa494ef1893a637489099741`: conflict-free composition of all
  three isolated worker commits. Parent follow-up fixes the reviewed local/
  anonymous identity gap and updates the measured documentation.

T3 task IDs end in `architecture-ratchet-menu-animation-20261023-r1`,
`architecture-ratchet-sprite-ownership-20261023-r1`, and
`architecture-ratchet-rewind-lifecycle-20261023-r1`; all completed with clean,
retained owned worktrees. Native read-only reviewers are finished. No idle CLI
workers or native-session fallback registry was created.

## Combined validation result

Tested source: `d48d51304`, clean before execution, including the conflict-free
merge of `develop` at `aebb390ed` in `44c183e29`. No production or test changes
followed these runs; the final edits record results and completed queue status.
The incoming develop delta concerned measurement tooling and evidence, with no
ownership-code overlap or conflict. API descriptor, version, signature pins,
`ChannelContext`, frozen rule descriptions and update-disabled configuration
remain unchanged.

- Focused combined command above: **224 tests, 0 failures/errors/skips**.
  The 25 selectors matched 26 classes because `TestSonic3kLevelLoading` exists
  in two packages. All required ROM-backed and EGL controls executed. Queue
  wait was 945 seconds; Maven execution 1 minute 27 seconds.
- Complete separate `-Pguards` command: **727 tests, 1 failure, 0 errors/skips**.
  All 32 `TestArchUnitRules` checks, all 76 source guards, published accounting,
  rewind and lifecycle checks passed. Queue wait was 1032 seconds; Maven
  execution 5 minutes 18 seconds. The profile is not fully green.
- Sole failure:
  `TestBuildToolingGuard#canonicalDisassembliesStayOptionalAndTrackableWhileLocalReferencesStayIgnored`,
  line 4152: `the default developer setup must use an ordinary clone`.
  The test expects literal `git clone https://...`; the unchanged guide uses
  `git clone --branch develop https://...`. Both the test and guide are identical
  to the pinned pre-task base; the guide change predates this campaign.
- Matched attribution at `b979916808b7f9379ede2b5dcdde1bcdc8bf8357` in the isolated
  baseline worktree and at `d48d51304` in the development tree used:

  ```bash
  env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
    JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
    python3 tools/testing/maven_queue.py --lean -Dmse=off \
    '-Dtest=TestBuildToolingGuard#canonicalDisassembliesStayOptionalAndTrackableWhileLocalReferencesStayIgnored' test -B
  ```

  Each run produced **1 test, 1 identical failure, 0 errors/skips** at line 4152.
  XML test identity and assertion messages were compared directly. Baseline
  waited 834 seconds and executed for 1 minute 28 seconds; current waited
  833 seconds and executed for 32.307 seconds. Waiting intervals overlap and
  must not be added as elapsed delivery time. Total delivery time is unmeasured.

No new or worsened failures were observed within this bounded coverage. The
pre-existing tooling failure is left unchanged and does not block integration
under repository policy. The full 3621-class smoke selection, exhaustive traces
and release-only full suite were not run. No gameplay parity/frontier claim is
made beyond the named checks. Relative documentation links and `git diff --check`
passed. Diagnostic XML was consumed; owned worktree cleanup removes it rather
than archiving it. No category `--run` directories were created.

## Delivery and remaining work

The approved implementation is complete. Preserve `develop`'s unrelated dirty
submodules and untracked files. Integrate the verified source without switching
the main branch; check the integrated tree against the tested source, run branch
policy, push only `develop`, then remove the five fully accounted owned worktrees
(parent, three implementation workers and matched baseline) and their local
branches. Only setup links, generated config examples, build output and Python
bytecode were found outside tracked changes; original ROM/config/reference
locations remain untouched. Temporary candidate sketches are superseded by the
committed design. Integration/push/cleanup outcomes belong in the final delivery
message rather than a separate tracking framework.

Next architecture slice: the nine `MasterTitleRomPreview` dependencies, grounded
in provider ownership and host/donor behavior. Background-renderer lifetime is a
queued investigation. Remaining lifecycle and rewind inventories require
behavior-specific migrations, not blanket substitution or automatic refreezing.
