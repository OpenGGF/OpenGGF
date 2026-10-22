package com.openggf;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.game.GameServices;
import com.openggf.ghost.GhostFrame;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.ghost.ActiveGhost;
import com.openggf.sprites.ghost.GhostArtBankAllocator;
import com.openggf.sprites.ghost.GhostOpacityCalculator;
import com.openggf.sprites.ghost.GhostTraceRenderer;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
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

    /** Widest overhead name: eleven compact-font glyphs, so a crowded pack stays readable. */
    static final int NAMEPLATE_TEXT_WIDTH = 65;
    static final int NAMEPLATE_HEIGHT = 9;
    /** Clearance between a standing character's centre and the bottom of its name tag. */
    private static final int NAMEPLATE_LIFT = 22;
    private static final int NAMEPLATE_FILL_RGB = 0x101828;
    // Denser than the HUD panels: scenery showing through 5x7 glyphs breaks them up.
    private static final int NAMEPLATE_FILL_ALPHA = 0xD8;
    private static final int NAMEPLATE_TEXT_RGB = 0xF5F8FF;

    private final Map<String, Slot> slots = new HashMap<>();
    private record Nameplate(String text, int centreX, int bottom, float alpha) { }
    /** A placed, clipped overhead tag in logical viewport pixels. */
    record NameplateTag(String text, int x, int y, int width, int height, float alpha) { }
    private final Map<String, Nameplate> pendingNameplates = new LinkedHashMap<>();

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
                queueNameplate(ghost, camera.getXWithShake(), camera.getYWithShake(), alpha);
            }
        }
    }

    // Sprite rendering only prepares commands. Immediate drawing here would be
    // overwritten when those world commands execute later in the frame.
    void queueNameplate(ActiveGhost ghost, int cameraX, int cameraY, float alpha) {
        if (ghost.nameplate() == null || ghost.nameplate().isBlank()) {
            return;
        }
        pendingNameplates.put(ghost.slotId(), new Nameplate(ghost.nameplate(),
                ghost.frame().x() - cameraX, ghost.frame().y() - cameraY - NAMEPLATE_LIFT, alpha));
    }

    /** Draw only labels collected by the sprite pass, after the world and before the host HUD. */
    void drawNameplates(PixelCanvas canvas) {
        if (pendingNameplates.isEmpty()) {
            return;
        }
        try {
            for (NameplateTag tag : layoutNameplates(canvas.width(), canvas.height())) {
                int fillAlpha = Math.round(NAMEPLATE_FILL_ALPHA * tag.alpha());
                int textAlpha = Math.round(0xFF * tag.alpha());
                canvas.fill(tag.x(), tag.y(), tag.width(), tag.height(), (fillAlpha << 24) | NAMEPLATE_FILL_RGB);
                CompactFont.draw(canvas, tag.text(), tag.x() + 2, tag.y() + 1, 1,
                        (textAlpha << 24) | NAMEPLATE_TEXT_RGB);
            }
        } finally {
            pendingNameplates.clear();
        }
    }

    /**
     * Places pending tags above their ghosts. Lower tags keep their anchor and higher
     * overlapping tags step upward, so a bunched pack reads as a short stack, not a smear.
     */
    List<NameplateTag> layoutNameplates(int viewportWidth, int viewportHeight) {
        List<Nameplate> visible = new ArrayList<>();
        for (Nameplate nameplate : pendingNameplates.values()) {
            if (nameplate.alpha() > 0f && nameplate.centreX() >= 0 && nameplate.centreX() < viewportWidth
                    && nameplate.bottom() > 0 && nameplate.bottom() - NAMEPLATE_HEIGHT < viewportHeight) {
                visible.add(nameplate);
            }
        }
        visible.sort(Comparator.comparingInt(Nameplate::bottom).reversed()
                .thenComparingInt(Nameplate::centreX));
        List<NameplateTag> placed = new ArrayList<>(visible.size());
        for (Nameplate nameplate : visible) {
            String text = CompactFont.fit(nameplate.text().strip().replaceAll("\\s+", " "),
                    NAMEPLATE_TEXT_WIDTH, 1);
            int width = CompactFont.width(text, 1) + 4;
            int x = Math.max(1, Math.min(viewportWidth - width - 1, nameplate.centreX() - width / 2));
            int y = nameplate.bottom() - NAMEPLATE_HEIGHT;
            boolean moved = true;
            for (int attempt = 0; moved && attempt <= placed.size(); attempt++) {
                moved = false;
                for (NameplateTag other : placed) {
                    if (x < other.x() + other.width() + 1 && other.x() < x + width + 1
                            && y < other.y() + other.height() + 1 && other.y() < y + NAMEPLATE_HEIGHT + 1) {
                        y = other.y() - NAMEPLATE_HEIGHT - 1;
                        moved = true;
                    }
                }
            }
            placed.add(new NameplateTag(text, x, Math.max(1, y), width, NAMEPLATE_HEIGHT,
                    Math.min(1f, nameplate.alpha())));
        }
        return placed;
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
