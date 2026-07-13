package com.openggf.level.render;

/**
 * Describes a contiguous tile load from source art.
 */
@com.openggf.game.ModApi
public record TileLoadRequest(int startTile, int count, int destinationOffset) {
    /** Legacy sequential destination behavior. */
    public TileLoadRequest(int startTile, int count) {
        this(startTile, count, -1);
    }

    public TileLoadRequest {
        if (destinationOffset < -1) {
            throw new IllegalArgumentException("destinationOffset must be -1 or nonnegative");
        }
    }
}
