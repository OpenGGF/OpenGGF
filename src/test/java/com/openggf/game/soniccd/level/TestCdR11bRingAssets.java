package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Original USA B source views only; no Ring body, drawing, residency or World executes.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * ObjectIndex (Level/Palmtree Panic/Object Index (Act 1 Past).asm:7-24);
 * ObjRing_Index/ObjRing_Deltas (Level/_Objects/Ring/Main.asm:15-35),
 * ObjRing_Init.GotSubt (:76-83), ObjRing_Init.InitSubObj (:150-158),
 * ObjRing_Sparkle (:276-279), Ani_Ring/MapSpr_Ring (:464-469);
 * .Anim/.Sparkle (Level/_Objects/Ring/Data/Animations.asm:1-7),
 * .Map through .Invisible (Level/_Objects/Ring/Data/Mappings.asm:1-45);
 * PLC_Std and Art_Rings (Level/Palmtree Panic/Data (Act 1 Past).asm:82-83,376-378).
 * Literal tables are complete independent native declaration expansions, joined to B operands.
 * All Java discovery, RED, compilation and positive execution are initially UNRUN.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bRingAssets {
    private static final String PAST_SHA =
            "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final byte[] NATIVE_DELTAS = HexFormat.of().parseHex(
            "100018002000001000180020101018182020f010e818e02010081810f008e810");
    private static final byte[] NATIVE_ANIMATION = HexFormat.of().parseHex("00020504050607fc");
    private static final byte[] NATIVE_MAPPING = HexFormat.of().parseHex(
            "00120018001e0024002a00300036003c0042"
                    + "01f8050000f801f8050004f801f8010008fc01f8050804f8"
                    + "01f805000af801f805180af801f805100af801f805080af800");

    @Test void onlyThreeRawViewsAppendAfterEveryInheritedOrdinal() {
        assertEquals(89, CdR11bAssets.Asset.values().length);
        assertEquals(List.of(RING_DELTAS, ANI_RING, MAP_SPR_RING),
                Arrays.asList(CdR11bAssets.Asset.values()).subList(77, 80));
        var keys = new CdR11bAssets.Asset[]{RING_DELTAS, ANI_RING, MAP_SPR_RING};
        String[] labels = {"ObjRing_Deltas", "Ani_Ring", "MapSpr_Ring"};
        int[] offsets = {0x9AEE, 0x9F4A, 0x9F52}, lengths = {32, 8, 67};
        for (int i = 0; i < keys.length; i++) {
            var key = keys[i];
            assertEquals(77 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertEquals(CdNativeFile.R11B, key.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, key.range().domain());
            assertEquals(0x200000 + offsets[i], key.range().nativeAddress());
            assertEquals(0x200000 + offsets[i] + lengths[i], key.range().endExclusive());
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
        }
        assertEquals(69, ART_LIFE_ICON.ordinal()); assertEquals(70, TOUCH_DIMENSIONS.ordinal());
        assertEquals(71, ENEMY_POINTS.ordinal()); assertEquals(72, ART_HUD_NUMBERS.ordinal());
        assertEquals(76, MAP_SPR_MONITOR_TIME.ordinal());
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
        assertEquals(0x361E2, ART_RINGS.fileOffset()); assertEquals(244, ART_RINGS.length());
        var art = ART_RINGS.readerProfile().orElseThrow();
        assertEquals(448, art.outputBytes()); assertEquals(244, art.javaInputRange().length());
        assertEquals(244, art.nativeInputRange().length()); assertEquals(244, art.javaConsumedInputBytes());
    }

    @Test void actualIdTenDispatchAndItsExactBodyOperandsSelectOnlyTheseBViews() throws Exception {
        var f = retail(); var mmd = f.programs().r11b();
        assertEquals(PAST_SHA, sha(mmd.readFile(0, mmd.fileBytes())));
        var index = f.assets().view(OBJECT_INDEX).bytes();
        assertEquals(0x3C38, OBJECT_INDEX.fileOffset());
        assertEquals(0x3C74, OBJECT_INDEX.fileOffset() + (0x10 - 1) * 4);
        assertEquals(0x209AD6L, index.unsignedLong((0x10 - 1) * 4));
        assertArrayEquals(HexFormat.of().parseHex("00209ad6"), mmd.readFile(0x3C74, 4));
        assertArrayEquals(HexFormat.of().parseHex("700010280024323b00064efb1002"),
                mmd.readFile(0x9AD6, 14));
        assertArrayEquals(HexFormat.of().parseHex("002a01de0216026e027c"), mmd.readFile(0x9AE4, 10));
        int[] roots = {0x2A, 0x1DE, 0x216, 0x26E, 0x27C};
        int[] targets = {0x209B0E, 0x209CC2, 0x209CFA, 0x209D52, 0x209D60};
        for (int i = 0; i < roots.length; i++) assertEquals(targets[i], 0x209AE4 + roots[i]);
        assertArrayEquals(HexFormat.of().parseHex("43f900209aee1a31000048851c310001"),
                mmd.readFile(0x9B6C, 16)); // LEA ObjRing_Deltas, then both indexed byte reads.
        assertArrayEquals(HexFormat.of().parseHex("237c00209f520004337ca7ae0002"),
                mmd.readFile(0x9C22, 14)); // MOVE.L #MapSpr_Ring,oMap(a1); MOVE.W #$A7AE,oTile(a1).
        assertArrayEquals(HexFormat.of().parseHex("43f900209f4a6100bda460009b98"),
                mmd.readFile(0x9D52, 14)); // ObjRing_Sparkle's actual absolute LEA, animator and draw branches.
        assertEquals(0x209AEE, RING_DELTAS.range().nativeAddress());
        assertEquals(0x209F4A, ANI_RING.range().nativeAddress());
        assertEquals(0x209F52, MAP_SPR_RING.range().nativeAddress());
    }

    @Test void allNativeDeltaPairsAndTheCompleteCountedAnimationMatchOriginalB() throws Exception {
        var a = retail().assets(); var deltas = a.view(RING_DELTAS).bytes();
        assertArrayEquals(NATIVE_DELTAS, deltas.copy());
        assertEquals("35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86",
                sha(deltas.copy()));
        int[][] signed = {{16, 0}, {24, 0}, {32, 0}, {0, 16}, {0, 24}, {0, 32},
                {16, 16}, {24, 24}, {32, 32}, {-16, 16}, {-24, 24}, {-32, 32},
                {16, 8}, {24, 16}, {-16, 8}, {-24, 16}};
        assertEquals(16, signed.length);
        for (int i = 0; i < signed.length; i++) {
            assertEquals(signed[i][0], (int) (byte) deltas.unsignedByte(i * 2));
            assertEquals(signed[i][1], (int) (byte) deltas.unsignedByte(i * 2 + 1));
        }
        var animation = a.view(ANI_RING).bytes();
        assertArrayEquals(NATIVE_ANIMATION, animation.copy());
        assertEquals("4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a",
                sha(animation.copy()));
        assertEquals(2, animation.unsignedWord(0));
        assertArrayEquals(new byte[]{5, 4, 5, 6, 7, (byte) 0xFC}, animation.slice(2, 6).copy());
        assertThrows(IndexOutOfBoundsException.class, () -> deltas.unsignedByte(32));
        assertThrows(IndexOutOfBoundsException.class, () -> animation.unsignedByte(8));
    }

    @Test void completeNineRootMappingKeepsAttributesEmptyFrameAndExternalEvenByte() throws Exception {
        var f = retail(); var mapping = f.assets().view(MAP_SPR_RING).bytes();
        assertArrayEquals(NATIVE_MAPPING, mapping.copy());
        assertEquals("cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a",
                sha(mapping.copy()));
        int[] roots = {18, 24, 30, 36, 42, 48, 54, 60, 66};
        int[] size = {5, 5, 1, 5, 5, 5, 5, 5};
        int[] tileWords = {0, 4, 8, 0x804, 0xA, 0x180A, 0x100A, 0x80A};
        boolean[] patterns = new boolean[14];
        for (int frame = 0; frame < roots.length; frame++) {
            int root = roots[frame]; assertEquals(root, mapping.unsignedWord(frame * 2));
            int end = frame + 1 < roots.length ? roots[frame + 1] : 67;
            int count = mapping.unsignedByte(root); assertEquals(frame == 8 ? 0 : 1, count);
            assertEquals(1 + 5 * count, end - root);
            if (frame == 8) continue;
            assertEquals(0xF8, mapping.unsignedByte(root + 1));
            assertEquals(size[frame], mapping.unsignedByte(root + 2));
            assertEquals(tileWords[frame], mapping.unsignedWord(root + 3));
            assertEquals(frame == 2 ? 0xFC : 0xF8, mapping.unsignedByte(root + 5));
            int tiles = (((size[frame] >>> 2) & 3) + 1) * ((size[frame] & 3) + 1);
            int first = tileWords[frame] & 0x7FF;
            for (int p = first; p < first + tiles; p++) {
                assertTrue(p >= 0 && p < 14); patterns[p] = true;
            }
        }
        for (int pattern = 0; pattern < 14; pattern++) assertTrue(patterns[pattern], "relative pattern " + pattern);
        assertEquals(0x7AE, 0xA7AE & 0x7FF);
        assertEquals(0x7BB, (0xA7AE & 0x7FF) + patterns.length - 1);
        assertEquals(0xF5C0, (0xA7AE & 0x7FF) * 32);
        assertEquals(448, patterns.length * 32); // Existing Art_Rings; no decode or current publication here.
        assertEquals(0x209F95, MAP_SPR_RING.range().endExclusive());
        assertEquals(0, f.programs().r11b().readFile(0x9F95, 1)[0]); // Native EVEN belongs outside the 67-byte view.
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.unsignedByte(67));
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.slice(66, 2));
    }

    @Test void rawViewsAreDetachedImmutableAndNeverBecomeReaderOrPlcInputs() throws Exception {
        var a = retail().assets();
        for (var key : new CdR11bAssets.Asset[]{RING_DELTAS, ANI_RING, MAP_SPR_RING}) {
            var view = a.view(key); assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(key.length(), view.bytes().length());
            byte[] independent = retail().programs().r11b().readFile(key.fileOffset(), key.length());
            assertArrayEquals(independent, view.bytes().copy());
            byte[] copy = view.bytes().copy(); copy[0] ^= 0x55;
            assertArrayEquals(independent, a.view(key).bytes().copy());
            assertThrows(ReadOnlyBufferException.class, () -> view.bytes().buffer().put(0, (byte) 0));
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().slice(key.length(), 1));
        }
        assertThrows(NullPointerException.class, () -> a.view(null));
    }

    @Test void foreignRegionsAndContradictorySourceClaimsStillStopBeforeImageAccess() throws Exception {
        assertThrows(NullPointerException.class, () -> CdR11bAssets.fromPrograms(null));
        for (var source : List.of(
                new CdTimeWarpPrograms.Source(CdReleaseVariant.JAPAN, CdRetailReleases.USA.normalizedSha256(), "unused", PAST_SHA),
                new CdTimeWarpPrograms.Source(CdReleaseVariant.EUROPE, CdRetailReleases.USA.normalizedSha256(), "unused", PAST_SHA),
                new CdTimeWarpPrograms.Source(CdReleaseVariant.USA, "0".repeat(64), "unused", PAST_SHA),
                new CdTimeWarpPrograms.Source(CdReleaseVariant.USA, CdRetailReleases.USA.normalizedSha256(), "unused", "0".repeat(64)))) {
            var bundle = mock(CdTimeWarpPrograms.class); when(bundle.source()).thenReturn(source);
            assertThrows(IOException.class, () -> CdR11bAssets.fromPrograms(bundle));
            verify(bundle, never()).r11b(); verify(bundle, never()).warp();
        }
    }

    @Test void claimedPastIdentityCannotExposeViewsFromMutatedTruncatedOrPresentBytes() throws Exception {
        var f = retail(); var past = f.programs().r11b();
        byte[] mutated = past.readFile(0, past.fileBytes()); mutated[RING_DELTAS.fileOffset()] ^= 1;
        for (var wrong : List.of(CdMmdImage.parse(mutated),
                CdMmdImage.parse(past.readFile(0, past.fileBytes() - 1)), f.present())) {
            var bundle = mock(CdTimeWarpPrograms.class);
            when(bundle.source()).thenReturn(f.programs().source()); when(bundle.r11b()).thenReturn(wrong);
            assertThrows(IOException.class, () -> CdR11bAssets.fromPrograms(bundle));
            verify(bundle, never()).warp();
        }
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets, CdMmdImage present) { }
    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Explicit Ring source cases require the configured original USA media root");
        assertFalse(root.isBlank(), "The original USA media root must not be blank");
        if (retained != null && root.equals(retainedRoot)) return retained;
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must name exactly one existing CUE");
            cue = cues.getFirst();
        }
        CdTimeWarpPrograms programs; CdMmdImage present;
        try (var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            programs = CdTimeWarpPrograms.fromPrograms(owner); present = owner.mmd(CdNativeFile.R11A);
        }
        retained = new Fixture(programs, CdR11bAssets.fromPrograms(programs), present); retainedRoot = root;
        return retained;
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
