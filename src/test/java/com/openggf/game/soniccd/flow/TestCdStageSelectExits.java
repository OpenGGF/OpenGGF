package com.openggf.game.soniccd.flow;

import com.openggf.NativeMenuPresentationAccess;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.game.soniccd.program.CdStageSelection;
import com.openggf.game.soniccd.release.CdRetailReleases;
import com.openggf.game.soniccd.title.CdCominSoonPreviewScreen;
import com.openggf.game.soniccd.title.CdStageSelectScreen;
import com.openggf.game.soniccd.title.CdTitleScreen;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.Palette;
import com.openggf.level.PaletteDisplayColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;

/**
 * The stage-select secret's rows that are not stages, through the engine's hosts: IPX {@code StageSelect}
 * runs the row's program and returns to {@code .GameLoop}, the title (IPX.asm:710-728); for the special-stage
 * row it runs {@code SpecStage1Demo} (IPX.asm:111-118) and returns to the title too.
 */
@EnabledOnOs(OS.LINUX)
class TestCdStageSelectExits {
    /** The retail secret at PRESS START: Up, Down, Down, Left, Right, B (Title Screen menu cheats). */
    private static void enterStageSelect(CdFlowHarness h) {
        h.until(() -> h.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
        var title = (CdTitleScreen) h.loop.getTitleScreenProvider();
        h.until(() -> title.program() != null && title.program().acceptsMenuInput(), 3000, 0);
        int[][] code = {{CdFlowHarness.UP, 0}, {CdFlowHarness.DOWN, 0}, {CdFlowHarness.DOWN, 0},
                {CdFlowHarness.LEFT, 0}, {CdFlowHarness.RIGHT, 0}, {0, CdFlowHarness.B}};
        for (int[] press : code) {
            for (int i = 0; i < 4; i++) h.step(press[0], press[1], false);
            h.idle(12);
        }
        h.until(() -> h.loop.getCurrentGameMode() == GameMode.LEVEL_SELECT, 900, 0);
    }

    /** Moves the menu to {@code row} with Down presses, then Start. */
    private static void choose(CdFlowHarness h, int row) {
        var select = (CdStageSelectScreen) h.module.getLevelSelectProvider();
        h.until(() -> select.program() != null, 300, 0);
        int guard = 0;
        while (select.program().selection() != row) {
            if (guard++ > 4000) fail("menu never reached row " + row);
            h.step(CdFlowHarness.DOWN, 0, false);
            h.idle(1);
        }
        h.idle(2);
        h.step(0, 0, true);
    }

    private static int firstRow(CdFlowHarness h, java.util.function.Predicate<CdStageSelection> test) {
        List<CdStageSelection> rows = h.scenes.flow().selections();
        for (int i = 0; i < CdStageSelection.MENU_ENTRIES; i++) if (test.test(rows.get(i))) return i;
        throw new AssertionError("no such StageSels row");
    }

    @ParameterizedTest
    @EnumSource(value = CdCommand.class, names = {"WARP", "OPENING"})
    void otherNonStageRowsStillReturnToTheTitle(CdCommand command) throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            enterStageSelect(h);
            List<GameMode> modes = new ArrayList<>();
            h.loop.setGameModeChangeListener((from, to) -> modes.add(to));
            int row = firstRow(h, s -> s.command() == command);
            choose(h, row);
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
            assertEquals(List.of(GameMode.TITLE_SCREEN), modes);
            var steps = h.scenes.notHosted();
            assertEquals(h.scenes.flow().selections().get(row).program().orElseThrow(), steps.get(steps.size() - 1).program(),
                    "the row's program is recorded as not hosted, then IPX returns to .GameLoop");
        }
    }

    @Test
    void cominSoonOpensTheDiscPreviewAndBackStartsAFreshTitleWithoutWritingTheSave() throws Exception {
        var saved = CdSaveRecord.decode(new byte[CdSaveRecord.FILE_BYTES]);
        saved.store(new CdSaveVariables(0x0215, 3, 0x04, 0x15, 0, 4, 0x0f));
        try (CdFlowHarness h = new CdFlowHarness(module -> CdFlowHarness.attachReadOnlySave(module, saved))) {
            enterStageSelect(h);
            List<GameMode> modes = new ArrayList<>();
            h.loop.setGameModeChangeListener((from, to) -> modes.add(to));
            int row = firstRow(h, s -> s.command() == CdCommand.COMINSOON);
            choose(h, row);
            // Keep the stage-select confirmation held across the mode handoff.
            for (int i = 0; i < 180; i++) h.step(0, 0, true);
            assertEquals(GameMode.OPTIONS, h.loop.getCurrentGameMode());
            assertFalse(GameServices.fade().isActive());
            assertDiscPixels(h);

            h.step(0, CdFlowHarness.C, false);
            var title = (CdTitleScreen) h.loop.getTitleScreenProvider();
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN
                    && title.program() != null && title.program().acceptsMenuInput(), 3000, 0);
            assertTrue(modes.contains(GameMode.OPTIONS));
            assertFalse(modes.contains(GameMode.LEVEL), modes.toString());
            assertNull(NativeMenuPresentationAccess.screen(h.loop));
            assertNull(h.scenes.titleRequest(), "fresh title clears the preview request");
            assertEquals(saved, h.scenes.flow().backupRam().readSave());
        }
    }

    private static void assertDiscPixels(CdFlowHarness h) throws IOException {
        var disc = h.disc.assets();
        assertSame(CdRetailReleases.USA, CdRetailReleases.find(disc.identity()).orElseThrow());
        var file = CdNativeFile.COMINSOON;
        byte[] packed = disc.read(file, 0x2D8, 0x7DC - 0x2D8);
        byte[] patterns;
        try (var input = Channels.newChannel(new ByteArrayInputStream(packed))) {
            patterns = NemesisReader.decompress(input);
        }
        assertEquals(150 * 32, patterns.length);
        var palette = new Palette();
        palette.fromSegaFormat(disc.read(file, 0x2B8, 32));
        int[] expectedColors = new int[16];
        for (int i = 1; i < expectedColors.length; i++) {
            expectedColors[i] = PaletteDisplayColor.packed(palette.colors[i], GameServices.graphics());
        }

        var screen = assertInstanceOf(CdCominSoonPreviewScreen.class, NativeMenuPresentationAccess.screen(h.loop));
        screen.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT);
        screen.draw();
        GameServices.graphics().flushScreenSpace();
        var rendered = ScreenshotCapture.captureFramebuffer(320, 224);

        // The preview uses declaration order and a 4px gutter, independent of the original maps.
        int panelColor = rendered.argb(80, 54);
        var discColors = new HashSet<Integer>();
        for (int tile = 0; tile < 150; tile++) {
            int left = 72 + 12 * (tile % 15);
            int top = 54 + 12 * (tile / 15);
            assertEquals(panelColor, rendered.argb(left + 8, top), "gutter after tile " + tile);
            for (int pixel = 0; pixel < 64; pixel++) {
                int indices = Byte.toUnsignedInt(patterns[tile * 32 + pixel / 2]);
                int index = (pixel & 1) == 0 ? indices >>> 4 : indices & 0xF;
                int actual = rendered.argb(left + pixel % 8, top + pixel / 8);
                int expected = index == 0 ? panelColor : 0xFF000000 | expectedColors[index];
                assertEquals(expected, actual, "disc tile " + tile + ", pixel " + pixel);
                if (index != 0) discColors.add(actual);
            }
        }
        assertTrue(discColors.size() > 1, "the sheet must contain visible disc art");
    }

    @Test
    void theSpecialStageRowRunsTheStageOneDemoThenTheTitle() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            enterStageSelect(h);
            int row = firstRow(h, s -> s.command() == CdCommand.SPECSTAGE);
            choose(h, row);
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE, 600, 0);
            var provider = (com.openggf.game.soniccd.special.CdSpecialStageProvider) h.module.getSpecialStageProvider();
            assertTrue(provider.session().stage().flags().temporary(), "SpecStage1Demo: temporary mode (IPX.asm:111-118)");
            assertEquals(0, provider.session().stage().stageId());
            java.util.List<GameMode> modes = new java.util.ArrayList<>();
            h.until(() -> {
                GameMode mode = h.loop.getCurrentGameMode();
                if (modes.isEmpty() || modes.get(modes.size() - 1) != mode) modes.add(mode);
                return mode == GameMode.TITLE_SCREEN;
            }, 20000, 0);
            assertTrue(h.scenes.route().contains("-> Title[]"), h.scenes.route().toString());
            assertFalse(modes.contains(GameMode.LEVEL), "no stage runs between the demo and the title: " + modes);
        }
    }
}
