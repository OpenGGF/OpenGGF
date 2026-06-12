package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the lazy render-bucket path that replaced the unconditional per-frame
 * {@code invalidateRenderBuckets()} calls in {@code LevelRenderer}: the cached
 * buckets must only rebuild when their inputs change, and a lazy rebuild after
 * an untracked priority mutation must produce exactly the order an eager
 * rebuild would.
 *
 * <p>{@code LevelRenderer} itself cannot be constructed headlessly (it needs a
 * live {@code LevelManager} + GL state), so bucket-order equivalence is
 * asserted directly against {@link ObjectManager} and {@link SpriteManager}
 * — the same managers the renderer pass draws from — plus source-level
 * assertions that the renderer calls the change-detecting refresh hook.
 */
class TestLevelRendererBucketInvalidation {

    private GraphicsManager graphicsManager;

    @BeforeEach
    void setUp() {
        graphicsManager = GraphicsManager.getInstance();
        graphicsManager.initHeadless();
    }

    @AfterEach
    void tearDown() {
        graphicsManager.resetState();
    }

    @Test
    void rendererPassesRevalidateInsteadOfUnconditionallyInvalidating() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelRenderer.java"));

        assertFalse(source.contains(".invalidateRenderBuckets()"),
                "LevelRenderer must not unconditionally invalidate render buckets every frame.");
        assertTrue(source.contains("spriteManager.refreshRenderBucketsIfChanged()")
                        && source.contains("objectManager.refreshRenderBucketsIfChanged()"),
                "LevelRenderer must re-validate cached buckets against live priority state "
                        + "before drawing the sprite/object pass.");
    }

    @Test
    void objectManagerLazyRebuildMatchesEagerOrderAcrossMutations() throws Exception {
        ObjectManager manager = newObjectManager();

        TestObject first = new TestObject(0x100, 2, false);
        TestObject second = new TestObject(0x120, 2, false);
        TestObject third = new TestObject(0x140, 3, true);
        manager.addDynamicObjectAtSlot(first, 40);
        manager.addDynamicObjectAtSlot(second, 41);
        manager.addDynamicObjectAtSlot(third, 42);

        List<DrawnEntry> initial = collectObjectOrder(manager);
        assertEquals(3, initial.size());
        assertEquals(eagerObjectOrder(manager), initial);

        // No mutation: refresh must not mark the cached buckets dirty.
        manager.refreshRenderBucketsIfChanged();
        assertFalse(bucketsDirty(manager, ObjectManager.class),
                "Refresh without any priority/membership change must keep cached buckets valid.");

        // Untracked priority mutation: refresh must detect it and the lazy
        // rebuild must match a forced eager rebuild.
        second.priorityBucket = 5;
        manager.refreshRenderBucketsIfChanged();
        assertTrue(bucketsDirty(manager, ObjectManager.class),
                "Refresh must detect a direct priority-bucket mutation.");
        List<DrawnEntry> afterBucketChange = collectObjectOrder(manager);
        assertNotEquals(initial, afterBucketChange);
        assertEquals(eagerObjectOrder(manager), afterBucketChange);

        // Untracked high-priority flip.
        third.highPriority = false;
        manager.refreshRenderBucketsIfChanged();
        List<DrawnEntry> afterPriorityFlip = collectObjectOrder(manager);
        assertNotEquals(afterBucketChange, afterPriorityFlip);
        assertEquals(eagerObjectOrder(manager), afterPriorityFlip);

        // Insertion (add path marks dirty itself).
        TestObject fourth = new TestObject(0x160, 2, false);
        manager.addDynamicObjectAtSlot(fourth, 39);
        List<DrawnEntry> afterInsert = collectObjectOrder(manager);
        assertTrue(containsDrawable(afterInsert, fourth));
        assertEquals(eagerObjectOrder(manager), afterInsert);

        // Removal (remove path marks dirty itself).
        manager.removeDynamicObject(first);
        List<DrawnEntry> afterRemove = collectObjectOrder(manager);
        assertFalse(containsDrawable(afterRemove, first));
        assertEquals(3, afterRemove.size());
        assertEquals(eagerObjectOrder(manager), afterRemove);
    }

    @Test
    void spriteManagerLazyRebuildMatchesEagerOrderAcrossMutations() throws Exception {
        SpriteManager manager = new SpriteManager();
        manager.clearAllSprites();

        AbstractPlayableSprite player = playableSprite("player", 2, false, false);
        AbstractPlayableSprite sidekick = playableSprite("sidekick", 2, false, true);
        manager.addSprite(player);
        manager.addSprite(sidekick);

        List<DrawnEntry> initial = collectSpriteOrder(manager);
        assertEquals(2, initial.size());
        assertEquals(eagerSpriteOrder(manager), initial);

        manager.refreshRenderBucketsIfChanged();
        assertFalse(bucketsDirty(manager, SpriteManager.class),
                "Refresh without any priority/membership change must keep cached buckets valid.");

        // Untracked priority mutation (e.g. plane switcher calling setPriorityBucket).
        when(player.getPriorityBucket()).thenReturn(5);
        manager.refreshRenderBucketsIfChanged();
        assertTrue(bucketsDirty(manager, SpriteManager.class),
                "Refresh must detect a playable-sprite priority mutation.");
        List<DrawnEntry> afterBucketChange = collectSpriteOrder(manager);
        assertNotEquals(initial, afterBucketChange);
        assertEquals(eagerSpriteOrder(manager), afterBucketChange);

        // Untracked high-priority flip (e.g. hurt state).
        when(sidekick.isHighPriority()).thenReturn(true);
        manager.refreshRenderBucketsIfChanged();
        List<DrawnEntry> afterPriorityFlip = collectSpriteOrder(manager);
        assertEquals(eagerSpriteOrder(manager), afterPriorityFlip);

        // Insertion and removal mark dirty through the add/remove paths.
        AbstractPlayableSprite extra = playableSprite("extra", 3, true, false);
        manager.addSprite(extra);
        List<DrawnEntry> afterInsert = collectSpriteOrder(manager);
        assertTrue(containsDrawable(afterInsert, extra));
        assertEquals(eagerSpriteOrder(manager), afterInsert);

        manager.removeSprite("player");
        List<DrawnEntry> afterRemove = collectSpriteOrder(manager);
        assertFalse(containsDrawable(afterRemove, player));
        assertEquals(eagerSpriteOrder(manager), afterRemove);

        manager.clearAllSprites();
    }

    private ObjectManager newObjectManager() {
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);
        return new ObjectManager(List.of(), null, 0, null, null,
                graphicsManager, camera, new StubObjectServices());
    }

    private record DrawnEntry(Object drawable, boolean highPriority) {
    }

    private static List<DrawnEntry> collectObjectOrder(ObjectManager manager) {
        List<DrawnEntry> order = new ArrayList<>();
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            manager.drawUnifiedBucket(bucket,
                    (instance, highPriority) -> order.add(new DrawnEntry(instance, highPriority)));
        }
        return order;
    }

    private static List<DrawnEntry> eagerObjectOrder(ObjectManager manager) {
        manager.invalidateRenderBuckets();
        return collectObjectOrder(manager);
    }

    private static List<DrawnEntry> collectSpriteOrder(SpriteManager manager) {
        List<DrawnEntry> order = new ArrayList<>();
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            manager.drawUnifiedBucket(bucket,
                    (sprite, highPriority) -> order.add(new DrawnEntry(sprite, highPriority)));
        }
        return order;
    }

    private static List<DrawnEntry> eagerSpriteOrder(SpriteManager manager) {
        manager.invalidateRenderBuckets();
        return collectSpriteOrder(manager);
    }

    private static boolean containsDrawable(List<DrawnEntry> entries, Object drawable) {
        return entries.stream().anyMatch(entry -> entry.drawable() == drawable);
    }

    private static AbstractPlayableSprite playableSprite(
            String code, int bucket, boolean highPriority, boolean cpuControlled) {
        AbstractPlayableSprite sprite = mock(AbstractPlayableSprite.class);
        when(sprite.getCode()).thenReturn(code);
        when(sprite.getPriorityBucket()).thenReturn(bucket);
        when(sprite.isHighPriority()).thenReturn(highPriority);
        when(sprite.isCpuControlled()).thenReturn(cpuControlled);
        return sprite;
    }

    private static boolean bucketsDirty(Object manager, Class<?> type) throws Exception {
        Field field = type.getDeclaredField("bucketsDirty");
        field.setAccessible(true);
        return field.getBoolean(manager);
    }

    private static final class TestObject extends AbstractObjectInstance {
        private int priorityBucket;
        private boolean highPriority;

        private TestObject(int x, int priorityBucket, boolean highPriority) {
            super(new ObjectSpawn(x, 0x100, 0x01, 0, 0, false, 0), "bucket-test-object");
            this.priorityBucket = priorityBucket;
            this.highPriority = highPriority;
        }

        @Override
        public int getPriorityBucket() {
            return priorityBucket;
        }

        @Override
        public boolean isHighPriority() {
            return highPriority;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
