package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/** Canonical asset authority; no Slots variant, payout or route claim. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSlotRomLayout {
    @AfterEach void clearSession() { SessionManager.clear(); }

    private static RomByteReader original() throws Exception {
        String path = System.getProperty("s3k.rom.path");
        assertNotNull(path, "Use the original absolute locked-on ROM");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        CRC32 crc = new CRC32(); crc.update(bytes);
        assertEquals(0x63522553L, crc.getValue());
        return new RomByteReader(bytes);
    }

    @Test void canonicalRomEntryAndEveryLayoutCellMatchTheIndependentStockOracle() throws Exception {
        var data = S3kSlotRomData.loadLayout(original());
        assertEquals((short) 0x460, data.startX()); assertEquals((short) 0x360, data.startY());
        assertArrayEquals(S3kSlotLayoutFixture.layout(), data.layout());
        var buffers = S3kSlotRenderBuffers.fromLayout(data);
        assertArrayEquals(S3kSlotLayoutFixture.expandedLayout(), buffers.expandedLayout());
    }

    @Test void productionBootstrapUsesTheActualRomLayoutAndEntryWithoutASeparateFixtureCall() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var player = new Sonic("sonic", (short) 0, (short) 0);
        GameServices.sprites().addSprite(player); GameServices.camera().setFocusedSprite(player);
        var manager = GameServices.rom();
        Rom previous = manager.isRomAvailable() ? manager.getRom() : null;
        try (var rom = Rom.fromReader(original(), "original canonical Slots source")) {
            manager.setRom(rom);
            var runtime = new S3kSlotBonusStageRuntime(); runtime.bootstrap();
            assertTrue(runtime.isInitialized());
            var active = GameServices.sprites().getSprite("sonic");
            assertInstanceOf(S3kSlotBonusPlayer.class, active);
            assertEquals(0x460, active.getCentreX()); assertEquals(0x360, active.getCentreY());
            assertArrayEquals(S3kSlotLayoutFixture.layout(), runtime.renderBuffersForTest().layout());
            assertArrayEquals(S3kSlotLayoutFixture.expandedLayout(), runtime.renderBuffersForTest().expandedLayout());
            assertEquals(0x460, runtime.activeSlotCageForTest().getCurrentX());
            assertEquals(0x430, runtime.activeSlotCageForTest().getCurrentY());
        } finally { manager.setRom(previous); }
    }
}
