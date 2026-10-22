# Bounded immutable sprite Tile reuse

Task base: `8aa692b045c195ef8a42896b6bbc9549158470c0` (develop).
Implementation workspace: `t3-ddec71b5`,
branch `bugfix/ai-sprite-tile-reuse-20261022`. This is a bounded allocation
experiment within the authorized CPU/GPU investigation, not a timing benchmark.

## Decision and ownership

`SpritePresentation.Builder.add/addRows` previously constructed one escaping
immutable `Tile` per emitted tile on every preparation. The builder now checks
the caller's preceding immutable frame at the same ordinal **before** its sole
`new Tile` expression. A hit appends that existing Tile; a miss constructs the
replacement. Position compares first to reject moving-world inputs cheaply.
The comparison covers every Tile component, including complete Subject record
equality (head mask/transform/art/replay, body role/anchor, palette replay and
unknown producer IDs), with stricter mask/callback identity gates described
below. `Float.compare` matches record equality for NaNs and signed zero. There
are no scans, maps or matching searches for reuse.

The existing public preparation signatures delegate with no hint. New overloads
are engine-internal bridges, on unannotated classes, not creator API additions.
The existing API signature pin and internal-terminal checks pass unchanged.
The level renderer supplies `Tables.prepared()` for its world/SAT preparation
and `Tables.counters()` for its independent VBlank counter pass. No cache was
added to GraphicsManager. Those existing owners already reset and rewind their
frames; a throwing producer cannot replace either owner because preparation
must return successfully before assignment. Publication/lag order is unchanged.
The temporary builder holds only the preceding tile list, with no frame chain.

Tiles contain logical art addresses, not mutable pixels. Every new frame still
captures its own referenced PatternVersions. Sharing a Tile therefore permits
different immutable art generations at the same address. Primitive emission and
its tile boundaries are unchanged. Head composition runs after native emission;
its expanded/reordered previous frame remains only an ordinal hint. Correct
reuse also preserves identities observed by composition and opaque callbacks.

## Focused evidence

The ROM-free `TestSpriteTileReuse` uses actual headless preparation for scenes,
clips, frozen art, primitives, composition and failure cleanup. Its scalar
contract cases use a mock host solely to bypass setter clamps when checking
values such as signed alpha zero. Independent record-component substitutions
check that every changed scalar and Subject field replaces its tile.
`TestLevelSpriteTileReuse` calls the real LevelRenderer preparation/counter
methods with ROM-free HUD emission to check pending versus published state,
separate streams/worlds, failure/retry, reset and rewind candidate restoration.

Deterministic representative **synthetic** scene counts (160 emitted tiles):

| Input transition | Same-ordinal identities reused | New Tiles |
| --- | ---: | ---: |
| Unchanged stationary world + HUD | 160 / 160 (100%) | 0 |
| Camera scroll by 8 px; 128 world + 32 screen-anchored HUD | 32 / 160 (20%) | 128 |
| Further scroll plus reversed debris ordering/art | 32 / 160 (20%) | 128 |

The zero-construction conclusion combines these identity assertions with the
source structure: the hit branch returns before the only Tile constructor.
Lists, Attributes, Subjects, frame/map copies and composition allocations remain.
These counts are not live-route reuse rates and establish no measured CPU, FPS,
GC pause or GPU gain. Scrolling can have zero hits without a stable HUD; changed
pose, tile count/order or semantic metadata can similarly lose reuse. Equal
Subject metadata can require nested record/collection comparisons, so net CPU
benefit still needs an isolated measurement coordinated by the delivery lead.

Rejected alternatives: mutable pooling violates retained trails/rewind frames;
a graphics-global previous frame mixes world/counter/capture owners; full-frame
searches add unbounded matching work. No camera-wide bypass was added: scrolling
still reuses the screen-anchored HUD, and an early per-tile position comparison
handles the common miss. Packed primitive storage remains outside this task.

## Validation and handoff

Hooks installed with `tools/testing/install-hooks.sh`. Java probe and exact
focused Maven validation are queued through `tools/testing/maven_queue.py` with
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`. The existing absolute S3K ROM path is
the main workspace's root `s3k.gen`, CRC32 `63522553`, SHA-1
`cfbf98c36c776677290a872547ac47c53d2761d6`; no ROM copies/links were created.

Completed against the task base plus the four production changes and two new
test classes committed with this audit; no executable inputs changed after the
run. Java version probe: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3
tools/testing/maven_queue.py -v` reported Java **25.0.5**, exit 0 (386 seconds
queue waiting). Focused command, exit 0, finished 2026-10-22 12:18:53 BST
(only the verified ROM's absolute path is neutralized below for repository policy):

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSpriteTileReuse,TestLevelSpriteTileReuse,TestSpritePresentation,TestLevelSpritePresentation,TestLevelSpritePresentationLifecycle,TestModApiSignatureSurface#candidateZeroSevenSurfaceIsPinnedToTheCurrentSurface+engineInternalTerminalsArePinnedAndStayOutOfTheSurface,TestArchitecturalSourceGuard#lowLevelGraphicsAndAudioDoNotGainGameplayServiceLookups' '-Ds3k.rom.path=<absolute path to existing main-workspace s3k.gen>' test -B
```

**78 tests, zero failures/errors/skips**, confirmed in all seven XML reports:
new Tile reuse 45, new level-owner reuse 3, existing SpritePresentation 14,
LevelSpritePresentation 7, LevelSpritePresentationLifecycle 6, API pin/internal
terminals 2, exact low-level graphics service-lookup guard 1. This includes the
four ROM-backed Tails art cases and six real gameplay/act-reload lifecycle cases;
none skipped for prerequisites. Queue waiting was 307 seconds; Maven reported
1:13 including compilation. Those are validation-job observations, not performance
measurements. Consumed focused reports are removed after inspection;
`git diff --check` also passes. No category run/results cache was created.

No broad smoke, full suite, visual capture or performance timing ran here.
The lead owns combined smoke/guards, consolidated release prose, integration,
push and workspace cleanup. Concurrent production ownership is preserved:
GraphicsManager, PatternAtlas, PatternRenderCommand and
SpritePresentationRenderer.drawPicture are untouched; only the additive
preparation overload/delegate at the top of that renderer changes.

## Review correction: mask and callback identities

Independent review found that initial implementation `da2d53023` was unsafe for
a generic **raw** preceding-frame hint. `HeadMask.equals` accepts reconstructed
equal masks, while `PlayerHeadPresentation.sameDraw` groups native draw
occurrences with `mask() == mask()`. Reusing only one of two tiles could replace
its current mask with the old equal instance and change body/head ordering.
Normal LevelRenderer preceding tables have already been composed and tend to
strip that metadata; this is a reproduced generic-hint defect, not a claimed
stock gameplay reproduction.

The builder now requires the same HeadMask and HeadSatReplay instances for
head-bearing reuse, and the same PaletteReplay instance for every reuse.
Both callback interfaces are opaque: legal implementations can override
`equals` without encoding callback behavior. Identity is checked before the
remaining exact Subject value comparison. Equal Subject/HeadTransform records
with the same mask/callback instances remain eligible; changed scalar, body,
head-art or other metadata still prevents reuse. No public signature changed.

`TestSpriteTileReuseHeadComposition` prepares two native head tiles with a
shared occurrence mask, changes the second tile's geometry, and compares full
hinted/no-hint composition. Both same-instance and rebuilt-equal masks are
covered, including explicit body/body/head/head order and retained raw state.
Two callback tests deliberately define equal-but-behaviorally-distinct replay
implementations and check that the new callback is retained and invoked.
The scalar field matrix shares a fixed mask so identity gates cannot conceal
a missing scalar comparison.

Red command on `da2d53023` production plus the new regression inputs:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSpriteTileReuseHeadComposition,TestSpriteTileReuse#equalOpaqueHeadCallbacksMustKeepTheirIdentityAndBehavior+equalOpaquePaletteCallbacksMustKeepTheirIdentityAndBehaviorWithoutAHead' test -B
```

Completed 2026-10-22 12:36:30 BST: **4 tests, 3 failures, zero errors/skips**.
The rebuilt-mask parameter and both callback tests failed; the same-mask
parameter passed. XML identities were inspected and consumed reports removed.
Queue wait was 28 seconds; Maven execution including test recompilation 27.110
seconds. These are validation times, not benchmark results.

The corrected focused run adds `TestSpriteTileReuseHeadComposition` to the
previous exact selection:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSpriteTileReuse,TestSpriteTileReuseHeadComposition,TestLevelSpriteTileReuse,TestSpritePresentation,TestLevelSpritePresentation,TestLevelSpritePresentationLifecycle,TestModApiSignatureSurface#candidateZeroSevenSurfaceIsPinnedToTheCurrentSurface+engineInternalTerminalsArePinnedAndStayOutOfTheSurface,TestArchitecturalSourceGuard#lowLevelGraphicsAndAudioDoNotGainGameplayServiceLookups' '-Ds3k.rom.path=<absolute path to existing main-workspace s3k.gen>' test -B
```

Completed 2026-10-22 12:45:05 BST: **82 tests, zero failures/errors/skips**,
including 47 Tile reuse tests, two raw composition cases and the unchanged
33 existing/owner/API/architecture checks. All eight XML reports were inspected
and removed. Queue wait was 369 seconds; Maven execution including recompilation
1:21. Tested production is `da2d53023` plus the identity helper/gate; test inputs
are the fixed-mask scalar matrix, two callback boundaries and new composition
class committed with this correction. Probe timing is separate from these checks.
