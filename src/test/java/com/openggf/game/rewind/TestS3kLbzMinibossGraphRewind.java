package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.LbzMinibossBoxKnuxInstance;
import com.openggf.game.sonic3k.objects.LbzMinibossInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.OwnedMocks;
import com.openggf.tests.TestablePlayableSprite;
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
import java.util.TreeMap;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;

class TestS3kLbzMinibossGraphRewind {
    private static final ObjectSpawn BOX_SPAWN =
            new ObjectSpawn(0x3C40, 0x0936, Sonic3kObjectIds.LBZ_MINIBOSS_BOX_KNUX, 0, 0, false, 50);
    private static final ObjectSpawn LEFT_MINIBOSS_SPAWN =
            new ObjectSpawn(0x3C20, 0x0936, Sonic3kObjectIds.LBZ_MINIBOSS, 0, 0, false, 51);
    private static final ObjectSpawn RIGHT_MINIBOSS_SPAWN =
            new ObjectSpawn(0x3C60, 0x0936, Sonic3kObjectIds.LBZ_MINIBOSS, 2, 0, false, 52);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void knucklesMinibossesRestoreFreshWithRestoredBoxParentRefsAndScalars() throws Exception {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        LbzMinibossBoxKnuxInstance sourceBox = objectManager.createDynamicObject(
                () -> new LbzMinibossBoxKnuxInstance(BOX_SPAWN));
        LbzMinibossInstance sourceLeft = createMiniboss(objectManager, LEFT_MINIBOSS_SPAWN, sourceBox);
        LbzMinibossInstance sourceRight = createMiniboss(objectManager, RIGHT_MINIBOSS_SPAWN, sourceBox);
        sourceLeft.forceOpenForTest(0x3C20, 0x0930);
        sourceRight.forceOpenForTest(0x3C60, 0x0940);
        writeIntField(sourceLeft, "hitReactionTimer", 7);
        writeIntField(sourceRight, "waitTimer", 11);

        ObjectRefId boxId = objectId(objectManager, sourceBox);
        ObjectRefId leftId = objectId(objectManager, sourceLeft);
        ObjectRefId rightId = objectId(objectManager, sourceRight);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceLeft);
        LbzMinibossInstance divergentLeft = createMiniboss(
                objectManager,
                new ObjectSpawn(0x3D20, 0x0940, Sonic3kObjectIds.LBZ_MINIBOSS, 0, 0, false, 53),
                sourceBox);
        assertEquals(2, liveObjects(objectManager, LbzMinibossInstance.class).size(),
                "diverge step should leave one captured miniboss plus one unrelated miniboss before restore");

        rewindRegistry.restore(snapshot);

        LbzMinibossBoxKnuxInstance restoredBox =
                objectById(objectManager, LbzMinibossBoxKnuxInstance.class, boxId);
        LbzMinibossInstance restoredLeft =
                objectById(objectManager, LbzMinibossInstance.class, leftId);
        LbzMinibossInstance restoredRight =
                objectById(objectManager, LbzMinibossInstance.class, rightId);
        assertEquals(1, liveObjects(objectManager, LbzMinibossBoxKnuxInstance.class).size(),
                "restore must keep exactly one LBZ Knuckles box parent");
        assertEquals(2, liveObjects(objectManager, LbzMinibossInstance.class).size(),
                "restore must keep exactly the captured LBZ miniboss pair");
        assertNotSame(sourceBox, restoredBox, "restore must recreate the LBZ Knuckles box");
        assertNotSame(sourceLeft, restoredLeft, "restore must recreate the left miniboss");
        assertNotSame(sourceRight, restoredRight, "restore must recreate the right miniboss");
        assertNotSame(divergentLeft, restoredLeft, "restore must drop the divergent miniboss");
        assertSame(restoredBox, readObjectField(restoredLeft, "knucklesFightParent"),
                "left miniboss parent must resolve to the restored box");
        assertSame(restoredBox, readObjectField(restoredRight, "knucklesFightParent"),
                "right miniboss parent must resolve to the restored box");
        assertNotSame(sourceBox, readObjectField(restoredLeft, "knucklesFightParent"),
                "left miniboss must not retain the stale pre-restore box");
        assertEquals(7, restoredLeft.getHitReactionTimerForTest(),
                "left miniboss hit reaction timer must restore from compact state");
        assertEquals(11, restoredRight.getWaitTimerForTest(),
                "right miniboss wait timer must restore from compact state");
    }

    @Test
    void lbzMinibossUsesGenericRecreateWithoutExplicitS3kCodec() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(LbzMinibossInstance.class),
                "LBZ miniboss must restore through RewindRecreatable generic recreate");
    }

    @Test
    void captureFailsWhenKnucklesMinibossParentHasNoRewindIdentity() throws Exception {
        Harness harness = Harness.create();
        LbzMinibossBoxKnuxInstance unmanagedBox = new LbzMinibossBoxKnuxInstance(BOX_SPAWN);
        unmanagedBox.setServices(harness.services());
        LbzMinibossInstance miniboss = createMiniboss(
                harness.objectManager(), LEFT_MINIBOSS_SPAWN, unmanagedBox);
        assertSame(unmanagedBox, readObjectField(miniboss, "knucklesFightParent"),
                "precondition: miniboss parent is outside ObjectManager identity registration");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> registryFor(harness.objectManager()).capture());
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing LBZ miniboss parent identity must fail loudly");
    }

    @RequiresRom(SonicGame.SONIC_3K)
    @ParameterizedTest(name = "{0} attacks, checkpoint {1}")
    @CsvSource({"sonic,257", "tails,257", "sonic,282", "tails,282",
            "sonic,0", "tails,0", "sonic,17", "tails,17", "sonic,128", "tails,128"})
    void progressedPanelRingsRestoreFreshAndReplayRotationDetachAndRetirement(
            String attackingCharacter, int checkpoint) throws Exception {
        try (OwnedMocks mocks = new OwnedMocks()) {
            List<PanelDraw> draws = new ArrayList<>();
            int[] bucket = {0};
            PatternSpriteRenderer renderer = mocks.mock(PatternSpriteRenderer.class);
            ObjectRenderManager renderManager = mocks.mock(ObjectRenderManager.class);
            when(renderer.isReady()).thenReturn(true);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.LBZ_MINIBOSS)).thenReturn(renderer);
            doAnswer(call -> {
                draws.add(new PanelDraw(bucket[0], call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3), call.getArgument(4), call.getArgument(5)));
                return null;
            }).when(renderer).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt());
            Harness harness = Harness.create(renderManager, new GameStateManager());
            ObjectManager manager = harness.objectManager();
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0x3CA0, (short) 0x0980);
            TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x3CC0, (short) 0x0980);
            TestablePlayableSprite attacker = attackingCharacter.equals("tails") ? tails : sonic;
            LbzMinibossInstance source = manager.createDynamicObject(() -> new LbzMinibossInstance(LEFT_MINIBOSS_SPAWN));
            // Actual manager dispatch creates the centre and two six-link rings; no forced-open helper.
            step(manager, sonic, tails);
            assertEquals(13, source.getPanelCountForTest());
            int initialAngle = (int) readObjectField(panelStates(source).get(6), "angle");
            for (int tick = 0; tick < (checkpoint >= 257 ? checkpoint : 350); tick++) {
                step(manager, sonic, tails);
            }
            if (checkpoint == 257) {
                assertEquals(4, source.getPanelRoutineForTest(5, false), "initial timer borrowed into rotate callback");
            } else {
                assertTrue((int) readObjectField(panelStates(source).get(6), "angle") != initialAngle,
                        "uninterrupted arm must have advanced its angle");
            }
            if (checkpoint < 257) {
                for (int hit = 0; hit < 3; hit++) {
                    source.onPlayerAttack(attacker, enemyTouch());
                    if (hit < 2) {
                        for (int tick = 0; tick < 33; tick++) step(manager, sonic, tails);
                    }
                }
                assertEquals(3, source.getCollisionProperty(), "third native player attack arms second-ring detach");
                for (int tick = 0; tick < checkpoint; tick++) step(manager, sonic, tails);
                assertEquals(checkpoint > 0, readObjectField(panelStates(source).get(7), "detached"));
                assertFalse((boolean) readObjectField(panelStates(source).get(7), "deleted"),
                        "checkpoint retains live flight just before lifetime retirement");
            }
            PanelOutput immediate = output(source, draws, bucket);
            assertEquals(14, immediate.draws().size(), "positive control: body, centre, twelve live arms draw immediately");
            assertTrue(immediate.regions().stream().anyMatch(r -> r.collisionFlags() == 0x98),
                    "positive control: uninterrupted live arm collision regions");
            ObjectRefId id = objectId(manager, source);
            List<?> originalHelpers = panelStates(source);
            RewindRegistry registry = registryFor(manager);
            CompositeSnapshot snapshot = registry.capture();
            List<PanelOutput> expected = replay(manager, source, sonic, tails, attacker, checkpoint, draws, bucket);
            assertEquals(3, source.getCollisionProperty());
            assertEquals(8, expected.getLast().draws().size(), "body, centre and first ring survive second-ring retirement");
            assertTrue(panelStates(source).subList(7, 13).stream().allMatch(p -> {
                try { return (boolean) readObjectField(p, "deleted"); }
                catch (Exception e) { throw new AssertionError(e); }
            }), "all six detached links must reach retirement during the bounded replay");
            manager.removeDynamicObject(source);
            LbzMinibossInstance divergent = manager.createDynamicObject(() -> new LbzMinibossInstance(RIGHT_MINIBOSS_SPAWN));
            registry.restore(snapshot);
            LbzMinibossInstance restored = objectById(manager, LbzMinibossInstance.class, id);
            assertNotSame(source, restored);
            assertNotSame(divergent, restored);
            assertEquals(1, liveObjects(manager, LbzMinibossInstance.class).size());
            assertEquals(13, restored.getPanelCountForTest(), "fresh restore must retain progressed rings");
            for (int i = 0; i < originalHelpers.size(); i++) {
                assertNotSame(originalHelpers.get(i), panelStates(restored).get(i), "helper must belong to restored owner");
            }
            assertEquals(immediate, output(restored, draws, bucket), "collision and bucket draw order before any update");
            assertEquals(expected, replay(manager, restored, sonic, tails, attacker, checkpoint, draws, bucket),
                    "bounded manager replay must preserve timers, callbacks, links, fractions and retirement");
        }
    }

    private static List<PanelOutput> replay(ObjectManager manager, LbzMinibossInstance boss,
            TestablePlayableSprite sonic, TestablePlayableSprite tails, TestablePlayableSprite attacker,
            int checkpoint, List<PanelDraw> draws, int[] bucket) throws Exception {
        List<PanelOutput> result = new ArrayList<>();
        for (int tick = 0; tick < 300; tick++) {
            // Early checkpoints precede opening; attack only after the real body collision gate opens.
            if (checkpoint >= 257 && (tick == 60 || tick == 94 || tick == 128)) {
                boss.onPlayerAttack(attacker, enemyTouch());
            }
            step(manager, sonic, tails);
            result.add(output(boss, draws, bucket));
        }
        return result;
    }

    private static void step(ObjectManager manager, TestablePlayableSprite sonic, TestablePlayableSprite tails) {
        manager.update(0x3B80, sonic, List.of(tails), 0, false);
    }

    private static TouchResponseResult enemyTouch() {
        return new TouchResponseResult(0x06, 0x20, 0x20, TouchCategory.ENEMY);
    }

    private record PanelDraw(int bucket, int frame, int x, int y, boolean flipX, boolean flipY, int palette) {}
    private record PanelOutput(List<Map<String, Object>> helpers, Map<String, Object> owner,
            List<TouchResponseProvider.TouchRegion> regions, List<Integer> extraBuckets, List<PanelDraw> draws) {}

    private static PanelOutput output(LbzMinibossInstance boss, List<PanelDraw> draws, int[] bucket) throws Exception {
        draws.clear();
        for (bucket[0] = RenderPriority.MIN; bucket[0] <= RenderPriority.MAX; bucket[0]++) {
            boss.appendRenderCommands(new ArrayList<>(), bucket[0]);
        }
        List<Map<String, Object>> helpers = new ArrayList<>();
        for (Object panel : panelStates(boss)) helpers.add(scalarFields(panel));
        Map<String, Object> owner = scalarFields(boss);
        owner.put("motion", scalarFields(readObjectField(boss, "motion")));
        owner.put("spawn", boss.getSpawn());
        owner.put("slot", boss.getSlotIndex());
        var regions = boss.getMultiTouchRegions();
        return new PanelOutput(helpers, owner, regions == null ? List.of() : List.of(regions),
                Arrays.stream(boss.extraRenderBuckets()).boxed().toList(), List.copyOf(draws));
    }

    private static Map<String, Object> scalarFields(Object target) throws Exception {
        Map<String, Object> result = new TreeMap<>();
        for (Field field : target.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()
                    && (field.getType().isPrimitive() || field.getType().isEnum())) {
                field.setAccessible(true);
                result.put(field.getName(), field.get(target));
            }
        }
        return result;
    }

    private static List<?> panelStates(LbzMinibossInstance boss) throws Exception {
        return StreamSupport.stream(((Iterable<?>) readObjectField(boss, "panels")).spliterator(), false).toList();
    }

    private static LbzMinibossInstance createMiniboss(
            ObjectManager objectManager,
            ObjectSpawn spawn,
            LbzMinibossBoxKnuxInstance parent) {
        return objectManager.createDynamicObject(() -> {
            LbzMinibossInstance miniboss = new LbzMinibossInstance(spawn);
            miniboss.setKnucklesFightParent(parent);
            return miniboss;
        });
    }

    private record Harness(ObjectManager objectManager, ObjectServices services) {
        static Harness create() {
            return create(null, null);
        }

        static Harness create(ObjectRenderManager renderManager, GameStateManager gameState) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public ObjectRenderManager renderManager() { return renderManager; }
                @Override public GameStateManager gameState() { return gameState; }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(),
                    null,
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(camera.getX());
            return new Harness(objectManager, services);
        }
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager,
            Class<T> type,
            ObjectRefId id) {
        return liveObjects(objectManager, type).stream()
                .filter(object -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable()
                        .idFor(object)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }

    private static <T extends ObjectInstance> List<T> liveObjects(ObjectManager objectManager, Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> !object.isDestroyed())
                .sorted(Comparator.comparingInt(ObjectInstance::getX))
                .toList();
    }

    private static Object readObjectField(Object target, String name) throws Exception {
        return field(target, name).get(target);
    }

    private static int readIntField(Object target, String name) throws Exception {
        return field(target, name).getInt(target);
    }

    private static void writeIntField(Object target, String name, int value) throws Exception {
        field(target, name).setInt(target, value);
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0x3B80; }
            @Override public short getY() { return 0x0900; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
