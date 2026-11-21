package com.openggf.game.soniccd.level;

import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectServices;

import java.util.Arrays;

/**
 * Stage colors published by VBlankStage (common/main.asm:854-877) before the
 * object and palette-cycle writes in StageMainLoopUpdate (common/main.asm:406-410)
 * and StageMainLoopDraw (common/main.asm:443-453).
 */
public final class CdStagePalette implements RewindSnapshottable<Palette[]> {
    private static final String REWIND_KEY = "cd-stage-palette";

    private final CdLevel level;
    private final Palette[] displayed = new Palette[4];

    CdStagePalette(CdLevel level) {
        this.level = level;
    }

    public void publish(GraphicsManager graphics) {
        for (int line = 0; line < displayed.length; line++) {
            Palette working = level.getPalette(line);
            if (!working.dataEquals(displayed[line])) {
                Palette published = working.deepCopy();
                displayed[line] = published;
                if (graphics.isGlInitialized()) {
                    graphics.cachePaletteTexture(published, line);
                }
            }
        }
    }

    Palette.Color backdrop(int line) {
        Palette palette = displayed[line];
        return (palette != null ? palette : level.getPalette(line)).getColor(0);
    }

    public static void writeLine(ObjectServices services, int line, byte[] words) {
        var levels = services.levelManager();
        if (levels != null && levels.getCurrentLevel() instanceof CdLevel cd) {
            cd.getPalette(line).fromSegaFormat(words);
        } else {
            services.updatePalette(line, words);
        }
    }

    @Override public String key() { return REWIND_KEY; }

    // Published rows are replaced as a unit and never written by stage logic.
    @Override public Palette[] capture() { return displayed.clone(); }

    @Override
    public void restore(Palette[] snapshot) {
        System.arraycopy(snapshot, 0, displayed, 0, displayed.length);
    }

    @Override
    public void resetForMissingSnapshot() {
        Arrays.fill(displayed, null);
    }

    public static void bindRewind(RewindRegistry registry, GraphicsManager graphics, CdStagePalette palette) {
        registry.deregister(REWIND_KEY);
        registry.deregisterPostRestoreCallback(REWIND_KEY);
        if (palette != null) {
            registry.register(palette);
            registry.registerPostRestoreCallback(REWIND_KEY, () -> palette.restoreGraphics(graphics));
        }
    }

    private void restoreGraphics(GraphicsManager graphics) {
        if (displayed[0] == null) {
            publish(graphics);
        } else if (graphics.isGlInitialized()) {
            for (int line = 0; line < displayed.length; line++) {
                graphics.cachePaletteTexture(displayed[line], line);
            }
        }
    }
}
