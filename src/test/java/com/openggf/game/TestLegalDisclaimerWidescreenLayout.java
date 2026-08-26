package com.openggf.game;

import com.openggf.graphics.PixelFont;
import com.openggf.graphics.TexturedQuadRenderer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestLegalDisclaimerWidescreenLayout {

    @Test
    void drawForTestingEmitsWideBackdropAndCenteredTextCommands() throws Exception {
        LegalDisclaimerScreen screen = new LegalDisclaimerScreen(new com.openggf.graphics.FadeManager());
        RecordingRenderer renderer = new RecordingRenderer();
        RecordingFont font = new RecordingFont();
        setField(screen, "renderer", renderer);
        setField(screen, "font", font);
        setField(screen, "solidWhiteTextureId", 7);
        setField(screen, "wrappedBodyLines", List.of("BODY"));
        screen.setViewportWidth(400);

        screen.drawForTesting();

        assertEquals(new Quad(7, 0, 0, 400, 224), renderer.quad);
        assertEquals(List.of(new Text("LEGAL NOTICE", 146, 22), new Text("BODY", 186, 48)),
                font.texts);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Quad(int texture, int x, int y, int width, int height) { }
    private record Text(String text, int x, int y) { }

    private static final class RecordingRenderer extends TexturedQuadRenderer {
        private Quad quad;

        @Override
        public void drawTexture(int textureId, float x, float y, float width, float height,
                                float r, float g, float b, float a) {
            quad = new Quad(textureId, (int) x, (int) y, (int) width, (int) height);
        }
    }

    private static final class RecordingFont extends PixelFont {
        private final java.util.ArrayList<Text> texts = new java.util.ArrayList<>();

        @Override
        public int measureWidth(String text) {
            return text.length() * 9;
        }

        @Override
        public int measureWidth(String text, float scale) {
            return Math.round(text.length() * 9 * scale);
        }

        @Override
        public void drawText(String text, float x, float y, float scale,
                             float r, float g, float b, float a) {
            texts.add(new Text(text, Math.round(x), Math.round(y)));
        }

        @Override public void beginMegaBatch() { }
        @Override public void endMegaBatch() { }
    }
}
