package com.openggf.level;

/** Selects presentation bounds without changing native camera/player constraints. */
final class LevelBoundsMaskGeometry {
    private LevelBoundsMaskGeometry() { }

    record Bounds(int minX, int maxX) {
        Bounds protectPlayer(int currentMinX, int currentMaxX, int playerLeft, int playerRight) {
            // The ROM gate can still admit movement between current and destination
            // bounds. Widescreen must allow that area to fade back into view while
            // occupied. Each side is independent; expansion is never held back.
            int left = minX > currentMinX && playerLeft < minX && playerRight > currentMinX
                    ? currentMinX : minX;
            int right = maxX < currentMaxX && playerRight > maxX + 320 && playerLeft < currentMaxX + 320
                    ? currentMaxX : maxX;
            return new Bounds(left, right);
        }
    }

    static Bounds select(LevelManager level, int targetMinX, int targetMaxX, int currentMaxX) {
        Bounds bounds = new Bounds(targetMinX, targetMaxX);
        // Keep transient/wrapped inverted domains intact for the renderer's no-op rule.
        if (targetMaxX < targetMinX) return bounds;
        int currentMinX = level.camera.getMinX();
        var leader = level.camera.getFocusedSprite();
        if (leader != null) bounds = protect(bounds, currentMinX, currentMaxX, leader);
        if (level.spriteManager != null) {
            for (var sprite : level.spriteManager.getAllSprites()) {
                if (sprite instanceof com.openggf.sprites.playable.AbstractPlayableSprite player
                        && player != leader && !player.isCpuControlled()) {
                    bounds = protect(bounds, currentMinX, currentMaxX, player);
                }
            }
            // Suppressed followers are not participants; the normal active sidekick
            // list also includes a follower temporarily controlled by player two.
            for (var player : level.spriteManager.getSidekicks()) {
                if (player != leader) bounds = protect(bounds, currentMinX, currentMaxX, player);
            }
        }
        return bounds;
    }

    private static Bounds protect(Bounds bounds, int minX, int maxX,
                                  com.openggf.sprites.playable.AbstractPlayableSprite player) {
        // Use the renderer's body envelope, not the smaller terrain collision radius.
        int radius = Math.max(player.getWidth() / 2, player.getRenderFlagWidthPixels());
        int x = player.getRenderCentreX();
        return bounds.protectPlayer(minX, maxX, x - radius, x + radius);
    }
}
