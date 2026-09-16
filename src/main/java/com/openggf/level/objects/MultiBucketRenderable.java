package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import java.util.List;

/**
 * An object whose ROM counterpart owns child SST slots with their own
 * {@code priority} words and art-word bit 15, but which the engine draws inline
 * from the owner's render call (boss orbs, panels, box pieces, body parts).
 * <p>
 * The owner stays one instance for update, rewind and slot accounting; the
 * {@link ObjectManager} places it in every bucket it reports and asks it to draw
 * only the parts that belong to the bucket being painted. Within a bucket the
 * parts are ordered by the SST slot {@link #partSlotIndex(int, boolean)} reports,
 * so an owner can place its parts where the ROM's child slots would fall.
 * <p>
 * Part buckets may change at runtime (ROM {@code priority} rewrites); the
 * render-bucket cache re-reads them once per frame.
 */
public interface MultiBucketRenderable extends ObjectInstance {

    /**
     * Buckets other than {@link #getPriorityBucket()} that this object draws into
     * this frame, each a valid {@link RenderPriority} index. Return an empty array
     * when every part currently shares the owner's bucket. The array is read, not
     * retained; a cached constant is fine.
     */
    int[] extraRenderBuckets();

    /** {@link #partTilePriorities(int)} bit: the bucket holds parts with art-word bit 15 clear. */
    int LOW_PARTS = 1;
    /** {@link #partTilePriorities(int)} bit: the bucket holds parts with art-word bit 15 set. */
    int HIGH_PARTS = 2;

    /**
     * Whether the parts drawn in {@code bucket} sit above high-priority tiles (ROM
     * art-word bit 15). For {@link #getPriorityBucket()} this must agree with
     * {@link #isHighPriority()}. When a bucket mixes both, override
     * {@link #partTilePriorities(int)} and {@link #appendRenderCommands(List, int, boolean)}.
     */
    boolean isHighPriority(int bucket);

    /**
     * Which tile-priority classes the parts in {@code bucket} occupy: {@link #LOW_PARTS},
     * {@link #HIGH_PARTS} or both. The manager lists the owner in the matching low/high
     * sub-lists of the bucket and draws each class separately. Defaults to the single
     * class {@link #isHighPriority(int)} reports.
     */
    default int partTilePriorities(int bucket) {
        return isHighPriority(bucket) ? HIGH_PARTS : LOW_PARTS;
    }

    /**
     * Appends only the parts that belong to {@code bucket}. Called once per bucket
     * in {@code {getPriorityBucket()} ∪ extraRenderBuckets()} during the object
     * pass. Implementations route {@link #appendRenderCommands(List)} to
     * {@code appendRenderCommands(commands, getPriorityBucket())}.
     */
    void appendRenderCommands(List<GLCommand> commands, int bucket);

    /**
     * Appends the parts of {@code bucket} whose art-word bit 15 equals
     * {@code highPriority}. The default draws the whole bucket when the flag matches
     * {@link #isHighPriority(int)}; owners that report both classes for a bucket
     * override it to split their parts.
     */
    default void appendRenderCommands(List<GLCommand> commands, int bucket, boolean highPriority) {
        if (highPriority == isHighPriority(bucket)) {
            appendRenderCommands(commands, bucket);
        }
    }

    /**
     * SST slot that orders the parts of {@code bucket} and class against other
     * objects in the same display list (lower slot paints on top, as Draw_Sprite
     * appends in slot order). Defaults to the owner's slot; owners whose ROM
     * children occupy known slots (allocated after the parent) return that slot.
     */
    default int partSlotIndex(int bucket, boolean highPriority) {
        return ObjectRenderParts.slotIndex(this);
    }

    /** Palette-line occlusion mask for parts of {@code bucket} in the {@code highPriority} class. */
    static int tileOcclusionPaletteMask(boolean highPriority) {
        return highPriority ? 0 : 0xF;
    }

    /** Palette-line occlusion mask for the parts in {@code bucket}; see {@link #getTileOcclusionPaletteMask()}. */
    default int getTileOcclusionPaletteMask(int bucket) {
        return tileOcclusionPaletteMask(isHighPriority(bucket));
    }

    /**
     * Packs every bucket's part classes into 16 bits (low bit and high bit per
     * bucket, primary bucket included) so render-bucket caches detect part changes.
     */
    static int extraBucketSignature(MultiBucketRenderable renderable) {
        int signature = classBits(renderable, renderable.getPriorityBucket());
        for (int bucket : renderable.extraRenderBuckets()) {
            signature |= classBits(renderable, bucket);
        }
        return signature;
    }

    /** Eight-bit hash of the part slots so a slot change re-sorts the cached lists. */
    static int partSlotSignature(MultiBucketRenderable renderable) {
        int hash = slotHash(renderable, renderable.getPriorityBucket());
        for (int bucket : renderable.extraRenderBuckets()) {
            hash = hash * 31 + slotHash(renderable, bucket);
        }
        return hash & 0xFF;
    }

    private static int slotHash(MultiBucketRenderable renderable, int bucket) {
        int classes = renderable.partTilePriorities(bucket);
        int hash = 0;
        if ((classes & LOW_PARTS) != 0) {
            hash = hash * 31 + renderable.partSlotIndex(bucket, false);
        }
        if ((classes & HIGH_PARTS) != 0) {
            hash = hash * 31 + renderable.partSlotIndex(bucket, true);
        }
        return hash;
    }

    private static int classBits(MultiBucketRenderable renderable, int bucket) {
        int index = RenderPriority.clamp(bucket) - RenderPriority.MIN;
        return (renderable.partTilePriorities(bucket) & (LOW_PARTS | HIGH_PARTS)) << (index * 2);
    }
}
