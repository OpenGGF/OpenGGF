package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.FlipperObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestS2FlipperGraphRewind {
    private static final ObjectSpawn FLIPPER_SPAWN =
            new ObjectSpawn(0x1200, 0x0400, Sonic2ObjectIds.FLIPPER, 0, 0, false, 0x86);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void flipperUsesRewindRecreatableWithoutExplicitDynamicCodec() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(FlipperObjectInstance.class),
                "Flipper must restore through RewindRecreatable");
    }

    @Test
    void flipperPlayerMapsRestoreToCurrentLivePlayerReferences() throws Exception {
        TestablePlayableSprite capturedMain = player("old-sonic");
        TestablePlayableSprite capturedSidekick = player("old-tails");
        Harness harness = Harness.create(capturedMain, List.of(capturedSidekick));
        ObjectManager objectManager = harness.objectManager();
        FlipperObjectInstance source = objectManager.createDynamicObject(
                () -> new FlipperObjectInstance(FLIPPER_SPAWN, "Flipper"));
        seedFlipper(source, capturedMain, capturedSidekick);
        ObjectRefId sourceId = objectId(objectManager, source);
        RewindRegistry registry = registryFor(objectManager);
        CompositeSnapshot snapshot = registry.capture();

        TestablePlayableSprite restoredMain = player("new-sonic");
        TestablePlayableSprite restoredSidekick = player("new-tails");
        harness.setPlayers(restoredMain, List.of(restoredSidekick));

        registry.restore(snapshot);

        FlipperObjectInstance restored = only(objectManager, FlipperObjectInstance.class);
        assertNotSame(source, restored, "restore must recreate the flipper");
        assertEquals(sourceId, objectId(objectManager, restored),
                "flipper dynamic identity must be preserved");
        assertEquals(2, readInt(restored, "mappingFrame"));
        assertTrue(readBoolean(restored, "verticalLaunchTriggered"));
        assertPlayerMaps(restored, restoredMain, restoredSidekick, capturedMain, capturedSidekick);
        // Native state can change after the object snapshot. Recreated Obj86
        // owns movement suppression only, never a saved copy of Obj84's byte.
        restoredSidekick.setPinballMode(true);
        restoredSidekick.setSpindash(true);
        com.openggf.sprites.playable.ObjectControlState.setMovementSuppressionPreservingOwnership(restoredSidekick, true);
        var release = FlipperObjectInstance.class.getDeclaredMethod(
                "releaseLockedPlayer", AbstractPlayableSprite.class);
        release.setAccessible(true);
        release.invoke(restored, restoredSidekick);
        assertFalse(restoredSidekick.isObjectControlSuppressesMovement());
        assertTrue(restoredSidekick.getPinballMode());
        assertTrue(restoredSidekick.getSpindash());
    }

    @ParameterizedTest(name = "cold restore after trigger pass {0}")
    @ValueSource(ints = {6, 12})
    void nonIdleAnimationRestoresColdAndContinuesThroughItsPendingSwitch(int triggerPasses) throws Exception {
        assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(FlipperObjectInstance.class),
                "the production compact schema must use ObjectAnimationStateCodec for this field");
        TestablePlayableSprite main = player("sonic");
        Harness harness = Harness.create(main, List.of());
        ObjectManager objectManager = harness.objectManager();
        harness.services.withPlayerQuery(new ObjectPlayerQuery(harness.camera::getFocusedSprite, List::of));
        SpriteAnimationSet animations = flipperAnimations();
        ObjectArtProvider art = mock(ObjectArtProvider.class);
        when(art.getAnimations(Sonic2ObjectArtKeys.ANIM_FLIPPER)).thenReturn(animations);
        harness.services.renderManager = new ObjectRenderManager(art);
        FlipperObjectInstance source = objectManager.createDynamicObject(
                () -> new FlipperObjectInstance(FLIPPER_SPAWN, "Flipper"));
        objectManager.update(harness.camera.getX(), main, List.of(), 0, false);
        ObjectAnimationState sourceAnimation = animationState(source);
        assertNotNull(sourceAnimation, "the real Flipper update must initialize its animation owner");
        sourceAnimation.setAnimId(1);
        for (int pass = 0; pass < triggerPasses; pass++) {
            objectManager.update(harness.camera.getX(), main, List.of(), 0, false);
        }
        assertTrue(readBoolean(source, "animInitialized"));
        assertEquals(1, sourceAnimation.getAnimId());
        assertEquals(1, readInt(sourceAnimation, "lastAnimId"));
        assertTrue(readInt(sourceAnimation, "frameIndex") > 0);
        assertTrue(readInt(sourceAnimation, "frameTick") > 0);
        assertEquals(triggerPasses == 12 ? 0 : -1, readInt(sourceAnimation, "pendingSwitchAnimId"));
        ObjectAnimationState capturedAnimation = sourceAnimation.copyForRewind();
        int capturedMapping = readInt(source, "mappingFrame");
        ObjectRefId sourceId = objectId(objectManager, source);
        RewindRegistry registry = registryFor(objectManager);
        CompositeSnapshot snapshot = registry.capture();
        List<AnimationStep> expectedForward = advanceAnimation(objectManager, harness.camera, main, source);

        objectManager.removeDynamicObject(source);
        FlipperObjectInstance divergent = objectManager.createDynamicObject(() -> new FlipperObjectInstance(
                new ObjectSpawn(0x1240, 0x0440, Sonic2ObjectIds.FLIPPER, 1, 0, false, 0x87), "Flipper"));
        registry.restore(snapshot);

        FlipperObjectInstance restored = only(objectManager, FlipperObjectInstance.class);
        assertNotSame(source, restored, "restore must recreate the removed source");
        assertNotSame(divergent, restored, "restore must discard the post-snapshot replacement");
        assertEquals(sourceId, objectId(objectManager, restored));
        ObjectAnimationState restoredAnimation = animationState(restored);
        assertNotNull(restoredAnimation, "cold restore must allocate the captured animation owner");
        assertNotSame(sourceAnimation, restoredAnimation);
        assertEquals(capturedAnimation, restoredAnimation,
                "frame, tick, prior animation and pending switch must all survive the real codec");
        assertEquals(capturedMapping, readInt(restored, "mappingFrame"));
        assertTrue(readBoolean(restored, "animInitialized"),
                "the captured lazy-init flag must remain consistent with its captured owner");
        assertSame(animations, findField(ObjectAnimationState.class, "animationSet").get(restoredAnimation),
                "the codec must retain the immutable script resource for future object passes");
        assertEquals(expectedForward, advanceAnimation(objectManager, harness.camera, main, restored),
                "normal object-manager passes must replay the trigger-to-idle continuation identically");
    }

    private static SpriteAnimationSet flipperAnimations() {
        SpriteAnimationSet animations = new SpriteAnimationSet();
        // Unit-test script resources mirror byte_2B43C (s2.asm:58664-58666)
        // and byte_2B43F (s2.asm:58666-58667), including the deferred $FD 0.
        animations.addScript(0, new SpriteAnimationScript(0x0F, List.of(0), SpriteAnimationEndAction.LOOP, 0));
        animations.addScript(1, new SpriteAnimationScript(3, List.of(1, 2, 1), SpriteAnimationEndAction.SWITCH, 0));
        return animations;
    }

    private static List<AnimationStep> advanceAnimation(ObjectManager manager, Camera camera,
            AbstractPlayableSprite main, FlipperObjectInstance flipper) throws Exception {
        List<AnimationStep> steps = new ArrayList<>();
        for (int pass = 0; pass < 8; pass++) {
            manager.update(camera.getX(), main, List.of(), 0, false);
            steps.add(new AnimationStep(readInt(flipper, "mappingFrame"), animationState(flipper).copyForRewind()));
        }
        assertEquals(0, animationState(flipper).getAnimId(), "forward coverage must reach the idle handoff");
        return List.copyOf(steps);
    }

    private record AnimationStep(int mappingFrame, ObjectAnimationState animation) { }

    private static ObjectAnimationState animationState(FlipperObjectInstance flipper) throws Exception {
        return (ObjectAnimationState) findField(FlipperObjectInstance.class, "animationState").get(flipper);
    }

    private static void seedFlipper(
            FlipperObjectInstance flipper,
            AbstractPlayableSprite main,
            AbstractPlayableSprite sidekick) {
        writeInt(flipper, "mappingFrame", 2);
        writeBoolean(flipper, "verticalLaunchTriggered", true);
        mapPut(flipper, "launchCooldown", main, 4);
        mapPut(flipper, "launchCooldown", sidekick, 9);
        mapPut(flipper, "playerFlipperState", main, 1);
        mapPut(flipper, "playerFlipperState", sidekick, 2);
        mapPut(flipper, "lockedPlayerPrevSuppressed", main, true);
        mapPut(flipper, "lockedPlayerPrevSuppressed", sidekick, false);
    }

    private static void assertPlayerMaps(
            FlipperObjectInstance restored,
            AbstractPlayableSprite restoredMain,
            AbstractPlayableSprite restoredSidekick,
            AbstractPlayableSprite oldMain,
            AbstractPlayableSprite oldSidekick) {
        Map<?, ?> cooldowns = readMap(restored, "launchCooldown");
        Map<?, ?> flipperState = readMap(restored, "playerFlipperState");
        Map<?, ?> previousSuppression = readMap(restored, "lockedPlayerPrevSuppressed");
        for (Map<?, ?> map : List.of(cooldowns, flipperState, previousSuppression)) {
            assertFalse(map.containsKey(oldMain), "restored map must not retain stale main-player key");
            assertFalse(map.containsKey(oldSidekick), "restored map must not retain stale sidekick key");
        }
        assertEquals(4, cooldowns.get(restoredMain));
        assertEquals(9, cooldowns.get(restoredSidekick));
        assertEquals(1, flipperState.get(restoredMain));
        assertEquals(2, flipperState.get(restoredSidekick));
        assertEquals(true, previousSuppression.get(restoredMain));
        assertEquals(false, previousSuppression.get(restoredSidekick));
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(objectManager.rewindSnapshottable());
        return registry;
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager identity table must register " + object.getClass().getName());
        return id;
    }

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .toList();
        assertEquals(1, matches.size(), "expected exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static TestablePlayableSprite player(String code) {
        return new TestablePlayableSprite(code, (short) 0x1200, (short) 0x0400);
    }

    private static int readInt(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static boolean readBoolean(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> readMap(Object target, String fieldName) {
        try {
            Object value = findField(target.getClass(), fieldName).get(target);
            return (Map<Object, Object>) value;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static void writeInt(Object target, String fieldName, int value) {
        try {
            findField(target.getClass(), fieldName).setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void writeBoolean(Object target, String fieldName, boolean value) {
        try {
            findField(target.getClass(), fieldName).setBoolean(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void mapPut(Object target, String fieldName, Object key, Object value) {
        readMap(target, fieldName).put(key, value);
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Walk superclass chain.
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static final class Harness {
        private final ObjectManager objectManager;
        private final TestCamera camera;
        private final MutableServices services;

        private Harness(ObjectManager objectManager, TestCamera camera, MutableServices services) {
            this.objectManager = objectManager;
            this.camera = camera;
            this.services = services;
        }

        static Harness create(AbstractPlayableSprite main, List<? extends PlayableEntity> sidekicks) {
            TestCamera camera = new TestCamera();
            camera.setFocusedSprite(main);
            MutableServices services = new MutableServices(camera, List.copyOf(sidekicks));
            ObjectManager objectManager = new ObjectManager(
                    List.of(),
                    null,
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            services.objectManager = objectManager;
            objectManager.reset(camera.getX());
            objectManager.setRewindInPlaceRestoreEnabledForTest(false);
            return new Harness(objectManager, camera, services);
        }

        ObjectManager objectManager() {
            return objectManager;
        }

        void setPlayers(AbstractPlayableSprite main, List<? extends PlayableEntity> sidekicks) {
            camera.setFocusedSprite(main);
            services.sidekicks = List.copyOf(sidekicks);
        }
    }

    private static final class MutableServices extends StubObjectServices {
        private ObjectManager objectManager;
        private ObjectRenderManager renderManager;
        private final Camera camera;
        private List<? extends PlayableEntity> sidekicks;

        private MutableServices(Camera camera, List<? extends PlayableEntity> sidekicks) {
            this.camera = camera;
            this.sidekicks = sidekicks;
        }

        @Override public ObjectManager objectManager() { return objectManager; }
        @Override public ObjectRenderManager renderManager() { return renderManager; }
        @Override public Camera camera() { return camera; }
        @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        @Override public List<PlayableEntity> sidekicks() { return List.copyOf(sidekicks); }
    }

    private static final class TestCamera extends Camera {
        private AbstractPlayableSprite focusedSprite;

        @Override public void setFocusedSprite(AbstractPlayableSprite sprite) { focusedSprite = sprite; }
        @Override public AbstractPlayableSprite getFocusedSprite() { return focusedSprite; }
        @Override public short getX() { return 0x1000; }
        @Override public short getY() { return 0x0300; }
        @Override public short getWidth() { return 320; }
        @Override public short getHeight() { return 224; }
        @Override public boolean isVerticalWrapEnabled() { return false; }
    }
}
