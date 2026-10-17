package com.openggf.game.sonic3k.specialstage;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Blue Sphere's indexed SAT pixel ownership, before sprite/Plane A priority.
 * Draw_SSSprites appends mapping pieces in traversal order, independently of
 * their Y coordinate or priority bit. An opaque low sprite still owns its pixel
 * when a high plane hides it; a later high sprite must never shine through.
 *
 * Only overlapping/clipped tiles need private, ROM-derived masked patterns.
 * Shared gameplay SAT replay and its shaders are deliberately not involved.
 */
final class S3kSpecialStageSpriteLayer {
    // The stage's loaded ROM art occupies the bottom of its existing range.
    static final int MASK_PATTERN_BASE = PatternAtlasRange.SPECIAL_STAGE_PLAYFIELD.endExclusive() - 0x2000;
    private static final int WIDTH = 320, HEIGHT = 224;
    private final boolean[] occupied = new boolean[WIDTH * HEIGHT];
    private final boolean[] highPlane = new boolean[WIDTH * HEIGHT];
    private final ArrayList<Pattern> masks = new ArrayList<>();
    private final Pattern scratch = new Pattern();
    private final PatternDesc maskedDesc = new PatternDesc();
    private GraphicsManager graphics;
    private Object atlas;
    private int originX, maskCount;

    /** Direct pattern commands choose their shader at execution, after submission flags restore. */
    PriorityScope priorityScope() {
        var scope = new PriorityScope();
        if (graphics.getShaderProgram() != null) graphics.registerCommand(scope::begin);
        return scope;
    }

    final class PriorityScope implements GLCommandable {
        private boolean active, previous;

        private void begin(int x, int y, int width, int height) {
            previous = graphics.isUseSpritePriorityShader();
            active = true;
            graphics.setUseSpritePriorityShader(false);
        }

        void endSubmission() {
            if (graphics.getShaderProgram() != null) graphics.registerCommand(this);
        }

        @Override public void execute(int x, int y, int width, int height) { discard(); }
        @Override public void discard() {
            if (active) {
                graphics.setUseSpritePriorityShader(previous);
                active = false;
            }
        }
    }

    void begin(GraphicsManager graphics, int originX) {
        this.graphics = graphics;
        this.originX = originX;
        if (atlas != graphics.getPatternAtlas()) {
            atlas = graphics.getPatternAtlas();
            masks.clear();
        }
        maskCount = 0;
        Arrays.fill(occupied, false);
        Arrays.fill(highPlane, false);
    }

    /** Retire owned atlas slots on stage exit/re-entry; the atlas owns its page textures. */
    void reset() {
        if (graphics != null && atlas == graphics.getPatternAtlas()) {
            for (int i = 0; i < masks.size(); i++) graphics.uncachePattern(MASK_PATTERN_BASE + i);
        }
        masks.clear();
        atlas = null;
        maskCount = 0;
    }

    /** Plane A replaces a name-table cell, including its transparent pixels. */
    void plane(int pattern, PatternDesc desc, int x, int y) {
        var pixels = SpritePresentation.patternSample(graphics, pattern);
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            int at = screenIndex(x + col, y + row);
            if (at >= 0) highPlane[at] = desc.getPriority() && pixel(pixels, desc, col, row) != 0;
        }
    }

    void sprite(int pattern, PatternDesc desc, int x, int y, boolean shadow) {
        var pixels = SpritePresentation.patternSample(graphics, pattern);
        // Missing residency draws no GPU pixels. Keeping the original submission
        // also preserves CPU presentation/geometry recorders with no uploaded art.
        if (pixels == null) {
            draw(pattern, desc, x, y, shadow);
            return;
        }
        boolean changed = false, visible = false;
        scratch.clear();
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            int index = pixel(pixels, desc, col, row);
            if (index == 0) continue;
            int at = screenIndex(x + col, y + row);
            if (at < 0 || occupied[at]) {
                changed = true;
                continue;
            }
            occupied[at] = true; // Claim BEFORE testing the plane, including hidden pixels.
            if (!desc.getPriority() && highPlane[at]) {
                changed = true;
                continue;
            }
            scratch.setPixel(col, row, (byte) index);
            visible = true;
        }
        if (!visible) return;
        if (!changed) {
            draw(pattern, desc, x, y, shadow);
            return;
        }
        int id = MASK_PATTERN_BASE + maskCount;
        if (id >= PatternAtlasRange.SPECIAL_STAGE_PLAYFIELD.endExclusive()) {
            throw new IllegalStateException("Blue Sphere masked sprite pattern range exhausted");
        }
        if (maskCount == masks.size()) {
            var retained = new Pattern();
            retained.copyFrom(scratch);
            masks.add(retained);
            graphics.cachePatternTexture(retained, id);
        } else {
            var retained = masks.get(maskCount);
            if (!samePixels(retained, scratch)) {
                retained.copyFrom(scratch);
                graphics.cachePatternTexture(retained, id);
            }
        }
        maskCount++;
        // Mask pixels are already in screen order; palette and priority survive.
        maskedDesc.set(0);
        maskedDesc.setPaletteIndex(desc.getPaletteIndex());
        maskedDesc.setPriority(desc.getPriority());
        draw(id, maskedDesc, x, y, shadow);
    }

    private void draw(int pattern, PatternDesc desc, int x, int y, boolean shadow) {
        if (shadow) graphics.addShadowPattern(pattern, desc, x + originX, y);
        else graphics.renderPatternWithId(pattern, desc, x + originX, y);
    }

    private int screenIndex(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT ? y * WIDTH + x : -1;
    }

    private static int pixel(SpritePresentation.PatternVersion pixels, PatternDesc desc, int x, int y) {
        if (pixels == null) return 0;
        int at = (desc.getVFlip() ? 7 - y : y) * 8 + (desc.getHFlip() ? 7 - x : x);
        long packed = switch (at >>> 4) {
            case 0 -> pixels.a(); case 1 -> pixels.b(); case 2 -> pixels.c(); default -> pixels.d();
        };
        return (int) (packed >>> ((at & 15) * 4)) & 15;
    }

    private static boolean samePixels(Pattern a, Pattern b) {
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            if (a.getPixel(x, y) != b.getPixel(x, y)) return false;
        return true;
    }
}
