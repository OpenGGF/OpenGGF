package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MultiBucketRenderable}: an owner whose ROM children carry their own
 * {@code priority} words is listed in every bucket its parts occupy, is asked to
 * draw only that bucket's parts, reports that bucket's art-word bit 15, and a
 * runtime part rewrite (ROM {@code move.w #$380,priority(a1)} style) is picked up
 * by the lazy render-bucket refresh.
 */
class TestObjectManagerMultiBucketRenderable {

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
    void ownerDrawsEachPartInItsOwnBucketWithItsOwnTilePriority() {
        ObjectManager manager = newObjectManager();
        Owner owner = new Owner(5, false, new int[] {2}, true);
        Plain plainInFront = new Plain(2, false);
        manager.addDynamicObjectAtSlot(owner, 40);
        manager.addDynamicObjectAtSlot(plainInFront, 41);

        List<String> order = collect(manager);

        // Painter order: bucket 5 (body) first, then bucket 2 where the part sits
        // behind the lower-slot plain object... the plain object has the higher slot,
        // so it is painted first within bucket 2 and the part (owner slot 40) on top.
        assertEquals(List.of("body@5:low", "plain@2:low", "part@2:high"), order);
        assertEquals(List.of(5, 2), owner.bucketsAsked);
    }

    @Test
    void partBucketRewriteIsDetectedByTheLazyRefresh() {
        ObjectManager manager = newObjectManager();
        Owner owner = new Owner(5, false, new int[] {2}, false);
        manager.addDynamicObjectAtSlot(owner, 40);
        assertEquals(List.of("body@5:low", "part@2:low"), collect(manager));

        // ROM-style runtime rewrite of the child's priority word, no add/remove.
        owner.extraBuckets = new int[] {7};
        manager.refreshRenderBucketsIfChanged();
        assertEquals(List.of("part@7:low", "body@5:low"), collect(manager));

        // Same buckets, but the part's art-word bit flips: also a cache input.
        owner.extraHigh = true;
        manager.refreshRenderBucketsIfChanged();
        assertEquals(List.of("part@7:high", "body@5:low"), collect(manager));
    }

    @Test
    void bucketWithLowAndHighPartsIsDrawnOncePerClass() {
        ObjectManager manager = newObjectManager();
        Owner owner = new Owner(5, true, new int[] {5}, false);
        owner.mixedBucket = 5; // body high, a floor-phase part low, both in list 5
        manager.addDynamicObjectAtSlot(owner, 40);
        assertEquals(List.of("body@5:low", "body@5:high"), collect(manager));
        assertEquals(List.of("5:low", "5:high"), owner.classesAsked);
    }

    @Test
    void partsSortByTheSlotTheyReport() {
        ObjectManager manager = newObjectManager();
        Owner owner = new Owner(5, false, new int[] {2}, false);
        owner.partSlot = 60; // ROM child allocated after the plain object below
        Plain plain = new Plain(2, false);
        manager.addDynamicObjectAtSlot(owner, 40);
        manager.addDynamicObjectAtSlot(plain, 50);
        // Bucket 2: the part (slot 60) paints first, the plain object (slot 50) on top.
        assertEquals(List.of("body@5:low", "part@2:low", "plain@2:low"), collect(manager));

        owner.partSlot = 41; // slot change alone must re-sort the cached list
        manager.refreshRenderBucketsIfChanged();
        assertEquals(List.of("body@5:low", "plain@2:low", "part@2:low"), collect(manager));
    }

    @Test
    void partSharingTheOwnersBucketIsListedOnce() {
        ObjectManager manager = newObjectManager();
        Owner owner = new Owner(5, false, new int[] {5}, false);
        manager.addDynamicObjectAtSlot(owner, 40);
        assertEquals(List.of("body@5:low"), collect(manager));
        assertEquals(List.of(5), owner.bucketsAsked);
    }

    private static List<String> collect(ObjectManager manager) {
        List<String> order = new ArrayList<>();
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            int drawn = bucket;
            manager.drawUnifiedBucket(bucket, (instance, high) -> {
                String name = instance instanceof Owner o
                        ? (drawn == o.getPriorityBucket() ? "body" : "part") : "plain";
                order.add(name + "@" + drawn + ":" + (high ? "high" : "low"));
            });
        }
        return order;
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

    private static final class Owner extends AbstractObjectInstance implements MultiBucketRenderable {
        private final int bodyBucket;
        private final boolean bodyHigh;
        private int[] extraBuckets;
        private boolean extraHigh;
        private int mixedBucket = -1;
        private int partSlot = -1;
        private final List<Integer> bucketsAsked = new ArrayList<>();
        private final List<String> classesAsked = new ArrayList<>();

        private Owner(int bodyBucket, boolean bodyHigh, int[] extraBuckets, boolean extraHigh) {
            super(new ObjectSpawn(0x100, 0x100, 0x01, 0, 0, false, 0), "multi-bucket-owner");
            this.bodyBucket = bodyBucket;
            this.bodyHigh = bodyHigh;
            this.extraBuckets = extraBuckets;
            this.extraHigh = extraHigh;
        }

        @Override
        public int getPriorityBucket() {
            return bodyBucket;
        }

        @Override
        public boolean isHighPriority() {
            return bodyHigh;
        }

        @Override
        public int[] extraRenderBuckets() {
            return extraBuckets;
        }

        @Override
        public boolean isHighPriority(int bucket) {
            return bucket == bodyBucket ? bodyHigh : extraHigh;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            appendRenderCommands(commands, getPriorityBucket());
        }

        @Override
        public int partSlotIndex(int bucket, boolean highPriority) {
            return bucket != bodyBucket && partSlot >= 0 ? partSlot
                    : MultiBucketRenderable.super.partSlotIndex(bucket, highPriority);
        }

        @Override
        public int partTilePriorities(int bucket) {
            return bucket == mixedBucket ? LOW_PARTS | HIGH_PARTS
                    : MultiBucketRenderable.super.partTilePriorities(bucket);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands, int bucket) {
            bucketsAsked.add(bucket);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands, int bucket, boolean highPriority) {
            classesAsked.add(bucket + ":" + (highPriority ? "high" : "low"));
            MultiBucketRenderable.super.appendRenderCommands(commands, bucket, highPriority);
        }
    }

    private static final class Plain extends AbstractObjectInstance {
        private final int bucket;
        private final boolean high;

        private Plain(int bucket, boolean high) {
            super(new ObjectSpawn(0x120, 0x100, 0x01, 0, 0, false, 0), "plain");
            this.bucket = bucket;
            this.high = high;
        }

        @Override
        public int getPriorityBucket() {
            return bucket;
        }

        @Override
        public boolean isHighPriority() {
            return high;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
