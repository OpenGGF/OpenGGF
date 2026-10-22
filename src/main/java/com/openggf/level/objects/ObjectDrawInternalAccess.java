package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandGroup;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.render.SpriteSlotEmission;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL11.GL_LINES;

/** Native render-slot composition kept outside the creator ObjectManager contract. */
public final class ObjectDrawInternalAccess {
    private ObjectDrawInternalAccess() { }
    public static void drawBucket(ObjectManager manager, int bucket, GraphicsManager graphics,
                                  List<SpriteSlotEmission> external) {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(ObjectDrawInternalAccess.class);
        if (caller.getClassLoader() != ObjectDrawInternalAccess.class.getClassLoader()) {
            throw new SecurityException("Native slot composition belongs to the engine renderer");
        }
        manager.drawBucketWithSlotEmissions(bucket, graphics, external);
    }

    /** Manager-owned presentation collaborator; all lists and callbacks retain their identity. */
    static final class Drawing {
        private final ObjectManager manager;
        private final GraphicsManager graphicsManager;
        private final Camera camera;
        private final ObjectServices objectServices;
        private final ObjectCallbackRouter objectCallbacks;
        private final List<GLCommand> renderCommands;
        private final List<ObjectRenderParts.RenderEntry>[] lowPriorityBuckets;
        private final List<ObjectRenderParts.RenderEntry>[] highPriorityBuckets;
        private final List<ObjectRenderParts.RenderEntry>[] unifiedBuckets;
        private final Runnable ensureBuckets;
        private final IntFunction<AbstractObjectInstance> currentOwner;
        private final Supplier<IntFunction<SpriteSlotEmission>> externalResolver;

        Drawing(ObjectManager manager, GraphicsManager graphicsManager, Camera camera, ObjectServices objectServices,
                ObjectCallbackRouter objectCallbacks, List<GLCommand> renderCommands,
                List<ObjectRenderParts.RenderEntry>[] lowPriorityBuckets,
                List<ObjectRenderParts.RenderEntry>[] highPriorityBuckets,
                List<ObjectRenderParts.RenderEntry>[] unifiedBuckets, Runnable ensureBuckets,
                IntFunction<AbstractObjectInstance> currentOwner,
                Supplier<IntFunction<SpriteSlotEmission>> externalResolver) {
            this.manager = manager;
            this.graphicsManager = graphicsManager;
            this.camera = camera;
            this.objectServices = objectServices;
            this.objectCallbacks = objectCallbacks;
            this.renderCommands = renderCommands;
            this.lowPriorityBuckets = lowPriorityBuckets;
            this.highPriorityBuckets = highPriorityBuckets;
            this.unifiedBuckets = unifiedBuckets;
            this.ensureBuckets = ensureBuckets;
            this.currentOwner = currentOwner;
            this.externalResolver = externalResolver;
        }

        void populateBuckets(Collection<ObjectInstance> active, List<ObjectInstance> dynamic,
                NativeSpriteSubmissionQueue queue) {
            ObjectRenderParts.populateBuckets(active, dynamic, queue, currentOwner, externalResolver,
                    unifiedBuckets, lowPriorityBuckets, highPriorityBuckets);
        }

        void drawLowPriority() {
            ensureBuckets.run();
            for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
                manager.drawPriorityBucket(bucket, false);
            }
        }

        void drawHighPriority() {
            ensureBuckets.run();
            for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
                manager.drawPriorityBucket(bucket, true);
            }
        }

        void drawPriorityBucket(int bucket, boolean highPriority) {
            ensureBuckets.run();
            int targetBucket = RenderPriority.clamp(bucket);
            int idx = targetBucket - RenderPriority.MIN;
            List<ObjectRenderParts.RenderEntry>[] buckets = highPriority ? highPriorityBuckets : lowPriorityBuckets;
            drawBucketInstancesWithPriority(buckets[idx], targetBucket, graphicsManager);
        }

        void drawUnifiedBucket(int bucket, ObjectManager.ObjectDrawCallback callback) {
            ensureBuckets.run();
            int targetBucket = RenderPriority.clamp(bucket);
            int idx = targetBucket - RenderPriority.MIN;

            drawBucketInstances(unifiedBuckets[idx], targetBucket, callback);
        }

        private void drawBucketInstances(List<ObjectRenderParts.RenderEntry> entries, int bucket,
                                         ObjectManager.ObjectDrawCallback callback) {
            if (entries.isEmpty()) {
                return;
            }

            enableVerticalWrapIfNeeded();
            try {
                renderCommands.clear();
                for (ObjectRenderParts.RenderEntry entry : entries) {
                    if (callback != null) {
                        callback.beforeDraw(entry.instance(), entry.high());
                    }
                    objectCallbacks.run(entry.instance(),
                            () -> ObjectRenderParts.appendRenderCommands(entry, renderCommands, bucket, objectServices));
                }

                if (!renderCommands.isEmpty()) {
                    graphicsManager.enqueueDebugLineState();
                    graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, renderCommands));
                    graphicsManager.enqueueDefaultShaderState();
                }
            } finally {
                graphicsManager.disableVerticalWrapAdjust();
            }
        }

        void drawUnifiedBucketWithPriority(int bucket, GraphicsManager gfx) {
            ensureBuckets.run();
            int idx = RenderPriority.clamp(bucket) - RenderPriority.MIN;

            // The BatchedPatternRenderer uses a single tile-occlusion uniform for
            // the entire batch — it cannot vary per-instance. The helper flushes
            // at every mask transition while retaining SST slot order.
            // (The InstancedPatternRenderer bakes priority per-instance and doesn't
            // need the flush, but it's harmless — empty flushes are no-ops.)

            drawBucketInstancesWithPriority(unifiedBuckets[idx], idx + RenderPriority.MIN, gfx);
        }

        void drawBucketWithSlotEmissions(int bucket, GraphicsManager graphics,
                List<com.openggf.level.render.SpriteSlotEmission> external) {
            ensureBuckets.run();
            int index = RenderPriority.clamp(bucket) - RenderPriority.MIN;
            drawBucketInstancesWithPriority(unifiedBuckets[index], index + RenderPriority.MIN, graphics, external);
        }

        private void drawBucketInstancesWithPriority(List<ObjectRenderParts.RenderEntry> entries, int bucket,
                                                     GraphicsManager gfx) {
            drawBucketInstancesWithPriority(entries, bucket, gfx, List.of());
        }

        void drawBucketInstancesWithPriority(List<ObjectRenderParts.RenderEntry> entries, int bucket,
                GraphicsManager gfx, List<com.openggf.level.render.SpriteSlotEmission> external) {
            if (entries.isEmpty() && external.isEmpty()) return;

            // Native queue entries already resolve reserved ring slots through the live resolver.
            // Legacy anchoring cannot emit those slots twice or sort an explicit FIFO by SST.
            var nativeSlots = new java.util.HashSet<Integer>();
            for (var cachedBucket : unifiedBuckets) {
                for (var entry : cachedBucket) if (entry.nativeSubmission()) nativeSlots.add(entry.slot());
            }
            if (!nativeSlots.isEmpty()) {
                external = external.stream().filter(emission -> !nativeSlots.contains(emission.slot())).toList();
            }

            enableVerticalWrapIfNeeded();
            try {
                renderCommands.clear();
                // Draw_Sprite / Render_Sprites consume SST order; masks affect later entries.
                // The cached lists use reverse order for ordinary painter drawing.
                boolean collectingSat = gfx.isSpriteSatCollectionActive();
                int ordinary = 0, extra = 0;
                while (ordinary < entries.size() || extra < external.size()) {
                    ObjectRenderParts.RenderEntry entry = ordinary < entries.size()
                            ? entries.get(collectingSat ? entries.size() - 1 - ordinary : ordinary) : null;
                    var next = extra < external.size()
                            ? external.get(collectingSat ? extra : external.size() - 1 - extra) : null;
                    if (next != null && (entry == null || (collectingSat
                            ? !entry.nativeSubmission() && next.slot() < entry.slot()
                            : entry.nativeSubmission() || next.slot() > entry.slot()))) {
                        if (gfx.getCurrentSpriteTileOcclusionPaletteMask() != next.tileOcclusionPaletteMask()) {
                            gfx.flushPatternBatch();
                            gfx.setCurrentSpriteTileOcclusionPaletteMask(next.tileOcclusionPaletteMask());
                            gfx.beginPatternBatch();
                        }
                        next.draw().run();
                        extra++;
                        continue;
                    }
                    ordinary++;
                    // A queued pointer owns this slot's presentation even when conversion/reuse
                    // changes the current object's inferred bucket. Preserve every native repeat.
                    if (!entry.nativeSubmission() && nativeSlots.contains(entry.slot())) continue;
                    objectCallbacks.run(entry.instance(), () -> {
                        int mask = ObjectRenderParts.tileOcclusionPaletteMask(entry);
                        if (gfx.getCurrentSpriteTileOcclusionPaletteMask() != mask) {
                            gfx.flushPatternBatch();
                            gfx.setCurrentSpriteTileOcclusionPaletteMask(mask);
                            gfx.beginPatternBatch();
                        }
                        ObjectRenderParts.appendRenderCommands(entry, renderCommands, bucket, objectServices);
                    });
                }

                if (!renderCommands.isEmpty()) {
                    graphicsManager.enqueueDebugLineState();
                    graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, renderCommands));
                    graphicsManager.enqueueDefaultShaderState();
                }
            } finally {
                graphicsManager.disableVerticalWrapAdjust();
            }
        }

        private void enableVerticalWrapIfNeeded() {
            if (camera.isVerticalWrapEnabled()) {
                graphicsManager.enableVerticalWrapAdjust(camera.getVerticalWrapRange(), camera.getY());
            }
        }
    }
}
