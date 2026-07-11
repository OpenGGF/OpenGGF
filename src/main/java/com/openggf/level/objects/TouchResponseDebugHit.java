package com.openggf.level.objects;

@com.openggf.game.ModApi
public record TouchResponseDebugHit(
        int slotIndex,
        ObjectSpawn spawn,
        int objectX,
        int objectY,
        int flags,
        int sizeIndex,
        int width,
        int height,
        TouchCategory category,
        boolean overlapping,
        String debugDetails
) {
}
