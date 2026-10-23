package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.RomByteReader;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpritePieceRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceDecoder.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestSonic2InterlaceDecoder {
    @Test void immutableVramKeepsAllSixteenRowsAndIgnoresOnlyPatternBitTen() {
        byte[] source = patternedVram();
        var vram = new Sonic2InterlaceVram(source);
        source[0] ^= 0xFF;
        byte[] exposed = vram.bytes(); exposed[0] ^= 0xFF;
        assertEquals(new Sonic2InterlaceVram(patternedVram()), vram);
        assertEquals(vram.hashCode(), new Sonic2InterlaceVram(patternedVram()).hashCode());
        byte[] decoded = decodePattern(vram, 1023);
        assertEquals(128, decoded.length);
        for (boolean h : new boolean[]{false, true}) for (boolean v : new boolean[]{false, true}) {
            int attributes = 0xE000 | (h ? 0x800 : 0) | (v ? 0x1000 : 0);
            var name = new NameWord(attributes | 0x7FF);
            assertEquals(1023, name.patternIndex()); assertEquals(3, name.palette()); assertTrue(name.priority());
            for (int y = 0; y < 16; y++) for (int x = 0; x < 8; x++) {
                int expected = referencePixel(vram.bytes(), 1023, h ? 7 - x : x, v ? 15 - y : y);
                assertEquals(expected, sampleCell(vram, name, x, y).color());
                assertEquals(referencePixel(vram.bytes(), 1023, x, y), decoded[y * 8 + x]);
            }
        }
        var modified = vram.withWrite(65535, new byte[]{0x12});
        assertNotEquals(vram, modified);
        assertEquals(0x12, modified.readByte(-1));
        assertEquals(0x12 << 8 | modified.readByte(0), modified.readWord(65535));
        assertThrows(IllegalArgumentException.class, () -> vram.withWrite(65535, new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> new Sonic2InterlaceVram(new byte[64]));
    }

    @Test void projectedSpritesMatchIndependentNativeNibbleOracleForEveryPixel() {
        var vram = new Sonic2InterlaceVram(patternedVram());
        byte[] reference = vram.bytes();
        var producer = new Sonic2InterlaceProducer(0x1D8000, 0x1000);
        var atlas = uploadedPatterns(producer, vram);
        assertEquals(2048, atlas.size());
        for (var field : Field.values()) for (boolean h : new boolean[]{false, true}) {
            for (boolean v : new boolean[]{false, true}) for (int top : new int[]{-3, 0, 1, 224, 225}) {
                var sprite = new Sprite(top + 256, 11, 0,
                        new NameWord(0xE3FA | (h ? 0x800 : 0) | (v ? 0x1000 : 0)), 141);
                var mutable = new ArrayList<>(List.of(sprite));
                var input = new Input(vram, field, mutable); mutable.clear();
                var prepared = producer.prepareSprites(input);
                assertEquals(1, prepared.sprites().size());
                var piece = prepared.sprites().getFirst();
                assertEquals(sprite, piece.nativeSprite());
                assertEquals(3, piece.geometry().widthTiles()); assertEquals(4, piece.geometry().heightTiles());
                assertEquals(64, sprite.heightPixels());
                assertEquals(12, piece.fragments().size());
                assertEquals(0x3FA, piece.geometry().rawTileWordLow11());
                assertTrue(piece.geometry().piecePriority()); assertEquals(3, piece.geometry().paletteIndex());
                assertEquals(field.projectY(top), piece.geometry().y());
                assertEquals(h, piece.geometry().hFlip()); assertEquals(v, piece.geometry().vFlip());
                assertThrows(UnsupportedOperationException.class, () -> piece.fragments().clear());
                for (var fragment : piece.fragments()) {
                    SpritePieceRenderer.renderPreparedPiece(fragment, (id, hf, vf, pal, x, y) -> {
                        var pixels = atlas.get(id);
                        assertNotNull(pixels);
                        for (int fy = 0; fy < 8; fy++) for (int fx = 0; fx < 8; fx++) {
                            int localX = x + fx - sprite.x();
                            int localY = field.physicalLine(y + fy) - sprite.y();
                            int sx = h ? sprite.widthPixels() - 1 - localX : localX;
                            int sy = v ? sprite.heightPixels() - 1 - localY : localY;
                            int pattern = (1018 + sx / 8 * 4 + sy / 16) % 1024;
                            int expected = referencePixel(reference, pattern, sx % 8, sy % 16);
                            assertEquals(expected, pixels[(vf ? 7 - fy : fy) * 8 + (hf ? 7 - fx : fx)],
                                    "native row/parity/stride at " + localX + "," + localY + "/" + field);
                            assertEquals(expected, sampleSprite(vram, sprite, localX, localY).color());
                        }
                    });
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new Sonic2InterlaceProducer(0, 2047));
        assertThrows(IllegalArgumentException.class, () -> new Sonic2InterlaceProducer(Integer.MAX_VALUE, 2048));
    }

    @Test void planeWrappingOddScrollAndVerticalFlipUseNativeSixteenRowCells() {
        byte[] bytes = patternedVram();
        for (int row = 0; row < 32; row++) for (int col = 0; col < 32; col++) {
            int word = ((row * 32 + col) & 1023) | ((col % 4) << 13)
                    | ((col & 1) == 0 ? 0x800 : 0) | ((row & 1) == 0 ? 0x9000 : 0);
            word(bytes, 0xC000 + (row * 32 + col) * 2, word);
        }
        var vram = new Sonic2InterlaceVram(bytes);
        var producer = new Sonic2InterlaceProducer(0, 8192);
        var atlas = uploadedPatterns(producer, vram);
        for (var field : Field.values()) for (int scrollY : new int[]{-19, -1, 0, 1, 15, 513}) {
            var plane = new Plane(0xC000, 32, 32, -7, scrollY);
            var projected = producer.preparePlane(vram, plane, field,
                    new Sonic2InterlaceProducer.Viewport(-3, -2, 37, 19));
            assertThrows(UnsupportedOperationException.class, () -> projected.cells().clear());
            for (int y = -2; y < 17; y++) for (int x = -3; x < 34; x++) {
                int sx = Math.floorMod(x - 7, 256), sy = Math.floorMod(y * 2 + field.parity() + scrollY, 512);
                int raw = vram.readWord(0xC000 + (sy / 16 * 32 + sx / 8) * 2);
                int expected = referencePixel(bytes, raw & 1023, (raw & 0x800) != 0 ? 7 - sx % 8 : sx % 8,
                        (raw & 0x1000) != 0 ? 15 - sy % 16 : sy % 16);
                assertEquals(expected, samplePlane(vram, plane, field, x, y).color());
                int px = x, py = y;
                var cell = projected.cells().stream().filter(c -> px >= c.x() && px < c.x() + 8
                        && py >= c.y() && py < c.y() + 8).findFirst().orElseThrow();
                int tx = x - cell.x(), ty = y - cell.y();
                int actual = atlas.get(cell.patternId())[(cell.name().vFlip() ? 7 - ty : ty) * 8
                        + (cell.name().hFlip() ? 7 - tx : tx)];
                assertEquals(expected, actual, "plane field projection");
                assertEquals(raw, cell.name().value());
            }
        }
    }

    @Test void satLinksRetainNativeYAndBoundRepeatedChains() {
        byte[] bytes = new byte[65536];
        sat(bytes, 0xD000, 0x3FF, 15, 3, 0xFC21, 0x1FF);
        sat(bytes, 0xD018, 0x1E0, 4, 0, 0x2401, 0x80);
        var entries = readSat(new Sonic2InterlaceVram(bytes), 0xD000, true);
        assertEquals(2, entries.size()); assertEquals(767, entries.getFirst().y());
        assertEquals(383, entries.getFirst().x()); assertEquals(64, entries.getFirst().heightPixels());
        assertEquals(224, entries.getLast().y());
        var producer = new Sonic2InterlaceProducer(0, 2048);
        assertEquals(112, producer.prepareSprites(new Input(new Sonic2InterlaceVram(bytes), Field.EVEN,
                List.of(entries.getLast()))).sprites().getFirst().geometry().y());
        bytes[0xD018 + 3] = 3;
        assertEquals(80, readSat(new Sonic2InterlaceVram(bytes), 0xD000, true).size());
        assertEquals(64, readSat(new Sonic2InterlaceVram(bytes), 0xD000, false).size());
        assertThrows(UnsupportedOperationException.class, entries::clear);
    }

    @Test void largePlaneDimensionsAliasInsideTheRelativeEightKiBNameTableWindow() {
        byte[] bytes = new byte[65536];
        word(bytes, 0xE000, 0x1234); word(bytes, 0, 0x5678);
        word(bytes, 0xE03E, 0x9ABC); word(bytes, 0x3E, 0xDEF0);
        var image = new Sonic2InterlaceVram(bytes);
        for (int width : new int[]{32, 64, 128}) {
            var plane = new Plane(0xE000, width, 128, 0, 0);
            for (int row : new int[]{0, 4096 / width, 8192 / width}) {
                assertEquals(0x1234, planeName(image, plane, 0, row).value());
                assertEquals(0x9ABC, planeName(image, plane, 31, row).value());
            }
        }
    }

    @Test void mappingUsesSeparateTwoPlayerWordsAndShippedMultispriteFlipDispatch() {
        byte[] bytes = new byte[32];
        word(bytes, 0, 4); word(bytes, 4, 1);
        bytes[6] = -9; bytes[7] = 10; // ordinary 3x3 -> native 3x2 cells
        word(bytes, 8, 0x7777); word(bytes, 10, 0x2803); word(bytes, 12, -5);
        var pieces = Sonic2InterlaceSpriteMapper.readFrame(RomByteReader.fromBytes(bytes), 0, 0);
        assertEquals(List.of(new Sonic2InterlaceSpriteMapper.MappingPiece(-5, -9, 10, 0x2803)), pieces);
        int art = Sonic2InterlaceSpriteMapper.nativeArtWord(0xF81B);
        assertEquals(0xF80D, art);
        var ordinary = Sonic2InterlaceSpriteMapper.map(pieces, 100, 200, art, true, true, false).getFirst();
        assertEquals(81, ordinary.x()); assertEquals(185, ordinary.y()); assertEquals(9, ordinary.size());
        assertEquals(((0xF80D + 0x2803) & 65535) ^ 0x1800, ordinary.name().value());
        var multi = Sonic2InterlaceSpriteMapper.map(pieces, 100, 200, art, true, true, true).getFirst();
        assertEquals(95, multi.x()); assertEquals(191, multi.y());
        assertEquals((0xF80D + 0x2803) & 65535, multi.name().value());
        var maskAvoidance = Sonic2InterlaceSpriteMapper.map(pieces, -123, 0, 0, false, false, false).getFirst();
        assertEquals(1, maskAvoidance.rawX());
    }

    static byte[] patternedVram() {
        byte[] result = new byte[65536];
        for (int pattern = 0; pattern < 1024; pattern++) for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 8; x += 2) {
                int left = (pattern * 3 + y * 5 + x * 7) % 16;
                int right = (pattern * 3 + y * 5 + (x + 1) * 7) % 16;
                result[pattern * 64 + y * 4 + x / 2] = (byte) (left * 16 + right);
            }
        }
        return result;
    }

    static int referencePixel(byte[] bytes, int pattern, int x, int y) {
        int packed = bytes[pattern * 64 + y * 4 + x / 2] & 255;
        return x % 2 == 0 ? packed / 16 : packed % 16;
    }

    static Map<Integer, byte[]> uploadedPatterns(Sonic2InterlaceProducer producer, Sonic2InterlaceVram vram) {
        var graphics = mock(GraphicsManager.class);
        Map<Integer, byte[]> result = new HashMap<>();
        doAnswer(call -> {
            byte[] pixels = new byte[64]; ((Pattern) call.getArgument(0)).copyInto(pixels, 0);
            result.put(call.getArgument(1), pixels); return null;
        }).when(graphics).cachePatternTexture(any(Pattern.class), anyInt());
        producer.uploadPatterns(graphics, vram);
        verify(graphics).beginPatternAtlasBatch(); verify(graphics).endPatternAtlasBatch();
        return result;
    }

    static void word(byte[] bytes, int address, int value) {
        bytes[address] = (byte) (value >>> 8); bytes[address + 1] = (byte) value;
    }

    static void sat(byte[] bytes, int address, int y, int size, int link, int name, int x) {
        word(bytes, address, y); bytes[address + 2] = (byte) size; bytes[address + 3] = (byte) link;
        word(bytes, address + 4, name); word(bytes, address + 6, x);
    }
}
