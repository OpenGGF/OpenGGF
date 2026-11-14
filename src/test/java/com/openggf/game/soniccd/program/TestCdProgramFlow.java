package com.openggf.game.soniccd.program;

import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.program.CdProgramRequest.NotHosted;
import com.openggf.game.soniccd.program.CdProgramRequest.Route;
import com.openggf.game.soniccd.program.CdProgramRequest.Stage;
import com.openggf.game.soniccd.program.CdProgramRequest.StageSelect;
import com.openggf.game.soniccd.program.CdProgramRequest.Title;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IPX flow decisions ({@code CD Initial Program/IPX.asm}) on tables built from the catalogue in the
 * source order of {@code R11SubCmds..R83SubCmds} (IPX.asm:651-704). The disc's own tables are checked
 * against the same expectations by {@code TestCdRetailRouteReplay}.
 */
class TestCdProgramFlow {
    private static final String[] ZONES = {"1", "3", "4", "5", "6", "7", "8"};

    private static CdProgramFlow flow() {
        byte[] sub = new byte[70];
        int at = 0;
        for (String zone : ZONES) {
            for (String stage : new String[] {"1A", "1B", "1C", "1D", "2A", "2B", "2C", "2D", "3C", "3D"}) {
                sub[at++] = (byte) CdStagePayload.valueOf("R" + zone + stage).loadCommand().id();
            }
        }
        List<CdStageSelection> selections = new ArrayList<>();
        selections.add(new CdStageSelection(0, CdCommand.R11A, 0x000, CdStageResult.TIME_PRESENT, false));
        for (int i = 1; i < CdStageSelection.MENU_ENTRIES; i++) {
            CdCommand command = switch (i) {
                case 8 -> CdCommand.WARP;
                case 9 -> CdCommand.OPENING;
                case 73 -> CdCommand.SPECSTAGE;
                case 31 -> CdCommand.R43C;
                default -> CdCommand.R12A;
            };
            selections.add(new CdStageSelection(i, command, i == 31 ? 0x202 : 0x001, CdStageResult.TIME_FUTURE, i == 31));
        }
        return new CdProgramFlow(selections, sub);
    }

    private static CdStagePayload stage(CdProgramRequest request) {
        return assertInstanceOf(Stage.class, request).payload();
    }

    private static CdStageResult cleared(boolean goodFuture) {
        return CdStageResult.cleared(3, CdStageResult.TIME_PRESENT, goodFuture, 0);
    }

    @Test
    void newGameRunsPalmtreePanicAct1PresentThenTheNativeOrder() {
        CdProgramFlow flow = flow();
        flow.enterTitle();
        assertEquals(CdStagePayload.R11A, stage(flow.titleExited(CdProgramFlow.EXIT_NEW_GAME)));
        assertEquals(CdStagePayload.R12A, stage(flow.stageEnded(cleared(false))));     // RunR12 starts present
        assertEquals(CdStagePayload.R13D, stage(flow.stageEnded(cleared(false))));     // RunBossLevel: bad future
        assertEquals(CdStagePayload.R31A, stage(flow.stageEnded(cleared(false))));     // RunR3
        assertEquals(0x0100, flow.savedLevel());                                        // addq.b #1,savedLevel
        assertEquals(0x60, flow.titleFlags());                                          // continue, time attack
        assertEquals(3, flow.timeAttackUnlock());
    }

    @Test
    void goodFutureSelectsTheGoodBossFileAndIsRecordedPerZone() {
        CdProgramFlow flow = flow();
        flow.titleExited(CdProgramFlow.EXIT_NEW_GAME);
        flow.stageEnded(cleared(true));
        assertEquals(CdStagePayload.R13C, stage(flow.stageEnded(cleared(true))));
        flow.stageEnded(cleared(true));
        assertEquals(1, flow.goodFutures());                                            // bset #0,goodFutures
    }

    @Test
    void timeWarpRunsTheWarpThenThePeriodFileOfTheSameAct() {
        CdProgramFlow flow = flow();
        flow.titleExited(CdProgramFlow.EXIT_NEW_GAME);
        NotHosted warp = assertInstanceOf(NotHosted.class,
                flow.stageEnded(CdStageResult.warp(3, CdStageResult.TIME_PAST, false, 0)));
        assertEquals(CdNativeProgram.WARP, warp.program());
        assertEquals(CdStagePayload.R11B, stage(warp.then()));
        NotHosted future = assertInstanceOf(NotHosted.class,
                flow.stageEnded(CdStageResult.warp(3, CdStageResult.TIME_FUTURE, true, 0)));
        assertEquals(CdStagePayload.R11C, stage(future.then()));
        future = assertInstanceOf(NotHosted.class,
                flow.stageEnded(CdStageResult.warp(3, CdStageResult.TIME_FUTURE, false, 0)));
        assertEquals(CdStagePayload.R11D, stage(future.then()));
        assertEquals(CdStagePayload.R12A, stage(flow.stageEnded(cleared(false))));
    }

    @Test
    void specialStageRunsWithTheSavedStageAndContinuesWithTheNextAct() {
        CdProgramFlow flow = flow();
        flow.titleExited(CdProgramFlow.EXIT_NEW_GAME);
        var special = assertInstanceOf(CdProgramRequest.SpecialStage.class,
                flow.stageEnded(new CdStageResult(3, CdStageResult.TIME_PRESENT, false, true, 0)));
        assertEquals(com.openggf.game.soniccd.special.CdSpecialStageEntry.normal(0, 0), special.entry());
        assertEquals(CdStagePayload.R12A, stage(special.then()));
        // Results: the next stage and the stones are saved (Special Stage/Main.asm:295-296).
        var won = new com.openggf.game.soniccd.special.CdSpecialStageResult(0, true, 0, 0x01, 1, 40, 30, false, true);
        assertEquals(CdStagePayload.R12A, stage(flow.specialStageEnded(special, won)));
        assertEquals(1, flow.currentSpecialStage());
        var next = assertInstanceOf(CdProgramRequest.SpecialStage.class,
                flow.stageEnded(new CdStageResult(3, CdStageResult.TIME_PRESENT, false, true, 0x01)));
        assertEquals(com.openggf.game.soniccd.special.CdSpecialStageEntry.normal(1, 0x01), next.entry());
        // The seventh stone sets the good future (IPX.asm:593-597).
        var seventh = new com.openggf.game.soniccd.special.CdSpecialStageResult(1, true, 0, 0x7F, 0, 0, 0, false, true);
        flow.specialStageEnded(next, seventh);
        assertEquals(true, flow.goodFuture());
    }

    @Test
    void temporarySpecialStagesLeaveTheSaveAloneAndSoundTestRunsStageEight() {
        CdProgramFlow flow = flow();
        var ss8 = assertInstanceOf(CdProgramRequest.SpecialStage.class, flow.soundTestExited(1));
        assertEquals(com.openggf.game.soniccd.special.CdSpecialStageEntry.secret(), ss8.entry());
        var lost = new com.openggf.game.soniccd.special.CdSpecialStageResult(7, false, 6, 0, 0, 0, 0, false, false);
        assertInstanceOf(Title.class, flow.specialStageEnded(ss8, lost));
        assertEquals(0, flow.currentSpecialStage());
        var beaten = new com.openggf.game.soniccd.special.CdSpecialStageResult(7, false, 0, 0, 0, 0, 0, false, false);
        assertEquals(CdNativeProgram.SS8CREDS,
                assertInstanceOf(NotHosted.class, flow.specialStageEnded(ss8, beaten)).program());
    }

    @Test
    void gameOverReturnsToTheTitleAndContinueResumesTheSavedZone() {
        CdProgramFlow flow = flow();
        flow.titleExited(CdProgramFlow.EXIT_NEW_GAME);
        flow.stageEnded(cleared(false));
        flow.stageEnded(cleared(false));
        assertEquals(CdStagePayload.R31A, stage(flow.stageEnded(cleared(false))));
        assertInstanceOf(Title.class, flow.stageEnded(CdStageResult.gameOver(1, false, 0)));
        assertEquals(0x0100, flow.savedLevel());
        flow.enterTitle();
        assertEquals(CdStagePayload.R31A, stage(flow.titleExited(CdProgramFlow.EXIT_LOAD_GAME)));
    }

    @Test
    void stageSelectRunsOneStageThenReturnsToTheTitle() {
        CdProgramFlow flow = flow();
        assertInstanceOf(StageSelect.class, flow.titleExited(CdProgramFlow.EXIT_STAGE_SELECT));
        Stage selected = assertInstanceOf(Stage.class, flow.stageSelected(31));
        assertEquals(CdStagePayload.R43C, selected.payload());
        assertEquals(Route.STAGE_SELECT, selected.route());
        assertEquals(true, flow.goodFuture());
        // A warp from a stage-select stage still returns to the title (StageSelect: bsr RunMMD; rts).
        assertInstanceOf(Title.class, flow.stageEnded(CdStageResult.warp(3, CdStageResult.TIME_PAST, true, 0)));
        assertEquals(CdNativeProgram.WARP,
                assertInstanceOf(NotHosted.class, flow.stageSelected(8)).program());
        assertEquals(com.openggf.game.soniccd.special.CdSpecialStageEntry.demo(0),
                assertInstanceOf(CdProgramRequest.SpecialStage.class, flow.stageSelected(73)).entry());
        assertThrows(IllegalArgumentException.class, () -> flow.stageSelected(74));
    }

    @Test
    void unhostedTitleExitsReturnToTheTitleAndDemosCycle() {
        CdProgramFlow flow = flow();
        int[] exits = {CdProgramFlow.EXIT_TIME_ATTACK, CdProgramFlow.EXIT_BURAM_MANAGER, CdProgramFlow.EXIT_DA_GARDEN,
                CdProgramFlow.EXIT_VISUAL_MODE, CdProgramFlow.EXIT_SOUND_TEST, CdProgramFlow.EXIT_BEST_STAFF_TIMES};
        CdNativeProgram[] programs = {CdNativeProgram.TIMEATK, CdNativeProgram.BURAMMGR, CdNativeProgram.DAGARDEN,
                CdNativeProgram.VISMODE, CdNativeProgram.SNDTEST, CdNativeProgram.STAFFTIMES};
        for (int i = 0; i < exits.length; i++) {
            NotHosted step = assertInstanceOf(NotHosted.class, flow.titleExited(exits[i]));
            assertEquals(programs[i], step.program());
            assertInstanceOf(Title.class, step.then());
        }
        CdNativeProgram[] demos = {CdNativeProgram.OPENING, CdNativeProgram.R11ADEMO, CdNativeProgram.SPECSTAGE,
                CdNativeProgram.R43CDEMO, CdNativeProgram.SPECSTAGE, CdNativeProgram.R82ADEMO, CdNativeProgram.OPENING};
        for (CdNativeProgram demo : demos) {
            CdProgramRequest request = flow.titleExited(CdProgramFlow.EXIT_DEMO);
            if (demo == CdNativeProgram.SPECSTAGE) {
                // SpecStage1Demo / SpecStage6Demo (IPX.asm:111-131) run the special stage provider.
                assertInstanceOf(CdProgramRequest.SpecialStage.class, request);
            } else {
                assertEquals(demo, assertInstanceOf(NotHosted.class, request).program());
            }
        }
    }

    @Test
    void finishingMetallicMadnessRunsTheGoodEnding() {
        CdProgramFlow flow = flow();
        flow.titleExited(CdProgramFlow.EXIT_NEW_GAME);
        CdProgramRequest next = null;
        for (int i = 0; i < 21; i++) next = flow.stageEnded(cleared(true));
        // lane END: GameDone's ending is hosted (ending/CdEndingProvider).
        CdProgramRequest.Ending ending = assertInstanceOf(CdProgramRequest.Ending.class, next);
        assertEquals(true, ending.good());                                   // all seven good futures
        assertInstanceOf(Title.class, ending.then());
        assertEquals(0x7F, flow.goodFutures());
    }
}
