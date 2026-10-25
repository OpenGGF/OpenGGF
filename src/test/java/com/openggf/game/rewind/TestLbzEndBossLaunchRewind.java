package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.bosses.LbzEndBossInstance;
import com.openggf.game.sonic3k.objects.bosses.LbzEndBossInstance.LbzEndBossSpikeBallChild;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/** Field-local evidence: attempted launches are not a live-child inventory or gameplay state. */
class TestLbzEndBossLaunchRewind {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(0x3B00, 0x5F8, 0xCB, 0, 0, false, 0);

    @BeforeEach
    void initGraphics() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retiredLaunchesDoNotReappearAndNextAttemptReplaysExactly(boolean exhaustSlots) throws Exception {
        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            // Bounded terrain seam: real falling/rolling/explosion/manager retirement, no force helpers.
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(-1, (byte) 0, 0));
            terrain.when(() -> ObjectTerrainUtils.checkRightWallDist(anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(-1, (byte) 0, 0));
            Harness h = new Harness();
            for (int tick = 0; tick < 2000 && !h.beforeThirdAttempt(); tick++) {
                h.step();
            }
            assertTrue(h.beforeThirdAttempt(), "reach the real fire timer before attempt three");
            assertEquals(List.of(0, 2), h.manager.attempts.stream().map(Attempt::subtype).toList());
            assertTrue(h.balls().isEmpty(), "both earlier projectiles retired before this capture");
            assertTrue(h.manager.attempts.stream().allMatch(a -> a.id() != null && a.slot() >= 0));
            assertEquals(2, readInt(h.boss(), "launchCount"));
            int captureClock = h.manager.getVblaCounter();
            int releasedSlot = -1;
            if (exhaustSlots) {
                // Real managed occupants survive capture. One retires AFTER the rejected attempt,
                // so no ownerless allocator bits or post-restore slot overrides are needed.
                while (h.manager.getActiveObjects().size() < ObjectSlotLayout.SONIC_3K.dynamicSlotCount()) {
                    boolean last = h.manager.getActiveObjects().size()
                            == ObjectSlotLayout.SONIC_3K.dynamicSlotCount() - 1;
                    var occupant = h.manager.createDynamicObject(() ->
                            new SlotOccupant(last ? captureClock + 2 : Integer.MAX_VALUE));
                    if (last) releasedSlot = occupant.getSlotIndex();
                }
            }
            Frame captured = h.state();
            var originals = List.copyOf(h.manager.getActiveObjects());
            ObjectRefId bossId = h.id(h.boss());
            CompositeSnapshot snapshot = h.registry.capture();

            Outcome uninterrupted = h.forward(exhaustSlots);
            assertEquals(0x12345678L, h.services.rng().getSeed(), "launches do not consume RNG");
            assertEquals(exhaustSlots ? List.of(1, 2) : List.of(1),
                    uninterrupted.attempts().stream().map(Attempt::subtype).toList());
            Attempt first = uninterrupted.attempts().getFirst();
            assertEquals(captureClock + 1, first.clock(), "timer borrow owns the attempt clock");
            if (exhaustSlots) {
                assertEquals(-1, first.slot());
                assertNull(first.id(), "failed admission must not mint a managed identity");
                assertTrue(first.destroyed());
                assertEquals(releasedSlot, uninterrupted.attempts().getLast().slot());
            }
            Attempt admitted = uninterrupted.attempts().getLast();
            assertNotNull(admitted.id());
            assertFalse(admitted.destroyed());
            assertTrue(admitted.slot() > h.boss().getSlotIndex(), "native after-current allocation");
            Frame firstTick = uninterrupted.frames().stream()
                    .filter(f -> f.clock() == admitted.clock()).findFirst().orElseThrow();
            Occupant ball = firstTick.occupants().stream()
                    .filter(o -> admitted.id().equals(o.id())).findFirst().orElseThrow();
            assertEquals(14, ball.ball().getFirst(), "higher-slot child runs on its allocation pass");
            assertEquals(admitted.x() + (admitted.xVel() >> 8), ball.x());
            assertEquals(admitted.y() + (admitted.yVel() >> 8), ball.y());
            assertEquals(exhaustSlots ? 4 : 3, readInt(h.boss(), "launchCount"),
                    "a failed attempt consumes its table entry as well");
            assertTrue(h.balls().isEmpty(), "the admitted replay projectile also retires");

            // Destructively empty the REAL manager, then reconstruct through its registry path.
            for (ObjectInstance object : List.copyOf(h.manager.getActiveObjects())) {
                h.manager.removeDynamicObject(object);
            }
            assertTrue(h.manager.getActiveObjects().isEmpty());
            h.registry.restore(snapshot);
            assertEquals(bossId, h.id(h.boss()));
            assertEquals(captured, h.state(), "capture contains current state, not retired launch history");
            assertTrue(h.manager.getActiveObjects().stream().noneMatch(restored ->
                    originals.stream().anyMatch(original -> original == restored)), "every object is fresh");
            assertTrue(h.balls().isEmpty(), "no historical balls are reconstructed");
            assertEquals(uninterrupted, h.forward(exhaustSlots),
                    "attempt clocks/order, SFX, identities, slots, first ticks and retirement must replay");
        }
    }

    private record Attempt(int clock, int subtype, int afterSlot, int slot, ObjectRefId id,
                           boolean destroyed, int x, int y, int xVel, int yVel) { }
    private record Occupant(ObjectRefId id, int slot, String type, int x, int y,
                            ObjectRefId parent, List<Integer> ball) { }
    private record Frame(int clock, int launchCount, int routine, int secondary, int timer,
                         int flags, long rng, List<Occupant> occupants) { }
    private record Outcome(List<Attempt> attempts, List<String> events, List<Frame> frames) { }

    private static final class RecordingManager extends ObjectManager {
        final List<Attempt> attempts = new ArrayList<>();
        final List<String> events = new ArrayList<>();

        RecordingManager(Camera camera, ObjectServices services) {
            super(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
        }

        @Override
        public void addDynamicObjectAfterSlot(ObjectInstance object, int parentSlot) {
            super.addDynamicObjectAfterSlot(object, parentSlot);
            if (object instanceof LbzEndBossSpikeBallChild ball) {
                attempts.add(new Attempt(getVblaCounter(), ball.getRomSubtypeForTests(), parentSlot,
                        ball.getSlotIndex(), captureIdentityContext().requireIdentityTable().idFor(ball),
                        ball.isDestroyed(), ball.getX(), ball.getY(),
                        ball.getXVelForTests(), ball.getYVelForTests()));
                events.add(getVblaCounter() + ":submit");
            }
        }
    }

    /** A bounded native SST occupant; its scheduled expiration is ordinary captured scalar state. */
    public static final class SlotOccupant extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private int releaseClock;

        public SlotOccupant(int releaseClock) {
            this(SPAWN);
            this.releaseClock = releaseClock;
        }

        public SlotOccupant(ObjectSpawn spawn) {
            super(spawn, "LaunchBoundaryOccupant");
            releaseClock = Integer.MAX_VALUE;
        }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            if (vIntRunCount == releaseClock) ObjectLifetimeOps.expireDynamic(this);
        }
    }

    private static final class Harness {
        final Camera camera = new Camera();
        final RecordingManager manager;
        final RewindRegistry registry = new RewindRegistry();
        final StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public void playSfx(int id) {
                if (id == Sonic3kSfx.TUBE_LAUNCHER.id) manager.events.add(manager.getVblaCounter() + ":sfx");
            }
        };

        Harness() {
            camera.setX((short) 0x3A20);
            camera.setY((short) 0x5A0);
            camera.setMaxX((short) 0x7FFF);
            camera.setMaxY((short) 0x7FFF);
            services.rng().setSeed(0x12345678L);
            manager = new RecordingManager(camera, services);
            manager.reset(camera.getX());
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.createDynamicObject(() -> new LbzEndBossInstance(SPAWN));
            registry.register(manager.rewindSnapshottable());
            registry.register(camera);
            registry.register(services.rng());
        }

        LbzEndBossInstance boss() {
            return manager.getActiveObjects().stream().filter(LbzEndBossInstance.class::isInstance)
                    .map(LbzEndBossInstance.class::cast).findFirst().orElseThrow();
        }

        List<LbzEndBossSpikeBallChild> balls() {
            return manager.getActiveObjects().stream().filter(LbzEndBossSpikeBallChild.class::isInstance)
                    .map(LbzEndBossSpikeBallChild.class::cast).toList();
        }

        boolean beforeThirdAttempt() throws Exception {
            return readInt(boss(), "launchCount") == 2 && boss().getState().routine == 0x0A
                    && boss().getState().routineSecondary == 1 && boss().getCustomFlag(0x2E) == 0;
        }

        void step() {
            manager.update(camera.getX(), null, List.of(), manager.getVblaCounter() + 1, false);
        }

        Outcome forward(boolean exhausted) throws Exception {
            manager.attempts.clear();
            manager.events.clear();
            List<Frame> frames = new ArrayList<>();
            int expectedAttempts = exhausted ? 2 : 1;
            for (int tick = 0; tick < 600; tick++) {
                step();
                frames.add(state());
                if (manager.attempts.size() == expectedAttempts && balls().isEmpty()) break;
            }
            assertEquals(expectedAttempts, manager.attempts.size(), "bounded next launch progression");
            assertTrue(balls().isEmpty(), "bounded projectile retirement");
            assertEquals(manager.attempts.stream().flatMap(a ->
                            List.of(a.clock() + ":sfx", a.clock() + ":submit").stream()).toList(),
                    manager.events, "SFX precedes every admission attempt, including rejection");
            return new Outcome(List.copyOf(manager.attempts), List.copyOf(manager.events), List.copyOf(frames));
        }

        ObjectRefId id(ObjectInstance object) {
            return manager.captureIdentityContext().requireIdentityTable().idFor(object);
        }

        Frame state() throws Exception {
            List<Occupant> occupants = new ArrayList<>();
            for (ObjectInstance object : manager.getActiveObjects()) {
                var instance = (AbstractObjectInstance) object;
                ObjectRefId parentId = null;
                if (object instanceof AbstractBossChild) {
                    var field = AbstractBossChild.class.getDeclaredField("parent");
                    field.setAccessible(true);
                    parentId = id((ObjectInstance) field.get(object));
                }
                List<Integer> ballState = object instanceof LbzEndBossSpikeBallChild ball
                        ? List.of(readInt(ball, "timer"), ball.getPhaseForTests(), ball.getXVelForTests(),
                                ball.getYVelForTests(), readInt(ball, "xFixed"), readInt(ball, "yFixed"),
                                ball.getCollisionFlags()) : List.of();
                occupants.add(new Occupant(id(object), instance.getSlotIndex(), object.getClass().getSimpleName(),
                        object.getX(), object.getY(), parentId, ballState));
            }
            occupants.sort(Comparator.comparingInt(Occupant::slot));
            return new Frame(manager.getVblaCounter(), readInt(boss(), "launchCount"), boss().getState().routine,
                    boss().getState().routineSecondary, boss().getCustomFlag(0x2E), boss().getCustomFlag(0x38),
                    services.rng().getSeed(), List.copyOf(occupants));
        }
    }

    private static int readInt(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
