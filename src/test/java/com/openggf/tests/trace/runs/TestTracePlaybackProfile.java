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
    void sonic2ProfilesItsMeasuredPerDestinationLevelEntryMask() {
        var profile = new Sonic2GameModule().getTracePlaybackProfile();
        assertTrue(profile.alignsInterLevelVblank());
        // The one masked stretch between Level: (docs/s2disasm/s2.asm:4757) and
        // Level_MainLoop (:5088) is line 4768's move #$2700,sr over ClearScreen
        // + LoadTitleCard. Measured 10 at 20 of 21 level->level boundaries of
        // the complete-emerald run, and 10 in every independently recorded
        // s2-lvl-select-*.bk2 act advance that has a second witness.
        assertEquals(10, profile.interLevelNonAdvancingMovieRows());
        assertEquals(10, profile.interLevelNonAdvancingMovieRows(0, 2), "EHZ2 entry");
        assertEquals(10, profile.interLevelNonAdvancingMovieRows(6, 1), "OOZ1 entry");
        assertEquals(10, profile.interLevelNonAdvancingMovieRows(7, 3), "MTZ3 entry");
        // The single deviant destination: entering OOZ act 2 measures 9 in BOTH
        // the complete-emerald run and s2-lvl-select-OOZ.bk2.
        assertEquals(9, profile.interLevelNonAdvancingMovieRows(6, 2), "OOZ2 entry");
    }

    @Test
    void otherGamesLeaveMovieClockAlignmentDisabledUntilMeasured() {
        assertFalse(new Sonic2GameModule().getTracePlaybackProfile()
                .alignsStageResultsPresentationVblank());
        assertFalse(new Sonic3kGameModule().getTracePlaybackProfile()
                .alignsStageResultsPresentationVblank());
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
