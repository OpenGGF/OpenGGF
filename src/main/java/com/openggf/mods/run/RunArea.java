package com.openggf.mods.run;

import com.openggf.game.ModApi;

/**
 * A fenced part of a run's act: every load of the act (launch, retry and respawn after a death)
 * places the main character at the spawn, and the camera is held inside the bounds on every
 * frame, so the character cannot leave (as a boss arena's camera lock holds it). The engine
 * re-applies the bounds after the act's own level events, which may rewrite them each frame.
 *
 * <p>The character's walls follow the camera as in the shipped games: the left wall is 16 pixels
 * inside {@code minX}, the right wall near the right edge of a native 320-pixel screen at
 * {@code maxX} ({@code maxX + 0x128} in S3K, {@code + 0x168} in S1/S2), and falling below the
 * screen at {@code maxY} ({@code maxY + 0xE0}) is a death. A level object can still move the character (a
 * spring, a conveyor) within those walls.
 *
 * @param spawnX ROM centre X of the main character at each load
 * @param spawnY ROM centre Y of the main character at each load (standing on the floor)
 * @param minX   leftmost camera X (the camera's top-left corner)
 * @param maxX   rightmost camera X
 * @param minY   topmost camera Y
 * @param maxY   bottommost camera Y
 */
@ModApi
public record RunArea(int spawnX, int spawnY, int minX, int maxX, int minY, int maxY) {
    public RunArea {
        if (spawnX < 0 || spawnY < 0 || spawnX > 0x7FFF || spawnY > 0x7FFF) {
            throw new IllegalArgumentException("spawn must be within 0..0x7FFF");
        }
        if (minX < 0 || minY < 0 || maxX < minX || maxY < minY || maxX > 0x7FFF || maxY > 0x7FFF) {
            throw new IllegalArgumentException("camera bounds must satisfy 0 <= min <= max <= 0x7FFF");
        }
    }
}
