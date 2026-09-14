package com.openggf.level.animation;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;

/**
 * Runtime state for a single AniPLC (animated pattern load cue) script entry.
 *
 * <p>Both Sonic 2 and Sonic 3&K use an identical binary format for zone tile
 * animation scripts ({@code zoneanimstart}/{@code zoneanimdecl} macros).
 * This class holds the parsed data and mutable playback state for one script.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code globalDuration} — signed byte; if negative, per-frame durations are used</li>
 *   <li>{@code destTileIndex} — VRAM destination tile index</li>
 *   <li>{@code frameTileIds} — source tile index for each animation frame</li>
 *   <li>{@code frameDurations} — per-frame durations (null when globalDuration >= 0)</li>
 *   <li>{@code tilesPerFrame} — number of consecutive tiles copied per frame</li>
 *   <li>{@code artPatterns} — pre-loaded pattern art tiles</li>
 *   <li>{@code timer} — countdown timer for current frame</li>
 *   <li>{@code frameIndex} — current animation frame index</li>
 * </ul>
 */
public class AniPlcScriptState {
    private final byte globalDuration;
    private final int destTileIndex;
    private final int[] frameTileIds;
    private final int[] frameDurations;
    private final int tilesPerFrame;
    private final Pattern[] artPatterns;
    private int timer;
    private int frameIndex;

    public AniPlcScriptState(byte globalDuration,
                             int destTileIndex,
                             int[] frameTileIds,
                             int[] frameDurations,
                             int tilesPerFrame,
                             Pattern[] artPatterns) {
        this.globalDuration = globalDuration;
        this.destTileIndex = destTileIndex;
        this.frameTileIds = frameTileIds;
        this.frameDurations = frameDurations;
        this.tilesPerFrame = tilesPerFrame;
        this.artPatterns = artPatterns;
        this.timer = 0;
        this.frameIndex = 0;
    }

    /**
     * Advances this script and reports whether it copied a new animation frame.
     * Callers use the result to refresh object-art atlases that reference the
     * same level-pattern objects as the AniPLC destination.
     */
    public boolean tick(Level level, GraphicsManager graphicsManager) {
        return tickSubmission(tileId -> applyFrame(level, graphicsManager, tileId));
    }

    /** Advances ROM counters and submits art to the caller's hardware publication owner. */
    public boolean tickSubmission(java.util.function.IntConsumer submitSourceTile) {
        if (frameTileIds.length == 0 || artPatterns.length == 0) {
            return false;
        }
        if (timer > 0) {
            timer = (timer - 1) & 0xFF;
            return false;
        }

        int currentFrame = frameIndex;
        if (currentFrame >= frameTileIds.length) {
            currentFrame = 0;
            frameIndex = 0;
        }
        frameIndex = currentFrame + 1;

        int duration = globalDuration & 0xFF;
        if (globalDuration < 0 && frameDurations != null) {
            duration = frameDurations[currentFrame];
        }
        timer = duration & 0xFF;

        int tileId = frameTileIds[currentFrame];
        submitSourceTile.accept(tileId);
        return true;
    }

    /** Immutable-by-copy ROM payload for a submitted source frame (packed Genesis nibbles). */
    public byte[] copyFramePayload(int tileId) {
        byte[] bytes = new byte[tilesPerFrame * Pattern.PATTERN_SIZE_IN_ROM];
        for (int tile = 0; tile < tilesPerFrame; tile++) {
            Pattern pattern = artPatterns[tileId + tile];
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x += 2)
                bytes[tile * 32 + y * 4 + x / 2] = (byte) (
                        (pattern.getPixel(x, y) & 15) << 4 | pattern.getPixel(x + 1, y) & 15);
        }
        return bytes;
    }

    public int requiredPatternCount() {
        return destTileIndex + Math.max(tilesPerFrame, 1);
    }

    public int destinationTileIndex() {
        return destTileIndex;
    }

    public int tilesPerFrame() {
        return tilesPerFrame;
    }

    public void prime(Level level, GraphicsManager graphicsManager) {
        if (frameTileIds.length == 0 || artPatterns.length == 0) {
            return;
        }
        applyFrame(level, graphicsManager, frameTileIds[0]);
    }

    /** Returns the current countdown timer (rewind-restore access). */
    public int getTimer() { return timer; }

    /** Returns the current frame index (rewind-restore access). */
    public int getFrameIndex() { return frameIndex; }

    /** Unsigned source duration byte, exposed for deterministic script validation. */
    public int globalDurationForTesting() { return globalDuration & 0xFF; }

    /** Defensive copy of the parsed source-tile sequence. */
    public int[] frameTileIdsForTesting() { return frameTileIds.clone(); }

    /** Restores playback counters from a snapshot (rewind-restore path). */
    public void restoreCounters(int timer, int frameIndex) {
        this.timer = timer;
        this.frameIndex = frameIndex;
    }

    public void applyFrame(Level level, GraphicsManager graphicsManager, int tileId) {
        int maxPatterns = level.getPatternCount();
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        for (int i = 0; i < tilesPerFrame; i++) {
            int srcIndex = tileId + i;
            int destIndex = destTileIndex + i;
            if (srcIndex < 0 || srcIndex >= artPatterns.length) {
                continue;
            }
            if (destIndex < 0 || destIndex >= maxPatterns) {
                continue;
            }
            Pattern dest = level.getPattern(destIndex);
            dest.copyFrom(artPatterns[srcIndex]);
            if (canUpdateTextures) {
                graphicsManager.updatePatternTexture(dest, destIndex);
            }
        }
    }
}
