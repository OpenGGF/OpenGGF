package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.*;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.competition.Sonic2CompetitionObjectStreaming;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.graphics.GLCommand;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independent source-contract cases for loc_1B720, not S2 cursor comparisons. */
class TestNativeCompetitionUpfrontPlacement {
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() { SessionManager.clear(); }

    private static final class Actor extends AbstractObjectInstance implements RewindRecreatable {
        int ticks;
        Actor(ObjectSpawn spawn) { super(spawn, "native-upfront-test"); }
        @Override public void update(int frame, PlayableEntity player) { ticks++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isPersistent() { return getSpawn().objectId() == 1; }
        @Override public boolean usesCustomOutOfRangeCheck() { return getSpawn().objectId() == 3; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return getSpawn().subtype() == 1; }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new Actor(context.spawn());
        }
    }

    private static final class Fixture {
        final Camera[] cameras = {new Camera(SonicConfigurationService.getInstance()),
                new Camera(SonicConfigurationService.getInstance())};
        final ObjectManager manager;
        final List<ObjectSpawn> created = new ArrayList<>();
        Consumer<ObjectSpawn> onCreate = ignored -> { };
        Fixture(List<ObjectSpawn> spawns) { this(spawns, true); }
        Fixture(List<ObjectSpawn> spawns, boolean explicitRoster) { this(spawns, explicitRoster, explicitRoster); }
        Fixture(List<ObjectSpawn> spawns, boolean explicitRoster, boolean bindUpfront) {
            NativeCompetitionRoster roster = mock(NativeCompetitionRoster.class);
            when(roster.camera(0)).thenReturn(cameras[0]);
            when(roster.camera(1)).thenReturn(cameras[1]);
            GameModule module = mock(GameModule.class);
            WorldSessionPolicyState policies = mock(WorldSessionPolicyState.class);
            if (explicitRoster) when(policies.getService(NativeCompetitionRoster.class)).thenReturn(roster);
            when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policies);
            WorldSession world = new WorldSession(module);
            ObjectRegistry registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) {
                    created.add(spawn);
                    onCreate.accept(spawn);
                    return new Actor(spawn);
                }
                @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
                @Override public String getPrimaryName(int id) { return "native-upfront-test"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
            };
            manager = new ObjectManager(spawns, registry, -1, null, null, null, cameras[0],
                    new StubObjectServices() {
                        @Override public Camera camera() { return cameras[0]; }
                        @Override public WorldSession worldSession() { return world; }
                    });
            manager.enableExecThenLoadPlacement();
            if (bindUpfront) ObjectManagerInternalAccess.enableNativeCompetitionUpfrontPlacement(manager, 0x800);
        }
        void at(int firstX, int secondX, int firstY, int secondY) {
            cameras[0].setX((short) firstX); cameras[1].setX((short) secondX);
            cameras[0].setY((short) firstY); cameras[1].setY((short) secondY);
            manager.update(firstX, null, List.of(), 0, false);
            manager.postCameraPlacementUpdate(firstX);
        }
        List<Actor> actors() {
            return manager.getActiveObjects().stream().map(o -> (Actor) o)
                    .sorted(Comparator.comparingInt(Actor::getSlotIndex)).toList();
        }
        List<String> graph() {
            return actors().stream().map(a -> a.getSlotIndex() + ":" + a.getSpawn().layoutIndex()
                    + ":" + a.getRespawnStateIndex() + ":" + a.getX() + ":" + a.ticks).toList();
        }
        RewindRegistry worldSnapshots() {
            RewindRegistry snapshots = new RewindRegistry();
            snapshots.registerOrRefresh(manager.rewindSnapshottable());
            snapshots.registerOrRefresh(ObjectManagerInternalAccess.nativeCompetitionUpfrontPlacementRewindAdapter(manager));
            snapshots.registerOrRefresh(cameras[0]);
            snapshots.registerOrRefresh(new RewindSnapshottable<CameraSnapshot>() {
                @Override public String key() { return "upfront_test_p2_camera"; }
                @Override public CameraSnapshot capture() { return cameras[1].capture(); }
                @Override public void restore(CameraSnapshot state) { cameras[1].restore(state); }
            });
            return snapshots;
        }
    }

    private static ObjectSpawn spawn(int x, int index) {
        return new ObjectSpawn(x, 0xF00, 1, 0, 3, false, 0x6F00, index);
    }
    private static List<ObjectSpawn> records(int count) {
        List<ObjectSpawn> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(spawn(0x100 + i, i));
        return result;
    }
    private static void assertWorldEquals(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        expected.entries().forEach((key, value) -> assertTrue(
                RewindSnapshotDiff.keyEquals(key, value, actual.get(key)), "world key " + key));
    }

    @Test void resetLoadsSourceOrderDuplicatesAndExactUnsignedCutoffWithoutYOrCameraFilters() {
        ObjectSpawn first = spawn(0x7FF, 0);
        ObjectSpawn second = spawn(0x120, 1);
        ObjectSpawn duplicate = spawn(0x120, 2);
        ObjectSpawn cutoff = spawn(0x800, 3);
        ObjectSpawn afterCutoff = spawn(0x20, 4);
        Fixture f = new Fixture(List.of(first, second, duplicate, cutoff, afterCutoff));
        f.cameras[0].setX((short) 0x4000); f.cameras[1].setX((short) 0x7000);
        f.manager.reset(0x4000);
        assertEquals(List.of(first, second, duplicate), f.created,
                "Stop at the first source X>=800, even if a later record has lower X");
        assertEquals(List.of(4, 5, 6), f.actors().stream().map(Actor::getSlotIndex).toList());
        assertEquals(7, f.manager.firstFreeDynamicSlot(), "The final forward probe consumes no slot");
        assertEquals(List.of(0xF00, 0xF00, 0xF00), f.actors().stream().map(Actor::getY).toList());
        assertEquals(List.of(3, 3, 3), f.actors().stream().map(a -> a.getSpawn().renderFlags()).toList());
        assertEquals(-1, ObjectManagerInternalAccess.nativeCompetitionGeneralAllocationSlotLimitExclusive(f.manager));
    }

    @Test void usesAllNinetyNativeSlotsAndDoesNotLatchTheFirstUnloadedRecord() {
        Fixture f = new Fixture(records(92));
        f.manager.reset(0);
        assertEquals(90, f.created.size());
        assertEquals(4, f.actors().getFirst().getSlotIndex());
        assertEquals(93, f.actors().getLast().getSlotIndex());
        var state = f.manager.rewindSnapshottable().capture().placement();
        for (byte value : state.objState()) assertEquals(0, value, "No per-placement bset in sub_1BA0C");
        f.actors().getFirst().setDestroyed(true);
        f.at(0, 0, 0, 0);
        f.at(0x5000, 0x1000, 0xF00, 0x800);
        assertEquals(90, f.created.size(), "Freed RAM and camera movement cannot resume the terminal routine");
        assertEquals(89, f.actors().size());
    }

    @Test void initialFullRamCompletesWithoutRetryAndResetIsTheOnlyNewLoad() {
        Fixture f = new Fixture(records(3));
        f.manager.resetForPreludeHydration(0);
        List<Actor> fillers = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            Actor actor = new Actor(spawn(0x100, 1000 + i));
            f.manager.addDynamicObject(actor); fillers.add(actor);
        }
        f.manager.preloadInitialSpawnsForHydration();
        assertTrue(f.created.isEmpty());
        f.manager.removeDynamicObject(fillers.getFirst());
        f.manager.preloadInitialSpawnsForHydration();
        f.at(0x1000, 0x6000, 0, 0x1000);
        assertTrue(f.created.isEmpty());
        f.manager.reset(0x5000);
        assertEquals(records(3), f.created);
        assertEquals(List.of(4, 5, 6), f.actors().stream().map(Actor::getSlotIndex).toList());
    }

    @Test void explicitSpawnResyncReinitializesFromReplacementSourceAndKeepsScratchSeparate() {
        Fixture f = new Fixture(records(1));
        f.manager.reset(0);
        List<ObjectSpawn> replacement = records(300);
        f.manager.resyncSpawnList(replacement);
        f.manager.preloadInitialSpawnsForHydration();
        assertEquals(90, f.actors().size());
        assertEquals(replacement.subList(0, 90), f.actors().stream().map(Actor::getSpawn).toList());
        assertEquals(300, f.actors().getFirst().getRespawnStateIndex());
        int before = f.created.size();
        f.manager.preloadInitialSpawnsForHydration();
        assertEquals(before, f.created.size(), "Replacement source still gets only one pass");
        Fixture beforeInitialLoad = new Fixture(records(1));
        beforeInitialLoad.manager.resetForPreludeHydration(0);
        beforeInitialLoad.manager.resyncSpawnList(List.of(spawn(0x7FF, 4)));
        beforeInitialLoad.manager.preloadInitialSpawnsForHydration();
        assertEquals(List.of(spawn(0x7FF, 4)), beforeInitialLoad.created);
        Fixture retainedDynamic = new Fixture(records(1));
        retainedDynamic.manager.reset(0);
        Actor dynamic = new Actor(spawn(0x100, 999));
        retainedDynamic.manager.addDynamicObject(dynamic); // slot 5 remains live across editor resync
        retainedDynamic.manager.resyncSpawnList(records(2));
        retainedDynamic.manager.preloadInitialSpawnsForHydration();
        assertEquals(List.of(4, 5, 6), retainedDynamic.actors().stream().map(Actor::getSlotIndex).toList());
        assertSame(dynamic, retainedDynamic.actors().get(1));

    }

    @Test void forwardScanNeverReusesAnEarlierSlotFreedDuringConstruction() {
        Fixture f = new Fixture(records(4));
        f.manager.resetForPreludeHydration(0);
        Actor blocker = new Actor(spawn(0x100, 99));
        f.manager.addDynamicObject(blocker); // slot 4
        assertTrue(f.manager.reserveDynamicSlot(6));
        f.onCreate = spawn -> {
            if (spawn.layoutIndex() == 0) f.manager.removeDynamicObject(blocker);
        };
        f.manager.preloadInitialSpawnsForHydration();
        assertEquals(List.of(5, 7, 8, 9), f.actors().stream().map(Actor::getSlotIndex).toList());
        assertEquals(4, f.manager.firstFreeDynamicSlot());
    }

    @Test void sharedSeparateScratchStateHasNoLoadLatchAndRestoresWithoutEightBitTruncation() {
        Fixture f = new Fixture(records(300));
        f.manager.reset(0);
        Actor first = f.actors().getFirst();
        Actor second = f.actors().get(1);
        int scratch = first.getRespawnStateIndex();
        assertEquals(300, scratch, "Scratch address cannot alias any ordinary per-entry respawn byte");
        assertEquals(scratch, second.getRespawnStateIndex());
        f.manager.setSpawnStateBit(first.getSpawn(), 2);
        f.manager.setSpawnStateBit(second.getSpawn(), 7);
        assertTrue(f.manager.isSpawnStateBitSet(first.getSpawn(), 7));
        assertTrue(f.manager.isSpawnStateBitSet(second.getSpawn(), 2));
        var snapshot = f.manager.rewindSnapshottable().capture();
        for (int i = 0; i < scratch; i++) assertEquals(0, snapshot.placement().objState()[i]);
        first.setDestroyed(true); // direct Delete_Current_Sprite does not clear scratch
        f.at(0, 0, 0, 0);
        assertTrue(f.manager.isSpawnStateBitSet(second.getSpawn(), 7));
        f.manager.clearSpawnCounterActiveBit(second.getSpawn());
        assertFalse(f.manager.isSpawnStateBitSet(second.getSpawn(), 7));
        f.manager.rewindSnapshottable().restore(snapshot);
        assertEquals(scratch, f.manager.getSpawnCounter(second.getSpawn()));
        assertTrue(f.manager.isSpawnStateBitSet(second.getSpawn(), 7));
        assertTrue(f.manager.isSpawnStateBitSet(second.getSpawn(), 2));
        int beforeReset = f.created.size();
        f.manager.reset(0);
        assertEquals(beforeReset + 90, f.created.size(), "Upfront ignores even a previously set shared scratch latch");
        assertTrue(f.manager.isSpawnStateBitSet(second.getSpawn(), 2), "Common init does not wipe _unkF712");
    }

    @Test void standardCullUsesFixedCoarseZeroAndCustomChecksKeepTheirOwnAuthority() {
        ObjectSpawn edge = new ObjectSpawn(0x2FF, 0x100, 2, 0, 0, false, 0x100, 0);
        ObjectSpawn outside = new ObjectSpawn(0x300, 0x100, 2, 0, 0, false, 0x100, 1);
        ObjectSpawn customKeep = new ObjectSpawn(0x700, 0x100, 3, 0, 0, false, 0x100, 2);
        ObjectSpawn customDelete = new ObjectSpawn(0x100, 0x100, 3, 1, 0, false, 0x100, 3);
        Fixture f = new Fixture(List.of(edge, outside, customKeep, customDelete));
        f.manager.reset(0x300);
        f.manager.setSpawnStateBit(edge, 7);
        f.at(0x300, 0x7000, 0, 0);
        assertEquals(List.of(edge, customKeep), f.actors().stream().map(Actor::getSpawn).toList());
        assertFalse(f.manager.isSpawnStateBitSet(edge, 7), "Standard cull clears bit 7 at the shared respawn address");
        f.at(0, 0, 0, 0);
        assertEquals(4, f.created.size(), "Culling does not rearm placement");
    }

    @Test void compositeWorldRestoreBeforeAndAfterInitReplaysActorsScratchAndBothCameras() {
        Fixture f = new Fixture(records(6));
        f.manager.resetForPreludeHydration(0);
        RewindRegistry world = f.worldSnapshots();
        CompositeSnapshot beforeInit = world.capture();
        f.manager.preloadInitialSpawnsForHydration();
        f.manager.setSpawnStateBit(f.actors().getFirst().getSpawn(), 3);
        f.at(0x1000, 0x7000, 0x100, 0xC00);
        CompositeSnapshot afterInit = world.capture();
        assertFalse(RewindSnapshotDiff.keyEquals("native_competition_upfront_placement",
                beforeInit.get("native_competition_upfront_placement"), afterInit.get("native_competition_upfront_placement")));
        f.actors().getFirst().setDestroyed(true);
        f.at(0x3000, 0x5000, 0xF00, 0x400);
        List<String> forward = f.graph();
        CompositeSnapshot expected = world.capture();
        world.restore(afterInit);
        assertWorldEquals(afterInit, world.capture());
        f.actors().getFirst().setDestroyed(true);
        f.at(0x3000, 0x5000, 0xF00, 0x400);
        assertEquals(forward, f.graph());
        assertWorldEquals(expected, world.capture());
        world.restore(beforeInit);
        assertTrue(f.actors().isEmpty());
        f.manager.preloadInitialSpawnsForHydration();
        f.manager.setSpawnStateBit(f.actors().getFirst().getSpawn(), 3);
        f.at(0x1000, 0x7000, 0x100, 0xC00);
        assertWorldEquals(afterInit, world.capture());
    }

    @Test void completedFullRamCompositeRestoreCannotAccidentallyRearmInitialAllocation() {
        Fixture f = new Fixture(records(4));
        f.manager.resetForPreludeHydration(0);
        f.manager.reserveAllButNFreeSlots(0);
        f.manager.preloadInitialSpawnsForHydration();
        RewindRegistry world = f.worldSnapshots();
        CompositeSnapshot full = world.capture();
        f.manager.reset(0);
        assertEquals(4, f.actors().size());
        world.restore(full);
        assertTrue(f.actors().isEmpty());
        f.at(0x7000, 0, 0, 0xF00);
        f.manager.preloadInitialSpawnsForHydration();
        assertTrue(f.actors().isEmpty());
        assertEquals(4, f.created.size());
    }

    @Test void firstProbePrecedesCutoffAndFinalProbeDoesNotRequireAnotherRecord() {
        NativeCompetitionUpfrontPlacement policy = new NativeCompetitionUpfrontPlacement(0x800);
        AtomicInteger firstProbes = new AtomicInteger();
        AtomicInteger nextProbes = new AtomicInteger();
        policy.reset(List.of(spawn(0x800, 0)));
        policy.initialize(() -> { firstProbes.incrementAndGet(); return 4; },
                (spawn, slot) -> fail("Cutoff must precede construction"), slot -> fail("No write means no next probe"));
        assertEquals(1, firstProbes.get());
        policy.initialize(() -> fail("The terminal loader does not retry"), (spawn, slot) -> false, slot -> -1);
        policy.reset(List.of(spawn(0x7FF, 0)));
        policy.initialize(() -> 93, (spawn, slot) -> { assertEquals(93, slot); return true; },
                slot -> { nextProbes.incrementAndGet(); return -1; });
        assertEquals(1, nextProbes.get(), "CreateNewSprite4 probes even after the final write");
    }

    @Test void configurationRequiresAnExplicitRosterAndExcludesS2StreamingInBothDirections() {
        Fixture ordinary = new Fixture(List.of(spawn(0x700, 0)), false);
        assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.enableNativeCompetitionUpfrontPlacement(ordinary.manager, 0x800));
        ordinary.manager.reset(0);
        assertTrue(ordinary.created.isEmpty(), "Ordinary single-human placement keeps its camera window");
        ordinary.at(0x600, 0, 0xF00, 0);
        assertEquals(1, ordinary.created.size(), "Ordinary two-axis streaming still loads on movement");
        Fixture upfront = new Fixture(List.of());
        assertThrows(IllegalStateException.class, () -> ObjectManagerInternalAccess.enableNativeCompetitionStreaming(
                upfront.manager, new Sonic2CompetitionObjectStreaming()));
        Fixture streaming = new Fixture(List.of(), true, false);
        ObjectManagerInternalAccess.enableNativeCompetitionStreaming(streaming.manager, new Sonic2CompetitionObjectStreaming());
        assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.enableNativeCompetitionUpfrontPlacement(streaming.manager, 0x800));
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void fiveCompetitionRomLayoutsLoadTheirRawSourcePrefixThroughTheProductionParser() throws Exception {
        Sonic3kObjectPlacement parser = new Sonic3kObjectPlacement(RomByteReader.fromRom(GameServices.rom().getRom()));
        // SpriteLocPtrs ALZ/BPZ/DPZ/CGZ/EMZ (sonic3k.asm:202539-202548).
        int[] nativeCounts = {8, 15, 17, 15, 15};
        for (int zone = 14; zone <= 18; zone++) {
            List<ObjectSpawn> raw = parser.load(zone, 0);
            assertEquals(nativeCounts[zone - 14], raw.size(), "Verified ROM record count for zone " + zone);
            List<ObjectSpawn> prefix = raw.stream().takeWhile(spawn -> spawn.x() < 0x800).limit(90).toList();
            assertFalse(prefix.isEmpty());
            Fixture f = new Fixture(raw);
            f.manager.reset(0x7000);
            assertEquals(prefix, f.created, "Raw source-order prefix zone " + zone);
            assertEquals(prefix, f.actors().stream().map(Actor::getSpawn).toList());
            assertTrue(f.actors().stream().allMatch(a -> a.getRespawnStateIndex() >= raw.size()));
        }
    }
}
