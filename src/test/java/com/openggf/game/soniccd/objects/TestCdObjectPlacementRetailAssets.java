package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.security.MessageDigest;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production authentication and metadata join only; no game loop/body is admitted. ALL UNRUN. */
class TestCdObjectPlacementRetailAssets {
    @Test
    void authenticatedUsaCommonLayoutProducesOnlyThreeParentsBeforeBodies() throws Exception {
        var assets = load(CdReleaseVariant.USA, "USA");
        var runtime = CdObjectPlacementRuntime.fromAssets(assets);
        assertSame(assets.placements(), runtime.placements());
        assertEquals(Optional.of(assets.identity()), runtime.sourceIdentity());
        assertEquals("7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2", assets.identity().mmdSha256());
        var records = assets.view(OBJECT_PLACEMENT_RECORDS);
        assertEquals(0x7906, records.range().fileOffset());
        assertEquals(3984, records.bytes().length());
        assertEquals("c5441a5a61b8c17cbac91d85a830f1560ca1c4e101df72e10326f0c7d9d71791",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(records.bytes().copy())));
        assertEquals(497, assets.placements().records().size());
        assertEquals(0x3E34, assets.view(OBJECT_INDEX).range().fileOffset());
        assertEquals(0x209C7EL, assets.view(OBJECT_INDEX).bytes().unsignedLong(4 * (0x10 - 1)));
        assertEquals(0x20B578L, assets.view(OBJECT_INDEX).bytes().unsignedLong(4 * (0x3D - 1)));
        runtime.spawnObjects(0, 1);
        int[][] expected = {{32, 3, 0xF0, 0x720, 0x10, 0x21, 3},
                {33, 8, 0x1F0, 0x765, 0x10, 0x22, 8}, {34, 11, 0x270, 0x730, 0x3D, 0, 10}};
        for (int[] fact : expected) {
            var row = assets.placements().records().get(fact[1]);
            var state = runtime.pool().state(fact[0]);
            assertEquals(row.centreX(), state.centreX());
            assertEquals(fact[2], state.centreX());
            assertEquals(fact[3], state.centreY());
            assertEquals(fact[4], state.id());
            assertEquals(fact[5], state.subtype());
            assertEquals(fact[6], state.savedFlagsId());
            assertEquals(0, state.routine());
            assertEquals(0x80, runtime.savedFlag(fact[6], 1));
        }
        assertEquals(3, runtime.pool().capture().slots().stream().filter(slot -> slot.id() != 0).count());
        assertEquals(12, runtime.status().rightRecordIndex());
        assertEquals(0x286, assets.placements().records().get(12).centreX());
        assertEquals(11, runtime.status().rightSavedId());
        assertEquals(0, runtime.status().leftRecordIndex());
        assertEquals(1, runtime.status().leftSavedId());
        assertEquals(0, runtime.pool().state(35).id());
        var otherOwner = CdObjectPlacementRuntime.fromAssets(assets);
        assertThrows(IllegalArgumentException.class, () -> otherOwner.restore(runtime.capture()));
    }

    @Test
    void newProductionPrerequisiteExplicitlyRejectsUnauditedRegionalObjectBindings() throws Exception {
        for (var variant : new CdReleaseVariant[]{CdReleaseVariant.JAPAN, CdReleaseVariant.EUROPE}) {
            var assets = load(variant, variant == CdReleaseVariant.JAPAN ? "Japan" : "Europe");
            assertThrows(UnsupportedOperationException.class, () -> CdObjectPlacementRuntime.fromAssets(assets));
            assertEquals(497, assets.placements().records().size(), "Existing regional assets are unchanged");
        }
    }

    private static CdR11aAssets load(CdReleaseVariant variant, String directory) throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for supplied-media checks");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve(directory))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured region needs exactly one actual CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
            return CdR11aAssetLoader.load(source, variant, CdStageKey.parse("R11A"));
        }
    }
}
