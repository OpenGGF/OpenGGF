package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerGroundCollision;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/** Original USA Past format queries at one fixed cleared native row, not a player/World ingress. */
class TestCdStageTerrainQueriesRetail {
    private static CdR11bAssets assets;

    @BeforeAll
    static void loadActualPast() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for original USA R11B");
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA directory must contain exactly one original CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void actualPastChunksUseTheNative210000BaseAndClearedFourthRow() {
        var t = assets.terrain();
        assertEquals(0x210000, assets.loadPlan().levelIndex().chunksNativeAddress());
        assertEquals(4, t.layout().rows(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(800, t.collision().ids().length()); assertEquals(54272, t.chunks().length());
        for (int x = 0; x < 64; x++) assertEquals(0, t.layout().nativeCell(CdStageTerrain.Plane.FOREGROUND, x, 4).rawByte());
        var p = assertInstanceOf(CdTerrainCollision.Blank.class, CdTerrainCollision.fromStageTerrain(t).lookup(0x20, 0x420, 0));
        assertEquals(new CdTerrainCollision.Address(0x200, 0, 0, 0), p.address());
        assertEquals(0x210000, p.address().nativeAddress());
    }

    @Test
    void actualClearRowHasLiteralFirstAndExtensionDistancesWithoutAngleStores() {
        var t = assets.terrain(); var q = CdPlayerTerrain.fromStageTerrain(t);
        byte[] chunks = t.chunks().copy(), ids = t.collision().ids().copy();
        var writes = new ArrayList<Integer>();
        assertEquals(new CdPlayerTerrain.Result(31, OptionalInt.empty()),
                q.floorWithPrefix(0x20, 0x420, 13, 0, 16, 0, writes::add));
        assertEquals(new CdPlayerTerrain.Result(15, OptionalInt.empty()), q.floorExtension(0x20, 0x420, 13, 0, 16, 0));
        assertEquals(new CdPlayerTerrain.Result(31, OptionalInt.empty()), q.wall(0x20, 0x420, 13, 0, 16, 0));
        assertEquals(new CdPlayerTerrain.Result(15, OptionalInt.empty()), q.wallExtension(0x20, 0x420, 13, 0, 16, 0));
        assertEquals(List.of(), writes); assertArrayEquals(chunks, t.chunks().copy()); assertArrayEquals(ids, t.collision().ids().copy());
    }

    @Test
    void actualTerrainGroundCompositionKeepsTheNativeSupportBypass() {
        var old = new CdPlayerGroundCollision.State(0x1234ABCD, 0x56789ABC, 0x81, 8, 0x5A);
        assertEquals(new CdPlayerGroundCollision.Result(old, 0, 0),
                CdPlayerGroundCollision.fromStageTerrain(assets.terrain()).ground(
                        new CdPlayerGroundCollision.Input(old, 9, 19, 0x44, 0)));
    }
}
