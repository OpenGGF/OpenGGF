package com.openggf;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.game.GameServices;
import com.openggf.ghost.GhostFrame;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.debug.DebugColor;
import com.openggf.level.LevelManager;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.ghost.ActiveGhost;
import com.openggf.sprites.ghost.GhostArtBankAllocator;
import com.openggf.sprites.ghost.GhostOpacityCalculator;
import com.openggf.sprites.ghost.GhostTraceRenderer;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hydration-free gameplay ghost renderer (main spec §6.1): consumes resolved
 * render frames straight into PlayerSpriteRenderer.drawFrame — no physics
 * state, no animation manager. Art slots reuse the isolated-DPLC-bank pattern
 * from GhostTraceRenderer.slotFor.
 */
public final class GhostRenderer {
    private static final Logger LOGGER = Logger.getLogger(GhostRenderer.class.getName());
    private static final int FULL_OPACITY_DISTANCE = 32;

    private final Map<String, Slot> slots = new HashMap<>();
    private record Nameplate(String text, int centreX, int top) { }
    private final Map<String, Nameplate> pendingNameplates = new LinkedHashMap<>();
    private final PixelFontTextRenderer nameplateRenderer;

    public GhostRenderer() {
        this(new PixelFontTextRenderer());
    }

    GhostRenderer(PixelFontTextRenderer nameplateRenderer) {
        this.nameplateRenderer = nameplateRenderer;
    }

    public static boolean layerMatches(GhostFrame frame, int bucket, boolean highPriority) {
        return frame.priorityBucket() == bucket && frame.highPriority() == highPriority;
    }

    public void renderForLayer(List<ActiveGhost> ghosts, int bucket, boolean highPriority,
                               int playerCentreX, int playerCentreY) {
        for (ActiveGhost ghost : ghosts) {
            GhostFrame frame = ghost.frame();
            if (!layerMatches(frame, bucket, highPriority)) {
                continue;
            }
            float alpha = GhostOpacityCalculator.alphaForDistance(
                    frame.x() - playerCentreX, frame.y() - playerCentreY, FULL_OPACITY_DISTANCE)
                    * Math.max(0f, ghost.opacityScale());
            if (alpha <= 0.0f) {
                continue;
            }
            Slot slot = slotFor(ghost.slotId(), ghost.characterCode());
            if (slot == null) {
                continue;
            }
            if (frame.mappingFrame() < slot.lastMappingFrame) {
                slot.renderer.invalidateDplcCache(); // backwards jump (retry snap): force fresh DPLC
            }
            slot.lastMappingFrame = frame.mappingFrame();
            GraphicsManager graphics = GameServices.graphics();
            graphics.flushPatternBatch();
            boolean previousHighPriority = graphics.getCurrentSpriteHighPriority();
            graphics.setCurrentSpriteHighPriority(frame.highPriority());
            graphics.beginGhostRenderEffect(alpha);
            graphics.beginPatternBatch();
            try {
                slot.renderer.drawFrame(frame.mappingFrame(), frame.x(), frame.y(),
                        frame.hFlip(), frame.vFlip());
            } finally {
                graphics.flushPatternBatch();
                graphics.endGhostRenderEffect();
                graphics.setCurrentSpriteHighPriority(previousHighPriority);
            }
            var camera = GameServices.cameraOrNull();
            if (camera != null) {
                queueNameplate(ghost, camera.getXWithShake(), camera.getYWithShake());
            }
        }
    }

    // Sprite rendering only prepares commands. Immediate font drawing here would
    // be overwritten when those world commands execute later in the frame.
    void queueNameplate(ActiveGhost ghost, int cameraX, int cameraY) {
        if (ghost.nameplate() == null || ghost.nameplate().isBlank()) {
            return;
        }
        pendingNameplates.put(ghost.slotId(), new Nameplate(ghost.nameplate(),
                ghost.frame().x() - cameraX, ghost.frame().y() - cameraY - 24));
    }

    /** Draw only labels collected by the sprite pass, after the world has finished. */
    void drawNameplates() {
        if (pendingNameplates.isEmpty()) {
            return;
        }
        GraphicsManager graphics = GameServices.graphics();
        graphics.flushScreenSpace();
        drawNameplates(graphics.getProjectionMatrixBuffer());
    }

    void drawNameplates(float[] projection) {
        if (pendingNameplates.isEmpty()) {
            return;
        }
        try {
            nameplateRenderer.setProjectionMatrix(projection);
            nameplateRenderer.beginBatch();
            try {
                for (Nameplate nameplate : pendingNameplates.values()) {
                    int width = nameplateRenderer.measureWidth(nameplate.text(), 0.5f);
                    nameplateRenderer.drawShadowedText(nameplate.text(), nameplate.centreX() - width / 2,
                            nameplate.top(), DebugColor.WHITE, 0.5f);
                }
            } finally {
                nameplateRenderer.endBatch();
            }
        } finally {
            pendingNameplates.clear();
        }
    }

    /** Drop cached art slots (call on level unload / time-attack teardown). */
    public void clearSlots() {
        slots.clear();
        pendingNameplates.clear();
    }

    private Slot slotFor(String slotId, String characterCode) {
        String code = characterCode == null || characterCode.isBlank()
                ? "sonic" : characterCode.trim().toLowerCase(Locale.ROOT);
        String key = slotId + ":" + code;
        Slot existing = slots.get(key);
        if (existing != null) {
            return existing;
        }
        LevelManager level = GameServices.levelOrNull();
        if (level == null || !(level.getGame() instanceof PlayerSpriteArtProvider artProvider)) {
            return null;
        }
        try {
            SpriteArtSet sourceArt = artProvider.loadPlayerSpriteArt(code);
            if (sourceArt == null || sourceArt.isEmpty() || sourceArt.bankSize() <= 0) {
                return null;
            }
            int bankBase = level.reserveSidekickPatternBank(sourceArt.bankSize());
            SpriteArtSet ghostArt = GhostArtBankAllocator.shiftToGhostBank(sourceArt, bankBase);
            Slot slot = new Slot(new PlayerSpriteRenderer(ghostArt));
            slots.put(key, slot);
            return slot;
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to create ghost slot for " + characterCode, e);
            return null;
        }
    }

    private static final class Slot {
        final PlayerSpriteRenderer renderer;
        int lastMappingFrame = -1;

        Slot(PlayerSpriteRenderer renderer) {
            this.renderer = renderer;
        }
    }
}
