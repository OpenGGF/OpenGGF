package survivors;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import java.util.function.Supplier;

/**
 * Wraps the stock Sonic 2 title. Once it is interactive, a "SURVIVORS" wordmark sits under the
 * emblem and a start-zone picker appears at the bottom: left/right chooses any zone a previous
 * run has reached (later starts grant catch-up level-ups), and the title's Start launches that
 * zone's first act, which opens in camp. Everything else is the ROM-driven title.
 */
final class SurvivorsTitle implements TitleScreenProvider {
    static final int REVEAL_DELAY = 100;
    private final com.openggf.mods.ui.BitmapFont font = Draw.createFont();
    private final TitleScreenProvider base;
    private final Supplier<Profile> profile;
    private int selected;
    private int activeFrames;
    private int kick;
    private InputHandler input;

    SurvivorsTitle(TitleScreenProvider base, Supplier<Profile> profile) {
        this.base = base;
        this.profile = profile;
    }

    TitleScreenProvider base() { return base; }
    InputHandler input() { return input; }

    @Override public void initialize() { base.initialize(); }
    @Override public void setClearColor() { base.setClearColor(); }
    @Override public void reset() { base.reset(); activeFrames = 0; }
    @Override public State getState() { return base.getState(); }
    @Override public boolean isExiting() { return base.isExiting(); }
    @Override public boolean isActive() { return base.isActive(); }
    @Override public boolean supportsLevelSelectOverlay() { return false; }
    @Override public TitleScreenAction consumeExitAction() { return base.consumeExitAction(); }
    @Override public void setExitToLevelHandler(Runnable handler) { base.setExitToLevelHandler(handler); }
    @Override public int startZoneIndex() { return Stages.zone(Math.min(selected, unlocked())); }

    private int unlocked() {
        Profile p = profile.get();
        return p == null ? 0 : p.unlocked;
    }

    @Override public void update(InputHandler input) {
        this.input = input;
        boolean interactive = base.getState() == State.ACTIVE;
        if (interactive && input != null && activeFrames > 20) {
            var logical = input.logical();
            int step = (logical.menuRight() ? 1 : 0) - (logical.menuLeft() ? 1 : 0);
            int count = unlocked() + 1;
            if (step != 0 && count > 1) {
                selected = Math.floorMod(selected + step, count);
                kick = 8;
                GameServices.audio().playSfx(0xCD);
            }
        }
        if (kick > 0) kick--;
        base.update(input);
        activeFrames = base.getState() == State.ACTIVE ? activeFrames + 1 : 0;
    }

    @Override public void draw() {
        base.draw();
        // Wait for the emblem to rise into place, then fade in.
        if (activeFrames < REVEAL_DELAY) return;
        GraphicsManager graphics = GraphicsManager.getInstance();
        int width = graphics.getProjectionWidth() > 0 ? graphics.getProjectionWidth() : 320;
        float alpha = Math.min(1f, (activeFrames - REVEAL_DELAY) / 20f);
        drawWordmark(graphics, width, alpha);
        drawPicker(graphics, width, alpha);
        graphics.flushScreenSpace();
    }

    private void drawWordmark(GraphicsManager graphics, int width, float alpha) {
        String word = "SURVIVORS";
        int scale = 3;
        int x = (width - Draw.width(word, scale)) / 2;
        int y = 150;
        rect(graphics, x - 8, y - 5, Draw.width(word, scale) + 16, 7 * scale + 10, 0x101848, alpha * 0.85f);
        text(graphics, word, x + 2, y + 2, scale, 0x000000, alpha * 0.6f);
        // A fire gradient: each glyph row a little redder.
        for (int row = 0; row < 7; row++) {
            int colour = row < 2 ? 0xFFF080 : row < 4 ? 0xFFC020 : row < 6 ? 0xFF7010 : 0xD02010;
            textRow(graphics, word, x, y, scale, row, colour, alpha);
        }
    }

    private void drawPicker(GraphicsManager graphics, int width, float alpha) {
        Profile p = profile.get();
        int stage = Math.min(selected, unlocked());
        String label = "START: " + Stages.name(stage);
        int y = 182;
        int labelW = Draw.width(label, 1);
        rect(graphics, (width - labelW) / 2 - 18, y - 4, labelW + 36, 15, 0x0A1440, alpha * 0.85f);
        text(graphics, label, (width - labelW) / 2, y, 1, 0xFFD030, alpha);
        if (unlocked() > 0) {
            int bob = kick / 2;
            text(graphics, "<", (width - labelW) / 2 - 12 - bob, y, 1, 0xFFFFFF, alpha);
            text(graphics, ">", (width + labelW) / 2 + 7 + bob, y, 1, 0xFFFFFF, alpha);
        }
        if (p != null) {
            String info = "RING BANK " + p.bank + "   EMERALDS " + p.emeraldCount() + "/7"
                    + (stage > 0 ? "   +" + 2 * stage + " LEVEL UPS" : "");
            text(graphics, info, (width - Draw.width(info, 1)) / 2, y + 14, 1, 0xA0C8FF, alpha);
        }
    }

    // ---- Screen-space drawing straight to the graphics manager (no camera at the title). ----
    private void text(GraphicsManager graphics, String text, int x, int y, int scale, int rgb, float alpha) {
        font.glyphs(text.toUpperCase(java.util.Locale.ROOT), x, y, scale,
                (px, py, w, h) -> rectH(graphics, px, py, w, h, rgb, alpha));
    }

    private void textRow(GraphicsManager graphics, String text, int x, int y, int scale, int row, int rgb,
                                float alpha) {
        int rowTop = y + row * scale;
        font.glyphs(text.toUpperCase(java.util.Locale.ROOT), x, y, scale, (px, py, w, h) -> {
            // Cached stems may span several differently coloured wordmark rows.
            int top = Math.max(py, rowTop), bottom = Math.min(py + h, rowTop + scale);
            if (bottom > top) rectH(graphics, px, top, w, bottom - top, rgb, alpha);
        });
    }

    private static void rect(GraphicsManager graphics, int x, int y, int w, int h, int rgb, float alpha) {
        rectH(graphics, x, y, w, h, rgb, alpha);
    }

    private static void rectH(GraphicsManager graphics, int x, int y, int w, int h, int rgb, float alpha) {
        if (w <= 0 || h <= 0) return;
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        graphics.registerCommand(alpha >= 1f
                ? new GLCommand(GLCommand.CommandType.RECTI, 0, r, g, b, x, y, x + w, y + h)
                : new GLCommand(GLCommand.CommandType.RECTI, 0, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                        r, g, b, alpha, x, y, x + w, y + h));
    }
}
