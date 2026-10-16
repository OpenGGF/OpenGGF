package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
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
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    private static RomByteReader original() throws Exception {
        String path = System.getProperty("s3k.rom.path");
        assertNotNull(path, "Use the original absolute locked-on ROM");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        CRC32 crc = new CRC32(); crc.update(bytes);
        assertEquals(0x63522553L, crc.getValue());
        return new RomByteReader(bytes);
    }

    @Test void canonicalRomEntryAndEveryLayoutCellMatchTheIndependentStockOracle() throws Exception {
        var bank = S3kSlotResources.fromRom(original());
        assertEquals((short) 0x460, bank.playerStartX()); assertEquals((short) 0x360, bank.playerStartY());
        assertArrayEquals(S3kSlotRomTestData.SLOT_BONUS_LAYOUT, bank.layout());
        var buffers = S3kSlotRenderBuffers.fromRomData(bank);
        assertArrayEquals(S3kSlotRomTestData.buildExpandedLayoutBuffer(), buffers.expandedLayout());
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
            assertArrayEquals(S3kSlotRomTestData.SLOT_BONUS_LAYOUT, runtime.renderBuffersForTest().layout());
            assertArrayEquals(S3kSlotRomTestData.buildExpandedLayoutBuffer(),
                    runtime.renderBuffersForTest().expandedLayout());
            assertEquals(0x460, runtime.activeSlotCageForTest().getCurrentX());
            assertEquals(0x430, runtime.activeSlotCageForTest().getCurrentY());
        } finally { manager.setRom(previous); }
    }

    /** sub_4B6AA's whole source resolves before the runtime changes; a bad ROM leaves play untouched. */
    @Test void failedProductionLoadLeavesAnAlreadyBootstrappedRuntimeAndPlayerUntouched() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var player = new Sonic("sonic", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(player);
        GameServices.camera().setFocusedSprite(player);
        var manager = GameServices.rom();
        Rom previous = manager.isRomAvailable() ? manager.getRom() : null;
        try (var rom = Rom.fromReader(original(), "original canonical Slots source")) {
            manager.setRom(rom);
            var runtime = new S3kSlotBonusStageRuntime();
            runtime.bootstrap();
            assertTrue(runtime.isInitialized());
            var state = runtime.stageStateForTest(); var buffers = runtime.renderBuffersForTest();
            var cage = runtime.activeSlotCageForTest(); var active = GameServices.sprites().getSprite("sonic");
            try (var truncated = Rom.fromReader(new RomByteReader(new byte[0x1E4483]), "truncated Slots test")) {
                manager.setRom(truncated);
                assertThrows(IllegalStateException.class, runtime::bootstrap);
                assertTrue(runtime.isInitialized());
                assertSame(state, runtime.stageStateForTest()); assertSame(buffers, runtime.renderBuffersForTest());
                assertSame(cage, runtime.activeSlotCageForTest());
                assertSame(active, GameServices.sprites().getSprite("sonic"));
            }
        } finally { manager.setRom(previous); }
    }
}
