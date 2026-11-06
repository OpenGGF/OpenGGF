package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Actual disc-backed detached geometry. No programme, World, service or current-art admission. */
class TestSonicCdAct3FutureLevelRetail {
    @Test void bothOriginalProgrammesExposeTheirOwnGeometryAndNativeBounds() throws Exception {
        for (var assets : actual()) {
            var level = new SonicCdAct3FutureLevel(assets);
            assertSame(assets, level.assets());
            assertEquals(assets.terrain().patternCount(), level.getPatternCount());
            assertEquals(assets.terrain().blockCount(), level.getChunkCount());
            assertEquals(107, level.getBlockCount()); // 106 native chunks plus the native blank-layout cell.
            assertEquals(15, level.getLayerWidthBlocks(0));
            assertEquals(4, level.getLayerHeightBlocks(0));
            assertEquals(10, level.getLayerWidthBlocks(1));
            assertEquals(2, level.getLayerHeightBlocks(1));
            assertEquals(0, level.getMinX()); assertEquals(0xD97, level.getMaxX());
            assertEquals(0, level.getMinY()); assertEquals(0x310, level.getMaxY());
            assertEquals(new CdR11bLoadPlan.InitialCentre(0x40, 0x2A2), level.initialCentre());
            assertSame(assets.placements(), level.nativePlacements());
            assertEquals(2, level.getBackdropPaletteLine());
        }
    }

    @Test void detachedConversionCannotInventCurrentPatternsOrNativeCollisionExecution() throws Exception {
        for (var assets : actual()) {
            var level = new SonicCdAct3FutureLevel(assets);
            assertThrows(UnsupportedOperationException.class,
                    () -> level.ensurePatternCapacity(assets.terrain().patternCount() + 1));
            assertThrows(UnsupportedOperationException.class,
                    () -> level.resolveCollisionBlockIndex(0, 0, 0));
            assertEquals(assets.initialCentre(), level.initialCentre());
        }
    }

    @Test void missingAggregateCannotSupplyAFallbackStage() {
        assertThrows(NullPointerException.class, () -> new SonicCdAct3FutureLevel(null));
    }

    private static List<CdR13FutureAssets> retained;
    private static synchronized List<CdR13FutureAssets> actual() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(root != null && !root.isBlank(), "Optional USA CD media absent");
        List<Path> cues;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
        }
        assertEquals(1, cues.size());
        try (var programs = CdProgramAssets.open(cues.getFirst(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = List.of(CdR13FutureAssets.fromPrograms(programs, CdStagePayload.R13C.stage()),
                    CdR13FutureAssets.fromPrograms(programs, CdStagePayload.R13D.stage()));
        }
        return retained;
    }
}
