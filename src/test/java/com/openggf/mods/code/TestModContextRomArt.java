package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.io.ModAssetRoot;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TestModContextRomArt {

    private static RomArtRequest request() {
        return new RomArtRequest(0x50000, RomArtCompression.NEMESIS, 0, 0x60000, 0, 0, 1);
    }

    @Test
    void stagedRequestSurvivesFreezeUnderNamespacedKey() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerRomObjectArt("bird", request());
        ModRegistrationPlan plan = context.freeze();
        assertEquals(Set.of("owner:bird"), plan.romObjectArt().keySet());
        assertEquals(request(), plan.romObjectArt().get("owner:bird"));
    }

    @Test
    void standaloneContextRejectsRomArt() {
        ModContext context = new ModContext("owner", null, ModAssetRoot.forTests("owner"), null, true);
        assertThrows(ModRegistrationException.class,
                () -> context.registerRomObjectArt("bird", request()));
    }

    @Test
    void nonSonic2BaseGameRejected() {
        ModContext context = new ModContext("owner", "s1", ModAssetRoot.forTests("owner"));
        assertThrows(ModRegistrationException.class,
                () -> context.registerRomObjectArt("bird", request()));
    }

    @Test
    void addressBeyondStaticSonic2RomBoundRejected() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        RomArtRequest outOfBounds = new RomArtRequest(0x100000, RomArtCompression.NEMESIS,
                0, 0x60000, 0, 0, 1);
        assertThrows(ModRegistrationException.class,
                () -> context.registerRomObjectArt("bird", outOfBounds));
    }

    @Test
    void duplicateKeyAcrossBakedAndRomArtRejected() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerObjectArt("bird", new BakedSheetRef("art/bird.ggfs"));
        assertThrows(ModRegistrationException.class,
                () -> context.registerRomObjectArt("bird", request()));
    }

    @Test
    void namedSonic1ArtOnS3kStagesSourceAndUsesS1Bounds() {
        ModContext context = new ModContext("owner", "s3k", ModAssetRoot.forTests("owner"));
        context.registerRomObjectArt("bird", com.openggf.game.patch.LogicalRom.S1, request());
        assertEquals(com.openggf.game.patch.LogicalRom.S1, context.freeze().romObjectArt().get("owner:bird").source());
        ModContext bad = new ModContext("owner", "s3k", ModAssetRoot.forTests("owner"));
        assertThrows(ModRegistrationException.class, () -> bad.registerRomObjectArt("bad", com.openggf.game.patch.LogicalRom.S1,
                new RomArtRequest(0x80000, RomArtCompression.NEMESIS, 0, 0x100, 0, 0, 1)));
    }

    @Test
    void namedArtStillRejectsStandaloneAndS1Dplc() {
        ModContext standalone = new ModContext("owner", null, ModAssetRoot.forTests("owner"), null, true);
        assertThrows(ModRegistrationException.class, () -> standalone.registerRomObjectArt("bird", com.openggf.game.patch.LogicalRom.S1, request()));
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0, RomArtCompression.NEMESIS, 0, 1, 2, 0, 1,
                com.openggf.game.patch.LogicalRom.S1));
    }

    @Test
    void planWithoutRomArtHasEmptyMap() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        ModRegistrationPlan plan = context.freeze();
        assertTrue(plan.romObjectArt().isEmpty());
    }
}
