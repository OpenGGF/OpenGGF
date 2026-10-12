# Mod author and framework audit — 2026-10-12

The highest-value work is persistence correctness, render-independent scene input,
and an ordinary-gameplay hold contract. Several examples already have useful shared
helpers; the remaining work combines adoption of those helpers with a few narrow API
additions. A generic game framework or a wholesale rewrite of the mods is not justified.

## Basis, scope and interpretation

Reviewed `develop` at **`f9d4e04747a6474c81cc9c9424951fecf3de191b`**, after fetching
and fast-forwarding from `765ec8533`. Three Codex subagents covered the eleven example
projects; the parent reviewed the fixture mods, SDK starters, creator export, handbook,
and cross-cutting contracts, then reconciled the findings against production code.
The isolated review tree was `.worktrees/ai-mod-author-audit`.

The tracked inventory contains **25 concrete manifests**: eleven example games, four
Flappy Tails tutorial checkpoints, nine test-fixture mods, and the music-pack sample.
The examples contain 474 production Java files / 82,977 lines, including the tutorial
checkpoints. Review covered every project's source areas and framework boundaries,
with deeper checks of the findings below. This is an inventory-complete static audit
with selected actual-source probes, not exhaustive rule, route, graphics or timing
qualification. Existing tests were inspected for their coverage; their presence is
not treated as a passing result.

The [release descriptor](../../../mod-api-release-policy.properties) says API
`0.7.0`, unpublished candidate, no published baseline. All 25 source manifests use
`>=0.7.0 <0.8.0`. Candidate mutability permits deliberate API improvements, but each
surface change still needs the descriptor/version/signature policy and owner-boundary
checks in the [compatibility contract](../mod-api-compatibility.md).

Two ignored installed jars in the main checkout were inventoried read-only:
`mods/eggmans-sky.jar` declares the candidate range; `mods/sample-flappy-mod.jar`
declares obsolete `>=2.4.0 <3.0.0` and cannot match the current API. Rebuild the latter
from the maintained source rather than editing its manifest. These are player artifacts,
not additional maintained source projects; their bytecode was not independently audited.
Other branches and unrelated unmerged worktrees are outside this snapshot's scope.

Priority **P1** means player-visible correctness or progress loss; **P2** means framework
usage, author workflow or an important unqualified lifecycle risk; **P3** means a bounded
convenience improvement. Each finding distinguishes executed evidence from static
reasoning. Recommendations are unimplemented unless explicitly marked as documentation
corrections delivered by this audit.

## Coverage ledger

### Example projects

| Project | Production Java files / lines | Areas reviewed and disposition |
|---|---:|---|
| [Hello Scene](../../../examples/hello-scene/README.md) | 2 / 262 | Registration, scene/cache lifecycle, input, saved counter, portable packed-mod test. Good small boundary reference; keep it small. |
| [Flappy Tails](../../../examples/flappy-tails/README.md), including four checkpoints | 32 / 4,484 | Tutorial evolution, flight/course/autopilot model, art/palette/background/title cards, menus, records, scene and tutorial checks. Preserve the ROM flight model; repair record preservation and practice identity. |
| [Starfall Frontier](../../../examples/starfall-frontier/README.md) | 15 / 2,005 | Generation/biomes, mining/building/crafting/combat/quests, view/camera/art, chunked saves and recovery, scene/model tests. Expanded geometry is inconsistent with camp interaction; custom ROM block decoding merits migration review. |
| [Sitar Hero](../../../examples/sitar-hero/README.md) | 40 / 6,275 | Catalogues/charts, timing/judgement, career/practice, action bindings/calibration, performers/stages, cancellable audio, network protocol, scene/storage/tests. Strong separation of rhythm rules from host timing; preserve future saves and honor remapped calibration actions. |
| [Eggman's Sky](../../../examples/eggmans-sky/README.md) | 62 / 15,036 | Galaxy/planet/terrain/inventory/player models, surface and space modes, software renderer, ROM art/recolours, loading, voice/audio, UI/save lifecycle and engine tests. Fix decoder bounds/version and generation-scoped caches. |
| [Slay the Robotnik](../../../examples/slay-the-robotnik/README.md) | 170 / 28,770 | Rules/content/cards/relics/events/maps/run/save, UI/control/fonts, ROM art/composite bosses/animation, combat presentation and test bots. Keep room-entry save and rules separation; remove drawing from targeting authority. |
| [Three Islands](../../../examples/three-islands/README.md) | 51 / 8,848 | Combat/progression/story/save, maps/routes/dungeons/puzzles, mixed-ROM heroes/bosses, screens/controls/music/loading and source tests/generator. Maps regenerate exactly; repair checked save recovery and success feedback. |
| [Infinite Sonic](../../../examples/infinite-sonic/README.md) | 31 / 4,963 | Seeded course/terrain recycling, entities/physics/HUD, launch/menu/art, rewind/continue, leaderboard and engine route tests. Intentional expert dependencies need a visible debt ledger; migrate raw leaderboard I/O to owner storage. |
| [Putt Putt Paradise](../../../examples/putt-putt-paradise/README.md) | 36 / 4,618 | Course checkpoint/hold/launch/roster state, input/swing/replay/presentation, spring/placements, title art, direct TCP protocol/lifecycle and tests. Strong `CourseControl` consumer; ten internal-reference warnings are already acknowledged in its README. |
| [Robotnik Tower Defence](../../../examples/robotnik-tower-defense/README.md) | 12 / 1,284 | Scene, battlefield/waves/economy, title/HUD/mouse-pad controls, ROM/font art, records and rules/scene tests. Existing font/focus helpers are adopted; cache repeated preview preparation locally before adding an API. |
| [Sonic Survivors](../../../examples/sonic-survivors/README.md) | 23 / 6,432 | Module/arena/zone wrappers, auxiliary entities, stage/menu/waves/combat/loot, profile/run/rewind, input/HUD/art and multi-act engine tests. Check payout across rewind; ordinary-row menu freezing needs a better framework contract. |

Local example test trees contain 60 Java files, including helpers; 59 are omitted from
creator-kit export. Eggman's Sky, Infinite and Survivors instead have engine-side tests;
absence of a local test tree is not absence of tests. The root's `Test*Example` and
`Test*Scene` classes frequently compile these real external sources. Their coverage
gaps below are about missing scenarios, not file counts.

### Fixture mods, data samples and SDK starters

| Source | Reviewed obligations and follow-up |
|---|---|
| [Music pack](../../modding/samples/phase4-gallery-music-pack/README.md) | Generated original waveform, provenance/license, loop bounds, manifest and package recipe; data-only, no code trust. No new shared gameplay abstraction needed. |
| [Data-only reskin](../../../src/test/resources/mods/sample-reskin-src/META-INF/openggf-mod.yaml) | Current stock `signpost` art key, original base64 image, converter and packaging script. It correctly avoids code and has the maintained API range. |
| [Object/zone sample](../../../src/test/resources/mods/sample-mod-src/README.md) | Transactional object/art/preview/zone registration, injected services, semantic destruction sound, patrol helper, recreation, source build and integration consumers. Unregistered character is explicitly a stub. |
| [Character sample](../../../src/test/resources/mods/sample-character-src/README.md) | Bounded playable-art intake, owned factory/identity, physics transformation, sensors/rendering, source builds and launch/save/rewind tests. Runtime-code suffix parsing is repeated author work; see U7. |
| [Standalone sample](../../../src/test/resources/mods/sample-standalone-src/README.md) | No-ROM level/assets, owner characters/team, streamed sound, declarative providers, object registry and recreation. Repeated registry/identity plumbing is a convenience candidate, not a broken standalone loader. |
| [Native Flappy](../../../src/test/resources/mods/sample-flappy-src/README.md) | Destination-scoped Tails/input/HUD, native flight, fixed camera, captured recycling-pipe state and geometry, injected object queries and recreation. Keep this distinct from the whole-screen Flappy tutorial. |
| [Standalone platformer](../../../src/test/resources/mods/sample-platformer-src/README.md) | Art/level/ring assembly, declarative module, Bolt ability/landing/reset/subclass snapshot, ZapBug patrol/destruction and SpringPad contact, source builds and integration/rewind tests. Its contact/sound workarounds are genuine API friction; see U3/U7. |
| [ROM-art remix](../../../src/test/resources/mods/sample-rom-art-remix-src/README.md) | Bounded ROM art/mapping/DPLC request, owned object and zone, animation/recreation, source builds and decoded-art/session tests. Hard-coded expert requests are legitimate when supported by ROM identity/evidence. |
| [Two-act Tide Circuit](../../../src/test/resources/mods/sample-two-act-campaign-src/project/README.md) | Multi-act registration, water/scroll/palette/animated tile/render providers, captured runtime state, goal/progression, authored binary generator and route obligations. Preserve its deliberate per-act consumers and original assets. |
| [Dynamic rewind fixture](../../../src/test/resources/mods/dynamic-rewind-src/example/dynamicrewind/DynamicProbe.java) | Owner-loader dynamic recreation and captured scalar state; intentionally no-op registration/rendering. This is a diagnostic contract fixture, not a creator-game starter. |
| [SDK templates](../../../src/main/resources/META-INF/openggf-mod-sdk/templates/README.md.template) and [maintained starter index](../../../src/main/resources/META-INF/openggf-mod-sdk/starters/index.txt) | Generated object/zone project plus complete character/standalone materialization, two-artifact build, converter cleanup and strict validation advice. Starter tests require manual POM/profile copying; see U6. |

The nine maintained gallery samples include the music pack and eight `sample-*` mods;
the dynamic fixture is the ninth fixture directory with a manifest. No generic
recreation requirement was imposed on whole-screen scene state: arbitrary scene
games do not currently promise debug-world rewind. Durable profiles may remain
outside gameplay snapshots, provided repeated settlement cannot duplicate rewards.

## Correctness and framework-usage findings

### R1. Save format rejection can still destroy the original — P1

Flappy [Records.java](../../../examples/flappy-tails/src/main/java/flappytails/Records.java)
`:24–52` catches a rejected future version and returns empty records, then saves those
defaults without retaining a read-only/incompatible status. Sitar
[CareerJournal.java](../../../examples/sitar-hero/src/main/java/sitarhero/model/CareerJournal.java)
`:94–113` correctly declines an unsupported header, but the fresh journal remains empty;
the scene later writes its v1 encoding. Both models lose the information needed to
preserve a rejected file. `VersionedSettings.requireVersion` already exists; another
parser does not fix the load-to-exit policy.

Eggman's Sky [Player.java](../../../examples/eggmans-sky/src/main/java/eggsky/game/Player.java)
`:173,250–271` emits `version=1` but never validates it. Arbitrary prose and
`version=999` are accepted and re-encode as version 1. Saved `bonusSlots` and technology
levels determine allocation before validation: `slots()` computes
`16 + 4 * tech[T_CARGO] + bonusSlots`, then `Inventory.resize` allocates that capacity.
An actual-source probe observed **1,000,016 slots** from `bonusSlots=1000000`.
This establishes unchecked allocation, not a measured OOM. The scene's Continue/exit
paths can publish and overwrite such a decoded save; that host consequence was inspected
statically, not exercised in a ROM session.

**Repair:** preserve explicit absent/valid/invalid/incompatible load status. Decode into
a candidate, validate format, identities and bounded arithmetic, then publish. An
incompatible file must stay untouched unless the player explicitly starts replacement
progress. Validate technology levels and capacity before allocating. Use existing flat
settings helpers where appropriate; keep complex world/game codecs local.

**Acceptance:** supported legacy/current round trips, future files surviving entry and
exit byte-for-byte, malformed/empty/duplicate-key input, negative/extreme capacity,
overflow, failure notices, and no partial live-state mutation. Flappy/Sitar model probes
reproduced the rejection-to-empty behavior; whole-scene overwrite checks remain required.

### R2. Recovery and save-success feedback are unreliable — P1

Three Islands [Game.java](../../../examples/three-islands/src/main/java/threeislands/Game.java)
`:140–159` recovers from valid `save.bak` after primary decode fails, then rotates the
unvalidated primary bytes over that good backup. If the following primary write fails,
the recovered generation is lost. Both boolean write results are ignored.
[FieldScreen.java](../../../examples/three-islands/src/main/java/threeislands/screen/FieldScreen.java)
`:189–197` nevertheless announces “Journey saved.” Slay's
[Shell.java](../../../examples/slay-the-robotnik/src/main/java/slaytherobotnik/scene/Shell.java)
`:127–148` and `scene/Profile.java:29–36` similarly discard write/delete results.
Flappy records and several other profiles also discard boolean persistence outcomes.
Tower's `TowerDefenseScene.java:265–269` additionally sets `recorded=true` after a
rejected write, preventing a later results/exit call from retrying. Separate
"counted this campaign" from dirty persistence state so retries cannot duplicate wins.

This is a static finding against the actual `ModStorage` contract. Storage failures may
return false without throwing. Existing
[FileSceneStorage](../../../src/main/java/com/openggf/mods/scene/host/FileSceneStorage.java)
already provides bounded owner-scoped reads and per-file replacement with an atomic-move
attempt; the missing abstraction is validated recovery/result orchestration. Starfall's
`WorldSaves.java:24–45` already validates the old generation before backup and checks
publication, while Eggman's Sky checks write success and reports failure.

**Repair:** back up only successfully decoded state; propagate a save outcome to the UI,
preserve a recoverable generation on every failed write, and limit repeated notices.
Do not change the mod's intended save boundary or silently restore gameplay from profile
files. Failure-injection, corrupt-primary recovery and interrupted-rotation tests are
required before calling the repair complete. See U1.

### R3. Starfall's camp still uses the old world coordinate — P1

[World.java](../../../examples/starfall-frontier/src/main/java/starfall/World.java)
`:318` tests `abs(ty - 27) < 4`, while
[FrontierView.java](../../../examples/starfall-frontier/src/main/java/starfall/FrontierView.java)
`:117` draws the camp relative to `surface(40)`. Expanded worlds have the camp surface
at row 116, with the player interacting near row 114. Actual generated SMALL, MEDIUM
and LARGE worlds rejected that reachable camp interaction; the legacy row 27 succeeded.
The progression test uses the old coordinate and therefore masks the reported route.

**Repair:** give the world one semantic camp/beacon location used by generation,
interaction, drawing and tests. Keep this game-specific location in Starfall; a new
engine location API is unnecessary. Test completion at the generated camp for every
world size and after save restoration, without teleporting to legacy geometry.

### R4. Slay combat targeting depends on drawing

**Priority: P1.**

[CombatView.java](../../../examples/slay-the-robotnik/src/main/java/slaytherobotnik/scene/CombatView.java)
`:936,940,1073–1074` assigns stage placement and clears/populates enemy target boxes
during `draw`. Logic at `:690–700,726–745` creates and activates target hotspots from
those boxes. The stored stage is explicitly “from the last draw” at `:1181`.
The [scene contract](../../../src/main/java/com/openggf/mods/scene/ModScene.java) permits
draws to be skipped or repeated. The existing ROM smoke path draws after every tick and
uses a debug kill to advance fights; it does not prove draw-independent targeted input.

**Repair:** prepare immutable stage/pose/layout geometry in update or entry preparation,
and draw it without changing interaction authority. Keep effect choreography and boss
rules in Slay. U4 can remove repeated composite-placement arithmetic shared with Three
Islands. This is a static data-flow finding; no live targeting failure was executed here.

**Acceptance:** targeted cards and monitors under zero, ordinary, sparse and duplicated
draw calls must produce identical HP, energy, choices and save state. Verify transformed
sprite bounds separately from representative rendered pixels.

### R5. Survivors' durable payout needs a rewind settlement check — P1 risk

[Stage.java](../../../examples/sonic-survivors/src/main/java/survivors/Stage.java)
`:1637–1670` adds bank, kills, elite counts and records to the non-rewound Profile,
then ends the captured run. [RunState.java](../../../examples/sonic-survivors/src/main/java/survivors/RunState.java)
`:257–265` captures/restores `active` and reward inputs; the registration publishes
Profile as a normal service and RunState as a rewind adapter. No history invalidation or
run-settlement identity was found around banking. Restoring a pre-death run can therefore
make the payout eligible again. Existing death-payout and active-rewind tests are separate.

This scenario remains **unexecuted**; it is a concrete lifecycle risk, not a claimed
runtime reproduction. Verify capture → earn → die/bank → restore → die/bank before
choosing a repair. Preserve durable records, but use a stable run/settlement identity
outside rollback so each reward is credited once. Infinite's non-rewound award/run-ID
handling is a useful comparison. A generic economy or profile API is not justified.

### R6. Manual menu freezes do not freeze the ordinary gameplay row — P2

Survivors [Stage.java](../../../examples/sonic-survivors/src/main/java/survivors/Stage.java)
`:1733–1769` freezes the player through object control and restores selected invincibility
timers. A speed-shoes pickup uses the stock timer, whose display phase continues without
an object-control gate. Time spent in menus can consume that effect. Infinite similarly
has authored held/ready presentation over an ordinarily advancing world. Its partial
hold may intentionally leave enemies/animation live; preserve that product policy
rather than treating it as the same freeze defect.

The first proposed answer was adoption of `GameplayFrameController` HOLD. That is
incomplete: [ControlledFrameRuntime.java](../../../src/main/java/com/openggf/game/mode/ControlledFrameRuntime.java)
`:51–58` unconditionally supplies **neutral input** on advanced controlled rows. Direct
adoption would lose normal native movement/jump input. Golf intentionally wants that
neutral-step policy; Survivors and Infinite need ordinary input between holds.

**Repair/design:** U2 should distinguish ordinary-input advance, controlled neutral
advance, and HOLD at the existing frame boundary. Validate all stock/world clocks,
power-up timers, input edges, entry presentation, audio policy, rewind and frame counts.
Do not copy timer restoration into another mod or introduce a second stepping loop.
The menu timer result is static inspection, not a completed runtime test.

### R7. Eggman's Sky caches omit galaxy identity — P2

[Game.java](../../../examples/eggmans-sky/src/main/java/eggsky/Game.java)
`:242–244,338–365` retains a planet by its system-coordinate/index key when the galaxy
changes. `SpaceMode.java:260–272` uses the same incomplete key for orbital textures;
`Planet.java:75` and generated recolour keys omit the galaxy seed. `Art.java:39–40,207–216`
also retains unbounded mod-owned tinted-image maps through the scene lifetime.
The host's texture eviction does not remove these CPU references.

**Repair:** include seed/generation identity in keys or clear generation-scoped caches
on new/load; bound procedural image caches and retain active images safely. Compare
two seeds at the same coordinate against fresh scenes, including new expedition and
Continue. Measure retained image count before claiming a memory/performance improvement.
`SceneArtCache` already bounds decoded banks; it is not a procedural planet-image cache.

### R8. Practice and calibration lose the selected input semantics — P2

Flappy's `FlappyScene.java:204` practice entry treats zone zero as a normal run, permitting a practice flight
from Angel Island to affect records. Sitar's calibration samples fixed pad BUTTON_A
rather than the configured kick action at `SitarScene.java:803–809`, even though gameplay supports remapping.
These are local policy/adoption corrections, not demand for another input framework.
Keep practice identity independent of start offset, and route calibration through the
same configured action/event timestamp used by gameplay. Test first-zone practice,
alternate pad bindings and transitions with held buttons. Source evidence and model
coverage were inspected; no full scene input test was run for these findings.

### R9. Gameplay examples still depend on engine internals — P2

A source import comparison against the current signature inventory found **31 explicit
production import sites** outside the pin: Infinite 12, Golf 8, Survivors 11. This is an
import-site count, **not** a bytecode-validator warning count; wildcard and fully qualified
uses are not counted. `ModValidator.java:150–154` deliberately makes
`NON_API_ENGINE_REFERENCE` a warning. These expert dependencies are therefore admitted
prototype debt rather than a bypass of code trust. Golf acknowledges its ten package
warnings; the other READMEs do not enumerate the equivalent compatibility exposure.

Examples include `GameServices`, game-specific title loaders, terrain helpers,
`ObjectConstructionContext`, `NativePositionOps`, stock spring classes and badnik
configuration. Do not make whole internal packages public to clear warnings. Map each
use to an existing injected service, a narrow missing semantic capability, or a temporary
expert dependency with its warning identity and acceptance test.

Infinite [InfiniteSonicMod.java](../../../examples/infinite-sonic/src/main/java/infinite/InfiniteSonicMod.java)
`:89–90` builds its leaderboard from `SavePaths.root()`; `Leaderboard.java:80,107–110`
performs raw file reads/write/move. Owner-scoped `ModContext.storage()` already exists,
and Survivors uses it. Migrate bounded I/O while preserving format, top-ten ordering,
run-ID idempotence and eligible legacy reads. Raw storage is an adoption task; it does
not justify a new filesystem API. U3/U5 and the exploratory capabilities below address
other recurring internal dependencies.

### R10. Creator exports omit the tests and source tools authors need — P2

[build_creator_kit.py](../../../tools/modding/build_creator_kit.py)
`:78–89` exports `src/main`, with only Hello's integration-test exception and Tide's
generator exception. An in-memory invocation of the real exporter confirmed that
**59 existing Java test files** are absent, and Three Islands' `tools/generate_maps.py`
is absent even though its README explains changing maps through that generator.
Source examples contain 60 test Java files; only Hello's one is exported. No generated
kit ZIP was needed to establish this selection behavior.

[testing.md](../../modding/testing.md) requires generated-project authors to copy a
Jupiter/testkit/Surefire profile from Hello's exported POM. The testkit itself is already
implemented. U6 should distribute an executable testing recipe with each appropriate
starter/fork and retain authored generator sources, while keeping test classes and
dependencies out of the distributed mod jar. Test empty discovery and failing tests as
well as a green path. Keep checkout-only ROM/internal tests explicitly separate.

### R11. Immutable definitions are rebuilt on hot paths — P2

Infinite `CourseSpecies.java:49–95` allocates trait records/frame arrays during badnik
movement, animation, collision and drawing. Survivors `Species.java:48–108` has the
same pattern; `Stages.ground/air` also constructs act lineups repeatedly. Tower
`Catalog.java:91–110` builds wave spawn lists through `Battlefield.preview` on each
draw, and `Catalog.defense` returns fresh definitions for repeated lookups. Flappy's
`Zone.tour()` reconstructs its zone list on update/draw queries; Sitar's
`SongCatalog.arrangements()` rebuilds catalogues during role/selection queries.

Cache immutable definitions per module/entity and prepare previews/lineups once. The
static-state restriction does not prohibit instance-owned tables. Preserve frame-array
ownership, encounter ordering, seeded behavior, recreation and session isolation;
derived object caches must not become mutable shared gameplay authority. Check warmed
lookup allocation and profile the same dense scenario before claiming a frame-time
gain. An engine catalog framework is unnecessary for this repair. A narrower object
visitor/query API is only worth considering after measuring repeated filtered-list
allocation in Survivors and Infinite.

## API uplift and rewrite decisions

These are proposed focal points, not promises that a roadmap or helper already exists.
Prioritize repairs demonstrating the behavior before introducing shared contracts.

| ID / priority | Proposal and concrete consumers | Boundary and acceptance |
|---|---|---|
| **U1 / first** | Typed save-slot loading, checked publication and validated recovery over existing `ModStorage`. Three Islands backup, Starfall chunked generations, Slay run/profile, Flappy/Sitar version rejection, Eggman's Sky expedition. | Keep creator codecs/migrations and durable-save policy local. Return absent/valid/recovered/invalid/incompatible and explicit save outcomes; preserve good generations and future files under failed writes. Single-file replacement already exists. Avoid claiming multi-file crash atomicity without designing it. |
| **U2 / first** | Extend the existing frame controller with ordinary-input advance, controlled neutral advance and HOLD. Survivors menus, Infinite ready/death/continue, Golf controlled stepping. | One production row owner across interactive/headless/rewind paths; preserve Golf's neutral behavior and ordinary native input. No clocks/physics during HOLD beyond documented entry presentation/audio work. Test timers, filters, edges, transitions and full forward replay. |
| **U3 / next** | Narrow injected terrain/contact and semantic player/object effects. Survivors' Enemy/Boss/Pickup/Shot repeat live floor probes; Infinite/Survivors need configurable destruction recipes; standalone SpringPad and GolfUpSpring need contact/bounce semantics. | Typed queries/results and owner-bound spring/destruction/SFX effects, preserving game rules and supplied-ROM behavior. Infinite's detached terrain survey is a different operation and must not be collapsed into a live floor probe. `SpringPad` works around unavailable solid-contact markers; Golf subclasses an internal stock spring. `setSpringing` is already public on the candidate `AbstractPlayableSprite`, though absent from `PlayableEntity`: do not invent it again. Namespaced hit sounds currently require an attack override because `DestructionConfig` carries a raw integer; `DestructionEffects` itself already exists. Test sidekick breadth, contact edge/velocity/shape, one-shot effects and rewind. |
| **U4 / next** | Pure sprite compositions with measurable bounds and a reusable canvas transform. Slay and Three Islands composite bosses; Sitar translated canvas and Flappy manual shake/layout offsets. | Share origin/flip/scale/translation/alpha mechanics, retaining recipes, choreography and rules in mods. Layout must work without drawing; nested transforms and clipping must be deterministic. `SpriteAnchors`, fonts and primitives already exist. Repair R4 before generalizing. |
| **U5 / incremental** | Adopt/extend verified `StockSceneArt` recipes and existing scene level kits. Eggman's Sky, Slay, Three Islands, Flappy, Starfall, Sitar and Tower reuse stock art banks. | Move repeated request facts into the existing ROM-qualified catalogue; retain palette/art choices, stats and motion locally. Compare recipe frames/origins/palettes to raw requests on exact supplied ROMs. Starfall's custom Kosinski/block intake should first be compared with `SceneRomArt.levelKit`; add only proven missing block-identity/variant access, not another level-kit API. |
| **U6 / next** | Test-ready SDK starters and reproducible example forks; central launcher adoption. R10's seven omitted local suites, Three Islands generator, all generated starters; Infinite/Survivors duplicated compile/package launchers. | Ship tests/source tools as source, never in main mod jars. Use real Jupiter discovery and matching SDK/engine/testkit metadata. Preserve existing launcher output/run semantics during consolidation; portable kit acceptance must require no source checkout. |
| **U7 / later** | Character factory identity/render convenience and declarative owned object registries. Character, standalone and Bolt samples repeatedly strip `_p\d+` from runtime code and repeat the same playable draw forwarding; standalone/platformer registries repeat factory/key/browse dispatch. | Prefer the engine's already-bound canonical identity rather than teaching authors suffix syntax. Keep owner checks authoritative, factories fresh, callback boundaries intact and expert overrides possible. `StandaloneGameSpec` already supplies ordinary providers; do not recreate it. Test main/sidekick/respawn/rewind and unknown/foreign object keys. Baked ring materialization has only one clear consumer here: a convenience candidate after broader demand. |

Further hard author workflows were considered but need a separate bounded design:

- **Cancellable art preparation:** Eggman's Sky and Three Islands each implement loading
  screens around synchronous art/level generation. A tick budget cannot preempt an
  expensive individual step, and scene ROM art/level kits are not thread-safe. Consider
  host-owned preparation with immutable results, cancellation/progress and budgets;
  preserve creator procedural logic. Cancellable music preparation already exists.
- **Gameplay-session transport:** Golf owns sockets, DNS, virtual workers, bounded queues,
  deadlines and teardown. Sitar correctly uses existing scene-visit `SceneNetwork`; it
  cannot directly replace Golf's binary latest-presentation/reliable-control transport.
  Session-owned lifetime/queues are a potential extension, with Golf as the primary
  demonstrated consumer. Keep golf's room/turn/fingerprint protocol local and qualify
  cancellation/backpressure/disconnect before sharing. Do not build a generic multiplayer
  framework from this audit.
- **Auxiliary object spawning:** Survivors relies on internal construction and auxiliary
  dynamic-object admission outside the native object-slot table. Existing `spawnFreeChild`
  has native-slot semantics. Consider a verified creator spawn capability only after
  documenting capacity, owner fault, service injection, destruction and recreation;
  this audit has one clear outside-slot consumer.
- **Title-card choreography and font options:** Flappy, Sitar and Slay repeat native title
  component arrival/hold/exit layouts. A small presentation value/helper may be useful.
  Three Islands keeps a mixed-case atlas parser because current `AtlasFont` uppercases
  glyphs; an opt-in case policy is cheaper than a new font system. Slay's disabled-but-
  inspectable hotspots differ deliberately from `FocusRegions`' disabled focus policy.
  Preserve those UX choices and demonstrate a second focus-policy consumer before uplift.

Existing APIs worth adopting include `ActionMap`/`ActionReducer`, `TextLayout`,
`PaletteAssembly`, `SpriteAnchors`, `SceneArtCache`, `PhysicsProvider.transform`,
`DelegatingLevel`/`DelegatingZoneRegistry`, `CapturedPool`, `GameServiceBundle`,
`LevelPatch`, `StandaloneGameSpec`, and `ModRewindRecreatable`. Their existence does not
imply every migration is behavior-preserving: RNG algorithms/bound consumption, native
ordinary versus raw animation dialects, focus policy, mixed-case fonts and intentional
room-entry saves must retain their contracts. Explicitly reject blanket replacement of
mod RNGs with `SnapshotRandom` or Slay's raw animation dialect with `RomAnimationPlayer`.

Local code-quality work should serve the findings: extract immutable combat layout from
Slay's large CombatView, split generation/cache ownership from Eggman's Sky's large
SurfaceMode, and cache Tower's repeated wave preview preparation. Art facades that catch
arbitrary runtime errors and permanently cache null need bounded diagnostic status so
missing optional ROMs can be distinguished from invalid requests. None of these static
observations establishes an FPS, latency or memory improvement.

## Documentation corrections delivered here

- Six example installation guides now use the title's Apply/reload flow landed in
  `f9d4e0474`, including renewed hash trust for Golf updates; no JVM restart is prescribed
  as the normal install/update step.
- Survivors' README names `saves/mods/sonic-survivors/profile.txt`, with eligible legacy
  reads explained. Infinite's legacy leaderboard path is intentionally still described
  because its migration is proposed, not implemented.
- Slay's tour no longer asserts a fixed 20-type/two-package surface or a blanket enum
  prohibition. It links the existing helper contracts and records the remaining
  draw-independent targeting correction instead of claiming that all drawing is pure.
- The handbook/backlog link this audit as a proposed author-workflow triage record.
  No runtime, manifest, API descriptor, signature pin, selection policy or executable
  build contract changes are made by this delivery.

## Validation and next delivery slices

Executed checks used the frozen `f9d4e0474` runtime/model sources. Java 21.0.12.1 was
observed. Actual-source standalone probes establish the stated model behavior; they
are not a Jupiter suite or a ROM/session qualification. The map generator reproduced
committed Three Islands maps byte-for-byte. The real creator exporter was called in
memory to inspect source/test/tool selection. Static manifest/import inventories and
handbook relative-link checks were also performed. Exact probe inputs and final
documentation checks follow.

The parent recompiled and reran all four small probes against the actual sources. Their
temporary input files and class directories were outside the repo; they are removed
after their outcomes are recorded. To reconstruct their few-line inputs, use:

| Probe input | Production calls / precondition | Observed outcome |
|---|---|---|
| `EggSkySaveProbe.java`, default package | Three fresh `new Player(new Catalog())` values; decode arbitrary prose, `version=999\nrings=1234\n`, and `version=1\nbonusSlots=1000000\n`, respectively; inspect `encode`, `rings`, `cargo.slots()`. | Prose and future format encode as version 1; future rings become 1234; unchecked capacity is 1,000,016. |
| `starfall/BeaconProbe.java`, package `starfall` | For every `WorldSize`, generate `new World(73, size)`, set the completed-quest precondition `wardens=7`, add one `Content.Item.BEACON`, and interact at `(40, surface(40)-2)`. Then move the player to `(40*T+6, 28*T)` and interact at `(40,27)`. | Reachable real camp returns false with `won=false`; legacy point returns true with `won=true` for all three sizes. The probe asserts the defect, so exit zero is not a gameplay pass. |
| `flappytails/RecordsProbe.java`, package `flappytails` | In-memory `SceneStorage` map with successful writes, seeded `Records.FILE` with `formatVersion=9\nbest.CLASSIC=999\n`; call actual `Records.load(storage).save(storage)` and compare original bytes. | `futurePreserved=false`; replaced by v1 zero records. Only storage is a fake; all record/helper logic is production source. |
| `sitarhero/model/CareerReadProbe.java`, package `sitarhero.model` | Fresh `CareerJournal`; read `sitar-career=9\nclear=green-hill\n`, then encode. | Returns empty `sitar-career=1\n`. This is model evidence; the scene-exit publication consequence remains static. |

Exact parent commands, with `AUDIT_PROBES` denoting the task-owned temporary input/output
directory outside the repository used during the audit:

```sh
javac --release 21 -sourcepath examples/eggmans-sky/src/main/java:src/main/java \
  -d "$AUDIT_PROBES/root-classes" "$AUDIT_PROBES/EggSkySaveProbe.java"
java -Xmx64m -cp "$AUDIT_PROBES/root-classes" EggSkySaveProbe

javac --release 21 -d "$AUDIT_PROBES/root-starfall" \
  examples/starfall-frontier/src/main/java/starfall/World.java \
  examples/starfall-frontier/src/main/java/starfall/Content.java \
  examples/starfall-frontier/src/main/java/starfall/Biome.java \
  examples/starfall-frontier/src/main/java/starfall/EnemyType.java \
  examples/starfall-frontier/src/main/java/starfall/WorldSize.java \
  "$AUDIT_PROBES/starfall/BeaconProbe.java"
java -cp "$AUDIT_PROBES/root-starfall" starfall.BeaconProbe

javac --release 21 -d "$AUDIT_PROBES/root-flappy" \
  -sourcepath src/main/java:examples/flappy-tails/src/main/java \
  "$AUDIT_PROBES/flappytails/RecordsProbe.java"
java -cp "$AUDIT_PROBES/root-flappy" flappytails.RecordsProbe

javac --release 21 -d "$AUDIT_PROBES/root-sitar" \
  examples/sitar-hero/src/main/java/sitarhero/model/*.java \
  examples/sitar-hero/src/main/java/sitarhero/catalogue/*.java \
  examples/sitar-hero/src/test/java/sitarhero/model/*.java \
  "$AUDIT_PROBES/sitarhero/model/CareerReadProbe.java"
java -cp "$AUDIT_PROBES/root-sitar" sitarhero.model.CareerReadProbe
java -cp "$AUDIT_PROBES/root-sitar" sitarhero.model.WorldTourChecks
java -cp "$AUDIT_PROBES/root-sitar" sitarhero.model.RhythmChecks

python3 examples/three-islands/tools/generate_maps.py "$AUDIT_PROBES/Maps-root-check.java"
cmp examples/three-islands/src/main/java/threeislands/field/Maps.java "$AUDIT_PROBES/Maps-root-check.java"
```

All parent commands exited zero with the outcomes above. The two existing standalone
Sitar `main` probes completed nine authored world-tour and ten rhythm checks; they were
not run through Jupiter discovery and are not a claimed Sitar suite pass. An agent's
separate attempted Flappy practice-scene source-only compile lacked the engine's
third-party classpath and was terminated (exit 143); it contributes no scene validation.
The practice finding remains static. Tutorial byte comparisons matched 1/2/3/6 shared
classes for checkpoints 2/3/4/5; copied teaching snapshots are not independent API
consumers.

No engine full suite, category suite, trace fixture, graphics capture, audio-timing
qualification or persistence-failure session was run. This is a prose-only delivery;
repository policy permits link, syntax, mirror, policy and behavioral-scenario checks
without repeating engine suites. Existing integration tests are future repair targets,
not claimed passes in this audit.

The unchanged planner was inspected with
`python3 tools/testing/run_categories.py --base f9d4e04747a6474c81cc9c9424951fecf3de191b`.
It selected all **3,080 ordinary source classes plus fresh guards** because example
README paths are unclassified. A normal broad run costs approximately 24 minutes
ordinary plus 10 minutes guards; it was not started for these prose-only edits.
No selection rule was narrowed. The temporary documentation checker ran as:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 "$AUDIT_PROBES/verify_mod_audit.py"
git diff --check
cmp AGENTS.md CLAUDE.md
```

It checked all handbook Markdown plus the changed READMEs/audit: **55 files, 425
relative link targets**, balanced fences and the linked audit heading; all passed.
It also verified the 25 manifest ranges, 474-file/82,977-line source inventory,
unchanged instruction mirrors, prose-only diff, and the actual exporter selection
(12 projects, 60 authored Java test files, one exported, missing Three Islands
generator). The source-range policy hooks additionally run at commit/integration/push.

Suggested delivery order:

1. Repair R1/R2 save preservation and outcomes with failure-injection/model regressions;
   then extract U1 from the repaired real consumers.
2. Repair R3/R4 generated camp and render-independent targeting. Add ordinary-input HOLD
   semantics for R6/U2 with matched stock and Golf regressions; reproduce R5 settlement.
3. Repair generation-scoped caches and R8's practice/calibration policies; adopt existing
   storage/art/action helpers before expanding their APIs.
4. Deliver U6's test/tool-preserving creator forks and maintain a visible internal-
   dependency ledger. Extend U3/U4/U5 only with migrated consumers and behavior evidence.

Each executable slice uses the actual integration-base change plan and relevant mod,
owner-fault, API-signature, rewind/session, ROM-identity and route checks. A passed model
probe or packaged jar alone cannot certify gameplay, graphics, audio or API publication.
