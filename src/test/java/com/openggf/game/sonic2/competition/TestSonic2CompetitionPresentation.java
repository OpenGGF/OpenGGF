package com.openggf.game.sonic2.competition;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.Mockito.mock;

@RequiresRom(SonicGame.SONIC_2)
@ExtendWith(SingletonResetExtension.class)
class TestSonic2CompetitionPresentation {
    @Test void actResultsUseNativeScoreUnitsFrameConversionCategoryLabelsAndTotals() throws Exception {
        var assets = Sonic2CompetitionAssets.load(TestEnvironment.currentRom());
        var s = started();
        s.gameState(0).addScore(12340);
        s.levelState(0).setTimerFrames(59);
        s.levelState(1).setTimerFrames(60);
        s.playerState(1).addRings(42);
        finish(s);
        var map = Sonic2CompetitionPresentation.compose(assets, s);
        assertEquals(0x11, map.words().get(0x342 / 2)); // Six raw-score cells: 001234; template supplies trailing zero.
        assertEquals(0x14, map.words().get(0x348 / 2));
        assertEquals(0x19, map.words().get(0x3E8 / 2)); // Native frame59 ->99 hundredths.
        assertEquals(0x19, map.words().get(0x3EA / 2));
        assertEquals(0x6031, map.words().get(0x5E4 / 2)); // TIED monitors.
        assertEquals(0x12, map.words().get(0x70A / 2)); // P1 score+time wins.
        assertEquals(0x12, map.words().get(0x712 / 2)); // P2 rings+total rings wins.
    }

    @Test void zoneResultsPreserveNativeNoGameTextOffsetAndTieLabels() throws Exception {
        var assets = Sonic2CompetitionAssets.load(TestEnvironment.currentRom());
        var s = started();
        for (int act = 0; act < 2; act++) {
            s.gameState(0).addScore(100); finish(s); s.updateMenu(start(), true);
        }
        var map = Sonic2CompetitionPresentation.compose(assets, s);
        assertEquals(0x2B, map.words().get(0x618 / 2)); // Native NO GAME begins at row origin.
        assertEquals(0, map.words().get(0x62C / 2));
        assertEquals(0x11, map.words().get(0x39C / 2));
    }

    @Test void backgroundAnimationUsesNativeSixEntriesAndReplaysFromCapturedPresentationClock() {
        assertEquals(0, Sonic2CompetitionPresentation.backgroundSource(0));
        assertEquals(0, Sonic2CompetitionPresentation.backgroundSource(199));
        assertEquals(10, Sonic2CompetitionPresentation.backgroundSource(200));
        assertEquals(20, Sonic2CompetitionPresentation.backgroundSource(206));
        assertEquals(30, Sonic2CompetitionPresentation.backgroundSource(212));
        assertEquals(20, Sonic2CompetitionPresentation.backgroundSource(412));
        assertEquals(10, Sonic2CompetitionPresentation.backgroundSource(418));
        assertEquals(0, Sonic2CompetitionPresentation.backgroundSource(424));
        var s = new Sonic2CompetitionSession();
        for (int i = 0; i < 206; i++) s.advancePresentation();
        s.setResultsPlcReady(true);
        var captured = s.capture();
        s.advancePresentation(); s.setResultsPlcReady(false); s.restore(captured);
        assertEquals(20, Sonic2CompetitionPresentation.backgroundSource(s.presentationFrame()));
        assertTrue(s.resultsPlcReady());
    }

    @Test @EnabledOnOs(OS.LINUX)
    void originalSelectionAndResultsRenderThroughProductionGraphicsInDisplayFreeEgl() throws Exception {
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        var rom = TestEnvironment.currentRom();
        var assets = Sonic2CompetitionAssets.load(rom);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            var graphics = GameServices.graphics();
            var screen = new Sonic2CompetitionScreen(new Sonic2CompetitionSession(), mock(Sonic2CompetitionHost.class), null, rom);
            screen.initialize(); screen.setClearColor();
            var clear = BufferUtils.createFloatBuffer(4);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
            assertEquals(0, clear.get(0));
            assertEquals(36f / 255, clear.get(1));
            assertEquals(219f / 255, clear.get(2)); // Pal_Menu color0 is native word$0C20.
            var presentation = new Sonic2CompetitionPresentation(assets, rom, graphics);
            var s = new Sonic2CompetitionSession();
            byte[] selection = picture(presentation, s);
            s.updateMenu(start(), true); s.gameState(1).addScore(12340); finish(s);
            byte[] result = picture(presentation, s);
            int changed = 0, drawn = 0;
            for (int i = 0; i < result.length; i += 4) {
                if (selection[i] != result[i] || selection[i + 1] != result[i + 1] || selection[i + 2] != result[i + 2]) changed++;
                if (result[i] != 0 || (result[i + 1] & 255) != 36 || (result[i + 2] & 255) != 219) drawn++;
            }
            assertTrue(drawn > 30000, "ROM menu must replace cleared target");
            assertTrue(changed > 3000, "Act fields and original result art must differ from selection");
            assertEquals(GL_NO_ERROR, glGetError());
            var directory = Path.of("target", "capture", "sonic2-competition");
            Files.createDirectories(directory);
            writePng(selection, directory.resolve("select.png"));
            writePng(result, directory.resolve("act-results.png"));
        }
    }
    private static byte[] picture(Sonic2CompetitionPresentation presentation, Sonic2CompetitionSession s) {
        glClearColor(0, 36f / 255, 219f / 255, 1); glClear(GL_COLOR_BUFFER_BIT);
        presentation.draw(s, s.presentationFrame());
        GameServices.graphics().flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        glFinish();
        var pixels = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] bytes = new byte[pixels.capacity()]; pixels.get(bytes); return bytes;
    }
    private static void writePng(byte[] pixels, Path path) throws Exception {
        var image = new BufferedImage(320, 224, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int offset = ((223 - y) * 320 + x) * 4;
            image.setRGB(x, y, ((pixels[offset] & 255) << 16) | ((pixels[offset + 1] & 255) << 8) | (pixels[offset + 2] & 255));
        }
        ImageIO.write(image, "png", path.toFile());
    }
    private static LogicalInputSnapshot start() {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral());
    }
    private static Sonic2CompetitionSession started() {
        var s = new Sonic2CompetitionSession(); s.updateMenu(start(), true); return s;
    }
    private static void finish(Sonic2CompetitionSession s) {
        s.observeGoal(0, 100, 100); s.observeGoal(1, 100, 100); s.completeSignpostSpin();
    }
}
