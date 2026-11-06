package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.game.PlayableEntity;
import com.openggf.level.render.SpriteSlotEmission;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

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

    /** Rebuilds presentation lists without changing submission or simulation state. */
    static void populateBuckets(Collection<ObjectInstance> active, List<ObjectInstance> dynamic,
            NativeSpriteSubmissionQueue queue, IntFunction<AbstractObjectInstance> currentOwner,
            Supplier<IntFunction<SpriteSlotEmission>> externalResolver, StageplayObjectDirection direction,
            List<RenderEntry>[] unified, List<RenderEntry>[] low, List<RenderEntry>[] high) {
        // Clear all buckets
        for (int i = 0; i < unified.length; i++) {
            low[i].clear();
            high[i].clear();
            unified[i].clear();
        }

        // Every accepted address owns its presentation, including ID-zero and
        // orphan words that emit nothing. Picture direction never refunds a call.
        var nativeSlots = new java.util.HashSet<Integer>();
        for (int ordinal = 0; ordinal < queue.size(); ordinal++) nativeSlots.add(queue.slotAt(ordinal));

        // Explicit picture authority belongs to the legacy suffix. Undirected
        // native producers still require their actual DisplaySprite calls.
        for (ObjectInstance instance : active) {
            if (direction != null && direction.addRenderEntries(instance, nativeSlots, unified, low, high)) continue;
            if (!NativeSpriteSubmissionAccess.authoritative(instance)) {
                ObjectRenderParts.addToRenderBuckets(instance, unified, low, high);
            }
        }
        for (ObjectInstance instance : dynamic) {
            if (direction != null && direction.addRenderEntries(instance, nativeSlots, unified, low, high)) continue;
            if (!NativeSpriteSubmissionAccess.authoritative(instance)) {
                ObjectRenderParts.addToRenderBuckets(instance, unified, low, high);
            }
        }
        if (direction != null) direction.addVisualRenderEntries(nativeSlots, unified, low, high);

        // ROM parity: lower sprite-table indices render in front. Objects execute and
        // call Draw_Sprite in slot order, so lower SST slots must be drawn later in
        // painter's-algorithm order. Sort each bucket descending by slot so lower
        // slot indices appear on top.
        for (int i = 0; i < unified.length; i++) {
            low[i].sort(ObjectRenderParts.SLOT_DESCENDING);
            high[i].sort(ObjectRenderParts.SLOT_DESCENDING);
            unified[i].sort(ObjectRenderParts.SLOT_DESCENDING);
        }

        // BuildSprites (_inc/BuildSprites.asm:29-38) walks accepted SST pointers
        // FIFO, resolving current RAM even after deletion/reuse. Cached lists
        // are painter order, reversed by the SAT consumer: append reverse FIFO
        // so native calls form its FIFO prefix. Unported legacy objects retain
        // their old SST ordering as a suffix; that mixed bridge is not evidence
        // of native DisplaySprite call coverage or capacity for those objects.
        for (int ordinal = queue.size() - 1; ordinal >= 0; ordinal--) {
            int slot = queue.slotAt(ordinal);
            AbstractObjectInstance currentObject = currentOwner.apply(slot);
            ObjectInstance occupant = currentObject;
            if (currentObject != null) {
                // ID zero consumes the already admitted queue word but emits no
                // SAT pieces. Do not replace a real cleared owner with a ring.
                if ((currentObject.getLiveObjectId() & 0xFF) == 0) continue;
            } else {
                IntFunction<SpriteSlotEmission> resolver = externalResolver.get();
                if (resolver != null) {
                    SpriteSlotEmission emission = resolver.apply(slot);
                    if (emission != null && emission.slot() == slot) {
                        occupant = ObjectRenderParts.externalSprite(emission);
                    }
                }
            }
            if (occupant != null) {
                ObjectRenderParts.addNativeToRenderBuckets(occupant,
                        queue.bucketAt(ordinal), slot, ordinal,
                        unified, low, high);
            }
        }
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
            return;
        }
        var direction = services == null || services.objectManager() == null
                ? null : services.objectManager().stageplayDirection;
        if (direction != null
                && direction.drawReplacement(entry.instance(), bucket, entry.high(), entry.slot())) return;
        if (services != null && com.openggf.graphics.SpriteMotionInternalAccess.collecting(services.graphicsManager())) {
            ObjectMotionPresentation.draw(entry.instance(), services,
                    () -> ObjectHeadPresentation.draw(entry.instance(), services,
                            () -> appendRenderCommands(entry, commands, bucket)));
        } else ObjectHeadPresentation.draw(entry.instance(), services, () -> appendRenderCommands(entry, commands, bucket));
    }
}
