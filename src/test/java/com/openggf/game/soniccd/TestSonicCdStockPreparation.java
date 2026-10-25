package com.openggf.game.soniccd;

import com.openggf.game.soniccd.audio.CdFmSoundAssets;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Real supplied media is optional here; parent validation passes soniccd.media.root explicitly. */
class TestSonicCdStockPreparation {
    @TempDir Path temporary;

    @Test void unsupportedStageFailsBeforeOpeningAnyMedia() {
        var request = new CdStockLaunch.Request(CdStageKey.parse("R11B"), Optional.empty(), Optional.empty());
        assertThrows(UnsupportedOperationException.class,
                () -> CdStockLaunch.prepare(temporary.resolve("missing.cue"), request));
    }

    @Test void missingMediaHasNoCartridgeOrStandaloneFallback() {
        assertThrows(IOException.class,
                () -> CdStockLaunch.prepare(temporary.resolve("missing.cue"), CdStockLaunch.Request.r11a()));
    }

    @ParameterizedTest(name = "{0}: detached authenticated stock source")
    @EnumSource(CdReleaseVariant.class)
    void preparedSourceRetainsOnlyDetachedDataAndExactContentIdentity(CdReleaseVariant variant) throws Exception {
        Path cue = suppliedCue(variant);
        var source = CdStockLaunch.prepare(cue, CdStockLaunch.Request.r11a());
        assertTrue(source.rom().isEmpty(), "a CD source must never expose a cartridge address space");
        assertEquals(variant, source.release().variant());
        assertEquals(CdStageKey.parse("R11A"), source.assets().identity().stage());
        var release = switch (variant) {
            case USA -> CdRetailReleases.USA;
            case JAPAN -> CdRetailReleases.JAPAN;
            case EUROPE -> CdRetailReleases.EUROPE;
        };
        assertEquals(release, source.release());
        assertTrue(source.identity().contains(release.normalizedSha256()));
        assertTrue(source.identity().contains(source.assets().identity().mmdSha256()));
        assertFalse(source.identity().contains(cue.toString()), "identity must be independent of host paths");

        // prepare() has already closed its disc. These are bounded owned-memory streams.
        try (var blocks = source.openAsset(CdGameDataSource.DECODED_BLOCKS);
             var second = source.openAsset(CdGameDataSource.DECODED_BLOCKS);
             var tiles = source.openAsset(CdGameDataSource.DECODED_TILES);
             var palette = source.openAsset(CdGameDataSource.INITIAL_PALETTE)) {
            byte[] firstBytes = blocks.readAllBytes();
            assertEquals(6432, firstBytes.length);
            assertEquals("0bfe16586ccdd38a27b3437441852f49b888ed527927d9ea635986579eddf0d7", sha(firstBytes));
            firstBytes[0] ^= 0x7F;
            assertNotEquals(firstBytes[0], second.readAllBytes()[0], "callers cannot mutate durable source bytes");
            byte[] art = tiles.readAllBytes();
            assertEquals(25600, art.length);
            assertEquals("314d9df246ee9c58371d6512d2bf496a2aecd08835e8698752c8f21a1e41223d", sha(art));
            assertArrayEquals(source.assets().loadPlan().initialPalette().copy(), palette.readAllBytes());
        }
        for (String invalid : new String[]{"../stage/R11A/tiles.bin", "/stage/R11A/tiles.bin",
                "stage/R11A/../R11A/tiles.bin", "stage\\R11A\\tiles.bin", "stage/R11B/tiles.bin",
                "stage/R11A/shield-dma.bin", "stage/R11A/tiles.bin/", ""}) {
            assertThrows(FileNotFoundException.class, () -> source.openAsset(invalid), invalid);
        }
    }

    @ParameterizedTest(name = "{0}: requested software variant cannot override disc bytes")
    @EnumSource(CdReleaseVariant.class)
    void requestedVariantRejectsAnotherRegion(CdReleaseVariant variant) throws Exception {
        Path cue = suppliedCue(variant);
        var wrong = variant == CdReleaseVariant.USA ? CdReleaseVariant.JAPAN : CdReleaseVariant.USA;
        var request = new CdStockLaunch.Request(CdStageKey.parse("R11A"), Optional.of(wrong), Optional.empty());
        assertThrows(IOException.class, () -> CdStockLaunch.prepare(cue, request));
    }

    @Test void forcedContentRecordRejectsAnotherDiscEvenWhenStageProgramIsIdentical() throws Exception {
        Path cue = suppliedCue(CdReleaseVariant.JAPAN);
        // JP/EU R11A MMD bytes match, but their complete disc identities do not.
        var request = new CdStockLaunch.Request(CdStageKey.parse("R11A"), Optional.empty(),
                Optional.of(CdRetailReleases.EUROPE.key()));
        assertThrows(IOException.class, () -> CdStockLaunch.prepare(cue, request));
    }

    @Test void constructorRejectsNullReleaseWithoutInventingAnAuthenticatedOwner() {
        assertThrows(NullPointerException.class,
                () -> new CdGameDataSource(null, null, Optional.empty()));
    }

    @ParameterizedTest(name = "{0}: stage preflight still precedes media I/O")
    @EnumSource(CdReleaseVariant.class)
    void unsupportedStageStillPreflightsEveryRequestedRegion(CdReleaseVariant variant) {
        var request = new CdStockLaunch.Request(CdStageKey.parse("R11B"), Optional.of(variant),
                Optional.of(CdRetailReleases.USA.key()));
        assertThrows(UnsupportedOperationException.class,
                () -> CdStockLaunch.prepare(temporary.resolve("missing.cue"), request));
    }

    @ParameterizedTest(name = "{0}: explicit FM retention policy after preparation closes its owner")
    @EnumSource(CdReleaseVariant.class)
    void preparedSourceRetainsAuthenticatedUsaFmAndExplicitlyOmitsUnsupportedRegionalFm(CdReleaseVariant variant)
            throws Exception {
        Path cue = suppliedCue(variant);
        var source = CdStockLaunch.prepare(cue, CdStockLaunch.Request.r11a());
        var binding = source.assets().identity();
        assertEquals(variant, source.release().variant());
        assertEquals(variant, binding.variant());
        assertEquals(source.release().normalizedSha256(), binding.observedWholeDiscSha256());
        assertEquals("scd:detached-stage-v1:" + source.release().key() + ":R11A:"
                + binding.observedWholeDiscSha256() + ":" + binding.mmdSha256(), source.identity(),
                "the authenticated whole-disc identity already binds MDINIT without a token bump");
        assertTrue(source.rom().isEmpty());
        assertFalse(source.identity().contains(cue.toString()));
        assertEquals(variant == CdReleaseVariant.USA, source.fmSoundAssets().isPresent());

        var rebuilt = new CdGameDataSource(source.release(), source.assets(), source.fmSoundAssets());
        assertSame(source.release(), rebuilt.release());
        assertSame(source.assets(), rebuilt.assets());
        assertEquals(source.identity(), rebuilt.identity());
        assertEquals(source.fmSoundAssets(), rebuilt.fmSoundAssets());
        assertThrows(NullPointerException.class,
                () -> new CdGameDataSource(null, source.assets(), source.fmSoundAssets()));
        assertThrows(NullPointerException.class,
                () -> new CdGameDataSource(source.release(), null, source.fmSoundAssets()));
        assertThrows(NullPointerException.class,
                () -> new CdGameDataSource(source.release(), source.assets(), null));
        assertThrows(NullPointerException.class, () -> source.openAsset(null));
        for (String unsupported : new String[]{"MDINIT.MMD;1", "audio/fm/bank.bin", "out/misc/fm_driver.bin",
                cue.toString()}) {
            assertThrows(FileNotFoundException.class, () -> source.openAsset(unsupported), unsupported);
        }
        if (variant != CdReleaseVariant.USA) {
            assertTrue(source.fmSoundAssets().isEmpty(), "JP/EU static preparation does not admit a USA FM bank");
            return;
        }
        assertThrows(IllegalArgumentException.class,
                () -> new CdGameDataSource(source.release(), source.assets(), Optional.empty()),
                "a USA source cannot silently drop required authenticated FM data");

        // prepare() has returned only after closing the original CdProgramAssets/CUE owner.
        var fm = source.fmSoundAssets().orElseThrow();
        assertSame(fm, rebuilt.fmSoundAssets().orElseThrow());
        assertEquals(CdReleaseVariant.USA, fm.source().variant());
        assertEquals(binding.observedWholeDiscSha256(), fm.source().wholeDiscSha256());
        assertEquals(CdRetailReleases.USA.normalizedSha256(), fm.source().wholeDiscSha256());
        assertEquals(CdNativeFile.MDINIT, fm.source().file());
        assertEquals("abd4735cb55aac797402d611b6d732276f02f1394ef39e7e1af6cd9dbd6fdf7e",
                fm.source().mdinitSha256());
        assertEquals(new CdMmdHeader(0x40, 0, 0xFF2000, 0x7FF, 0xFF2000, 0, 0), fm.source().header());
        assertEquals(934, fm.source().unresolvedRelocationBytes());
        assertEquals(0x1DA, CdFmSoundAssets.FILE_OFFSET);
        assertEquals(0, CdFmSoundAssets.Z80_ORIGIN);
        assertEquals(6870, fm.bank().length());
        assertEquals("23f3df846b228c8de5b55cb6503318f05df1a49c5bdfe282ceecf2502ce5bc99",
                sha(fm.bank().copy()));
        assertEquals(0x6EB, fm.unsignedWordLE(0x6DD));
        assertEquals(0x78B, fm.unsignedWordLE(0x6D7));
        assertEquals("e2ef7beee36bb541a881edc5d77ef151775c2de735f1c3696ed057f0910e4ae3",
                sha(fm.readZ80(0x6EB, 160).copy()));
        int[] ids = {0x93, 0x95, 0x96, 0x98, 0xA4, 0xAB};
        int[] pointers = {0x8EB, 0x95E, 0x98D, 0xA0B, 0xD59, 0xE83};
        int[] priorities = {0x7D, 0x70, 0x70, 0x70, 0x6D, 0x7D};
        for (int i = 0; i < ids.length; i++) {
            assertEquals(pointers[i], fm.sfxHeaderAddress(ids[i]));
            assertEquals(priorities[i], fm.nativePriorityByte(ids[i]));
        }
        assertArrayEquals(HexFormat.of().parseHex("74090101800468090005"), fm.readZ80(0x95E, 10).copy());
        assertEquals(79, fm.declaredPriorities().length());
        assertEquals("3a9de3bdbc16c73921451cee1f1d231195ae0b2e655b45f6fd3d8ba62bdd783c",
                sha(fm.declaredPriorities().copy()));
        assertEquals(4, fm.nativePriorityByte(0xDF), "native unchecked row 80 is not a declared priority");
        assertEquals(0xE6A, fm.unsignedWordLE(fm.sfxHeaderAddress(0xAB)));
        assertEquals(fm.unsignedWordLE(fm.sfxHeaderAddress(0xAA)), fm.unsignedWordLE(fm.sfxHeaderAddress(0xAB)));
        assertArrayEquals(HexFormat.of().parseHex("f2f2"), fm.readZ80(0xE8D, 2).copy());
        assertEquals(0, fm.readZ80(0x1AD6, 0).length());
        for (int address : new int[]{0x1AD6, 0x1C00, 0xA00000, 0x2001DA, 0xFF20DA}) {
            assertThrows(IndexOutOfBoundsException.class, () -> fm.readZ80(address, 1));
        }
        int original = fm.bank().unsignedByte(0);
        byte[] copy = fm.bank().copy();
        copy[0] ^= 0x55;
        assertEquals(original, fm.bank().unsignedByte(0));
        byte[] headerCopy = fm.readZ80(0x95E, 10).copy();
        headerCopy[0] ^= 0x55;
        assertEquals(0x74, fm.readZ80(0x95E, 10).unsignedByte(0));
        assertThrows(ReadOnlyBufferException.class, () -> fm.bank().buffer().put(0, (byte) 1));
        assertThrows(ReadOnlyBufferException.class, () -> fm.readZ80(0x95E, 10).buffer().put(0, (byte) 1));
    }

    @Test void constructorRejectsCrossDiscStagesAndForeignFmWithoutForgingPrivateOwners() throws Exception {
        var usa = CdStockLaunch.prepare(suppliedCue(CdReleaseVariant.USA), CdStockLaunch.Request.r11a());
        var japan = CdStockLaunch.prepare(suppliedCue(CdReleaseVariant.JAPAN), CdStockLaunch.Request.r11a());
        var europe = CdStockLaunch.prepare(suppliedCue(CdReleaseVariant.EUROPE), CdStockLaunch.Request.r11a());
        assertTrue(usa.fmSoundAssets().isPresent());
        assertTrue(japan.fmSoundAssets().isEmpty());
        assertTrue(europe.fmSoundAssets().isEmpty());
        assertEquals(japan.assets().identity().mmdSha256(), europe.assets().identity().mmdSha256());
        assertNotEquals(japan.assets().identity().observedWholeDiscSha256(),
                europe.assets().identity().observedWholeDiscSha256());
        assertThrows(IllegalArgumentException.class,
                () -> new CdGameDataSource(usa.release(), japan.assets(), usa.fmSoundAssets()));
        assertThrows(IllegalArgumentException.class,
                () -> new CdGameDataSource(japan.release(), europe.assets(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new CdGameDataSource(japan.release(), japan.assets(), usa.fmSoundAssets()));
        assertThrows(IllegalArgumentException.class,
                () -> new CdGameDataSource(europe.release(), europe.assets(), usa.fmSoundAssets()));
    }

    private static Path suppliedCue(CdReleaseVariant variant) throws IOException {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied discs");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var files = Files.list(Path.of(configured).resolve(directory))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return cues.getFirst();
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
