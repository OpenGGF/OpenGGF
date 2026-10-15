package com.openggf.level.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;

import java.util.List;

/**
 * A fixed-size pattern bank that can be updated via tile load requests.
 */
public class DynamicPatternBank {
    private final Pattern[] patterns;
    private final int basePatternIndex;
    private GraphicsManager graphicsManager;
    private boolean cached;

    public DynamicPatternBank(int basePatternIndex, int capacity) {
        this.basePatternIndex = basePatternIndex;
        this.patterns = new Pattern[Math.max(0, capacity)];
        for (int i = 0; i < this.patterns.length; i++) {
            this.patterns[i] = new Pattern();
        }
    }

    public Pattern[] getPatterns() {
        return patterns;
    }

    public int getBasePatternIndex() {
        return basePatternIndex;
    }

    public void ensureCached(GraphicsManager graphicsManager) {
        if (cached || patterns.length == 0) {
            this.graphicsManager = graphicsManager;
            return;
        }
        if (graphicsManager == null || !graphicsManager.isGlInitialized()) {
            this.graphicsManager = graphicsManager;
            return;
        }
        this.graphicsManager = graphicsManager;
        for (int i = 0; i < patterns.length; i++) {
            graphicsManager.cachePatternTexture(patterns[i], basePatternIndex + i);
        }
        cached = true;
    }

    public void applyRequests(List<TileLoadRequest> requests, Pattern[] source) {
        if (requests == null || source == null) {
            return;
        }

        if (cached && graphicsManager != null) {
            graphicsManager.beginPatternAtlasBatch();
        }
        try {
            int dstIndex = 0;
            for (TileLoadRequest request : requests) {
                int count = Math.max(0, request.count());
                int startTile = Math.max(0, request.startTile());
                if (request.destinationOffset() >= 0) {
                    dstIndex = request.destinationOffset();
                }
                for (int i = 0; i < count; i++) {
                    if (dstIndex >= patterns.length) {
                        return;
                    }
                    int srcIndex = startTile + i;
                    if (srcIndex >= 0 && srcIndex < source.length) {
                        patterns[dstIndex].copyFrom(source[srcIndex]);
                        if (cached && graphicsManager != null) {
                            graphicsManager.updatePatternTexture(patterns[dstIndex], basePatternIndex + dstIndex);
                        }
                    }
                    dstIndex++;
                }
            }
        } finally {
            if (cached && graphicsManager != null) {
                graphicsManager.endPatternAtlasBatch();
            }
        }
    }

    /** Copies every resident tile; the copies share no storage with this bank. */
    public Pattern[] copyResident() {
        Pattern[] copy = new Pattern[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            copy[i] = new Pattern();
            copy[i].copyFrom(patterns[i]);
        }
        return copy;
    }

    /**
     * Replaces the whole resident bank (CPU tiles and, when cached, their GPU
     * textures) without a load request. Restoring VRAM is not a DMA transfer.
     */
    public void restoreResident(Pattern[] resident) {
        if (resident == null || resident.length != patterns.length) {
            throw new IllegalArgumentException("Resident bank restore requires " + patterns.length + " tiles");
        }
        for (int i = 0; i < patterns.length; i++) patterns[i].copyFrom(resident[i]);
        refreshTextures();
    }

    /** Re-uploads every cached tile, e.g. after another bank shared these atlas indices. */
    public void refreshTextures() {
        if (!cached || graphicsManager == null) return;
        graphicsManager.beginPatternAtlasBatch();
        try {
            for (int i = 0; i < patterns.length; i++) {
                graphicsManager.updatePatternTexture(patterns[i], basePatternIndex + i);
            }
        } finally {
            graphicsManager.endPatternAtlasBatch();
        }
    }

    /**
     * Applies an already-decided runtime art state. Lifecycle identity,
     * duplicate suppression, and completion are owned by the production
     * dynamic-art service rather than this presentation bank.
     */
    public void consumeRuntimeArtState(
            List<TileLoadRequest> requests,
            Pattern[] source) {
        applyRequests(requests, source);
    }
}
