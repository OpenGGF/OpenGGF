package com.openggf.game.sonic2.kis2;

import com.openggf.data.RetailRomRevision;
import com.openggf.data.Rom;
import com.openggf.game.internal.RingCollectionPolicy;
import com.openggf.game.internal.ScoreAdditionPolicy;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2RevisionProfile;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

@RequiresRom(SonicGame.SONIC_2)
class TestKis2RetailRevisionIsolation {
    @Test void attachedRev00DoesNotReplaceTheLockOnProgramsOwnBehavior() throws Exception {
        var baseline = com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable().toPath().toAbsolutePath();
        String explicit = System.getProperty("sonic2.rev00.rom.path");
        var path = explicit == null ? baseline.resolveSibling("s2-r0.gen") : java.nio.file.Path.of(explicit);
        if (explicit == null) assumeTrue(java.nio.file.Files.isRegularFile(path), "Original REV00 image is optional");
        try (var rom = new Rom()) {
            assertTrue(rom.open(path.toString()));
            var stock = new Sonic2GameModule();
            stock.bindRetailRom(rom);
            assertEquals(RetailRomRevision.S2_REV00, stock.retailRevision());
            assertNotNull(stock.getGameService(RingCollectionPolicy.class));
            assertNotNull(stock.getGameService(ScoreAdditionPolicy.class));
            var lockOn = new Kis2GameModule(stock, mock(PatchContext.class));
            assertSame(Sonic2RevisionProfile.COMPATIBLE, lockOn.getGameService(Sonic2RevisionProfile.class));
            assertNull(lockOn.getGameService(RingCollectionPolicy.class));
            assertNull(lockOn.getGameService(ScoreAdditionPolicy.class));
            assertEquals(RetailRomRevision.S2_REV00, lockOn.getGameService(RetailRomRevision.class));
            assertSame(Kis2Rules.RULES, lockOn.getRules());
            assertTrue(stock.getGameService(Sonic2RevisionProfile.class).isRev00());
        }
    }
}
