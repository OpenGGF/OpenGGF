# Choose a mod-authoring task

Start with [setup and your first project](getting-started.md). The unpublished
0.7.0 candidate is mutable: use engine, SDK and (for tests) testkit from the same
source commit. Matching version labels or filenames are insufficient; follow the
[artifact identity and test setup](testing.md). Validate the exact jar you will
install. The recipes below are starting points, not new API promises.

| Request | Smallest supported starting point |
|---|---|
| [Put a badnik in a stock act](#badnik-in-a-stock-act) | Owned factory plus a decoded placement binding; true additions need expert assembly |
| [Change one character's movement](#one-characters-movement) | Scoped immutable physics transform |
| [Hold gameplay for a menu](#hold-gameplay-for-a-menu) | Captured frame controller with HOLD/native admission |
| [Keep an upgrade between sessions](#persistent-upgrade) | Owner storage plus a validated save slot |
| [Reuse a parameterized native enemy](#parameterized-native-enemy) | Supported factory and finite recipe |
| [Build an original act or campaign](#original-act-or-campaign) | Editor export; code runtime only when needed |

Read one recipe and its linked source first. Keep expert Java/provider overrides
when the small helper cannot express the requested behavior. Explain the extra
contract being assumed; never invent a hook or treat an internal class as public
because it happens to be on the engine classpath.

## Badnik in a stock act

**Own the factory; let the stock loader own the act.** Register a local key with
[ModContext](../../src/main/java/com/openggf/mods/code/ModContext.java).registerObject, then use `decodedLevelPatch` with
[LevelPatch](../../src/main/java/com/openggf/level/LevelPatch.java).empty().nativePlacements().forZone(...).select(...).bind(localKey).
This replaces selected native placements with your verified owner's factory while
retaining the stock decoded level. Gate the original inventory where acts share a
zone; `forZone` alone does not select an act. See only `register` and
`nativeOpening` in [SecondStrikeMod](../../examples/second-strike/src/main/java/secondstrike/SecondStrikeMod.java).

For an original enemy, start from [SampleBadnik](../../src/test/resources/mods/sample-mod-src/project/src/main/java/example/phase2sample/SampleBadnik.java)
and the [object quickstart](quickstarts/object.md). Use injected `services()` after
construction, owned baked art and public movement/destruction helpers. Keep mutable
state on the instance, supply `ModRewindRecreatable`, and verify that custom fields
and relationships restore through the actual object snapshot path. Never use
static gameplay state or allocate a stock numeric object id for a mod factory.

**Limit:** `nativePlacements()` admits factory bindings and original-ring retention,
not appended, moved or arbitrary rewritten native objects. If “add” means an extra
slot rather than replacing one, the binding recipe is insufficient. Expert
[GameModule](../../src/main/java/com/openggf/game/GameModule.java).loadLevelOverride / transformDecodedLevel and
[MutableLevel](../../src/main/java/com/openggf/level/MutableLevel.java).snapshot / addObjectSpawn remain available for deliberate level
assembly, with unique stable placement identities and owner/key pairs. Such a
snapshot is not a guarantee of native zone scripts or every concrete-level service;
verify host events, camera, water, collision and load/respawn before claiming support.
Do not disguise a replacement as an addition.

**Verify:** use [TestNativeDecodedPlacementPatchHost](../../src/test/java/com/openggf/mods/code/TestNativeDecodedPlacementPatchHost.java) and
[TestNativeDecodedPlacementPatchRom](../../src/test/java/com/openggf/level/TestNativeDecodedPlacementPatchRom.java) as boundary references; add your own packed-mod
registration, selected/unselected act, collision/defeat, respawn and full-session
capture/restore/replay checks. Test the target ROM with an absolute path. A factory
registration pass alone proves neither placement nor a playable encounter.

## One character's movement

**Edit the existing provider, not every character or the game loop.** A registered
[GamePatch](../../src/main/java/com/openggf/game/patch/GamePatch.java) can return a [DelegatingGameModule](../../src/main/java/com/openggf/game/patch/DelegatingGameModule.java) overriding `getPhysicsProvider()`
with `base.getPhysicsProvider().transform("sonic"::equals, p -> p.withMax(0x0C00))`.
[PhysicsProvider](../../src/main/java/com/openggf/game/PhysicsProvider.java).transform preserves source modifiers/rules, edits matching normal
and optional initialization profiles, and leaves other characters untouched.
[PhysicsProfile](../../src/main/java/com/openggf/game/PhysicsProfile.java).toBuilder() supports wider immutable edits; speeds use fixed-point
units. Keep launch scope in the patch's `activatesFor` and preserve composition by
forwarding through the supplied base.

**State:** a constant profile needs no extra mutable rewind state. If an earned
upgrade controls the choice, its session state needs an owned captured service;
its durable purchase record belongs in storage (next recipe). Verify initialization,
death reload, water and speed-shoes changes, including S3K's separate init profile.

**Verify:** [TestPhysicsProfileEditing](../../src/test/java/com/openggf/game/TestPhysicsProfileEditing.java) demonstrates preservation and character
scoping; add a session test proving the intended character changes while another
does not, then capture/restore and replay. These value tests alone do not prove
in-level movement. For a new character or custom ability, use the
[character quickstart](quickstarts/character.md) and [character contracts](characters.md)
(`CharacterPhysicsSpec`, sensors and supported ability hooks). A physics transform
is not a general player-state-machine replacement; expert providers remain available.

## Hold gameplay for a menu

**Keep the level session; control admission.** Register a per-application
[GameServiceBundle](../../src/main/java/com/openggf/game/GameServiceBundle.java) using `ModContext.registerServiceBundle` and
`builder().frameController(localKey, controller)`. That helper registers the same
[GameplayFrameController](../../src/main/java/com/openggf/game/mode/GameplayFrameController.java) instance as both controller and rewind adapter.
In `beforeFrame`, return `GameplayFrameDisposition.HOLD` while the menu is open and
`ADVANCE_NATIVE_INPUT` when gameplay resumes. `ADVANCE_CONTROLLED` instead consumes
`nativeInput`, which is neutral by default. The legacy `beforeTick(true)` maps to
controlled input, so it is not a substitute for native admission.

**State/lifecycle:** capture menu mode, selection, edge latches and any scheduling
state that affects future simulation. Create a fresh graph per application; close
owned resources at session teardown. Use `drawOverlay` for presentation and
`presentationPaused()` for an audio hold when appropriate. HOLD freezes world
physics, objects, clocks, animation and ordinary PLC work; host fades, title-card
exit, focus/pause and return handling retain their own rules. Read the
[controlled-level contract](content-mods.md#controlled-level-modes-and-course-views)
when these boundaries matter. Follow [UI design guidance](../../.agents/skills/ui-design-language/SKILL.md)
for overlays. A full-screen independent game can use a scene instead; no frame
controller is required for arbitrary original scene content.

**Minimal source:** the bundle setup in
[TypingHedgehogMod](../../examples/typing-of-the-hedgehog/src/main/java/typinghog/TypingHedgehogMod.java)
shows shared ownership; read the controller interface before its larger game.
**Verify:** [TestOwnerFrameDisposition](../../src/test/java/com/openggf/mods/code/TestOwnerFrameDisposition.java) covers owner dispatch and legacy mapping;
[TestControlledFrameRuntime](../../src/test/java/com/openggf/game/mode/TestControlledFrameRuntime.java) covers runtime admission. Add menu open/held/close
input tests, unchanged world state during HOLD, native input after closing, and
full-session rewind/replay across the menu. Test audio and pixels separately;
module-state snapshots alone do not cover them. Expert module controllers and
`rewindAdapters()` remain available, but must share the same stable state object.

## Persistent upgrade

**Separate durable settlement from reversible gameplay.** Obtain [ModStorage](../../src/main/java/com/openggf/mods/ModStorage.java) via
`ModContext.storage()` (or a scene's compatible storage), retain the handle, and
keep a [ModSaveSlot](../../src/main/java/com/openggf/mods/state/ModSaveSlot.java) with a mod-owned [ModSaveCodec](../../src/main/java/com/openggf/mods/state/ModSaveCodec.java) on the owning module/scene.
Load once at the appropriate lifecycle boundary; decode a versioned format with
VALID/CORRUPT/INCOMPATIBLE outcomes. Save only an explicitly settled purchase or
reward. Storage authority comes from the host, not an owner id or arbitrary path.

Check `saved()` before reporting success. On failure, `pending()` / `retry()` retain
the encoded checkpoint: retry publication, not the purchase/reward mutation.
Protect unreadable or future-version data; call `allowReplacement()` only after an
explicit player reset/replacement choice. See the focused
[save-slot contract](guides/creator-helpers.md#captured-state-and-persistent-settings)
and [Three Islands SaveCodec](../../examples/three-islands/src/main/java/threeislands/core/SaveCodec.java)
for one real versioned format, rather than copying a whole game.

**State:** capture temporary upgrade effects and reversible run state through
[GameServiceBundle](../../src/main/java/com/openggf/game/GameServiceBundle.java).capturedService. Keep a non-rewound settlement ledger when
rewinding must not award the same durable upgrade twice; define stable reward
identity and the reset policy in the mod. Disk I/O is not reversed by gameplay
rewind. Atomic individual file writes do not make the primary/backup sequence or
separate gameplay mutations a multi-file transaction.

**Verify:** [TestModSaveSlot](../../src/test/java/com/openggf/mods/state/TestModSaveSlot.java) covers storage failure/recovery boundaries. Add the
upgrade's own fresh start, restart/load, corrupt/future file, failed-write retry,
and rewind-before/after-settlement tests. Assert no duplicate debit/reward and
truthful UI status. Save helpers do not implement upgrade rules or provide a
global stock-save extension; direct bounded `ModStorage` with your own codec is
an expert option if you own equivalent failure handling.

## Parameterized native enemy

**Use a supported facade and its admitted parameters.** For S3K Caterkiller Jr,
register an owned factory returning [CaterkillerJrFactory](../../src/main/java/com/openggf/mods/objects/CaterkillerJrFactory.java).create(spawn,
CaterkillerJrRecipe.STOCK or DOUBLE_LENGTH). The [CaterkillerJrRecipe](../../src/main/java/com/openggf/game/sonic3k/objects/badniks/CaterkillerJrRecipe.java) enum is a
bounded body-graph choice; it does not accept arbitrary lengths, movement speeds,
renderer keys or projectile timings. The facade requires the donor's Caterkiller
Jr and Monkey Dude art (normally AIZ). It does not make the enemy portable to every
host/zone without the corresponding assets/services.

**Minimal source:** [SecondStrikeCaterkiller.register](../../examples/second-strike/src/main/java/secondstrike/enemies/SecondStrikeCaterkiller.java)
is the complete owned factory. Bind an existing placement as in the first recipe
or reference the namespaced object in an authored level. Native code owns AI,
child allocation, touch behavior and reconstruction of the linked graph; retain
that ownership rather than cloning internal children or subclassing native AI.

**Verify:** [TestSecondStrikeCaterkillerModRewind](../../src/test/java/com/openggf/mods/code/TestSecondStrikeCaterkillerModRewind.java) checks the owned native graph;
add encounter tests for the chosen recipe, child retirement, re-entry and
capture/restore/forward replay in the claimed donor/viewport/team. A longer body
is not proof that every route is completable. For parameters outside the facade,
choose original object behavior or explicitly extend the supported API with ROM
research and regressions. Native behavior research routes to the matching game
object/boss and disassembly skills; this is not a prerequisite for original scene rules.

## Original act or campaign

**Choose data before custom runtime code.** For an ordered original hosted route,
use the editor's **Campaign levels → Export playable campaign** workflow in the
[two-act guide](guides/two-act-campaign.md). Place `openggf:authored-goal` in each act;
the editor does not infer a goal from the terrain edge. Data-only campaigns share
one host and required team and finish at credits. Native stock zone scripts are
not inherited merely by choosing a host.

For custom runtime events or stock-campaign insertion, read only `register` in
[TideCircuitMod](../../src/test/resources/mods/sample-two-act-campaign-src/project/src/main/java/example/tide/TideCircuitMod.java):
[ModZoneContribution](../../src/main/java/com/openggf/mods/code/ModZoneContribution.java).multiAct holds ordered exports; `withRuntime` creates
per-act [ModZoneRuntimeServices](../../src/main/java/com/openggf/game/modzone/ModZoneRuntimeServices.java). Keep destination keys stable in saves, not
allocated numeric zone ids. Recreate handlers on load; serialize mutable runtime
state through `ZoneRuntimeState` and events through `RewindableZoneEvents`.
Use host-supplied act/context metadata, and retire old adapters at replacement.
The original finish gate demonstrates `ObjectServices.advanceToNextLevel()` after
its captured tally; do not assume later-act stock signposts always survive.

**Verify:** [TestTwoActModCampaign](../../src/test/java/com/openggf/mods/code/TestTwoActModCampaign.java) exercises the production path with supplied S2
ROM, tagged saves, checkpoint reload and physical progression. Give each act and
supported character/team/viewport/donor route its own obligations under the
[level testing standard](../guide/contributing/level-test-standard.md), including
short independent goal checks, load/respawn, rewind and forward replay. See the
[Tide Circuit matrix](../architecture/validation/levels/tide-circuit.md) for explicit
remaining breadth. Registration, a load, or positioned checks alone do not certify
a cold complete campaign. For a no-ROM game, use the
[standalone quickstart](quickstarts/standalone.md) and [StandaloneGameSpec](../../src/main/java/com/openggf/game/StandaloneGameSpec.java);
expert `AbstractStandaloneGameModule`/providers remain available. Original authored
content does not require disassembly unless it claims native behavior parity.

## Focused verification and evidence

Use the [production-backed testing guide](testing.md) to package/load the exact
artifact under test and inspect findings/disabled owners. In a repository checkout,
run only relevant checks through the Maven queue, for example:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestPhysicsProfileEditing test
```

Select tests for the changed behavior; the linked classes are maintained reference
coverage, not a mandate to run every test for every task. ROM-backed checks need
absolute paths to the original required ROMs and their actual test property (for
example `-Dsonic2.rom.path=/absolute/original.gen`). Inspect skips: a missing ROM can
produce a successful command with no gameplay coverage. Use the repository's
headless/offscreen workflow for visuals. Report command, source/artifact identity,
executed tests, skips, limits and remaining route gaps. No productivity measurement
or fresh runtime test pass is implied by this guide.
