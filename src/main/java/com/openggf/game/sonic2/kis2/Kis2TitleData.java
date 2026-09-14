package com.openggf.game.sonic2.kis2;

import com.openggf.data.compression.EnigmaReader;
import com.openggf.data.compression.NemesisReader;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.util.PatternDecompressor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.List;

/** KiS2 TitleScreen ROM uploads. Offsets verified against a byte-identical c336fed chip build. */
public final class Kis2TitleData {
    private final LockOnAddressSpace rom;
    public final Pattern[] patterns = new Pattern[0x4C0];
    public final int[] foreground;
    public final int[] background = new int[64 * 28];
    public final Palette[] palettes;
    public final byte[] ripple, starCycle;
    public final int[][] starPositions = new int[10][2];
    public final List<SpriteMappingFrame> knuckles, stars, emblem, banner, text;

    public Kis2TitleData(LockOnAddressSpace rom) {
        if (!rom.hasChip()) throw new IllegalArgumentException("KiS2 title requires the chip");
        this.rom = rom;
        Arrays.setAll(patterns, i -> new Pattern());
        // TitleScreen NemDec calls and their ArtTile_ destinations.
        art(0x274F6C, 0); art(0x33CEDC, 0x150); art(0x33E65A, 0x3C0);
        art(0x33E700, 0x3CF); art(0x33E78A, 0x3DD);
        art(0x33E93E, 0x3FE); art(0x33EF7A, 0x488);
        foreground = map(0x274E86, 0xE000);
        int[] screen = map(0x274DC6, 0x4000), back = map(0x274E3A, 0x4000);
        for (int row = 0; row < 28; row++) {
            System.arraycopy(screen, row * 40, background, row * 64, 40);
            System.arraycopy(back, row * 24, background, row * 64 + 40, 24);
        }
        palettes = new Palette[]{palette(0x3106A8), palette(0x30249E), palette(0x3106C8), palette(0x3106E8)};
        ripple = bytes(0x30A8EA, 66);
        starCycle = bytes(0x3100AC, 12);
        starPositions[0] = new int[]{128, 40};
        byte[] positions = bytes(0x310280, 36);
        for (int i = 0; i < 9; i++) for (int axis = 0; axis < 2; axis++) {
            int at = i * 4 + axis * 2;
            starPositions[i + 1][axis] = ((positions[at] & 255) << 8 | positions[at + 1] & 255) - 128;
        }
        knuckles = mappings(0x3109B8, 0x150);
        stars = mappings(0x310912, 0x3C0);
        emblem = mappings(0x31093A, 0);
        banner = mappings(0x310B76, 0);
        text = mappings(0x310C34, 0x3DD);
    }

    private byte[] bytes(int address, int count) {
        var read = rom.require(address);
        return read.reader().slice(read.localAddress(), Math.min(count, read.reader().size() - read.localAddress()));
    }
    private void art(int address, int destination) {
        try (var channel = Channels.newChannel(new ByteArrayInputStream(bytes(address, 16384)))) {
            Pattern[] decoded = PatternDecompressor.fromBytes(NemesisReader.decompress(channel));
            System.arraycopy(decoded, 0, patterns, destination, decoded.length);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private int[] map(int address, int tile) {
        try (var channel = Channels.newChannel(new ByteArrayInputStream(bytes(address, 1024)))) {
            byte[] decoded = EnigmaReader.decompress(channel, tile);
            int[] words = new int[decoded.length / 2];
            for (int i = 0; i < words.length; i++) words[i] = (decoded[i * 2] & 255) << 8 | decoded[i * 2 + 1] & 255;
            return words;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private Palette palette(int address) {
        Palette result = new Palette(); result.fromSegaFormat(bytes(address, 32)); return result;
    }
    private List<SpriteMappingFrame> mappings(int address, int tile) {
        var read = rom.require(address);
        return Kis2SpriteMappings.load(read.reader(), read.localAddress(), tile);
    }
}
