package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Source authenticity uses supplied original media; constructed metadata never supplies a positive capability. */
class TestCdR11aPaletteAssets {
    private static final int[] SCRIPT_OFFSETS = {0x2C0, 0x2CE, 0x2DC, 0x2EA, 0x2F8, 0x306};
    // Independently expanded pinned N final Present:120-171 dc.b/dc.w; excluded prototype is never selected.
    private static final byte[] SOURCE_LITERAL = hex("31030800080108020eee0cc60eea32030800080108020eea0eee0cc6"
            + "33030800080108020cc60eea0eee2c030600060106020eca0ea80e86"
            + "2d030600060106020ea80e860eca2f030600060106020c860eca0ea8");
    // N PalCycle_Do:86-115 + CycleColor:15-47 assembled operands in original USA R11A, not executable evidence.
    private static final byte[] ROUTINE_LITERAL = hex("4bf8f7b749f8f7b0"
            + "43f9002002c045f9002002c8614443f9002002ce45f9002002d66136"
            + "43f9002002dc45f9002002e4612843f9002002ea45f9002002f2611a"
            + "43f9002002f845f900200300610c43f90020030645f90020030e"
            + "53156a30700010191219d04047f8fb0047f30000700010145200b001650270001880"
            + "d0401ab10000103100014880d04036b20000d8fc0001dafc00014e75");

    @Test
    void originalUsaMediaAuthenticatesNativeOperandsAndSixSourceViewsBeforePublishingCachedDetachedOwner() throws Exception {
        CdR11aAssets owner; CdR11aPaletteAssets palettes; CdMmdImage image;
        try (var program = CdProgramAssets.open(cue(CdReleaseVariant.USA), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            owner = CdR11aAssetLoader.load(program, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
            image = owner.internalReader().retainedOriginalR11Image();
            assertSame(owner.sonic(), owner.internalReader().sonic());
            assertArrayEquals(ROUTINE_LITERAL, image.readFile(0x228, 152));
            assertArrayEquals(SOURCE_LITERAL, image.readFile(0x2C0, 84));
            assertEquals("10944706086aebf5803d68a3c5f4e8d3a6036520eacd9ecbd9d005f1b8d225f7", CdR11aProfile.sha256(SOURCE_LITERAL));
            for (int i = 0; i < 6; i++) {
                int site = 0x230 + i * 14;
                assertEquals(0x43F9, word(image.readFile(site, 2), 0));
                assertEquals(0x200000L + SCRIPT_OFFSETS[i], unsignedLong(image.readFile(site + 2, 4)));
                assertEquals(0x45F9, word(image.readFile(site + 6, 2), 0));
                assertEquals(0x200000L + SCRIPT_OFFSETS[i] + 8, unsignedLong(image.readFile(site + 8, 4)));
            }
            var cache = CdR11aAssets.class.getDeclaredField("paletteCycle"); cache.setAccessible(true);
            assertNull(cache.get(owner));
            var before = new HashMap<String, Object>();
            for (var field : CdR11aAssets.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getName().equals("paletteCycle")) continue;
                field.setAccessible(true); before.put(field.getName(), field.get(owner));
            }
            palettes = owner.paletteCycle(); assertSame(palettes, owner.paletteCycle()); assertSame(palettes, cache.get(owner));
            for (var entry : before.entrySet()) {
                var field = CdR11aAssets.class.getDeclaredField(entry.getKey()); field.setAccessible(true);
                assertSame(entry.getValue(), field.get(owner), "palette source must not warm/change " + entry.getKey());
            }
            for (int offset : new int[]{0x228, 0x230, 0x232, 0x27E, 0x282, 0x29E, 0x2C0, 0x2C4, 0x2C8, 0x313, 0x3FFFF}) {
                byte[] changed = image.readFile(0, image.fileBytes()); changed[offset] ^= 1;
                var changedSource = spy(program);
                doReturn(CdMmdImage.parse(changed)).when(changedSource).mmd(CdNativeFile.R11A);
                assertThrows(IOException.class, () -> CdR11aAssetLoader.load(changedSource, CdReleaseVariant.USA, CdStageKey.parse("R11A")));
            }
            var id = owner.identity();
            for (var foreign : java.util.List.of(
                    new CdR11aAssets.Identity(id.variant(), CdStageKey.parse("R11B"), id.file(), id.domain(), id.mmdSha256(), id.observedWholeDiscSha256()),
                    new CdR11aAssets.Identity(id.variant(), id.stage(), CdNativeFile.R11B, id.domain(), id.mmdSha256(), id.observedWholeDiscSha256()),
                    new CdR11aAssets.Identity(id.variant(), id.stage(), id.file(), CdMmdImage.Domain.RELOCATED_COPY, id.mmdSha256(), id.observedWholeDiscSha256()),
                    new CdR11aAssets.Identity(id.variant(), id.stage(), id.file(), id.domain(), "0".repeat(64), id.observedWholeDiscSha256()))) {
                assertThrows(IllegalArgumentException.class, () -> CdR11aPaletteAssets.load(componentOwner(owner, foreign, image), owner.internalReader()));
            }
        }
        assertSame(owner.identity(), palettes.identity()); assertSame(palettes, owner.paletteCycle());
        assertEquals(6, palettes.views().size()); assertEquals(6, palettes.scripts().size());
        for (int i = 0; i < 6; i++) {
            var view = palettes.views().get(i); assertEquals(i, view.cycle());
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, SCRIPT_OFFSETS[i], 8), view.scriptRange());
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, SCRIPT_OFFSETS[i] + 8, 6), view.coloursRange());
            assertSame(view.data(), palettes.scripts().get(i));
            var common = owner.internalReader().loadTables().cycles().get(i);
            assertSame(common.script(), view.data().script());
            assertSame(common.colors(), view.data().colours());
            var p = CdPpzDataLayout.Programme.R11A;
            assertSame(owner.internalReader().view(p.range("PalCycleScript" + (i + 1))), view.data().script());
            assertSame(owner.internalReader().view(p.range("PalCycleColors" + (i + 1))), view.data().colours());
            assertArrayEquals(Arrays.copyOfRange(SOURCE_LITERAL, i * 14, i * 14 + 8), view.data().script().copy());
            assertArrayEquals(Arrays.copyOfRange(SOURCE_LITERAL, i * 14 + 8, i * 14 + 14), view.data().colours().copy());
            assertArrayEquals(image.readFile(SCRIPT_OFFSETS[i], 8), view.data().script().copy());
            byte[] copy = view.data().script().copy(); Arrays.fill(copy, (byte) 0xFF);
            assertArrayEquals(Arrays.copyOfRange(SOURCE_LITERAL, i * 14, i * 14 + 8), view.data().script().copy());
            assertThrows(ReadOnlyBufferException.class, () -> view.data().colours().buffer().put((byte) 0));
            assertThrows(IndexOutOfBoundsException.class, () -> view.data().script().unsignedByte(8));
            assertThrows(IndexOutOfBoundsException.class, () -> view.data().colours().unsignedWord(5));
        }
        assertThrows(UnsupportedOperationException.class, () -> palettes.views().clear());
        assertThrows(UnsupportedOperationException.class, () -> palettes.scripts().clear());
    }

    @ParameterizedTest(name = "{0} cannot select USA palette source offsets")
    @EnumSource(value = CdReleaseVariant.class, names = {"JAPAN", "EUROPE"})
    void otherOriginalRegionsRetainTheirTerrainButHaveNoUsaPaletteCycleOwner(CdReleaseVariant variant) throws Exception {
        try (var program = CdProgramAssets.open(cue(variant), Optional.of(variant), Optional.empty())) {
            var owner = CdR11aAssetLoader.load(program, variant, CdStageKey.parse("R11A"));
            assertThrows(UnsupportedOperationException.class, owner::paletteCycle);
            var original = program.mmd(CdNativeFile.R11A);
            assertThrows(UnsupportedOperationException.class, () -> CdR11aPaletteAssets.load(owner, null));
            try (var usa = CdProgramAssets.open(cue(CdReleaseVariant.USA), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
                var wrongRegion = spy(usa);
                doReturn(original).when(wrongRegion).mmd(CdNativeFile.R11A);
                assertThrows(IOException.class, () -> CdR11aAssetLoader.load(wrongRegion, CdReleaseVariant.USA, CdStageKey.parse("R11A")));
            }
        }
    }

    /** Replacing exact custody with equal metadata must fail before creating a palette owner. */
    @Test
    void independentEqualReadersAndComponentOwnersCannotSupplyTheAggregatesPaletteSource() throws Exception {
        CdR11aAssets first; CdR11aAssets second;
        try (var program = CdProgramAssets.open(cue(CdReleaseVariant.USA), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            first = CdR11aAssetLoader.load(program, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
            second = CdR11aAssetLoader.load(program, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
        assertEquals(first.identity(), second.identity());
        assertNotSame(first.internalReader(), second.internalReader());
        assertNotSame(first.internalReader().retainedOriginalR11Image(), second.internalReader().retainedOriginalR11Image());
        var cache = CdR11aAssets.class.getDeclaredField("paletteCycle"); cache.setAccessible(true);
        assertNull(cache.get(first)); assertNull(cache.get(second));
        assertThrows(IllegalArgumentException.class, () -> CdR11aPaletteAssets.load(first, second.internalReader()));
        assertThrows(IllegalArgumentException.class, () -> CdR11aPaletteAssets.load(first, null));
        var component = componentOwner(first, first.identity(), first.internalReader().retainedOriginalR11Image());
        assertThrows(IllegalArgumentException.class, () -> CdR11aPaletteAssets.load(component, first.internalReader()));
        assertThrows(IllegalArgumentException.class, component::paletteCycle);
        assertNull(cache.get(first)); assertNull(cache.get(second)); assertNull(cache.get(component));
        var firstPalette = first.paletteCycle();
        assertSame(firstPalette, first.paletteCycle());
        assertNotSame(firstPalette, second.paletteCycle());
        assertSame(first.identity(), firstPalette.identity());
        assertArrayEquals(SOURCE_LITERAL, concat(firstPalette));
        first.internalReader().requireOriginalR11Image(first.internalReader().retainedOriginalR11Image());
        assertThrows(IllegalArgumentException.class, () -> first.internalReader().requireOriginalR11Image(second.internalReader().retainedOriginalR11Image()));
    }

    private static CdR11aAssets componentOwner(CdR11aAssets source, CdR11aAssets.Identity identity, CdMmdImage image) {
        return new CdR11aAssets(source.profile(), identity, image, source.blocksDecode(), source.levelTilesDecode(),
                source.terrain(), source.loadPlan(), source.placements(), source.sonic(), source.player(), source.shield());
    }
    private static byte[] concat(CdR11aPaletteAssets palettes) {
        byte[] bytes = new byte[84]; int at = 0;
        for (var script : palettes.scripts()) {
            System.arraycopy(script.script().copy(), 0, bytes, at, 8); at += 8;
            System.arraycopy(script.colours().copy(), 0, bytes, at, 6); at += 6;
        }
        return bytes;
    }

    private static Path cue(CdReleaseVariant variant) throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for original-media checks");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var files = Files.list(Path.of(configured).resolve(directory))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured regional media must contain exactly one CUE"); return cues.getFirst();
        }
    }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static int word(byte[] bytes, int offset) { return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255); }
    private static long unsignedLong(byte[] bytes) { return ((long) word(bytes, 0) << 16) | word(bytes, 2); }
}
