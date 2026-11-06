package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual C/D programme bytes plus explicitly constructed scalar music selectors; no audio/World execution. */
class TestCdR13ProgrammeAssets {
    @Test void actualTableAndBothIndependentPcConsumersBelongToTheOriginalAggregate() throws Exception {
        for (var a : actual()) {
            var data = CdR13ProgrammeAssets.fromAssets(a);
            assertTrue(data.ownsAssets(a));
            assertFalse(data.ownsAssets(actual().get(a == actual().getFirst() ? 1 : 0)));
            assertEquals("800f1110801214138015171680181a19801b1d1c801e201f80216622",
                    HexFormat.of().formatHex(data.musicCommands().copy()));
            assertEquals(a.identity().file() == CdNativeFile.R13C ? 0x201A30 : 0x201A9C,
                    data.musicCommandsAddress());
        }
    }
    @Test void nativeFutureAdditionUsesRawWrappedByteAndTimeAttackSkipsIt() throws Exception {
        for (var a : actual()) {
            var data = CdR13ProgrammeAssets.fromAssets(a);
            assertEquals(CdCommand.R1DMUS, data.selectLevelMusic(2, 2, 0, 0));
            assertEquals(CdCommand.R1CMUS, data.selectLevelMusic(2, 0x82, 0, 1));
            assertEquals(CdCommand.R1AMUS, data.selectLevelMusic(2, 2, 0, 255));
            assertEquals(CdCommand.PASTMUS, data.selectLevelMusic(2, 2, 0, 254));
            for (int good = 0; good < 256; good++) {
                assertEquals(CdCommand.R1DMUS, data.selectLevelMusic(2, 2, 1, good));
                assertEquals(CdCommand.R1AMUS, data.selectLevelMusic(2, 1, 0, good));
            }
        }
    }
    @Test void nativeZoneWordIndexSelectsEveryCompleteTableRowWithoutNormalizingPeriod() throws Exception {
        int[] raw = {128,15,17,16,128,18,20,19,128,21,23,22,128,24,26,25,
                128,27,29,28,128,30,32,31,128,33,102,34};
        for (var a : actual()) {
            var data = CdR13ProgrammeAssets.fromAssets(a);
            for (int zone = 0; zone < 7; zone++) for (int period = 0; period < 4; period++)
                assertEquals(CdCommand.fromId(raw[zone * 4 + period]),
                        data.selectLevelMusic((zone << 8) | 2, period, 1, 0));
            assertThrows(CdR13ProgrammeAssets.UnownedMusicByte.class, () -> data.selectLevelMusic(0x702,0,1,0));
            assertThrows(CdR13ProgrammeAssets.UnownedMusicByte.class, () -> data.selectLevelMusic(2,2,0,27));
        }
    }
    @Test void widthsAndMissingSourceRejectWithoutExecutingAService() throws Exception {
        assertThrows(NullPointerException.class, () -> CdR13ProgrammeAssets.fromAssets(null));
        var data = CdR13ProgrammeAssets.fromAssets(actual().getFirst());
        for (int bad : new int[]{-1,256}) {
            assertThrows(IllegalArgumentException.class, () -> data.selectLevelMusic(2,bad,0,0));
            assertThrows(IllegalArgumentException.class, () -> data.selectLevelMusic(2,2,bad,0));
            assertThrows(IllegalArgumentException.class, () -> data.selectLevelMusic(2,2,0,bad));
        }
        assertThrows(IllegalArgumentException.class, () -> data.selectLevelMusic(65536,2,0,0));
    }
    private static List<CdR13FutureAssets> retained;
    private static synchronized List<CdR13FutureAssets> actual() throws Exception {
        if (retained != null) return retained;
        String root=System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Optional USA CD media absent");
        List<Path> cues;
        try(var paths=Files.list(Path.of(root).resolve("USA"))) {
            cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
        }
        assertEquals(1,cues.size());
        try(var p=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            retained=List.of(CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),
                    CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()));
        }
        return retained;
    }
}
