package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Independent Obj24 routine/slot/RNG controls; no trace/movie inputs.
 * Uses an actual-ROM headless world for water and ROM services, an empty real
 * S2 ObjectManager for deterministic object topology, and production rewind
 * adapters in a private component registry. This does not replace a session test.
 */
@RequiresRom(SonicGame.SONIC_2)
@Isolated("Headless world owns legacy engine services")
class TestSonic2BubbleNativeLifecycle {
    private HeadlessTestFixture fixture;
    private Graph graph;

    @BeforeEach
    void setUp() throws Exception {
        // The load API uses registry index2; water/object services use ROM zone$0F.
        fixture = HeadlessTestFixture.builder().withZoneAndAct(2, 1).build();
        assertEquals(Sonic2Constants.ZONE_ARZ, GameServices.level().getRomZoneId());
        graph = new Graph();
    }

    @AfterEach
    void tearDown() { TestEnvironment.resetAll(); }

    @Test
    void romOwnsAllSevenScriptsIncludingImmediateAndLargePopAndGeneratorLoop() {
        int[][] expected = {
                {0x0E, 0, 1, 2, 0xFC}, {0x0E, 1, 2, 3, 4, 0xFC},
                {0x0E, 2, 3, 4, 5, 6, 0xFC}, {4, 0xFC}, {4, 0xFC},
                {4, 6, 7, 0xFC}, {0x0F, 0x0E, 0x0F, 0xFF}
        };
        for (int animation = 0; animation < expected.length; animation++) {
            int address = graph.script(animation);
            for (int i = 0; i < expected[animation].length; i++)
                assertEquals(expected[animation][i], graph.rom.readU8(address + i),
                        "Ani_obj24 script " + animation + " byte " + i);
        }
    }

    @Test
    void retailControlFlowOwnsChildRenderCullAndAllocatesBeforeGeneratorXDraw() {
        // loc_1F988: ObjectMove; tst render_flags; bpl DeleteObject; DisplaySprite.
        assertBytes(0x1F988, 0x61, 0, 3, 0x4C, 0x4A, 0x28, 0, 1, 0x6A, 6,
                0x4E, 0xF9, 0, 1, 0x64, 0xF4, 0x4E, 0xF9, 0, 1, 0x64, 0xE6);
        // loc_1FA2A: timer draw and store, THEN allocation and failure branch.
        assertBytes(0x1FA2A, 0x4E, 0xB9, 0, 0, 0x33, 0x90, 2, 0x40, 0, 0x1F,
                0x31, 0x40, 0, 0x38, 0x61, 0, 0x85, 0xA0, 0x66, 0x68);
        // Only the successful allocation path reaches the child-X RandomNumber.
        assertBytes(0x1FA4A, 0x4E, 0xB9, 0, 0, 0x33, 0x90);
        // loc_1FACE is the late coarse range/underwater/DisplaySprite tail.
        assertBytes(0x1FACE, 0x30, 0x28, 0, 8, 2, 0x40, 0xFF, 0x80, 0x90, 0x78,
                0xF7, 0xDA, 0x0C, 0x40, 2, 0x80, 0x62, 0, 1, 0xEA,
                0x30, 0x38, 0xF6, 0x46, 0xB0, 0x68, 0, 0x0C, 0x65, 0, 1, 0xD8, 0x4E, 0x75);
        int dispatch = 0x1F8B6 + graph.rom.readS16BE(0x1F8B6 + 8);
        assertEquals(0x1F9BC, dispatch, "routine8 dispatch is a later own-pass delete branch");
        assertBytes(dispatch, 0x60, 0, 3, 0x0C);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrWrongReaderFailsClosedWithoutStaticAnimationFallback(boolean truncated) {
        TestObjectServices services = new TestObjectServices() {
            @Override public RomByteReader romReader() {
                return truncated ? RomByteReader.fromBytes(new byte[0x100]) : null;
            }
        };
        BubbleObjectInstance bubble = new BubbleObjectInstance(0x80, 0x80, 0, 0);
        bubble.setServices(services);
        if (truncated) assertThrows(IndexOutOfBoundsException.class, () -> bubble.update(0, null));
        else assertThrows(NullPointerException.class, () -> bubble.update(0, null));
    }

    @Test
    void unsupportedRomAnimationCommandFailsClosed() {
        RomByteReader malformed = mock(RomByteReader.class);
        int script = graph.script(0);
        when(malformed.readS16BE(0x1FBCC)).thenReturn(script - 0x1FBCC);
        when(malformed.readU8(script)).thenReturn(graph.rom.readU8(script));
        when(malformed.readU8(script + 1)).thenReturn(0xFD);
        BubbleObjectInstance bubble = new BubbleObjectInstance(0x80, 0x80, 0, 0);
        bubble.setServices(new TestObjectServices().withRomReader(malformed));
        assertThrows(IllegalStateException.class, () -> bubble.update(0, null));
    }

    @Test
    void childOutsideCoarseWindowKeepsSlotUntilItsNextOwnRenderBitCull() throws Exception {
        SpawnProbe producer = graph.objects.createDynamicObjectWithReservedSlot(
                () -> new SpawnProbe(graph.x + 0x40, graph.y));
        BubbleObjectInstance bubble = graph.bubble(graph.x + 0x300, graph.y, 0, true);
        int childSlot = bubble.getSlotIndex();
        RngOracle oracle = new RngOracle(); oracle.draw();

        graph.tick();
        assertFalse(bubble.isDestroyed(), "Obj24_Init $84 survives its first own pass");
        assertTrue(graph.live().contains(bubble), "generic coarse culling must not release it");
        assertFalse(bool(bubble, "romRenderOnScreen"));
        assertEquals(oracle.seed, graph.rng.getSeed());
        assertFalse(bubble.isPersistent(), "own cull must not change seamless-carry classification");

        producer.armed = true;
        graph.tick();
        assertEquals(childSlot + 1, producer.allocatedSlot,
                "lower producer runs before child deletion and cannot reuse the child's slot");
        assertFalse(graph.live().contains(bubble));
        Noop replacement = graph.objects.createDynamicObjectWithReservedSlot(
                () -> new Noop(graph.x + 0x40, graph.y));
        assertEquals(childSlot, replacement.getSlotIndex());
        assertEquals(oracle.seed, graph.rng.getSeed(), "cull does not consume another angle draw");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void surfacePopUsesRomScriptAndDefersRoutineEightDeletionToNextOwnPass(int size) throws Exception {
        BubbleObjectInstance bubble = graph.bubble(graph.x + 0x80, graph.waterY, size, false);
        int popScript = graph.script(size + 3);
        int firstCommand = graph.rom.readU8(popScript + 1);
        graph.execute();
        assertFalse(bubble.isDestroyed(), "surface branch still publishes its pop/FC turn");

        if (firstCommand == 0xFC) {
            assertEquals(8, integer(bubble, "routine"));
            assertEquals(size, integer(bubble, "mappingFrame"));
            assertTrue(bool(bubble, "romDisplayedLastPass"), "FC still falls through to DisplaySprite");
            assertTrue(graph.live().contains(bubble));
            graph.publishSprites();
            graph.tick();
            assertFalse(graph.live().contains(bubble));
            return;
        }

        assertEquals(6, integer(bubble, "routine"));
        assertEquals(6, integer(bubble, "mappingFrame"));
        assertTrue(bool(bubble, "romDisplayedLastPass"));
        graph.publishSprites();
        int duration = graph.rom.readU8(popScript);
        for (int i = 0; i < duration; i++) {
            graph.tick();
            assertEquals(6, integer(bubble, "mappingFrame"));
        }
        graph.tick();
        assertEquals(7, integer(bubble, "mappingFrame"));
        for (int i = 0; i < duration; i++) graph.tick();
        graph.execute();
        assertEquals(8, integer(bubble, "routine"));
        assertEquals(7, integer(bubble, "mappingFrame"));
        assertTrue(bool(bubble, "romDisplayedLastPass"));
        assertTrue(graph.live().contains(bubble));
        graph.publishSprites();
        graph.tick();
        assertFalse(graph.live().contains(bubble));
    }

    @Test
    void inflationFcAdvancesToRoutineFourAndStopsAnimationWhileRiseContinues() throws Exception {
        BubbleObjectInstance bubble = graph.bubble(graph.x + 0x80, graph.y, 0, false);
        int[] frames = {0, 1, 2};
        int duration = graph.rom.readU8(graph.script(0)) + 1;
        for (int frame : frames) {
            graph.tick();
            assertEquals(frame, integer(bubble, "mappingFrame"));
            for (int i = 1; i < duration; i++) graph.tick();
        }
        graph.tick();
        assertEquals(4, integer(bubble, "routine"));
        int index = integer(bubble, "animFrameIndex"), timer = integer(bubble, "animTimer");
        int beforeY = bubble.getY();
        for (int i = 0; i < 4; i++) graph.tick();
        assertEquals(index, integer(bubble, "animFrameIndex"));
        assertEquals(timer, integer(bubble, "animTimer"));
        assertTrue(bubble.getY() < beforeY);
    }

    @Test
    void inhaledLargeBubbleRunsPopAndPublishesUnclampedFrameSeven() throws Exception {
        BubbleObjectInstance bubble = graph.bubble(graph.x + 0x80, graph.y, 2, false);
        int duration = graph.rom.readU8(graph.script(2)) + 1;
        for (int i = 0; i < 4 * duration; i++) graph.tick();
        fixture.sprite().setInWater(true);
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), bubble.getX());
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), bubble.getY() + 8);

        graph.execute(fixture.sprite());
        assertTrue(bubble.wasBreathed());
        assertEquals(6, integer(bubble, "routine"));
        assertTrue(bool(bubble, "romDisplayedLastPass"));
        graph.publishSprites();
        for (int i = 0; i <= graph.rom.readU8(graph.script(5)); i++) graph.tick();
        assertEquals(7, integer(bubble, "mappingFrame"));
        assertTrue(graph.live().contains(bubble));
        bubble.appendRenderCommands(new ArrayList<>());
        verify(graph.renderer).drawFrameIndex(7, bubble.getX(), bubble.getY(), false, false);
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true"})
    void idleWaterOrRenderGateSkipsRandomAndAnimationButStillChecksRangeTail(
            boolean aboveWater, boolean offscreen) throws Exception {
        BubbleGeneratorObjectInstance generator = graph.generator(
                graph.x - 0x180, aboveWater ? graph.waterY : graph.y);
        set(generator, "romRenderOnScreen", !offscreen);
        set(generator, "animFrameIndex", 1);
        set(generator, "animTimer", 7);
        graph.tick();
        assertEquals(0, graph.rng.getSeed());
        assertEquals(1, integer(generator, "animFrameIndex"));
        assertEquals(7, integer(generator, "animTimer"));
        assertTrue(generator.isDestroyed(), "both idle exits still run loc_1FACE range deletion");
    }

    @Test
    void activeBurstAboveWaterStillSpawnsBeforeTailReturnsWithoutDisplay() throws Exception {
        BubbleGeneratorObjectInstance generator = graph.generator(graph.x + 0x80, graph.waterY - 1);
        primeActive(generator, 2, false);
        RngOracle oracle = new RngOracle(); oracle.draw(); oracle.draw(); oracle.draw();
        graph.tick();
        assertEquals(oracle.seed, graph.rng.getSeed(), "timer/X plus child's own angle");
        assertEquals(1, graph.children().size());
        assertEquals(0x0E, integer(generator, "displayFrame"));
        assertFalse(bool(generator, "romDisplayedLastPass"), "above-water tail never DisplaySprites");
        assertFalse(generator.isDestroyed());
    }

    @Test
    void activeBurstCompletesRandomAndAllocationBeforeRangeDeletion() throws Exception {
        BubbleGeneratorObjectInstance generator = graph.generator(graph.x - 0x180, graph.y);
        primeActive(generator, 2, false);
        RngOracle oracle = new RngOracle(); oracle.draw(); oracle.draw(); oracle.draw();
        graph.tick();
        assertEquals(oracle.seed, graph.rng.getSeed());
        assertTrue(generator.isDestroyed());
        assertEquals(1, graph.children().size(), "native final off-range spawn remains a real SST child");
        assertFalse(graph.children().getFirst().isDestroyed(), "child keeps its initial $84 through this pass");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void failedAllocationConsumesTimerAndOptionalLastBubbleDelayOnly(int remaining) throws Exception {
        BubbleGeneratorObjectInstance generator = graph.generator(graph.x + 0x80, graph.y);
        primeActive(generator, remaining, true);
        int filled = 0;
        for (int attempt = 0; attempt < 0x70; attempt++) {
            if (graph.objects.createDynamicObjectWithReservedSlot(() -> new Noop(graph.x + 0x40, graph.y)) == null) break;
            filled++;
        }
        assertEquals(0x6F, filled, "stock dynamic pool has $70 slots, one already held by generator");
        assertNull(graph.objects.createDynamicObjectWithReservedSlot(() -> new Noop(graph.x + 0x40, graph.y)));
        RngOracle oracle = new RngOracle();
        int expectedTimer = oracle.draw() & 0x1F;
        if (remaining == 0) expectedTimer += (oracle.draw() & 0x7F) + 0x80;
        graph.tick();
        assertEquals(oracle.seed, graph.rng.getSeed(), "no X, large-chance or child angle draw on failure");
        assertEquals(expectedTimer, integer(generator, "frameTimer"));
        assertEquals(remaining - 1, integer(generator, "bubblesRemainingInBurst"));
        assertEquals(remaining == 0 ? 0 : 0x81, integer(generator, "stateFlags"));
        assertTrue(graph.children().isEmpty());
    }

    @Test
    void successfulLargeModeAllocationConsumesXChanceAndOwnPassAngle() throws Exception {
        BubbleGeneratorObjectInstance generator = graph.generator(graph.x + 0x80, graph.y);
        primeActive(generator, 2, true);
        RngOracle oracle = new RngOracle();
        for (int i = 0; i < 4; i++) oracle.draw();
        graph.tick();
        assertEquals(oracle.seed, graph.rng.getSeed());
        assertEquals(1, graph.children().size());
    }

    @Test
    void generatorAnimationUsesItsOwnRomTimerAcrossUnrelatedVIntJumps() throws Exception {
        BubbleGeneratorObjectInstance generator = graph.generator(graph.x + 0x80, graph.y);
        set(generator, "frameTimer", 300);
        int duration = graph.rom.readU8(graph.script(6)) + 1;
        for (int pass = 0; pass < duration * 2 + 1; pass++) {
            graph.objects.initVblaCounter(pass % 2 == 0 ? 15 : 0x70000000);
            graph.tick();
            int expected = pass < duration ? 14 : pass < duration * 2 ? 15 : 14;
            assertEquals(expected, integer(generator, "displayFrame"), "own-pass " + pass);
        }
        assertEquals(0, graph.rng.getSeed());
    }

    @Test
    void destructiveRegistryRestoreReplaysChildPopAndGeneratorAllocationState() throws Exception {
        BubbleGeneratorObjectInstance generator = graph.generator(graph.x + 0x80, graph.y);
        primeActive(generator, 3, false);
        BubbleObjectInstance pop = graph.bubble(graph.x + 0x60, graph.waterY, 2, false);
        graph.tick();
        assertEquals(6, integer(pop, "routine"));
        RewindRegistry registry = new RewindRegistry();
        registry.register(graph.objects.rewindSnapshottable());
        registry.register(graph.rng);
        CompositeSnapshot checkpoint = registry.capture();
        List<Output> expected = new ArrayList<>();
        for (int i = 0; i < 24; i++) { graph.tick(); expected.add(graph.output()); }
        CompositeSnapshot expectedState = registry.capture();

        graph.objects.reset(graph.x);
        graph.rng.setSeed(0xA5A5A5A5L);
        assertTrue(graph.live().isEmpty(), "destructive branch removes every captured instance");
        registry.restore(checkpoint);
        assertTrue(graph.live().stream().noneMatch(o -> o == generator || o == pop),
                "restore must reconstruct, not reuse these removed instances");
        assertSnapshotEqual(checkpoint, registry.capture());
        for (int i = 0; i < 24; i++) {
            graph.tick();
            assertEquals(expected.get(i), graph.output(), "native outputs after restored turn " + i);
        }
        assertSnapshotEqual(expectedState, registry.capture());
    }

    private static void primeActive(BubbleGeneratorObjectInstance generator, int remaining, boolean large)
            throws Exception {
        set(generator, "stateFlags", large ? 0x81 : 1);
        set(generator, "frameTimer", 0);
        set(generator, "bubblesRemainingInBurst", remaining);
        set(generator, "sequenceOffset", 0);
    }

    private void assertBytes(int address, int... expected) {
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], graph.rom.readU8(address + i), "ROM byte $" + Integer.toHexString(address + i));
    }

    private static void assertSnapshotEqual(CompositeSnapshot expected, CompositeSnapshot actual) {
        for (String key : List.of("object-manager", "gamerng")) {
            List<String> diffs = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diffs.isEmpty(), () -> String.join("\n", diffs));
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static int integer(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.getInt(object);
    }
    private static boolean bool(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.getBoolean(object);
    }

    private final class Graph {
        final Camera camera = fixture.camera();
        final int x = 0x1000;
        final int waterY = GameServices.water().getWaterLevelY(Sonic2Constants.ZONE_ARZ, 1);
        final int y = waterY + 0x40;
        final GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
        final RomByteReader rom;
        final ObjectManager objects;
        final PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);

        Graph() throws Exception {
            ObjectManager production = GameServices.level().getObjectManager();
            rom = production.getObjectServices().romReader();
            assertTrue(GameServices.water().hasWater(Sonic2Constants.ZONE_ARZ, 1), "actual S2 ARZ2 water setup is required");
            camera.setX((short) x); camera.setY((short) (waterY - 0x80));
            ObjectManager[] holder = new ObjectManager[1];
            ObjectRenderManager renders = mock(ObjectRenderManager.class);
            when(renders.getRenderer(Sonic2ObjectArtKeys.BUBBLES)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            TestObjectServices services = new TestObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public ObjectRenderManager renderManager() { return renders; }
            }.withLevelManager(GameServices.level()).withCamera(camera)
                    .withWaterSystem(GameServices.water()).withRng(rng).withRomReader(rom)
                    .withGameModule(production.getObjectServices().gameModule());
            objects = new ObjectManager(List.of(), new Sonic2ObjectRegistry(), 0, null, null,
                    GameServices.graphics(), camera, services);
            holder[0] = objects;
            objects.reset(x);
        }

        int script(int animation) { return 0x1FBCC + rom.readS16BE(0x1FBCC + animation * 2); }
        BubbleObjectInstance bubble(int px, int py, int size, boolean angleRng) {
            return objects.createDynamicObjectWithReservedSlot(() -> new BubbleObjectInstance(px, py, size, 0, angleRng));
        }
        BubbleGeneratorObjectInstance generator(int px, int py) {
            return objects.createDynamicObjectWithReservedSlot(() -> new BubbleGeneratorObjectInstance(
                    new ObjectSpawn(px, py, 0x24, 0x81, 0, false, 0), "Independent Obj24"));
        }
        void execute() { execute(null); }
        void execute(PlayableEntity player) { objects.update(x, player, List.of(), 0, false); }
        void publishSprites() { objects.refreshPostCameraRenderState(); }
        void tick() { execute(); publishSprites(); }
        List<ObjectInstance> live() { return List.copyOf(objects.getActiveObjects()); }
        List<BubbleObjectInstance> children() {
            return live().stream().filter(BubbleObjectInstance.class::isInstance)
                    .map(BubbleObjectInstance.class::cast).toList();
        }
        Output output() throws Exception {
            List<String> slots = new ArrayList<>();
            for (ObjectInstance object : live()) {
                AbstractObjectInstance nativeObject = (AbstractObjectInstance) object;
                String state = object instanceof BubbleObjectInstance
                        ? integer(object, "routine") + "/" + integer(object, "mappingFrame") + "/" + bool(object, "romRenderOnScreen")
                        : integer(object, "displayFrame") + "/" + integer(object, "frameTimer") + "/" + integer(object, "stateFlags");
                slots.add(nativeObject.getSlotIndex() + ":" + object.getClass().getSimpleName()
                        + ":" + object.getX() + ":" + object.getY() + ":" + state);
            }
            slots.sort(String::compareTo);
            return new Output(rng.getSeed(), List.copyOf(slots));
        }
    }

    private record Output(long seed, List<String> slots) { }

    /** Literal native multiply/fold, independent of GameRng's instruction-by-instruction implementation. */
    private static final class RngOracle {
        long seed;
        int draw() {
            long input = seed == 0 ? 0x2A6D365AL : seed;
            long product = input * 41 & 0xFFFFFFFFL;
            int fold = (int) ((product >>> 16) + (product & 0xFFFF)) & 0xFFFF;
            seed = ((long) fold << 16) | (product & 0xFFFF);
            return (int) ((input & 0xFFFF0000L) | fold);
        }
    }

    public static class Noop extends AbstractObjectInstance implements RewindRecreatable {
        public Noop(int x, int y) { super(new ObjectSpawn(x, y, 0x7F, 0, 0, false, 0), "Slot control"); }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
        @Override public Noop recreateForRewind(RewindRecreateContext context) {
            return new Noop(context.spawn().x(), context.spawn().y());
        }
    }

    private static final class SpawnProbe extends Noop {
        boolean armed;
        int allocatedSlot = -1;
        SpawnProbe(int x, int y) { super(x, y); }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            if (armed) {
                Noop child = services().objectManager().createDynamicObjectWithReservedSlot(
                        () -> new Noop(getX(), getY()));
                allocatedSlot = child == null ? -1 : child.getSlotIndex();
                armed = false;
            }
        }
    }
}
