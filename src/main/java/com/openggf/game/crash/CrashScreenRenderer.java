package com.openggf.game.crash;

import com.openggf.game.MenuChrome;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PngTextureLoader;
import com.openggf.graphics.TexturedQuadRenderer;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Render-thread resources for the crash page. Created on first use, so a fault that happens
 * before any menu has drawn still gets its own font and art.
 */
public final class CrashScreenRenderer implements AutoCloseable {
    private MenuPixelFont font;
    private TexturedQuadRenderer renderer;
    private final Map<FaultKind, Integer> art = new EnumMap<>(FaultKind.class);

    public void render(CrashScreen screen, float[] projection, int width) {
        if (renderer == null) {
            var created = new TexturedQuadRenderer();
            var createdFont = new MenuPixelFont();
            try {
                created.init();
                createdFont.init("pixel-font.png", created);
                renderer = created;
                font = createdFont;
            } catch (IOException | RuntimeException failure) {
                createdFont.cleanup();
                created.cleanup();
                throw new IllegalStateException("Unable to render the crash screen", failure);
            }
        }
        renderer.setProjectionMatrix(projection);
        font.beginMegaBatch();
        screen.render(font, width);
        font.endMegaBatch();
        if (!screen.detailsOpen() && !screen.showingQr()) drawArt(screen);
    }

    private void drawArt(CrashScreen screen) {
        FaultKind kind = screen.report().kind();
        Integer texture = art.get(kind);
        if (texture == null) {
            try {
                texture = PngTextureLoader.loadTexture(kind.artResource());
            } catch (IOException missing) {
                throw new IllegalStateException("Crash screen art is missing: " + kind.artResource(), missing);
            }
            art.put(kind, texture);
        }
        CrashScreen.Box box = screen.artBox();
        // Textures draw with y up; the page is laid out with y down.
        renderer.drawTexture(texture, box.x(), MenuChrome.SCREEN_H - box.y() - box.height(), box.width(), box.height());
    }

    @Override
    public void close() {
        art.values().forEach(PngTextureLoader::deleteTexture);
        art.clear();
        if (font != null) { font.cleanup(); font = null; }
        if (renderer != null) { renderer.cleanup(); renderer = null; }
    }
}
