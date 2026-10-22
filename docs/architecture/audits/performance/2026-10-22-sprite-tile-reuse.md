# Bounded immutable sprite Tile reuse

Task base: `8aa692b045c195ef8a42896b6bbc9549158470c0` (develop).
Implementation workspace: `t3-ddec71b5`,
branch `bugfix/ai-sprite-tile-reuse-20261022`. The initial structural experiment
was followed by the isolated CPU/allocation comparison below. **Decision: reject
the production reuse optimization.** Allocation savings came with consistently
higher preparation CPU and wall time on representative normal inputs.

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

## Isolated CPU/allocation protocol

The lead authorized a quiet measurement window, holding later validation while
existing jobs finish normally. Dedicated measurement branch
`bugfix/ai-sprite-tile-measure-20261022`, workspace
`t3-ddec71b5/.worktrees/sprite-tile-measure-20261022`, starts at exact safe commit
`44d11628a9c881d0d24c37e3b3c19629759590d6`. Cowtree reported
`fallback=yes reason=copy-on-write-unavailable`; the checkout hook installed its
ordinary shared resource links. Its own `target/` was built with
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py
-Dmse=off compile -B` (exit 0, 1:02); no build tree was copied or shared.

The engine-facing probe is `com.openggf.tools.SpriteTileReuseProbe`, outside the
Mod API. It calls the existing public internal preparation overload directly in
both arms; no reflective access or signature change is needed. Probe prebuild
used Java 25 `javac --release 25 -cp <own target/classes plus resolved dependency
jars> -d target/classes src/main/java/com/openggf/tools/SpriteTileReuseProbe.java`
under this worktree's build lock and shared build-activity lease. Only resolved
dependency jar paths were reused from the inspected focused run, never compiled
classes from another worktree.

Pinned SHA-256 inputs before launch:

- Probe source: `9f60213286cec20aa21a1a6f01002d7a3e893eef4baea66d79c40d8e10592b0c`.
- Probe main class: `7df3ab9f0941a9ce6b3858b39f3396574feddbed2fb0a7d75bd25530a49c6703`.
- `SpritePresentation.java`: `5babcf91f58f5a3d1336cc14e6fbec0ff2943b94f2faa5451dc410c88cd781e7`.
- POM: `9d5f877837d473a7fa9fec1f62528abe1b724753bae1d2f1b4a71be5c9bcd5d8`.

The only source beyond that commit in the measurement checkout is the probe
above, identical to the source retained with the final evidence. Admission:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/performance/run_isolated.py --worktree . --cwd . --audit-report target/sprite-probe-isolation.json --estimate-seconds 180 -- python3 target/run-sprite-probe.py
```

The temporary launcher sequentially starts three fresh Java 25 JVMs with
`-Xms1g -Xmx1g --enable-native-access=ALL-UNNAMED -Djava.awt.headless=true`, quiet
test logging and their own runtime classpath, invoking
`com.openggf.tools.SpriteTileReuseProbe <fork:1..3> 4096 8192 6`. Every JVM first
checks 16 complete hinted/no-hint frame pairs per scene, exact expected identity
counts, and consumed-output equality. All seven cases then warm both arms for
three rounds of 4,096 frames. Six measured pairs per case each run 8,192
preparations per arm, alternate arm order independently of rotated scene order,
and check equal consumed checksums. Frames are published to a volatile escape
sink and sampled during consumption. The launcher bounds each JVM to 180 seconds.

All scenes emit 128 world tiles plus 32 screen-anchored HUD tiles. Cases are
WORLD idle/scroll, rebuilt equal Subject/Body idle/scroll, head-bearing shared
mask with rebuilt equal eight-pattern maps, rebuilt equal head masks while idle,
and rebuilt masks while scrolling. Head producers bind their 128 referenced
immutable art versions. This exercises value-equality costs and identity-gated
misses, rather than assuming all subjects are the WORLD singleton.

Each measured boundary includes input/Subject/map production, ordinary immutable
preparation, one initial no-hint seed frame and 8,192 output escapes/sample
consumptions. Allocation uses the executing thread's allocated-byte counter;
CPU uses that thread's CPU-time counter. Wall time and JVM GC counts/milliseconds
are observed separately. Whole-process GC/JIT work, GPU rendering, head anatomy
composition, ROM loading and full game-loop cost are outside this CPU boundary.
The no-hint arm is the current implementation's legacy delegate path, not a
separately rebuilt historical algorithm. This is a bounded paired synthetic
comparison, not a JMH or live-route/FPS result. Host activity outside linked
worktrees and sampling gaps in bypass detection remain limitations.

### Measured result and rejection

Session `60125` exited 0. Exclusive admission waited **311 seconds**; the
three-JVM command then ran **13.248 seconds**, 2026-10-22 11:54:07.661 to
11:54:20.909 UTC. The isolation audit confirms exclusive test and build-activity
leases, 0.5-second bypass sampling, exit 0 and no observed interference. The
launcher completed all three JVMs. All **252 observations** (18 paired samples
per scene) and paired checksums were inspected; there were no missing pairs,
verification failures or skips. This is 2,064,384 measured preparations plus
their 252 initial seed frames; warmup and equality controls are separate.

CPU nanoseconds and allocated bytes below are per-preparation medians. Ratio is
the median of the 18 individual paired hinted/no-hint CPU ratios, not the ratio
of the two displayed medians. Positive CPU change is a regression.

| Scene | CPU no hint / hint (ns) | Paired CPU change | Allocated no hint / hint (bytes) |
| --- | ---: | ---: | ---: |
| WORLD idle | 1496.7 / 1891.0 | +27.6% | 19354.4 / 7834.4 |
| WORLD scroll | 1495.9 / 1611.7 | +7.6% | 19354.4 / 17050.4 |
| Equal Subject/Body idle | 1599.0 / 2460.1 | +57.2% | 21146.6 / 9626.6 |
| Equal Subject/Body scroll | 1607.4 / 1708.5 | +6.9% | 21146.6 / 18842.6 |
| Shared head mask, equal rebuilt art maps | 6882.9 / 11448.1 | +66.3% | 53278.5 / 41758.5 |
| Rebuilt equal head mask, idle | 6981.6 / 7792.2 | +12.2% | 56606.9 / 54302.9 |
| Rebuilt head mask, scroll | 7041.5 / 7143.8 | +3.4% | 56606.9 / 54302.9 |

All three fresh-JVM median CPU ratios were above 1 for every case. WORLD idle
fork medians: **1.2120, 1.2949, 1.2939**; WORLD scroll: **1.0601, 1.0892,
1.0899**. Every individual pair was slower for both normal WORLD cases, despite
11,520 bytes/frame saved on full hits and 2,304 on HUD-only hits. Median wall
time also worsened in every case (WORLD idle 1512.9 to 1912.3 ns; WORLD scroll
1536.6 to 1634.8 ns). GC collection counts were observed but do not make the
executing-thread CPU counter a whole-process GC/JIT cost measurement. No FPS
claim follows from these synthetic results.

The rejection is based on the common paths as well as head-map comparison cost;
special-casing head reuse would leave the normal idle/scroll regressions. The
bounded implementation is therefore removed rather than broadened into packed
storage, pooling or caching. Initial reuse fractions remain structural counts,
not performance success. The safe identity fix is `44d11628a`; its measured
implementation is kept in Git history with this exact probe source. The final
tree keeps fresh immutable preparation, ownership/composition regressions and
a diagnostic probe that explicitly refuses timing when the rejected hint is
absent. No second timing batch is needed to establish removal of the losing
optimization. Consumed raw measurements/isolation reports are removed after
retaining this bounded summary, source pins and reproducible protocol.
