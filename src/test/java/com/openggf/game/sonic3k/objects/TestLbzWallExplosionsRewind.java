package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameRng;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Managed destruction/recreation oracle for the five folded Knuckles wall controllers. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLbzWallExplosionsRewind {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(
            0x3C40, 0x936, Sonic3kObjectIds.LBZ_MINIBOSS_BOX_KNUX, 0, 0, false, 50);

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void pendingWallEmissionsRestoreFreshInOrderAndReplayAgainstCurrentSharedRng() throws Exception {
        Harness h = new Harness();
        LbzMinibossBoxKnuxInstance source = h.startWallExplosions();
        List<S3kBossExplosionController> originals = controllers(source);
        assertEquals(5, originals.size());
        // Deliberately retain queued effects as well as timers. Ordinary owner updates
        // drain these immediately; a helper snapshot must still preserve its full shape.
        for (int i = 0; i < originals.size(); i++) {
            for (int tick = 0; tick < i + 3; tick++) originals.get(i).tick();
        }
        List<S3kBossExplosionController.Snapshot> expected = states(source);
        assertTrue(expected.stream().allMatch(s -> s.pending().length > 0));
        assertTrue(expected.stream().anyMatch(s -> s.pending().length > 1));
        ObjectRefId id = h.id(source);
        int slot = source.getSlotIndex();
        CompositeSnapshot snapshot = h.registry().capture();
        List<Frame> forward = h.advance(100);
        assertTrue(forward.stream().anyMatch(f -> !f.children().isEmpty()), "control emits real managed children");
        assertTrue(forward.stream().anyMatch(f -> !f.sfx().isEmpty()), "control plays explosion SFX");
        assertTrue(forward.getLast().destroyed(), "control reaches DONE deletion");
        long finalSeed = h.services.rng().getSeed();
        h.manager.removeDynamicObject(source);
        h.services.rng = new GameRng(GameRng.Flavour.S3K, 0xDEADBEEFL);
        h.registry().restore(snapshot);

        LbzMinibossBoxKnuxInstance restored = h.box();
        assertNotSame(source, restored);
        assertEquals(id, h.id(restored));
        assertEquals(slot, restored.getSlotIndex());
        assertEquals(50, restored.getRespawnStateIndex());
        assertEquals(7, field(restored, "armTimer").getInt(restored), "default owner scalars still restore");
        assertEquals(5, restored.getDefeatBitsForTest());
        assertControllerStates(expected, states(restored));
        for (int i = 0; i < originals.size(); i++) {
            assertNotSame(originals.get(i), controllers(restored).get(i));
            assertSame(h.services.rng(), field(controllers(restored).get(i), "rng").get(controllers(restored).get(i)));
        }
        assertIterableEquals(forward, h.advance(100), "coordinates, slots, raw clocks, SFX, shared cursor and lifetime replay");
        assertEquals(finalSeed, h.services.rng().getSeed());
        // Reuse the same immutable snapshot after both source and restored helpers advanced.
        h.registry().restore(snapshot);
        assertControllerStates(expected, states(h.box()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 89, 95})
    void doneWaitsForAllFiveControllersAfterDestructiveRestore(int elapsed) throws Exception {
        Harness h = new Harness();
        LbzMinibossBoxKnuxInstance source = h.startWallExplosions();
        h.advance(elapsed);
        assertFalse(source.isDestroyed(), "control is captured before the zero-timer deletion pass");
        CompositeSnapshot snapshot = h.registry().capture();
        List<Frame> forward = h.advance(100 - elapsed);
        int deletion = -1;
        for (int i = 0; i < forward.size(); i++) {
            if (forward.get(i).destroyed()) { deletion = i + 1; break; }
        }
        assertEquals(96 - elapsed, deletion, "subtype 0 deletes on the 32nd three-frame timer pass");
        h.manager.removeDynamicObject(source);
        h.registry().restore(snapshot);
        assertNotSame(source, h.box());
        assertIterableEquals(forward, h.advance(100 - elapsed), "empty recreation must not make DONE allMatch true early");
        assertFalse(h.box.isDestroyedRespawnable(), "completion remains a no-respawn deletion");
    }

    private static void assertControllerStates(List<S3kBossExplosionController.Snapshot> expected,
                                               List<S3kBossExplosionController.Snapshot> actual) {
        assertEquals(expected.size(), actual.size(), "every ordered wall controller survives recreation");
        for (int i = 0; i < expected.size(); i++) {
            var e = expected.get(i);
            var a = actual.get(i);
            assertAll("controller " + i,
                    () -> assertEquals(e.centreX(), a.centreX()), () -> assertEquals(e.centreY(), a.centreY()),
                    () -> assertEquals(e.xRange(), a.xRange()), () -> assertEquals(e.yRange(), a.yRange()),
                    () -> assertEquals(e.timer(), a.timer()), () -> assertEquals(e.intervalCounter(), a.intervalCounter()),
                    () -> assertEquals(e.rng(), a.rng()), () -> assertArrayEquals(e.pending(), a.pending()));
        }
    }

    private static List<S3kBossExplosionController.Snapshot> states(LbzMinibossBoxKnuxInstance box) throws Exception {
        return controllers(box).stream().map(S3kBossExplosionController::captureSnapshot).toList();
    }

    private static List<S3kBossExplosionController> controllers(LbzMinibossBoxKnuxInstance box) throws Exception {
        var result = new ArrayList<S3kBossExplosionController>();
        for (Object controller : (Iterable<?>) field(box, "wallExplosions").get(box)) {
            result.add((S3kBossExplosionController) controller);
        }
        return result;
    }

    private record Child(int slot, int x, int y, int cursor, int timer, boolean pendingDelete) {}
    private record Frame(List<Child> children, List<Integer> sfx, long rng, boolean destroyed,
                         int frameCounter, int vIntRunCount) {}

    private static final class Harness {
        private final Camera camera = new Camera() {
            @Override public short getX() { return 0x3C40; }
            @Override public short getY() { return 0x0A7C; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        private final RecordingServices services = new RecordingServices();
        private final ObjectManager manager = new ObjectManager(
                List.of(), null, 0, null, null, GraphicsManager.getInstance(), camera, services);
        private LbzMinibossBoxKnuxInstance box;

        private Harness() {
            services.zoneRuntimeRegistry().install(new LbzZoneRuntimeState(0, PlayerCharacter.KNUCKLES));
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.reset(camera.getX());
        }

        private LbzMinibossBoxKnuxInstance startWallExplosions() throws Exception {
            box = manager.createDynamicObject(() -> new LbzMinibossBoxKnuxInstance(SPAWN));
            Field phase = field(box, "phase");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object fight = Enum.valueOf((Class) phase.getType(), "FIGHT");
            phase.set(box, fight);
            field(box, "armTimer").setInt(box, 7);
            box.setRespawnStateIndex(50);
            box.onKnucklesMinibossDefeated(0);
            box.onKnucklesMinibossDefeated(2);
            box.update(0xFA, null); // Use the real defeat publisher to build the five helpers.
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object done = Enum.valueOf((Class) phase.getType(), "DONE");
            phase.set(box, done); // Isolate the wall-completion gate after the sign handoff.
            field(box, "chunkSwapTimer").setInt(box, -1);
            field(box, "maxYRaiseActive").setBoolean(box, false);
            return box;
        }

        private RewindRegistry registry() {
            RewindRegistry registry = new RewindRegistry();
            registry.register(manager.rewindSnapshottable());
            registry.register(services.rng());
            return registry;
        }

        private ObjectRefId id(LbzMinibossBoxKnuxInstance object) {
            return manager.captureIdentityContext().requireIdentityTable().idFor(object);
        }

        private LbzMinibossBoxKnuxInstance box() {
            box = manager.getActiveObjects().stream().filter(LbzMinibossBoxKnuxInstance.class::isInstance)
                    .map(LbzMinibossBoxKnuxInstance.class::cast).findFirst().orElseThrow();
            return box;
        }

        private List<Frame> advance(int frames) throws Exception {
            List<Frame> result = new ArrayList<>();
            for (int i = 0; i < frames; i++) {
                services.sfx.clear();
                // An unrelated shared consumer distinguishes rebinding from per-controller RNG clones.
                services.rng().nextRaw();
                manager.update(camera.getX(), null, List.of(), i, false);
                List<Child> children = manager.getActiveObjects().stream()
                        .filter(S3kBossExplosionChild.class::isInstance).map(S3kBossExplosionChild.class::cast)
                        .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                        .map(c -> new Child(c.getSlotIndex(), c.getX(), c.getY(), c.rawCursorForTest(),
                                c.rawTimerForTest(), c.pendingDeleteForTest())).toList();
                assertTrue(services.sfx.stream().allMatch(s -> s == Sonic3kSfx.EXPLODE.id));
                result.add(new Frame(children, List.copyOf(services.sfx), services.rng().getSeed(), box.isDestroyed(),
                        field(manager, "frameCounter").getInt(manager), field(manager, "vblaCounter").getInt(manager)));
            }
            return result;
        }

        private final class RecordingServices extends StubObjectServices {
            private final List<Integer> sfx = new ArrayList<>();
            private GameRng rng = new GameRng(GameRng.Flavour.S3K, 0x12345678L);
            @Override public ObjectManager objectManager() { return manager; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public Rom rom() { return TestEnvironment.currentRom(); }
            @Override public GameRng rng() { return rng; }
            @Override public void playSfx(int soundId) { sfx.add(soundId); }
        }
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
