package com.openggf.game.soniccd.flow;

import com.openggf.game.GameMode;
import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.program.CdStageSelection;
import com.openggf.game.soniccd.title.CdStageSelectScreen;
import com.openggf.game.soniccd.title.CdTitleScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void aRowNamingAProgramReturnsToTheTitle() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            enterStageSelect(h);
            int row = firstRow(h, s -> s.stage().isEmpty() && s.command() != CdCommand.SPECSTAGE);
            choose(h, row);
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
            var steps = h.scenes.notHosted();
            assertEquals(h.scenes.flow().selections().get(row).program().orElseThrow(), steps.get(steps.size() - 1).program(),
                    "the row's program is recorded as not hosted, then IPX returns to .GameLoop");
        }
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
