package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import java.util.List;

/**
 * Render-bucket helpers for {@link MultiBucketRenderable} owners, kept out of
 * {@link ObjectManager}: which buckets and tile-priority classes an object occupies
 * and how its parts are drawn for a given bucket and class.
 */
final class ObjectRenderParts {

    private ObjectRenderParts() {
    }

    /** Adds {@code instance} to every bucket list its parts occupy. */
    static void addToRenderBuckets(ObjectInstance instance, List<ObjectInstance>[] unified,
                                   List<ObjectInstance>[] low, List<ObjectInstance>[] high) {
        int primary = RenderPriority.clamp(instance.getPriorityBucket());
        if (!(instance instanceof MultiBucketRenderable parts)) {
            int idx = primary - RenderPriority.MIN;
            unified[idx].add(instance);
            (instance.isHighPriority() ? high[idx] : low[idx]).add(instance);
            return;
        }
        addParts(parts, primary, unified, low, high);
        for (int extra : parts.extraRenderBuckets()) {
            int bucket = RenderPriority.clamp(extra);
            if (bucket != primary) {
                addParts(parts, bucket, unified, low, high);
            }
        }
    }

    private static void addParts(MultiBucketRenderable parts, int bucket, List<ObjectInstance>[] unified,
                                 List<ObjectInstance>[] low, List<ObjectInstance>[] high) {
        int idx = bucket - RenderPriority.MIN;
        int classes = parts.partTilePriorities(bucket);
        unified[idx].add(parts);
        if ((classes & MultiBucketRenderable.LOW_PARTS) != 0) {
            low[idx].add(parts);
        }
        if ((classes & MultiBucketRenderable.HIGH_PARTS) != 0) {
            high[idx].add(parts);
        }
    }

    /** Tile-priority classes {@code instance} draws in {@code bucket}: exactly one for ordinary objects. */
    static int tilePriorityClasses(ObjectInstance instance, int bucket) {
        if (instance instanceof MultiBucketRenderable parts) {
            return parts.partTilePriorities(bucket);
        }
        return instance.isHighPriority() ? MultiBucketRenderable.HIGH_PARTS : MultiBucketRenderable.LOW_PARTS;
    }

    /** True when {@code instance} has parts of the {@code high} class in {@code bucket}. */
    static boolean drawsClass(ObjectInstance instance, int bucket, boolean high) {
        int wanted = high ? MultiBucketRenderable.HIGH_PARTS : MultiBucketRenderable.LOW_PARTS;
        return (tilePriorityClasses(instance, bucket) & wanted) != 0;
    }

    /** Palette-line occlusion mask for the parts of {@code instance} in the {@code high} class. */
    static int tileOcclusionPaletteMask(ObjectInstance instance, boolean high) {
        return instance instanceof MultiBucketRenderable
                ? MultiBucketRenderable.tileOcclusionPaletteMask(high)
                : instance.getTileOcclusionPaletteMask();
    }

    /** Draws the parts of {@code instance} in {@code bucket} and class; whole object for ordinary ones. */
    static void appendRenderCommands(ObjectInstance instance, List<GLCommand> commands, int bucket, boolean high) {
        if (instance instanceof MultiBucketRenderable parts) {
            parts.appendRenderCommands(commands, bucket, high);
        } else {
            instance.appendRenderCommands(commands);
        }
    }
}
