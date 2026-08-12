package com.openggf.tools.audio.completerun.s3k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.LiveSfx;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.SavedMusic;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.RomPointer;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestS3kCompleteRunStateDecoder {
    private static final int TRACKS = 0x40;
    private static final int OVERLAP = TRACKS + 9 * 0x30;

    @Test
    void authenticatesTheLockedOnRomAndExposesTheSourceExactBankRanges() throws Exception {
        S3kCompleteRunAssetCatalog catalog = S3kCompleteRunAssetCatalog.load(rom());

        assertEquals(List.of(
                "music.bank.1c", "music.bank.1d", "music.bank.59", "music.bank.5a",
                "music.bank.5b", "sfx.bank.1f", "z80.driver", "z80.driver-data"),
                catalog.assets().keySet().stream().sorted().toList());
        assertEquals(0x0e4104L, catalog.assets().get("music.bank.1c").romBase());
        assertEquals(0x0e8000L, catalog.assets().get("music.bank.1c").romEndExclusive());
        assertEquals(0x2c8000L, catalog.assets().get("music.bank.59").romBase());
        assertEquals(0x2d0000L, catalog.assets().get("music.bank.59").romEndExclusive());
        assertEquals(0x0f8000L, catalog.assets().get("sfx.bank.1f").romBase());
        assertEquals(0x100000L, catalog.assets().get("sfx.bank.1f").romEndExclusive());
        assertEquals(0x1c00L, catalog.assets().get("z80.driver-data").romEndExclusive());
    }

    @Test
    void resolvesTheUnifiedVoiceTableInsideTheSecondInstalledDriverImage() throws Exception {
        byte[] raw = baseState();
        word(raw, 0x37, 0x17d8);
        track(raw, TRACKS, 0x80, 0x06, 0x8120, 0x30);
        track(raw, TRACKS + 0x30, 0x80, 0x00, 0x81bc, 0x30);
        word(raw, TRACKS + 0x30 + 0x1c, 0x1a45);

        S3kCompleteRunAssetCatalog catalog = S3kCompleteRunAssetCatalog.load(rom());
        var snapshot = S3kCompleteRunStateDecoder.decode(raw, catalog);

        assertEquals("z80.driver-data", snapshot.globals().voiceTablePointer().assetKey());
        assertEquals(0x17d8L, snapshot.globals().voiceTablePointer().pointer());
        assertEquals("z80.driver-data", snapshot.musicTracks().get(1).totalLevelPointer().assetKey());
        assertEquals(0x1a45L, snapshot.musicTracks().get(1).totalLevelPointer().pointer());
    }

    @Test
    void decodesLiveMusicAndSfxPointersThroughTheirOwningBanks() throws Exception {
        byte[] raw = baseState();
        track(raw, TRACKS, 0x80, 0x06, 0x8120, 0x30);
        track(raw, OVERLAP, 0x80, 0x02, 0xde3a, 0x30);

        S3kCompleteRunAssetCatalog catalog = S3kCompleteRunAssetCatalog.load(rom());
        var snapshot = S3kCompleteRunStateDecoder.decode(raw, catalog);

        assertEquals("music.bank.59", snapshot.musicTracks().getFirst().dataPointer().assetKey());
        assertEquals(0x2c8120L, snapshot.musicTracks().getFirst().dataPointer().pointer());
        LiveSfx overlap = assertInstanceOf(LiveSfx.class, snapshot.overlap());
        assertEquals("sfx.bank.1f", overlap.tracks().getFirst().dataPointer().assetKey());
        assertEquals(0x0fde3aL, overlap.tracks().getFirst().dataPointer().pointer());
        assertFalse(snapshot.musicTracks().get(1).populated());
        S3kCompleteRunAudioProfile.profile().validateState(
                S3kCompleteRunStateNormalizer.normalizeReference(snapshot, catalog.assets()));
    }

    @Test
    void decodesEveryLivePointerUnionFromItsLittleEndianOwningBytes() throws Exception {
        byte[] raw = baseState();
        int fm1 = TRACKS + 0x30;
        track(raw, fm1, 0x80, 0x00, 0x81bc, 0x2e);
        raw[fm1 + 7] = (byte) 0x80;
        word(raw, fm1 + 0x0d, 0x3456);
        raw[fm1 + 0x18] = (byte) 0x80;
        word(raw, fm1 + 0x19, 0x1a13);
        word(raw, fm1 + 0x1c, 0x1a45);
        word(raw, fm1 + 0x20, 0x8200);
        word(raw, fm1 + 0x22, 0x1234);
        for (int offset = 0; offset < 6; offset++) raw[fm1 + 0x28 + offset] = (byte) (offset + 1);
        word(raw, fm1 + 0x2e, 0x8300);

        var snapshot = S3kCompleteRunStateDecoder.decode(raw,
                S3kCompleteRunAssetCatalog.load(rom()));
        var track = snapshot.musicTracks().get(1);

        assertEquals(0x3456, track.frequencyOrDac());
        assertEquals(new RomPointer("z80.driver-data", 0x1a13), track.ssgEgPointer());
        assertEquals(new RomPointer("z80.driver-data", 0x1a45), track.totalLevelPointer());
        assertEquals(new RomPointer("music.bank.59", 0x2c8200), track.modulationPointer());
        assertEquals(0x1234, track.modulationValue());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 0x00, 0x83), track.sharedStorage());
        assertEquals(List.of(new RomPointer("music.bank.59", 0x2c8300)), track.returnStack());
    }

    @Test
    void ignoresEveryStaleUnionByteOnAnInactiveLiveTrack() throws Exception {
        byte[] raw = baseState();
        int stale = TRACKS + 0x30;
        for (int offset = 0; offset < 0x30; offset++) raw[stale + offset] = (byte) 0xff;
        raw[stale] = 0;

        var snapshot = S3kCompleteRunStateDecoder.decode(raw,
                S3kCompleteRunAssetCatalog.load(rom()));

        assertFalse(snapshot.musicTracks().get(1).populated());
    }

    @Test
    void decodesAllNineFixSndbugsZeroSavedTracksFromTheOverlappingRegion() throws Exception {
        byte[] raw = baseState();
        raw[0x16] = 0x29;
        raw[0x2d] = 0x59;
        word(raw, 0x2a, 0x8240);
        int[] voices = {0x06, 0x00, 0x01, 0x02, 0x04, 0x05, 0x80, 0xa0, 0xc0};
        for (int index = 0; index < voices.length; index++) {
            track(raw, OVERLAP + index * 0x30, 0x00, voices[index], 0x8300 + index * 0x20, 0x30);
        }

        var snapshot = S3kCompleteRunStateDecoder.decode(raw,
                S3kCompleteRunAssetCatalog.load(rom()));

        SavedMusic saved = assertInstanceOf(SavedMusic.class, snapshot.overlap());
        assertEquals(9, saved.tracks().size());
        assertTrue(saved.tracks().stream().allMatch(track -> track.populated()));
        assertEquals(0x2c8400L, saved.tracks().get(8).dataPointer().pointer());
    }

    @Test
    void failsClosedOnUnknownBanksWindowEscapesAndInvalidLiveStackPointers() throws Exception {
        S3kCompleteRunAssetCatalog catalog = S3kCompleteRunAssetCatalog.load(rom());

        byte[] unknownBank = baseState();
        unknownBank[0x3e] = 0x58;
        track(unknownBank, TRACKS, 0x80, 0x06, 0x8120, 0x30);
        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunStateDecoder.decode(unknownBank, catalog));

        byte[] windowEscape = baseState();
        track(windowEscape, TRACKS, 0x80, 0x06, 0x7fff, 0x30);
        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunStateDecoder.decode(windowEscape, catalog));

        byte[] bankPadding = baseState();
        bankPadding[0x3e] = 0x1c;
        word(bankPadding, 0x33, 0x8100);
        word(bankPadding, 0x37, 0x8100);
        track(bankPadding, TRACKS, 0x80, 0x06, 0x8100, 0x30);
        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunStateDecoder.decode(bankPadding, catalog));

        byte[] badStack = baseState();
        track(badStack, TRACKS, 0x80, 0x06, 0x8120, 0x2d);
        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunStateDecoder.decode(badStack, catalog));
    }

    @Test
    void rejectsWrongStateWidthAndAnyRomOtherThanThePinnedLockedOnImage() throws Exception {
        S3kCompleteRunAssetCatalog catalog = S3kCompleteRunAssetCatalog.load(rom());
        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunStateDecoder.decode(new byte[1023], catalog));
        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunAssetCatalog.load(Path.of("pom.xml")));
    }

    private static Path rom() {
        String configured = System.getProperty("s3k.rom.path");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("test requires -Ds3k.rom.path=<locked-on ROM>");
        }
        return Path.of(configured);
    }

    private static byte[] baseState() {
        byte[] raw = new byte[1024];
        raw[0x3e] = 0x59;
        word(raw, 0x33, 0x8120);
        word(raw, 0x35, 0x0695);
        word(raw, 0x37, 0x8100);
        word(raw, 0x39, 0xde45);
        return raw;
    }

    private static void track(byte[] raw, int base, int playback, int voice,
            int dataPointer, int stackPointer) {
        raw[base] = (byte) playback;
        raw[base + 1] = (byte) voice;
        raw[base + 2] = 1;
        word(raw, base + 3, dataPointer);
        raw[base + 9] = (byte) stackPointer;
    }

    private static void word(byte[] raw, int offset, int value) {
        raw[offset] = (byte) value;
        raw[offset + 1] = (byte) (value >>> 8);
    }
}
