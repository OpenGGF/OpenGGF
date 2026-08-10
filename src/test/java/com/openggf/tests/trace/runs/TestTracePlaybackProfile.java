package com.openggf.tests.trace.runs;

import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTracePlaybackProfile {

    @Test
    void sonic1ProfilesSixNonAdvancingRowsAtOrdinaryLevelSeams() {
        var profile = new Sonic1GameModule().getTracePlaybackProfile();
        assertTrue(profile.alignsInterLevelVblank());
        assertEquals(6, profile.interLevelNonAdvancingMovieRows());
        assertTrue(profile.alignUncomparedInteriorReturnVblank());
        assertTrue(profile.reinitializeOscillationAtLoadedLevelAttach());
        assertEquals(new com.openggf.game.profiles.trace.TracePlaybackProfile.LevelIdentity(5, 2),
                profile.resolveRecordedLevel(1, 4), "ROM LZ4 is engine SBZ3");
        assertEquals(new com.openggf.game.profiles.trace.TracePlaybackProfile.LevelIdentity(6, 0),
                profile.resolveRecordedLevel(5, 3), "ROM SBZ3 is engine Final Zone");
    }

    @Test
    void sonic1ProfilesSevenNonAdvancingRowsAtItsStageResultsEntry() {
        var profile = new Sonic1GameModule().getTracePlaybackProfile();
        assertTrue(profile.alignsStageResultsPresentationVblank());
        // SS_Finish's disable_ints ... ClearScreen / NemDec Nem_TitleCard /
        // Hud_Base ... enable_ints block (docs/s1disasm/sonic.asm:3369-3383).
        assertEquals(7, profile.stageResultsEntryNonAdvancingMovieRows());
    }

    @Test
    void otherGamesLeaveMovieClockAlignmentDisabledUntilMeasured() {
        assertFalse(new Sonic2GameModule().getTracePlaybackProfile()
                .alignsStageResultsPresentationVblank());
        assertFalse(new Sonic3kGameModule().getTracePlaybackProfile()
                .alignsStageResultsPresentationVblank());
        assertFalse(new Sonic2GameModule().getTracePlaybackProfile().alignsInterLevelVblank());
        assertFalse(new Sonic3kGameModule().getTracePlaybackProfile().alignsInterLevelVblank());
        assertFalse(new Sonic2GameModule().getTracePlaybackProfile()
                .alignUncomparedInteriorReturnVblank());
        assertFalse(new Sonic3kGameModule().getTracePlaybackProfile()
                .alignUncomparedInteriorReturnVblank());
        assertFalse(new Sonic2GameModule().getTracePlaybackProfile()
                .reinitializeOscillationAtLoadedLevelAttach());
        assertFalse(new Sonic3kGameModule().getTracePlaybackProfile()
                .reinitializeOscillationAtLoadedLevelAttach());
    }
}
