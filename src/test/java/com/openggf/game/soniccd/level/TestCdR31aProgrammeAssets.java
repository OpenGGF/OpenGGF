package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests FIRST. Actual original R31 source; explicit scalar/negative spies are data only. Java UNRUN. */
class TestCdR31aProgrammeAssets {
    private static final String TABLE = "800f1110801214138015171680181a19801b1d1c801e201f80216622";
    private static final String CONSUMER = "70007200103900ff152e088000074a3900ff0f01660c0c0000026606d03900ff156a123900ff1506d241d241d2407000103b10084ef900205c2a";

    @Test void onlyTypedOriginalAssetsFactoryAndFiniteDataSurfaceArePublic() {
        assertEquals(0, CdR31aProgrammeAssets.class.getConstructors().length);
        assertEquals(List.of("fromAssets/1", "musicCommands/0", "musicCommandsAddress/0", "ownsAssets/1", "selectLevelMusic/4"), publicMethods(CdR31aProgrammeAssets.class));
        assertThrows(NullPointerException.class, () -> CdR31aProgrammeAssets.fromAssets(null));
        assertEquals(0, CdR31aProgrammeAssets.UnownedMusicByte.class.getConstructors().length);
    }

    @Test void actualCompleteConsumerAndTableAreBoundToR31InsteadOfEqualCdTables() throws Exception {
        var a = actual().getFirst(); var data = CdR31aProgrammeAssets.fromAssets(a); var source = a.programmeMusicSource();
        assertEquals(CdNativeFile.R31A, a.identity().file());
        assertEquals("30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e", a.identity().mmdSha256());
        assertEquals(CONSUMER, HexFormat.of().formatHex(source.consumer().copy()));
        assertEquals("14cecfa395f0eabdbbb3174bf050b5251506f5bdbf2d0403254e8ce511598f5d", CdR11aProfile.sha256(source.consumer().copy()));
        assertEquals(TABLE, HexFormat.of().formatHex(data.musicCommands().copy()));
        assertEquals("6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932", CdR11aProfile.sha256(data.musicCommands().copy()));
        assertEquals(58, source.consumer().length()); assertEquals(28, source.commands().length());
        assertEquals(0x201AB0, data.musicCommandsAddress());
        assertEquals(0x205C2AL, source.consumer().unsignedLong(54));
        assertEquals(0x103B, source.consumer().unsignedWord(48)); assertEquals(0x1008, source.consumer().unsignedWord(50));
        assertEquals(0x201AB0, 0x201A76 + 50 + 8, "actual indexed-PC operand base, not a C/D offset");
        assertEquals(0x660C, source.consumer().unsignedWord(20)); assertEquals(0x6606, source.consumer().unsignedWord(26));
        assertEquals(0xFF152EL, source.consumer().unsignedLong(6)); assertEquals(0xFF0F01L, source.consumer().unsignedLong(16));
        assertEquals(0xFF156AL, source.consumer().unsignedLong(30)); assertEquals(0xFF1506L, source.consumer().unsignedLong(36));
    }

    @Test void sourceOwnershipIsExactWhileTwoOriginalOwnersCanHaveEqualData() throws Exception {
        var a = actual(); var first = CdR31aProgrammeAssets.fromAssets(a.getFirst());
        assertTrue(first.ownsAssets(a.getFirst())); assertFalse(first.ownsAssets(null)); assertFalse(first.ownsAssets(a.get(1)));
        var second = CdR31aProgrammeAssets.fromAssets(a.get(1));
        assertEquals(a.getFirst().identity(), a.get(1).identity());
        assertArrayEquals(first.musicCommands().copy(), second.musicCommands().copy());
        assertFalse(second.ownsAssets(a.getFirst()));
    }

    @Test void actualRaw0100PresentAndMarkedPresentIgnoreUnusedDirtyGoodFutureBytes() throws Exception {
        var d = CdR31aProgrammeAssets.fromAssets(actual().getFirst());
        for (int good = 0; good < 256; good++) for (int ta : new int[]{0, 255}) {
            assertEquals(CdCommand.R3AMUS, d.selectLevelMusic(0x100, 1, ta, good));
            assertEquals(CdCommand.R3AMUS, d.selectLevelMusic(0x1FF, 0x81, ta, good));
            assertEquals(CdCommand.PASTMUS, d.selectLevelMusic(0x100, 0x80, ta, good));
        }
    }

    @Test void conditionalRawFutureAdditionWrapsByteAndNeverNormalizesGoodFutureToBoolean() throws Exception {
        var d = CdR31aProgrammeAssets.fromAssets(actual().getFirst());
        assertEquals(CdCommand.R3DMUS, d.selectLevelMusic(0x100, 2, 0, 0));
        assertEquals(CdCommand.R3CMUS, d.selectLevelMusic(0x100, 0x82, 0, 1));
        assertEquals(CdCommand.R3AMUS, d.selectLevelMusic(0x100, 2, 0, 255));
        assertEquals(CdCommand.PASTMUS, d.selectLevelMusic(0x100, 2, 0, 254));
        assertEquals(CdCommand.PASTMUS, d.selectLevelMusic(0x100, 2, 0, 2));
        assertEquals(CdCommand.R4AMUS, d.selectLevelMusic(0x100, 2, 0, 3));
        for (int good = 0; good < 256; good++) assertEquals(CdCommand.R3DMUS, d.selectLevelMusic(0x100, 2, 255, good));
    }

    @Test void allSevenActualRowsUseNativeZoneByteAndWordIndexWithActNonread() throws Exception {
        int[] commands = {128,15,17,16,128,18,20,19,128,21,23,22,128,24,26,25,128,27,29,28,128,30,32,31,128,33,102,34};
        var d = CdR31aProgrammeAssets.fromAssets(actual().getFirst());
        for (int zone = 0; zone < 7; zone++) for (int period = 0; period < 4; period++) {
            assertEquals(CdCommand.fromId(commands[zone * 4 + period]), d.selectLevelMusic(zone << 8, period, 1, 0));
            assertEquals(CdCommand.fromId(commands[zone * 4 + period]), d.selectLevelMusic((zone << 8) | 255, period | 128, 1, 255));
        }
    }

    @Test void firstOutsideTableByteIsTypedAndPreservesAllDataWithoutReadingAdjacentCode() throws Exception {
        var d = CdR31aProgrammeAssets.fromAssets(actual().getFirst()); var before = d.musicCommands(); byte[] bytes = before.copy();
        assertEquals(0x201ACC, assertThrows(CdR31aProgrammeAssets.UnownedMusicByte.class,
                () -> d.selectLevelMusic(0x700, 0, 1, 0)).address());
        assertEquals(0x201ACC, assertThrows(CdR31aProgrammeAssets.UnownedMusicByte.class,
                () -> d.selectLevelMusic(0x100, 2, 0, 22)).address());
        assertEquals(0x201BB3, assertThrows(CdR31aProgrammeAssets.UnownedMusicByte.class,
                () -> d.selectLevelMusic(0x100, 2, 0, 253)).address());
        assertEquals(0x201AB0 + 1020 + 127, assertThrows(CdR31aProgrammeAssets.UnownedMusicByte.class,
                () -> d.selectLevelMusic(0xFFFF, 127, 1, 0)).address());
        assertSame(before, d.musicCommands()); assertArrayEquals(bytes, d.musicCommands().copy());
    }

    @Test void widthsRejectInsteadOfTruncatingOrBorrowingAnOrdinaryGate() throws Exception {
        var d = CdR31aProgrammeAssets.fromAssets(actual().getFirst());
        for (int bad : new int[]{-1, 65536}) assertThrows(IllegalArgumentException.class, () -> d.selectLevelMusic(bad, 1, 0, 0));
        for (int bad : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> d.selectLevelMusic(0x100, bad, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> d.selectLevelMusic(0x100, 1, bad, 0));
            assertThrows(IllegalArgumentException.class, () -> d.selectLevelMusic(0x100, 1, 0, bad));
        }
    }

    @Test void completeViewsAndEveryOldPublicAggregateApiRemainImmutableAndUnchanged() throws Exception {
        assertEquals(67, CdR31aAssets.Asset.values().length);
        assertEquals(List.of("blocksDecode/0", "bounds/0", "fromPrograms/2", "identity/0", "initialCentre/0", "levelIndex/0", "levelTilesDecode/0", "loadMetadata/0", "placementRows/0", "placements/0", "range/1", "ranges/0", "sonic/0", "terrain/0", "view/1"), publicMethods(CdR31aAssets.class));
        var a = actual().getFirst(); var oldViews = Arrays.stream(CdR31aAssets.Asset.values()).map(a::view).toList();
        var ranges = a.ranges(); var identity = a.identity(); var placements = a.placements();
        var d = CdR31aProgrammeAssets.fromAssets(a); var music = d.musicCommands(); byte[] original = music.copy(); byte[] copy = music.copy(); copy[0] ^= 1;
        assertArrayEquals(original, music.copy()); var first = music.buffer(); var second = music.buffer(); first.get(); assertEquals(0, second.position());
        assertThrows(ReadOnlyBufferException.class, () -> second.put(0, (byte)1));
        assertEquals(0, music.slice(28, 0).length()); assertThrows(IndexOutOfBoundsException.class, () -> music.unsignedByte(28));
        assertThrows(IndexOutOfBoundsException.class, () -> music.slice(-1, 1)); assertThrows(IndexOutOfBoundsException.class, () -> music.slice(0, 29));
        assertSame(ranges, a.ranges()); assertSame(identity, a.identity()); assertSame(placements, a.placements());
        for (var asset : CdR31aAssets.Asset.values()) assertSame(oldViews.get(asset.ordinal()), a.view(asset));
    }

    @Test void constructedForeignAndMalformedBorrowSpiesCannotMintPositiveSourceBindings() throws Exception {
        var a = actual().getFirst(); var raw = a.programmeMusicSource();
        var foreign = negativeSource(a, raw.consumer(), raw.commands());
        var id = a.identity(); when(foreign.identity()).thenReturn(new CdR31aAssets.Identity(id.variant(), id.stage(), id.file(), id.domain(), id.mmdSha256(), "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> CdR31aProgrammeAssets.fromAssets(foreign));
        byte[] changed = raw.consumer().copy(); changed[57] ^= 1;
        var wrongCode = negativeSource(a, CdAssetBytes.copyOf(changed), raw.commands());
        assertThrows(IllegalArgumentException.class, () -> CdR31aProgrammeAssets.fromAssets(wrongCode));
        var truncated = negativeSource(a, raw.consumer(), raw.commands().slice(0, 27));
        assertThrows(IllegalArgumentException.class, () -> CdR31aProgrammeAssets.fromAssets(truncated));
        byte[] different = raw.commands().copy(); different[5] ^= 1;
        var wrongTable = negativeSource(a, raw.consumer(), CdAssetBytes.copyOf(different));
        assertThrows(IllegalArgumentException.class, () -> CdR31aProgrammeAssets.fromAssets(wrongTable));
    }
    // Explicit CONSTRUCTED negative source only. No successful owner is made from this spy.
    private static CdR31aAssets negativeSource(CdR31aAssets actual, CdAssetBytes consumer, CdAssetBytes commands) {
        var p = mock(CdR31aAssets.class); when(p.identity()).thenReturn(actual.identity());
        when(p.programmeMusicSource()).thenReturn(new CdR31aAssets.ProgrammeMusicSource(consumer, commands)); return p;
    }
    private static List<String> publicMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(m -> m.getName() + "/" + m.getParameterCount()).sorted().toList();
    }
    private static List<CdR31aAssets> retained;
    private static synchronized List<CdR31aAssets> actual() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(root != null && !root.isBlank(), "Optional original USA media absent");
        List<Path> cues; try (var files = Files.list(Path.of(root).resolve("USA"))) {
            cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
        }
        assertEquals(1, cues.size(), "Configured source requires exactly one original CUE");
        try (var p = CdProgramAssets.open(cues.getFirst(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = List.of(CdR31aAssets.fromPrograms(p, CdStagePayload.R31A.stage()), CdR31aAssets.fromPrograms(p, CdStagePayload.R31A.stage()));
        }
        return retained;
    }
}
