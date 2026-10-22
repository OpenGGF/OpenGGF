package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.game.PlayableEntity;
import com.openggf.level.render.SpriteSlotEmission;

import java.util.Comparator;
import java.util.List;

/**
 * Render-bucket entries for {@link ObjectManager}: one per object and
 * tile-priority class in each display list it occupies, each carrying the SST
 * slot that orders it. Ordinary objects contribute one entry; a
 * {@link MultiBucketRenderable} contributes one per bucket and class, with the
 * slot its parts report, so inline-drawn ROM children sort as their own slots.
 */
final class ObjectRenderParts {

    /** One display-list entry: the object, the class it draws here, and its ordering slot. */
    record RenderEntry(ObjectInstance instance, boolean high, int slot,
                       boolean nativeSubmission, int admissionOrdinal) {
        RenderEntry(ObjectInstance instance, boolean high, int slot) {
            this(instance, high, slot, false, -1);
        }
    }

    /** ROM parity: lower SST slots render later and therefore in front. */
    static final Comparator<RenderEntry> SLOT_DESCENDING = (a, b) -> Integer.compare(b.slot(), a.slot());

    private ObjectRenderParts() {
    }

    /** SST slot of an object for render ordering; objects without a slot sort behind everything. */
    static int slotIndex(ObjectInstance instance) {
        return instance instanceof AbstractObjectInstance object ? object.getSlotIndex() : Integer.MAX_VALUE;
    }

    /** Adds the entries of {@code instance} to every bucket list its parts occupy. */
    static void addToRenderBuckets(ObjectInstance instance, List<RenderEntry>[] unified,
                                   List<RenderEntry>[] low, List<RenderEntry>[] high) {
        int primary = RenderPriority.clamp(instance.getPriorityBucket());
        if (!(instance instanceof MultiBucketRenderable parts)) {
            add(new RenderEntry(instance, instance.isHighPriority(), slotIndex(instance)), primary, unified, low, high);
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

    /** One authenticated call uses its captured bucket, never inferred extra buckets. */
    static void addNativeToRenderBuckets(ObjectInstance instance, int bucket, int slot, int ordinal,
                                        List<RenderEntry>[] unified, List<RenderEntry>[] low,
                                        List<RenderEntry>[] high) {
        if (instance instanceof MultiBucketRenderable parts) {
            int classes = parts.partTilePriorities(bucket);
            if ((classes & MultiBucketRenderable.LOW_PARTS) != 0) {
                add(new RenderEntry(instance, false, slot, true, ordinal), bucket, unified, low, high);
            }
            if ((classes & MultiBucketRenderable.HIGH_PARTS) != 0) {
                add(new RenderEntry(instance, true, slot, true, ordinal), bucket, unified, low, high);
            }
        } else {
            add(new RenderEntry(instance, instance.isHighPriority(), slot, true, ordinal),
                    bucket, unified, low, high);
        }
    }

    static ObjectInstance externalSprite(SpriteSlotEmission emission) {
        return new ExternalSprite(emission);
    }

    /** A presentation-cache value only: never registered, updated, or snapshotted. */
    private record ExternalSprite(SpriteSlotEmission emission) implements ObjectInstance {
        @Override public ObjectSpawn getSpawn() {
            return new ObjectSpawn(0, 0, 1, 0, 0, false, 0);
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { emission.draw().run(); }
        @Override public boolean isHighPriority() { return emission.tileOcclusionPaletteMask() == 0; }
        @Override public int getTileOcclusionPaletteMask() { return emission.tileOcclusionPaletteMask(); }
        @Override public int getPriorityBucket() { return emission.bucket(); }
        @Override public boolean isDestroyed() { return false; }
    }

    private static void addParts(MultiBucketRenderable parts, int bucket, List<RenderEntry>[] unified,
                                 List<RenderEntry>[] low, List<RenderEntry>[] high) {
        int classes = parts.partTilePriorities(bucket);
        if ((classes & MultiBucketRenderable.LOW_PARTS) != 0) {
            add(new RenderEntry(parts, false, parts.partSlotIndex(bucket, false)), bucket, unified, low, high);
        }
        if ((classes & MultiBucketRenderable.HIGH_PARTS) != 0) {
            add(new RenderEntry(parts, true, parts.partSlotIndex(bucket, true)), bucket, unified, low, high);
        }
    }

    private static void add(RenderEntry entry, int bucket, List<RenderEntry>[] unified,
                            List<RenderEntry>[] low, List<RenderEntry>[] high) {
        int idx = bucket - RenderPriority.MIN;
        unified[idx].add(entry);
        (entry.high() ? high[idx] : low[idx]).add(entry);
    }

    /** Palette-line occlusion mask for {@code entry}. */
    static int tileOcclusionPaletteMask(RenderEntry entry) {
        return entry.instance() instanceof MultiBucketRenderable
                ? MultiBucketRenderable.tileOcclusionPaletteMask(entry.high())
                : entry.instance().getTileOcclusionPaletteMask();
    }

    /** Draws {@code entry}: the whole object, or only the parts of its bucket and class. */
    static void appendRenderCommands(RenderEntry entry, List<GLCommand> commands, int bucket) {
        if (entry.instance() instanceof MultiBucketRenderable parts) {
            parts.appendRenderCommands(commands, bucket, entry.high());
        } else {
            entry.instance().appendRenderCommands(commands);
        }
    }
    static void appendRenderCommands(RenderEntry entry, List<GLCommand> commands, int bucket, ObjectServices services) {
        if (entry.instance() instanceof ExternalSprite) {
            appendRenderCommands(entry, commands, bucket);
        } else {
            ObjectHeadPresentation.draw(entry.instance(),services,()->appendRenderCommands(entry,commands,bucket));
        }
    }
}
