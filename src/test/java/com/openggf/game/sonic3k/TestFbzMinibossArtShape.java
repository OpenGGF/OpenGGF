package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.resources.CompressionType;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestFbzMinibossArtShape {
    @Test
    void lockedOnDirectKosmMappingHasExactEighteenFrameShape() throws Exception {
        Sonic3kPlcArtRegistry.StandaloneArtEntry entry = Sonic3kPlcArtRegistry.getPlan(4, 0)
                .standaloneArt().stream().filter(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ_MINIBOSS))
                .findFirst().orElseThrow();
        assertEquals(Sonic3kConstants.ART_KOSM_FBZ_MINIBOSS_ADDR, entry.artAddr());
        assertEquals(Sonic3kConstants.MAP_FBZ_MINIBOSS_ADDR, entry.mappingAddr());
        assertEquals(CompressionType.KOSINSKI_MODULED, entry.compression());

        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists());
        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()));
            List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(
                    RomByteReader.fromRom(rom), Sonic3kConstants.MAP_FBZ_MINIBOSS_ADDR, 18);
            assertArrayEquals(new int[] {4,1,1,2,2,2,2,4,6,6,6,6,6,6,6,6,6,2},
                    frames.stream().mapToInt(frame -> frame.pieces().size()).toArray());
            assertEquals(0x51, frames.stream().flatMap(frame -> frame.pieces().stream())
                    .mapToInt(piece -> piece.tileIndex() & 0x7FF).max().orElseThrow());
        }
    }

    @Test
    void paletteIsExactThirtyTwoBytesAndUnusedPlc5eIsNotRuntimeSource() throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists());
        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()));
            assertEquals(32, rom.readBytes(Sonic3kConstants.PAL_FBZ_MINIBOSS_ADDR, 32).length);
        }
        Sonic3kPlcArtRegistry.StandaloneArtEntry entry = Sonic3kPlcArtRegistry.getPlan(4, 0)
                .standaloneArt().stream().filter(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ_MINIBOSS))
                .findFirst().orElseThrow();
        assertNotEquals(0x5E, entry.artAddr());
    }

    @Test
    void actOneFragmentsUseGenericCapsuleMapAtNativeMinus46LevelTileBase() {
        Sonic3kPlcArtRegistry.LevelArtEntry entry = Sonic3kPlcArtRegistry.getPlan(4, 0)
                .levelArt().stream().filter(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ1_MINIBOSS_FRAGMENTS))
                .findFirst().orElseThrow();

        assertEquals(Sonic3kConstants.MAP_EGG_CAPSULE_ADDR, entry.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_EGG_CAPSULE - 0x46, entry.artTileBase());
        assertEquals(0, entry.palette());
        assertArrayEquals(new int[] {2, 3, 0xA, 4, 0xB}, entry.frameFilter());
        assertTrue(Sonic3kPlcArtRegistry.getPlan(4, 1).levelArt().stream()
                .noneMatch(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ1_MINIBOSS_FRAGMENTS)),
                "the FBZ1 fragments are not the act-2-only standalone egg-capsule sheet");
    }

    @Test
    void actOneFragmentSheetCompactsFiveSourceFramesAndTheirExactPatternWindow() throws Exception {
        Sonic3kPlcArtRegistry.LevelArtEntry entry = Sonic3kPlcArtRegistry.getPlan(4, 0)
                .levelArt().stream().filter(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ1_MINIBOSS_FRAGMENTS))
                .findFirst().orElseThrow();
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists());

        Level level = mock(Level.class);
        when(level.getPatternCount()).thenReturn(0x800);
        when(level.getPattern(anyInt())).thenAnswer(invocation -> new Pattern());
        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()));
            RomByteReader reader = RomByteReader.fromRom(rom);
            List<SpriteMappingFrame> source = entry.mappingFrameCount() > 0
                    ? S3kSpriteDataLoader.loadMappingFrames(reader, entry.mappingAddr(),
                            entry.mappingFrameCount(), entry.mappingFormat())
                    : S3kSpriteDataLoader.loadMappingFrames(reader, entry.mappingAddr(), entry.mappingFormat());
            Sonic3kObjectArt art = new Sonic3kObjectArt(level, reader);
            ObjectSpriteSheet sheet = art.buildLevelArtSheetFromRomFiltered(
                    entry.mappingAddr(), entry.artTileBase(), entry.palette(), entry.frameFilter(),
                    entry.mappingFormat(), entry.mappingFrameCount());

            assertNotNull(sheet);
            assertEquals(5, sheet.getFrameCount(), "the filtered sheet exposes compact indices 0..4");
            int minTile = Integer.MAX_VALUE;
            int maxTileExclusive = Integer.MIN_VALUE;
            for (int sourceIndex : entry.frameFilter()) {
                for (SpriteMappingPiece piece : source.get(sourceIndex).pieces()) {
                    minTile = Math.min(minTile, piece.tileIndex());
                    maxTileExclusive = Math.max(maxTileExclusive,
                            piece.tileIndex() + piece.widthTiles() * piece.heightTiles());
                }
            }
            assertEquals(maxTileExclusive - minTile, sheet.getPatterns().length);
            assertEquals(entry.artTileBase() + minTile, art.getLastBuildStartTile());
            assertEquals(sheet.getPatterns().length, art.getLastBuildTileCount());

            for (int compactIndex = 0; compactIndex < entry.frameFilter().length; compactIndex++) {
                SpriteMappingFrame expected = source.get(entry.frameFilter()[compactIndex]);
                SpriteMappingFrame actual = sheet.getFrame(compactIndex);
                assertEquals(expected.pieces().size(), actual.pieces().size());
                for (int pieceIndex = 0; pieceIndex < expected.pieces().size(); pieceIndex++) {
                    SpriteMappingPiece sourcePiece = expected.pieces().get(pieceIndex);
                    SpriteMappingPiece compactPiece = actual.pieces().get(pieceIndex);
                    assertEquals(sourcePiece.xOffset(), compactPiece.xOffset());
                    assertEquals(sourcePiece.yOffset(), compactPiece.yOffset());
                    assertEquals(sourcePiece.widthTiles(), compactPiece.widthTiles());
                    assertEquals(sourcePiece.heightTiles(), compactPiece.heightTiles());
                    assertEquals(sourcePiece.tileIndex() - minTile, compactPiece.tileIndex());
                    assertEquals(sourcePiece.hFlip(), compactPiece.hFlip());
                    assertEquals(sourcePiece.vFlip(), compactPiece.vFlip());
                    assertEquals(sourcePiece.paletteIndex(), compactPiece.paletteIndex());
                    assertEquals(sourcePiece.priority(), compactPiece.priority());
                }
            }
        }
    }
}
