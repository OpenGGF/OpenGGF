package com.openggf.game.sonic2.competition;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.Sonic2RingArt;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;

import java.io.IOException;
import java.util.Arrays;

/** ROM-only native geometry. Visual words retain their 2P pattern interpretation. */
public final class Sonic2CompetitionTerrain extends AbstractLevel {
    private Sonic2CompetitionTerrain(Rom rom, Sonic2CompetitionNativeArt art) throws IOException {
        super(art.profile().romZone());
        var profile = art.profile();
        var source = art.baseTerrain();
        byte[] layout = source.layoutBytes();
        if (layout.length != 128 * 16 * 2) throw new IOException("Invalid S2 competition layout size");
        map = new Map(2, 128, 16, layout);

        palettes = new Palette[4];
        palettes[0] = new Palette();
        palettes[0].fromSegaFormat(rom.readBytes(address(rom, Sonic2Constants.SONIC_TAILS_PALETTE_ADDR), 32));
        byte[] zonePalette = rom.readBytes(profile.paletteAddress(), profile.paletteSize());
        if (zonePalette.length != 96) throw new IOException("Invalid competition zone palette size");
        for (int line = 1; line < 4; line++) {
            palettes[line] = new Palette();
            palettes[line].fromSegaFormat(Arrays.copyOfRange(zonePalette, (line - 1) * 32, line * 32));
        }

        byte[] initialArt = source.patternBytes();
        patternCount = records(initialArt, Pattern.PATTERN_SIZE_IN_ROM);
        patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
            patterns[i].fromSegaFormat(record(initialArt, i, Pattern.PATTERN_SIZE_IN_ROM));
        }
        // These are native 8x16 VRAM references, including PLC/animated slots
        // outside LoadZoneTiles' initial art. Do not validate them against or
        // pad the unrelated ordinary 8x8 pattern cache. Native views read VRAM.
        byte[] blockTable = art.blockTableBytes();
        chunkCount = records(blockTable, Chunk.CHUNK_SIZE_IN_ROM);
        int[] primary = indices(source.primaryCollisionBytes(), chunkCount);
        int[] secondary = indices(source.secondaryCollisionBytes(), chunkCount);
        chunks = new Chunk[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            chunks[i] = new Chunk();
            chunks[i].fromSegaFormat(record(blockTable, i, Chunk.CHUNK_SIZE_IN_ROM), primary[i], secondary[i]);
        }
        byte[] chunkTable = source.chunkBytes();
        blockCount = records(chunkTable, 128);
        blocks = new Block[blockCount];
        for (int i = 0; i < blockCount; i++) {
            blocks[i] = new Block();
            blocks[i].fromSegaFormat(record(chunkTable, i, 128));
        }
        byte[] heights = rom.readBytes(Sonic2Constants.SOLID_TILE_VERTICAL_MAP_ADDR, 0x1000);
        byte[] widths = rom.readBytes(Sonic2Constants.SOLID_TILE_HORIZONTAL_MAP_ADDR, 0x1000);
        byte[] angles = rom.readBytes(Sonic2Constants.SOLID_TILE_ANGLE_ADDR, 0x100);
        solidTileCount = angles.length;
        solidTiles = new SolidTile[solidTileCount];
        for (int i = 0; i < solidTileCount; i++) {
            solidTiles[i] = new SolidTile(i, record(heights, i, 16), record(widths, i, 16), angles[i]);
        }
        var bounds = profile.bounds();
        minX = bounds.minX(); maxX = bounds.maxX();
        minY = bounds.minY(); maxY = bounds.maxY();
        objects = profile.objects();
        rings = profile.rings();
        ringSpriteSheet = new Sonic2RingArt(rom, RomByteReader.fromRom(rom)).load();
    }

    public static Sonic2CompetitionTerrain decode(Rom rom, Sonic2CompetitionNativeArt art) throws IOException {
        return new Sonic2CompetitionTerrain(rom, art);
    }

    private static int records(byte[] bytes, int size) throws IOException {
        if (bytes.length % size != 0) throw new IOException("Misaligned native terrain record");
        return bytes.length / size;
    }

    private static byte[] record(byte[] bytes, int index, int size) {
        return Arrays.copyOfRange(bytes, index * size, (index + 1) * size);
    }

    private static int[] indices(byte[] bytes, int count) throws IOException {
        if (bytes.length > count) throw new IOException("Collision indices exceed native Block_Table");
        int[] result = new int[count];
        for (int i = 0; i < bytes.length; i++) result[i] = bytes[i] & 255;
        return result;
    }
}
