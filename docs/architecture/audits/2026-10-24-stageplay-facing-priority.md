# Stageplay facing and directed priority — 2026-10-24

Base: `ac46111bc`, engine worktree `.worktrees/stageplay-facing`.
The Day Off source is `$DAY_OFF_PRODUCTION/scenes/build_scene.py`.

## Directed foreground occlusion

`StageplayObjectDraw.draw` flushed the enclosing sprite batch to bind its private
art/palettes, then emitted tiles without reopening a batch. Their direct commands
selected the shader at execution, after the enclosing sprite pass had disabled
the priority shader. Native low-priority pictures consequently painted over
foreground tiles. This is a batch-ownership error, independent of game, zone,
object identity, source priority bits or cast mode.

Reopen the batch while the part's native priority/mask is active, flush before
restoring that scope, and reopen the surrounding batch for subsequent sprites.
The template retains its native priority, tile attributes and palette mask.
No ROM behavior, art or GHZ-specific rule changes.

The first ordinary-publication comparison passed but did not exercise the live
cinematic command path. It was replaced by native and directed draws in the same
production cinematic view. A stock world alone cannot create that view: the
fixture must adopt the Stageplay world module's directed scroll service. Comparing
an old capture session's ordinary picture with the adopted world also compares
different publication/background owners; that was rejected as an oracle.

The final regression uses the real S1 Motobug constructor/art, four GHZ loop
positions, a frozen native graph and a separately installed baked visual. A
no-occlusion control proves that foreground actually hides sprite pixels, and
an empty picture rejects blank or stale comparison success. With the old draw
scope, the native/directed comparison differed by 688 pixels at X=5580. Reopening
the batch fixes the comparison. No ROM pixels are stored in the repository.

Validation on the base plus this change (Java 25, surfaceless EGL, S1 REV01 SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`):

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path="$OPENGGF_MAIN/s1.gen" \
  -Dtest=TestStageplayObjectPriorityRendering,TestStageplayObjectDirection test -B
```

Completed 2026-10-24 10:04 Europe/London: 30 tests, 0 failures/errors/skips.
This is focused validation, not broad smoke or release-suite validation.


## Logical facing ownership

Day Off's `drive` moves Motobug toward positive X with `flip=False`; that native
art faces left. Its Tails `flip=True` correctly faces left because player art has
the opposite basis. The same raw-art/semantic-direction mistake appears in
Eggman's Sky (`69cbe6b83`) and Super Sonic Bros (`ff5088abf`). These establish a
repeated class rather than a single bad shot.

The author-facing contract now uses one `Facing.LEFT/RIGHT` type in both player
and object keys, screenplay fields and both editor controls. Native render flips
remain internal presentation output. Canonical document version 8 and screenplay
version 7 replace the unpublished formats directly; old raw `flipX` screenplay
fields fail with the replacement named. The 0.7 candidate API signature pin is
updated without changing its release-policy descriptor or published pins.

Facing means native **logical direction**, as explicitly chosen by the user.
Native turn, skid and look-back frames retain the art that native play draws.
Rhinobot mapping frame 3 is deliberately opposite its ordinary travel art; it is
not normalized into a different pose. Player facing is converted at the native
player/presentation boundary.

A universal formula is unsupported: Motobug and Ball Hog have different art
polarity; animals use their actual native flip predicate (including zero
velocity); bosses may own direction independently of generic render flags.
Mechanical mounting geometry and animated angle poses need not have a shared
horizontal body-facing transform. Every factory allocation therefore needs an
explicit native `ObjectVisualFacing.Source` declaration, including explicit
`NonDirectional`, `Container` and `PoseOwned` declarations where justified.
There is no RIGHT, placement-bit or neutral fallback on `AbstractObjectInstance`.
The bytecode guard follows registry factory lambdas, helpers, S3K pointer-table
branches, zone overrides and auxiliary animal factories without instantiating
them. Runtime capture also rejects unresolved concrete appearances. Declaration
coverage proves ownership is explicit; it does not by itself prove each polarity.

Capture freezes the resolved native basis alongside the picture. A requested
opposite logical facing mirrors the complete native assembly around its root
anchor, retaining child offsets and native relative flips. `Container` roots
instead retain their own geometry and capture independently owned child groups;
each group's transform uses its native anchor. Native pose-owned direction and
non-directional geometry retain their source presentation. This does not turn
placement asymmetry into an invented logical RIGHT default.

Constructor-only baking uses deliberate source flags independent of requested
facing. Existing placements keep their exact flags. A newly authored Free object
uses a constructor-facing mapping only when its native owner declares
`PlacementSource`; otherwise initialization and AI retain authority. Directed
Puppet/Pose pictures resolve their resulting native basis and transform once.
This separates source construction from picture mirroring, avoiding double flips.

The native placement/render-state fixes `4a0b498cc`, `cbf7b66e8`, `43badd5f1`,
`45cecf566` and `6ab17a820` are another repeated symptom group, but their owners
and flag meanings differ. Stageplay semantic facing does not repair dropped
native state. A global placement XOR would double-apply already-owned flips and
break independent native state. `65d622c9f` fixes an art bank, while `ddbc651e0`
removes invented MonkeyDude player-facing turns/anchor movement (including the
approach in `1f37ab726`); those are distinct ROM details, not evidence for a
universal direction formula. Native gameplay routines remain authoritative.

The enforced rule table pairs semantic key facing/native ownership with
`TestStageplayFacingGuard`, `TestNativeObjectFacingGuard`, focused native-state
checks and native pixel comparisons. The separate priority row pairs preserved
foreground occlusion with `TestStageplayObjectPriorityRendering`.

## Facing validation

Validation uses this worktree based on `ac46111bc`, plus priority fix `1700f5a553`
and the facing changes. Broad smoke and integration belong to the lead and were
not requested here. Offscreen tests unset `DISPLAY` and `WAYLAND_DISPLAY` and use
surfaceless EGL. No fixture pixels or ROM assets are committed.

The ROM-free focused selection passed 189 tests, 0 failures/errors/skips:

```sh
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dtest=TestNativeObjectFacingGuard,TestStageplayFacingGuard,TestObjectVisualFacing,TestStageplayObjectDirection,TestStageplayObjectCast,TestStageplayDocument,TestStageplayStore,TestStageplayTimeline,TestStageplayScriptCompiler,TestStageplayScriptDecompiler,TestStageplayScriptRoundTrip,TestStageplayObjectScriptCompiler,TestStageplayWorkspace,TestStageplayPuppetWorkspace,TestStageplayObjectWorkspace,TestStageplayGroundWorkspace \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true test -B
```

That run initially found an omitted LRZ miniboss declaration, the shared invisible
host-gizmo path, two obsolete fixture expectations, and a child graph fixture
that installed the director after child notifications. Corrections retained full
factory coverage and native ownership; the repeat selection passed.

The ROM selection used all three absolute `sonic1.rom.path`, `sonic2.rom.path` and
`s3k.rom.path` properties, plus the same EGL and single-fork flags:

```sh
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path="$OPENGGF_MAIN/s1.gen" \
  -Dsonic2.rom.path="$OPENGGF_MAIN/s2.gen" \
  -Ds3k.rom.path="$OPENGGF_MAIN/s3k.gen" \
  -Dtest=TestStageplayObjectPriorityRendering,TestStageplayObjectFacingRendering,TestStageplayObjectCastRom,TestStageplayTailsRom,TestStageplayUnifiedWorldRom,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true test -B
```

It passed 78 of 79 tests, with no skips; only the new Rhinobot fixture failed.
Its first capture correctly rejected dormant Map_Offscreen. Native dispatches
through the object manager admit the real wait/sign-scan/release/Init sequence.
The anti-blank control then rejected the native initial black palette reveal.
Matching the production Stageplay `withoutDisplayedFade` draw scope resolved
that fixture issue. The final focused rerun of
`-Dtest=TestStageplayObjectFacingRendering` passed both Motobug and Rhinobot tests,
0 failures/errors/skips, with absolute S1/S3K ROM properties. The other 78 ROM
checks were not repeated after these test-only fixture changes.

The pixel checks compare native rendering for both source placement flips and
both requested logical directions, including Rhinobot frame 3. Other focused
checks cover child-group anchors, Free/direct recapture, independent boss state,
animal zero/negative velocity and preservation of unrelated placement bits.
These are bounded behavioral checks, not exhaustive per-object polarity proof.

The historical enforcement proof loaded the actual compiled `ActorKey`,
`ObjectKey` and Motobug class bytes retained from `1700f5a553` before facing edits.
The same `TestStageplayFacingGuard.requireSemanticFacing` predicate rejected both
old records with the `Facing.LEFT/RIGHT` replacement named. The native factory
guard's `Source.class.isAssignableFrom` predicate rejected that actual undeclared
Motobug class. A temporary Java source-mode harness invoked those predicates via
queued `exec:exec`; no historical code or build tree was copied into another
worktree. The committed guard also rejects the real Day Off raw-field recipe.
Temporary bytecode and proof outputs are consumed diagnostics, not repository
assets. The priority regression separately failed on its real pre-fix draw scope,
as recorded above.

The first separate `-Pguards` invocation completed 777 tests with 775 passing,
0 skips and two `TestArchUnitRules` failures. Placing the new author enum in
`physics` added `editor -> physics` and made pure Stageplay document values depend
on runtime physics. That placement was rejected. The final author type is
`StageplayDocument.Facing`; native owners retain their independent `Basis`
contract, and `StageplayObjectCast.nativeFacing` translates at the existing
adapter boundary. No architecture ratchet, hook or build policy was changed.

The matched fresh-JVM rerun `-Pguards -Dtest=TestArchUnitRules test -B` passed all
34 architecture rules, 0 failures/errors/skips, after moving the enum to the pure
document model. Other guards from the completed profile were not repeated: their
rules and affected behavior were unchanged. The current native/key guards and
API signature checks are included again in the final focused selection below.

Final engine focused selection (2026-10-24 11:45 Europe/London) passed **261
tests, 0 failures/errors/skips** on the final typed boundary and candidate pin:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path="$OPENGGF_MAIN/s1.gen" \
  -Dsonic2.rom.path="$OPENGGF_MAIN/s2.gen" \
  -Ds3k.rom.path="$OPENGGF_MAIN/s3k.gen" \
  -Dtest=TestNativeObjectFacingGuard,TestStageplayFacingGuard,TestObjectVisualFacing,TestStageplayObjectDirection,TestStageplayObjectCast,TestStageplayDocument,TestStageplayStore,TestStageplayTimeline,TestStageplayScriptCompiler,TestStageplayScriptDecompiler,TestStageplayScriptRoundTrip,TestStageplayObjectScriptCompiler,TestStageplayWorkspace,TestStageplayPuppetWorkspace,TestStageplayObjectWorkspace,TestStageplayGroundWorkspace,TestModApiSignatureSurface,TestStageplayObjectCatalog,TestStageplayRenderer,TestStageplayTool,TestStageplayLiveContract,TestSeesawBallTouchState,TestS2HtzSeesawMultiSidekick,TestSeesawBallGraphRewind,TestStageplayObjectPriorityRendering,TestStageplayObjectFacingRendering,TestStageplayObjectCastRom,TestStageplayTailsRom,TestStageplayUnifiedWorldRom \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true test -B
```

The historical bytecode proof was repeated successfully against the final
`StageplayDocument.Facing` guard. Original native comments and mixed line endings
were retained during cleanup; no executable tokens changed in that cleanup.
Eight bundled canonical examples and one screenplay passed JSON/version/facing
syntax checks. Agent documents and Stageplay skill mirrors match. Candidate
signature verification passed all 11 tests within the final selection; native
facing helpers remain outside that creator surface.

Desktop validation used the requested `scripts/install-engine.sh` from the Editor
worktree against this engine worktree. Its Maven calls ran through a temporary
queue-wrapper shim, including the version query and private-repository install;
no project script, hook or scheduling policy was changed. It installed
`OpenGGF 0.7.prerelease` into the Editor's private Maven repository. Then:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dtest=TestStageplayChrome,TestChromeDiscovery,TestEditorChromeLayout package -B
```

Completed 2026-10-24 11:50 Europe/London: package built, **23 tests, 0
failures/errors/skips**. Screenshot refresh tests were compiled but not selected.
The inspector, three affected fixture files and README are the only Editor
changes. Its base is `6812543`; both task branches remain local for lead-owned
integration. No push, merge or broad smoke was performed.

The optional repository-wide `python3 tools/disasm/disasm_citations.py check`
exited 1: 122 missing-file and 35 out-of-range references; 3,368 stale anchors
were also reported. The local disassembly checkouts differ from pinned reference
revisions. A bounded comparison using the checker's parser on every changed file
against `HEAD` found **zero newly introduced citations**. Pins and existing
citation text were preserved; this pre-existing corpus issue was not expanded
into unrelated fixes. Diagnostic outputs and temporary bytecode/proof/shim files
are consumed and removed after recording these results.
