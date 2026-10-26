package com.openggf.game;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.preview.GeneratedTitlePreviewSequence;
import com.openggf.game.preview.TitlePreviewProvider;
import com.openggf.game.preview.TitlePreviewProvider.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestTitlePreviewCapabilities {
    @Test
    void incompleteCdMenusDoNotBorrowCartridgeTitleOrAudioCapabilities() {
        assertSame(TitlePreviewProvider.NONE, GameModuleRegistry.createStockTitlePreview(GameId.SCD));
        assertThrows(IllegalArgumentException.class, () -> GameModuleRegistry.createStockMenuAudio(GameId.SCD));
        assertThrows(IllegalArgumentException.class, () -> GameModuleRegistry.stockRomSoundCatalog(GameId.SCD));
    }

    @Test
    void stockCapabilityResolutionDoesNotReadLiveServices() {
        try (var liveServices = mockStatic(GameServices.class)) {
            for (GameId game : GameId.values()) {
                assertNotNull(GameModuleRegistry.createStockTitlePreview(game));
            }
            liveServices.verifyNoInteractions();
        }
        assertSame(TitlePreviewProvider.NONE, GameModuleRegistry.createStockTitlePreview(GameId.STANDALONE));
    }

    @ParameterizedTest
    @EnumSource(MasterTitleScreen.GameEntry.class)
    void dispatchesCardIdentityAndExactSuppliedRom(MasterTitleScreen.GameEntry game) throws Exception {
        try (Rom rom = Rom.fromReader(RomByteReader.fromBytes(new byte[] {42}), "explicit source")) {
            var sequence = MasterTitleRomPreview.loadSequenceFor(game, rom, id -> {
                assertEquals(GameId.fromCode(game.gameId), id);
                return supplied -> {
                    assertSame(rom, supplied);
                    byte value = supplied.readBytes(0, 1)[0];
                    return Optional.of(new GeneratedTitlePreviewSequence(1, 1, 0, tick -> 0,
                            token -> new Image(1, 1, new byte[] {value, 0, 0, -1})));
                };
            }).orElseThrow();
            assertEquals(42, sequence.imageAt(0).rgba()[0]);
            assertTrue(rom.isOpen(), "the loader must not close a caller-owned ROM");
        }
    }

    @Test
    void unsupportedBlankAndBrokenProvidersLeavePreviewAbsent() throws Exception {
        try (Rom rom = Rom.fromReader(RomByteReader.fromBytes(new byte[] {1}), "source")) {
            var game = MasterTitleScreen.GameEntry.SONIC_1;
            assertTrue(MasterTitleRomPreview.loadSequenceFor(game, rom, id -> TitlePreviewProvider.NONE).isEmpty());
            assertTrue(MasterTitleRomPreview.loadSequenceFor(game, rom, id -> supplied -> {
                throw new IOException("unreadable preview data");
            }).isEmpty());
            assertTrue(MasterTitleRomPreview.loadSequenceFor(game, rom, id -> supplied -> {
                throw new IllegalArgumentException("invalid preview data");
            }).isEmpty());
            assertTrue(MasterTitleRomPreview.loadSequenceFor(game, rom, id -> supplied -> Optional.of(
                    new GeneratedTitlePreviewSequence(1, 1, 0, tick -> 0,
                            token -> new Image(1, 1, new byte[4])))).isEmpty());
        }
        GameModule unsupported = mock(GameModule.class, CALLS_REAL_METHODS);
        assertSame(TitlePreviewProvider.NONE, unsupported.getTitlePreviewProvider());
    }

    @Test
    void invalidSourceNeverResolvesProvider() {
        assertTrue(MasterTitleRomPreview.loadSequenceFor(MasterTitleScreen.GameEntry.SONIC_1,
                (Rom) null, id -> { throw new AssertionError("must not resolve"); }).isEmpty());
        assertTrue(MasterTitleRomPreview.loadSequenceFor(null, new Rom(),
                id -> { throw new AssertionError("must not resolve"); }).isEmpty());
    }

    @Test
    void tokenCacheClampsNegativeTicksAndReusesRepeatedAnimationStates() {
        AtomicInteger generated = new AtomicInteger();
        var sequence = new GeneratedTitlePreviewSequence(1, 1, 5, tick -> tick % 2, token -> {
            generated.incrementAndGet();
            return new Image(1, 1, new byte[] {(byte) token, 0, 0, -1});
        });
        assertEquals(0, sequence.frameTokenAt(-99));
        assertEquals(1, sequence.frameTokenAt(5));
        Image first = sequence.imageAt(-1);
        Image second = sequence.imageAt(1);
        assertSame(first, sequence.imageAt(100));
        assertSame(second, sequence.imageAt(101));
        assertEquals(2, generated.get());
        assertEquals(1, second.rgba()[0]);
    }

    @Test
    void imagesRejectOverflowAndSequencesRejectChangingDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new Image(1 << 30, 4, new byte[0]));
        var sequence = new GeneratedTitlePreviewSequence(1, 1, 0, tick -> tick,
                token -> new Image(2, 1, new byte[8]));
        assertThrows(IllegalArgumentException.class, () -> sequence.imageAt(0));
    }
}
