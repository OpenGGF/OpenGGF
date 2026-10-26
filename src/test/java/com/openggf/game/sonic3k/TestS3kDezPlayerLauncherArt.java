package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.resources.PlcParser;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native $78: DEZ1's PLC $36 supplies the frame-1 art; fresh DEZ2 does not. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezPlayerLauncherArt {
    private static final String KEY = Sonic3kObjectArtKeys.FBZ_DEZ_PLAYER_LAUNCHER;
    private static final List<SpriteMappingPiece> FBZ_FRAME = List.of(
            new SpriteMappingPiece(-8, -16, 1, 1, 0, false, false, 0, false),
            new SpriteMappingPiece(-16, -8, 4, 1, 1, false, false, 0, false));
    private static final List<SpriteMappingPiece> DEZ_FRAME = List.of(
            new SpriteMappingPiece(-16, -16, 2, 1, 0, false, false, 0, false),
            new SpriteMappingPiece(-16, -8, 4, 1, 2, false, false, 0, false));

    @AfterEach void tearDown() { TestEnvironment.resetAll(); }

    @Test
    void plansSelectOnlyTheLoadedDez1FrameAndKeepBothFbzActsUnchanged() {
        var dez = entry(Sonic3kZoneIds.ZONE_DEZ, 0);
        assertEquals(0x03BA8A, dez.mappingAddr());
        assertEquals(0x02FC, dez.artTileBase());
        assertEquals(1, dez.palette());
        assertArrayEquals(new int[]{1}, dez.frameFilter());
        assertTrue(Sonic3kPlcArtRegistry.getPlan(Sonic3kZoneIds.ZONE_DEZ, 1).levelArt().stream()
                .noneMatch(candidate -> candidate.key().equals(KEY)),
                "fresh DEZ2's PLC $38 does not load ArtNem_DEZMiniboss at $02FC");
        for (int act = 0; act < 2; act++) {
            var fbz = entry(Sonic3kZoneIds.ZONE_FBZ, act);
            assertEquals(0x03BA8A, fbz.mappingAddr());
            assertEquals(0x03B5, fbz.artTileBase()); // ArtTile_FBZMisc+$3C
            assertEquals(1, fbz.palette());
            assertArrayEquals(new int[]{0}, fbz.frameFilter());
        }
    }

    @Test
    void lockedOnRomPinsBothFullMappingFramesAndThePlacedDez1Resource() throws Exception {
        try (Rom rom = openRom()) {
            var reader = RomByteReader.fromRom(rom);
            // Map_FBZDEZPlayerLauncher $3BA8A: literal table, counts and all six-byte pieces.
            assertArrayEquals(HexFormat.of().parseHex(
                    "000400120002f0000000fff8f80c0001fff00002f0040000fff0f80c0002fff0"),
                    reader.slice(0x03BA8A, 32));
            var frames = S3kSpriteDataLoader.loadMappingFrames(reader, 0x03BA8A, 2);
            assertEquals(2, frames.size());
            assertEquals(FBZ_FRAME, frames.get(0).pieces());
            assertEquals(DEZ_FRAME, frames.get(1).pieces());

            // Native LevelLoadBlock DEZ1: both high-byte PLC IDs are $36.
            assertEquals(0x36, reader.readU8(0x09211C));
            assertEquals(0x36, reader.readU8(0x092120));
            assertEquals(List.of(new PlcParser.PlcEntry(0x154552, 0x034D),
                            new PlcParser.PlcEntry(0x155AAA, 0x02FC)),
                    Sonic3kPlcLoader.parsePlc(rom, 0x36).entries());
            assertTrue(Sonic3kPlcLoader.parsePlc(rom, 0x38).entries().stream()
                    .noneMatch(cue -> cue.tileIndex() == 0x02FC));

            // DEZ1_Sprites $1F98F4: actual $78 placements, not a fabricated object inlet.
            int launcherCount = 0;
            for (int ordinal = 0; ordinal < 365; ordinal++) {
                if (reader.readU8(0x1F98F4 + ordinal * 6 + 4) == 0x78) launcherCount++;
            }
            assertEquals(10, launcherCount);
            assertArrayEquals(HexFormat.of().parseHex("04500a807800"),
                    reader.slice(0x1F9930, 6), "DEZ1 placement 10 is the native launcher");
        }
    }

    @Test
    void freshDez1ProviderPublishesNativeFrameOneAsLocalZeroWithLoadedRomPixels() throws Exception {
        // Declared inlet: the public fresh-level lifecycle, without player-coordinate seeding.
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .withFreshLevelStartLifecycle().withSkippedZoneIntro().build();
        var level = assertInstanceOf(Sonic3kLevel.class, GameServices.level().getCurrentLevel());
        assertTrue(level.getPatternLoadCueSchedule().contains(0x36));
        ObjectSpriteSheet sheet = publishedSheet(DEZ_FRAME, 0x02FC, List.of(
                List.of(-16, -16, 2, 1, 0x02FC, 1, 0, 0, 0),
                List.of(-16, -8, 4, 1, 0x02FE, 1, 0, 0, 0)));
        assertEquals(6, sheet.getPatterns().length);
        // Comparison only: independent ROM archive decode must match the production-loaded pixels.
        try (Rom rom = openRom()) {
            Pattern[] nativeArt = PlcParser.decompressEntry(rom, new PlcParser.PlcEntry(0x155AAA, 0x02FC));
            assertTrue(nativeArt.length >= 6);
            for (int tile = 0; tile < 6; tile++) {
                assertSame(level.getPattern(0x02FC + tile), sheet.getPatterns()[tile]);
                assertArrayEquals(pixels(nativeArt[tile]), pixels(sheet.getPatterns()[tile]),
                        "launcher tile +" + tile + " comes from the native DEZ1 PLC archive");
            }
        }
    }

    @Test
    void freshFbz1ProviderRetainsNativeFrameZeroAsTheControl() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 0)
                .withFreshLevelStartLifecycle().withSkippedZoneIntro().build();
        ObjectSpriteSheet sheet = publishedSheet(FBZ_FRAME, 0x03B5, List.of(
                List.of(-8, -16, 1, 1, 0x03B5, 1, 0, 0, 0),
                List.of(-16, -8, 4, 1, 0x03B6, 1, 0, 0, 0)));
        assertEquals(5, sheet.getPatterns().length);
        for (int tile = 0; tile < 5; tile++) {
            assertSame(GameServices.level().getCurrentLevel().getPattern(0x03B5 + tile),
                    sheet.getPatterns()[tile]);
        }
    }

    private static Sonic3kPlcArtRegistry.LevelArtEntry entry(int zone, int act) {
        var matches = Sonic3kPlcArtRegistry.getPlan(zone, act).levelArt().stream()
                .filter(candidate -> candidate.key().equals(KEY)).toList();
        assertEquals(1, matches.size(), "exactly one art entry for the native launcher key");
        return matches.getFirst();
    }

    private static ObjectSpriteSheet publishedSheet(List<SpriteMappingPiece> expected,
            int nativeTileBase, List<List<Integer>> expectedPrepared) {
        var provider = assertInstanceOf(Sonic3kObjectArtProvider.class,
                GameModuleRegistry.getCurrent().getObjectArtProvider());
        ObjectSpriteSheet sheet = provider.getSheet(KEY);
        assertNotNull(sheet, "the actual level provider must publish the launcher sheet");
        assertNotNull(provider.getRenderer(KEY));
        assertTrue(provider.getRenderer(KEY).isReady(), "the public renderer entry must be ready");
        assertEquals(1, sheet.getFrameCount());
        assertEquals(expected, sheet.getFrame(0).pieces(), "selected native frame is compacted to local zero");
        assertEquals(1, sheet.getPaletteIndex());
        var prepared = new ArrayList<List<Integer>>();
        for (var piece : sheet.getFrame(0).pieces()) {
            SpritePieceRenderer.preparePiece(piece, 0, 0, nativeTileBase, sheet.getPaletteIndex(),
                    false, false, false, actual -> prepared.add(List.of(actual.x(), actual.y(),
                            actual.widthTiles(), actual.heightTiles(), actual.firstPatternIndex(),
                            actual.paletteIndex(), actual.hFlip() ? 1 : 0, actual.vFlip() ? 1 : 0,
                            actual.piecePriority() ? 1 : 0)));
        }
        assertEquals(expectedPrepared, prepared, "native piece geometry, tiles, flips, priority and palette");
        return sheet;
    }

    private static byte[] pixels(Pattern pattern) {
        byte[] pixels = new byte[64];
        pattern.copyInto(pixels, 0);
        return pixels;
    }

    private static Rom openRom() throws Exception {
        var file = RomTestUtils.ensureSonic3kRomAvailable();
        assertNotNull(file);
        Rom rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        return rom;
    }
}
