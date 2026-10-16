package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Loader boundaries and explicit component inputs, without a user ROM or fallback. */
class TestS3kSlotLayoutData {
    @AfterEach void clearSession() { SessionManager.clear(); }

    @Test void loaderReadsBothWordsAndAllBytesFromItsBoundedRomSource() {
        byte[] source = new byte[0x1E4084 + 1024];
        source[0x1E4080] = 4; source[0x1E4081] = 0x50;
        source[0x1E4082] = 3; source[0x1E4083] = 0x70;
        for (int i = 0; i < 1024; i++) source[0x1E4084 + i] = (byte) (i % 9);
        var data = S3kSlotRomData.loadLayout(new RomByteReader(source));
        assertEquals((short) 0x450, data.startX());
        assertEquals((short) 0x370, data.startY());
        for (int i = 0; i < 1024; i++) assertEquals((byte) (i % 9), data.layout()[i]);
        source[0x1E4084] = 99;
        assertEquals(0, data.layout()[0]);
    }

    @Test void dataAndEachRuntimeBufferOwnIndependentLayoutCopies() {
        byte[] original = S3kSlotLayoutFixture.layout();
        var data = new S3kSlotLayoutData((short) 0x460, (short) 0x360, original);
        original[0] = 8; data.layout()[1] = 8;
        var first = S3kSlotRenderBuffers.fromLayout(data);
        var second = S3kSlotRenderBuffers.fromLayout(data);
        first.layout()[2] = 8;
        assertArrayEquals(S3kSlotLayoutFixture.layout(), data.layout());
        assertArrayEquals(S3kSlotLayoutFixture.layout(), second.layout());
        assertArrayEquals(S3kSlotLayoutFixture.expandedLayout(), second.expandedLayout());
    }

    @Test void malformedOrTruncatedSourcesCannotBecomeAStockBoard() {
        assertThrows(NullPointerException.class, () -> S3kSlotRomData.loadLayout(null));
        assertThrows(IndexOutOfBoundsException.class,
                () -> S3kSlotRomData.loadLayout(new RomByteReader(new byte[0x1E4483])));
        assertThrows(IllegalArgumentException.class,
                () -> new S3kSlotLayoutData((short) 0, (short) 0, new byte[1023]));
        assertThrows(IllegalArgumentException.class,
                () -> S3kSlotRomData.buildExpandedLayoutBuffer(new byte[1025]));
    }

    @Test void failedProductionLoadLeavesAnAlreadyBootstrappedRuntimeAndPlayerUntouched() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var player = new Sonic("sonic", (short) 0x460, (short) 0x430);
        GameServices.sprites().addSprite(player);
        GameServices.camera().setFocusedSprite(player);
        var runtime = new S3kSlotBonusStageRuntime();
        S3kSlotLayoutFixture.bootstrap(runtime);
        assertTrue(runtime.isInitialized());
        var state = runtime.stageStateForTest(); var buffers = runtime.renderBuffersForTest();
        var cage = runtime.activeSlotCageForTest(); var active = GameServices.sprites().getSprite("sonic");
        var manager = GameServices.rom();
        Rom previous = manager.isRomAvailable() ? manager.getRom() : null;
        try (var truncated = Rom.fromReader(new RomByteReader(new byte[0x1E4483]), "truncated Slots test")) {
            manager.setRom(truncated);
            assertThrows(IndexOutOfBoundsException.class, runtime::bootstrap);
            assertTrue(runtime.isInitialized());
            assertSame(state, runtime.stageStateForTest()); assertSame(buffers, runtime.renderBuffersForTest());
            assertSame(cage, runtime.activeSlotCageForTest());
            assertSame(active, GameServices.sprites().getSprite("sonic"));
        } finally { manager.setRom(previous); }
    }
}
