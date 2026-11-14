package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdPpzDataLayout;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate C smoke: every one of the 70 stage payloads decodes through the one loader without
 * error. This is a load check, not certification of any stage. The R11A catalogue offsets
 * are also checked against K3a's independently derived PPZ data layout.
 */
class TestCdStageLoaderAllPayloads {
    @Test
    void catalogueAgreesWithTheCarriedPalmtreePanicLayouts() {
        for (CdPpzDataLayout.Programme programme : CdPpzDataLayout.Programme.values()) {
            var entry = CdStageCatalogue.entry(programme.payload());
            assertEquals(programme.range("LevelDataIndex").fileOffset(), entry.levelDataIndex(), programme + " index");
            assertEquals(programme.range("PLCLists").fileOffset(), entry.plcLists(), programme + " PLCs");
            assertEquals(programme.range("LevelLayouts").fileOffset(), entry.levelLayouts(), programme + " layouts");
            assertEquals(programme.range("LevelCollision").fileOffset(), entry.levelCollision(), programme + " collision");
            assertEquals(programme.range("ColAngleMap").fileOffset(), entry.colAngleMap(), programme + " angles");
            assertEquals(programme.range("ColHeightMap").fileOffset(), entry.colHeightMap(), programme + " heights");
            assertEquals(programme.range("ColWidthMap").fileOffset(), entry.colWidthMap(), programme + " widths");
            assertEquals(programme.range("ObjectLayouts").fileOffset(), entry.objectLayouts(), programme + " objects");
            assertEquals(programme.range("PlayerMappings").fileOffset(), entry.playerMappings(), programme + " mappings");
            assertEquals(programme.range("PlayerDplc").fileOffset(), entry.playerDplc(), programme + " DPLCs");
        }
    }

    @Test
    void everyStagePayloadLoads() throws Exception {
        List<String> failures = new ArrayList<>();
        int loaded = 0;
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdStageLoader loader = new CdStageLoader(disc);
            for (CdStagePayload payload : CdStagePayload.values()) {
                try {
                    CdLevel level = loader.load(payload, SonicCdZoneRegistry.zoneIndex(payload), false);
                    if (level.getPatternCount() == 0 || level.getChunkCount() == 0 || level.getBlockCount() < 2
                            || level.getMap().getWidth() == 0 || level.getMaxX() <= level.getMinX()
                            || level.startX() < level.getMinX() || level.startX() > level.getMaxX() + 320) {
                        failures.add(payload + ": implausible decode");
                    }
                    loaded++;
                } catch (Exception | Error e) {
                    failures.add(payload + ": " + e);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertEquals(70, loaded);
    }
}
