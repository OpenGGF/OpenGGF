package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.OwnedMocks;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** Initialized encounter controls: rig presence, piece publication and managed reconstruction. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLbzBoxRigsRewind {
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
    void initializedEncounterRestoresProgressedRigsAndReplaysThroughRetirement() throws Exception {
        try (Harness h = new Harness()) {
            LbzMinibossBoxKnuxInstance source = h.initializeAndRelease();
            for (int i = 0; i < 80; i++) h.step();
            source.onKnucklesMinibossDefeated(0);
            source.onKnucklesMinibossDefeated(2);
            for (int i = 0; i < 5; i++) h.step();
            Output immediate = h.output(source);
            assertFalse(immediate.draws().isEmpty(), "positive control: progressed box pieces draw");
            assertTrue(immediate.draws().size() < 20, "burst panels have retired; this is not the closed constructor shape");
            assertFalse(immediate.regions().isEmpty(), "positive control: managed miniboss arms publish collision");
            assertFalse(immediate.effects().isEmpty(), "positive control: real wall explosion children exist");
            var id = h.manager.captureIdentityContext().requireIdentityTable().idFor(source);
            var snapshot = h.registry.capture();
            List<Output> expected = h.replay(source);
            assertTrue(expected.stream().anyMatch(o -> o.extraBuckets().contains(LbzMinibossBoxRig.LINGER_PRIORITY_BUCKET)),
                    "forward control reaches late-animation bucket changes");
            assertTrue(expected.getLast().destroyed(), "forward control retires every piece and the DONE owner");
            for (var object : List.copyOf(h.manager.getActiveObjects())) h.manager.removeDynamicObject(object);

            h.registry.restore(snapshot);

            LbzMinibossBoxKnuxInstance restored = h.box();
            assertNotSame(source, restored);
            assertEquals(id, h.manager.captureIdentityContext().requireIdentityTable().idFor(restored));
            assertEquals(immediate, h.output(restored), "draw/collision/effects before any restored update");
            assertEquals(expected, h.replay(restored), "all piece movement, buckets, owner state and retirement replay");
            h.registry.restore(snapshot);
            assertEquals(immediate, h.output(h.box()), "reusing a snapshot must not alias advanced helpers");
        }
    }

    @Test
    void emptyCameraGatedSnapshotClearsInitializedRigsAndReinitializesOnlyAtTheGate() throws Exception {
        try (Harness h = new Harness()) {
            h.camera.setX((short) 0x3B9F); // Just outside the native xMin=$3BA0 activation range.
            var source = h.manager.createDynamicObject(() -> new LbzMinibossBoxKnuxInstance(SPAWN));
            h.step();
            var empty = h.registry.capture();
            assertEquals("WAIT_CAMERA_RANGE", String.valueOf(source.getPhaseForTest()));
            assertEquals(List.of(), h.output(source).draws());
            h.camera.setX((short) 0x3BA0);
            h.step();
            assertEquals(20, h.output(source).draws().size(), "camera gate initializes ten closed pieces per box");
            var closed = h.registry.capture();
            Output expected = h.output(source);
            h.manager.removeDynamicObject(source);
            h.registry.restore(closed);
            assertNotSame(source, h.box());
            assertEquals(expected, h.output(h.box()), "closed initialized rigs also restore before release");
            h.registry.restore(empty);
            assertEquals(List.of(), h.output(h.box()).draws(), "empty restore clears divergent initialized helpers");
            h.step();
            assertEquals("WAIT_CAMERA_RANGE", String.valueOf(h.box().getPhaseForTest()));
            assertEquals(List.of(), h.output(h.box()).draws(), "restoration does not bypass the camera gate");
            h.camera.setX((short) 0x3BA0);
            h.step();
            assertEquals(20, h.output(h.box()).draws().size());
            h.registry.restore(empty);
            assertEquals(List.of(), h.output(h.box()).draws(), "repeated empty restore remains empty");
        }
    }

    @ParameterizedTest(name = "left {0}, right {1}, explicit context {2}")
    @CsvSource({"false,false,false", "true,false,false", "false,true,false", "true,true,false",
            "false,false,true", "true,false,true", "false,true,true", "true,true,true"})
    void publicRoundTripsPreserveIndependentPresenceAndProgressWithoutServices(
            boolean left, boolean right, boolean explicitContext) throws Exception {
        try (Harness h = new Harness()) {
            // Partial combinations are codec boundary cases, not reachable encounter phases.
            // Seed only optional helper presence; assertions use draw/movement/lifetime behavior.
            var source = new LbzMinibossBoxKnuxInstance(SPAWN);
            if (left) setRig(source, "leftRig", progressedRig(0x3C20, 182));
            if (right) setRig(source, "rightRig", progressedRig(0x3C60, 83));
            source.onKnucklesMinibossDefeated(0);
            var snapshot = explicitContext ? source.captureRewindState(RewindCaptureContext.none())
                    : source.captureRewindState();
            var immediate = h.rigsOutput(source);
            assertEquals(left, immediate.get(0).present());
            assertEquals(right, immediate.get(1).present());
            var expected = h.replayRigs(source);
            var target = new LbzMinibossBoxKnuxInstance(SPAWN);
            setRig(target, "leftRig", new LbzMinibossBoxRig(0x100, 0x200));
            setRig(target, "rightRig", new LbzMinibossBoxRig(0x300, 0x400));
            if (explicitContext) target.restoreRewindState(snapshot, RewindCaptureContext.none());
            else target.restoreRewindState(snapshot);
            assertEquals(1, target.getDefeatBitsForTest(), "owner scalars remain captured");
            assertEquals(immediate, h.rigsOutput(target));
            assertEquals(expected, h.replayRigs(target), "each optional rig keeps its own phase, fractions and buckets");
            if (explicitContext) source.restoreRewindState(snapshot, RewindCaptureContext.none());
            else source.restoreRewindState(snapshot);
            assertEquals(immediate, h.rigsOutput(source), "same-instance context-free restoration after divergence");
            assertEquals(expected, h.replayRigs(source), "repeated restore cannot reuse advanced piece state");
        }
    }

    @Test
    void optionalRigsAndWallControllersStayCapturedOnTheActualDefaultRoute() {
        Class<?> owner = LbzMinibossBoxKnuxInstance.class;
        assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(owner));
        assertTrue(GenericRewindEligibility.usesCompactDefaultSubclassCapture(owner),
                "stable optional holders now permit compact fresh-owner restoration");
        var snapshot = new LbzMinibossBoxKnuxInstance(SPAWN).captureRewindState();
        assertNotNull(snapshot.compactGenericState(), "verify the actual public capture route");
        assertNull(snapshot.genericState(), "the generic fallback is no longer needed");
        var fields = GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit(owner).stream()
                .map(Field::getName).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("phase", "leftRig", "rightRig", "armTimer", "defeatBits", "chunkSwapTimer",
                "maxYRaiseActive", "maxYAccumulator", "wallExplosions"), fields);
    }

    private static LbzMinibossBoxRig progressedRig(int x, int ticks) {
        var rig = new LbzMinibossBoxRig(x, 0x936);
        rig.release();
        for (int i = 0; i < ticks; i++) rig.update(LbzMinibossBoxRig.NO_CAMERA);
        return rig;
    }

    private static LbzMinibossBoxRig rig(LbzMinibossBoxKnuxInstance box, String name) throws Exception {
        Object holder = field(box, name).get(box);
        return (LbzMinibossBoxRig) field(holder, "value").get(holder);
    }

    private static void setRig(LbzMinibossBoxKnuxInstance box, String name, LbzMinibossBoxRig rig) throws Exception {
        Object holder = field(box, name).get(box);
        field(holder, "value").set(holder, rig);
    }

    private record Draw(int bucket, int frame, int x, int y, boolean flipX, boolean flipY, int palette) {}
    private record RigOutput(boolean present, boolean released, boolean visible, List<Draw> draws, int buckets) {}
    private record Effect(int slot, int x, int y, int cursor, int timer, boolean pendingDelete) {}
    private record Output(List<Draw> draws, List<Integer> extraBuckets,
                          List<TouchResponseProvider.TouchRegion> regions, List<Effect> effects,
                          Map<String, Object> owner, List<Integer> sfx, long rng, boolean destroyed,
                          boolean respawnable, int cameraMaxY, int frame, int vInt) {}

    private static final class Harness implements AutoCloseable {
        private final OwnedMocks mocks = new OwnedMocks();
        private final List<Draw> draws = new ArrayList<>();
        private int bucket;
        private final Camera camera = new Camera();
        private final PatternSpriteRenderer renderer = mocks.mock(PatternSpriteRenderer.class);
        private final ObjectRenderManager renderManager = mocks.mock(ObjectRenderManager.class);
        private final AudioManager audioManager = mocks.mock(AudioManager.class);
        private final GameStateManager gameState = new GameStateManager();
        private final Services services = new Services();
        private final ObjectManager manager = new ObjectManager(
                List.of(), null, 0, null, null, GraphicsManager.getInstance(), camera, services);
        private final RewindRegistry registry = new RewindRegistry();

        private Harness() {
            camera.setX((short) 0x3C40);
            camera.setY((short) 0x900);
            services.zoneRuntimeRegistry().install(new LbzZoneRuntimeState(0, PlayerCharacter.KNUCKLES));
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.reset(camera.getX());
            registry.register(manager.rewindSnapshottable());
            registry.register(services.rng());
            registry.register(camera);
            registry.register(gameState);
            when(renderer.isReady()).thenReturn(true);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.LBZ_MINIBOSS_BOX)).thenReturn(renderer);
            doAnswer(call -> {
                draws.add(new Draw(bucket, call.getArgument(0), call.getArgument(1), call.getArgument(2),
                        call.getArgument(3), call.getArgument(4), call.getArgument(5)));
                return null;
            }).when(renderer).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt());
        }

        private LbzMinibossBoxKnuxInstance initializeAndRelease() {
            LbzMinibossBoxKnuxInstance box = manager.createDynamicObject(() -> new LbzMinibossBoxKnuxInstance(SPAWN));
            box.setRespawnStateIndex(50);
            step(); // Real camera-range initialization creates the two closed rigs.
            assertEquals("WAIT_FIGHT_START", String.valueOf(box.getPhaseForTest()));
            camera.setY((short) 0x936);
            step();
            assertEquals("ARM_WAIT", String.valueOf(box.getPhaseForTest()));
            for (int i = 0; i < 120; i++) step();
            assertEquals("FIGHT", String.valueOf(box.getPhaseForTest()));
            assertEquals(2, bosses().size(), "normal release creates the managed miniboss pair");
            return box;
        }

        private void step() {
            services.sfx.clear();
            services.rng().nextRaw(); // An independent consumer of the same session RNG.
            manager.update(camera.getX(), null, List.of(), 0, false);
        }

        private List<Output> replay(LbzMinibossBoxKnuxInstance box) throws Exception {
            List<Output> result = new ArrayList<>();
            for (int i = 0; i < 180; i++) {
                if (i == 120) camera.setY((short) 0xA7C); // Handoff; results remain outside this bounded replay.
                if (i == 160) camera.setX((short) 0x4000); // Native coarse-camera cull of lingering panels.
                step();
                result.add(output(box));
            }
            return result;
        }

        private List<RigOutput> rigsOutput(LbzMinibossBoxKnuxInstance box) throws Exception {
            List<RigOutput> result = new ArrayList<>();
            for (String name : List.of("leftRig", "rightRig")) {
                var rig = rig(box, name);
                draws.clear();
                if (rig != null) {
                    for (bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++) rig.draw(renderer, 2, bucket);
                }
                result.add(new RigOutput(rig != null, rig != null && rig.isReleased(),
                        rig != null && rig.hasVisiblePieces(), List.copyOf(draws), rig == null ? 0 : rig.liveBucketMask()));
            }
            return result;
        }

        private List<List<RigOutput>> replayRigs(LbzMinibossBoxKnuxInstance box) throws Exception {
            List<List<RigOutput>> result = new ArrayList<>();
            for (int tick = 0; tick < 180; tick++) {
                for (String name : List.of("leftRig", "rightRig")) {
                    var rig = rig(box, name);
                    if (rig != null) rig.update(tick < 160 ? 0x3C40 : 0x4000);
                }
                result.add(rigsOutput(box));
            }
            assertTrue(result.getLast().stream().noneMatch(RigOutput::visible), "all present pieces eventually retire");
            return result;
        }

        private Output output(LbzMinibossBoxKnuxInstance box) throws Exception {
            draws.clear();
            for (bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++) {
                box.appendRenderCommands(new ArrayList<>(), bucket);
            }
            List<TouchResponseProvider.TouchRegion> regions = new ArrayList<>();
            for (var boss : bosses()) {
                var touch = boss.getMultiTouchRegions();
                if (touch != null) regions.addAll(List.of(touch));
            }
            var effects = manager.getActiveObjects().stream().filter(S3kBossExplosionChild.class::isInstance)
                    .map(S3kBossExplosionChild.class::cast).sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                    .map(c -> new Effect(c.getSlotIndex(), c.getX(), c.getY(), c.rawCursorForTest(),
                            c.rawTimerForTest(), c.pendingDeleteForTest())).toList();
            Map<String, Object> owner = new TreeMap<>();
            for (Field f : LbzMinibossBoxKnuxInstance.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && (f.getType().isPrimitive() || f.getType().isEnum())) {
                    f.setAccessible(true);
                    owner.put(f.getName(), f.get(box));
                }
            }
            owner.put("slot", box.getSlotIndex());
            owner.put("respawn", box.getRespawnStateIndex());
            owner.put("spawn", box.getSpawn());
            return new Output(List.copyOf(draws), Arrays.stream(box.extraRenderBuckets()).boxed().toList(),
                    regions, effects, owner, List.copyOf(services.sfx), services.rng().getSeed(), box.isDestroyed(),
                    box.isDestroyedRespawnable(), camera.getMaxY() & 0xFFFF, manager.getFrameCounter(),
                    field(manager, "vblaCounter").getInt(manager));
        }

        private List<LbzMinibossInstance> bosses() {
            return manager.getActiveObjects().stream().filter(LbzMinibossInstance.class::isInstance)
                    .map(LbzMinibossInstance.class::cast).sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
        }

        private LbzMinibossBoxKnuxInstance box() {
            return manager.getActiveObjects().stream().filter(LbzMinibossBoxKnuxInstance.class::isInstance)
                    .map(LbzMinibossBoxKnuxInstance.class::cast).findFirst().orElseThrow();
        }

        private final class Services extends StubObjectServices {
            private final List<Integer> sfx = new ArrayList<>();
            @Override public ObjectManager objectManager() { return manager; }
            @Override public Camera camera() { return camera; }
            @Override public ObjectRenderManager renderManager() { return renderManager; }
            @Override public AudioManager audioManager() { return audioManager; }
            @Override public GameStateManager gameState() { return gameState; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public Rom rom() { return TestEnvironment.currentRom(); }
            @Override public void playSfx(int id) { sfx.add(id); }
        }

        @Override public void close() { mocks.close(); }
    }

    private static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
