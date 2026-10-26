package com.openggf.game.sonic2.options;

import com.openggf.control.InputHandler;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN;
import static com.openggf.game.sonic2.options.TestSonic2OptionsControls.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Full native options pictures through the actual GPU pattern/palette/command pipeline. */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2OptionsPixels {
    @Test
    @EnabledOnOs(OS.LINUX)
    void threeSelectedRowsAndAnimatedBackgroundMatchRomTilePixels() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        var data = Sonic2OptionsData.load(TestEnvironment.currentRom(), true);
        var state = new Sonic2OptionsState();
        try (var context = PresentationOffscreenTestContext.open(320, 224)) {
            var graphics = new GraphicsManager();
            try {
                RenderContext.reset();
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, 320, 224);
                graphics.setProjectionWidth(320);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
                graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
                glViewport(0, 0, 320, 224);
                glDisable(GL_DITHER);
                var screen = new Sonic2OptionsScreen(state, true, data, () -> graphics, () -> {}, ignored -> {});
                screen.initialize();
                var neutral = new InputHandler();
                for (int frame = 0; frame < 22; frame++) screen.update(neutral);
                for (int row = 0; row < 3; row++) {
                    assertPicture(screen, graphics, data, state, 0);
                    press(state, INPUT_DOWN, 0);
                }
                // The native background switches source tile bank after 200 active menu iterations.
                for (int frame = 0; frame < 200; frame++) screen.update(neutral);
                assertPicture(screen, graphics, data, state, 10);
            } finally {
                graphics.cleanup();
                RenderContext.reset();
            }
        }
    }

    private static void assertPicture(Sonic2OptionsScreen screen, GraphicsManager graphics,
            Sonic2OptionsData data, Sonic2OptionsState state, int backgroundSource) {
        screen.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT);
        screen.draw();
        graphics.flushScreenSpace();
        glFinish();
        ByteBuffer actual = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, actual);
        int[] expected = new int[320 * 224];
        Arrays.fill(expected, rgb(data.palettes[0].colors[0]));
        for (int y = 0; y < 28; y++) for (int x = 0; x < 40; x++) {
            int word = data.backgroundMap[y * 40 + x];
            int tile = word & 0x7FF;
            Pattern pattern = tile == 0 ? new Pattern() : data.backgroundArt[backgroundSource + tile - 1];
            paint(expected, pattern, data.palettes[(word >>> 13) & 3], word, x * 8, y * 8);
        }
        for (int row = 0; row < 3; row++) {
            int[] box = data.box(row, state);
            for (int cell = 0; cell < 176; cell++) {
                int word = box[cell];
                paint(expected, data.patterns[word & 0x7FF], data.palettes[(word >>> 13) & 3], word,
                        (9 + cell % 22) * 8, (3 + row * 8 + cell / 22) * 8);
            }
        }
        int differences = 0;
        int nonBackdrop = 0;
        String first = null;
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int offset = ((223 - y) * 320 + x) * 4;
            int got = (Byte.toUnsignedInt(actual.get(offset)) << 16)
                    | (Byte.toUnsignedInt(actual.get(offset + 1)) << 8)
                    | Byte.toUnsignedInt(actual.get(offset + 2));
            if (got != expected[y * 320 + x]) {
                differences++;
                if (first == null) first = x + "," + y + " expected=" + Integer.toHexString(expected[y * 320 + x])
                        + " actual=" + Integer.toHexString(got);
            }
            if (got != rgb(data.palettes[0].colors[0])) nonBackdrop++;
        }
        assertTrue(nonBackdrop > 10000, "The production screen must actually draw opaque ROM pixels");
        assertEquals(0, differences, "row=" + state.selectedRow() + " background=" + backgroundSource + " first=" + first);
        assertEquals(GL_NO_ERROR, glGetError());
    }

    private static void paint(int[] destination, Pattern pattern, Palette palette, int word, int x, int y) {
        for (int py = 0; py < 8; py++) for (int px = 0; px < 8; px++) {
            int index = pattern.getPixel((word & 0x800) == 0 ? px : 7 - px,
                    (word & 0x1000) == 0 ? py : 7 - py);
            if (index != 0) destination[(y + py) * 320 + x + px] = rgb(palette.colors[index]);
        }
    }

    private static int rgb(Palette.Color color) {
        return (Byte.toUnsignedInt(color.r) << 16) | (Byte.toUnsignedInt(color.g) << 8) | Byte.toUnsignedInt(color.b);
    }
}
