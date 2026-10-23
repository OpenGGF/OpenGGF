package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.game.SpecialStageViewport;
import com.openggf.game.sonic2.specialstage.SpecialStageBackgroundRenderer;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.NativeIntensityPalette;
import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.graphics.PaletteView;
import com.openggf.graphics.PatternRenderCommand;
import com.openggf.graphics.ScopedPaletteGraphicsAccess;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.level.Palette;
import com.openggf.level.PatternDesc;
import com.openggf.util.FboHelper;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/** Same native plane sources at normal and shadow DAC intensity, before output colour conversion. */
final class CompetitiveHalfpipePlanes {
    private final GraphicsManager graphics;
    private final SpecialStageViewport viewport;
    private final int backgroundBase;
    private final int trackBase;
    private final byte[] backgroundMappings;
    private final PatternDesc descriptor = new PatternDesc();
    private final SpecialStageBackgroundRenderer normalBackground;
    private final SpecialStageBackgroundRenderer shadowBackground;
    private final Object shadowPaletteOwner = new Object();
    private Palette[] palettes;
    private PaletteView[] shadowPalettes;
    private ScopedPaletteGraphicsAccess.Bank shadowBank;
    private FboHelper.FboHandle companion;

    CompetitiveHalfpipePlanes(CompetitiveHalfpipeRomData data, GraphicsManager graphics,
                             SpecialStageViewport viewport, int backgroundBase, int trackBase) {
        this.graphics = graphics;
        this.viewport = viewport;
        this.backgroundBase = backgroundBase;
        this.trackBase = trackBase;
        backgroundMappings = data.backgroundMappings();
        normalBackground = new SpecialStageBackgroundRenderer(graphics);
        normalBackground.setSpecialStageViewport(viewport);
        shadowBackground = new SpecialStageBackgroundRenderer(graphics);
        shadowBackground.setSpecialStageViewport(viewport);
    }

    void initialize(Palette[] sourcePalettes) throws IOException {
        setPalettes(sourcePalettes);
        normalBackground.init();
        shadowBackground.init();
        if (!graphics.isHeadlessMode()) {
            int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            int active = glGetInteger(GL_ACTIVE_TEXTURE);
            int texture = glGetInteger(GL_TEXTURE_BINDING_2D);
            try {
                companion = FboHelper.createColorOnly(viewport.logicalWidth(), 224, GL_CLAMP_TO_EDGE);
                if (companion == null) throw new IllegalStateException("Competitive native shadow target is incomplete");
            } finally {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
                glActiveTexture(active);
                glBindTexture(GL_TEXTURE_2D, texture);
            }
        }
    }

    void setPalettes(Palette[] sourcePalettes) {
        palettes = new Palette[4];
        for (int line = 0; line < 4; line++) palettes[line] = sourcePalettes[line].deepCopy();
        shadowPalettes = new PaletteView[4];
        for (int line = 0; line < 4; line++) shadowPalettes[line] = NativeIntensityPalette.shadow(palettes[line], line);
        shadowBank = ScopedPaletteGraphicsAccess.acquire(graphics, shadowPaletteOwner, shadowPalettes);
    }

    void draw(int[] tiles, int hScroll, int vScroll, Runnable sprites) {
        if (companion == null || !normalBackground.isInitialized() || !shadowBackground.isInitialized()) {
            throw new IllegalStateException("Competitive halfpipe drawing requires an owned GL context");
        }
        // Refresh the exact native source-line fade and display profile in the derived bank.
        shadowBank = ScopedPaletteGraphicsAccess.acquire(graphics, shadowPaletteOwner, shadowPalettes);
        int shadowBase = ScopedPaletteGraphicsAccess.base(shadowBank, graphics, shadowPaletteOwner);
        // RGBA background targets are rebuilt from indexed ROM art. Neither a stale palette-resolved
        // image nor a post-profile RGB multiply may stand in for native DAC shadow intensity.
        backgroundTiles(normalBackground, 0);
        backgroundTiles(shadowBackground, shadowBase);

        SceneTarget target = new SceneTarget(backdrop(true));
        graphics.registerCommand(target.begin);
        try {
            backgroundShader(shadowBackground, hScroll, vScroll);
            track(tiles, shadowBase);
        } finally {
            graphics.registerCommand(target.end);
        }
        clearNativeBackdrop(backdrop(false));
        backgroundShader(normalBackground, hScroll, vScroll);
        track(tiles, 0);
        SpriteSatInternalAccess.withShadow15Backdrop(graphics, companion.textureId(),
                graphics.getViewportX(), graphics.getViewportY(), graphics.getViewportWidth(), graphics.getViewportHeight(), sprites);
    }

    private void backgroundTiles(SpecialStageBackgroundRenderer background, int paletteBase) {
        TileScope scope = new TileScope(background);
        background.beginFBOProjection();
        try {
            graphics.registerCommand(scope.begin);
            graphics.beginPatternBatch();
            for (int row = 0; row < 32; row++) for (int column = 0; column < 32; column++) {
                int offset = (row * 32 + column) * 2;
                int word = (backgroundMappings[offset] & 0xFF) << 8 | backgroundMappings[offset + 1] & 0xFF;
                if (word == 0) continue;
                descriptor.set(word);
                descriptor.setPaletteIndex(paletteBase + (word >>> 13 & 3));
                graphics.renderPatternWithId(backgroundBase + (word & 0x7FF), descriptor, column * 8, row * 8);
            }
            graphics.flushPatternBatch();
        } finally {
            background.endFBOProjection();
            graphics.registerCommand(scope.end);
        }
    }

    private void backgroundShader(SpecialStageBackgroundRenderer background, int hScroll, int vScroll) {
        graphics.registerCommand((x, y, width, height) -> {
            background.setUniformHScroll(hScroll);
            background.renderWithShader(vScroll);
        });
    }

    /** Four 32-cell strips select two source scanlines each from the native 128-cell plane. */
    private void track(int[] tiles, int paletteBase) {
        graphics.beginPatternBatch();
        for (int row = 0; row < 28; row++) for (int strip = 0; strip < 4; strip++) {
            for (int column = 0; column < 32; column++) {
                int index = row * 128 + strip * 32 + column;
                if (index >= tiles.length) break;
                int word = tiles[index];
                if ((word & 0x7FF) == 0) continue;
                descriptor.set(word);
                descriptor.setPaletteIndex(paletteBase + (word >>> 13 & 3));
                graphics.renderStripPatternWithId(trackBase + (word & 0x7FF), descriptor,
                        viewport.innerH32OriginX() + column * 8, row * 8 + strip * 2, strip);
            }
        }
        graphics.flushPatternBatch();
    }

    private int backdrop(boolean shadow) {
        Palette.Color source = palettes[0].getColor(0);
        int r = source.r & 255, g = source.g & 255, b = source.b & 255;
        PaletteFadePresentation fade = PaletteUploadPresentation.displayedFade(graphics);
        if (fade.affects(0)) {
            int packed = fade.fadeRgb(r, g, b);
            r = packed >>> 16 & 255; g = packed >>> 8 & 255; b = packed & 255;
        }
        return shadow ? DisplayColorConverter.toNativeShadowPackedRgb((r * 7 + 127) / 255,
                (g * 7 + 127) / 255, (b * 7 + 127) / 255,
                graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion())
                : DisplayColorConverter.toPackedRgb(r, g, b,
                        graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
    }

    private void clearNativeBackdrop(int color) {
        graphics.registerCommand((cameraX, cameraY, cameraWidth, cameraHeight) -> {
            int[] actual = new int[4], scissor = new int[4];
            float[] clear = new float[4];
            glGetIntegerv(GL_VIEWPORT, actual); glGetIntegerv(GL_SCISSOR_BOX, scissor);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
            boolean enabled = glIsEnabled(GL_SCISSOR_TEST);
            try {
                double scale = actual[2] / (double) viewport.logicalWidth();
                int left = actual[0] + (int) Math.floor(viewport.innerH32OriginX() * scale);
                int right = actual[0] + (int) Math.ceil((viewport.innerH32OriginX() + 256) * scale);
                int bottom = actual[1], top = actual[1] + actual[3];
                if (enabled) {
                    left = Math.max(left, scissor[0]); right = Math.min(right, scissor[0] + scissor[2]);
                    bottom = Math.max(bottom, scissor[1]); top = Math.min(top, scissor[1] + scissor[3]);
                }
                glEnable(GL_SCISSOR_TEST);
                glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
                glClearColor((color >>> 16 & 255) / 255f, (color >>> 8 & 255) / 255f, (color & 255) / 255f, 1);
                glClear(GL_COLOR_BUFFER_BIT);
            } finally {
                glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
                if (enabled) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
                glClearColor(clear[0], clear[1], clear[2], clear[3]);
            }
        });
    }

    void cleanup() {
        try {
            normalBackground.cleanup();
        } finally {
            try {
                shadowBackground.cleanup();
            } finally {
                try {
                    if (companion != null) { FboHelper.destroy(companion); companion = null; }
                } finally {
                    ScopedPaletteGraphicsAccess.release(shadowBank, shadowPaletteOwner);
                    shadowBank = null;
                }
            }
        }
    }

    private static final class TileScope {
        private final SpecialStageBackgroundRenderer target;
        private boolean armed;
        TileScope(SpecialStageBackgroundRenderer target) {
            this.target = target;
            begin = (x, y, width, height) -> { target.beginTilePass(224); armed = true; };
        }
        final GLCommandable begin;
        final GLCommandable end = new GLCommandable() {
            @Override public void execute(int x, int y, int width, int height) { restore(); }
            @Override public void unwindAfterFailure(int x, int y, int width, int height) { restore(); }
        };
        private void restore() { if (armed) { target.endTilePass(); armed = false; } }
    }

    private final class SceneTarget {
        private final int clear;
        private final int[] oldViewport = new int[4];
        private final int[] oldGraphicsViewport = new int[4];
        private final int[] oldScissor = new int[4];
        private final float[] oldClear = new float[4];
        private int draw, read;
        private boolean scissor, depth, armed;
        SceneTarget(int clear) {
            this.clear = clear;
            begin = (x, y, width, height) -> open();
        }
        final GLCommandable begin;
        private void open() {
            draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            glGetIntegerv(GL_VIEWPORT, oldViewport);
            glGetIntegerv(GL_SCISSOR_BOX, oldScissor);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, oldClear);
            oldGraphicsViewport[0] = graphics.getViewportX(); oldGraphicsViewport[1] = graphics.getViewportY();
            oldGraphicsViewport[2] = graphics.getViewportWidth(); oldGraphicsViewport[3] = graphics.getViewportHeight();
            scissor = glIsEnabled(GL_SCISSOR_TEST); depth = glIsEnabled(GL_DEPTH_TEST); armed = true;
            glBindFramebuffer(GL_FRAMEBUFFER, companion.fboId());
            glViewport(0, 0, viewport.logicalWidth(), 224);
            graphics.setViewport(0, 0, viewport.logicalWidth(), 224);
            glDisable(GL_SCISSOR_TEST); glDisable(GL_DEPTH_TEST);
            glClearColor((clear >>> 16 & 255) / 255f, (clear >>> 8 & 255) / 255f, (clear & 255) / 255f, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            PatternRenderCommand.resetFrameState();
        }
        final GLCommandable end = new GLCommandable() {
            @Override public void execute(int x, int y, int width, int height) { restore(); }
            @Override public void unwindAfterFailure(int x, int y, int width, int height) { restore(); }
        };
        private void restore() {
            if (!armed) return;
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw); glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
            graphics.setViewport(oldGraphicsViewport[0], oldGraphicsViewport[1], oldGraphicsViewport[2], oldGraphicsViewport[3]);
            glScissor(oldScissor[0], oldScissor[1], oldScissor[2], oldScissor[3]);
            if (scissor) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
            if (depth) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
            glClearColor(oldClear[0], oldClear[1], oldClear[2], oldClear[3]);
            PatternRenderCommand.resetFrameState(); armed = false;
        }
    }
}
