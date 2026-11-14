package com.openggf.game.soniccd.ending;

import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdStageResult;
import com.openggf.game.soniccd.program.CdStageSelection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code GameDone} (IPX.asm:301-326) as the flow reports it: the last boss act asks for the ending, good when all
 * seven good futures (or time stones) are held, and the ending's return code decides between running it again and
 * {@code .GameLoop}. No media needed.
 */
class TestCdEndingFlow {
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
        for (int i = 0; i < CdStageSelection.MENU_ENTRIES; i++) {
            selections.add(new CdStageSelection(i, CdCommand.R11A, 0x000, CdStageResult.TIME_PRESENT, false));
        }
        return new CdProgramFlow(selections, sub);
    }

    private static CdProgramRequest playThrough(CdProgramFlow flow, boolean goodFutures) {
        flow.enterTitle();
        CdProgramRequest request = flow.titleExited(CdProgramFlow.EXIT_NEW_GAME);
        for (int stage = 0; stage < 7 * 3; stage++) {
            assertInstanceOf(CdProgramRequest.Stage.class, request, "stage " + stage);
            request = flow.stageEnded(CdStageResult.cleared(3, CdStageResult.TIME_PRESENT, goodFutures, 0));
        }
        return request;
    }

    @Test
    void allGoodFuturesRunTheGoodEndingThenTheTitle() {
        CdProgramFlow flow = flow();
        CdProgramRequest.Ending ending = assertInstanceOf(CdProgramRequest.Ending.class, playThrough(flow, true));
        assertEquals(true, ending.good());
        assertEquals("GoodEnding", ending.nativeCaller());
        assertEquals(0x7F, flow.endingId());                        // move.b #$7F,endingID
        assertEquals(0x7F, flow.goodFutures());
        assertInstanceOf(CdProgramRequest.Title.class, flow.endingReturned(ending, 0x10));   // rts to .GameLoop
        assertSame(ending, flow.endingReturned(ending, 0x80));      // bmi.s GoodEnding: again
    }

    @Test
    void anyBadFutureRunsTheBadEnding() {
        CdProgramFlow flow = flow();
        CdProgramRequest.Ending ending = assertInstanceOf(CdProgramRequest.Ending.class, playThrough(flow, false));
        assertEquals(false, ending.good());
        assertEquals("BadEnding", ending.nativeCaller());
        assertEquals(0, flow.endingId());
    }
}
