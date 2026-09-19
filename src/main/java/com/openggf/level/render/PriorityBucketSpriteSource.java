package com.openggf.level.render;

/**
 * A producer of hardware sprites that the ROM appends to one priority level of the sprite table
 * itself, outside the object list.
 *
 * <p>{@code Render_Sprites} walks the sprite table one priority level at a time and a zone may
 * splice its own entries in at the end of a level before the next one starts - Lava Reef's special
 * rock sprites at {@code Render_Sprites_NextLevel} (sonic3k.asm:36386-36390) are the case this
 * exists for. Sprite-table order is behaviour: those entries sit behind everything already in that
 * level and in front of every later one.
 *
 * <p>Engine-internal. A zone feature provider implements it to receive the callback; the level
 * renderer asks the provider it already holds, so no zone name reaches shared render code.
 */
public interface PriorityBucketSpriteSource {

    /**
     * Emits any sprites that belong at the end of {@code bucket}. Called once per priority level
     * per frame, in sprite-table order, with the sprite pass's collection state already set up for
     * that level.
     */
    void appendSpritesAfterPriorityBucket(int bucket);
}
