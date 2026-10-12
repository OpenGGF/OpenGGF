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
Skipped outcomes were inspected:32 Infinite terrain/fixture assumptions,5 GL/EGL
availability checks,1 CPZ spin-tube interaction assumption and19 opt-in checks.
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
sources after this batch), with at most64 expected failures and256-character request/
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
