# Mod audit remediation execution — 2026-10-12

Execution of the [remediation plan](../plans/2026-10-12-mod-audit-remediation.md).
The initial implementation base is `15f3cf371a49e9755568588b00b1f515225aaa4d`
on `develop`. Work is isolated in `feature/ai-mod-remediation` and three owned
worker trees; unrelated main-workspace files and dirty disassemblies are preserved.
This record distinguishes checks already observed from pending qualification.

## Initial evidence and checks

- Java is OpenJDK 21.0.12.1. Existing S1 REV01, S2 REV01 and locked-on S3K ROMs
  matched the CRC32/SHA-1 identities in repository guidance; no assets were copied
  or renamed.
- Root `python3 tools/testing/maven_queue.py -Dmse=off test-compile` passed
  at the initial base plus regression sources (1m14s). This is compilation,
  not a test-suite pass.
- Creator export regressions failed because Flappy's authored `CourseTest.java`
  and a fixture's test/resource/generator inputs were absent. Shared-builder
  regression rejected its missing output override. SDK-template checks failed
  because both generic/simple templates omitted `creator-tests`.
- After the export/builder/template changes,
  `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/modding -p test_creator_kit.py`
  passed 16 tests. This structural Python pass does not qualify gameplay; actual consumer results follow below.
- Initial Maven requests waited behind an unrelated Java migration run; later admission
  proceeded normally. No unrelated queue lock or process was changed.

## Ownership and outstanding work

Root owns Three Islands/Slay correctness, creator tooling, shared save/UI helpers,
combined API pinning and integration. Workers own Flappy/Sitar/Hello/Tower/Practice
save/input changes plus their catalogues; Egg/Starfall decoding/world/cache changes;
and Survivors settlement/catalogues/frame-control changes. The plan remains the
completion ledger; no wave is declared delivered by this initial record.

Subsequent sections will record actual failing/passing regression identities,
API decisions, integrated commits, broad selections, skips and baseline attribution.
Raw Maven logs and diagnostic directories are temporary and are not archived here.

## Observed implementation qualifications

Root checks are at `8c69d75a1` plus the recorded uncommitted implementation, not yet integrated:

- Queued lean `TestThreeIslandsExample,TestSlayTheRobotnikExample`: four wrapper tests
  passed, zero skips. Their authored suites were also launched through actual external
  creator-kit Jupiter: Three Islands 86 checks, Slay 132, all passing with no skips.
  This portable model qualification uses no ROM and does not certify pixels/audio.
- Queued `TestSlayTheRobotnikScene,TestThreeIslandsScene,TestModTestKit`, with all
  three verified absolute ROM properties: 27 tests passed, zero skips, 33.478 s Maven
  execution after 121 s admission wait.
- Queued lean `TestProjectScaffolder`: two tests passed, zero skips. An actual generated
  scene starter's packed-mod registration and testkit scene-state inspection passed two
  external Jupiter checks. The testkit-only `scene()` inspection helper is committed as
  `8c69d75a1`; callbacks continue through `tick`/`draw` and their owner boundary.
- Queued lean `TestModSaveSlot,TestModStorage`: 25 passed, zero skips. After actual
  Three Islands/Slay migrations plus the preserved replacement/counter edge regressions,
  adding their two example selectors passed 29 wrapper/storage checks, zero skips,
  1m19s Maven execution. New public API pins/gates remain pending at this checkpoint.
- Input/save worker's initial batch is `7947bd9a90f1ab985d89a3464f19732912daeec8`.
  World worker's initial batch is `51f16847c`: portable Egg 25/25 and Starfall 53/53;
  engine focused checks 64/64 plus corrected actual owned-close lifecycle 1/1, zero skips.
  These are local worker results, not a main-branch delivery claim.
- Survivors' production registry restore reproduced a doubled death settlement:
  bank 55 became 110. The bounded new lifecycle repair passes its death/retire/victory/
  failed-write/old-run checks; the first full domain run exposed two migration regressions
  caused by eager ID reservation writes. Reserving an ID no longer writes a file;
  the matched eight-test migration/settlement subset passed. Full repaired qualification
  and the related additive milestone replay check remain pending.
- Flappy/Tower/Sitar prepared-table checks measured 1,000 distinct lookup values before
  the change and one afterward, with pinned content/RNG hashes unchanged. Portable
  affected checks passed 120; normal engine selector passed 110, zero skips, including
  the ROM-backed complete Sitar song catalogue (513 s for that class).

These counts distinguish wrapper Jupiter tests from the nested authored checks.
No full ordinary/guard suite, complete graphics route or final artifact qualification
has been claimed by this checkpoint.

## Shared slot/presentation and Infinite checkpoint

The root implementation now includes P12's `ModSaveSlot` and P14's composition/
canvas scopes, with Three Islands and Slay profile/composition migrations and Sitar
canvas migration. Local commits have not yet been integrated into main `develop`.

- Queued lean `TestSceneArtHelpers,TestCreatorUi,TestRecordingCanvas,TestSlayTheRobotnikExample,TestThreeIslandsExample`:
  29 wrapper/helper tests passed, zero skips (1m23s). The first test compilation failed
  on a missing `assertThrows` import; it was corrected before the completed run.
- Queued normal `TestInfiniteSonic` plus the six common API gate classes with S1 REV01:
  312 tests, two failures confined to exact signature pins, 32 structural assumptions
  skipped. Infinite's 241 tests had no failures/errors; the stock native collision,
  course/rewind, top-ten/tie and owned-storage regressions ran. First compilation of
  the new derived trait cache rejected a missing required `RewindTransient.reason`;
  that annotation was corrected before this completed domain run.
- Signature generation was rerun after compilation, adding the 142 missing helper
  signatures. A prior attempt ran during compilation and used an older inventory;
  another lacked dependencies and was rejected. Neither was accepted as a valid pin.
- A combined pin/Sitar/Slay request was accidentally submitted to the lean lane,
  cancelled as incomplete, and rerun in the normal lane. The completed normal
  `TestModApiSignatureSurface,TestModApiPinPolicy,TestSitarHeroCareer,TestSitarHeroArcade,TestSlayTheRobotnikExample`
  with verified S3K: 63 tests passed, zero skips, 32.633s. Slay's nested authored suite
  passed134 checks, including targeted cards/monitors and full saved-state comparison
  at no/sparse/duplicate draw cadence.
- Creator Python safety/export checks remain16/16 after preferring an authored legacy
  check program's `main` when present, so its private checks are not silently omitted.
- Local integration merges: input/save/definitions `9d26f4d04`; world/camp/art diagnosis
  `94ce59bf6`; Survivors once-per-run settlement `1fbca056f`. Creator tooling checkpoint
  is `edec25cad`. Worker P08 Survivors `85796e996` is the next local integration.

Final matching SDK-only exports, graphics/pixel qualification, broad ordinary/guards,
remaining capabilities and main-branch delivery remain outstanding. Candidate version
and release status stay0.7.0/unpublished; no published API baseline was changed.

## Published upstream reconciliation and Java 25 boundary

`8172c0780` composes all initial worker/API batches with published `develop`
`b6d0ff76f` (Java 25 migration and expanded Mutator Lab). Seven conflicts were resolved
by retaining checked exports/shared builder output/definition prose and adopting the
upstream compiler 25/native-access requirements. Added creator-test compilation and
profiles also use 25 and explicit native access. Existing disassemblies, archives,
launch scripts and player settings remained untouched.

Java 25 reconciliation check: queued normal commonAPI+ProjectScaffolder+
TestModExamplePersistence selector 93/93 passed, zero skips,1m14s. The mistakenly named
TestControlledFrameRuntimeNativeInput selector matches no class; no controlled-runtime
qualification is inferred from it. The actual TestControlledFrameRuntime/CourseControl
and new Mutator overlay integration are separately required. Creator Python safety
suite remains 16/16. Build-only package passed 40.780s; bundled Time Attack strict package
reported zero findings. This package is a provisional matching8172 source identity,
not the final kit/install qualification.

The updated destination base is pinned at b6d0ff76f73b3e57165e9d475f0c95496eb762ad.
Baseline dry-run selects 3,144 ordinary classes plus all guards. Preflight initially
rejected host default Lua 5.5, then passed with JAVA_HOME=/usr/lib/jvm/java-25-openjdk
and LUA_BIN=/usr/bin/lua5.4. No alternatives or host tools were changed. Baseline run
20261012T154326Z-11024d8f began after 333s queue wait; its complete outcome remains
pending here. Execution timeout 40 minutes/no-output 10 minutes, with timeout treated as
incomplete. The candidate union and post-integration checks remain required.

Installed inventory at this checkpoint: Egg jar SHA256
6eaf6ed5edd4928881df9fbf8abbcb50a5eebe70bab013f7a333d3759618bb22 is unrelated and untouched;
old sample-Flappy SHA256 2e8d21c4c44ec9b30cf201919000d75260cae31f619da7c59496f65663084ed6
is disabled/untrusted in modstate. Rebuild/replacement qualification will preserve that
player choice, use isolated trust/Apply/startup checks and retain its rollback outside
mods. No player enablement/trust/saves have been changed at this checkpoint.

## Remaining source/API composition checkpoint

Local root `17b94be0f` merges `df7a9a799` art lifecycle repairs for Slay and Three
Islands. The actual provisional matching Java 25 creator runs pass Slay142/142 and
Three Islands95/95, zero skips and strict package zero findings. Canonical engine/ROM
selectors remain queued; these external checks do not certify native scene pixels.

The pending root qualification strengthens Native Flappy's existing integration
fixture with real isolated two-confirm trust, title Apply, persisted-hash rescan and
native launch. `sample.flappy.jar.path` chooses a real SDK-built binary instead of
recompiling source. Player installed enablement/trust stays untouched. A new offscreen
Slay check compares actual ROM boss composition pixels with the pre-uplift ordered
part emitter for origins/scale/mirrored children at three animation samples; availability
and skips will be recorded after its queued result, not inferred from compilation.

Time Attack's remaining settings/custody source is stable on the worker: dirty snapshot
retry/exit/LAN publication, bounded uploader admission/dedup/close, and bounded round
recording storage preserving the original unexpired-body policy. Initial regressions
were five engine custody failures plus eight actual creator scene/TLS failures; final
provisional external consumer run38/38, zero skips, strict package zero findings.
The normal ten-class engine request remains the commit gate.

The 28-manifest provisional ledger reports52 actual NON_API_ENGINE_REFERENCE warnings:
Infinite25, Golf9, Survivors18, with zero findings in the other25 sources. All28 packages
passed against matching8172 Java25 artifacts. Converter staging was initially wrong
in four sources because binary-assets.properties must be removed after decoding;
only those staging recipes were corrected/rerun. Tide's22 authored assets and gallery
waveform were generated, Three Islands Maps generator matched source exactly. Final
new-identity requalification and source-qualified warning dispositions follow separately.

## Updated-base timeout (not a baseline suite pass)

Baseline `b6d0ff76f` run20261012T154326Z-11024d8f stopped at the declared40-minute
invocation limit after2401.33s. It had22720 reported tests,0 failures/errors and57
skips in2593 XML reports; ordinary validation was incomplete at
TestS3kDdzCompatibilityMatrix and guards did not run. The process was still producing
route output, so this is a cost limit rather than evidence of a deadlock or pass.
Skipped outcomes were inspected: 32 Infinite terrain/fixture assumptions, 5 GL/EGL
availability checks, 1 CPZ spin-tube interaction assumption and19 opt-in checks.
Results were consumed and the run acknowledged/deleted. Full baseline qualification
will be rerun unchanged with120-minute execution and10-minute no-output limits,
consistent with the observed current full-run cost; no selection is removed.

## Historical Java 25 consumer qualification: 28 manifests

The worker at `df7a9a799` compiled the maintained inputs for all 28 tracked manifests,
including the four Flappy tutorial projects, bundled TimeAttack, gallery music sample
and nine test fixtures. This used the provisional engine/SDK/testkit artifacts built
at `8172c0780`, all reporting `app.dirty=false`, version/base version
`0.7.prerelease`. It precedes upstream `f02bdbfd` and its 29th manifest,
Starpost Valley; that new source/API scope requires a new matching qualification.
These results establish packaging/validator behavior, not gameplay, visual, audio,
rewind or final delivery qualification.

The three artifact SHA-256 identities were:

| Artifact | SHA-256 |
|---|---|
| Engine | `a84a467ba807837a0d874626a4a0b91e830416d3934bfab54d81f8ee54239fda` |
| SDK | `fbacfec56ec06f04208fc8b36c1e11a85ed6ddd0fc1a2eb0c91df7b2047f8159` |
| Testkit | `8ec563e3453c3995f163fc260f7df1dd61cc92ba2ca165206205c40f877474cf` |

Compilation used Java 25 `javac --release 25` with only the engine and SDK jars on
the compile classpath. Task-owned external outputs replayed maintained SDK converter
arguments, then invoked `GgfModCli package --input <classes> --out <jar> --format json
--warnings allow` and `validate <jar> --format json --warnings allow`. Each pair
returned identical reports: 28 valid packages, zero errors, 52 warnings. No ROM,
player state or existing `target` tree was copied. Builds were bounded to two workers
and 1 GiB SDK JVMs. Authored base64 resources were decoded; Tide's generator and
`--check`, gallery waveform generator, object/playable art and TMX/from-export
converters ran. Three Islands' Maps generator exactly reproduced its maintained
`Maps.java`; that maintained source was compiled. Existing authored Egg voice
resources were used without regenerating voice content.

The first staging attempt incorrectly left `binary-assets.properties` alongside the
strict level converter's exact input inventory, failing four fixture conversions.
Removing that staging-only properties file and rerunning those four projects repaired
the harness; this was not a source defect. Reports below are the completed run.

| Maintained source root | Java sources | Validator warnings |
|---|---:|---:|
| `docs/modding/samples/phase4-gallery-music-pack` | 0 | 0 |
| `examples/act-practice` | 3 | 0 |
| `examples/eggmans-sky` | 65 | 0 |
| `examples/example-mutators` | 10 | 0 |
| `examples/flappy-tails` | 16 | 0 |
| `examples/flappy-tails/tutorial/part-2-first-scene` | 2 | 0 |
| `examples/flappy-tails/tutorial/part-3-tails` | 3 | 0 |
| `examples/flappy-tails/tutorial/part-4-flight` | 4 | 0 |
| `examples/flappy-tails/tutorial/part-5-pillars` | 7 | 0 |
| `examples/hello-scene` | 2 | 0 |
| `examples/infinite-sonic` | 31 | 25 |
| `examples/putt-putt-paradise` | 36 | 9 |
| `examples/robotnik-tower-defense` | 12 | 0 |
| `examples/sitar-hero` | 41 | 0 |
| `examples/slay-the-robotnik` | 171 | 0 |
| `examples/sonic-survivors` | 25 | 18 |
| `examples/starfall-frontier` | 16 | 0 |
| `examples/three-islands` | 51 | 0 |
| `racing/time-attack` | 40 | 0 |
| `src/test/resources/mods/dynamic-rewind-src` | 1 | 0 |
| `src/test/resources/mods/sample-character-src/project` | 3 | 0 |
| `src/test/resources/mods/sample-flappy-src/project` | 3 | 0 |
| `src/test/resources/mods/sample-mod-src/project` | 3 | 0 |
| `src/test/resources/mods/sample-platformer-src/project` | 5 | 0 |
| `src/test/resources/mods/sample-reskin-src` | 0 | 0 |
| `src/test/resources/mods/sample-rom-art-remix-src/project` | 2 | 0 |
| `src/test/resources/mods/sample-standalone-src/project` | 4 | 0 |
| `src/test/resources/mods/sample-two-act-campaign-src/project` | 2 | 0 |

### Actual findings and their dispositions

This historical 52-pair finding set is superseded by the clean `175531f6d`
45-pair ledger below. Its purposes and action-owner definitions remain preserved.

Every finding was `WARNING / NON_API_ENGINE_REFERENCE`, with an empty member field.
The tables preserve every reported referencing-class/referenced-class pair. Purposes
and proposed alternatives are source-qualified reviews, not runtime certification.
`com.openggf` is omitted from referenced type names. Dispositions are **existing
alternative**, **narrow missing service**, or **retained expert bridge**. The action
key links each pair to an owner and an explicit reopen condition below; retaining a
bridge does not create a compatibility promise or mark the capability work complete.

| Infinite referencing class | Referenced type | Actual call purpose | Disposition / action |
|---|---|---|---|
| `CourseBadnik` | `graphics.RenderPriority` | Validate species priority bucket | Retained expert bridge / A |
| `CourseBadnik` | `level.objects.TouchResponseListener` | Guard absorption before native hurt | Narrow missing service / B |
| `CourseBadnik` | `physics.TrigLookupTable` | Orbinaut quarter-turn spikes, integer sine/cosine shifted by 4 | Retained expert bridge / C |
| `CourseBurst` | `graphics.RenderPriority` | Explosion bucket 1 | Retained expert bridge / A |
| `CourseController` | `level.rings.LostRingObjectInstance` | Delay terrain rebasing until live unshifted ring spills settle | Retained expert bridge / D |
| `CourseController` | `sprites.NativePositionOps` | Safe continue position with reset subpixels and history | Retained expert bridge / E |
| `CourseController` | `sprites.animation.ScriptedVelocityAnimationProfile` | Copy native locomotion profile with lower run threshold | Retained expert bridge / F |
| `CourseHazard` | `graphics.RenderPriority` | Hazard bucket 4 | Retained expert bridge / A |
| `CourseHazard` | `level.objects.TouchResponseListener` | Shield/20-ring guard absorption and native flashing before hurt | Narrow missing service / B |
| `CourseHazard` | `physics.TrigLookupTable` | Exact rolling, chain and wrecking-ball coordinates | Retained expert bridge / C |
| `CourseMonitor` | `graphics.RenderPriority` | Native monitor bucket 3 | Retained expert bridge / A |
| `CourseMonitor` | `level.objects.TouchResponseListener` | SPECIAL hit burst and item reward once | Narrow missing service / B |
| `CourseRing` | `graphics.RenderPriority` | Ring bucket 2, sparkle bucket 1 | Retained expert bridge / A |
| `CourseRing` | `level.objects.TouchResponseListener` | SPECIAL collection gated by native flashing/control state | Narrow missing service / B |
| `InfiniteSonicMod$Module` | `data.RomManager` | ROM authority inferred from `GameServices.rom().getRom()` | Retained expert bridge / G |
| `InfiniteSonicMod$Module` | `game.GameServices` | Current restored controller lookup and supplied title ROM | Retained expert bridge / G |
| `InfiniteSonicMod$Module` | `game.sonic1.Sonic1` | Independent stock reader for act-1 title backgrounds, preserving session PLC ownership | Retained expert bridge / G |
| `TitleBackground` | `game.sonic1.scroll.Sonic1ScrollHandlerProvider` | Separate stock Deform handlers produce packed scanline scrolls | Retained expert bridge / H |
| `TitleBackground` | `game.sonic1.titlescreen.Sonic1TitleScreenManager` | Reserved atlas bounds and viewport tile-column policy | Retained expert bridge / H |
| `TitleBackground` | `game.sonic1.titlescreen.Sonic1TitleScreenManager$BackgroundOverride` | Replace Plane B/backdrop while retaining stock title behavior | Retained expert bridge / H |
| `TitleBackground` | `game.titlescreen.SegaPaletteFade` | Native fade precedence and 12-frame transition with full palette copies | Retained expert bridge / H |
| `TitleBackground` | `game.titlescreen.SegaPaletteFade$Mode` | Select native title fade mode | Retained expert bridge / H |
| `TitleWordmark` | `game.sonic1.titlescreen.Sonic1TitleScreenManager` | Attach background to the native title manager | Retained expert bridge / H |
| `TitleWordmark` | `game.sonic1.titlescreen.Sonic1TitleScreenManager$BackgroundOverride` | Keep stock emblem/state machine while replacing background | Retained expert bridge / H |
| `ZoneMenu` | `game.GameServices` | Native title selection `SFX_SWITCH` | Narrow missing service / I |

| Golf referencing class | Referenced type | Actual call purpose | Disposition / action |
|---|---|---|---|
| `GolfMode` | `game.GameServices` | Native scene capture/presenter, focused camera, viewport/HUD/trail/guide overlay and guest gate | Retained expert bridge / J |
| `GolfModule` | `game.GameServices` | Native title graphics, projection width, character/sidekick configuration and menu audio | Narrow missing service / I,J |
| `GolfOnline` | `game.GameServices` | Publish/accept scene frames, peer viewport, focused radius and charge/release cues | Retained expert bridge / J |
| `GolfSounds` | `game.GameServices` | Native title/feedback track IDs and act-clear cue | Narrow missing service / I |
| `GolfSwing` | `game.GameServices` | Focused native springing/angle/speed state and launch/landing-spin audio | Existing partial alternative; retained expert bridge / J |
| `objects.GolfUpSpring` | `game.sonic2.objects.SpringObjectInstance` | Native Obj41 subclass retains ROM geometry, art, strength, animation and SFX | Retained expert bridge / K |
| `objects.GolfUpSpring` | `sprites.NativePositionOps` | Preserve-subpixel -8 nudge compensates top-only `loc_189CA` entry | Retained expert bridge / E,K |
| `ui.GolfTitleArt` | `game.sonic2.titlescreen.TitleScreenDataLoader` | ROM title planes/palette/pattern GPU cache and reset after course | Retained expert bridge / L |
| `ui.GolfTitleArt` | `game.sonic2.titlescreen.TitleScreenMappings` | Ordered priority mapping pieces and crown/wind frames | Retained expert bridge / L |

| Survivors referencing class | Referenced type | Actual call purpose | Disposition / action |
|---|---|---|---|
| `ArenaObjects` | `level.objects.ObjectConstructionContext` | Inject construction services, suppress probe admission, then admit rewindable auxiliary object | Retained expert bridge / M |
| `Boss` | `graphics.RenderPriority` | Boss bucket 3 | Retained expert bridge / A |
| `Boss` | `level.objects.TouchResponseListener` | Native player contact: guard absorption then laugh, excluding CPU/dead players | Narrow missing service / B |
| `Draw` | `level.render.SpritePieceRenderer` | Expand/mirror mapping pieces into scaled world-space quads | Narrow missing service / N |
| `Draw` | `level.render.SpritePieceRenderer$TileConsumer` | Preserve piece tile, palette and priority bits while emitting scaled quads | Narrow missing service / N |
| `Enemy` | `graphics.RenderPriority` | Enemy bucket 4 | Retained expert bridge / A |
| `Enemy` | `level.objects.TouchResponseListener` | Guard absorption at actual native player contact | Narrow missing service / B |
| `Hud` | `sprites.playable.Tails` | Describe captured Tails hover/magnet versus Sonic fever/combo perks | Existing alternative / O |
| `Pickup` | `graphics.RenderPriority` | Collected bucket 1 versus active bucket 3 | Retained expert bridge / A |
| `Shot` | `graphics.RenderPriority` | Projectile bucket 3 | Retained expert bridge / A |
| `Shot` | `level.objects.TouchResponseListener` | Guard absorption destroys the absorbed projectile | Narrow missing service / B |
| `Stage` | `game.sonic2.audio.Sonic2Music` | Boss/final boss, super/chest, restored boss, act-clear, emerald and ending cues | Existing partial alternative; retained expert bridge / P |
| `Stage` | `physics.ObjectTerrainUtils` | Spawn-floor scan from floorTop-16 to floorBottom+48 in 16-pixel origins | Existing alternative / Q |
| `Stage` | `physics.TerrainCheckResult` | Accept first distance 0..15, otherwise floorTop | Existing alternative / Q |
| `Stage` | `sprites.NativePositionOps` | Arena ceiling resets Y subpixel, cancels upward velocity and sets airborne | Retained expert bridge / E |
| `Stage` | `sprites.playable.Tails` | Capture run character identity for supported native Sonic/Tails perks | Existing alternative / O |
| `SurvivorsMod$Module` | `game.rules.DynamicArtDmaServiceModel` | Full GameRules copy changes speed-shoes expiry while retaining native DMA policy | Narrow missing service / R |
| `SurvivorsTitle` | `game.GameServices` | Start-zone picker title navigation SFX 0xCD | Narrow missing service / I |

| Action | Owner and precise reopen condition |
|---|---|
| A | Native rendering / Mod API: deliberately publish a validated bucket/conversion seam and prove normalized native layering, including collectible phases. Literal `getPriorityBucket` values are mechanically available, but the implementation-pitfall catalogue requires `RenderPriority.bucket/fromS3kWord`; do not replace checked transcription with bare literals. |
| B | Object callback/contact owner: publish a purpose-scoped, verified-owner pre-hurt/item callback port; prove attribution/fault boundaries, contact order, exactly-once reward/absorption, shields, CPU exclusion, HOLD and rewind/recreation. Public `TouchResponseProvider` exposes regions/gates, not this callback moment; update polling is not equivalent. |
| C | Native physics / creator math: publish exact byte-angle integer sine/cosine only with all 256 entries and resulting render/contact coordinates equal. Floating-point `Math.sin` is not an exact substitute. |
| D | Rings/object lifecycle: provide a complete semantic live-spill query across native slots, logical overflow, step order and rewind. `RingManager.getActiveLostRings` is a pooled snapshot whose equivalence to all live object instances is unproved. |
| E | Playable position owner: deliberately expose reset/preserve-subpixel position operations with signed-short coordinates, sensors/history, velocity and rewind proof. Public centre setters show mechanical delegation but do not override the repository's native-write invariant. |
| F | Playable animation owner: supply a prepared locomotion-threshold copy operation preserving every other field and native selection for fresh and recreated characters. Generic `SpriteAnimationProfile` has no equivalent operation. |
| G | Native module lifecycle / ROM-title resources: inject current-world lookup and a bounded native-title background loader, verified before gameplay and after ROM/session changes/restore without PLC replacement. `SceneRomArt.levelBackground` belongs to a scene context and cannot be substituted into the unchanged native title lifecycle. |
| H | Native title rendering/resources: an owned background/scroll/fade port must preserve stock fallback, atlas isolation, attach/detach, two instances, ROM reload, viewport and palette restoration. A scene-background rewrite would be a lifecycle migration. |
| I | Native title/audio host: inject bounded owner-bound title audio/configuration/presentation capabilities, with live visit/thread/close behavior, stock coexistence and once-per-navigation cues. Gameplay `ObjectServices` and `SceneContext` cannot be assumed available to unchanged native title callbacks. |
| J | Controlled course/presentation owner: use existing `CourseControl`/row `ObjectServices` for proven query/cue alternatives, and publish only remaining native presentation/state operations after launch/landing/wall/stock-spring/replay and peer-view proof. Preserve row lifetime; the public `SceneFrameCodec` alone does not own Golf's transport, peer presentation or legacy mode callbacks. P17 transport disposition remains root-owned. |
| K | Native spring/contact owner: an admitted side-entry operation must retain complete Obj41 ROM behavior and the preserve-subpixel compensation, with exact route/replay proof. The P13 detach/checkpoint helper does not replace the whole native spring class. |
| L | Native title art owner: require exact canonical plane/mapping pixels, origins, palette/priority, GPU reinitialization and 320/400/528/800 viewport equality before replacing these decoders. Existing stock scene recipes/level kits are not proven equivalents. |
| M | Object construction/admission owner: consider convenience only with a second real consumer and equivalent probe suppression, factory fault, inherited ownership, slotless/native-pressure, deferred update and repeated recreation checks. |
| N | Mapping renderer owner: add origin-aware scaled mapping rendering only with fractional scale, flips, palette/priority, camera translation and resource-lifetime pixel proof. `PatternSpriteRenderer.drawFrameIndex` lacks scale; scene sprite raster composition is a different path. |
| O | Authored run-character policy: migrate to `characterKey().equals(CharacterKey.TAILS)` with both supported native leaders and captured/restored perk identity. Do not silently reinterpret custom Tails subclasses as capabilities. |
| P | Explicit S2 soundtrack owner: `GameMusic.SUPER/ACT_CLEAR/EMERALD` are existing alternatives; boss/final/ending need a named provider, exact IDs, restore/preemption/expiry checks and another matching consumer. Unlabelled integers are not remediation. |
| Q | Terrain provider / authored spawn placement: migrate to `ObjectServices.floorSurface` plus immutable `TerrainSurface`, preserving the exact scan cadence, first-floor selection and fallback with row-proxy lifetime coverage. `findFloorSurface` accepts [-16,16), which would change this caller's 0..15 contract. |
| R | Semantic GameRules owner: a `withPowerUp` copy operation must retain every other component by identity, including hidden DMA scheduling, with native positive control. The older ten-argument constructor resets DMA policy and is unsafe here. |

### P18 Slay and Three Islands facade qualification

Commit `df7a9a799` separates successful art caches from bounded expected-failure status
and retries at 120 owner logic ticks. Both retain success/image formats and host-owned
CPU/GPU image lifetime; local close clears references and rejects reuse. Missing
optional ROM, unsupported request and invalid data remain distinct. Dependency
failures retain their original cause category and deadline. NPE, generic runtime
faults, errors and `CallbackAborted` reach the actual verified owner boundary.
Slay publishes eight slot faces only after the complete batch; Three Islands does
not cache a missing-kit zero palette. The legacy pure `Art(SceneArt)` Three Islands
constructor has a fixed clock; live Game supplies its explicit advancing logic clock.
No claim of blanket scene-art host revocation is made.

- The initial Java 25 exact two-example regression invocation failed both outer
  selectors: Slay 139 nested checks (134 passed, five failures), Three Islands 91
  (86 passed, five failures), zero skips. Meaningful failures included permanent
  failed-cache entries, repeated title decode, swallowed NPE and premature palette
  publication. Fixture clock/owner/stage inputs were then corrected.
- Actual external creator consumers using `build_project.py --engine <engine>
  --sdk <sdk> --testkit <testkit> --test` against the matching provisional identities
  passed Slay 142/142 and Three Islands 95/95 authored Jupiter checks, zero skips.
  Package validators reported zero findings. These include repair after failure,
  bounded status, atomic faces/palette, underlying dependency cooldown, proper owned
  host close and actual callback-fault attribution.
- Queued normal Maven at `df7a9a799`, Java 25, exact selectors
  `TestSlayTheRobotnikExample,TestThreeIslandsExample,TestSlayTheRobotnikScene,TestThreeIslandsScene`,
  `-Dmse=off test` and all three verified canonical absolute ROM properties: 18 outer
  tests passed, zero failures/errors/skips, 39.383 s execution on 2026-10-12.
  This includes the production scene routes and ten Three Islands zones/interiors.
- Two obsolete green requests were cancelled while still waiting, before execution,
  because fixtures/source were revised; both are incomplete checks with zero tests,
  not passes. Queue waiting was never bypassed.

Final combined source/SDK identities, the new Starpost scope, graphics obligations
and root baseline/integration checks remain separate, outstanding qualifications.

## Peer review and second upstream composition

Published `f02bdbfd5` adds Starpost Valley (257 main Java /18 authored test files),
the29th maintained manifest. Root `4b631cdc5` retains the resource-generator path
and creator Jupiter launcher while merging scene/act APIs; combined pins were a
three-way set composition respecting upstream removals, pending compiled inventory.
Creator Python checks now17/17. P20 settings/custody `5e838fa78` merged as
`ebfacef35`; its normal ten-class Java25 selector81/81 and subsequent uploader7/7
passed with zero skips. Historical28-ledger merged as `3b52e43c4`.

Peer review reproduced two missing helper edges at the updated composition:
ABSENT→new readable corrupt primary incorrectly returned SAVED; closing an outer
canvas scope left its descendants usable and allowed their close to restore a retired
clip. Both regressions failed exactly as reported (14 tests,2 failures,0 errors/skips).
Retain CORRUPT bytes when no validated recovery/replacement exists; ancestor closure
now revokes descendants and skips retired clip restoration. Queued lean
TestModSaveSlot,TestModStorage,TestRecordingCanvas,TestSceneArtHelpers,TestCreatorUi
passed55/55, zero skips,1m07s. Recovered known-valid generations still repair normally.

Native Flappy's source-built normal title trust/Apply and gameplay selector passed11/11,
zero skips,24.524s combined Maven execution. The new boss pixel test was the twelfth
outcome and skipped: offscreen OpenGL4.1 unavailable despite EGL_PLATFORM=surfaceless.
No pixels are certified by that skip. A scoped software-renderer qualification is
pending, keeping the test off the ambient desktop. Its first obsolete waiting request
was cancelled before execution to add the known surfaceless environment.

Full updated baseline is run20261012T163202Z-606c65e8 at f02bdbfd5,3155 ordinary
classes plus guards,120-minute execution/10-minute idle limits. Preflight passed scoped
Java25/Lua5.4. No completed broad result is claimed yet. Starpost's newly reproduced
unchecked progress/settings writes, rejected-save replacement and art fault/failure
caches are assigned to existing P01/P12/P18 tasks; scene-act/controller coexistence is
assigned to P11. Native session/identity/outcome protections are retained.

Root clean09af93df1 provisional Java25 package passed38.234s, including strict bundled
Time Attack zero findings; engine/SDK/testkit are matching and supplied to Starpost
workers for actual external creator checks. Compiled inventory exactly matched all
20072 combined pin rows with zero missing/stale rows, after compilation completed.
The newly merged optional root generate_resources.py contract exposed an exporter gap:
its source path was absent (actual Python KeyError regression). The exporter now retains
it and schedules it after Maven resource copying as well as portable copying; the
existing17-test Python suite passes. Final actual Maven/portable generated-resource
matrix remains pending with the final kit.

A bounded owned offscreen diagnostic established the pixel provider limitation:
GLFW3.4 initializes, but EGL returns GLFW_FORMAT_UNAVAILABLE / Failed to find a suitable
EGLConfig, including scoped surfaceless/software settings. The still-waiting redundant
GPU request was cancelled before execution; no ambient desktop or driver installation
was used. An independent actual-ROM CPU pixel-centre oracle is added to compare the
three boss recipes at three animation samples; this qualifies decoded part geometry
and ordering separately from the optional production GL readback test.

The root generator contract has actual external qualification against matching clean09af
Java25 jars, separate from the final kit: exported Hello plus an authored generator
passes three Jupiter checks (zero skips), strict package zero findings. The generator
verified authored manifest copying before writing its resource. Exported Maven
`-DskipTests package` also passed1.207s; the generated resource is present in both
packed jars and Jupiter/testkit/test classes are absent. This is an SDK/build contract
qualification, not a Maven engine-suite pass. The owned consumer tree was deleted after
inspection; no raw logs retained. Python safety/export suite remains17/17.

## Starpost Valley upstream scope delta

Main fast-forwarded to upstream `f02bdbfd570276f7377d7055d236f63e34691484` without
changing its branch or unrelated files. The worker read that source, then fast-forwarded
its clean branch to combined `3b52e43c4` before implementation. The upstream addition
has 257 main Java sources and 18 authored test files, a 29th manifest (`starpost-valley`,
0.0.1, patch/S3K, candidate range >=0.7.0 <0.8.0), a large dated design record and a
[real Ruins matrix](levels/starpost-realruins.md). Its six native destinations are
valley, ruins, race, hunt, snowboard and lake; the startup/belt farm and stationary
menus remain scenes. The review did not rewrite the 257-source game.

The actual registration uses ModContext transactions, supplied S1 logical-ROM
prerequisites, captured `TownSession`, `RuinsSession` and `ActivitySession` service
bundles, and rewind-recreatable native controller/object factories. Placeholder
terrain is original typed data generated by `tools/make_placeholder.py`; native
load patches replace it from the supplied S1 ROM through PatchContext and the ROM
pipeline. Fixed animation/DPLC table reads are bounded by their authored lengths.
Scene/native palettes and cache state belong to their scene or session instances.
`RomDetectionService`/stock-module loading, direct mapping rasterization and native
seasonal palette/render effects are expert composition paths whose semantic behavior
still needs native route evidence; absence of validator findings is not that evidence.

### Concrete P18/P20 extension findings

The source-qualified review found:

- `StarpostScene.enter` caught every RuntimeException from Art construction and
  presented a generic Green Hill failure. `Music.update` also swallowed provider/
  programming faults and published a playing-track identity before success.
- `RuinsArt` permanently stored failed/null kits and badnik banks; missile/lava
  null paths could decode repeatedly. `FestivalArt` set tried flags before decode
  and permanently retained failed art, including partial slot/flower batch paths.
- `Shell.save` and `Settings.save` discarded storage write outcomes. `DayEndScreen`
  mutated shipping/date/weather/RNG/sections before that unchecked write; a retry
  must retain the encoded checkpoint rather than repeat sleep or rewards. File
  load folded invalid/future data into null, allowing an unconfirmed replacement.
  The input/save worker owns this preservation/publication repair and scene exit.
- `Capsule.load` accepted `vault1.rings=100000` against a 2500-ring target.
  `deliver` then used negative need/payment and could mint 97500 wallet rings;
  CapsuleMenu invokes delivery even for complete bundles. The input/save owner
  must validate against each exact target and make complete delivery a no-op,
  with over-target file/menu and once-only reward regressions.

File decode supplies fresh Game/SaveSection instances; failed sequential section
decoding does not itself establish live partial publication. Town rewind captures
wallet/calendar/RNG/inventory/section state and restores the same scene Game in place,
matching the intended retained scene/act loop. No broader stale-history or foreign-
graph guarantee is inferred from this bounded source review.

The bounded P18 implementation introduces one local AssetFailures class (258 main
sources after this batch), with at most 64 expected failures and256-character request/
cause fields, retrying at120 owner logic ticks. Startup required art, Ruins/Festival
facades and soundtrack retain successes separately, propagate NPE/generic runtime/
Error/CallbackAborted faults and publish complete flower/slot/snowboard candidates.
Mecha palette ranges complete before sprite preparation. The scene passes its clock;
native Ruins and activity presentation explicitly pass their captured session clocks,
because the ModSceneHost scene clock stops while suspended. SceneArtCache supplies
equivalent successful ROM request/palette banks. Local Art/Music close is terminal;
host-owned image/ROM/audio destruction remains host-owned. The input/save worker's
exit finally plumbing is a combined qualification dependency, not certified here.

### Observed provisional qualification

New engine, SDK and testkit jars all report clean `09af93df1`, version/base version
0.7.prerelease. They were verified before copying only jars and authored source inputs
to task-owned external outputs. This is another provisional identity, distinct from
the historical28 ledger and the eventual final matching kit.

- Java25 `build_project.py --engine <engine> --sdk <sdk> --testkit <testkit> --test`
  first reached one real music retry red plus four fixture admission errors: the
  original-pixel fake ROM did not supply the exact recipe identity. After correcting
  that fixture gate,202 authored checks ran:197 passed, five meaningful failures,
  zero skips. They reproduced permanent Ruins/Festival failures, missing music
  recovery, swallowed NPE and the failed-kit/status path before implementation.
- The repaired external consumer passed203/203 authored checks, zero skips, SDK
  package validation zero findings. The additional check proves no sprite is decoded
  with a partial Mecha palette and the complete bank recovers at its deadline.
- Engine build-only `test-compile` initially rejected an incorrect SceneDrawOp
  package in the new test helper; the helper now returns `List<?>`. That failed
  compilation executed no engine tests. Queued normal `TestStarpostArtFailures`
  with all three canonical absolute ROM properties is pending at this checkpoint;
  its actual owner-fault/recovery/host-close cases are not yet reported as passes.

Ten broad art catches still remain in the newly added FishArt (54/81/89), TreeArt
(69/394) and MuseumArt (74/83/91/122/173), outside this named facade batch. The P18
owner must inspect their fallback/publication behavior and use the same narrow
expected-failure contract before declaring all new Starpost art qualified. SaveCodec
parse/section catches are input/save-owned; Debug's broad command-parser catch is a
separate diagnostics path. The new29-package final artifact ledger, native routes,
held/modal pacing, rewind breadth, graphics and combined delivery checks remain open.

## P18 source follow-up: remaining facades and provider-state faults

The named art batch is `cff57b9c9`; input/save checkpoint `ed8633ff6` was cherry-picked
into the world worker as `a2ebbe2d6`. Their source was combined before this follow-up.
All ten remaining FishArt/TreeArt/MuseumArt broad catches are removed. Complete
palette-dependent fish banks and crimson frames publish on success; native lake
facades use the activity clock. Tree's authored palm fallback remains in world drawing
but cannot become permanent Mushroom Hill art. Museum banks, pictures and icons are
success-only caches. ItemIcons separates source successes from the built-in fallback,
so the actual rendered item icon recovers. Original successful ROM requests and pixel
transforms remain; this is not a new canonical pixel certification.

Peer review found Music losing ownership of song A after an expected failed switch
to B, even if A still played. A regression reproduced release performing zero stops;
the repair retains the last successful marker until audio stops and leaves B retryable.
Input's nested exit finally closes saves, Music, non-null Art and the failed-enter
ledger even when Shell construction never occurred.

Root review found generic IllegalStateException being treated as invalid data.
Actual RomSceneArt/SceneRomArtFactory direct IOException-caused state wrappers alone
remain retryable UNAVAILABLE failures. Unqualified state faults, including closed
provider/owner state, propagate. Starpost's block-size/skid-cue validation uses an
explicit local invalid-data signal. The same correction applies to Egg, Slay and
Three Islands, including Egg's central recorder. This does not assert a blanket
production scene-art revocation API.

SceneArtCache remains the successful request/copied-palette cache. It has no typed
failure authority, owner clock or native-versus-suspended-scene clock policy. Moving
retry handling there requires a deliberate host contract and owner/lifetime evidence.
The proven handling stays mod-local; no speculative generic public API was added.

Actual Java25 external creator `build_project.py --test` qualification uses the clean
matching `09af93df1` engine/SDK/testkit identity:

| Consumer | Completed authored checks | Skips | Package findings |
|---|---:|---:|---:|
| Starpost | 230/230 | 0 | 0 |
| Egg | 35/35 | 0 | 0 |
| Slay | 144/144 | 0 | 0 |
| Three Islands | 97/97 | 0 | 0 |

Starpost's first follow-up red ran210 checks (203 passed/seven failed): six remaining-
facade failures and Music release. Two independent rendered-icon/provider-state reds
then ran229 (227 passed/two failed), zero skips. A staging copy missed the new test
directory once and launched mixed external inputs; its ten failures are harness
evidence, not a source result. A complete copy and completed230-check run supersede it.
Older-facade reds were Egg35 (33 passed/two failed), Slay144 (142/two), Three Islands97
(95/two), all zero skips: pure lookup and actual owned state faults were swallowed.
NPE callback faults remain positive controls; direct I/O wrappers recover after120
ticks. One old Slay fixture used generic state for damaged art; the tightened contract
rejected it and the fixture now uses the decoder's invalid-argument shape.

Two obsolete Starpost requests were cancelled while waiting, before any engine tests
executed: the initial ownership selector while exit/remaining source changed, and the
five-selector request when root extended the same correction to older facades. Both
are incomplete. Frozen combined normal request81071 at `a2ebbe2d6` plus this final
source diff uses Java25/Lua5.4, `-Dmse=off test`, all three canonical absolute ROM
properties and exact selectors:
`TestStarpostArtFailures,TestStarpostValleyExample,TestStarpostValleyScene,TestStarpostRealFestivals,TestStarpostRealRuins,TestEggmansSkyExample,TestEggmansSkyScene,TestEggmansSkyFauna,TestSlayTheRobotnikExample,TestSlayTheRobotnikScene,TestThreeIslandsExample,TestThreeIslandsScene`.
Its ownership/scene/native/ROM outcomes remain pending here. Final matching29-manifest
artifacts and combined root integration/ordinary/guard checks remain open.

## Late corruption without a recovery slot

The actual Starpost creator probe against clean `09af93df1` reproduced a no-backup
slot saving once, then overwriting externally arrived corrupt `valley.sav` bytes on
its next save (1 test, 1 failure, zero skips). The condition remained at `d2b8b6202`.
Automatic late-corruption recovery now requires both a last validated generation
and a configured backup that can retain it. Without that backup, publication returns
REPLACEMENT_REQUIRED and protects the corrupt bytes until an explicit New Game/reset.
Paired shared-helper regressions cover no-backup preservation/replacement and failed
backup publication followed by exact-checkpoint retry. The queued lean
`TestModSaveSlot,TestModStorage` qualification is pending; this source checkpoint
does not claim a passing run. Input worker is promoting the actual Starpost probe.

## Repeated art-failure mechanics: bounded uplift disposition

Read-only comparison at `38958c67b` covers Egg's `ArtFailures`, Slay/Three Islands'
`Art.attempt`, and Starpost's `AssetFailures.load`. All four now use the same pure
mechanics: at most 64 insertion-ordered failure entries, request/cause text bounded
to 256 characters, immutable snapshots without exception graphs, saturated
`now + 120` deadlines, suppressed work before a deadline, and status removal after
success. Successful images/banks stay in separate caches. Slay and Three additionally
propagate a dependency's original category, cause and deadline; this avoids a new
cooldown or misleading missing-ROM status at each parent request. Repetition is a
real creator burden and is a concrete extraction candidate, not a reason to mark
the four facades as an intended permanent SDK pattern.

The policy differs. Egg and Slay use the scene visit clock; Three uses its advancing
Game clock; Starpost uses the scene clock for menus/music and the captured activity
or ruins clock for native presentations. Native clocks can restore backwards, while
the compatibility-only constructors use a constant clock. Retry duration, reset on
new asset owner, dependency/fallback choices and optional-versus-required ROM admission
belong to those owners. Fault classification must also remain explicit: only proven
decoder argument/unsupported failures and direct I/O-caused state wrappers are
recoverable; generic provider state, NPE, callback aborts, ordinary runtime faults
and Error propagate. Starpost's local invalid-data signal is not a published host
failure type. Image/ROM/GPU destruction stays with the host; facade close and Egg's
generation-cache reset have different scopes. `SceneArtCache` currently guarantees
successful sprite requests/copied palettes, not any of these policies.

Defer the public uplift here. The narrow next seam is a bounded status ledger with
explicit `ready(request, now)`, `failed(request, category, cause, retryAt)`,
`succeeded(request)`, snapshot and retirement operations; it need not execute a
loader, classify exceptions or own images. Start with an internal implementation,
then publish an owner-bound preparation/status capability only after the scene/native
clock and typed expected-host-failure contracts are settled. The scene-art/Mod API
owner should reopen this from these four maintained consumers, retaining their
facades and dependency behavior rather than requiring authors to copy another
catch-all loader. Do not accept caller-supplied owner strings as framework authority.

Qualification must establish the following before migration/publication:

- Equal request attempts/status through failure, tick119/120, success, saturation,
  eviction and dependency reuse; bounded display text must not merge distinct keys.
- Scene suspension, native HOLD, rewind to an earlier activity clock, new-owner
  replacement and retirement behavior, with no work after close.
- Actual owned provider faults reaching the callback boundary and expected decoder
  failures remaining recoverable, including missing optional ROM versus invalid data.
- Existing palette/batch atomicity, fallback repair and canonical CPU pixels, with
  host resource ownership unchanged, followed by all four actual portable consumers
  and their focused scene/native checks. Current local passes do not certify this
  future extraction; no speculative API or duplicate test suite was added here.

## Backup rejection and CPU composite qualification

Two additional focused source probes failed against the clean `09af93df1` engine:
future-format backup rotation returned SAVED instead of REPLACEMENT_REQUIRED; an
unreadable existing backup returned SAVED instead of BACKUP_FAILED (2 tests, 2
failures, zero skips). The repaired helper and those tests compiled from current
authored sources against the same artifact dependencies passed 2/2, zero skips.
This is a bounded CPU helper qualification, not a final matching-kit or broad run.
The queued lean Maven storage gate remains responsible for the integrated source check.
Future backups now require explicit replacement; unreadable backups preserve both
files and the exact pending checkpoint for retry.

Queued Java25 normal `TestSlayCompositePixels#romBossCpuPixelsPreserveLegacyOriginsFlipsAndOrder`
with the canonical absolute S3K ROM passed 1/1, zero skips, 1m10 Maven execution.
The actual packed Slay mod was validator-clean. Three ROM-backed boss poses
(AIZ flamecraft, HCZ screw mobile and LBZ beam rocket) at ticks 0/29/61 preserve
legacy ordered pixel-centre rasterization, sprite origins, flips and overlaps.
The fixture tests detached CPU pixels; it does not certify the GPU renderer.
Its separate GL test remains availability-skipped because the owned offscreen
GLFW/EGL provider cannot create a suitable configuration on this host.

## Updated destination baseline: f02bdbfd5

Actual destination commit `f02bdbfd570276f7377d7055d236f63e34691484`, completed run
`20261012T163202Z-606c65e8`, Java25/Lua5.4; command:

```sh
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base f02bdbfd570276f7377d7055d236f63e34691484 --category all --rom-directory "$PWD" --max-minutes 120 --run
```

The command was launched from the main checkout; `$PWD` denotes its actual canonical
absolute ROM-directory argument without embedding a machine-local path here.
The plan selected 3155/3155 ordinary classes and all guards; reports cover 3153
ordinary and 88 guard classes. Ordinary completed 27,431 tests: 26 failures, 0 errors,
63 skips, 3659.31s. Fresh guards completed 677 tests: 0 failures/errors/skips, 280.10s.
This is a completed red baseline, not a green delivery. All 26 failures are existing
MHZ/LRZ/SSZ/DEZ route assertions. Skips were inspected: 32 Infinite terrain/fixture
assumptions, 5 GL/EGL availability checks, 1 CPZ interaction assumption, 1 missing local
BizHawk audio reference and 24 opt-in diagnostics/captures. Required ROM paths were
injected from the existing main checkout; no ROM copies or links were created.

Failure comparison uses the full assertion before its stack, not the runner
truncated `message`. The following SHA-256 values identify every full assertion.
These are raw assertion digests. `RewindObjectStateBlob.hashCode()` combines class
identity with schema and serialized state, so its `@<hex>` suffix is not a purely
incidental identity token. An earlier normalized grouping digest is superseded here.
If that suffix differs across JVMs, use a bounded semantic comparison of the failing
snapshot before attributing the difference; normalization alone cannot prove an
inherited failure. Candidate and integrated results must match by test identity and
assertion, and any new/worsened or unattributed failure blocks delivery.

| Failure identity | Assertion SHA-256 |
|---|---|
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | `c78f100afd3f3ed7379fdd15ed9746c6664e69286a8ac65a808a66806dbaf856` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | `5865a5f5ba14ca053c854e3be29043e16e8880ef2a049b9aed634adb8f1809f0` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding` | `5f31236278beb31ffe74659120fe89b2efe2ba864cc6b828a443fd29bbed2456` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `5f31236278beb31ffe74659120fe89b2efe2ba864cc6b828a443fd29bbed2456` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | `511552908ae3c54c14a3220cf1ad1749670c1a8a3ade21cbecfc237b37f87093` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | `7938aa41f6646ff7cf2c3ea103cb7a05b8851098fe04ffcb14b96da05b0d031f` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `5f31236278beb31ffe74659120fe89b2efe2ba864cc6b828a443fd29bbed2456` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday` | `1d3379af510e98acb1e16c79b7445bf765258c9abb1f8e77536f26ce7dfe74b8` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding` | `5f31236278beb31ffe74659120fe89b2efe2ba864cc6b828a443fd29bbed2456` |
| `com.openggf.tools.TestLrzActTwoColdRouteCapture#coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState` | `298ea9a43c0ec187c7508b8872f745d20989ec462ed3278a32bc051cd9906940` |
| `com.openggf.tools.TestLrzBossColdRouteCapture#coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState` | `298ea9a43c0ec187c7508b8872f745d20989ec462ed3278a32bc051cd9906940` |
| `com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace` | `71c9a94d11db41cbdfec16d6458e8e0a59d4947c7ee96e84a03d127da38c6c85` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsClearsActOneAndRestoresTraversalFightAndHandoff` | `3c4aeb3cf3950ca7bb7678707d23cf1512bb55d7cacae43cc8f64b6310590800` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsRestoresActTwoTraversalToTheMiddleCorridor` | `3c4aeb3cf3950ca7bb7678707d23cf1512bb55d7cacae43cc8f64b6310590800` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesBossActAndReachesPlayableHiddenPalace` | `3c4aeb3cf3950ca7bb7678707d23cf1512bb55d7cacae43cc8f64b6310590800` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesActTwoAndRestoresTheBoulderHandoff` | `3c4aeb3cf3950ca7bb7678707d23cf1512bb55d7cacae43cc8f64b6310590800` |
| `com.openggf.tools.TestLrzWideBossColdRouteCapture#coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld` | `49ca51894f4d6e67b693c698250a219ecc8074a00f06034b2320c51536c597eb` |
| `com.openggf.tools.TestMhzPairColdRouteCapture#pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad` | `489cb0022b1a8048135fa731250f4a9fd956bab37a9b987ba0c70f2bf2070190` |
| `com.openggf.tools.TestMhzWideColdRouteCapture#wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay` | `b8caa35f5648f2de6ffdab9b642b49a4850f43ae81cfe86a1f64263295ea85d3` |
| `com.openggf.tools.TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents` | `a331f9ebdb13fa37ad2a8e804e3b5eed1d0c0202dacd6483f05dabd3595d849f` |
| `com.openggf.tools.TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport` | `a331f9ebdb13fa37ad2a8e804e3b5eed1d0c0202dacd6483f05dabd3595d849f` |
| `com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches` | `00ac4ba91696465a1a8db1fd54a3b43bc46c1eae9ffb23d4ee9923dd694fe4ca` |
| `com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory` | `00ac4ba91696465a1a8db1fd54a3b43bc46c1eae9ffb23d4ee9923dd694fe4ca` |
| `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]` | `09c1414b4888b1025e4cbe3e38ffd88ea2e00ff8e4ec031760f63b18c4e67224` |
| `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]` | `09c1414b4888b1025e4cbe3e38ffd88ea2e00ff8e4ec031760f63b18c4e67224` |
| `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]` | `aa1f4afc287c8468e5ec8202d7ee021925681858f349f3d8580f080cfbb03a98` |

Completed baseline diagnostics were inspected and acknowledged; the run directory was deleted.

## Completed canonical art qualification and callback-clock fixture correction

The unchanged twelve-class normal request81071 completed at `85c2b28c6` (runtime
source identical to `38958c67b`), Java25/Lua5.4, with all three canonical absolute ROM
properties:112 checks,111 passes, one fixture failure, zero errors/skips. Execution
took186s after1288s queue wait. `TestStarpostRealFestivals` passed30/30 and
`TestStarpostRealRuins`37/37; all eleven other classes passed. Full failure identity
was `TestStarpostArtFailures.expectedPreparationFailureRetriesAndProperHostCloseRetiresArt:50`,
expected attempt2 but observed1. This is not a production art regression.

`ModSceneHost.update` increments its context clock after the callback. The fixture's
120th call therefore observed tick119, before the unchanged retry deadline120.
The three-line test correction now asserts no retry on that callback and invokes
the callback observing tick120 before asserting recovery. No deadline, loader or
production policy changed. The original failure stopped before the close assertions;
the initial run alone did not qualify those assertions.

The required focused normal rerun92446 used
`python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestStarpostArtFailures test`
with the same Java25/Lua5.4 and canonical S1/S2/S3K absolute ROM properties. It acquired
immediately and completed at18:49:03 BST in30.528s:4/4 passes, zero failures/errors/skips.
The actual packaged scene recovered fully, proper host close retired its retained Art,
missing optional ROM produced inspectable status, and both NPE and generic provider
state reached the verified owner fault boundary. XML cases/skips and the full earlier
assertion were inspected; no raw logs were archived. This combined focused evidence
does not certify the later final SDK identity or root integration/ordinary/guards.

## Final helper composition checkpoint

P17 source `505d52bfd` is integrated as `87d990bab`: pure title-card positions in
Flappy, Sitar and Slay, and exact-case atlas lookup in Three Islands. The worker
qualified six presentation classes (35 tests), its common API gate with
CourseControl/JarValidator (93 tests), and four actual external portable consumers
(Flappy 24, Sitar 52, Slay 142, Three Islands 95; total 313). All have zero skips; each
portable package was validator-clean. The matching provisional artifacts were
labelled dirty at `3b52e43c4`, so this is not the final clean kit identity. An actual
Slay package failure caught helper calls in unsupported static initialization;
queries moved to ordinary methods, preserving the caller's literal completion policy.
Original recorded CPU pixels at 320/400/528/800 and release/hold/omission cases passed.

Input followups `2a825aeff` and `e46ce73a9` are integrated. Java25 queued Starpost
Example completed its two wrapper tests and all 231 authored checks with zero skips;
six canonical native classes passed 94/94, zero skips, 161s after 2253s queue waiting.
The latter includes real storage directory obstruction, exact retained checkpoint
recovery without repeated day/RNG changes, proper exit and native scene handoffs.

Root storage qualification first encountered a compile-phase source race: P17
merged between production compilation and test compilation, leaving new tests
without new helper classes. No tests ran in that attempt. A settled Java25 compile
passed 49.685s, and the compiled inventory exactly matches all 20,101 pinned rows.
Queued lean eight-class storage/common API run then completed 107 tests: 104 passes,
three policy errors, zero skips (30.525s). The 33 storage/save-slot cases passed.
The policy errors rejected comments added to the strict descriptor format; those
comments were removed, preserving its existing authority values. Only the three
affected policy classes reran: 18/18 passed, zero skips, 28.058s. No policy parser or
selection rule was loosened. Final broad delivery and matching clean kit remain open.

## Final native admission and existing-helper adoption

Survivor `266c8be0c` uses canonical character keys and the injected live floor query
while preserving the existing 16-pixel scan and strict distance range 0..15. All 29
selected terrain/perk behaviors were qualified across a 28-pass run plus the corrected
synthetic service-binding case; that fixture now binds services as ObjectManager does.
Its actual portable package/tests passed 5/5 without skips and retained 13 advisory
engine references, down from the historical 18 (including upstream NativePositionOps
becoming public). The final ledger must still classify every current warning.

Frame source `0aae9cefd` is integrated after meaningful native route reproductions:
duplicate scene input, missed bridge admission on the fade-completion setup row, and
controlled bridge consumption before host pause. Native and controlled paths now
service bridge exit/holds after host admission and before setup/world work; held rows
retain the existing audio boundary and prior-fade timing, and retired contexts cannot
reenter the controller. The duplicate scene input update is removed.

Normal Java25/Lua5.4 canonical three-ROM batch `10073` passed 201/201, zero skips,
2m12 execution after 16s queue wait. It ran from the Survivor worktree at dirty
`266c8be0c3a7aee42ed15722ffa52003004d2f9c`; the five changed frame/test sources were
committed without further edits as `0aae9cefdf5edc175a8a84953f22fe42ba9d543d`
(tree `b0ece704908aa979875abf66a7094ee7bd67f025`). Its generated metadata reported
`app.commit=266c8be0c`, `app.dirty=true`; the execution is not described as a clean
run at the later commit. It includes `3b52e43c4`, P17 and Survivor floor adoption,
and excludes the root's later save-slot repair. Exact command, with
`OPENGGF_ROM_ROOT` denoting the existing canonical main checkout:

```sh
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestModSceneActBridge,TestMutatorOverlayRouting,TestGameLoopConfigurationCommands,TestGameLoopMutatorPacing,TestGameLoopNativeStageMutatorPacing,TestControlledFrameRuntime,TestHostedRunController,TestSceneRunLauncher,TestLevelFrameHardwareTimingBoundaries,TestModSceneHostGameplay,TestActPracticeExample,TestPuttPuttParadise' "-Dsonic1.rom.path=$OPENGGF_ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$OPENGGF_ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

Normal lane; no profile/custom JVM arguments. Actual XML outcomes and skips were
inspected; no raw logs were retained. Completion: 2026-10-12 19:01:01 +0100.
Final clean artifacts and broad candidate/integrated comparisons remain open.

## Final independent contract-edge review

The clean `0c5524060` producer package completed1m18 with tests skipped and bundled
Time Attack validator-clean. That package is provisional: independent final review
then found the direct-close overlay fade edge and explicit unreadable-backup reset
asymmetry. No final matrix is attributed to that provisional producer.

An actual production-storage probe against its matching engine failed1/1 without
skips: explicit reset of an oversized backup returned BACKUP_FAILED instead of
SAVED. The paired oversized/invalid-UTF8 production regression then passed in the
queued Java25 lean `TestModSaveSlot,TestModStorage` run:34/34, zero skips,1m09.
An additional reset-sequence probe reproduced two failures against that producer:
replacing two rejected generations left the following checkpoint read-only, and
required rejected-backup failure did not prevent primary publication (2/2 failures,
zero skips). Explicit reset now retains a supported recovery generation or publishes
the already validated reset checkpoint into a rejected existing backup before the
primary. The queued Java25 lean `TestModSaveSlot,TestModStorage` run43231 passed
36/36, zero skips,1m09 at dirty `0c5524060` with these exact two source changes;
completion was 2026-10-12 19:23:25 +0100. This covers the following checkpoint and
required backup-write failure, not a multi-file atomicity guarantee.


## Completed candidate broad run and bounded attribution

Clean producer `175531f6dc4a0d0005c4593022393182d1f5e58c` completed combined
change-based run `20261012T183411Z-d39c898d` against destination
`f02bdbfd570276f7377d7055d236f63e34691484`. Java 25/Lua 5.4 preflight passed.
All 3,170 ordinary classes were selected, plus fresh structural guards; ordinary
reported 3,168 classes and 27,627 checks: 31 failures, 0 errors, 64 skips, 3627.69s.
Guards reported 88 classes/677 checks: 3 failures, 0 errors/skips, 262.77s. The
120-minute invocation and 10-minute no-output stopping rules were unchanged.
This completed red run blocks integration until all introduced failures are repaired.

The 26 baseline identities recur. Twenty-five full raw assertions match exactly.
The SSZ Tails width 400 replay failure at 4018 differs only in two compact blob hash
suffixes; these contain class identity **and** semantic state, so the difference
was initially unattributed. Five ordinary failures are new: the Flappy tutorial
shared-copy assertion; three Phase2 checked-starter inventory assertions; and
`TestPurposeStarters.everyPurposeHasCompleteCompilableSourcesAndRepeatedBuildConfiguration`
(music main-only compilation inadvertently includes generated Jupiter tests).
The fresh guards found two test-hook parameters named `frame`, a new runtime
`game -> io` edge from the standalone registration bound, and playable sprite
size 3264 against 3258. Their focused repairs are recorded separately.

Skips were inspected individually: 32 existing Infinite fixture/terrain assumptions,
6 GL availability checks, 1 CPZ interaction assumption,1 missing local BizHawk audio
reference and 24 opt-in diagnostic/capture checks. The additional GL skip is
`TestSlayCompositePixels.sharedCompositionPreservesBossPixelsOriginsFlipsAndOrder`; CPU
composition evidence does not certify that unavailable GPU path. Completed run
results were consumed and acknowledged; its entire diagnostic directory was deleted.

Exact candidate command, from the task tree with `OPENGGF_ROM_ROOT` expanding to
the existing original-ROM checkout:

```sh
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base f02bdbfd570276f7377d7055d236f63e34691484 --rom-directory "$OPENGGF_ROM_ROOT" --max-minutes 120 --run
```

### Compact SSZ state attribution, with no engine behavior change

The matched exact-method requests 39158 (destination f02b) and 93438 (candidate 175531)
each ran 2 checks: 1 pass/1 same 4018 failure, 0 errors/skips, 29.722s and 32.757s.
Raw hash suffixes still differed, so this comparison alone was insufficient.
An isolated baseline worktree then ran 89996; candidate request 7522 ran the identical
comparison-only test diagnostic. Each again ran 2 checks/1 same failure/0 skips,
1m10 and 33.850s respectively. Their source/test attribution is explicitly dirty:
both used the exact same temporary diagnostic on the unchanged original test
(SHA-256 `db4583214f4430c81cf25b7260a716358be575ff2d7772a6067be01367c2af1e`;
probe SHA-256 `1f8488866d9b050177638942a7de4abf1154bdaa41072be440557b0bc8df86fb`).
The candidate also had the two clock-parameter spelling repairs; production engine
code remained 175531. No comparison values were supplied to gameplay.

All six blob rows match by side, complete object identity, schema, native type,
scalar bytes and opaque values. Opaque arrays are empty in every row. The complete
non-blob assertion fields also match: frame 4018, slot bits 24/29, missing children
125/126 and relocated children 128/129. This bounds the difference to the Class
identity component of the documented `Objects.hash(schemaId,type,scalarHash,opaqueHash)`
implementation, rather than assuming the suffix has no semantics.

| Dynamic ID / side | Schema / native type | Complete scalar bytes (hex) | Opaque values |
|---|---|---|---|
|106 / A and B|15 / EggRoboJetFlameChildInstance|`00050000007f070000f4090000`|`[]`|
|107 / A and B|16 / EggRoboGunArmChildInstance|`ffffffff006f070000d4090000`|`[]`|
|125 / A only|15 / EggRoboJetFlameChildInstance|`01050000000b0500001c090000`|`[]`|
|126 / A only|16 / EggRoboGunArmChildInstance|`ffffffff011b050000fc080000`|`[]`|

The temporary diagnostic source was restored byte-for-byte in both trees; its
original-copy files and the accounted baseline worktree/branch were removed. No
production formatter, assertion weakening, snapshot hydration or unrelated SSZ
behavior repair was introduced. These are 26 attributed inherited failures, not a
passing ordinary suite. Integration and its full combined comparison remain open.

## Object V-int terminology repair

The new standalone stub's `update` and destruction test hook's `updateMovement`
parameters now say `vIntRunCount`, preserving both signatures and bodies. At dirty
175531 plus these exact two changes (and the inactive comparison-only diagnostic),
lean request 79295 passed 13/13 with 0 errors/skips, 1m20 execution after 1s admission:
`TestObjectUpdateClockTerminologyGuard,TestStandaloneGameSpec,TestDestructionEffects`.
Completion: 2026-10-12 20:52:33 +0100. The actual clock guard subprocess passed; no
ratchet, clock semantics, production callback or API signature changed.

## Candidate regression: Flappy tutorial shared definitions

The full candidate at `175531f6d` found a new tutorial failure, separate from the
26 known destination-baseline failures. `TestFlappyTailsTutorial` ran nine cases;
its mirror check failed at line60 with the full assertion:

```text
org.opentest4j.AssertionFailedError: part-5-pillars has a stale copy of Course.java ==> array lengths differ, expected: <9287> but was: <9150>
```

The P08 finished-example update prepared immutable owner-scoped tour definitions
but omitted the part5 mirrors. A byte comparison of every non-`FlappyScene.java`
shared file across all four checkpoints found12 copies:10 identical, and only
part5 `Course.java` (9150→9287 bytes) and `Zone.java` (3222→4300) stale. Both are now
exact copies of the finished definitions. The part5 scene owns one immutable
`Zone.Tour`, passes it to `Course`, and reuses it for entry/update/draw lookups.
Prepared definitions remain separate from mutable flight/course/score state; seeded
draw order, restart and checked best-save behavior are retained. Other checkpoint
callers keep the same flight contracts. The finished Practice command still passes
its explicit capability at Angel Island and later stops; normal Play stays eligible.
Tutorial prose now explains retained preparation and this practice policy. No test
or mirror invariant was weakened, and no new test duplicates the existing nine cases.

The changed part5 source was copied to an owned directory outside the checkout and
qualified through the final kit's actual `build_project.py`/Jupiter/SDK launcher.
All three artifacts identify clean `175531f6d`. Its existing authored rejected-best/
false-write retry test passed1/1, zero skips; ordinary and strict `--warnings error`
packages each reported zero findings. The strict13-entry main-only jar had SHA-256
`c45bb35d42e7a28ace9d4852f806042e289bc761b5e2ee6ce213adab650c4ebd`:
`flappy-tails-part5`, version0.5.0, range `>=0.7.0 <0.8.0`, base `s3k`. No JUnit,
testkit or authored test classes entered it. The frozen final kit contains no
tutorial Java paths; this supersedes the changed part5 package row. The Phase2
followup below supersedes one further row; the other27 artifact checks and matching
engine/SDK identity remain unchanged.

Queued normal Java25/Lua5.4 request84302 is the native/finished-consumer gate:

```sh
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestFlappyTailsTutorial,TestFlappyTailsExample,TestFlappyTailsScene' "-Dsonic1.rom.path=$OPENGGF_ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$OPENGGF_ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

The canonical supplied ROM directory is the existing main checkout; no ROM or build
tree was copied. Request84302 passed15/15, zero failures/errors/skips: nine tutorial
mirror/build/canonical-ROM cases, two finished-example wrappers with24/24 nested
authored rules cases, and four finished scene cases. Maven completed1m17 after2793s
queue waiting, at20:42:46 BST. The source was worker `9f5a1b79c` plus this stable
repair diff; generated metadata reports `app.commit=9f5a1b79c`, `app.dirty=true`.
It is not labelled a clean run at the later commit. The required post-integration
comparison remains open; no full-suite pass follows from this focused check.

## Candidate regression: checked Phase2 starter drift

The same broad candidate found three new failures in `TestPhase2SampleModIntegration`:

- `realCreatorSampleBuildsAndRegistersAuthoredResourcesWithoutRom`
- `destroyedCreatorBadnikSubmitsTheGamesBadnikHitSound`
- `realCreatorSampleLoadsZoneObjectRewindAndKeyedSave`

All fail the unchanged `assertTreesEqual` inventory assertion, `Checked sample must
match real ggfmod init inventory`. Actual clean175 SDK `init`, using the fixture's
metadata (`id=phase2-sample`, `package=example.phase2sample`, default kind), produces
20 files versus19 after materializing the checked binary assets. The sole inventory
omission is `src/test/java/example/phase2sample/StarterIntegrationTest.java`.
That early assertion also hid two byte differences: `pom.xml` lacked the P10
creator-tests profile (4745→6049 bytes), and the minimal `SampleCharacter` stub
still preceded the P16 explicit `CharacterPhysicsSpec`/`drawStandardArt` template
(1406→1369 bytes). Exactly those three generated files are synchronized. Every
other materialized file is byte-identical; authored badnik/mod/assets and unrelated
character/standalone/platformer fixtures are preserved. Repository search finds
only this fixture's full `ggfmod init` tree-equality assertion; those other fixtures
build their authored projects rather than asserting regenerated identity.

The actual regenerated project outside the checkout passed default Maven
`-DskipTests package` against matching clean175 engine/SDK in1.500s, including both
asset converters and zero-finding validated package. Tests were skipped in this
build; it demonstrates the default main-only lane. Separately compiling its new
test with the matching testkit and pinned Jupiter, then executing the actual
CreatorTestLauncher, passed1/1, zero skips. Strict SDK packaging also reported zero
findings; the16-entry main-only jar contains no JUnit, testkit or test classes,
SHA-256 `b7a92e7428e88697a7fd016c5885110a44d54fa5c651269a3711c1a24b44f980`.
This supersedes the changed Phase2 artifact row rather than repeating the other
unchanged consumers.

Queued normal request36768, Java25/Lua5.4, selects exactly
`TestPhase2SampleModIntegration,TestProjectScaffolder` with all three canonical
absolute ROM properties. It passed6/6, zero failures/errors/skips: four actual
tree/SDK/registration/sound/keyed-save/rewind cases, including canonical S2 native
behavior, plus two scaffolder checks. Maven completed37.049s after1738s queue wait,
at20:43:24 BST, at the same worker/dirty source identity recorded above. No inventory
invariant is weakened to accept the stale fixture.

## Candidate regression: generated starter test compilation boundary

The completed ordinary candidate reports31 failures: the26 inherited destination
failures plus the five new fixture failures described here (one tutorial, three
Phase2, one purpose starter). The additional exact case is
`TestPurposeStarters.everyPurposeHasCompleteCompilableSourcesAndRepeatedBuildConfiguration`:
`music ==> expected: <0> but was: <1>` at line132. Seven compiler errors show the
fixture walking generated JUnit test source alongside main source, then supplying
only the SDK/main compiled-class path. This is an observed compiler-boundary
regression, not evidence that a data-only music starter needs JUnit in its main jar.

The fixture now compiles `src/main/java` using that same SDK/main-only classpath,
then compiles `src/test/java` separately with main output plus the proper Jupiter/
testkit test classpath. It asserts generated test sources are nonempty for every
kind and the creator-tests profile/testkit dependency are present. Data-only
starters may have no main Java source; their test compilation is still required.
The inventory and SDK main-only lane remain checked, and generated tests are not
silently excluded. The separate actual seven-purpose Maven creator-profile
qualification covers execution; this fixture covers compilation/artifact boundaries.

Queued lean request65828 uses Java25/Lua5.4 and the exact bounded
`-Dtest=TestPurposeStarters` selector, with no ROM/profile/custom JVM arguments.
It passed3/3, zero failures/errors/skips, in28.719s after311s queue wait, at20:43:53
BST with the same recorded worker/dirty source identity. Existing normal
requests84302/36768 were retained; neither was cancelled or narrowed to bypass this
additional observed failure. All three focused gates are complete; required root
integration, broad post-integration comparison and delivery remain separate gates.
Owned supplemental consumer directories/pointers were removed after inspection;
normal output was consumed without archiving logs or copying build trees.

## Creator-profile regression: audio-only starter integration test

The actual external creator-profile reactor against clean `175531f6d` found a
separate P10 template defect. Its first music child ran one test, zero failures,
one error and zero skips: `StarterIntegrationTest.packedModRegistersWithoutFaults`
called `kit.plan("final-creator-music")`, then failed with
`IllegalArgumentException: No successful plan for final-creator-music` at
`ModTestKit.java:167` and the emitted test's line16. Request36286 exited1. The
reactor has seven starter children plus exported Hello; the six remaining starters
and Hello had no reports and did not execute. This is an incomplete reactor,
not a seven-starter pass. Its preserved originals belong to the qualifying worker.

An authored normal-queue regression now emits music, reskin and scene through
`ProjectScaffolder`, compiles main and test sources separately, converts reskin
art, and executes the emitted test in the actual project directory through
`CreatorTestLauncher`. Each child has a bounded256MiB heap and30-second timeout.
Queued lean request39608 reproduced the music exception before the template fix:
three outer cases, one failure, zero errors/skips, in28.187s after23s queue wait,
completed20:51:41 BST. Reskin and scene already passed. Inspection explains the
distinction: the loader retains manifest art overrides for a real reskin plan;
pure audio content is effective but has no runtime registration plan.

The shared template now checks exactly one actual effective catalog descriptor
and its expected mod identity. Verified code or manifest art overrides still
require the actual successful plan. Audio-only music verifies validated enabled
content without fabricating a plan. The checked Phase2 emitted test remains
byte-identical to the rendered template; its main code, POM and native paths are
unchanged. The testing handbook explains these boundaries. No public API or
production registration behavior changes.

The exact focused qualification is:

```sh
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestProjectScaffolder,TestPurposeStarters,TestModTestKit,TestCreatorTestLauncher' test
```

Request21708 passed23/23, zero failures/errors/skips: scaffolder5, purpose3,
testkit13 and launcher2. It completed31.869s with no queue wait, at20:55:53 BST.
The three new cases include actual emitted music, art-only reskin and code scene
test execution; existing fault/disabled-owner, invalid content, failed/empty
discovery and main/test compilation checks also pass. XML counts were inspected.
Both local runs identify `app.commit=e299bff34`, `app.dirty=true`, with the exact
stable source changes recorded here, rather than a later clean commit.

The previously qualified tutorial/native Phase2 production paths are unchanged.
A fresh matching exported SDK/testkit/engine kit and regeneration/execution of
all seven actual Maven creator profiles remain required after integration.
This focused pass does not complete that external gate or the final broad
comparison, installation, push and cleanup gates.

## Clean 175531f6d producer: 29-manifest package and strict-validator checkpoint

This supersedes the historical 52-pair finding set above. The historical recipes,
purposes and action-owner definitions remain evidence, rather than a current count.
At this checkpoint the engine, SDK and testkit were built from committed
`175531f6dc4a0d0005c4593022393182d1f5e58c`, all reporting `app.commit=175531f6d`,
`app.dirty=false`, version/base version `0.7.prerelease`. Only those frozen jars and
committed authored sources were materialized outside the checkout; no ROM, player
state or existing build tree was copied. The source was archived at the same producer
SHA, so later documentation commits do not change this qualification identity.
Later source and exported-template repairs require their separately identified
qualifications; these frozen artifacts are not relabelled as a later producer.

| Artifact | SHA-256 |
|---|---|
| Engine | `014a89f7ee16c5c0f6c583e25bf28d73ab5c07d386d196e7ff8780549045c320` |
| SDK | `0e5945ae6f089342ccfcdf40adc4752a16d18ce7799c7a378ac7ddafbf78489c` |
| Testkit | `b304922c92aa10a9ef978844cac191b7be20a8cad95196996fe2f7cf4b421c7c` |
| API Javadoc jar | `c08ef0f4dcf1aa83d82b4b0c9c0938cec943ed3ab1756739adc46a8851738f48` |

The coordinator independently verified all 20,101 compiled/pinned candidate API
rows; signature-pin SHA-256
`ff5ac4e6ac6fbbf9b7d7243fa1eda335bb220a49870aa5065ef8377e2c7cac34`.
The Javadoc and pin checks are producer evidence, distinct from this worker's
29-source consumer compilation and validator runs.

Actual Java 25 `javac --release 25` compilation used engine/SDK only. Two workers
configured each compiler/SDK JVM with `-Xmx1g` and `ActiveProcessorCount=2`. Every project executed
`GgfModCli package` and `validate` with both `--warnings allow` and `--warnings error`,
JSON reports. All 29 compiled and produced valid normal-policy jars, zero errors;
26 were strict-clean. Infinite/Golf/Survivors retained 24/8/13 warnings respectively
(45 total): both strict commands exited 1 and the strict package published no jar.
All other strict commands exited 0, and their jars exactly matched normal-policy
bytes. Package/validate findings and allow/strict finding identities were compared,
not inferred from process totals. This is compilation/package/API-reference evidence,
not gameplay, render, audio, rewind, lifecycle or ordinary/guard-suite certification.

The later candidate broad run reproduced stale `part-5-pillars` Course/Zone mirrors
against the finished Flappy source. Compilation and validator acceptance do not
certify source-copy parity. The table below is the actual pre-repair `175531f6d`
checkpoint; that one consumer row requires the input worker's repaired-source SDK/
strict and native tutorial qualification. The other 28 main source/resource inputs
are unchanged, so their existing proof remains valid by source diff. This exported kit contains
no tutorial entries, and its finished example/shared engine/API are unchanged;
no producer rebuild or general 29-project repeat is implied by that tutorial repair.
The later starter-test template repair requires its own exported-kit qualification. The
separate tutorial regression section records the corrected source and its outcomes.

The completed matrix took **23.757 seconds**, replacing the deliberately conservative
3–6 minute estimate. Each compiler/converter/SDK subprocess had a 300-second cap;
no subprocess timed out. The initial artifact-only preflight looked for `testkit.jar`
in the exported kit; its actual filename is `mod-testkit.jar`. That preflight stopped
before source staging/compilation. The matching testkit was then frozen under the
harness's `testkit.jar` name; no source repair or repeated qualification was needed.

`OPENGGF_TASK_TREE` denotes the isolated integration checkout;
`OPENGGF_QUAL_HARNESS` denotes the task-owned external directory containing
`qualify.py`, frozen artifacts and project outputs. `OPENGGF_ROM_ROOT` denotes
the existing canonical-ROM checkout for the later runtime command;
`OPENGGF_NATIVE_FLAPPY_PROJECT` denotes the task-owned maintained Native Flappy
project built by the coordinator against this kit. These variables describe
locations, not copies of ROMs, player state or existing build trees.

Exact matrix invocation (task-owned output adjacent to `qualify.py`):

```sh
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 OPENGGF_QUAL_SOURCE_TREE="$OPENGGF_TASK_TREE" OPENGGF_QUAL_SOURCE_COMMIT=175531f6dc4a0d0005c4593022393182d1f5e58c OPENGGF_QUAL_ARTIFACT_COMMIT=175531f6dc4a0d0005c4593022393182d1f5e58c python3 "$OPENGGF_QUAL_HARNESS/qualify.py"
```

Authored resources were produced through the existing base64 inventory, playable/
object-art and TMX/from-export SDK converters, Tide's generator plus `--check`,
gallery waveform generator and exact Three Islands Maps generator comparison.
Starpost's authored `make_placeholder.py` regenerated valley (line 3, colour 15)
and ruins (line 1, colour 2); all nine records per destination exactly matched the
maintained resources. Authored Egg voice content was used unchanged. The matrix
includes all four tutorials, gallery, Time Attack and nine fixtures.

| Maintained source root | Java sources | Warnings | Strict package/validate exits |
|---|---:|---:|---|
| `docs/modding/samples/phase4-gallery-music-pack` | 0 | 0 | 0/0 |
| `examples/act-practice` | 3 | 0 | 0/0 |
| `examples/eggmans-sky` | 65 | 0 | 0/0 |
| `examples/example-mutators` | 10 | 0 | 0/0 |
| `examples/flappy-tails` | 16 | 0 | 0/0 |
| `examples/flappy-tails/tutorial/part-2-first-scene` | 2 | 0 | 0/0 |
| `examples/flappy-tails/tutorial/part-3-tails` | 3 | 0 | 0/0 |
| `examples/flappy-tails/tutorial/part-4-flight` | 4 | 0 | 0/0 |
| `examples/flappy-tails/tutorial/part-5-pillars` | 7 | 0 | 0/0 |
| `examples/hello-scene` | 2 | 0 | 0/0 |
| `examples/infinite-sonic` | 31 | 24 | 1/1 |
| `examples/putt-putt-paradise` | 36 | 8 | 1/1 |
| `examples/robotnik-tower-defense` | 12 | 0 | 0/0 |
| `examples/sitar-hero` | 41 | 0 | 0/0 |
| `examples/slay-the-robotnik` | 171 | 0 | 0/0 |
| `examples/sonic-survivors` | 25 | 13 | 1/1 |
| `examples/starfall-frontier` | 16 | 0 | 0/0 |
| `examples/starpost-valley` | 258 | 0 | 0/0 |
| `examples/three-islands` | 51 | 0 | 0/0 |
| `racing/time-attack` | 40 | 0 | 0/0 |
| `src/test/resources/mods/dynamic-rewind-src` | 1 | 0 | 0/0 |
| `src/test/resources/mods/sample-character-src/project` | 3 | 0 | 0/0 |
| `src/test/resources/mods/sample-flappy-src/project` | 3 | 0 | 0/0 |
| `src/test/resources/mods/sample-mod-src/project` | 3 | 0 | 0/0 |
| `src/test/resources/mods/sample-platformer-src/project` | 5 | 0 | 0/0 |
| `src/test/resources/mods/sample-reskin-src` | 0 | 0 | 0/0 |
| `src/test/resources/mods/sample-rom-art-remix-src/project` | 2 | 0 | 0/0 |
| `src/test/resources/mods/sample-standalone-src/project` | 4 | 0 | 0/0 |
| `src/test/resources/mods/sample-two-act-campaign-src/project` | 2 | 0 | 0/0 |

### Final residual findings: actual class, call and purpose

All 45 are `WARNING / NON_API_ENGINE_REFERENCE`, empty member field. The validator
reports class/type references; the calls and purposes below were checked against
committed source. They do not imply that the validator certified those runtime
contracts. Referenced names omit `com.openggf`; action keys A–R above identify the
specific owner and reopen conditions. Retained expert bridges remain outside the
compatibility promise. No internal class was annotated merely to silence this run.

| Referencing class | Referenced type | Actual call / purpose | Disposition / action |
|---|---|---|---|
| `infinite.CourseBadnik` | `graphics.RenderPriority` | `getPriorityBucket`: `RenderPriority.bucket`; Validate species priority bucket | Retained expert bridge / A |
| `infinite.CourseBadnik` | `level.objects.TouchResponseListener` | Implemented `onTouchResponse` native callback; Guard absorption before native hurt | Narrow missing service / B |
| `infinite.CourseBadnik` | `physics.TrigLookupTable` | Native spike/ball coordinates: `cosHex` / `sinHex`; Orbinaut quarter-turn spikes, integer sine/cosine shifted by 4 | Retained expert bridge / C |
| `infinite.CourseBurst` | `graphics.RenderPriority` | `getPriorityBucket`: `RenderPriority.bucket`; Explosion bucket 1 | Retained expert bridge / A |
| `infinite.CourseController` | `level.rings.LostRingObjectInstance` | `spilledRings`: live-object `instanceof`; Delay terrain rebasing until live unshifted ring spills settle | Retained expert bridge / D |
| `infinite.CourseController` | `sprites.animation.ScriptedVelocityAnimationProfile` | `showRunAtCourseSpeed`: `instanceof` / `withRunSpeedThreshold`; Copy native locomotion profile with lower run threshold | Retained expert bridge / F |
| `infinite.CourseHazard` | `graphics.RenderPriority` | `getPriorityBucket`: `RenderPriority.bucket`; Hazard bucket 4 | Retained expert bridge / A |
| `infinite.CourseHazard` | `level.objects.TouchResponseListener` | Implemented `onTouchResponse` native callback; Shield/20-ring guard absorption and native flashing before hurt | Narrow missing service / B |
| `infinite.CourseHazard` | `physics.TrigLookupTable` | Native spike/ball coordinates: `cosHex` / `sinHex`; Exact rolling, chain and wrecking-ball coordinates | Retained expert bridge / C |
| `infinite.CourseMonitor` | `graphics.RenderPriority` | `getPriorityBucket`: `RenderPriority.bucket`; Native monitor bucket 3 | Retained expert bridge / A |
| `infinite.CourseMonitor` | `level.objects.TouchResponseListener` | Implemented `onTouchResponse` native callback; SPECIAL hit burst and item reward once | Narrow missing service / B |
| `infinite.CourseRing` | `graphics.RenderPriority` | `getPriorityBucket`: `RenderPriority.bucket`; Ring bucket 2, sparkle bucket 1 | Retained expert bridge / A |
| `infinite.CourseRing` | `level.objects.TouchResponseListener` | Implemented `onTouchResponse` native callback; SPECIAL collection gated by native flashing/control state | Narrow missing service / B |
| `infinite.InfiniteSonicMod$Module` | `data.RomManager` | `titleLevel`: inferred `GameServices.rom().getRom()` return; ROM authority inferred from `GameServices.rom().getRom()` | Retained expert bridge / G |
| `infinite.InfiniteSonicMod$Module` | `game.GameServices` | `courseController`: `levelOrNull`; `titleLevel`: `rom`; Current restored controller lookup and supplied title ROM | Retained expert bridge / G |
| `infinite.InfiniteSonicMod$Module` | `game.sonic1.Sonic1` | `titleLevel`: independent `new Sonic1` / `loadLevel`; Independent stock reader for act-1 title backgrounds, preserving session PLC ownership | Retained expert bridge / G |
| `infinite.TitleBackground` | `game.sonic1.scroll.Sonic1ScrollHandlerProvider` | `handler`: construct / `load` / `getHandler`; Separate stock Deform handlers produce packed scanline scrolls | Retained expert bridge / H |
| `infinite.TitleBackground` | `game.sonic1.titlescreen.Sonic1TitleScreenManager` | `draw` / background: atlas constants / `bgTileColumns`; Reserved atlas bounds and viewport tile-column policy | Retained expert bridge / H |
| `infinite.TitleBackground` | `game.sonic1.titlescreen.Sonic1TitleScreenManager$BackgroundOverride` | Implemented `BackgroundOverride.draw/backdrop` contract; Replace Plane B/backdrop while retaining stock title behavior | Retained expert bridge / H |
| `infinite.TitleBackground` | `game.titlescreen.SegaPaletteFade` | `faded`: `apply` / `ROM_FADE_FRAMES`; Native fade precedence and 12-frame transition with full palette copies | Retained expert bridge / H |
| `infinite.TitleBackground` | `game.titlescreen.SegaPaletteFade$Mode` | `faded` / `backdrop`: native fade mode selection; Select native title fade mode | Retained expert bridge / H |
| `infinite.TitleWordmark` | `game.sonic1.titlescreen.Sonic1TitleScreenManager` | Constructor: `instanceof` / `setBackgroundOverride`; Attach background to the native title manager | Retained expert bridge / H |
| `infinite.TitleWordmark` | `game.sonic1.titlescreen.Sonic1TitleScreenManager$BackgroundOverride` | Constructor: `setBackgroundOverride` parameter type; Keep stock emblem/state machine while replacing background | Retained expert bridge / H |
| `infinite.ZoneMenu` | `game.GameServices` | Selection feedback: `audio().playSfx`; Native title selection `SFX_SWITCH` | Narrow missing service / I |
| `paradise.GolfMode` | `game.GameServices` | Scene capture/presenter; `camera` / `graphics` / `level`; Native scene capture/presenter, focused camera, viewport/HUD/trail/guide overlay and guest gate | Retained expert bridge / J |
| `paradise.GolfModule` | `game.GameServices` | Title construction: `graphics` / `configuration`; Native title graphics, projection width, character/sidekick configuration and menu audio | Narrow missing service / I,J |
| `paradise.GolfOnline` | `game.GameServices` | `receive` / publish: scene presenter/capture, camera, audio; Publish/accept scene frames, peer viewport, focused radius and charge/release cues | Retained expert bridge / J |
| `paradise.GolfSounds` | `game.GameServices` | `play` / `menu`: `audio` native cues; Native title/feedback track IDs and act-clear cue | Narrow missing service / I |
| `paradise.GolfSwing` | `game.GameServices` | `launch` / vertical drive / landing spin: focused player/audio; Focused native springing/angle/speed state and launch/landing-spin audio | Existing partial alternative; retained expert bridge / J |
| `paradise.objects.GolfUpSpring` | `game.sonic2.objects.SpringObjectInstance` | Subclass constructor / `super.update` / `applyUpSpring`; Native Obj41 subclass retains ROM geometry, art, strength, animation and SFX | Retained expert bridge / K |
| `paradise.ui.GolfTitleArt` | `game.sonic2.titlescreen.TitleScreenDataLoader` | Loader field / native planes, patterns and palette; ROM title planes/palette/pattern GPU cache and reset after course | Retained expert bridge / L |
| `paradise.ui.GolfTitleArt` | `game.sonic2.titlescreen.TitleScreenMappings` | Frame field: `createFrames`; ordered piece emission; Ordered priority mapping pieces and crown/wind frames | Retained expert bridge / L |
| `survivors.ArenaObjects` | `level.objects.ObjectConstructionContext` | `construct` / `isProbeConstruction`; Inject construction services, suppress probe admission, then admit rewindable auxiliary object | Retained expert bridge / M |
| `survivors.Boss` | `graphics.RenderPriority` | `getPriorityBucket`: `RenderPriority.bucket`; Boss bucket 3 | Retained expert bridge / A |
| `survivors.Boss` | `level.objects.TouchResponseListener` | Implemented `onTouchResponse` native callback; Native player contact: guard absorption then laugh, excluding CPU/dead players | Narrow missing service / B |
| `survivors.Draw` | `level.render.SpritePieceRenderer` | `scaledSprite`: `renderPiece`; Expand/mirror mapping pieces into scaled world-space quads | Narrow missing service / N |
| `survivors.Draw` | `level.render.SpritePieceRenderer$TileConsumer` | `scaledSprite`: tile-emission lambda; Preserve piece tile, palette and priority bits while emitting scaled quads | Narrow missing service / N |
| `survivors.Enemy` | `graphics.RenderPriority` | `getPriorityBucket`: `RenderPriority.bucket`; Enemy bucket 4 | Retained expert bridge / A |
| `survivors.Enemy` | `level.objects.TouchResponseListener` | Implemented `onTouchResponse` native callback; Guard absorption at actual native player contact | Narrow missing service / B |
| `survivors.Pickup` | `graphics.RenderPriority` | `getPriorityBucket`: `RenderPriority.bucket`; Collected bucket 1 versus active bucket 3 | Retained expert bridge / A |
| `survivors.Shot` | `graphics.RenderPriority` | `getPriorityBucket`: `RenderPriority.bucket`; Projectile bucket 3 | Retained expert bridge / A |
| `survivors.Shot` | `level.objects.TouchResponseListener` | Implemented `onTouchResponse` native callback; Guard absorption destroys the absorbed projectile | Narrow missing service / B |
| `survivors.Stage` | `game.sonic2.audio.Sonic2Music` | Run/reward/restore: named track `.id` to injected audio; Boss/final boss, super/chest, restored boss, act-clear, emerald and ending cues | Existing partial alternative; retained expert bridge / P |
| `survivors.SurvivorsMod$Module` | `game.rules.DynamicArtDmaServiceModel` | Rules constructor: inferred `dynamicArtDmaService` copy; Full GameRules copy changes speed-shoes expiry while retaining native DMA policy | Narrow missing service / R |
| `survivors.SurvivorsTitle` | `game.GameServices` | Zone picker: `audio().playSfx(0xCD)`; Start-zone picker title navigation SFX 0xCD | Narrow missing service / I |

### Seven removed historical warnings and their actual owners

- `infinite.CourseController`, `paradise.objects.GolfUpSpring` and `survivors.Stage`
  no longer warn on `sprites.NativePositionOps`: upstream `6159c444d` declared the
  existing native reset/preserve-subpixel helper as `@ModApi`, now present in the final source,
  compiled API and SDK. The callers retain that helper and their position semantics;
  no unqualified setter substitution or task-local warning-suppression annotation
  was introduced. Owner: playable position API. The historical E reopening request
  is superseded for these operations present in the candidate API.
- `survivors.Hud` and `survivors.Stage` no longer reference concrete `Tails`:
  `266c8be0c` uses `characterKey().equals(CharacterKey.TAILS)` for the supported native
  Sonic/Tails perk policy. Owner: Survivor run-character policy, preserving captured
  identity/restore behavior; this does not generalize custom subclasses to abilities.
- `survivors.Stage` no longer references `ObjectTerrainUtils` or `TerrainCheckResult`:
  `266c8be0c` uses injected `floorSurface(FOREGROUND, PRIMARY)` with immutable
  `TerrainSurface`, preserving the 16-pixel origin scan, distance 0..15 acceptance
  and original fallback. Owners: terrain provider and author spawn placement.
  Historical O/Q existing-alternative rows are adopted, not remaining warnings.

The actual final finding set is exactly the historical set minus those seven pairs;
there are no added or silently omitted pairs. Existing partial alternatives remain
explicit in GolfSwing and Survivor soundtrack rows. Other retained/missing services
still require the owner-specific qualifications above.

### G: deferred native-title failure and ROM-generation lifetime

At this final source, `InfiniteSonicMod.Module.titleLevel` (lines 209–215) catches
`IOException | RuntimeException` and memoizes `Optional.empty()` in `titleLevels`;
its supplied-ROM `titleGame` is long-lived. This unchanged native expert bridge is
a concrete known limitation, distinct from the four repaired P18 scene-art facades.
Neither 26 strict-clean packages nor this class-reference inventory certifies its
failure recovery, programming-fault attribution or ROM/session replacement behavior.

`TitleBackground` requests `levels.apply` during update/draw/backdrop, potentially
multiple times per frame. Removing the negative cache alone would cause repeated
unbounded work and would leave ROM-owner lifetime unresolved. Owner G must provide
bounded native-title admission/status/recovery, propagate programming faults, publish
successful art only, and invalidate or replace the reader/cache on supplied-ROM or
session changes. Reopen with actual native-title tests for missing/failed-to-repaired
assets, attempt bounds/deadlines, verified owner faults, repeated requests per frame,
stock fallback, two instances, ROM replacement and resource retirement, plus canonical
pixels/atlas isolation. No speculative catch/cache change was made during final
qualification; retained source is not a completed native-title failure contract.

### Final Native Flappy artifacts and consumption

The matrix's actual SDK-built `sample-flappy` package is version `1.0.0`, API range
`>=0.7.0 <0.8.0`, base `s3k`, entrypoint `example.flappysample.FlappySampleMod`.
Its three classes and required converted resources package/validate with zero findings
under both policies. Retained binary:
`$OPENGGF_QUAL_HARNESS/projects/23-project/build/qualified.jar`,
SHA-256 `1ecf6473580be55502898e31b029b5830535c1a01404eed3f734ac93a5b1bc8e`.
The root separately produced the maintained `build.sh`/Maven jar against the same final
kit at `$OPENGGF_NATIVE_FLAPPY_PROJECT/target/sample-flappy-mod.jar`,
SHA-256 `03c311c6701058eb69721a9540f2ab327af0573f10e00b6c73203ad92b9e0bfd`.
The latter is the requested runtime binary; matrix validation is not its launch pass.
Its actual normal canonical `TestSampleFlappyIntegration` request 43929 completed
at 20:40:53 BST: **11/11 passed, zero failures/errors/skips**, Maven execution 1m04,
suite 1.633s. Queue telemetry records 3,774.9s waiting and 65.3s lease hold; telemetry
is cost evidence, not a test receipt. Fresh runtime build metadata was exactly
`app.commit=175531f6d`, `app.dirty=false`, version/base `0.7.prerelease`; XML records
Java 25.0.4.1, the requested binary and all three canonical absolute ROM properties.
The runtime jar hash was rechecked and unchanged.

Exact normal request from the clean owned world tree:

```sh
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSampleFlappyIntegration' "-Dsample.flappy.jar.path=$OPENGGF_NATIVE_FLAPPY_PROJECT/target/sample-flappy-mod.jar" "-Dsonic1.rom.path=$OPENGGF_ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$OPENGGF_ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

The property selects the actual SDK/Maven binary and validates it, without the
fixture's source-build fallback. Each isolated repository goes through the normal
title editor's two-confirm trust, Apply, persisted hash/enablement and validated
rescan before runtime registration. The case results inspected in actual XML were:

| Completed case | Outcome |
|---|---|
| `firstForwardUpdateCreatesSixIndependentEntriesAndCoversSuper32By9` | PASS |
| `engineRestartResetsRunAndRecreatesTheSameInitialSequence` | PASS |
| `scoreCrossingOneHundredDoesNotRunCollectibleRingBonusLogic` | PASS |
| `pipeAndVisibleBoundsAlwaysUseCrushDeath` | PASS |
| `initialTitleCardSeesTheResolvedS3kCustomZonePalette` | PASS |
| `bootsVisibleNativeTailsAtFixedAnchorAndMaintainsFlight` | PASS |
| `rewindRestoresAndResimulatesScoreAndLiveRecycleExactly` | PASS |
| `rewindAcrossFirstControllerUpdateKeepsOneControllerAndSixStablePipes` | PASS |
| `recycleMovesTheSameEntryAndAdvancesCounterPermutation` | PASS |
| `onePipeScoresOncePerCycleAndResetEnablesTheNextCycle` | PASS |
| `customZoneComposesNativeTailsHudCreatorClaimsAndDecodedPipeArt` | PASS |

These are actual native launch/flight/score/recycle/restart/rewind and decoded-art
checks through the real title trust flow, without ambient desktop UI. State was
isolated under the fixture's temporary repositories. This worker did not replace
installed jars or change player trust, enablement, saves or configuration; those
installation/preservation steps remain coordinator-owned.


Task-owned external source/class/jar/structured-report outputs remain accounted for
until binary consumption and durable evidence are complete. Cleanup must preserve
both final Native Flappy binaries until the coordinator's P19 handoff is consumed,
then remove only this worker's known scratch outputs; unknown/user outputs, the
coordinator's kit and binaries, ROMs and player state remain untouched. No raw logs
were archived. Prose-only follow-ups retain the explicit `175531f6d` producer identity
and do not require relabelling or rebuilding these artifacts.

## Playable presentation responsibility extraction

The later architecture guard reproduced the clean `175531f6d` class-budget failure:
`AbstractPlayableSprite` had 3,264 effective lines against its existing 3,258 limit.
The newly added protected final `drawStandardArt` helper accounts for six effective
lines. Source `9e2bf932f` extracts standard pose submission and the existing native
hurt/invulnerability display decision into package-private, stateless
`PlayableSpriteArtPresentation`, restoring 3,258 without raising the ratchet or
compressing formatting. The protected helper and candidate API signatures remain
unchanged. The collaborator owns no sprite state, rewind state, cache or resources;
physics, native positioning and renderer ownership are untouched.

Read-only peer review checked the exact virtual getter order and short-circuit
behavior: hidden check, renderer null check, renderer receiver, frame, centre X/Y,
then horizontal/vertical flips. Display timer fields are captured before virtual
`isHurt`, with direct-display ownership read afterward as before. No preservation
must-fix was found; this is review evidence, distinct from the executed checks.

The lean red request 89821 ran
`TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction`
at the clean producer and failed exactly that one check (zero errors/skips), 23.117s
execution after 53s queue waiting. The lean green request 34957 completed at
20:52:53 BST in 1m11 after 26s waiting: **192/192 passed, zero failures/errors/skips**.
Its generated metadata was `app.commit=175531f6d`, `app.dirty=true`, version/base
`0.7.prerelease`; the only dirty source files were the two presentation extraction
files, subsequently committed unchanged as `9e2bf932f`. Actual XML counts were:

| Selected class | Passed |
|---|---:|
| `TestModCharacterConstructionIdentity` | 14 |
| `TestPlayableCharacterRegistry` | 11 |
| `TestAbstractPlayableSpriteRewindCapture` | 14 |
| `TestSidekickCpuDespawnParity` | 65 |
| `TestPlayableMutatorPresentation` | 8 |
| `TestSpriteManagerRender` | 4 |
| `TestGraphicsManagerSpriteSatReplay` | 2 |
| `TestPlayerSpriteRendererPaletteContext` | 2 |
| `TestArchitecturalSourceGuard` | 72 |

Exact green invocation:

```sh
env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestModCharacterConstructionIdentity,TestPlayableCharacterRegistry,TestAbstractPlayableSpriteRewindCapture,TestSidekickCpuDespawnParity,TestPlayableMutatorPresentation,TestSpriteManagerRender,TestGraphicsManagerSpriteSatReplay,TestPlayerSpriteRendererPaletteContext,TestArchitecturalSourceGuard' test
```

These are bounded pure sprite/character/CPU/SAT and architecture checks. They do not
relate the earlier clean producer's artifacts to a later engine build or certify
ordinary/guard delivery. The unchanged-source Native Flappy binary qualification
above was not repeated; final integration, broad comparison and cleanup remain
coordinator-owned.
