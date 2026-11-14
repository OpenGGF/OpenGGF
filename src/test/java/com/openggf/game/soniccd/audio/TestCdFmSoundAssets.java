package com.openggf.game.soniccd.audio;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** MDINIT byte ownership only: no driver, sound, default RAM or clock installation. */
class TestCdFmSoundAssets {
    @Test void nullProgramOwnerDoesNotCreateAnAsset() {
        assertThrows(NullPointerException.class, () -> CdFmSoundAssets.fromPrograms(null));
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void actualMediaAdmitsOnlyTheMeasuredUsaMdinitAndDetachesItsEntireBank(CdReleaseVariant variant) throws Exception {
        CdFmSoundAssets assets;
        try (var programs = open(variant)) {
            if (variant != CdReleaseVariant.USA) {
                assertThrows(java.io.IOException.class, () -> CdFmSoundAssets.fromPrograms(programs));
                // Regional source is still usable; this audio rejection did not close it or admit USA offsets.
                assertTrue(programs.entry(CdNativeFile.MDINIT).size() > 0);
                return;
            }
            assets = CdFmSoundAssets.fromPrograms(programs);
            var image = programs.mmd(CdNativeFile.MDINIT);
            assertEquals(7514, image.fileBytes()); assertEquals(934, image.unresolvedCopyBytes());
            assertEquals("abd4735cb55aac797402d611b6d732276f02f1394ef39e7e1af6cd9dbd6fdf7e",
                    hash(image.readFile(0, image.fileBytes())));
            assertArrayEquals(image.readFile(0x1DA, 0x1AD6), assets.bank().copy());
        }
        assertEquals(6870, assets.bank().length());
        assertEquals("23f3df846b228c8de5b55cb6503318f05df1a49c5bdfe282ceecf2502ce5bc99", hash(assets.bank().copy()));
        assertEquals(CdReleaseVariant.USA, assets.source().variant());
        assertEquals(CdNativeFile.MDINIT, assets.source().file());
        assertEquals("a6b026a2381f926485cebb2da0bded29319cfd515ef7956a3ca79b18645bdb2b", assets.source().wholeDiscSha256());
        assertEquals(0xFF2000L, assets.source().header().origin());
        assertEquals(0xFF2000L, assets.source().header().entry());
        assertEquals(0x2000, assets.source().header().declaredCopyBytes());
        assertEquals(934, assets.source().unresolvedRelocationBytes());
        assertEquals(0x1DA, CdFmSoundAssets.FILE_OFFSET);
        assertEquals(0, CdFmSoundAssets.Z80_ORIGIN);
        // A copy remains immutable and readable after the actual disc owner has closed.
        byte[] copy = assets.bank().copy(); int first = assets.bank().unsignedByte(0); copy[0] ^= 0x55;
        assertEquals(first, assets.bank().unsignedByte(0));
        assertThrows(ReadOnlyBufferException.class, () -> assets.bank().buffer().put(0, (byte) 1));
    }

    @Test void indexAndPriorityViewsKeepLittleEndianOriginZeroAndTheReal79Of80Boundary() throws Exception {
        var assets = usa();
        assertEquals(0x6EB, assets.unsignedWordLE(0x6DB)); // DriverInfo+4 SongIndex aliases SFXIndex.
        assertEquals(0x6EB, assets.unsignedWordLE(0x6DD));
        assertEquals(0x78B, assets.unsignedWordLE(0x6D7));
        assertEquals(0x1C00, assets.unsignedWordLE(0x6E7)); // Variables outside the installed bank, not default RAM.
        assertEquals("e2ef7beee36bb541a881edc5d77ef151775c2de735f1c3696ed057f0910e4ae3",
                hash(assets.readZ80(0x6EB, 160).copy()));
        assertEquals(79, assets.declaredPriorities().length());
        assertEquals("3a9de3bdbc16c73921451cee1f1d231195ae0b2e655b45f6fd3d8ba62bdd783c",
                hash(assets.declaredPriorities().copy()));
        int previous = -1;
        for (int id = 0x90; id <= 0xDF; id++) {
            int pointer = assets.sfxHeaderAddress(id);
            assertTrue(pointer > previous && pointer >= 0x7DA && pointer < 0x1AD6);
            previous = pointer;
        }
        assertEquals(0x1A8F, assets.sfxHeaderAddress(0xDF));
        assertEquals(0x7D, assets.nativePriorityByte(0x93));
        assertEquals(0x6D, assets.nativePriorityByte(0xA4));
        // Native unchecked priority row80 reads the first header byte, not an invented declared priority.
        assertEquals(4, assets.nativePriorityByte(0xDF));
        assertEquals(assets.bank().unsignedByte(0x7DA), assets.nativePriorityByte(0xDF));
    }

    @Test void commonHeadersAndBackwardChargeStopVoiceUseTheCompleteBank() throws Exception {
        var a = usa();
        int[] ids = {0x93, 0x95, 0x96, 0xA4, 0xAB};
        int[] pointers = {0x8EB, 0x95E, 0x98D, 0xD59, 0xE83};
        String[] bytes = {"040901018001f508f400", "74090101800468090005", "ac09010280029d0900008004a709000b",
                "6b0d01018006630df400", "6a0e010180008d0e0000"};
        for (int i = 0; i < ids.length; i++) {
            assertEquals(pointers[i], a.sfxHeaderAddress(ids[i]));
            assertArrayEquals(HexFormat.of().parseHex(bytes[i]), a.readZ80(pointers[i], bytes[i].length() / 2).copy());
        }
        assertEquals(0xE6A, a.unsignedWordLE(a.sfxHeaderAddress(0xAB)));
        assertEquals(a.unsignedWordLE(a.sfxHeaderAddress(0xAA)), a.unsignedWordLE(a.sfxHeaderAddress(0xAB)));
        assertTrue(a.unsignedWordLE(a.sfxHeaderAddress(0xAB)) < a.sfxHeaderAddress(0xAB));
        assertEquals("98f5fbdb2c8b3f0782460735a138cd171d991523f0672e8f9b226405bda1e6fa", hash(a.readZ80(0xE6A, 25).copy()));
        assertArrayEquals(HexFormat.of().parseHex("f2f2"), a.readZ80(0xE8D, 2).copy());
    }

    @Test void sourceAndRamCoordinatesNeverAliasTheBoundedInstalledZ80Image() throws Exception {
        var a = usa();
        assertEquals(0, a.readZ80(0x1AD6, 0).length());
        assertEquals(1, a.readZ80(0x1AD5, 1).length());
        for (int address : new int[] {-1, 0x1AD6, 0x1C00, 0xA00000, 0x2001DA, 0xFF20DA, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> a.readZ80(address, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> a.unsignedWordLE(address));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> a.unsignedWordLE(0x1AD5));
        assertThrows(IndexOutOfBoundsException.class, () -> a.readZ80(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.readZ80(1, Integer.MAX_VALUE));
        for (int id : new int[] {-1, 0x8F, 0xE0, 0x100, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> a.sfxHeaderAddress(id));
            assertThrows(IllegalArgumentException.class, () -> a.nativePriorityByte(id));
        }
    }

    private static CdFmSoundAssets usa() throws Exception {
        try (var programs = open(CdReleaseVariant.USA)) { return CdFmSoundAssets.fromPrograms(programs); }
    }
    private static CdProgramAssets open(CdReleaseVariant variant) throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for actual FM source checks");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var files = Files.list(Path.of(root).resolve(directory))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return CdProgramAssets.open(cues.getFirst(), Optional.of(variant), Optional.empty());
        }
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
