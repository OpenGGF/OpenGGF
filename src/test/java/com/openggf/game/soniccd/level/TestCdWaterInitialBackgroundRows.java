package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Component DATA/read-fact controls; no World, renderer or native execution claim. */
class TestCdWaterInitialBackgroundRows {
    private static final CdR11aBackgroundPlane.FlagWords FLAGS =
            new CdR11aBackgroundPlane.FlagWords(0x005A, 0xA55A, 0x5AA5);

    @Test void nativeCompressionThresholdsAndFollowingRowRoundingHaveIndependentWordOracles() {
        // loc_2037C4/EA/800, native r4/stage_draw_1a.asm:542-568.
        // The following-row tie comes from the bounded completed block-row output,
        // translated to an integer selector rule, with no CPU/register/VDP model.
        int[][] rows = {{0,0,0,0}, {0x1FF,0x27F,0x1FF,0x1FF},
                {0x1FF,0x280,0x200,0x200}, {0x200,0,0x200,0x200},
                {0x380,0,0x320,0x320}, {0x215,0,0x20F,0x210},
                {0xFFFF,0,0xC07F,0xC07F}};
        for (int[] row : rows) {
            assertEquals(row[2], CdR11aBackgroundPlane.waterInitialSelectorYWord(row[0],row[1],true));
            assertEquals(row[3], CdR11aBackgroundPlane.waterInitialSelectorYWord(row[0],row[1],false));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CdR11aBackgroundPlane.waterInitialSelectorYWord(0x10000,0,true));
        assertThrows(IllegalArgumentException.class,
                () -> CdR11aBackgroundPlane.waterInitialSelectorYWord(0,-1,true));
    }

    @Test void actualR41aInitializerReadsTheNativeCompressedSelectorIndex() throws Exception {
        var source=CdProgrammeData.fromReader(TestCdR4TerrainData.reader(CdR4DataLayout.Programme.R41A))
                .levelLoadingSource();
        var owner=source.backgroundPlane();var before=seed();var live=cameras(0x380);
        var result=prefix(() -> owner.initializeWaterCompressedRows(before,live,FLAGS,0));
        var first=result.sections().getFirst();
        assertEquals(0x32,first.tableIndex());assertEquals(-16,first.relativeY());
        assertEquals(source.backgroundSectionIds().unsignedByte(0x32),first.selector());
        assertEquals(FLAGS,result.flags());
        assertArrayEquals(seed().nameTable().copy(),before.nameTable().copy());
    }

    @Test void zeroCameraUsesTheSameRowsAndNameTableAsTheExistingDirectKernel() throws Exception {
        var source=CdProgrammeData.fromReader(TestCdR4TerrainData.reader(CdR4DataLayout.Programme.R41A))
                .levelLoadingSource();
        var owner=source.backgroundPlane();var before=seed();var live=cameras(0);
        var direct=prefix(() -> owner.initialize(before,live,FLAGS));
        var water=prefix(() -> owner.initializeWaterCompressedRows(before,live,FLAGS,0));
        assertEquals(direct.sections(),water.sections());assertEquals(direct.blocks(),water.blocks());
        assertEquals(direct.flags(),water.flags());
        assertArrayEquals(direct.state().nameTable().copy(),water.state().nameTable().copy());
    }

    @Test void originalR11DirectSelectorKeepsItsUncompressedSourceIndex() throws Exception {
        try (var media=TestCdCczProgrammeData.open()) {
            var assets=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,CdStagePayload.R11A.stage());
            var owner=CdR11aBackgroundPlane.fromViews(assets.terrain(),
                    assets.view(CdR11aAsset.BG_CAMERA_SECT_IDS),assets.view(CdR11aAsset.BG_CAMERA_SECTS));
            var result=prefix(() -> owner.initialize(seed(),cameras(0x380),FLAGS));
            assertEquals(0x38,result.sections().getFirst().tableIndex());
        }
    }

    private interface Draw { CdR11aBackgroundPlane.Result run(); }
    private static CdR11aBackgroundPlane.Result prefix(Draw draw) {
        // A later unbacked geometry read does not invalidate an earlier selector read.
        // It is not accepted as a completed plane or pixel result.
        try { return draw.run(); }
        catch (CdR11aBackgroundPlane.InvalidPlane laterRead) {
            var reached=laterRead.completedPrefix().orElseThrow();
            assertFalse(reached.sections().isEmpty());return reached;
        }
    }
    private static CdR11aBackgroundPlane.State seed() {
        byte[] bytes=new byte[4096];Arrays.fill(bytes,(byte)0x5A);
        return new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(bytes));
    }
    private static CdR11aBackgroundPlane.LiveCameras cameras(int y) {
        var camera=new CdR11aBackgroundPlane.Camera(0,y<<16);
        return new CdR11aBackgroundPlane.LiveCameras(camera,camera,camera);
    }
}
