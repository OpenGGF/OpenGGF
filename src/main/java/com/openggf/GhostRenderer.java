package com.openggf;

import com.openggf.sprites.art.PlayerSpriteArtProvider;
import com.openggf.game.GameServices;
import com.openggf.ghost.GhostFrame;
import com.openggf.graphics.AppearancePalettes;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.net.identity.PlayerAppearance;
import com.openggf.sprites.art.SpriteArtSet;
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
 *
 * <p>A ghost with the stock look reads the level's shared palette rows and is drawn as a
 * desaturated ghost. A ghost with a chosen look gets private palette rows
 * ({@link AppearancePalettes}) and keeps its colours, fading only with distance.
 */
public final class GhostRenderer {
    private static final Logger LOGGER = Logger.getLogger(GhostRenderer.class.getName());
    private static final int FULL_OPACITY_DISTANCE = 32;

    /** Widest overhead name, so a crowded pack stays readable. */
    static final int NAMEPLATE_TEXT_WIDTH = 72;
    /** Frames a slot may go undrawn before its private palette rows return to the bank. */
    static final int IDLE_PALETTE_FRAMES = 60;
    /** Clearance between a standing character's centre and the bottom of its name tag. */
    private static final int NAMEPLATE_LIFT = 22;
    private static final int NAMEPLATE_FILL_RGB = 0x101828;
    // Denser than the HUD panels: scenery showing through thin glyphs breaks them up.
    private static final int NAMEPLATE_FILL_ALPHA = 0xD8;

    private final Map<String, Slot> slots = new HashMap<>();
    private record Nameplate(String text, int centreX, int bottom, float alpha, int textRgb) { }
    /**
     * A placed, clipped overhead tag in logical viewport pixels. {@code textTop} is the top of
     * the text's line box; {@code textRgb} is the player's name colour.
     */
    record NameplateTag(String text, int x, int y, int width, int height, float alpha,
                        int textTop, int textRgb) { }
    private final Map<String, Nameplate> pendingNameplates = new LinkedHashMap<>();
    private int frame;

    public static boolean layerMatches(GhostFrame frame, int bucket, boolean highPriority) {
        return frame.priorityBucket() == bucket && frame.highPriority() == highPriority;
    }

    /**
     * Draws the ghosts recorded on this layer.
     *
     * @param localCharacter the local main character, whose palette occupies the level's
     *                       character line; a ghost sharing that stock line is recoloured from
     *                       the live line, any other from its own stock line
     */
    public void renderForLayer(List<ActiveGhost> ghosts, int bucket, boolean highPriority,
                               int playerCentreX, int playerCentreY, String localCharacter) {
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
            boolean colour = applyLook(slot, ghost.appearance(), localCharacter);
            if (frame.mappingFrame() < slot.lastMappingFrame) {
                slot.renderer.invalidateDplcCache(); // backwards jump (retry snap): force fresh DPLC
            }
            slot.lastMappingFrame = frame.mappingFrame();
            GraphicsManager graphics = GameServices.graphics();
            graphics.flushPatternBatch();
            boolean previousHighPriority = graphics.getCurrentSpriteHighPriority();
            graphics.setCurrentSpriteHighPriority(frame.highPriority());
            graphics.beginGhostRenderEffect(alpha, colour);
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
                ghost.frame().x() - cameraX, ghost.frame().y() - cameraY - NAMEPLATE_LIFT, alpha,
                ghost.appearance().nameArgb() & 0xFFFFFF));
    }

    /** Draw only labels collected by the sprite pass, after the world and before the host HUD. */
    void drawNameplates(PixelCanvas canvas) {
        // Called once per drawn frame: the clock that retires idle ghosts' palette rows.
        frame++;
        releaseIdlePalettes();
        if (pendingNameplates.isEmpty()) {
            return;
        }
        try {
            for (NameplateTag tag : layoutNameplates(canvas.width(), canvas.height())) {
                int fillAlpha = Math.round(NAMEPLATE_FILL_ALPHA * tag.alpha());
                int textAlpha = Math.round(0xFF * tag.alpha());
                canvas.fill(tag.x(), tag.y(), tag.width(), tag.height(), (fillAlpha << 24) | NAMEPLATE_FILL_RGB);
                NameFont.draw(canvas, tag.text(), tag.x() + 2, tag.textTop(), (textAlpha << 24) | tag.textRgb());
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
                    && nameplate.bottom() > 0 && nameplate.bottom() - NameFont.LINE_HEIGHT < viewportHeight) {
                visible.add(nameplate);
            }
        }
        visible.sort(Comparator.comparingInt(Nameplate::bottom).reversed()
                .thenComparingInt(Nameplate::centreX));
        List<NameplateTag> placed = new ArrayList<>(visible.size());
        for (Nameplate nameplate : visible) {
            String text = NameFont.fit(nameplate.text().strip().replaceAll("\\s+", " "),
                    NAMEPLATE_TEXT_WIDTH);
            int width = NameFont.width(text) + 4;
            // The plate covers every row the text draws: the line box, plus any tall fallback glyph.
            int[] extent = NameFont.extent(text);
            int height = extent[1] - extent[0] + 1;
            int x = Math.max(1, Math.min(viewportWidth - width - 1, nameplate.centreX() - width / 2));
            int y = nameplate.bottom() - height;
            boolean moved = true;
            for (int attempt = 0; moved && attempt <= placed.size(); attempt++) {
                moved = false;
                for (NameplateTag other : placed) {
                    if (x < other.x() + other.width() + 1 && other.x() < x + width + 1
                            && y < other.y() + other.height() + 1 && other.y() < y + height + 1) {
                        y = other.y() - height - 1;
                        moved = true;
                    }
                }
            }
            int top = Math.max(1, y);
            placed.add(new NameplateTag(text, x, top, width, height, Math.min(1f, nameplate.alpha()),
                    top - extent[0], nameplate.textRgb()));
        }
        return placed;
    }

    /** Drop cached art slots (call on level unload / time-attack teardown). */
    public void clearSlots() {
        for (Slot slot : slots.values()) {
            slot.releasePalette();
        }
        slots.clear();
        pendingNameplates.clear();
    }

    /**
     * Points the slot's renderer at private rows for {@code look}, or at the shared rows for the
     * stock look. Returns true when the ghost draws in its own colours.
     */
    private boolean applyLook(Slot slot, PlayerAppearance look, String localCharacter) {
        slot.lastUsedFrame = frame;
        String key = look.encode();
        if (!key.equals(slot.lookKey)) {
            slot.releasePalette();
            slot.lookKey = key;
            if (!look.isStock()) {
                slot.palette = acquirePalette(slot.code, look, localCharacter);
                if (slot.palette == null && releaseIdlePalettes(frame) > 0) {
                    slot.palette = acquirePalette(slot.code, look, localCharacter);
                }
            }
        }
        RenderContext context = slot.palette == null ? null : slot.palette.context();
        slot.renderer.setRenderContext(context);
        return context != null;
    }

    private static AppearancePalettes.Block acquirePalette(String code, PlayerAppearance look, String localCharacter) {
        LevelManager level = GameServices.levelOrNull();
        if (level == null || !(level.getGame() instanceof PlayerSpriteArtProvider artProvider)) {
            return null;
        }
        Palette stock = artProvider.loadCharacterPalette(code);
        if (stock == null) {
            return null;
        }
        Palette local = localCharacter == null ? null : artProvider.loadCharacterPalette(localCharacter);
        // The level's line 0 holds the local character's stock line. A ghost that shares it (Tails
        // beside Sonic) recolours the live line, so palette cycles show; another character
        // (Knuckles in a Sonic race) is composed from its own stock colours.
        boolean liveSource = local != null && local.dataEquals(stock);
        return AppearancePalettes.acquire(GameServices.graphics(), null, 0, stock, liveSource,
                LocalPlayerLook.overrideColours(look), LocalPlayerLook.overrideMask(look));
    }

    private void releaseIdlePalettes() {
        releaseIdlePalettes(frame - IDLE_PALETTE_FRAMES);
    }

    /** Releases the palette rows of slots last drawn before {@code beforeFrame}; returns how many. */
    private int releaseIdlePalettes(int beforeFrame) {
        int released = 0;
        for (Slot slot : slots.values()) {
            if (slot.palette != null && slot.lastUsedFrame < beforeFrame) {
                slot.releasePalette();
                // Re-acquire on its next draw.
                slot.lookKey = null;
                released++;
            }
        }
        return released;
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
            Slot slot = new Slot(new PlayerSpriteRenderer(ghostArt), code);
            slots.put(key, slot);
            return slot;
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to create ghost slot for " + characterCode, e);
            return null;
        }
    }

    private static final class Slot {
        final PlayerSpriteRenderer renderer;
        final String code;
        int lastMappingFrame = -1;
        String lookKey = "";
        AppearancePalettes.Block palette;
        int lastUsedFrame;

        Slot(PlayerSpriteRenderer renderer, String code) {
            this.renderer = renderer;
            this.code = code;
        }

        void releasePalette() {
            if (palette != null) {
                palette.release();
                palette = null;
                renderer.setRenderContext(null);
            }
        }
    }
}
