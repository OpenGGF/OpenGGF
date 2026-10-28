package com.openggf.game.sonic2;

import com.openggf.Engine;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot.ArtSlotIdentity;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot.ArtSlotRange;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.sonic2.resources.Sonic2RuntimePlcPublisher;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.util.PatternDecompressor;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Real ROM PLCs and production object rendering, with an independent native-map pixel oracle. */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2PlcSharedArtRendering {
    private PresentationOffscreenTestContext context;
    private GraphicsManager graphics;
    private Rom rom;
    private Sonic2ObjectArtProvider provider;
    private Palette palette;
    private Pattern[] fiery;

    @BeforeEach
    void open() throws Exception {
        rom = GameServices.rom().getRom();
        context = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
        graphics = GameServices.graphics();
        graphics.setHeadlessMode(false);
        graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
        graphics.setProjectionWidth(320);
        graphics.applyResolvedDisplayWidth(320);
        graphics.setProjectionMatrixBuffer(new Matrix4f().ortho2D(0, 320, 0, 224).get(new float[16]));
        graphics.setViewport(0, 0, 320, 224);
        glViewport(0, 0, 320, 224);
        glDisable(GL_DITHER);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        palette = new Palette();
        palette.fromSegaFormat(rom.readBytes(Sonic2Constants.SONIC_TAILS_PALETTE_ADDR, 32));
        graphics.cachePaletteTexture(palette, 0);
        fiery = PatternDecompressor.nemesis(rom, 0x84890);
        provider = new Sonic2ObjectArtProvider(rom, RomByteReader.fromRom(rom));
        provider.loadArtForZone(14);
        cache();
    }

    @AfterEach
    void close() {
        try {
            if (graphics != null) graphics.cleanup();
        } finally {
            if (context != null) context.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realPlcsInEitherOrderReachEveryNativeExplosionFrame(boolean animalsLast) throws Exception {
        // Reach and render the ordinary fiery control before making the conflicting publication.
        provider.requestPlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        assertExplosion(fiery);
        Pattern[] expected = fiery.clone();
        if (animalsLast) {
            publishExpected(expected, Sonic2Constants.PLC_ANIMALS_DEZ);
        } else {
            publishExpected(expected, Sonic2Constants.PLC_ANIMALS_DEZ);
            publishExpected(expected, Sonic2Constants.PLC_FIERY_EXPLOSION);
        }
        assertExplosion(expected);
        assertAnimals(expected);
        assertTrue(animalsLast == !Arrays.equals(pixels(fiery), pixels(expected)),
                "the reached ordinary and alias outcomes must be distinguishable");
    }

    @Test
    void repeatedShorterOverlapsPreserveTheUnwrittenTailAndNonoverlapRemainsStable() throws Exception {
        provider.requestPlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        Pattern[] expected = fiery.clone();
        var animalEntries = Sonic2PlcLoader.parsePlc(rom, Sonic2Constants.PLC_ANIMALS_DEZ).entries();
        int animalEnd = animalEntries.get(1).tileIndex()
                + PatternDecompressor.nemesis(rom, animalEntries.get(1).romAddr()).length;
        assertTrue(animalEnd < 0x580 + fiery.length, "real animal writes only partly overlap the explosion");
        var renderer = provider.getRenderer(ObjectArtKeys.BOSS_EXPLOSION);
        int base = renderer.getPatternBase();
        for (int plc : new int[]{Sonic2Constants.PLC_ANIMALS_DEZ, Sonic2Constants.PLC_ANIMALS_DEZ,
                Sonic2Constants.PLC_FIERY_EXPLOSION, Sonic2Constants.PLC_ANIMALS_EHZ}) {
            publishExpected(expected, plc);
            assertSame(renderer, provider.getRenderer(ObjectArtKeys.BOSS_EXPLOSION));
            assertEquals(base, renderer.getPatternBase(), "publication updates existing residency");
            assertExplosion(expected);
        }
        for (var entry : Sonic2PlcLoader.parsePlc(rom, Sonic2Constants.PLC_STD1).entries()) {
            int end = entry.tileIndex() + PatternDecompressor.nemesis(rom, entry.romAddr()).length;
            assertTrue(end <= 0x580 || entry.tileIndex() >= 0x580 + fiery.length,
                    "ordinary Std1 control must be independently non-overlapping");
        }
        provider.requestPlc(Sonic2Constants.PLC_STD1);
        assertExplosion(expected);
        assertArrayEquals(pixels(Arrays.copyOfRange(fiery, animalEnd - 0x580, fiery.length)),
                pixels(Arrays.copyOfRange(expected, animalEnd - 0x580, fiery.length)));
    }

    @Test
    void snapshotsBeforeAndAfterPublicationRestoreBytesPixelsAndForwardReplay() throws Exception {
        Pattern[] initialAnimals = new Pattern[40];
        Arrays.setAll(initialAnimals, ignored -> new Pattern());
        overlayExpected(initialAnimals, Sonic2Constants.PLC_ANIMALS_DEZ);
        var initial = provider.capture();
        provider.requestPlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        var beforeAnimals = provider.capture();
        Pattern[] expected = fiery.clone();
        publishExpected(expected, Sonic2Constants.PLC_ANIMALS_DEZ);
        var afterAnimals = provider.capture();
        assertExplosion(expected);
        assertAnimals(expected);
        provider.restore(beforeAnimals);
        assertExplosion(fiery);
        assertAnimals(fiery);
        provider.restore(afterAnimals);
        assertExplosion(expected);
        assertAnimals(expected);
        provider.restore(initial);
        assertExplosion(fiery);
        assertAnimals(initialAnimals);
        provider.requestPlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        provider.requestPlc(Sonic2Constants.PLC_ANIMALS_DEZ);
        assertExplosion(expected);
        assertEquals(afterAnimals, provider.capture(), "forward replay returns to the identical publication image");
    }

    @Test
    void snapshotRestoresIntoAFreshSameRomWorldWithoutPublicationHistory() throws Exception {
        provider.requestPlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        Pattern[] expected = fiery.clone();
        publishExpected(expected, Sonic2Constants.PLC_ANIMALS_DEZ);
        var snapshot = provider.capture();
        assertEquals(4, snapshot.artSlotRanges().size(), "Pig, the retained two-tile gap, Chicken and the fiery tail coalesce");
        provider = new Sonic2ObjectArtProvider(rom, RomByteReader.fromRom(rom));
        provider.loadArtForZone(14);
        cache();
        assertExplosion(fiery);
        provider.restore(snapshot);
        assertExplosion(expected);
        assertEquals(snapshot, provider.capture());
    }

    @Test
    void foreignWorldEmptyImageAndInvalidRomRecipeAreRejectedBeforeMutation() throws Exception {
        var emptyDez = provider.capture();
        var preparedDez = provider.preparePlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        provider.loadArtForZone(0);
        cache();
        var before = provider.capture();
        assertThrows(IllegalArgumentException.class, () -> provider.restore(emptyDez));
        assertThrows(IllegalArgumentException.class, () -> provider.publishPreparedPlc(preparedDez));
        assertEquals(before, provider.capture());
        assertExplosion(fiery);
        provider.loadArtForZone(14);
        cache();
        provider.requestPlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        var current = provider.capture();
        var wrongRom = new ArtSlotIdentity("0000000000000000000000000000000000000000", 14);
        assertThrows(IllegalArgumentException.class, () -> provider.restore(
                new PlcProgressSnapshot(999, wrongRom, current.artSlotRanges())));
        assertThrows(IllegalArgumentException.class, () -> provider.restore(new PlcProgressSnapshot(999,
                current.artSlotIdentity(), java.util.List.of(new ArtSlotRange(0x580, 0x84890, fiery.length, 1)))));
        assertEquals(current, provider.capture());
        assertExplosion(fiery);
    }

    @Test
    void runtimeTransactionPublishesExistingSheetWritesAndRefreshesWithoutAllocatingAgain() throws Exception {
        var logicalPlc = new Sonic2PlcService(rom);
        var renderer = provider.getRenderer(ObjectArtKeys.BOSS_EXPLOSION);
        int base = renderer.getPatternBase();
        int[] refreshes = {0};
        Runnable refresh = () -> { refreshes[0]++; cache(); };
        Sonic2RuntimePlcPublisher.append(provider, logicalPlc, refresh, Sonic2Constants.PLC_FIERY_EXPLOSION);
        assertExplosion(fiery);
        int beforeAnimals = refreshes[0];
        assertFalse(Sonic2RuntimePlcPublisher.append(provider, logicalPlc, refresh, Sonic2Constants.PLC_ANIMALS_DEZ),
                "existing resident sheets refresh their ranges without a new registration");
        assertEquals(beforeAnimals, refreshes[0]);
        assertEquals(3, logicalPlc.capture().queuedEntries().size(), "both real ROM lists enter the native queue");
        Pattern[] expected = fiery.clone();
        // This helper also submits a repeated write, which must not need another allocation.
        publishExpected(expected, Sonic2Constants.PLC_ANIMALS_DEZ);
        assertExplosion(expected);
        assertSame(renderer, provider.getRenderer(ObjectArtKeys.BOSS_EXPLOSION));
        assertEquals(base, renderer.getPatternBase());
        assertFalse(Sonic2RuntimePlcPublisher.append(provider, logicalPlc, refresh, Sonic2Constants.PLC_ANIMALS_DEZ));
        assertEquals(beforeAnimals, refreshes[0]);
    }

    @Test
    void zoneLoadRetiresThePriorSlotImageAndReloadDoesNotReuseMutatedCachedArt() throws Exception {
        provider.requestPlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        Pattern[] alias = fiery.clone();
        publishExpected(alias, Sonic2Constants.PLC_ANIMALS_DEZ);
        assertExplosion(alias);
        var oldRenderer = provider.getRenderer(ObjectArtKeys.BOSS_EXPLOSION);
        int oldBase = oldRenderer.getPatternBase();
        provider.loadArtForZone(14);
        assertSame(oldRenderer, provider.getRenderer(ObjectArtKeys.BOSS_EXPLOSION));
        assertEquals(oldBase, oldRenderer.getPatternBase());
        assertExplosion(fiery);
        provider.requestPlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        provider.requestPlc(Sonic2Constants.PLC_ANIMALS_DEZ);
        assertExplosion(alias);
        provider.loadArtForZone(0);
        cache();
        assertNotSame(oldRenderer, provider.getRenderer(ObjectArtKeys.BOSS_EXPLOSION));
        assertExplosion(fiery);
        provider.requestPlc(Sonic2Constants.PLC_ANIMALS_EHZ);
        provider.loadArtForZone(14);
        cache();
        assertExplosion(fiery);
        // Animal sheets are cached by Sonic2ObjectArt across zone loads too.
        var dez = Sonic2PlcLoader.parsePlc(rom, Sonic2Constants.PLC_ANIMALS_DEZ).entries();
        Pattern[] pig = PatternDecompressor.nemesis(rom, dez.getFirst().romAddr());
        assertEquals(18, pig.length, "the native Pig source does not fill its entire $14-tile slot");
        assertArrayEquals(pixels(pig), pixels(Arrays.copyOf(
                provider.getSheet(ObjectArtKeys.ANIMAL).getPatterns(), pig.length)));
        provider.requestPlc(Sonic2Constants.PLC_FIERY_EXPLOSION);
        provider.requestPlc(Sonic2Constants.PLC_ANIMALS_DEZ);
        assertExplosion(alias);
    }

    private void cache() {
        provider.ensurePatternsCached(graphics, com.openggf.graphics.PatternAtlasRange.OBJECTS.base());
    }

    private void publishExpected(Pattern[] expected, int plcId) throws Exception {
        // Expectation is decoded from the actual ROM list before invoking the production publisher.
        overlayExpected(expected, plcId);
        provider.requestPlc(plcId);
    }

    private void overlayExpected(Pattern[] expected, int plcId) throws Exception {
        for (var entry : Sonic2PlcLoader.parsePlc(rom, plcId).entries()) {
            Pattern[] source = PatternDecompressor.nemesis(rom, entry.romAddr());
            for (int i = 0; i < source.length; i++) {
                int relative = entry.tileIndex() + i - 0x580;
                if (relative >= 0 && relative < expected.length) expected[relative] = source[i];
            }
        }
    }

    private void assertAnimals(Pattern[] expected) {
        assertArrayEquals(pixels(Arrays.copyOf(expected, 40)),
                pixels(provider.getSheet(ObjectArtKeys.ANIMAL).getPatterns()));
        // Map_obj28_c_0010 (mappings/sprite/obj28_c.asm:10-12) and
        // Map_obj28_a_0010 (mappings/sprite/obj28_a.asm:10-12): both native consumers.
        assertPiece(ObjectArtKeys.ANIMAL, 12, expected, 3, 2, -12, -8, 6);
        assertPiece(ObjectArtKeys.ANIMAL, 3, expected, 2, 2, -8, -8, 0x14 + 8);
    }

    private void assertExplosion(Pattern[] expected) {
        ObjectSpriteSheet sheet = provider.getSheet(ObjectArtKeys.BOSS_EXPLOSION);
        assertArrayEquals(pixels(expected), pixels(sheet.getPatterns()), "current production sheet bytes");
        cache();
        assertEquals(7, sheet.getFrameCount());
        for (int frame = 0; frame < 7; frame++) {
            // Immutable mapping: Map_obj58_000E (mappings/sprite/obj58.asm:10-12), followed by six 4x4 frames.
            int side = frame == 0 ? 2 : 4;
            int tile = frame == 0 ? 0 : 4 + (frame - 1) * 16;
            assertPiece(ObjectArtKeys.BOSS_EXPLOSION, frame, expected,
                    side, side, -side * 4, -side * 4, tile);
        }
    }

    private void assertPiece(String key, int frame, Pattern[] expected, int width, int height,
                             int xOffset, int yOffset, int tile) {
        var sheet = provider.getSheet(key);
        var piece = sheet.getFrame(frame).pieces().getFirst();
        assertEquals(1, sheet.getFrame(frame).pieces().size());
        assertEquals(width, piece.widthTiles()); assertEquals(height, piece.heightTiles());
        assertEquals(xOffset, piece.xOffset()); assertEquals(yOffset, piece.yOffset());
        assertEquals(tile, piece.tileIndex());
        assertFalse(piece.hFlip()); assertFalse(piece.vFlip()); assertEquals(0, piece.paletteIndex());
        int[] expectedPixels = new int[320 * 224];
        Arrays.fill(expectedPixels, 0xFF000000);
        int visible = 0;
        for (int x = 0; x < width * 8; x++) for (int y = 0; y < height * 8; y++) {
            int index = expected[tile + (x / 8) * height + y / 8].getPixel(x % 8, y % 8) & 15;
            if (index == 0) continue;
            var color = palette.getColor(index);
            expectedPixels[(112 + yOffset + y) * 320 + 160 + xOffset + x] = 0xFF000000
                    | (color.r & 255) << 16 | (color.g & 255) << 8 | color.b & 255;
            visible++;
        }
        assertTrue(visible > 0, "each real native frame has drawable source pixels");
        assertTrue(Arrays.stream(expectedPixels).anyMatch(pixel -> pixel != 0xFF000000),
                "the ROM palette must make this a nonblank pixel witness");
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        provider.getRenderer(key).drawFrameIndex(frame, 160, 112);
        graphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        glFinish();
        var actual = ScreenshotCapture.captureFramebuffer(320, 224);
        assertArrayEquals(expectedPixels, actual.pixels(), "production GPU pixels for " + key + " frame " + frame);
        assertEquals(GL_NO_ERROR, glGetError());
    }

    private static byte[] pixels(Pattern[] patterns) {
        byte[] bytes = new byte[patterns.length * 64];
        for (int i = 0; i < patterns.length; i++) patterns[i].copyInto(bytes, i * 64);
        return bytes;
    }
}
